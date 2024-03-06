/****************************************************************************************
 *  TestGISBase.java
 *
 *  Created: Feb 10, 2009
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
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.Assert;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import static org.opensextant.giscore.test.TestSupport.*;


/**
 * The base class provides a series of features of various kinds, used to feed
 * the test cases as well as establishing a common test output file directory.
 *
 * @author DRAND
 */
public abstract class TestGISBase {

    /**
     * Autodelete flag (default=true). To keep temp files set System flag
     * keepTempFiles=true before running tests.
     */
    protected static final boolean autoDelete = !Boolean.getBoolean("keepTempFiles");

    private static int id;
    public static final File tempdir;
    public static SimpleDateFormat FMT = new SimpleDateFormat("dd-HH-mm-ss");

    protected String getTestDir() {
    	String name = "src/test/resources/" + getClass().getPackage().getName().replaceAll("\\.", "/");
    	int last = name.lastIndexOf("/test");
    	return name.substring(0, last+5);
    }
    
    static {
        // String dir = System.getProperty("java.io.tmpdir");
        tempdir = new File(OUTPUT, "t" + FMT.format(new Date()));
        if (tempdir.mkdirs())
            System.out.println("Created temp output directory: " + tempdir);
    }

    public static final AtomicInteger count = new AtomicInteger();
    protected static final Random random = new Random(1000);

    /**
     * Create a temp file or directory for a test
     *
     * @param prefix string prefix, never <code>null</code> or empty
     * @param suffix string suffix, never <code>null</code> or empty
     * @return a non-<code>null</code> file path in the temp directory setup
     *         above
     */
    protected File createTemp(String prefix, String suffix) {
        return createTemp(prefix, suffix, tempdir);
    }

    /**
     * Create a temp file or directory for a test
     *
     * @param prefix    string prefix, never <code>null</code> or empty
     * @param suffix    string suffix, never <code>null</code> or empty
     * @param directory The directory in which the file is to be created, or
     *                  <code>null</code> if the default temporary-file
     *                  directory is to be used
     * @return a non-<code>null</code> file path in the temp directory setup
     *         above
     */
    protected File createTemp(String prefix, String suffix, File directory) {
        if (prefix == null || prefix.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "prefix should never be null or empty");
        }
        if (suffix == null || suffix.trim().length() == 0) {
            suffix = "";
        }
        if (directory == null) directory = tempdir;
        return new File(directory, prefix + count.incrementAndGet() + suffix);
    }

    /**
     * Create a feature with a number of data elements in the extended data.
     * This method will not use a schema.
     *
     * @param geoclass the class of the geometry objects to create
     * @param names    the names of the attributes
     * @param values   the values, the length must match the length of names
     * @return the new instance
     */
    protected Feature createFeature(Class<? extends Geometry> geoclass,
                                    String names[], Object values[]) {
        if (names == null) {
            throw new IllegalArgumentException("names should never be null");
        }
        if (values == null) {
            throw new IllegalArgumentException("values should never be null");
        }
        if (names.length != values.length) {
            throw new IllegalArgumentException("the count of names and values must match");
        }
        Feature f = createBasicFeature(geoclass);
        for (int i = 0; i < names.length; i++) {
            SimpleField field = new SimpleField(names[i]);
            f.putData(field, values[i]);
        }
        return f;
    }

    /**
     * Create a feature with a number of data elements in the extended data.
     * This method will not use a schema.
     *
     * @param geoclass the class of the geometry objects to create
     * @param schema   the schema
     * @param valuemap the valuemap, not <code>null</code>
     * @return the new instance
     */
    protected Feature createFeature(Class<? extends Geometry> geoclass,
                                    Schema schema, Map<String, Object> valuemap) {
        if (schema == null) {
            throw new IllegalArgumentException("schema should never be null");
        }
        if (valuemap == null) {
            throw new IllegalArgumentException("valuemap should never be null");
        }
        Feature f = createBasicFeature(geoclass);
        f.setSchema(schema.getId());
        for (String key : schema.getKeys()) {
            SimpleField field = schema.get(key);
            Object value = valuemap.get(key);
            f.putData(field, value != null ? value : ObjectUtils.NULL);
        }
        return f;
    }

    /**
     * Create a schema
     *
     * @param names the names for the fields
     * @param types the types for the fields
     * @return
     */
    protected Schema createSchema(String names[], SimpleField.Type types[]) {
        Schema s = new Schema();
        for (int i = 0; i < names.length; i++) {
            SimpleField field = new SimpleField(names[i]);
            field.setType(types[i]);
            s.put(names[i], field);
        }
        return s;
    }

    /**
     * @param geoclass
     * @return
     */
    protected Feature createBasicFeature(Class<? extends Geometry> geoclass) {
        Feature f = new Feature();
        count.incrementAndGet();
        f.setName("feature" + count);
        f.setDescription("feature description " + count);
        if (geoclass.isAssignableFrom(Point.class)) {
            Point p = new Point(new Geodetic2DPoint(random));
            f.setGeometry(p);
        } else if (geoclass.isAssignableFrom(Line.class)) {
            List<Point> pts = new ArrayList<>();
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            f.setGeometry(new Line(pts));
        } else if (geoclass.isAssignableFrom(LinearRing.class)) {
            List<Point> pts = new ArrayList<>();
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            f.setGeometry(new LinearRing(pts));
        } else if (geoclass.isAssignableFrom(Polygon.class)) {
            List<Point> pts = new ArrayList<>();
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            pts.add(new Point(new Geodetic2DPoint(random)));
            f.setGeometry(new Polygon(new LinearRing(pts)));
        }
        return f;
    }

    /**
     * @param f
     * @return
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    protected int countFeatures(File f) throws InstantiationException, IllegalAccessException {
        int count = 0;
        InputStream is = null;
        SimpleObjectInputStream ois = null;
        try {
            is = new FileInputStream(f);
            ois = new SimpleObjectInputStream(is);
            Object next = null;
            while ((next = ois.readObject()) != null) {
                Feature feature = (Feature) next;
                Assert.assertNotNull(feature);
                count++;
            }
            ois.close();
        } catch (IOException e) {
            return count;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return count;
    }

    /**
     * Create array of features with all possible MultiGeometry geometries:
     * MultiPoint, MultiLine, MultiLinearRings, MultiPolygons, GeometryBag
     *
     * @return
     */
    protected static List<Feature> getMultiGeometries() {
        List<Feature> feats = new ArrayList<>();

        List<Line> lines = new ArrayList<>();
        List<Point> pts = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            pts.add(new Point(i * .01, i * .01));
        }

        Geometry g = new MultiPoint(pts);
        feats.add(addFeature(g)); // MultiPoint

        Geodetic2DPoint center = g.getCenter();
        GeometryBag bag = new GeometryBag();
        bag.add(new Point(center));
        bag.addAll(pts);
        feats.add(addFeature(bag)); // GeometryBag with all points

        Line line = new Line(pts);
        line.setTessellate(true);
        lines.add(line);
        pts = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            pts.add(new Point(i * .01 + 0.1, i * .01 + 0.1));
        }
        line = new Line(pts);
        line.setTessellate(true);
        lines.add(line);
        g = new MultiLine(lines);
        feats.add(addFeature(g)); // MultiLine

        List<LinearRing> rings = new ArrayList<>(5);
        pts = new ArrayList<>();
        pts.add(new Point(.10, .20));
        pts.add(new Point(.10, .10));
        pts.add(new Point(.20, .10));
        pts.add(new Point(.20, .20));
        pts.add(pts.get(0)); // add first as last
        LinearRing ring = new LinearRing(pts);
        rings.add(ring);
        List<Point> pts2 = new ArrayList<>(5);
        pts2.add(new Point(.05, .25));
        pts2.add(new Point(.05, .05));
        pts2.add(new Point(.25, .05));
        pts2.add(new Point(.25, .25));
        pts2.add(pts2.get(0)); // add first as last
        rings.add(new LinearRing(pts2));
        g = new MultiLinearRings(rings);
        feats.add(addFeature(g)); // MultiLinearRings w/2 rings

        pts = new ArrayList<>(5);
        pts.add(new Point(.10, .10));
        pts.add(new Point(.10, -.10));
        pts.add(new Point(-.10, -.10));
        pts.add(new Point(-.10, .10));
        pts.add(pts.get(0)); // add first as last
        LinearRing outer = new LinearRing(pts);
        pts = new ArrayList<>(5);
        pts.add(new Point(.05, .05));
        pts.add(new Point(.05, -.05));
        pts.add(new Point(-.05, -.05));
        pts.add(new Point(-.05, .05));
        pts.add(pts.get(0)); // add first as last
        List<LinearRing> innerRings = Collections.singletonList(new LinearRing(pts));
        Polygon p = new Polygon(outer, innerRings);
        g = new MultiPolygons(Arrays.asList(new Polygon(ring), p));
        feats.add(addFeature(g)); // MultiPolygons with 2 polygons

        Circle circle = new Circle(pts.get(0).getCenter(), 50);
        g = new GeometryBag(Arrays.asList((Geometry) pts.get(0), circle));
        feats.add(addFeature(g)); // GeometryBag with point and Circle

        return feats;
    }

    public static Point getRandomPoint(double radius) {
        double lat = 40.0 + (radius * nextDouble());
        double lon = 40.0 + (radius * nextDouble());
        return new Point(lat, lon);
    }

    public static Point getRandomPoint() {
        return getRandomPoint(5);
    }

    protected static Point getRingPoint(Point cp, int n, int total, double size, double min) {
        double lat = cp.getCenter().getLatitudeAsDegrees();
        double lon = cp.getCenter().getLongitudeAsDegrees();
        double theta = Math.toRadians(360.0 * n / total);
        double magnitude = min + nextDouble() * size;
        double dy = magnitude * Math.sin(theta);
        double dx = magnitude * Math.cos(theta);
        return new Point(lat + dy, lon + dx);
    }

    /**
     * This method is used to generate a random Geodetic3DPoint
     * that is near the surface of the earth (between -12,000 and +26,000 meters
     * from the surface of the Elliposoid earth model).
     *
     * @return a random Geodetic3DPoint
     */
    public static Geodetic3DPoint random3dGeoPoint() {
        double maxBelow = 12000.0; // deeper than max ocean depth (Marianas Trench)
        double maxAbove = 26000.0; // higher than max altitude of SR-71 spy plane

        // Define a random Geodetic point
        double lonDeg = (nextDouble() * 360.0) - 180.0;  // lonDeg
        Longitude lon = new Longitude(lonDeg, Angle.DEGREES);
        double latDeg = (nextDouble() * 180.0) - 90.0;  // latDeg
        Latitude lat = new Latitude(latDeg, Angle.DEGREES);
        double elev = nextDouble() * ((randomBool()) ? maxAbove : -maxBelow);
        return new Geodetic3DPoint(lon, lat, elev);
    }

    private static Feature addFeature(Geometry g) {
        Feature f = new Feature();
        f.setName(Integer.toString(++id));
        /*
        String type = g.getClass().getName();
        int ind = type.lastIndexOf('.');
        if (ind > 0) type = type.substring(ind + 1);
        */
        f.setDescription(g.toString());
        f.setGeometry(g);
        return f;
    }

}
