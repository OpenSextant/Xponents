/****************************************************************************************
 *  FileGdbInputStream.java
 *
 *  Created: Jan 3, 2013
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2013
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
package org.opensextant.giscore.input.gdb;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.IAcceptSchema;
import org.opensextant.giscore.events.ContainerEnd;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.filegdb.EnumRows;
import org.opensextant.giscore.filegdb.Geodatabase;
import org.opensextant.giscore.filegdb.Row;
import org.opensextant.giscore.filegdb.Table;
import org.opensextant.giscore.filegdb.Table.FieldInfo;
import org.opensextant.giscore.input.GISInputStreamBase;
import org.opensextant.giscore.output.gdb.FileGdbConstants;
import org.opensextant.giscore.utils.Args;

public class FileGdbInputStream extends GISInputStreamBase implements FileGdbConstants {
	
	private class TableState {
		private final boolean hasGeo;
		private int index = -1;
		private final List<String> paths;
		/**
		 * If currentTable is null we're writing the schema for the table or 
		 * feature class. Otherwise we're enumerating the rows or features for
		 * that particular table or feature class. When we've reached the end
		 * of the table we'll set it back to null to trigger the next schema or
		 * to move on to the next set.
		 */
		private Table currentTable;
		private EnumRows rows;
		private Schema currentSchema;
		
		public TableState(boolean hasGeo, List<String> paths) {
			this.hasGeo = hasGeo;
			this.paths = paths;
		}

		/**
		 * We're ready if:
		 * <ul>
		 * <li>We've never touched this table set so the index is negative
		 * <li>There's a row ready for the current path
		 * <li>The incremented index is in the range [0..size-1] so it can 
		 * index one of the paths and we can hand back the schema and rows
		 * </ul>
		 * 
		 * We're definitely not ever ready if there aren't any paths.
		 * 
		 * @return
		 */
		public boolean ready() {
			if (paths.size() == 0) return false;
			int ipo = index + 1;
			
			return index < 0 
					|| (rows != null && rows.hasNext())
					|| (ipo < paths.size() && currentTable == null);	
		}

		/**
		 *
		 * @return
		 * @throws IllegalStateException
		 */
		private IGISObject next() {
			String fcPath;
			if (currentTable == null) {
				index++;
				fcPath = paths.get(index);
				currentSchema = getSchema(fcPath);
				if (acceptor != null && !acceptor.accept(currentSchema)) {
					currentSchema = null;
					return next();
				}
				currentTable = database.openTable(fcPath);
				rows = currentTable.enumerate();
				ContainerStart cs = new ContainerStart("Folder");
				cs.setSchema(currentSchema.getId());
				cs.setName(fcPath);
				addLast(cs);
				return currentSchema;
			} else {
				Row row = rows.next();
				if (row != null) {
					Map<String, Object> data = row.getAttributes();
					org.opensextant.giscore.events.Row gval;
					if (hasGeo) {
						gval = new Feature();
					} else {
						gval = new org.opensextant.giscore.events.Row();
					}
					// Map values to row/feature
					for(String name : currentSchema.getKeys()) {
						Object value = data.get(name);
						gval.putData(currentSchema.get(name), value);
					}
					if (hasGeo) {
						((Feature) gval).setGeometry(row.getGeometry());
					}
					gval.setSchema(currentSchema.getId());
					if (! rows.hasNext()) {
						currentTable = null;
						currentSchema = null;
						rows = null;
						addLast(new ContainerEnd());
					}
					return gval;
				} else {
					// If no rows at all. Does this ever happen?
					currentTable = null;
					currentSchema = null;
					rows = null;
					return new ContainerEnd();
				}
			}
		}

		/**
		 *
		 * @param path
		 * @return
		 * @throws IllegalStateException
		 * 			If the given path violates RFC&nbsp;2396 for URI construction
		 */
		private Schema getSchema(String path) {
			currentTable = database.openTable(path);
			Map<String, FieldInfo> fieldInfo = currentTable.getFieldTypes();
			Schema schema;
			try {
				String name = path.replaceAll("\\\\", "/");
				schema = new Schema(new URI("uri:" + name));
			} catch (URISyntaxException e) {
				throw new IllegalStateException("Unexpected failure due to URI exception", e);
			}
			for(Map.Entry<String,FieldInfo> entry : fieldInfo.entrySet()) {
				final FieldInfo info = entry.getValue();
				if (info.type == 7) continue; // Geometry
				final String name = entry.getKey();
				SimpleField field = new SimpleField(name);
				field.setLength(info.length);
				field.setType(convertFieldTypeToSQLType(info.type));
				field.setRequired(! info.nullable);
				schema.put(name, field);
			}
			schema.setName(path.substring(1));
			return schema;
		}
	}
	
	private final Geodatabase database;
	private final File inputPath;
	private boolean deleteOnClose = false;
	private TableState table;
	private TableState feature;
	private IAcceptSchema acceptor;

	/**
	 *
	 * @param stream
	 * @param args
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws IllegalArgumentException if stream argument is null

	 */
	public FileGdbInputStream(InputStream stream, Object[] args) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException("stream should never be null");
		}
		Args argv = new Args(args);
		IAcceptSchema acceptor = (IAcceptSchema) argv.get(IAcceptSchema.class, 0);
		deleteOnClose = true;
		File temp = new File(System.getProperty("java.io.tmpdir"));
		long t = System.currentTimeMillis();
		String name = "input" + t + ".gdb";
		inputPath = new File(temp, name);
		inputPath.mkdirs();
		
		// The stream better point to zip data
		ZipInputStream zis;
		if (!(stream instanceof ZipInputStream)) {
			zis = new ZipInputStream(stream);
		} else {
			zis = (ZipInputStream) stream;
		}
		
		ZipEntry entry;
		while((entry = zis.getNextEntry()) != null) {
			File entryPath = new File(entry.getName());
			File path = new File(inputPath, entryPath.getName());
			OutputStream os = new FileOutputStream(path);
			IOUtils.copy(zis, os);
			os.close();
		}

		database = new Geodatabase(inputPath);
		init(acceptor);
	}

	/**
	 *
	 * @param path
	 * @param args
	 * @throws IllegalArgumentException if path argument is null or path does not exist
	 */
	public FileGdbInputStream(File path, Object[] args) {
		if (path == null) {
			throw new IllegalArgumentException("path should never be null");
		}
		if (!path.exists()) {
			throw new IllegalArgumentException("path must exist");
		}
		Args argv = new Args(args);
		IAcceptSchema acceptor = (IAcceptSchema) argv.get(IAcceptSchema.class, 0);
		inputPath = path;
		database = new Geodatabase(inputPath);
		
		init(acceptor);
	}
	
	/**
	 * Initialize scanning information for the database by creating the
	 * two table states.
	 * @param acceptor 
	 */
	private void init(IAcceptSchema acceptor) {
		this.acceptor = acceptor;
		feature = new TableState(true, findAllChildren("\\", Geodatabase.FEATURE_CLASS));
		table = new TableState(false, findAllChildren("\\", Geodatabase.TABLE));
	}

	/**
	 * Walk the hierarchy and add all the found paths to the returned
	 * list
	 * 
	 * @param path
	 * @param type
	 * @return
	 */
	private List<String> findAllChildren(String path, String type) {
		String[] children = database.getChildDatasets(path, type);
		List<String> rval = new ArrayList<>();
		for(String child : children) {
			rval.add(child);
			rval.addAll(findAllChildren(path + "\\" + child, type));
		}
		return rval;
	}
	
	@Override
	// @CheckForNull

	public IGISObject read() throws IOException {
		if (hasSaved()) {
			return readSaved();
		}
		if (table.ready()) {
			return table.next();
		} else if (feature.ready()) {
			return feature.next();
		} else {
			return null;
		}
	}
	
	/**
	 * Convert esri types
	 * @param ft
	 * @return
	 */
	private SimpleField.Type convertFieldTypeToSQLType(int ft) {
		switch(ft) {
		case 0: // fieldTypeSmallInteger:
			return SimpleField.Type.SHORT;
		case 1: // fieldTypeInteger:
			return SimpleField.Type.INT;
		case 2: // fieldTypeSingle:
			return SimpleField.Type.FLOAT;
		case 3: // fieldTypeDouble:
			return SimpleField.Type.DOUBLE;
		case 4: // fieldTypeString:
			return SimpleField.Type.STRING;
		case 5: // fieldTypeDate:
			return SimpleField.Type.DATE;
		case 6: // fieldTypeOID:
			return SimpleField.Type.OID;
		case 7: // fieldTypeGeometry:
			return SimpleField.Type.GEOMETRY;
		case 8: // fieldTypeBlob:
			return SimpleField.Type.BLOB;
		case 9: // fieldTypeRaster:
			return SimpleField.Type.IMAGE;
		case 10: // fieldTypeGUID:
			return SimpleField.Type.GUID;
		case 11: // fieldTypeGlobalID:
			return SimpleField.Type.ID;
		case 12: // fieldTypeXML:
			return SimpleField.Type.CLOB;
		default:
			return null;
		}
	}

	@Override
	@NotNull
	public Iterator<Schema> enumerateSchemata() throws IOException {
		return Collections.emptyIterator();
	}

	@Override
	public void close() {
		if (deleteOnClose) {
			inputPath.delete();
		}
	}

}
