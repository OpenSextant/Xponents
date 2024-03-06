package org.opensextant.geodesy.test;

import org.junit.Test;
import org.opensextant.geodesy.FrameOfReference;
import org.opensextant.geodesy.GeoPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Topocentric3DPoint;
import static junit.framework.Assert.*;

/**
 * User: MATHEWS
 * Date: 12/5/13 3:15 PM
 */
public class TestTopocentric3DPoint {

	private final double easting = 630084;
	private final double northing = 4833439;

	@Test
	public void testCreate() {
		Topocentric3DPoint point = new Topocentric3DPoint(easting, northing, 5000);
		assertNotNull(point); // (630084m East, 4833439m North) @ 0m
		assertEquals(easting, point.getEasting(), 1e-6);
		assertEquals(northing, point.getNorthing(), 1e-6);
		assertEquals(5000, point.getElevation(), 1e-5);
		String s1 = point.toString();
		assertNotNull(s1);
		String s2 = point.toString(2);
		assertNotNull(s2);
		assertTrue(s2.length() > s1.length());

		FrameOfReference f = new FrameOfReference();
		Geodetic3DPoint pt = point.toGeodetic3D(f); // (5� 38' 31" E, 37� 10' 6" N) @ 1657072m
		assertNotNull(pt);
	}

	@Test
	public void testEquals() {
		Topocentric3DPoint p1 = new Topocentric3DPoint(easting, northing, 5000);
		Topocentric3DPoint p2 = new Topocentric3DPoint(easting, northing, 5000);
		assertEquals(p1, p2);
		assertEquals("hashCode", p1.hashCode(), p2.hashCode());
	}

	@Test
	public void testConversion() {
		Topocentric3DPoint tp = new Topocentric3DPoint(easting, northing, 5000);
		FrameOfReference f = new FrameOfReference();
		GeoPoint a2 = f.toTopocentric(tp);
		GeoPoint a3 = f.toGeocentric(tp);
		assertTrue(f.proximallyEquals(tp, a2));
		assertTrue(f.proximallyEquals(tp, a3));
	}

	@Test
	public void testNotEquals() {
		Topocentric3DPoint p1 = new Topocentric3DPoint(easting, northing, 5000);
		Topocentric3DPoint p2 = new Topocentric3DPoint(easting, northing, 6000);
		assertFalse(p1.equals(p2));

		p2.setEasting(p1.getEasting() + 1);
		p2.setElevation(p1.getElevation());
		assertFalse(p1.equals(p2));

		Topocentric3DPoint p3 = new Topocentric3DPoint(easting, northing+1, 5000);
		assertFalse(p1.equals(p3));

		Object other = new Object();
		assertFalse(p1.equals(other));

		Topocentric3DPoint pNull = null;
		assertFalse(p1.equals(pNull));
	}

}
