/****************************************************************************************
 *  TestUTM.java
 *
 *  Created: Mar 28, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2006
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
package org.opensextant.geodesy.test;

import java.util.Random;

import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.geodesy.UTM;
import static org.junit.Assert.*;

public class TestUTM {

    /**
     * This method tests UTM methods to determine Longitude Zones and their limits
     */
	@Test
	public void testLonZones() {
        int lonZone;
        char latBand = 'N';
        double lonDeg;
        // Test Lon Zones by longitude degrees (1° increment lons at lat band 'N')
        for (lonDeg = -180.0; lonDeg < 180.0; lonDeg += 1.0) {
            lonZone = UTM.getLonZone(lonDeg, latBand);
            assertTrue((UTM.minLonDegrees(lonZone, latBand) <= lonDeg) &&
                       (lonDeg < UTM.maxLonDegrees(lonZone, latBand)));
        }
        // Test each Lon Zones for Central Meridian inclusion
        for (lonZone = 1; lonZone <= 60; lonZone += 1) {
            lonDeg = UTM.getCentralMeridian(lonZone, latBand).inDegrees();
            assertTrue((UTM.minLonDegrees(lonZone, latBand) <= lonDeg) &&
                       (lonDeg < UTM.maxLonDegrees(lonZone, latBand)));
        }
        // Test random longitude degrees and random lon zones
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            lonDeg = (r.nextDouble() * 360.0) - 180.0;  // lonDeg
            lonZone = UTM.getLonZone(lonDeg, latBand);
            assertTrue((UTM.minLonDegrees(lonZone, latBand) <= lonDeg) &&
                       (lonDeg < UTM.maxLonDegrees(lonZone, latBand)));
            lonZone = r.nextInt(60) + 1;
            lonDeg = UTM.getCentralMeridian(lonZone, latBand).inDegrees();
            assertTrue((UTM.minLonDegrees(lonZone, latBand) <= lonDeg) &&
                       (lonDeg < UTM.maxLonDegrees(lonZone, latBand)));
        }
    }

    /**
     * This method tests UTM methods to determine Latitude Bands and their limits
     */
	@Test
	public void testLatBands() {
        char latBand;
        double latDeg;
        // Test the latitude band char mappings and band ranges
        // (note that 84° is considered in band 'X', hence use of '<=' for max)
        for (latDeg = -90.0; latDeg < 90.0; latDeg += 1.0) {
            try {
                latBand = UTM.getLatBand(latDeg);
                assertTrue((UTM.minLatDegrees(latBand) <= latDeg) &&
                           (latDeg <= UTM.maxLatDegrees(latBand)));
            } catch (Exception ex) {
                //System.err.println(ex.toString());
            }
        }
        // Test random latitude degrees and lat band constraints
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            latDeg = (164.0 * r.nextDouble()) - 80.0;
            latBand = UTM.getLatBand(latDeg);
            assertTrue((UTM.minLatDegrees(latBand) <= latDeg) &&
                       (latDeg < UTM.maxLatDegrees(latBand)));
        }
    }

	@Test
	public void testInvalidCreation() {
		try {
			// Hemisphere 'X', should be 'N' or 'S'
			new UTM(31, 'X', 353305.0, 7100467.0);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// outside UTM longitudinal zone valid range (1 to 60)
			new UTM(0, 'N', 353305.0, 7100467.0);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// outside easting valid range: 100,000 to 900,000 meters
			new UTM(31, 'N', 0.0, 7100467.0);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// outside northing valid range (0 to 10,000,000 meters)
			new UTM(31, 'N', 353305.0, -1.0);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testEquals() {
		Geodetic2DPoint g1 = new Geodetic2DPoint(
				new Longitude(-79, 23, 13.7),
				new Latitude(43, 38, 33.24));
		UTM u1 = new UTM(g1);

		assertTrue(u1.equals(u1));

		UTM u2 = new UTM(u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
		assertEquals(u1, u2);
		assertEquals(u1.hashCode(), u2.hashCode());
		assertEquals(u1.getLatitude(), u2.getLatitude());
		assertEquals(u1.getLongitude(), u2.getLongitude());

		UTM u3 = new UTM(u1.getEllipsoid(), u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
		assertEquals(u1, u3);
		assertEquals(u1.hashCode(), u3.hashCode());

		UTM u4 = new UTM(u1.getEllipsoid(), g1);
		assertEquals(u1, u4);
		assertEquals(u1.hashCode(), u4.hashCode());

		UTM u5 = new UTM(u1.getEllipsoid(), g1.getLongitude(), g1.getLatitude());
		assertEquals(u1, u5);
		assertEquals(u1.hashCode(), u5.hashCode());

		UTM u6 = new UTM(u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing() + 500);
		assertFalse(u1.equals(u6));

		UTM u7 = null;
		assertFalse(u1.equals(u7));

		Object other = g1;
		assertFalse(u1.equals(other));
	}

    /**
     * This method tests both some known point cases as well as some randomly
     * selected Geodetic points, for consistency in round trip conversions at
     * 2 fractional digits of precision (hundreths of arc seconds and hundreths
     * of meters)
     */
	@Test
    public void testProjections() {
        // Test Case : Toronto's CNN Tower
        //   WGS 84 Geodetic2DPoint (lon-lat): (79° 23' 13.70" W, 43° 38' 33.24" N)
        //   Zone 17, Band T (hemisphere 'N'), 630084m east, 4833439m north
        Geodetic2DPoint g1, g2;
        UTM u1, u2;
        int fractDig = 2; // Fractional digits to print in toString conversions

        // utm.toGeodetic(17, 'N', 630084.30, 4833438.55) => (79° 23' 13.70" W, 43° 38' 33.24" N)
        //  MGRS? 17TPJ 30084 33439
        g1 = new Geodetic2DPoint(
                new Longitude(-79, 23, 13.700468),
                new Latitude(43, 38, 33.240046));
        u1 = new UTM(g1);
        u2 = new UTM(u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
        g2 = u2.getGeodetic();
        assertEquals(g1.toString(fractDig), g2.toString(fractDig));

        u1 = new UTM(17, 'N', 630084.30, 4833438.55);
        g1 = u1.getGeodetic();
        u2 = new UTM(g1);
        assertEquals(u1.toString(fractDig), u2.toString(fractDig));

        // Another test case
        //   NAD 83 Geodetic2DPoint (lon-lat): (97° 44' 25.19" W, 30° 16' 28.82" N)
        //   UTM equivalent: Zone 14 R 621160.98, 3349893.53
        g1 = new Geodetic2DPoint(new Longitude(-97, 44, 25.19), new Latitude(30, 16, 28.82));
        u1 = new UTM(g1);
        u2 = new UTM(u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
        g2 = u2.getGeodetic();
        assertEquals(g1.toString(fractDig), g2.toString(fractDig));

        u1 = new UTM(14, 'N', 621160.98, 3349893.53);
        g1 = u1.getGeodetic();
        u2 = new UTM(g1);
        assertEquals(u1.toString(fractDig), u2.toString(fractDig));

        // The following test case is for a round off error that causes lonZone shift
        // if not detected
        g1 = new Geodetic2DPoint(new Longitude(0, 0, 0.0), new Latitude(64, 0, 0.0));
        u1 = new UTM(g1);
        u2 = new UTM(u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
        g2 = u2.getGeodetic();
        assertEquals(g1.toString(fractDig), g2.toString(fractDig));

        u1 = new UTM(31, 'N', 353305.0, 7100467.0);
        g1 = u1.getGeodetic();
        u2 = new UTM(g1);
        assertEquals(u1.toString(fractDig), u2.toString(fractDig));

        // Do some random Geodetic point conversion round trips
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            double lonDeg = (r.nextDouble() * 360.0) - 180.0;
            double latDeg = (164.0 * r.nextDouble()) - 80.0;
            g1 = new Geodetic2DPoint(
                    new Longitude(lonDeg, Angle.DEGREES), new Latitude(latDeg, Angle.DEGREES));
            u1 = new UTM(g1);
            u2 = new UTM(u1.getLonZone(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
            g2 = u2.getGeodetic();
            assertEquals(g1.toString(fractDig), g2.toString(fractDig));
        }
    }

	@Test
	public void testLonZone() {
		assertEquals(31, UTM.getLonZone(360, 'N'));
		assertEquals(31, UTM.getLonZone(-360, 'N'));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMinNorthing() {
		UTM.minNorthing('I');
		// UTM latitude band character ('C' to 'X", not including 'I' or 'O')
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMaxNorthing() {
		UTM.maxNorthing(1, 'O');
		// UTM latitude band character ('C' to 'X", not including 'I' or 'O')
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLatZoneLowerRange() {
		UTM.getLatBand(-81);
		// expected out of legal range (-80 deg to 84 deg) for UTM
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLatZoneUpperRange() {
		UTM.getLatBand(90);
		// expected out of legal range (-80 deg to 84 deg) for UTM
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidateLonZoneLowerRange() {
		UTM.validateLonZone(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidateLonZoneUpperRange() {
		UTM.validateLonZone(61);
	}

	@Test
	public void testToString() {
		UTM u1 = new UTM(14, 'N', 621160.08, 3349893.03);
		// tp.toString(0) WGS 84 UTM 14 N hemisphere 621160m E, 3349893m N
		// tp.toString(4) WGS 84 UTM 14 N hemisphere 621160.0800m E, 3349893.0300m N

        String base = u1.toString();
		assertEquals(base, u1.toString(0)); // toString() same as toString(0)
		int prevLen = base.length();
        String prefix = base.substring(0, base.indexOf("m E"));   // WGS 84 UTM 14 N hemisphere 621160
		// System.out.printf("prefix=[%s]%n", prefix);

		for(int i=1; i < 6; i++) {
            String s = u1.toString(i);
			// System.out.println(s);
            int len = s.length();
            assertTrue(len >= prevLen + 2);
            // simple tests: each should start with same easting whole number
			assertEquals(prefix, s.substring(0, prefix.length()));
            // assertTrue("prefix fails to match target", s.startsWith(prefix));
            prevLen = len;
		}
	}

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestUTM.class);
        new TestRunner().doRun(suite);
    }
}
