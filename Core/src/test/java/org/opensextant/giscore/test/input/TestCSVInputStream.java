/****************************************************************************************
 *  TestCSVInputStream.java
 *
 *  Created: Apr 10, 2009
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.test.TestGISBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

/**
 * Read from a CSV
 * 
 * @author DRAND
 * 
 */
public class TestCSVInputStream extends TestGISBase {

	private static final Logger log = LoggerFactory.getLogger(TestCSVInputStream.class);

	@Test
	public void testStreamInput() throws Exception {
		InputStream stream = getStream("csv_example.csv");
		IGISInputStream is = GISFactory.getInputStream(DocumentType.CSV, stream);
		List<IGISObject> contents = new ArrayList<>();
		while (true) {
			IGISObject obj = is.read();
			if (obj == null)
				break;
			contents.add(obj);
		}

		assertTrue(contents.size() > 0);
		assertEquals(Schema.class, contents.get(0).getClass());
		for (int i = 1; i < contents.size(); i++) {
			assertEquals(Row.class, contents.get(i).getClass());
		}
		Schema s = (Schema) contents.get(0);
		// Year,Make,Model,Desc,Price
		SimpleField fyear = s.get("Year");
		SimpleField fmake = s.get("Make");
		SimpleField fmodel = s.get("Model");
		SimpleField fdesc = s.get("Desc");
		SimpleField fprice = s.get("Price");

		assertNotNull(fyear);
		assertNotNull(fmake);
		assertNotNull(fmodel);
		assertNotNull(fdesc);
		assertNotNull(fprice);

		Row r1 = (Row) contents.get(1);
		// 1997,Ford,E350,"ac, abs, moon",3000.00
		assertEquals("1997", r1.getData(fyear));
		assertEquals("ac, abs, moon", r1.getData(fdesc));
		assertEquals("3000.00", r1.getData(fprice));

		Row r2 = (Row) contents.get(2);
		// 1999,Chevy,"Venture ""Extended Edition""","",4900.00
		assertEquals("Venture \"Extended Edition\"", r2.getData(fmodel));

		Row r3 = (Row) contents.get(3);
		// 1996,Jeep,Grand Cherokee,"MUST SELL!
		// air, moon roof, loaded",4799.00
		assertEquals("1996", r3.getData(fyear));
		assertEquals("Jeep", r3.getData(fmake));
		assertEquals("Grand Cherokee", r3.getData(fmodel));
		Object data = r3.getData(fdesc);
		assertNotNull(data);
		assertTrue(data instanceof String);
		//assertTrue(data.toString().startsWith("MUST SELL!"));
		//assertEquals("MUST SELL!\r\nair, moon roof, loaded", r3.getData(fdesc));
		assertEquals("4799.00", r3.getData(fprice));
	}

	@Test
	public void testAnother() throws Exception {
		// cglib-nodep-2.1_3.jar ,2.1.3, jar
		// ,http://sourceforge.net/projects/cglib,Spring & Hibernate
		InputStream stream = getStream("Lab software with versions.csv");
		Schema schema = new Schema(new URI("#labsoftware"));
		SimpleField fjar = new SimpleField("jar");
		schema.put(fjar);
		schema.put(new SimpleField("version"));
		SimpleField ftype = new SimpleField("type");
		schema.put(ftype);
		schema.put(new SimpleField("location"));
		SimpleField fdesc = new SimpleField("description");
		schema.put(fdesc);
		IGISInputStream is = GISFactory.getInputStream(DocumentType.CSV,
				stream, schema);
		List<IGISObject> contents = new ArrayList<>();
		while (true) {
			IGISObject obj = is.read();
			if (obj == null)
				break;
			contents.add(obj);
		}

		assertTrue(contents.size() > 0);
		for (IGISObject row : contents) {
			assertTrue(row instanceof Row);
			Row rrow = (Row) row;
			System.err.println("jar: " + rrow.getData(fjar) + " type: "
					+ rrow.getData(ftype) + " description: "
					+ rrow.getData(fdesc));
		}

	}

	private InputStream getStream(String filename) throws FileNotFoundException {
		File file = new File(getTestDir() + "/input/" + filename);
		if (file.exists())
			return new FileInputStream(file);
		System.out.println("File does not exist: " + file);
		return getClass().getResourceAsStream(filename);
	}
}
