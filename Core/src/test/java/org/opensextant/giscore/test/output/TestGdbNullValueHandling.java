/****************************************************************************************
 *  TestGdbNullValueHandling.java
 *
 *  Created: Apr 8, 2009
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
package org.opensextant.giscore.test.output;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.zip.ZipOutputStream;

import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.test.TestGISBase;


/**
 * This isn't so much a test, as a piece of code to create a set of null values
 * so we can see what the XML export looks like for the different types.
 * 
 * @author DRAND
 */
public class TestGdbNullValueHandling extends TestGISBase {
	// @Test
	public void createEmptyValueFileGDB() throws IOException, URISyntaxException {
		File test = createTemp("t", ".zip");
		FileOutputStream fos = new FileOutputStream(test);
		IGISOutputStream os = null;
		ZipOutputStream zos = null;
		zos = new ZipOutputStream(fos);
		os = GISFactory.getOutputStream(DocumentType.FileGDB, zos, createTemp("t",".gdb")); 
		
		Schema ts = new Schema(new URI("#test_schema"));
		
		SimpleField b = new SimpleField("b", Type.BOOL);
		ts.put(b);
		SimpleField dt = new SimpleField("dt", Type.DATE);
		ts.put(dt);
		SimpleField db = new SimpleField("db", Type.DOUBLE);
		ts.put(db);
		SimpleField f = new SimpleField("f", Type.FLOAT);
		ts.put(f);
		SimpleField i1 = new SimpleField("i1", Type.INT);
		ts.put(i1);
		SimpleField s1 = new SimpleField("s1", Type.SHORT);
		ts.put(s1);
		SimpleField str = new SimpleField("str", Type.STRING);
		ts.put(str);
		SimpleField ui = new SimpleField("ui", Type.UINT);
		ts.put(ui);
		SimpleField us = new SimpleField("us", Type.USHORT);
		ts.put(us);
		
		os.write(ts);
		
		Feature feature = new Feature();
		feature.setSchema(new URI("#test_schema"));
		feature.setGeometry(new Point(44.0, 32.0));
		os.write(feature);
		
		for(int i = 0; i < (9*9*9); i++) {
			feature = new Feature();
			feature.setSchema(new URI("#test_schema"));
			feature.putData(b, true);
			feature.putData(dt, new Date());
			feature.putData(db, Math.PI);
			feature.putData(f, (float) Math.E);
			feature.putData(i1, 1);
			feature.putData(s1, 2);
			feature.putData(str, "abc");
			feature.putData(ui, 3);
			feature.putData(us, 4);
			feature.setGeometry(new Point(44.0, 32.0));
			eliminateField(feature, i, ts);
			os.write(feature);	
		}
		
		os.close();
	}

	/**
	 * @param feature 
	 * @param i
	 * @param ts
	 */
	private void eliminateField(Feature feature, int i, Schema ts) {
		int count = ts.getKeys().size();
		Object[] names = ts.getKeys().toArray();
		int first = i % count;
		String firstField = (String) names[first];
		int rem = i / count;
		String secondField = null;
		if (rem > 0) {
			int second = rem % count;
			secondField = (String) names[second];
			rem = rem / count;
		}
		String thirdField = null;
		if (rem > 0) {
			int third = rem % count;
			thirdField = (String) names[third];
			rem = rem / count;
		}
		feature.putData(ts.get(firstField), null);
		if (secondField != null) {
			feature.putData(ts.get(secondField), null);
		}
		if (thirdField != null) {
			feature.putData(ts.get(thirdField), null);
		}
	}
}
