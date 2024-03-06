/****************************************************************************************
 *  TestKmlSchemaNull.java
 *
 *  Created: Feb 26, 2009
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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.ContainerEnd;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.test.TestGISBase;


/**
 * Test case where value is null for output of a feature
 * 
 * @author DRAND
 */
public class TestKmlSchemaNull extends TestGISBase {
	@Test public void testFeatureWNullValue() throws Exception {
		File temp = createTemp("test", ".kml");
		OutputStream fos = new FileOutputStream(temp);
		IGISOutputStream os = GISFactory.getOutputStream(DocumentType.KML, fos);
		
		os.write(new ContainerStart(IKml.DOCUMENT));
		String[] names = {"height", "width", "depth"};
		Type[] types = new Type[]{Type.DOUBLE, Type.DOUBLE, Type.DOUBLE};
		Schema schema = createSchema(names, types);
		os.write(schema);
		
		Feature feature;
		Map<String, Object> valuemap = new HashMap<>();
		valuemap.put("height", 1.2);
		valuemap.put("width", 5.0);
		valuemap.put("depth", 3.5);
		feature = createFeature(Point.class, schema, valuemap);
		os.write(feature);
		
		valuemap.clear();
		valuemap.put("height", 4.3);
		valuemap.put("width", 2);
		valuemap.put("depth", 2.5);
		feature = createFeature(Point.class, schema, valuemap);
		os.write(feature);
		
		valuemap.clear();
		valuemap.put("height", 4.3);
		valuemap.put("width", null);
		valuemap.put("depth", 3.5);
		feature = createFeature(Point.class, schema, valuemap);
		os.write(feature);
		
		valuemap.clear();
		valuemap.put("height", null);
		valuemap.put("width", 2);
		valuemap.put("depth", 3.5);
		feature = createFeature(Point.class, schema, valuemap);
		os.write(feature);
		
		valuemap.clear();
		valuemap.put("height", 1);
		valuemap.put("width", 2);
		valuemap.put("depth", null);
		feature = createFeature(Point.class, schema, valuemap);
		os.write(feature);
		os.write(new ContainerEnd());
		
		os.close();
		fos.close();
	}

}
