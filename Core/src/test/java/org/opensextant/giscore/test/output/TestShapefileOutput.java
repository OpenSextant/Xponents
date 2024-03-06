/****************************************************************************************
 *  TestShapefileOutput.java
 *
 *  Created: Jul 23, 2009
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
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.input.shapefile.SingleShapefileInputHandler;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.output.shapefile.SingleShapefileOutputHandler;
import org.opensextant.giscore.utils.FieldCachingObjectBuffer;
import org.opensextant.giscore.utils.ObjectBuffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opensextant.giscore.test.TestSupport.OUTPUT;

public class TestShapefileOutput extends TestShapefileBase {

    UniformRandomProvider RandomUtils = RandomSource.XO_RO_SHI_RO_128_PP.create();

    @Test
    public void testWriteReferencePointOutput() throws Exception {

        FileOutputStream zip = new FileOutputStream(new File(shapeOutputDir, "reference.zip"));
        ZipOutputStream zos = new ZipOutputStream(zip);
        File outDir = new File(OUTPUT + "/shptest/buf");
        outDir.mkdirs();
        IGISOutputStream shpos = GISFactory.getOutputStream(DocumentType.Shapefile, zos, outDir);
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField dtm = new SimpleField("dtm", Type.DATE);
        schema.put(dtm);
        DocumentStart ds = new DocumentStart(DocumentType.Shapefile);
        shpos.write(ds);
        ContainerStart cs = new ContainerStart("Folder");
        cs.setName("aaa");
        shpos.write(cs);
        shpos.write(schema);
        for (int i = 0; i < 5; i++) {
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(dtm, new Date(System.currentTimeMillis()));
            f.setSchema(schema.getId());
            double lat = 40.0 + (5.0 * RandomUtils.nextDouble());
            double lon = 40.0 + (5.0 * RandomUtils.nextDouble());
            Point point = new Point(lat, lon);
            f.setGeometry(point);
            shpos.write(f);
        }
        shpos.close();
        zos.flush();
        zos.close();
    }

    @Test
    public void testPointOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField dtm = new SimpleField("dtm", Type.DATE);
        schema.put(dtm);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        List<Point> pts = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(dtm, new Date(System.currentTimeMillis()));
            f.setSchema(schema.getId());
            Point point = getRandomPoint();
            pts.add(point);
            //System.out.format("Point-%d: %f %f%n", i, point.getCenter().getLongitudeAsDegrees(),
            //point.getCenter().getLatitudeAsDegrees()); // debug
            f.setGeometry(point);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, pts, "points");
    }

    @Test
    public void testLongOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField dtm = new SimpleField("dtm", Type.LONG);
        schema.put(dtm);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        List<Point> pts = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(dtm, i == 0 ? Long.MAX_VALUE : System.currentTimeMillis());
            f.setSchema(schema.getId());
            Point point = getRandomPoint();
            pts.add(point);
            f.setGeometry(point);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, pts, "longData");
    }

    @Test
    public void testPointzOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField dtm = new SimpleField("dtm", Type.DATE);
        schema.put(dtm);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        List<Point> pts = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(dtm, new Date(System.currentTimeMillis()));
            f.setSchema(schema.getId());
            Point point = getRandomPointZ();
            pts.add(point);
            f.setGeometry(point);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, pts, "pointz");
    }

    @Test
    public void testNumericOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);

        SimpleField db = new SimpleField("db", Type.DOUBLE);
        db.setScale(10);
        schema.put(db);

        SimpleField dtm = new SimpleField("dtm", Type.DOUBLE);
        dtm.setScale(2);
        schema.put(dtm);

        Feature f = new Feature();
        f.putData(id, "id0");
        f.putData(db, Double.MAX_VALUE); // 1.7976931348623157e+308
        f.putData(dtm, 1.2E+34);
        f.setSchema(schema.getId());
        Point point = getRandomPoint();
        f.setGeometry(point);

        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        try {
            buffer.write(f);

            SingleShapefileOutputHandler soh = new SingleShapefileOutputHandler(
                schema, null, buffer, shapeOutputDir, "doublePoint", null);
            soh.process();
        } finally {
            buffer.close();
        }

        SingleShapefileInputHandler handler = new SingleShapefileInputHandler(
                shapeOutputDir, "doublePoint");
        try {
            IGISObject ob = handler.read();
            assertTrue(ob instanceof Schema);
            ob = handler.read();
            assertTrue(ob instanceof Feature);
            Feature readF = (Feature) ob;
            // original value: 1.7976931348623157E+308 formatted to 24 characters
            // allow for small lose of precision
            assertEquals((Double) f.getData(db), (Double) readF.getData(db), 1e+2);
            assertEquals((Double) f.getData(dtm), (Double) readF.getData(dtm), 1e-6);
        } finally {
            handler.close();
        }
    }

    @Test
    public void testMultiPointOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        Feature f = new Feature();
        f.putData(id, "id multipoint");
        f.setSchema(schema.getId());
        List<Point> pts = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Point point = getRandomPoint();
            pts.add(point);
        }
        f.setGeometry(new MultiPoint(pts));
        buffer.write(f);

        writeShapefile(schema, buffer, null, "multipoint");
    }

    @Test
    public void testLineOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        List<Line> lines = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(date, new Date());
            f.setSchema(schema.getId());
            List<Point> pts = new ArrayList<>(2);
            pts.add(getRandomPoint());
            pts.add(getRandomPoint());
            Line line = new Line(pts);
            f.setGeometry(line);
            lines.add(line);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, lines, "lines");
    }

    @Test
    public void testLinezOutput() throws Exception {
        System.out.println("Test linez");
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        List<Line> lines = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(date, new Date());
            f.setSchema(schema.getId());
            List<Point> pts = new ArrayList<>(2);
            pts.add(getRandomPointZ());
            pts.add(getRandomPointZ());
            Line line = new Line(pts);
            f.setGeometry(line);
            lines.add(line);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, lines, "linez");
        // java.io.IOException: Shapefile contains record with unexpected shape type 1078346337, expecting 13
        // 	at org.opensextant.giscore.input.shapefile.SingleShapefileInputHandler.getGeometry(SingleShapefileInputHandler.java:253)
    }

    @Test
    public void testMultiLineOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        Feature f = new Feature();
        f.putData(id, "id multiline");
        f.putData(date, new Date());
        f.setSchema(schema.getId());
        List<Line> lines = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            List<Point> pts = new ArrayList<>(2);
            pts.add(getRandomPoint());
            pts.add(getRandomPoint());
            Line line = new Line(pts);
            lines.add(line);
        }
        MultiLine mline = new MultiLine(lines);
        f.setGeometry(mline);
        buffer.write(f);

        writeShapefile(schema, buffer, null, "multilines");
    }

    @Test
    public void testRingOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        List<LinearRing> rings = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint();
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(date, new Date());
            f.setSchema(schema.getId());
            List<Point> pts = new ArrayList<>(6);
            pts.add(getRingPoint(cp, 4, 5, .3, .4));
            pts.add(getRingPoint(cp, 3, 5, .3, .4));
            pts.add(getRingPoint(cp, 2, 5, .3, .4));
            pts.add(getRingPoint(cp, 1, 5, .3, .4));
            pts.add(getRingPoint(cp, 0, 5, .3, .4));
            pts.add(pts.get(0)); // should start and end with the same point

            LinearRing ring = new LinearRing(pts, true);
            /*
               //debug
               System.out.println("\nRing-" + i +" " + ring + " " + Integer.toHexString(ring.hashCode()));
               for (Point pt : pts) System.out.format("%f %f%n", pt.getCenter().getLongitudeAsDegrees(), pt.getCenter().getLatitudeAsDegrees());
               // end debug
               */
            if (!ring.clockwise()) System.out.println("rings must be in clockwise point order");
            f.setGeometry(ring);
            rings.add(ring);
            buffer.write(f);
        }

        // now read shape file back in and test against what we wrote
        writeShapefile(schema, buffer, rings, "rings");
    }

    @Test
    public void testRingZOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint();
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(date, new Date());
            f.setSchema(schema.getId());
            List<Point> pts = new ArrayList<>(6);
            pts.add(getRingPointZ(cp, 4, 5, .3, .4));
            pts.add(getRingPointZ(cp, 3, 5, .3, .4));
            pts.add(getRingPointZ(cp, 2, 5, .3, .4));
            pts.add(getRingPointZ(cp, 1, 5, .3, .4));
            pts.add(getRingPointZ(cp, 0, 5, .3, .4));
            pts.add(pts.get(0)); // should start and end with the same point
            // First (outer) ring should be in clockwise point order
            LinearRing ring = new LinearRing(pts, true);
            if (!ring.clockwise()) System.err.println("rings must be in clockwise point order");
            f.setGeometry(ring);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, null, "ringz");
    }

    @Test
    public void testPolyOutput() throws Exception {
        System.out.println("Test PolyOutput");
        Exception ex = null;
        for (int i = 1; i <= 8; i++) {
            try {
                realPolyOutputTest("polys" + i);
                return; // test successful
            } catch (IllegalArgumentException e) {
                System.out.println("*** warning: failed at polytest: " + i);
                e.printStackTrace(System.out);
                if (ex == null) ex = e; // save first failed test result
            }
        }
        if (ex != null) {
            // this means we failed all attempts so we really failed
            throw ex;
        }
    }

    private void realPolyOutputTest(String filebase) throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint(25.0); // Center of outer poly
            Feature f = new Feature();
            f.putData(id, "id " + i);
            f.putData(date, new Date());
            f.setSchema(schema.getId());
            List<Point> pts = new ArrayList<>(6);
            for (int k = 0; k < 5; k++) {
                pts.add(getRingPoint(cp, 4 - k, 5, 1.0, 2.0));
            }
            pts.add(pts.get(0)); // should start and end with the same point
            LinearRing outerRing = new LinearRing(pts, true);
            if (!outerRing.clockwise()) System.err.println("First (outer) ring should be in clockwise point order");
            List<LinearRing> innerRings = new ArrayList<>(4);
            for (int j = 0; j < 4; j++) {
                pts = new ArrayList<>(6);
                Point ircp = getRingPoint(cp, j, 4, .5, 1.0);
                for (int k = 0; k < 5; k++) {
                    pts.add(getRingPoint(ircp, k, 5, .24, .2));
                }
                pts.add(pts.get(0)); // should start and end with the same point
                innerRings.add(new LinearRing(pts, true));
            }
            Polygon p = new Polygon(outerRing, innerRings, true);
            f.setGeometry(p);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, null, filebase);
    }

    @Test
    public void testPolyZOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint(25.0); // Center of outer poly
            Feature f = new Feature();
            f.putData(id, "id polyz " + i);
            f.putData(date, new Date());
            f.setSchema(schema.getId());
            List<Point> pts = new ArrayList<>(6);
            for (int k = 0; k < 5; k++) {
                pts.add(getRingPointZ(cp, k, 5, 1.0, 2.0));
            }
            pts.add(pts.get(0)); // should start and end with the same point
            // First (outer) ring should be in clockwise point order
            LinearRing outerRing = new LinearRing(pts, true);
            List<LinearRing> innerRings = new ArrayList<>(4);
            for (int j = 0; j < 4; j++) {
                pts = new ArrayList<>(6);
                Point ircp = getRingPointZ(cp, j, 4, .5, 1.0);
                for (int k = 0; k < 5; k++) {
                    pts.add(getRingPointZ(ircp, k, 5, .24, .2));
                }
                pts.add(pts.get(0)); // should start and end with the same point
                innerRings.add(new LinearRing(pts, true));
            }
            Polygon p = new Polygon(outerRing, innerRings);
            f.setGeometry(p);
            buffer.write(f);
        }

        writeShapefile(schema, buffer, null, "polyz");
    }

    @Test
    public void testMultiRingOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        Feature f = new Feature();
        f.putData(id, "id multiring");
        f.putData(date, new Date());
        f.setSchema(schema.getId());
        List<LinearRing> rings = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint(25.0); // Center of outer poly
            List<Point> pts = new ArrayList<>(6);
            // rings must be in clockwise point order
            for (int k = 0; k < 5; k++) {
                pts.add(getRingPoint(cp, 4 - k, 5, .2, .5));
            }
            pts.add(pts.get(0)); // must start and end with the same point
            LinearRing outerRing = new LinearRing(pts, true);
            rings.add(outerRing);
        }
        MultiLinearRings mring = new MultiLinearRings(rings, true);
        f.setGeometry(mring);
        buffer.write(f);

        writeShapefile(schema, buffer, null, "multirings");
    }

    @Test
    public void testMultiRingZOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        Feature f = new Feature();
        f.putData(id, "id multiringz");
        f.putData(date, new Date());
        f.setSchema(schema.getId());
        List<LinearRing> rings = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint(25.0); // Center of outer poly
            List<Point> pts = new ArrayList<>(6);
            // rings must be in clockwise point order
            for (int k = 0; k < 5; k++) {
                pts.add(getRingPointZ(cp, 4 - k, 5, 1.0, 2.0));
            }
            pts.add(pts.get(0)); // should start and end with the same point
            LinearRing outerRing = new LinearRing(pts, true);
            rings.add(outerRing);
        }
        MultiLinearRings mring = new MultiLinearRings(rings, true);
        f.setGeometry(mring);
        buffer.write(f);

        writeShapefile(schema, buffer, null, "multiringz");
    }

    @Test
    public void testMultiPolyOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        Feature f = new Feature();
        f.putData(id, "id multipoly");
        f.putData(date, new Date());
        f.setSchema(schema.getId());
        List<Polygon> polys = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            Point cp = getRandomPoint(25.0); // Center of outer poly
            int sides = RandomUtils.nextInt(4) + 4;
            List<Point> pts = new ArrayList<>(sides + 1);
            // First (outer) ring should be in clockwise point order
            for (int k = 0; k < sides; k++) {
                pts.add(getRingPoint(cp, k, sides, 1.0, 2.0));
            }
            pts.add(pts.get(0)); // should start and end with the same point
            LinearRing outerRing = new LinearRing(pts, true);
            int inners = RandomUtils.nextInt(4) + 1;
            List<LinearRing> innerRings = new ArrayList<>(inners);
            for (int j = 0; j < inners; j++) {
                pts = new ArrayList<>(6);
                Point ircp = getRingPoint(cp, j, inners, .5, 1.0);
                for (int k = 0; k < 5; k++) {
                    pts.add(getRingPoint(ircp, k, 5, .24, .2));
                }
                pts.add(pts.get(0)); // should start and end with the same point
                innerRings.add(new LinearRing(pts, true));
            }
            Polygon p = new Polygon(outerRing, innerRings);
            polys.add(p);
        }
        MultiPolygons mp = new MultiPolygons(polys);
        f.setGeometry(mp);
        buffer.write(f);

        writeShapefile(schema, buffer, null, "multipolys");
    }

    @Test
    public void testMultiPolyZOutput() throws Exception {
        Schema schema = new Schema(new URI("urn:test"));
        SimpleField id = new SimpleField("testid");
        id.setLength(10);
        schema.put(id);
        SimpleField date = new SimpleField("today", SimpleField.Type.DATE);
        schema.put(date);
        ObjectBuffer buffer = new FieldCachingObjectBuffer();
        Feature f = new Feature();
        f.putData(id, "id multipolyz");
        f.putData(date, new Date());
        f.setSchema(schema.getId());
        List<Polygon> polys = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Point cp = getRandomPoint(25.0); // Center of outer poly
            List<Point> pts = new ArrayList<>(6);
            for (int k = 0; k < 5; k++) {
                pts.add(getRingPointZ(cp, k, 5, 2, 1.5));
            }
            pts.add(pts.get(0)); // should start and end with the same point
            LinearRing outerRing = new LinearRing(pts, true);
            List<LinearRing> innerRings = new ArrayList<>(4);
            for (int j = 0; j < 4; j++) {
                pts = new ArrayList<>(6);
                Point ircp = getRingPointZ(cp, j, 4, .5, 1.0);
                for (int k = 0; k < 5; k++) {
                    pts.add(getRingPointZ(ircp, k, 5, .24, .2));
                }
                pts.add(pts.get(0)); // should start and end with the same point
                innerRings.add(new LinearRing(pts, true));
            }
            Polygon p = new Polygon(outerRing, innerRings);
            polys.add(p);
        }
        MultiPolygons mp = new MultiPolygons(polys);
        f.setGeometry(mp);
        buffer.write(f);

        writeShapefile(schema, buffer, null, "multipolyz");
    }

}