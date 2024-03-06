package org.opensextant.geodesy.test;

import java.util.Random;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.FrameOfReference;
import org.opensextant.geodesy.GeoPoint;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;

public class TestGeodetic3DPoint extends TestCase {

    private final Random r = new Random();

    /**
     * This method is used to generate a random Geodetic3DPoint
     * that is near the surface of the earth (between -12,000 and +26,000 meters from the
     * surface of the Elliposoid earth model).
     *
     * @param r a Random object to use for selecting the coordinate components
     * @return a random Geodetic3DPoint suitable for testing conversions with
     */
    public static Geodetic3DPoint randomGeoPoint(Random r) {
        double maxBelow = 12000.0; // deeper than max ocean depth (Marianas Trench)
        double maxAbove = 26000.0; // higher than max altitude of SR-71 spy plane

        // Define a random Geodetic point
        double lonDeg = (r.nextDouble() * 360.0) - 180.0;  // lonDeg
        Longitude lon = new Longitude(lonDeg, Angle.DEGREES);
        double latDeg = (r.nextDouble() * 180.0) - 90.0;  // latDeg
        Latitude lat = new Latitude(latDeg, Angle.DEGREES);
        double elev = r.nextDouble() * ((r.nextBoolean()) ? maxAbove : -maxBelow);
        return new Geodetic3DPoint(lon, lat, elev);
    }

    /**
     * This method performs pair-wise format conversions to test for consistency
     * among the GeoPoint representations, using the default FrameOfReference.
     */
    public void testConversions() {
        FrameOfReference f = new FrameOfReference();
        for (int i = 0; i < 1000; i++) {
            GeoPoint a1 = randomGeoPoint(r);
            GeoPoint a2 = f.toTopocentric(a1);
            GeoPoint a3 = f.toGeocentric(a1);
            GeoPoint a4 = f.toGeodetic(a2);
            assertTrue(f.proximallyEquals(a1, a2));
            assertTrue(f.proximallyEquals(a1, a3));
            assertTrue(f.proximallyEquals(a1, a4));
        }
    }

	/**
     * This method performs format conversions with string constructors in degrees and dms
     */
    public void testRandomEquals() {
		for (int i = 0; i < 1000; i++) {
            Geodetic3DPoint a1 = randomGeoPoint(r);
			Geodetic3DPoint a2 = new Geodetic3DPoint(a1.getLongitude(), a1.getLatitude(), a1.getElevation());
			// note by making equals() work in round off errors (such as in this case) using Angle.equals() vs phi1=phi2 && lamb1==lamb2
			// but break contract in hashCode such that a.equals(b) -> true but hashCode(a) may not equal hashCode(b)
			assertEquals(a1, a2);
			assertEquals(a1.hashCode(), a2.hashCode());

			// for symmetric tests to work elevation must be non-zero
			final double elevation = a1.getElevation();
			if (elevation == 0.0 || Math.abs(elevation) < 1e-8) a1.setElevation(1234.5);

			// test symmetric equals tests a.equals(b) -> b.equals(a)
			Geodetic2DPoint pt2 = new Geodetic2DPoint(a1.getLongitude(), a1.getLatitude());
			assertFalse(pt2.equals(a1));
			assertFalse(a1.equals(pt2));
			a1.setElevation(0);
			assertEquals(pt2, a1); // pt2.equals(al) -> a1.equals(pt2)
			assertEquals(a1, pt2);
		}
    }

	private final Geodetic3DPoint a = new Geodetic3DPoint(new Longitude(-1), new Latitude(1), 31);
	private final Geodetic3DPoint b = new Geodetic3DPoint(new Longitude(-1), new Latitude(1), 31);
	private final Geodetic3DPoint c = new Geodetic3DPoint(new Longitude(-1.001), new Latitude(1), 31);
	private final Geodetic3DPoint d = new Geodetic3DPoint(new Longitude(-1), new Latitude(1), 31.001);

	public void testEquals() {
		// test equality with known geo-points
		assertEquals(a, b);
		assertFalse(a.equals(c));
		assertFalse(a.equals(d));
		assertFalse(c.equals(d));

		// test equality with any possible round off errors
		Geodetic3DPoint a2 = new Geodetic3DPoint(new Longitude(Math.toRadians(a.getLongitude().inDegrees())),
									new Latitude(Math.toRadians(a.getLatitude().inDegrees())), a.getElevation());
		assertEquals(a, a2);
		assertEquals(a.hashCode(), a2.hashCode());

		// approximate equals test for elevations up to 3 decimal places
		Geodetic3DPoint a3 = new Geodetic3DPoint(a.getLongitude(), a.getLatitude(), a.getElevation() + 10e-6);
		assertEquals(a, a3);
		assertEquals(a.hashCode(), a3.hashCode());
	}

	public void testNullCompare() {
		Geodetic3DPoint other = null;
		assertFalse(a.equals(other));
		Geodetic2DPoint p2 = null;
		assertFalse(a.equals(p2));
	}

    /**
     * Test 2d vs 3d points with 3d elavations at/close to 0
     */
    public void testMismatchedEquals() {
        Geodetic2DPoint p1 = makePoint(-81.9916466079043, 29.9420387052815, 5000.1);
		Geodetic2DPoint p2 = makePoint(-81.9916466079043, 29.9420387052815, 0.0);
        if (p1.equals(p2)) fail("different elevation but equals() == true");
        if (p2.equals(p1)) fail("different elevation but equals() == true");
		Geodetic2DPoint p3 = makePoint(-81.9916466079043, 29.9420387052815);
        assertEquals("2d with elev=0 and 3d point same lat/lon", p2, p3);
        assertEquals("2d with elev=0 and 3d point same lat/lon", p3, p2);
		Geodetic2DPoint p4 = makePoint(-81.9916466079043, 29.9420387052815, 1e-6);
        //assertEquals(p3, new Geodetic2DPoint(p4.getLongitude(), p4.getLatitude()));
        assertEquals("3d with elev=1e-4 and 3d point same lat/lon", p2, p4);
        assertEquals("3d with elev=1e-4 and 3d point same lat/lon", p4, p2);
        assertEquals("3d with elev=1e-4 and 3d point same lat/lon", p3, p4);
    }

	public void testHashCode() {
		assertEquals(a.hashCode(), b.hashCode());
		assertTrue(a.hashCode() != c.hashCode()); // this test isn't required by equals-hashCode contract but by how the hashCode is computed
	}

    public void testToString() {
        Geodetic3DPoint p = TestGeoPoint.randomGeodetic3DPoint(r);
        int prevLen = 0;

        String base = p.toString();
        String prefix = base.substring(0, base.indexOf(' '));      // e.g. "75 deg"
        char letter = base.charAt(base.indexOf(')') - 1);          // e.g. "S"

        // System.out.println("base=" + base + " pre=" + prefix + " letter=" + letter);

        for(int i=0; i < 6; i++) {
            /*
           length of string should increase two digits for each fractional # of digits in lat & lon
           0 (133 deg 47' 15" W, 73 deg 17' 33" S) @ 733484m
           1 (133 deg 47' 14.8" W, 73 deg 17' 32.7" S) @ 733484.0m
           2 (133 deg 47' 14.81" W, 73 deg 17' 32.67" S) @ 733484.00m
           3 (133 deg 47' 14.806" W, 73 deg 17' 32.675" S) @ 733484.000m
           4 (133 deg 47' 14.8060" W, 73 deg 17' 32.6749" S) @ 733484.0000m
           5 (133 deg 47' 14.80597" W, 73 deg 17' 32.67487" S) @ 733484.00000m
            ...
            */
            String s = p.toString(i);
            System.out.println(i + " " + s);
            int len = s.length();
            assertTrue(len >= prevLen + 2);
            // simple tests: 1) each should start with same degrees and 2) end with same hemisphere
            assertTrue(s.startsWith(prefix));
            assertEquals(letter, s.charAt(s.indexOf(')') - 1));
            prevLen = len;
        }
    }

    private static Geodetic2DPoint makePoint(double lon, double lat, double elev) {
		return new Geodetic3DPoint(
				new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES),
				elev);
	}

	private static Geodetic2DPoint makePoint(double lon, double lat) {
		return new Geodetic2DPoint(
				new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES));
	}

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestGeodetic3DPoint.class);
        new TestRunner().doRun(suite);
    }
}
