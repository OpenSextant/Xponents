/****************************************************************************************
 *  TestDbfOutputStream.java
 *
 *  Created: Jul 16, 2009
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
package org.opensextant.giscore.test.output;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Test;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.input.dbf.DbfInputStream;
import org.opensextant.giscore.output.dbf.DbfOutputStream;
import org.opensextant.giscore.utils.DateParser;
import static org.junit.Assert.*;

/**
 *
 */
public class TestDbfOutputStream {

    UniformRandomProvider RandomUtils = RandomSource.XO_RO_SHI_RO_128_PP.create();

    private final Random rand = new Random();

    private static final String[] DATE_STRINGS = {
            "2012-05-29T17:00:00.000Z",
            "05/29/2012 17:00:00",
            "05/29/2012 17:00",
            "05/29/2012",
            "29-May-2012",
            "20120529",
    };

    // @Test
    public void testDbfOutputStreamString() throws Exception {
        Schema s = new Schema();
        SimpleField s1 = new SimpleField("s1");
        s1.setLength(5);
        SimpleField s2 = new SimpleField("s2");
        s2.setLength(10);
        SimpleField s3 = new SimpleField("s3");
        s3.setLength(253);

        s.put(s1);
        s.put(s2);
        s.put(s3);

        File temp = File.createTempFile("test", ".dbf");
		FileInputStream is = null;
		FileOutputStream os = null;
		try {
			os = new FileOutputStream(temp);
			DbfOutputStream dbfos = new DbfOutputStream(os, null);
			dbfos.write(s);
			List<Row> data = new ArrayList<>(50);
			for (int i = 0; i < 50; i++) {
				Row r = new Row();
				r.putData(s1, randomString(s1));
				r.putData(s2, randomString(s2));
				r.putData(s3, randomString(s3));
				data.add(r);
				dbfos.write(r);
			}

			// generate large string that will be truncated in output
			Row r = new Row();
			String largeString = randomString(256);
			r.putData(s1, largeString);
			r.putData(s2, largeString);
			r.putData(s3, largeString);
			dbfos.write(r);

			// write row with all fields set as null values
			r = new Row();
			for (SimpleField sf : r.getFields()) {
				r.putData(sf, null);
			}
			dbfos.write(r);

			dbfos.close();
			os.close();
			os = null;

			is = new FileInputStream(temp);
			DbfInputStream dbfis = new DbfInputStream(is, null);
			Schema readschema = (Schema) dbfis.read();
			assertNotNull(readschema);
			assertEquals(3, readschema.getKeys().size());
			compare(s1, readschema.get("s1"));
			compare(s2, readschema.get("s2"));
			compare(s3, readschema.get("s3"));
			for (int i = 0; i < 50; i++) {
				Row readrow = (Row) dbfis.read();
				Row origrow = data.get(i);
				compare(s, readschema, origrow, readrow);
			}

			Row readrow = (Row) dbfis.read();
			assertNotNull(readrow);
			assertEquals(5, StringUtils.length((String) readrow.getData(s1)));
			assertEquals(10, StringUtils.length((String) readrow.getData(s2)));
			assertEquals(253, StringUtils.length((String) readrow.getData(s3)));

			readrow = (Row) dbfis.read();
			assertNotNull(readrow);
			for (Map.Entry<SimpleField, Object> e : readrow.getEntrySet()) {
				assertTrue(ObjectUtils.NULL == e.getValue());
			}
		} finally {
			IOUtils.closeQuietly(os);
			IOUtils.closeQuietly(is);
			if (temp.exists() && !temp.delete())
				temp.deleteOnExit();
		}
    }

    // @Test
    public void testDbfOutputStreamBoolean() throws Exception {
        Schema s = new Schema();
        SimpleField b = new SimpleField("b", Type.BOOL);
        s.put(b);
        File temp = File.createTempFile("test-bool", ".dbf");
        FileOutputStream os = new FileOutputStream(temp);
		FileInputStream is = null;
		try {
			DbfOutputStream dbfos = new DbfOutputStream(os, null);
			dbfos.write(s);

			Object[] objs = {Boolean.TRUE, Boolean.FALSE, "?", "T", "t", "F", "f", "1", "0", null, "abc"};

			List<Row> data = new ArrayList<>(objs.length);
			for (Object o : objs) {
				Row r = new Row();
				r.putData(b, o);
				data.add(r);
				dbfos.write(r);
			}
			dbfos.close();
			os.close();
			os = null;

			is = new FileInputStream(temp);
			DbfInputStream dbfis = new DbfInputStream(is, null);
            Schema readschema = (Schema) dbfis.read();
            assertNotNull(readschema);
            assertEquals(1, readschema.getKeys().size());
            compare(b, readschema.get("b"));
            for (Row origrow : data) {
                Row readrow = (Row) dbfis.read();
                compare(s, readschema, origrow, readrow);
            }
        } finally {
			IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
			if (temp.exists() && !temp.delete())
				temp.deleteOnExit();
        }
    }

    // @Test
    public void testDbfOutputLongNumeric() throws Exception {
        Schema s = new Schema();
        SimpleField li = new SimpleField("li", Type.LONG);
        li.setLength(24); // length will be set to max of 20 characters in output
        s.put(li);
        SimpleField ls = new SimpleField("ls", Type.LONG);
        ls.setLength(14); // length will be set to min of 15 characters in output
        s.put(ls);

        File temp = File.createTempFile("testLong", ".dbf");
        FileOutputStream os = new FileOutputStream(temp);
		FileInputStream is = null;
        try {
			List<Row> data = new ArrayList<>(50);
            DbfOutputStream dbfos = new DbfOutputStream(os, null);
            dbfos.write(s);
            for (int i = 0; i < 50; i++) {
                Row r = new Row();
                long rndLong = RandomUtils.nextLong() % 10000000;
                r.putData(li, rndLong);
                r.putData(ls, rndLong);
                data.add(r);
                dbfos.write(r);
            }
            dbfos.close();
			os.close();
			os = null;

			is = new FileInputStream(temp);
            DbfInputStream dbfis = new DbfInputStream(is, null);
            Schema readschema = (Schema) dbfis.read();
            assertNotNull(readschema);
            assertEquals(2, readschema.getKeys().size());
            compare(li, readschema.get("li"));
            compare(ls, readschema.get("ls"));
            for (int i = 0; i < 50; i++) {
                Row readrow = (Row) dbfis.read();
                Row origrow = data.get(i);
                compare(s, readschema, origrow, readrow);
            }
            dbfis.close();
        } finally {
			IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
			if (temp.exists() && !temp.delete())
				temp.deleteOnExit();
        }
    }

    // @Test
    public void testDbfOutputStreamNumeric() throws Exception {
        Schema s = new Schema();
        SimpleField b = new SimpleField("b", Type.BOOL);
        SimpleField f = new SimpleField("f", Type.FLOAT);
        SimpleField db = new SimpleField("db", Type.DOUBLE);
        SimpleField li = new SimpleField("li", Type.LONG);
        li.setLength(10);
        SimpleField it = new SimpleField("it", Type.INT);
        it.setLength(9);
        SimpleField sh = new SimpleField("sh", Type.SHORT);
        it.setLength(5);
        SimpleField ui = new SimpleField("ui", Type.UINT);
        ui.setLength(8);
        SimpleField us = new SimpleField("us", Type.USHORT);
        us.setLength(4);
        SimpleField oid = new SimpleField("oid", Type.OID);

        s.put(b);
        s.put(f);
        s.put(db);
        s.put(li);
        s.put(it);
        s.put(sh);
        s.put(ui);
        s.put(us);
        s.put(oid);

        File temp = File.createTempFile("test", ".dbf");
        FileOutputStream os = new FileOutputStream(temp);
		FileInputStream is = null;
		try {
			DbfOutputStream dbfos = new DbfOutputStream(os, null);
			dbfos.write(s);
			List<Row> data = new ArrayList<>(50);
			for (int i = 0; i < 50; i++) {
				Row r = new Row();
				r.putData(b, RandomUtils.nextBoolean());
				r.putData(f, RandomUtils.nextFloat());
				r.putData(db, RandomUtils.nextFloat());
				long rndLong = RandomUtils.nextLong();
				r.putData(li, rndLong % 10000000);
				r.putData(it, RandomUtils.nextInt(10000000));
				r.putData(sh, (short) RandomUtils.nextInt(10000));
				r.putData(ui, Math.abs(RandomUtils.nextInt(10000000)));
				r.putData(us, (short) Math.abs(RandomUtils.nextInt(2000)));
				r.putData(oid, Math.abs(RandomUtils.nextInt(10000000)));
				data.add(r);
				dbfos.write(r);
			}

			// create values larger than most field lengths
			Row r = new Row();
			r.putData(b, Boolean.FALSE);
			r.putData(f, Float.MAX_VALUE);
			r.putData(db, Double.MAX_VALUE);
			r.putData(li, Long.MIN_VALUE);
			r.putData(it, Integer.MIN_VALUE);
			r.putData(sh, Short.MIN_VALUE);
			r.putData(ui, Integer.MAX_VALUE);
			r.putData(us, Short.MAX_VALUE);
			r.putData(oid, Long.MAX_VALUE);
			dbfos.write(r);

			// write row with all null values
			r = new Row();
			for (SimpleField sf : r.getFields()) {
				r.putData(sf, null);
			}
			dbfos.write(r);

			dbfos.close();
			os.close();
			os = null;

			is = new FileInputStream(temp);
			DbfInputStream dbfis = new DbfInputStream(is, null);
			Schema readschema = (Schema) dbfis.read();
			assertNotNull(readschema);
			assertEquals(9, readschema.getKeys().size());
			compare(b, readschema.get("b"));
			compare(f, readschema.get("f"));
			compare(db, readschema.get("db"));
			compare(li, readschema.get("li"));
			compare(it, readschema.get("it"));
			compare(sh, readschema.get("sh"));
			compare(ui, readschema.get("ui"));
			compare(us, readschema.get("us"));
			compare(oid, readschema.get("oid"));

			for (int i = 0; i < 50; i++) {
				Row readrow = (Row) dbfis.read();
				Row origrow = data.get(i);
				compare(s, readschema, origrow, readrow);
			}

			Row readrow = (Row) dbfis.read();
			assertNotNull(readrow);
			assertTrue(readrow.hasExtendedData());
			assertFalse((Boolean) readrow.getData(b));
			for (Map.Entry<SimpleField, Object> e : readrow.getEntrySet()) {
				assertNotNull(e.getValue());
			}

			readrow = (Row) dbfis.read();
			assertNotNull(readrow);
			assertTrue(readrow.hasExtendedData());
			for (Map.Entry<SimpleField, Object> e : readrow.getEntrySet()) {
				// System.out.println(e.getKey() + " " + e.getValue());
				if (e.getKey().getType() == Type.BOOL)
					assertFalse((Boolean) e.getValue());
				else
					assertTrue(ObjectUtils.NULL == e.getValue());
			}
			dbfis.close();
		} finally {
			IOUtils.closeQuietly(os);
			IOUtils.closeQuietly(is);
			if (temp.exists() && !temp.delete())
				temp.deleteOnExit();
		}
	}

    // @Test
    public void testDbfOutputStreamDate() throws Exception {
        Schema s = new Schema();
        SimpleField date = new SimpleField("date", Type.DATE);
        s.put(date);

        File temp = File.createTempFile("test", ".dbf");
        FileOutputStream os = new FileOutputStream(temp);
		FileInputStream is = null;
		try {
			DbfOutputStream dbfos = new DbfOutputStream(os, null);
			dbfos.write(s);
			List<Row> data = new ArrayList<>(50 + DATE_STRINGS.length);
			for (int i = 0; i < 50; i++) {
				Row r = new Row();
				Date d = new Date(System.currentTimeMillis()
						- (200L * RandomUtils.nextInt()));
				// System.out.println("Date: " + d);
				r.putData(date, d);
				data.add(r);
				dbfos.write(r);
			}

			// multiple date formats
			for (String dateString : DATE_STRINGS) {
				Row r = new Row();
				r.putData(date, dateString);
				data.add(r);
				dbfos.write(r);
			}

			Row r = new Row();
			r.putData(date, null); // null date value
			dbfos.write(r);

			r = new Row();
			r.putData(date, "May-2014"); // incomplete date
			dbfos.write(r);

			dbfos.close();
			os.close();
			os = null;

			is = new FileInputStream(temp);
			DbfInputStream dbfis = new DbfInputStream(is, null);
            Schema readschema = (Schema) dbfis.read();
            assertNotNull(readschema);
            assertEquals(1, readschema.getKeys().size());
            compare(date, readschema.get("date"));
            for (Row origrow : data) {
                Row readrow = (Row) dbfis.read();
                compare(s, readschema, origrow, readrow);
            }
            Row readrow = (Row) dbfis.read();
            assertNotNull(readrow);
            assertTrue(readrow.hasExtendedData());
            assertNull(readrow.getData(date));

            readrow = (Row) dbfis.read(); // 05-31-2014 => 0004-09-01T00:00
            assertNotNull(readrow);
            //System.out.printf("Date: %s %n", isoFormat.format((Date) readrow.getData(date)));
            assertNull(readrow.getData(date));

        } finally {
			IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
			if (temp.exists() && !temp.delete())
				temp.deleteOnExit();
        }
    }

    private void compare(SimpleField orig, SimpleField read) {
        assertEquals(orig.getName(), read.getName());
        // Not really correct as the lengths from the dbf file are generally longer
        // than the normal lengths. Still correct for strings
        if (orig.getType().equals(Type.STRING))
            assertEquals(orig.getLength(), read.getLength());
        // Types won't always match because there are destructive mappings
        // assertEquals(orig.getType(), read.getType());
    }

    private void compare(Schema s, Schema readschema, Row origrow, Row readrow) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        assertNotNull(readrow);
        assertEquals(origrow.getFields().size(), readrow.getFields().size());
        for (Map.Entry<String, SimpleField> entry : s.entrySet()) {
            String key = entry.getKey();
            SimpleField field = entry.getValue();
            Object v1 = origrow.getData(field);
            SimpleField readfield = readschema.get(key);
            Object v2 = readrow.getData(readfield);
            if (field.getType() == Type.BOOL) {
                if (v1 == null) {
                    // null value -> as false
                    assertEquals(Boolean.FALSE, v2);
                    return;
                } else if (v1 instanceof String) {
                    // System.out.println("XXX: boolean: orig="+v1+", actual="+v2);
                    if ("?".equals(v1) && v2 == null) {
                        // v1 => ? boolean value interpreted as null
                        return;
                    } else if (v2 instanceof Boolean) {
                        assertEquals(getBoolean(v1), v2);
                        return;
                    }
                }
            } else if (v1 instanceof String && v2 instanceof Date) {
                v1 = getDate(v1);
            }

            if (v1 instanceof Long) {
                assertEquals(((Number) v1).longValue(), ((Number) v2).longValue());
            } else if (v1 instanceof Number) {
                assertEquals(((Number) v1).doubleValue(), ((Number) v2).doubleValue(), 1e-6);
            } else if (v1 instanceof Date) {
                int y1, y2, m1, m2, d1, d2;
                // note: original timestamp is discarded -- only YYYYMMDD is preserved in Date type
                cal.setTimeInMillis(((Date) v1).getTime());
                y1 = cal.get(Calendar.YEAR);
                m1 = cal.get(Calendar.MONTH);
                d1 = cal.get(Calendar.DAY_OF_MONTH);
                cal.setTimeInMillis(((Date) v2).getTime());
                y2 = cal.get(Calendar.YEAR);
                m2 = cal.get(Calendar.MONTH);
                d2 = cal.get(Calendar.DAY_OF_MONTH);
                assertEquals("YEAR field", y1, y2);
                assertEquals("MONTH field", m1, m2);
                assertEquals("DAY_OF_MONTH field", d1, d2);
            } else {
                assertEquals(v1, v2);
            }
        }
    }

    private String randomString(SimpleField s) {
        int len = s.getLength();
        if (rand.nextFloat() < .3) {
            len -= rand.nextInt(len / 2);
        }
        return randomString(len);
    }

    private static String randomString(int len) {
        StringBuilder b = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int ch;
            int m = i % 10;
            if (m == 0)
                ch = 'A' + (i / 10);
            else
                ch = '0' + m;
            b.append((char) ch);
        }
        return b.toString();
    }

    private Date getDate(Object data) {
        if (data == null) {
            return null;
        } else if (data instanceof Date) {
            return (Date) data;
        } else {
            String dstr = data.toString();
            return DateParser.parse(dstr);
        }
    }

    private static Boolean getBoolean(Object data) {
        if (data instanceof Boolean) {
            return (Boolean) data;
        } else if (data instanceof String) {
            String val = (String) data;
            if (val.equals("?"))
                return null;
            val = val.toLowerCase();
            return val.startsWith("t") || val.startsWith("y")
                    || "1".equals(val);
        } else {
            return Boolean.FALSE;
        }
    }

}
