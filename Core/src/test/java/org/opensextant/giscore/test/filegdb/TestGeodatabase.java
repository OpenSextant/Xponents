/****************************************************************************************
 *  TestGeodatabase.java
 *
 *  Created: Oct 3, 2012
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
package org.opensextant.giscore.test.filegdb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.opensextant.giscore.filegdb.EnumRows;
import org.opensextant.giscore.filegdb.Geodatabase;
import org.opensextant.giscore.filegdb.Row;
import org.opensextant.giscore.filegdb.Table;
import org.opensextant.giscore.geometry.Geometry;
import static org.junit.Assert.*;

public class TestGeodatabase {
	static Random rand;	

	public static final String DB = "data/gdb/Shapes.gdb";
	public static File path;
	public static Geodatabase test_db;
	
	public static File createTempDir(File parent, String prefix, String suffix) {
		while(true) {
			File test = new File(parent, prefix + Math.abs(rand.nextInt(1000000)) + "." + suffix);
			if (test.exists()) continue;
			return test;
		}
	}
	
	static {
		rand = new Random(System.currentTimeMillis());
		path = createTempDir(new File("c:/temp"), "temp", "gdb");
	}
	
	// @BeforeClass
	public static void createGeodatabase() throws IOException {
		test_db = new Geodatabase(path);
		assertTrue(test_db.isValid());
	}
	
	// @AfterClass
	public static void destroyGeodatabase() throws IOException {
		test_db.delete();
	}
	
	// @Test
	public void testGeoCreateAndDelete() throws IOException {
		File path = createTempDir(new File("c:/temp"), "temp", "gdb");
		Geodatabase db = new Geodatabase(path);
		assertTrue(db.isValid());
		db.delete();
	}
	
	// @Test
	public void testGeodatabaseOpenAndClose() {
		// Open the database
		Geodatabase db = new Geodatabase(new File(DB));
		
		assertTrue(db.isValid());
		
		try {
			db.close();
		} catch(Exception e) {
			fail();
		}
	}

	// @Test
	public void testGetDatasetChildren() throws Exception {
		Geodatabase db = new Geodatabase(new File(DB));
		
		String children[] = db.getChildDatasets("\\", Geodatabase.FEATURE_CLASS);
		assertTrue(children.length > 0);
		db.close();
	}
	
	// @Test
	public void testGetDatabaseTypes() throws Exception {
		Geodatabase db = new Geodatabase(new File(DB));
		
		String types[] = db.getDatasetTypes();
		assertTrue(types.length > 0);
		db.close();
	}

	// @Test
	public void testGetDatasetDef() throws Exception {
		Geodatabase db = new Geodatabase(new File(DB));
		
		String docs[] = db.getChildDatasetDefinitions("\\", Geodatabase.FEATURE_CLASS);
		assertTrue(docs.length > 0);
		db.close();
	}
	
	// @Test
	public void testTableCreate() throws Exception {
		InputStream t1 = this.getClass().getResourceAsStream("XMLsamples/Streets.xml");
		StringWriter sw = new StringWriter();
		IOUtils.copy(t1, sw);
		String doc = sw.toString();
		
		Table table = Table.createTable(test_db, "", doc);
		
		assertTrue(table != null);
		
		// Create a row, setup data and add it to the table
		Row r = table.createRow();
		Map<String, Object> data = new HashMap<>();
		data.put("TYPE", "interstate");
		data.put("LaneCount", Short.valueOf((short) 6));
		data.put("SpeedLimit", Long.valueOf(65));
		r.setAttributes(data);
		List<Object> geo = new ArrayList<>();
		geo.add(3); // Polyline
		geo.add(false); // hasZ
		geo.add(2); // Npoints 
		geo.add(1); // Nparts
		geo.add(2);
		geo.add(1.0);
		geo.add(2.0);
		geo.add(2.0);
		geo.add(5.0);
		r.setGeometry(geo.toArray());
		table.add(r);
		table.close();
	}
	
	// @Test
	public void testTableRead() throws Exception {
		// Open the named table and get the given row	
		Geodatabase test_db = new Geodatabase(new File("data/gdb/EH_20090331144528.gdb"));
		Table table = Table.openTable(test_db, "\\EHFC_20090331144528");
		assertTrue(table != null);
		EnumRows enumrows = table.enumerate();
		int count = 0;
		while(enumrows.hasNext()) {
			Row row = enumrows.next();
			assertNotNull(row);
			assertNotNull(row.getOID());
			Map<String, Object> attrs = row.getAttributes();
			assertNotNull(attrs);
			assertNotNull(attrs.get("lat"));
			assertNotNull(attrs.get("lon"));
			assertNotNull(attrs.get("precision"));
			assertNotNull(attrs.get("lpath"));
			Geometry geo = row.getGeometry();
			assertNotNull(geo);
			count++;
		}
		System.err.println("Total rows = " + count);
		table.close();
	}
	

}
