package org.opensextant.geodesy.test;

import junit.framework.TestCase;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DArc;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;

/**
 * Simple tests for Geodetic2DArc
 * @author Jason Mathews, MITRE Corp.
 */
public class TestGeodetic2DArc extends TestCase {

	public void testCreate() {
		Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
				new Latitude(30, Angle.DEGREES));
		Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(31, Angle.DEGREES),
				new Latitude(31, Angle.DEGREES));
		Geodetic2DArc arc = new Geodetic2DArc(west, east);
		assertNotNull(arc.getPoint1());
		assertNotNull(arc.getPoint2());
		assertNotNull(arc.toString());
		assertEquals(146647.2, arc.getDistanceInMeters(), 0.001);
		assertEquals(41, Math.round(arc.getForwardAzimuth().inDegrees()));

		Geodetic2DArc arc2 = new Geodetic2DArc(east, east);
		assertEquals(0.0, arc2.getDistanceInMeters(), 1e-6);
	}

	public void testEquals() {
		Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
				new Latitude(30, Angle.DEGREES));
		Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(32, Angle.DEGREES),
				new Latitude(32, Angle.DEGREES));
		Geodetic2DArc arc = new Geodetic2DArc(west, east);

		Geodetic2DArc arc2 = new Geodetic2DArc(east, west);
		assertEquals(arc.getDistanceInMeters(), arc2.getDistanceInMeters(), 10-6);

		Geodetic2DArc arc3 = new Geodetic2DArc(west, arc.getDistanceInMeters(),
				arc.getForwardAzimuth());
		assertEquals(arc, arc3);
		assertEquals(0, arc.compareTo(arc3));

		// change point to make the object fail equality
		arc3.setPoint2(new Geodetic2DPoint(new Longitude(33, Angle.DEGREES),
				new Latitude(33, Angle.DEGREES)));
		assertFalse(arc.equals(arc3));
	}

	public void testNotEquals() {
		Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
				new Latitude(30, Angle.DEGREES));
		Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(32, Angle.DEGREES),
				new Latitude(32, Angle.DEGREES));
		Geodetic2DArc arc = new Geodetic2DArc(west, east);
		assertFalse(arc.equals(null));
		Object other = this;
		assertFalse(arc.equals(other));
	}

}
