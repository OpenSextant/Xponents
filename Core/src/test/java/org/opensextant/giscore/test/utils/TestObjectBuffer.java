/****************************************************************************************
 *  TestObjectBuffer.java
 *
 *  Created: Jul 15, 2009
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
package org.opensextant.giscore.test.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.utils.FieldCachingObjectBuffer;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.ObjectBuffer;
import static org.junit.Assert.*;

import static org.opensextant.giscore.test.TestSupport.nextDouble;

/**
 * Test object buffers for several different scenarios
 *
 * @author DRAND
 *
 */
public class TestObjectBuffer {
    public static final int max = 10;

    @Test
    public void test1() throws Exception {
        int count = max - 1;
        FieldCachingObjectBuffer buffer = new FieldCachingObjectBuffer(max);
        IDataSerializable objects[] = setupTest(count, buffer);
        doTest(objects, buffer);
    }

    @Test
    public void test2() throws Exception {
        int count = max;
        ObjectBuffer buffer = new FieldCachingObjectBuffer(max);
        IDataSerializable objects[] = setupTest(count, buffer);
        doTest(objects, buffer);
    }

    @Test
    public void test3() throws Exception {
        int count = max + 1;
        ObjectBuffer buffer = new FieldCachingObjectBuffer(max);
        IDataSerializable objects[] = setupTest(count, buffer);
        doTest(objects, buffer);
    }

    @Test
    public void test4() throws Exception {
        int count = max * 2;
        ObjectBuffer buffer = new FieldCachingObjectBuffer(max);
        IDataSerializable objects[] = setupTest(count, buffer);
        doTest(objects, buffer);
    }

    @Test
    public void testPoints() throws Exception {
        final int count = 4;
        System.out.println("testPoints");
        ObjectBuffer buffer = new FieldCachingObjectBuffer(2);
        Schema schema = new Schema();
        SimpleField id = new SimpleField("id", SimpleField.Type.INT);
        SimpleField name = new SimpleField("name");
        name.setLength(1);
        schema.put(id);
        schema.put(name);
        // keeps 2 in-memory and 2 on disk
        List<IDataSerializable> objects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Feature f = makePointFeature();
            f.setName(Integer.toString(i));
            f.putData(id, i & 2);
            f.putData(name, f.getName());
            f.setSchema(schema.getId());
            objects.add(f);
            buffer.write(f);
        }
        doTest(objects.toArray(new IDataSerializable[count]), buffer);
    }

    @Test
    public void testTimed() throws Exception {
        ObjectBuffer buffer = new FieldCachingObjectBuffer(10000);
        try {
            long start = System.nanoTime();
            setupTest(10000, buffer);
            long end = System.nanoTime();
            long millis = (end - start) / 1000000;
            System.out.println("Storing the first 10000 elements to memory took " + millis + " ms");

            start = System.nanoTime();
            setupTest(10000, buffer);
            end = System.nanoTime();
            millis = (end - start) / 1000000;
            System.out.println("Storing the next 10000 elements to file took " + millis + " ms");

            start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                buffer.read();
            }
            end = System.nanoTime();
            millis = (end - start) / 1000000;
            System.out.println("Reading the first 10000 elements from memory took " + millis + " ms");

            start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                buffer.read();
            }
            end = System.nanoTime();
            millis = (end - start) / 1000000;
            System.out.println("Reading the next 10000 elements from file took " + millis + " ms");
        } finally {
            buffer.close();
        }
    }

    private IDataSerializable[] setupTest(int count, ObjectBuffer buffer)
            throws URISyntaxException, IOException {
        URI suri = new URI("urn:mitre:test:uri1");
        Schema s = new Schema(suri);
        SimpleField text = new SimpleField("text");
        text.setLength(100);
        s.put(text);

        IDataSerializable objects[] = new IDataSerializable[count];
        for (int i = 0; i < count; i++) {
            Row r = new Row();
            r.setSchema(suri);
            r.putData(text, "test text " + i);
            objects[i] = r;
            buffer.write(r);
        }

        return objects;
    }

    private Feature makePointFeature() {
        Feature f = new Feature();
        f.setDescription("this is a test placemark");
        Date date = new Date();
        f.setStartTime(date);
        f.setEndTime(date);
        double lat = 40.0 + (5.0 * nextDouble());
        double lon = 40.0 + (5.0 * nextDouble());
        f.setGeometry(new Point(lat, lon));
        return f;
    }

    private void doTest(IDataSerializable[] objects, ObjectBuffer buffer)
            throws IOException, ClassNotFoundException, InstantiationException,
            IllegalAccessException {
        try {
            assertEquals(objects.length, buffer.count());

            // Check each object against the buffer
            for (IDataSerializable object : objects) {
                IDataSerializable retrieved = buffer.read();
                assertNotNull(retrieved);
                //if (object instanceof Feature && retrieved instanceof Feature)
                // System.out.println( ((Feature)object).approximatelyEquals((Feature)retrieved) );
                assertEquals(object, retrieved);
            }
            assertNull(buffer.read());

        } finally {
            buffer.close();
        }
    }

}
