/****************************************************************************************
 *  TestGeoPoint.java
 *
 *  Created: Mar 27, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2006
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
package org.opensextant.geodesy.test;

import java.util.Random;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.opensextant.geodesy.*;

public class TestGeoPoint extends TestCase {

    private final Random r = new Random();

    /**
     * This method is used to generate a random Geodetic2DPoint.
     *
     * @param r a Random object to use for selecting the coordinate components
     * @return a random Geodetic2DPoint suitable for testing conversions with
     */
    public static Geodetic2DPoint randomGeodetic2DPoint(Random r) {
        // Define a random Geodetic point
        double lonDeg = (r.nextDouble() * 360.0) - 180.0;  // lonDeg
        Longitude lon = new Longitude(lonDeg, Angle.DEGREES);
        double latDeg = (r.nextDouble() * 180.0) - 90.0;  // latDeg
        Latitude lat = new Latitude(latDeg, Angle.DEGREES);
        return new Geodetic2DPoint(lon, lat);
    }

    public static Geodetic3DPoint randomGeodetic3DPoint(Random r) {
        Geodetic2DPoint pt = randomGeodetic2DPoint(r);
        double elev = r.nextInt(1000000);  // stress test at 1,000 km.
        return new Geodetic3DPoint(pt.getLongitude(), pt.getLatitude(), elev);
    }

    /**
     * This method performs pair-wise format conversions to test for consistency
     * among the GeoPoint representations, using the default FrameOfReference.
     */
    public void testConversions() {
        FrameOfReference f = new FrameOfReference();
        for (int i = 0; i < 1000; i++) {
            GeoPoint a1 = randomGeodetic2DPoint(r);
            GeoPoint a2 = f.toTopocentric(a1);
            GeoPoint a3 = f.toGeocentric(a1);
            GeoPoint a4 = f.toGeodetic(a2);
            assertTrue(f.proximallyEquals(a1, a2));
            assertTrue(f.proximallyEquals(a1, a3));
            assertTrue(f.proximallyEquals(a1, a4));
        }
    }

    public void testReference() {
        Geodetic3DPoint p = randomGeodetic3DPoint(r);
        FrameOfReference f = new FrameOfReference(p);
        assertEquals(p, f.getTopographicOrigin());

        final Ellipsoid ellip = Ellipsoid.getInstance("WGS 84");
        f = new FrameOfReference(ellip);
        assertEquals(ellip, f.getEllipsoid());
    }

	public void testBadReference() {
		Ellipsoid ellip = null;
		try {
			new FrameOfReference(ellip);
			fail("Expected NullPointerException");
		} catch(NullPointerException npe) {
			// expected
		}
		try {
			new FrameOfReference(ellip, randomGeodetic3DPoint(r));
			fail("Expected NullPointerException");
		} catch(NullPointerException npe) {
			// expected
		}
	}

    /**
     * This method performs format conversions with string constructors in degrees and dms
     */
    public void testRandomEquals() {
        for (int i = 0; i < 1000; i++) {
            Geodetic2DPoint a1 = randomGeodetic2DPoint(r);
            Geodetic2DPoint a2 = new Geodetic2DPoint(a1.getLongitude().inDegrees() + "," + a1.getLatitude().inDegrees());
            // note by making equals() work in round off errors (such as in this case) using Angle.equals() vs phi1=phi2 && lamb1==lamb2
            // but break contract in hashCode such that a.equals(b) -> true but hashCode(a) may not equal hashCode(b)
            assertEquals(a1, a2);
            assertEquals(a1.hashCode(), a2.hashCode());

            String aStr = a1.toString(7);
            Geodetic2DPoint a3 = new Geodetic2DPoint(aStr);

            /*
            System.out.println(a1.hashCode() + " " + a2.hashCode() + " " + a1.equals(a2)
                    + "\n\t" + aStr
                    + "\n\t" + a3.toString(6)
                    + "\n\t" + a1.getLongitude().inRadians() + " " + a1.getLatitude().inRadians()
                    + "\n\t" + a2.getLongitude().inRadians() + " " + a2.getLatitude().inRadians()
                    + "\n\t" + a3.getLongitude().inRadians() + " " + a3.getLatitude().inRadians());
            */

            /*
               Following test uncovered a bug near poles with decimal angle = 0 in
               south or west resuling in angle deg = -0 being used in floating point
               calculations and sign being incorrectly assigned.

                   (0 deg 46' 41.23530" W, 6 deg 18' 6.01751" N) parsed with wrong sign on longitude:
                   (0 deg 46' 41.23530" E, 6 deg 18' 6.01751" N)
                   a1 lon-lat (rads) = -0.013580771984344651 0.10998491659692418
                   a3 lon-lat (rads) =  0.013580771974469753 0.1099849165873849

                   dealing with -0 had problems with sign test: fixed in Angle(String valStr) constructor.

                   Note: 6 decimal places in seconds should be enough decimals such that
                   round off error adjustment in equals() is true in nearly all cases.

               Here's cases that fails if only using 5 decimal places for seconds where Angle.EPISON = 1e-7:
                   (60 deg 41' 16.04803" E, 0 deg 0' 12.67506" N)
                   (60 deg 41' 16.04803" E, 0 deg 0' 12.67506" N)
                   a1 lon-lat (rads) = 1.0592017708190238 6.145040119951688E-5
                   a3 lon-lat (rads) = 1.0592017707968808 6.145042496884236E-5
                   a1 != a3

                   If change EPSILON in Angle equals(double, double) from current value of 1e-7 then
                   will need to adjust # fractional digits of precision used above.
                   */
            assertEquals(a1, a3);
        }
    }

    private final Geodetic2DPoint a = new Geodetic2DPoint(new Longitude(-1), new Latitude(1));
    private final Geodetic2DPoint b = new Geodetic2DPoint(new Longitude(-1), new Latitude(1));
    private final Geodetic2DPoint c = new Geodetic2DPoint(new Longitude(-1.001), new Latitude(1));

	public void testLatLonAngle() {
		Latitude lat = new Latitude(new Angle());
		Longitude lon = new Longitude(new Angle());
		Geodetic2DPoint a = new Geodetic2DPoint(lon, lat);
		Geodetic2DPoint b = new Geodetic2DPoint( new Longitude(new Angle(0, Angle.DEGREES)),
				new Latitude(new Angle(0, Angle.DEGREES)));
		assertEquals(a, b);
	}

    public void testEquals() {
        // test equality with known geo-points
        assertEquals(a, b);
        assertFalse(a.equals(c));

        Geodetic2DPoint a2 = new Geodetic2DPoint(new Longitude(a.getLongitude().inRadians()),
                new Latitude(a.getLatitude().inRadians()));
        assertEquals(a, a2);

        // test equality with any possible round off errors
        Geodetic2DPoint a3 = new Geodetic2DPoint(new Longitude(Math.toRadians(a.getLongitude().inDegrees())),
                new Latitude(Math.toRadians(a.getLatitude().inDegrees())));

        assertEquals(a, a3);
    }

    public void testNullCompare() {
        Geodetic2DPoint other = null;
        assertFalse(a.equals(other));
    }

    public void testHashCode() {
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.hashCode() != c.hashCode()); // this test isn't required by equals-hashCode contract but by how the hashCode is computed
    }

    public void testStringCreation() {
        String[] latLons = {
                // "37d25'19.07\"N, 122d05'06.24\"W", // this format does not work
                "(122\u00B0 5' 6\" W, 37\u00B0 25' 19\" N)",
                "37 25 19.07 N, 122 05 06.24 W", // latitude first
                "37 25'19.07\"N, 122 05'06.24\"W", // no whitespace between min and sec fields
                "(42\u00B0 22' 11.77\" N, 71\u00B0 1' 40.30\" W)", // latitude first
                "(51 deg 28' 15.19\" N, 45 deg 27' 33.41\" W)",
                "51 deg 28' 15.19\" N, 45 deg 27' 33.41\" W",
                "(12 34 56E, 45 34 23N)",
                "12 34 56E, 45 34 23N"
        };
        for (String latLon : latLons) {
            // System.out.println(latLon);
            try {
                new Geodetic2DPoint(latLon);
            } catch (IllegalArgumentException e) {
                System.out.println("Failed on " + latLon);
                throw e;
            }
            //System.out.println("\t\t\t\t" + pt);
        }
    }

    public void testToString() {
        Geodetic2DPoint p = randomGeodetic2DPoint(r);
        int prevLen = 0;

        String base = p.toString();
        String prefix = base.substring(0, base.indexOf(' '));   // e.g. "75 deg"
        String suffix = base.substring(base.length() - 2);      // e.g. "N)"

        /*
        length of string should increase two digits for each fractional # of digits in lat & lon
        (75 deg 10' 31" E, 89 deg 17' 7" N)
        (75 deg 10' 31.5" E, 89 deg 17' 7.2" N)
        (75 deg 10' 31.46" E, 89 deg 17' 7.18" N)
        (75 deg 10' 31.455" E, 89 deg 17' 7.177" N)
        (75 deg 10' 31.4551" E, 89 deg 17' 7.1768" N)
        (75 deg 10' 31.45508" E, 89 deg 17' 7.17678" N)
         ...
         */
        for (int i = 0; i < 6; i++) {
            String s = p.toString(i);
            System.out.println(s);
            int len = s.length();
            assertTrue(len >= prevLen + 2);
            // simple tests: 1) each should start with same degrees and 2) end with same hemisphere letter
            assertTrue(s.startsWith(prefix));
            assertTrue(s.endsWith(suffix));
            prevLen = len;
        }
    }

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestGeoPoint.class);
        new TestRunner().doRun(suite);
    }
}
