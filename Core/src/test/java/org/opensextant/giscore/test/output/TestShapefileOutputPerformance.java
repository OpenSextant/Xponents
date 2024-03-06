/****************************************************************************************
 *  TestShapefileOutputPerformance.java
 *
 *  Created: Dec 10, 2009
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
import java.net.URI;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.output.shapefile.SingleShapefileOutputHandler;
import org.opensextant.giscore.test.TestGISBase;
import org.opensextant.giscore.utils.FieldCachingObjectBuffer;

/**
 * Create a shapefile with comp
 * 
 * @author DRAND
 */
public class TestShapefileOutputPerformance extends TestGISBase {
	public static final int memsize = 10;
	public static final int totsize = 300;

	UniformRandomProvider RandomUtils = RandomSource.XO_RO_SHI_RO_128_PP.create();
	
	@Test public void createShapefile() throws Exception {
		GISFactory.inMemoryBufferSize.set(memsize);
		Schema schema = new Schema(new URI("urn:test"));
		SimpleField id = new SimpleField("testid");
		id.setLength(10);
		schema.put(id);
		SimpleField dtm = new SimpleField("dtm", Type.DATE);
		schema.put(dtm);
		SimpleField lat = new SimpleField("lat", Type.DOUBLE);
		schema.put(lat);
		SimpleField lon = new SimpleField("lon", Type.DOUBLE);
		schema.put(lon);
		SimpleField charFields[] = new SimpleField[26];
		for(int i = 0; i < charFields.length; i++) {
			charFields[i] = new SimpleField(getRandomFieldName());
			charFields[i].setLength(RandomUtils.nextInt(100) + 120);
			schema.put(charFields[i]);
		}
		SimpleField intFields[] = new SimpleField[10];
		for(int i = 0; i < intFields.length; i++) {
			intFields[i] = new SimpleField(getRandomFieldName(), Type.INT);
			schema.put(intFields[i]);
		}
		
		File tempcsv = new File(tempdir, "large.csv");
		OutputStream csvos = new FileOutputStream(tempcsv);
		IGISOutputStream csvout = GISFactory.getOutputStream(DocumentType.CSV, csvos);
		long writestart = System.currentTimeMillis();
		for(int i = 0; i < totsize; i++) {
			Row r = new Row();
			r.putData(id, "id " + i);
			r.putData(dtm, new Date(System.currentTimeMillis()));
			r.putData(lat, RandomUtils.nextDouble() * 5.0 + 30.0);
			r.putData(lon, RandomUtils.nextDouble() * 5.0 + 30.0);
			r.setSchema(schema.getId());
			for (SimpleField charField : charFields) {
				if (RandomUtils.nextInt(3) == 1) continue; // null value
				r.putData(charField, getRandomText(charField));
			}
			for (SimpleField intField : intFields) {
				if (RandomUtils.nextInt(3) == 1) continue; // null value
				r.putData(intField, RandomUtils.nextInt());
			}
			csvout.write(r);
		}
		csvout.close();
		IOUtils.closeQuietly(csvos);
		
		IGISInputStream csvin = GISFactory.getInputStream(DocumentType.CSV, tempcsv, schema);
		FieldCachingObjectBuffer buffer = new FieldCachingObjectBuffer();
		try {
			long readstart = System.currentTimeMillis();
			System.out.println("Writing csv took " + (writestart - readstart) + "ms");
			while(true) {
				IGISObject ob = csvin.read();
				if (ob == null) {
					csvin.close();
					break;
				}
				if (ob instanceof Row) {
					Row row = (Row) ob;
					Feature f = new Feature();
					Double latVal = Double.valueOf((String) row.getData(lat));
					Double lonVal = Double.valueOf((String) row.getData(lon));
					f.setGeometry(new Point(latVal, lonVal));
					f.setSchema(schema.getId());
					for(SimpleField field : row.getFields()) {
						if (lat.equals(field) || lon.equals(field)) continue;
						Object val = row.getData(field);
						f.putData(field, val);
					}
					buffer.write(f);
				}
			}
			long start = System.currentTimeMillis();
			System.out.println("Reading into buffer took " + (readstart - start) + "ms");
			SingleShapefileOutputHandler handler = new SingleShapefileOutputHandler(schema,
					null, buffer, tempdir, "largepoints", null);
			handler.process();
			long delta = System.currentTimeMillis() - start;

			System.out.println("Writing " + totsize + " records took " + delta + " ms");
		} finally {
			buffer.close();
		}
	}

	private Object getRandomText(SimpleField simpleTextField) {
		int len = simpleTextField.getLength();
		StringBuilder sb = new StringBuilder(20);
		for(int i = 0; i < len; i++) {
			if (RandomUtils.nextInt(10) == 1) 
				sb.append(' ');
			else
				sb.append((char) ('a' + RandomUtils.nextInt(25)));
		}
		return sb.toString();
	}

	private String getRandomFieldName() {
		StringBuilder sb = new StringBuilder(20);
		for(int i = 0; i < (3 + RandomUtils.nextInt(10)); i++) {
			int n = RandomUtils.nextInt(20);
			switch(n) {
			case 1:
				sb.append(' ');
				break;
			case 2:
				sb.append(':');
				break;
			case 3:
				sb.append('(');
				break;
			case 4:
				sb.append(')');
				break;
			case 5:
				sb.append('_');
				break;
			case 6:
				sb.append('/');
				break;
			case 7:
				sb.append((char) '0' + RandomUtils.nextInt(9));
				break;
			default:
				sb.append((char) ('a' + RandomUtils.nextInt(26)));
			}
		}
		return sb.toString();
	}
}
