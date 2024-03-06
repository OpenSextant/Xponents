/****************************************************************************************
 *  TestSmallDiskBufferedShapefileCase.java
 *
 *  Created: Oct 28, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.ContainerEnd;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.IGISOutputStream;

import static org.opensextant.giscore.test.TestSupport.OUTPUT;
import static org.opensextant.giscore.test.TestSupport.nextDouble;

/**
 * Tests use of ObjectBuffer for very small in-memory buffer to debug problem
 * 
 * @author DRAND
 */
public class TestSmallDiskBufferedShapefileCase {

	@Test public void testSmallCase() throws Exception {
		final long timestamp = System.currentTimeMillis();
		File outputDir = new File(OUTPUT + "/tst/tmp" + timestamp);
		outputDir.mkdirs();
		File output = new File(OUTPUT + "/tst/shapes" + timestamp + ".zip");
		OutputStream os = new FileOutputStream(output);
		ZipOutputStream zos = new ZipOutputStream(os);
		GISFactory.inMemoryBufferSize.set(2000);
		IGISOutputStream gos = GISFactory.getOutputStream(DocumentType.Shapefile, zos, outputDir);
		gos.write(new DocumentStart(DocumentType.Shapefile));
		gos.write(new ContainerStart());
		Schema schema = new Schema(new URI("urn:test"));
		SimpleField id = new SimpleField("testid");
		id.setLength(10);
		schema.put(id);
		gos.write(schema);
		for(int i = 0; i < 4000; i++) {
			Feature f = new Feature();
			f.putData(id, "id " + i);
			f.setSchema(schema.getId());
			double lat = nextDouble() * 5.0 + 15.0;
			double lon = nextDouble() * 5.0 + 25.0;
			Point point = new Point(lat, lon);
			f.setGeometry(point);
			gos.write(f);
		}
		gos.write(new ContainerEnd());
		gos.close();
		zos.flush();
		zos.close();
	}
	
//	@Test public void testSmallCase() throws Exception {
//		File output = File.createTempFile("test", ".zip");
//		OutputStream os = new FileOutputStream(output);
//		ZipOutputStream zos = new ZipOutputStream(os);
//		File outputdir = new File("c:/temp/t" + System.currentTimeMillis());
//		outputdir.mkdirs();
//		IGISOutputStream gos = GISFactory.getOutputStream(DocumentType.Shapefile, zos, outputdir);
//		gos.write(new DocumentStart(DocumentType.Shapefile));
//		gos.write(new ContainerStart());
//		
//		SimpleField f1 = new SimpleField("A", Type.INT);
//		SimpleField f2 = new SimpleField("B", Type.DOUBLE);
//		SimpleField f3 = new SimpleField("C", Type.STRING);
//		for(int i = 0; i < 15; i++) {
//			Feature feature = new Feature();
//			double lat = RandomUtils.nextDouble() * 5.0 + 15.0;
//			double lon = RandomUtils.nextDouble() * 5.0 + 25.0;
//			Point point = new Point(lat, lon);
//			feature.setGeometry(point);
//			feature.putData(f1, RandomUtils.nextInt(5));
//			feature.putData(f2, RandomUtils.nextDouble() * 100.0);
//			feature.putData(f3, "str" + RandomUtils.nextInt());
//			gos.write(feature);
//		}
//		gos.write(new ContainerEnd());
//		gos.close();
//		zos.flush();
//		zos.close();
//	}
}
