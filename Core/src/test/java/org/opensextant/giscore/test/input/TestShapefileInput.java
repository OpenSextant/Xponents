/****************************************************************************************
 *  TestShapefileInput.java
 *
 *  Created: Jul 28, 2009
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
package org.opensextant.giscore.test.input;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.zip.ZipInputStream;

import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.IAcceptSchema;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.input.shapefile.ShapefileInputStream;
import org.opensextant.giscore.input.shapefile.SingleShapefileInputHandler;
import static org.junit.Assert.*;

/**
 * Test single shapefile reader
 * 
 * @author DRAND
 */
public class TestShapefileInput {
	public static final File shpdir = new File("data/shape");
	
	/* @Test */public void testReadShpDirectly() throws Exception {
		FileInputStream is = new FileInputStream(new File(shpdir, "afghanistan.shp"));
		ShapefileInputStream sis = new ShapefileInputStream(is, new Object[0]);
		assertNotNull(sis);
		IGISObject ob;
		while((ob = sis.read()) != null) {
			if (ob instanceof Feature) {
				Feature f = (Feature) ob;
				Geometry geo = f.getGeometry();
				assertTrue(geo instanceof MultiPolygons);
			}
		}
	}

	/* @Test */public void testReadShpDirectly2() throws Exception {
		FileInputStream is = new FileInputStream(new File(shpdir, "linez.shp"));
		ShapefileInputStream sis = new ShapefileInputStream(is, new Object[0]);
		assertNotNull(sis);
		IGISObject ob;
		while((ob = sis.read()) != null) {
			if (ob instanceof Feature) {
				Feature f = (Feature) ob;
				Geometry geo = f.getGeometry();
				assertTrue(geo instanceof Line);
			}
		}
	}

	@Test(expected=IOException.class)
	public void testBadStream() throws Exception {
		new ShapefileInputStream(new ByteArrayInputStream(new byte[0]), new Object[0]);
	}
	
	@Test(expected=IOException.class)
	public void testBadStream2() throws Exception {
		new ShapefileInputStream(new ByteArrayInputStream("not a shape file".getBytes()), new Object[0]);
	}

	/* @Test */public void testErrorcase1() throws Exception {
		doTest("Point File Test_Point File Test", Point.class);
	}

	/* @Test */public void testPoints() throws Exception {
		doTest("points", Point.class);
	}

	/* @Test */public void testPointz() throws Exception {
		doTest("pointz", Point.class);
	}
	
	/* @Test */public void testLines() throws Exception {
		doTest("lines", Line.class);
	}

	/* @Test */public void testMultilines() throws Exception {
		doTest("multilines", MultiLine.class);
	}
	
	/* @Test */public void testMultipoint() throws Exception {
		doTest("multipoint", MultiPoint.class);
	}

	/* @Test */public void testMultipolys() throws Exception {
		doTest("multipolys", MultiPolygons.class);                                                              
	}
	
	/* @Test */public void testMultipolyz() throws Exception {
		doTest("multipolyz", MultiPolygons.class);
	}

	/* @Test */public void testMultirings() throws Exception {
		doTest("multirings", MultiPolygons.class);
	}
	
	/* @Test */public void testMultiringz() throws Exception {
		doTest("multiringz", MultiPolygons.class);
	}

	/* @Test */public void testPolys() throws Exception {
		doTest("polys", Polygon.class);
	}
	
	/* @Test */public void testPolyz() throws Exception {
		doTest("polyz", MultiPolygons.class);
	}
   
	/* @Test */public void testRings() throws Exception {
		doTest("rings", LinearRing.class);
	}

	/* @Test */public void testRingz() throws Exception {
		doTest("ringz", LinearRing.class);
	}
	
	/* @Test */public void testAfghanistan() throws Exception {
		SingleShapefileInputHandler handler = new SingleShapefileInputHandler(shpdir, "afghanistan");
		Schema sh = (Schema) handler.read();
		Feature shape = (Feature) handler.read();
		assertNotNull(shape);
		assertTrue(shape.getGeometry() instanceof MultiPolygons);
		IGISObject next = handler.read();
		assertNull(next);
	}
	
	/* @Test */public void testShapefileInputStream() throws Exception {
		IAcceptSchema test = new IAcceptSchema() {
			@Override
			public boolean accept(Schema schema) {
				return schema.get("today") != null;
			}
		};
		
		IGISInputStream stream = GISFactory.getInputStream(DocumentType.Shapefile, shpdir, test);
		while(stream.read() != null) {
			// No body
		}
	}
	
	/* @Test */public void testShapefileInputStream2() throws Exception {
		IGISInputStream stream = GISFactory.getInputStream(DocumentType.Shapefile, shpdir);
		while(stream.read() != null) {
			// No body
		}
	}
	
	/* @Test */public void testShapefileInputStream3() throws Exception {
		FileInputStream fis = new FileInputStream(new File(shpdir, "testLayersShp.zip"));
		ZipInputStream zis = new ZipInputStream(fis);
		IGISInputStream stream = GISFactory.getInputStream(DocumentType.Shapefile, zis);
		
		IGISObject ob;
		while((ob = stream.read()) != null) {
			System.out.println("(Zip) read: " + ob);
		}
	}

	private void doTest(String file, Class geoclass) throws URISyntaxException, IOException {
		System.out.println("Test " + file);
		SingleShapefileInputHandler handler = new SingleShapefileInputHandler(shpdir, file);
		try {
			IGISObject ob = handler.read();
			assertTrue(ob instanceof Schema);
			int count = 0;
			while((ob = handler.read()) != null) {
				assertTrue(ob instanceof Feature);
				Feature feat = (Feature) ob;
				assertNotNull(feat.getGeometry());
				assertTrue(geoclass.isAssignableFrom(feat.getGeometry().getClass()));
				count++;
			}
			assertTrue(count > 0);
			System.out.println(" count=" + count);
		} finally {
			handler.close();
		}
	}
}
