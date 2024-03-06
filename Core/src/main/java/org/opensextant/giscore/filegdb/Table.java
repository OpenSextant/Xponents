/****************************************************************************************
 *  Table.java
 *
 *  Created: Nov 7, 2012
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
package org.opensextant.giscore.filegdb;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * A table represents a dataset or a feature class
 * 
 * @author DRAND
 *
 */
public class Table extends GDB {
	public static class FieldInfo {
		public int type;
		public int length;
		public boolean nullable;
	}
	
	private Geodatabase db;
	private Map<String, FieldInfo> fieldInfo;
	/**
	 * These hold references to C++ structures and are managed by the native
	 * code. Cleaned up in finalize.
	 */	
	private final long fieldinfo_holder = 0;
	private final long fieldtype_map = 0;
	
	protected Table() {
		// 
	}
	
	/**
	 * @return a fresh new row, ready to be populated. The row is not yet part
	 * of the table. Call {@link #add(Row)} to add the row to the table.
	 */
	public Row createRow() {
		Row row = new Row(this);
		initRow(row);
		return row;
	}
	
	public static Table createTable(Geodatabase db, String parentpath, String descriptor) {
		if (db == null) {
			throw new IllegalArgumentException("db should never be null");
		}
		if (parentpath == null) {
			throw new IllegalArgumentException("parentpath should never be null");
		}
		if (StringUtils.isEmpty(descriptor))
			throw new IllegalArgumentException("descriptor is required");
		else {
			Table table = db.createTable(parentpath, descriptor);
			table.db = db;
			return table;
		}
	}
	
	public static Table openTable(Geodatabase db, String parentpath) {
		if (db == null) {
			throw new IllegalArgumentException("db should never be null");
		}
		if (parentpath == null) {
			throw new IllegalArgumentException("parentpath should never be null");
		}
		Table table = db.openTable(parentpath);
		table.db = db;
		return table;
	}
	
	/**
	 * Add the given row to the table
	 * @param row
	 */
	public native void add(Row row);
	
	/**
	 * @return all the rows in the table as an iterator
	 */
	public EnumRows enumerate() {
		EnumRows rval = enumerate1();
		rval.setTable(this);
		return rval;
	}
	
	/**
	 * @return 
	 */
	public native EnumRows enumerate1();
	
	/**
	 * @return get the field names mapped to their types as a map. The 
	 * field types are from SQL.Types
	 */
	public Map<String, FieldInfo> getFieldTypes() {
		if (fieldInfo == null) {
			Object[] data = getFieldInfo();
			fieldInfo = new HashMap<>(data.length / 4);
			for(int i = 0; i < data.length; i += 4) {
				FieldInfo fi = new FieldInfo();
				String name = (String) data[i];
				fi.type = (Integer) data[i+1];
				fi.length = (Integer) data[i+2];
				fi.nullable = (Boolean) data[i+3];
				fieldInfo.put(name, fi);
			}
		}
		return fieldInfo;
	}
	
	/**
	 * Get field information as an array of values in the following order:
	 * <ul>
	 * <li>name
	 * <li>type (number)
	 * <li>length (number)
	 * <li>nullable (boolean>
	 * </ul>
	 * The type values are from SQL.Types
	 */
	private native Object[] getFieldInfo();
	
	/**
	 * Initialize new row object
	 */
	protected native void initRow(Row row);
	
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}

	/**
	 * Close this table
	 */
	public void close() {
		if (db != null) {
			db.closeTable(this);
			if (isValid()) close1();
			db = null;
		}
	}
	
	/**
	 * Cleanup
	 */
	private native void close1();
}
