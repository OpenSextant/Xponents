/****************************************************************************************
 *  DocumentType.java
 *
 *  Created: Jan 28, 2009
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
package org.opensextant.giscore;

import org.opensextant.giscore.data.DocType;
import org.opensextant.giscore.output.IGISOutputStream;

/**
 * Values to identify the various formats to the framework. Extend this list
 * as new formats are added.
 * 
 * @author DRAND
 */
public enum DocumentType {
	/* Google's KML Format */
	KML(false,false),
	/* Google's KMZ Format */
	KMZ(true,true),
	/* An ESRI format */
	Shapefile(true,false),
	/* Access GDB output, an ESRI format */
	PersonalGDB(true,false),
	/* File GDB output, an ESRI format */
	FileGDB(true,false),
	/* SDE - an ESRI enterprise geodatabase */
	SDE(false,false),
	/* An xml interchange format for ESRI Geodatabase data */
	XmlGDB(false,false),
	/* Delimiter separated values, only contains the data from the fields, no geometry */
	CSV(false,false),
    /* GeoRSS-Simple Format */
	GeoRSS(false,false),
	/* GeoAtom with embedded extended data */
	// GeoAtom(false,false),
	/* OGC Well Known Text format */
	WKT(false, false)
	;
	
	private final DocType dt;
	
	DocumentType(boolean reqZip, boolean reqZipEntry) {
		dt = new DocType(name(), reqZip, reqZipEntry);
	}
	
	/**
	 * @return {@code true} if this kind of type requires a zip output
	 * stream for the {@link IGISOutputStream}. 
	 */
	public boolean requiresZipStream() {
		return dt.requiresZipStream();
	}
	
	/**
	 * @return {@code true} if there must be a zip entry in the stream
	 * created before opening the {@link IGISOutputStream}. 
	 */
	public boolean requiresZipEntry() {
		return dt.requiresZipEntry();
	}
	
	/**
	 * @return the internal doc type object
	 */
	public DocType getDocType() {
		return dt;
	}
}
