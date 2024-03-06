package org.opensextant.geodesy.test;

import java.util.Random;

import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.geodesy.UPS;
import static org.junit.Assert.*;

/**
 * User: MATHEWS
 * Date: 6/21/11 10:41 AM
 */
public class TestUPS {

	@Test
	public void testEquals() {
		Geodetic2DPoint g1 = new Geodetic2DPoint(
				new Longitude(-79, 23, 13.7),
				new Latitude(88, 38, 33.24));
		UPS u1 = new UPS(g1);
		assertTrue(u1.equals(u1));
		assertEquals('Y', u1.getPolarZone());

		UPS u2 = new UPS(u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
		assertEquals(u1, u2);
		assertEquals(u1.hashCode(), u2.hashCode());
		assertEquals(u1.getLatitude(), u2.getLatitude());
		assertEquals(u1.getLongitude(), u2.getLongitude());
		//u1=WGS 84 UPS Y 1851864m E, 1972243m N
		//u2=WGS 84 UPS Z 1851864m E, 1972243m N
		//assertEquals(u1.getPolarZone(), u2.getPolarZone()); // not same

		UPS u3 = new UPS(u1.getEllipsoid(), u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
		assertEquals(u1, u3);
		assertEquals(u1.hashCode(), u3.hashCode());

		UPS u4 = new UPS(u1.getEllipsoid(), g1);
		assertEquals(u1, u4);
		assertEquals(u1.hashCode(), u4.hashCode());

		UPS u5 = new UPS(u1.getEllipsoid(), g1.getLongitude(), g1.getLatitude());
		assertEquals(u1, u5);
		assertEquals(u1.hashCode(), u5.hashCode());

		UPS u6 = new UPS(u1.getHemisphere(), u1.getEasting(), u1.getNorthing() + 500);
		assertFalse(u1.equals(u6));

		UPS u7 = null;
		assertFalse(u1.equals(u7));

		UPS u8 = new UPS(g1.getLongitude(), g1.getLatitude());
		// u8 => WGS 84 UPS Y 1851864m E, 1972243m N
		assertEquals(u1, u8);
		assertEquals(u1.getPolarZone(), u8.getPolarZone());

		Object other = g1;
		assertFalse(u1.equals(other));
	}

	@Test
	public void testInvalidCreation() {
		try {
			// Hemisphere 'X', should be 'N' or 'S'
			new UPS('X', 2364053.5818, 1718278.1249);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// Northing valid range (0 to 4,000,000 meters)
			new UPS('N', 0, 4818278.1249);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// Easting valid range (0 to 4,000,000 meters)
			new UPS('N', 4818278.1249, 0);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// out of legal latitude range (+83.5 deg to +90 deg) for UPS Northern Hemisphere
			new UPS(new Geodetic2DPoint(
				new Longitude(-79, 23, 13.7),
				new Latitude(45, 0, 0)));
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}

		try {
			// outside legal latitude range (-79.5 deg to -90 deg) for UPS Southern Hemisphere
			new UPS(new Geodetic2DPoint(
				new Longitude(-79, 23, 13.7),
				new Latitude(-45, 0, 0)));
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testSouthPole() {
		// WGS 84 UPS B 2m E, 2m N
		UPS u = new UPS('S', 0, 2);
		assertEquals('B', u.getPolarZone());
		assertEquals(2, u.getNorthing(), 1e-6);
		assertEquals(0, u.getEasting(), 1e-6);
		//System.out.println(u);
	}

	@Test
	public void testProjections() {
        Geodetic2DPoint g1, g2;
        UPS u1, u2;
        int fractDig = 2; // Fractional digits to print in toString conversions

		// WGS 84 UPS A 997635.0902m E, 2464957.4007m N => (65� 6' 55" W, 80� 4' 17" S)
        g1 = new Geodetic2DPoint(new Longitude(-65, 6, 55),
				new Latitude(-80, 4, 17));
        u1 = new UPS(g1);
        u2 = new UPS(u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
        g2 = u2.getGeodetic();
        assertEquals(g1.toString(fractDig), g2.toString(fractDig));

		// WGS 84 UPS Z 2364053.5818m E, 1718278.1249m N => (52� 15' 56" E, 85� 51' 20" N)
		g1 = new Geodetic2DPoint(new Longitude(-52, 15, 56),
				new Latitude(85, 51, 20));
        u1 = new UPS(g1);
        u2 = new UPS(u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
        g2 = u2.getGeodetic();
		assertEquals(g1.toString(fractDig), g2.toString(fractDig));

		// Do some random Geodetic point conversion round trips
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            double lonDeg = (r.nextDouble() * 360.0) - 180.0;
			// Latitude range +83.5 deg to +90 for UPS Northern Hemisphere
            double latDeg = 83.5 + (6.5 * r.nextDouble());
            g1 = new Geodetic2DPoint(
                    new Longitude(lonDeg, Angle.DEGREES), new Latitude(latDeg, Angle.DEGREES));
            u1 = new UPS(g1);
            u2 = new UPS(u1.getHemisphere(), u1.getEasting(), u1.getNorthing());
            g2 = u2.getGeodetic();
            assertEquals(g1.toString(fractDig), g2.toString(fractDig));
        }
	}

	@Test
	public void testToString() {
		UPS u1 = new UPS('N', 621160.08, 3349893.03);
		// tp.toString(0) WGS 84 UPS Z 621160m E, 3349893m N
		// tp.toString(4) WGS 84 UPS Z 621160.0800m E, 3349893.0300m N

		String base = u1.toString();
		assertEquals(base, u1.toString(0)); // toString() same as toString(0)
		// System.out.println(base);
		int prevLen = base.length();
		String prefix = base.substring(0, base.indexOf("m E")); // WGS 84 UPS Z 621160
		// System.out.printf("prefix=[%s]%n", prefix);

		for (int i = 1; i < 6; i++) {
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
}
