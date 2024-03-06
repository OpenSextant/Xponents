package org.opensextant.geodesy.test;

import java.util.Random;

import junit.framework.TestCase;
import org.opensextant.geodesy.GeocentricPoint;

/**
 * @author Jason Mathews, MITRE Corp.
 * Date: Mar 23, 2009 2:47:23 PM
 */
public class TestGeocentricPoint extends TestCase {

    private final Random r = new Random();

    /**
     * This method is used to generate a random GeocentricPoint.
     *
     * @return a random GeocentricPoint suitable for testing conversions with
     */
    private GeocentricPoint randomGeocentricPoint() {
        // Define a random Geodetic point
        double x = Math.abs(r.nextDouble());
        double y = Math.abs(r.nextDouble());
        double z = Math.abs(r.nextDouble());
        return new GeocentricPoint(x, y, z);
    }

    public void testGetSet() {
        GeocentricPoint a2 = new GeocentricPoint(a.getX() + 1, a.getY(), a.getZ() + 1);
        assertFalse(a.equals(a2));
        a2.setX(a.getX());
        a2.setY(a.getY());
        a2.setZ(a.getZ());
        assertEquals(a, a2);
    }

    public void testRandomEquals() {
        // FrameOfReference f = new FrameOfReference();
        for (int i = 0; i < 1000; i++) {
            GeocentricPoint a1 = randomGeocentricPoint();
            GeocentricPoint a2 = new GeocentricPoint(a1.getX(), a1.getY(), a1.getZ());
            // note by making equals() work in round off errors (such as in this case) using Angle.equals() vs phi1=phi2 && lamb1==lamb2
            // but break contract in hashCode such that a.equals(b) -> true but hashCode(a) may not equal hashCode(b)
            assertEquals(a1, a2);
            assertEquals(a1.hashCode(), a2.hashCode());
            a2.setX(a1.getX() * 2);
            assertNotSame(a1, a2);
        }
    }

    private final GeocentricPoint a = new GeocentricPoint(100, 1, 1);
    private final GeocentricPoint b = new GeocentricPoint(100, 1, 1);
    private final GeocentricPoint c = new GeocentricPoint(100.1, 1, 1.1);

    public void testEquals() {
        // test equality with known geo-points
        assertEquals(a, b);
        assertFalse(a.equals(c));

        GeocentricPoint a2 = new GeocentricPoint(a.getX(), a.getY(), a.getZ());
        assertEquals(a, a2);
    }

    public void testNullPointCompare() {
        GeocentricPoint g2 = null;
        assertFalse(a.equals(g2));
    }

    public void testHashCode() {
        assertEquals(a.hashCode(), b.hashCode());
        assertNotSame("hashCode different by design", a.hashCode(), c.hashCode()); // this test isn't required by equals-hashCode contract but by how the hashCode is computed
    }

    public void testToString() {
        GeocentricPoint p = a;
        int prevLen = 0;

        String base = p.toString();
        String prefix = base.substring(0, base.indexOf('m'));   // e.g. "100m
        String suffix = base.substring(base.length() - 2);      // e.g. "m)"

        /*
        length of string should increase two digits for each fractional # of digits in x, y, z
        (100m, 1m, 1m)
		(100.0m, 1.0m, 1.0m)
		(100.00m, 1.00m, 1.00m)
		(100.000m, 1.000m, 1.000m)
		(100.0000m, 1.0000m, 1.0000m)
		(100.00000m, 1.00000m, 1.00000m)
         ...
         */
        for (int i = 0; i < 6; i++) {
            String s = p.toString(i);
            //System.out.println(s);
            int len = s.length();
            assertTrue(len >= prevLen + 2);
            // simple tests: 1) each should start with same degrees and 2) end with same hemisphere letter
            assertTrue(s.startsWith(prefix));
            assertTrue(s.endsWith(suffix));
            prevLen = len;
        }
    }

}
