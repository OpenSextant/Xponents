/****************************************************************************************
 *  TestGdbStringTruncation.java
 *
 *  Created: Apr 17, 2009
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.MultiPoint;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.test.TestGISBase;


/**
 * @author DRAND
 *
 */
public class TestGdbStringTruncation extends TestGISBase {
	public static final String TEST = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
	
	// @Test
	public void doTest() throws IOException {
		File test = createTemp("t", ".zip");
		File testgdb = new File(test.getParentFile(), "test.gdb");
		FileOutputStream fos = new FileOutputStream(test);
		ZipOutputStream zos = new ZipOutputStream(fos);
		IGISOutputStream os = GISFactory.getOutputStream(DocumentType.FileGDB, zos, testgdb);

		SimpleField nameid = new SimpleField("nameid");
		nameid.setType(SimpleField.Type.INT);
		SimpleField dtm = new SimpleField("dtm");
		dtm.setType(SimpleField.Type.DATE);
		SimpleField extra = new SimpleField("extra");
		extra.setType(SimpleField.Type.INT);
		SimpleField large = new SimpleField("large", SimpleField.Type.STRING);
		large.setLength(550);
		
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
		
		f = new Feature();
		f.setSchema(s.getId());
		pnts = new ArrayList<>();
		pnts.add(new Point(45.3, 36.4));
		pnts.add(new Point(42.1, 34.6));
		pnts.add(new Point(42.1, 35.6));
		mp = new MultiPoint(pnts);
		f.setGeometry(mp);
		f.putData(nameid, 3);
		f.putData(dtm, new Date());
		f.putData(large, TEST);
		f.putData(extra, null);
		os.write(f);		
		
		os.close();
		IOUtils.closeQuietly(fos);
	}
}
