/****************************************************************************************
 *  EsriBaseOutputStream.java
 *
 *  Created: Feb 10, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.output;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.utils.FieldCachingObjectBuffer;
import org.opensextant.giscore.utils.ObjectBuffer;

/**
 * The esri formats require that the features be sorted into uniform bins where
 * the attributes and the type of geometry involved is homogeneous. This class
 * takes care of the sorting of features into temporary files that hold a
 * uniform set of features, and which allows the consumer to then get the
 * features back out by category.
 * 
 * @author DRAND
 */
public class FeatureSorter {
	private static final String OID = "OID";
	private static final SimpleField oid;
	
	static {
		oid = new SimpleField(OID);
		oid.setType(SimpleField.Type.OID);
		oid.setLength(4);
		oid.setRequired(true);
		oid.setEditable(false);
		oid.setAliasName(OID);
		oid.setModelName(OID);
	}
	
	/**
	 * Depending on the output, split the features by style as well as
	 * by the other information. 
	 */
	private boolean splitOnStyle = false;
	/**
	 * Maps the schema name to the schema. The schemata included are both
	 * defined schemata as well as implied or inline schemata that are defined
	 * with their data.
	 */
	private Map<URI, Schema> schemata = null;
	/**
	 * Maps a set of simple fields, derived from inline data declarations to a
	 * schema. This is used to gather like features together. THe assumption is
	 * that we will see consistent elements between features.
	 */
	private Map<Set<SimpleField>, Schema> internalSchema = null;
	/**
	 * Each schema's data is stored in a buffer since the actual record
	 * sets need to be written for one type at a time.
	 */
	private Map<FeatureKey, ObjectBuffer> bufferMap = null;
	/**
	 * The class keeps track of the overall extent of the features in a
	 * particular collection.
	 */
	private Map<FeatureKey, Geodetic2DBounds> boundingBoxes = null;
	/**
	 * To avoid all the juggling, we track the current feature key and current
	 * stream. When we hit a new piece of data for a different key we flip both
	 * of these pieces of information.
	 */
	private transient FeatureKey currentKey = null;

	/**
	 * Empty ctor
	 */
	public FeatureSorter() {
		this(false);
	}
	
	/**
	 * Ctor
	 * @param splitOnStyle if {@code true} then split features by the
	 * referenced style as well as geometry and path.
	 */
	public FeatureSorter(boolean splitOnStyle) {
		try {
			this.splitOnStyle = splitOnStyle;
			cleanup();
		} catch (IOException e) {
			// Ignore, can't happen since no stream is open
		}
	}

	/**
	 * @return the known keys to the files
	 */
	public Collection<FeatureKey> keys() {
		return bufferMap.keySet();
	}
	
	/**
	 * @return the schemata in use, either formally declared or informally derived
	 */
	public Collection<Schema> schemata() {
		return Collections.unmodifiableCollection(schemata.values());
	}

	/**
	 * Get the file corresponding to the given feature key.
	 * 
	 * @param featureKey
	 *            the key, never {@code null}
	 * @return the corresponding file, or {@code null} if the key is
	 *         unknown, which cannot occur if the {@link #keys()} method was
	 *         used to obtain the key set.
	 */
	public ObjectBuffer getBuffer(FeatureKey featureKey) {
		if (featureKey == null) {
			throw new IllegalArgumentException(
					"featureKey should never be null");
		}
		return bufferMap.get(featureKey);
	}

	/**
	 * Add a row to the appropriate file
	 * 
	 * @param row
	 * @param path
	 */
	public FeatureKey add(Row row, String path) {
		if (row == null) {
			throw new IllegalArgumentException(
					"row should never be null");
		}
		try {
			Class<? extends Geometry> geoclass = null;
			Geometry g = null;
			Schema s = getSchema(row);
			if (s.getOidField() == null) {
				s.put(oid);
			}
			String styleid = null;
			if (row instanceof Feature) {
				Feature feature = (Feature) row;
				if (feature.getGeometry() != null) {
					g = feature.getGeometry();
					geoclass = feature.getGeometry().getClass();
				}
				if (splitOnStyle && feature.getStyleUrl() != null 
						&& feature.getStyleUrl().startsWith("#")) {
					styleid = feature.getStyleUrl().substring(1);
				}
			}
			FeatureKey key = new FeatureKey(s, path, geoclass, row.getClass());
			if (styleid != null) key.setStyleRef(styleid);
			ObjectBuffer buffer;
			if (!key.equals(currentKey)) {
				currentKey = key;
				buffer = bufferMap.get(key);
				if (buffer == null) {
					buffer = new FieldCachingObjectBuffer();
					bufferMap.put(key, buffer);
				}
			}
			buffer = bufferMap.get(key);
			buffer.write(row);
			if (g != null) {
				Geodetic2DBounds bounds = boundingBoxes.get(key);
				if (bounds == null) {
					bounds = new Geodetic2DBounds(g.getBoundingBox());
					boundingBoxes.put(key, bounds);
				} else {
					bounds.include(g.getBoundingBox());
				}
			}
			return key;
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * A row may either have a reference to a known schema or data that is
	 * not governed by a schema. The assumption here is that the extended data
	 * will only belong to a single schema, either ad hoc or explicit.
	 * <p>
	 * This code assumes that local references to a schema involve schemata that
	 * have already been read. External references will result in the creation
	 * of a dummy schema object with an appropriate name.
	 * <p>
	 * Names of the schema are urls, either fragments, which reference local
	 * schemata, or full urls, which reference non-resident schemata.
	 * 
	 * @param row
	 *            the row
	 * @return the referenced schema, never {@code null} but keep in mind
	 *         that internal schemata may be returned.
	 */
	public Schema getSchema(Row row) {
		URI schema = row.getSchema();
		if (schema != null) {
			Schema rval = schemata.get(schema);
			if (rval == null) {
				rval = new Schema(schema);
				schemata.put(schema, rval);
			}
			return rval;
		}
		// No schema case
		Set<SimpleField> fields = getFields(row);
		Schema rval = internalSchema.get(fields);
		if (rval == null) {
			rval = new Schema();
			for (SimpleField field : fields) {
				rval.put(field.getName(), field);
			}
			internalSchema.put(fields, rval);
		}
		return rval;
	}

	/**
	 * This row has inline data in the extended data, so extract the field
	 * names and create a set of such fields.
	 * 
	 * @param row
	 *            the feature
	 * @return the fields, may be empty
	 */
	private Set<SimpleField> getFields(Row row) {
		Set<SimpleField> rval = new HashSet<>();
		for (SimpleField field : row.getFields()) {
			rval.add(field);
		}
		return rval;
	}

	/**
	 * @param schema
	 */
	public void add(Schema schema) {
		if (schema == null) {
			throw new IllegalArgumentException(
					"schema should never be null");
		}
		if (schema.getOidField() == null) {
			schema.put(oid);
		}
		schemata.put(schema.getId(), schema);
	}
	
	/**
	 * Get bounding area if the features are geometry
	 * @param key the key, assumed not {@code null}
	 * @return the bounding area, or {@code null} if the features aren't
	 * geometric.
	 */
	public Geodetic2DBounds getBounds(FeatureKey key) {
		return boundingBoxes.get(key);
	}

	/**
	 * Close any open streams.
	 * @throws IOException if an I/O error occurs
	 */
	public void close() throws IOException {
		if (bufferMap != null) {
			IOException exception = null;
			for (ObjectBuffer buffer : bufferMap.values()) {
				try {
					buffer.closeOutputStream();
				} catch(IOException ioe) {
					exception = ioe;
				}
			}
			if (exception != null) throw exception;
		}
	}

	/**
	 * Cleanup deletes the temporary files and resets all the data structures.
	 * @throws IOException if an I/O error occurs
	 */
	public void cleanup() throws IOException {
		IOException exception = null;
		if (bufferMap != null) {
			for (ObjectBuffer buffer : bufferMap.values()) {
				try {
					buffer.close();
				} catch(IOException ioe) {
					exception = ioe;
				}
			}
		}
		schemata = new HashMap<>();
		internalSchema = new HashMap<>();
		bufferMap = new HashMap<>();
		boundingBoxes = new HashMap<>();
		currentKey = null;
		if (exception != null) throw exception;
	}
}
