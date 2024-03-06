/****************************************************************************************
 *  TestXmlGdbSupport.java
 *
 *  Created: Feb 11, 2009
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
package org.opensextant.giscore.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.MultiPoint;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.test.input.TestKmlInputStream;


/**
 * Test inputting data from some KML sources and outputting to Gdb. 
 * 
 * Add input tests for Gdb once support is done for that format.
 * 
 * @author DRAND
 *
 */
public class TestXmlGdbSupport extends TestGISBase  {
	public static final String TEST = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

	/**
	 * Base path to test directories
	 */
	public static final String base_path = "data/kml/";

	/* @Test */
public void test1() throws Exception {
		InputStream s = TestKmlInputStream.class.getResourceAsStream("7084.kml");
		doXmlTest(s);
	}

	 /* @Test */
	public void test2() throws Exception {
		InputStream s = new FileInputStream(base_path
				+ "Placemark/LineString/straight.kml");
		doXmlTest(s);
	}

	/* @Test */
	public void test3() throws Exception {
		InputStream s = new FileInputStream(base_path
				+ "Placemark/LineString/extruded.kml");
		doXmlTest(s);
	}

	/* @Test */
	public void test4() throws Exception {
		InputStream s = new FileInputStream(base_path
				+ "Placemark/LinearRing/polygon-lr-all-modes.kml");
		doXmlTest(s);
	}

	/* @Test */
	public void test5() throws Exception {
		InputStream s = new FileInputStream(base_path
				+ "Polygon/treasureIsland.kml");
		doXmlTest(s);
	}

	/* @Test */
	public void test6() throws Exception {
		InputStream s = TestKmlInputStream.class
				.getResourceAsStream("KML_sample1.kml");
		doXmlTest(s);
	}
	
	/* @Test */
	public void testMultiPointWithDate() throws Exception {
		File test = createTemp("t", ".xml");
		FileOutputStream fos = new FileOutputStream(test);
		IGISOutputStream os = GISFactory.getOutputStream(DocumentType.XmlGDB, fos);
		
		SimpleField nameid = new SimpleField("nameid");
		nameid.setType(SimpleField.Type.INT);
		SimpleField dtm = new SimpleField("dtm");
		dtm.setType(SimpleField.Type.DATE);
		SimpleField extra = new SimpleField("extra");
		extra.setType(SimpleField.Type.INT);
		SimpleField large = new SimpleField("large", SimpleField.Type.STRING);
		large.setLength(100);
		
		Schema s = new Schema();
		s.put(nameid);
		s.put(dtm);
		s.put(extra);
		s.put(large);
		os.write(s);
		
		Feature f = new Feature();
		f.setSchema(s.getId());
		List<Point> pnts = new ArrayList<>();
		pnts.add(new Point(44.0, 33.0));
		pnts.add(new Point(44.1, 33.4));
		pnts.add(new Point(44.3, 33.3));
		pnts.add(new Point(44.2, 33.1));
		pnts.add(new Point(44.6, 33.2));
		MultiPoint mp = new MultiPoint(pnts);
		f.setGeometry(mp);
		f.putData(nameid, 5);
		f.putData(dtm, new Date());
		f.putData(extra, 123);
		f.putData(large, TEST.substring(0,80));
		os.write(f);
		
		f = new Feature();
		f.setSchema(s.getId());
		pnts = new ArrayList<>();
		pnts.add(new Point(44.5, 33.3));
		pnts.add(new Point(44.6, 33.1));
		pnts.add(new Point(44.7, 33.0));
		pnts.add(new Point(44.4, 33.4));
		pnts.add(new Point(44.2, 33.6));
		mp = new MultiPoint(pnts);
		f.setGeometry(mp);
		f.putData(nameid, 2);
		f.putData(dtm, new Date());
		f.putData(large, TEST.substring(0,180));
		f.putData(extra, null);
		os.write(f);
		
		os.close();
		IOUtils.closeQuietly(fos);
	}
	
	public void doXmlTest(InputStream is) throws IOException {
		try (IGISInputStream gisis = GISFactory.getInputStream(DocumentType.KML, is)) {
			doTest(gisis);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}
	
	public void doTest(IGISInputStream is) throws IOException {
		File test = createTemp("t", ".xml");
		FileOutputStream fos = new FileOutputStream(test);
		IGISOutputStream os = GISFactory.getOutputStream(DocumentType.XmlGDB, fos);
		for(IGISObject object = is.read(); object != null; object = is.read()) {
			os.write(object);
		}
		os.close();
		IOUtils.closeQuietly(fos);
	}
}
