/****************************************************************************************
 *  FileGdbOutputStream.java
 *
 *  Created: Dec 18, 2012
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2012
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
package org.opensextant.giscore.output.gdb;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.filegdb.ESRIErrorCodes;
import org.opensextant.giscore.filegdb.GDB;
import org.opensextant.giscore.filegdb.Geodatabase;
import org.opensextant.giscore.filegdb.Table;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.output.FeatureKey;
import org.opensextant.giscore.output.IContainerNameStrategy;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.utils.Args;
import org.opensextant.giscore.utils.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is using some mechanics form the parent class which was written
 * for ESRI's XML interchange. It is notable that it avoids setting the 
 * base classes' stream variable to allow the reuse of the parent classes' output
 * streams.
 * <p>
 * The parent class also contains a FeatureSorter which could take a good amount
 * of memory and which is not required for this direct writer. This class writes
 * using the FileGeodatabase API via a native method layer and therefore keeps
 * open pointers to all the used tables and writes directly to the tables. The
 * feature sorter is still needed as it tracks the schemas.
 *
 * @author DRAND
 *
 */
public class FileGdbOutputStream extends XmlGdbOutputStream implements
		IGISOutputStream, FileGdbConstants {

	private static final Logger log = LoggerFactory.getLogger(FileGdbOutputStream.class);

	private final ESRIErrorCodes codes = new ESRIErrorCodes();
	private boolean deleteOnClose;
	private final IContainerNameStrategy containerNameStrategy;
	private OutputStream outputStream;
	private final File outputPath;
	private Geodatabase database;
	private final Map<String, Table> tables = new HashMap<>();
	private final AtomicInteger nid = new AtomicInteger();

	/*
	 * Maps the schema name to the schema. The schemata included are both
	 * defined schemata as well as implied or inline schemata that are defined
	 * with their data.
	 */
	//private Map<URI, Schema> schemata = new HashMap<URI, Schema>();

	/*
	 * Maps a set of simple fields, derived from inline data declarations to a
	 * schema. This is used to gather like features together. THe assumption is
	 * that we will see consistent elements between features.
	 */
	//private Map<Set<SimpleField>, Schema> internalSchema;

	private ArrayList<Object> outputList;

	/**
	 * Ctor
	 * 
	 * @param stream
	 *            the output stream to write the resulting GDB into, never
	 *            {@code null}.
	 * 
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws IllegalArgumentException
	 * 				if stream argument is null
	 * @throws IllegalStateException
	 * 				if underlying ESRI FileGDB API is not present or misconfigured
	 */
	public FileGdbOutputStream(OutputStream stream, Object[] args) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException("stream should never be null");
		}
		Args argv = new Args(args);
		File path = (File) argv.get(File.class, 0);
		IContainerNameStrategy containerNameStrategy = (IContainerNameStrategy) argv.get(IContainerNameStrategy.class, 1);
		
		if (path == null || !path.getParentFile().exists()) {
			deleteOnClose = true;
			File temp = new File(System.getProperty("java.io.tmpdir"));
			long t = System.currentTimeMillis();
			path = new File(temp, "result" + t + ".gdb");
		}
		if (containerNameStrategy == null) {
			this.containerNameStrategy = new BasicContainerNameStrategy();
		} else {
			this.containerNameStrategy = containerNameStrategy;
		}
		outputStream = stream;
		outputPath = path;
		try {
			database = new Geodatabase(outputPath);
		} catch(Exception ex) { 
			codes.rethrow(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws IllegalStateException
	 * 				if underlying ESRI FileGDB API throws an exception
	 */
	@Override
	public void close() throws IOException {
		try {
			// close tables
			for (Table table : tables.values()) {
				table.close();
			}
			// close geodatabase
			database.close();

			if (outputStream != null) {
				// zip and stream
				ZipUtils.outputZipComponents(outputPath.getName(), outputPath,
						(ZipOutputStream) outputStream);
			}

		} catch(Exception ex) { 
			codes.rethrow(ex);
		} finally {

			if (deleteOnClose && outputPath.exists()) {
				deleteDirContents(outputPath);
				if (!outputPath.delete()) outputPath.deleteOnExit();
			}

			// Close original outputStream
			if (outputStream != null) {
				IOUtils.closeQuietly(outputStream);
				outputStream = null;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws IllegalStateException
	 * 				if underlying ESRI FileGDB API throws an exception
	 */
	@Override
	public void write(IGISObject object) {
		object.accept(this);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws IllegalStateException
	 * 				if underlying ESRI FileGDB API throws an exception
	 */
	@Override
	public void visit(Row row) {
		try {
			String fullpath = getFullPath();
			FeatureKey featureKey = new FeatureKey(sorter.getSchema(row), fullpath,
					null, row.getClass());
			checkAndRegisterKey(fullpath, featureKey);
			Table table = tables.get(fullpath);
			if (table == null) {
				String descriptor = createDescriptor(featureKey, false, row);
				table = database.createTable(getParentPath(), descriptor);
				tables.put(fullpath, table);
			}
			org.opensextant.giscore.filegdb.Row tablerow = table.createRow();
			transferData(tablerow, row);
			table.add(tablerow);
		} catch (Exception e) {
			codes.rethrow(e);
		}
	}

	private void transferData(org.opensextant.giscore.filegdb.Row tablerow, Row row) {
		Map<String,Object> datamap = new HashMap<>();
		for(SimpleField field : row.getFields()) {
			Object data = row.getData(field);
			if (data == null) {
				datamap.put(field.getName(), GDB.NULL_OBJECT);
			} else {
				datamap.put(field.getName(), data);
			}
		}
		tablerow.setAttributes(datamap);
	}

	/**
	 * This row has inline data in the extended data, so extract the field names
	 * and create a set of such fields.
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

	// On I/O of Geo data from the Java layer:
	// 
	// For all representations there is an intial Short which holds the
	// type from Shape Types. The point entries are either two or three
	// doubles depending on whether the z-axis has a value.
	//
	//		  Short: # (0 = Point, 1 = MultiPoint, 2 = Line/Polyline, 3 = Ring/Polygon)
	//		  Boolean: hasz (true if 3D points)
	//		  Integer: npoints
	//		  Integer: nparts
	//		  part array (may be empty if nparts == 0)
	//		  Double long, lat, zelev 

	/**
	 * {@inheritDoc}
	 *
	 * @throws IllegalStateException
	 * 				if underlying ESRI FileGDB API throws an exception
	 */
	@Override
	public void visit(Feature feature) {
		if (feature.getGeometry() == null) return; // Not really a feature, skip
		try {
			String fullpath = getFullPath();
			FeatureKey featureKey = new FeatureKey(sorter.getSchema(feature), fullpath,
					feature.getGeometry().getClass(), feature.getClass());
			checkAndRegisterKey(fullpath, featureKey);
			Table table = tables.get(fullpath);
			if (table == null) {
				String descriptor = createDescriptor(featureKey, true, feature);
				table = Table.createTable(database, "\\", descriptor);
				tables.put(fullpath, table);
			}
			org.opensextant.giscore.filegdb.Row tablerow = table.createRow();
			transferData(tablerow, feature);
			// Encode the geometry
			outputList = new ArrayList<>(10);
			Geometry geo = feature.getGeometry();
			geo.accept(this);
			tablerow.setGeometry(outputList.toArray());
			outputList = null;
			table.add(tablerow);
		} catch (Exception e) {
			codes.rethrow(e);
		}
	}

	private String getParentPath() {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < path.size() - 1; i++) {
			sb.append("\\");
			sb.append(path.get(i));
		}
		if (sb.toString().length() == 0) {
			sb.append("\\");
		}
		return sb.toString().replaceAll("\\s+", "_");
	}

	/**
	 * If the feature or row is presented without a container, we need to 
	 * derive a pseudo container name from the schema instead.
	 * 
	 * @param row
	 */
	private String getNameFromRow(Row row) {
		String rval;
		if (row.getSchema() != null) {
			rval = row.getSchema().toASCIIString();
			rval = rval.replaceAll("[\\p{Punct}+\\s+]", "_");
		} else {
			rval = "feature_" + nid.incrementAndGet();
		}
		if (row instanceof Feature)  {
			Feature f = (Feature) row;
			rval += "_";
			rval += f.getGeometry().getClass().getSimpleName();
		}
		return rval;
	}

	private String createDescriptor(FeatureKey featureKey, boolean isFeature, Row row)
			throws XMLStreamException, IOException {
		stream = new ByteArrayOutputStream(2000);
		init(stream, "UTF8");
		String datasetname = containerNameStrategy.deriveContainerName(path, featureKey);
		writeDataSetDef(featureKey, datasetname, 
				isFeature ? ElementType.FEATURE_CLASS : ElementType.TABLE);
		writer.writeEndDocument();
		closeWriter();
		return ((ByteArrayOutputStream) stream).toString(StandardCharsets.UTF_8);
	}

	private void closeWriter() throws XMLStreamException, IOException {
		writer.flush();
		writer.close();
		stream.close();
	}

	/** {@inheritDoc} */
	@Override
	public void visit(Point point) {
		if (hasNoPoints(point)) return;
		outputPartsAndPoints(shapePoint, shapePointZ, point);
	}

	/** {@inheritDoc} */
	@Override
	public void visit(MultiPoint multiPoint) {
		if (hasNoPoints(multiPoint)) return;
		
		outputPartsAndPoints(shapeMultipoint, shapeMultipointZ, multiPoint);
	}
	
	public boolean hasNoPoints(Geometry geo) {
		if (geo == null) return true;
		List<Point> points = geo.getPoints();
		return points.isEmpty();
	}

	/** {@inheritDoc} */
	@Override
	public void visit(Line line) {
		if (hasNoPoints(line)) return;
		outputPartsAndPoints(shapePolyline, shapePolylineZ, line);
	}

	/** {@inheritDoc} */
	@Override
	public void visit(GeometryBag geobag) {
		log.debug("Geometry Bag is not supported by FileGDB (at least at this time)");
	}

	/** {@inheritDoc} */
	@Override
	public void visit(MultiLine multiLine) {
		if (hasNoPoints(multiLine)) return;
		outputPartsAndPoints(shapePolyline, shapePolylineZ, multiLine);
	}
	
	/**
	 * Output common information for all complex geometries
	 * @param geo
	 */
	private void outputPartsAndPoints(Short type, Short type3d, Geometry geo) {
		boolean hasz = geo.getCenter() instanceof Geodetic3DPoint;
		GeoOffsetVisitor pov = new GeoOffsetVisitor();
		
		geo.accept(pov);
		int pc = pov.getPartCount();
		outputList.add(hasz ? type3d : type);
		outputList.add(hasz);
		outputList.add(pov.getTotal());
		outputList.add(pc);
		for(int i = 0; i < pc; i++) {
			outputList.add(pov.getOffsets().get(i));
		}
		List<Point> pts = geo.getPoints();
		Geodetic2DPoint center;
		for(Point p : pts) {
			center = p.getCenter();
			outputList.add(center.getLongitudeAsDegrees());
			outputList.add(center.getLatitudeAsDegrees());
			if (hasz) {
				outputList.add(((Geodetic3DPoint) center).getElevation());
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visit(LinearRing ring) {
		if (hasNoPoints(ring)) return;
		outputPartsAndPoints(shapePolygon, shapePolygonZ, ring);
	}

	/** {@inheritDoc} */
	@Override
	public void visit(MultiLinearRings rings) {
		for(LinearRing r : rings.getLinearRings()) {
			r.accept(this);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void visit(Polygon polygon) {
		if (hasNoPoints(polygon)) return;
		outputPartsAndPoints(shapePolygon, shapePolygonZ, polygon);
	}

	/** {@inheritDoc} */
	@Override
	public void visit(MultiPolygons polygons) {
		for(Polygon p : polygons.getPolygons()) {
			p.accept(this);
		}
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.output.esri.XmlGdbOutputStream#visit(org.opensextant.giscore.events.ContainerStart)
	 */
	@Override
	public void visit(ContainerStart containerStart) {
		if (containerStart.getName() == null) {
			containerStart.setName("");
		}
		super.visit(containerStart);
	}	
}
