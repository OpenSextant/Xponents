/****************************************************************************************
 *  Geodatabase.java
 *
 *  Created: Oct 30, 2012
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

import java.io.File;

import org.opensextant.giscore.input.gdb.FileGDBLibraryInitializer;

/**
 * Wraps the FileGDB API Geodatabase Object and presents the 
 * needed operations on the Geodatabase Object so they can be executed
 * from Java.
 * 
 * @author DRAND
 */
public class Geodatabase extends GDB {
	public static final String FEATURE_CLASS = "Feature Class";
	public static final String TABLE = "Table";
	public static final String FOLDER = "Folder";
	public static final String DATASET = "Dataset";
	private File path;
	
	static {
		FileGDBLibraryInitializer.initialize();
		
		initialize();
	}
	
	/**
	 * Ctor - FIXME remove
	 */
	protected Geodatabase() {
		// Empty ctor
	}
	
	/**
	 * Ctor
	 * 
	 * @param pathToGeodatabase the geodatabase, must not be {@code null}
	 * and must exist
	 * @throws IllegalArgumentException if pathToGeodatabase is null
	 */
	public Geodatabase(File pathToGeodatabase) {
		if (pathToGeodatabase == null) {
			throw new IllegalArgumentException("Bad path");
		}
		// test();
		if (pathToGeodatabase.exists()) {
			open(pathToGeodatabase.getAbsolutePath());
		} else {
			create(pathToGeodatabase.getAbsolutePath());
		}
		path = pathToGeodatabase;
	} 
	
	/**
	 * Delete the current Geodatabase, closing it if it is currently 
	 * open.
	 */
	public void delete() {
		closeAndDestroy(path.getAbsolutePath());
	}
	
	/**
	 * Close the geodatabase. If already closed do nothing.
	 */
	public void close() {
		closeAndDestroy(null);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		closeAndDestroy(null);		
		super.finalize();
	}
	
	/**
	 * Allow any initialization for the library to occur
	 */
	private static native void initialize();

	private native void open(String path);
	
	private native void create(String path);
	
	/**
	 * Do low level operation to close the geodatabase. If a path is given then
	 * destroy the geodatabase as well. Last, destroy the memory instance.
	 * @param path the path, if not {@code null} then destroy the given
	 * database.
	 */
	private native void closeAndDestroy(String path);
	
	//public native long test();
	
	public native String[] getDatasetTypes();
	
	public native String getDatasetDefinition(String path, String type);
	
	public native String[] getChildDatasets(String parentPath, String type);
	
	public native String[] getChildDatasetDefinitions(String parentPath, String type);
	
	public native void createFeatureDataset(String featureDatasetDef);
	
	public native Table openTable(String parentpath);
	
	public native Table createTable(String parentpath, String descriptor);
	
	public native void closeTable(Table t);	
}
