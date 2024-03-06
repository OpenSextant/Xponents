/****************************************************************************************
 *  FactoryDocumentTypeRegistry.java
 *
 *  Created: May 2, 2013
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
package org.opensextant.giscore.data;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.IAcceptSchema;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.input.csv.CsvInputStream;
import org.opensextant.giscore.input.gdb.FileGdbInputStream;
import org.opensextant.giscore.input.kml.KmlInputStream;
import org.opensextant.giscore.input.shapefile.ShapefileInputStream;
import org.opensextant.giscore.input.wkt.WKTInputStream;
import org.opensextant.giscore.output.IContainerNameStrategy;
import org.opensextant.giscore.output.csv.CsvOutputStream;
import org.opensextant.giscore.output.gdb.FileGdbOutputStream;
import org.opensextant.giscore.output.gdb.XmlGdbOutputStream;
import org.opensextant.giscore.output.kml.KmlOutputStream;
import org.opensextant.giscore.output.kml.KmzOutputStream;
import org.opensextant.giscore.output.shapefile.PointShapeMapper;
import org.opensextant.giscore.output.shapefile.ShapefileOutputStream;
import org.opensextant.giscore.output.wkt.WKTOutputStream;

/**
 * This class contains the statically intialized registry for GIScore. It will
 * also be accessed by any extension library that is loaded to extend GIScore.
 * 
 * The library provides a specific hook that is executed before the static
 * initializer. This must be called before the initializer runs if the extensions
 * are meant to override existing stream implementations.
 * 
 * It should be possible to find this via Spring's bean autowire mechanism or 
 * other mechanisms that can find the @Resource annotation. The bean name is
 * "giscore_registry".
 * 
 * @author DRAND
 */
// Deprecated use -- exposing Resource for Spring.
// @Resource(name = "giscore_registry")
public class FactoryDocumentTypeRegistry {
	private static final Map<DocType, DocumentTypeRegistration> ms_register = new LinkedHashMap<>(20);

	public static synchronized void put(DocType dt, DocumentTypeRegistration registration) {
		ms_register.put(dt, registration);
	}
	
	public static synchronized DocumentTypeRegistration get(DocType dt) {
		return ms_register.get(dt);
	}
	
	public static synchronized DocumentTypeRegistration get(DocumentType dt) {
		return ms_register.get(dt.getDocType());
	}
	
	
	public static synchronized void setExtraRegistrations(List dt_registration_pairs) {
		Iterator iter = dt_registration_pairs.iterator();
		while(true) {
			DocType dt = (DocType) iter.next();
			DocumentTypeRegistration reg = (DocumentTypeRegistration) iter.next();
			if (dt == null || reg == null) break;
			ms_register.put(dt, reg);
		}
	}
	
	static {
		DocumentTypeRegistration reg = new DocumentTypeRegistration(DocumentType.KML);
		reg.setInputStreamClass(KmlInputStream.class);
		reg.setOutputStreamArgs(new Class[] { String.class });
		reg.setOutputStreamArgsRequired(new boolean[] { false });
		reg.setOutputStreamClass(KmlOutputStream.class);
		FactoryDocumentTypeRegistry.put(DocumentType.KML.getDocType(), reg);
		
		reg = new DocumentTypeRegistration(DocumentType.KMZ);
		reg.setOutputStreamClass(KmzOutputStream.class);
		reg.setOutputStreamArgs(new Class[] { String.class });
		reg.setOutputStreamArgsRequired(new boolean[] { false });
		FactoryDocumentTypeRegistry.put(DocumentType.KMZ.getDocType(), reg);
		
		reg = new DocumentTypeRegistration(DocumentType.Shapefile);
		reg.setInputStreamClass(ShapefileInputStream.class);
		reg.setInputStreamArgs(new Class[] { IAcceptSchema.class });
		reg.setInputStreamArgsRequired(new boolean[] { false });
		reg.setOutputStreamClass(ShapefileOutputStream.class);
		reg.setOutputStreamArgs(new Class[] { File.class,
				IContainerNameStrategy.class, PointShapeMapper.class });
		reg.setOutputStreamArgsRequired(new boolean[] { true, false, false });
		reg.setHasFileCtor(true);
		FactoryDocumentTypeRegistry.put(DocumentType.Shapefile.getDocType(), reg);
		
		reg = new DocumentTypeRegistration(DocumentType.FileGDB);
		reg.setInputStreamClass(FileGdbInputStream.class);
		reg.setInputStreamArgs(new Class[] { IAcceptSchema.class });
		reg.setInputStreamArgsRequired(new boolean[] { false });
		reg.setOutputStreamClass(FileGdbOutputStream.class);
		reg.setOutputStreamArgs(new Class[] { File.class, IContainerNameStrategy.class });
		reg.setOutputStreamArgsRequired(new boolean[] { false, false });
		reg.setHasFileCtor(true);
		FactoryDocumentTypeRegistry.put(DocumentType.FileGDB.getDocType(), reg);
		
		reg = new DocumentTypeRegistration(DocumentType.XmlGDB);
		reg.setOutputStreamClass(XmlGdbOutputStream.class);
		FactoryDocumentTypeRegistry.put(DocumentType.XmlGDB.getDocType(), reg);
		
		reg = new DocumentTypeRegistration(DocumentType.CSV);
		reg.setInputStreamClass(CsvInputStream.class);
		reg.setInputStreamArgs(new Class[] { Schema.class, String.class, 
				Character.class, Character.class });
		reg.setInputStreamArgsRequired(new boolean[] { false, false, false, false });
		reg.setOutputStreamClass(CsvOutputStream.class);
		reg.setOutputStreamArgs(new Class[] { String.class, Character.class, 
			Character.class, Boolean.class });
		reg.setOutputStreamArgsRequired(new boolean[] { false, false, false, false });
		reg.setHasFileCtor(true);
		FactoryDocumentTypeRegistry.put(DocumentType.CSV.getDocType(), reg);

		reg = new DocumentTypeRegistration(DocumentType.WKT);
		reg.setInputStreamClass(WKTInputStream.class);
		reg.setOutputStreamClass(WKTOutputStream.class);
		FactoryDocumentTypeRegistry.put(DocumentType.WKT.getDocType(), reg);

	}
}



