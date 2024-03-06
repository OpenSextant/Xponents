/****************************************************************************************
 *  TestGeodetic2DEllipse.java
 *
 *  Created: Jun 24, 2010 9:14:59 AM
 *
 *  @author Jason Mathews
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

import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DArc;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DEllipse;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import static org.junit.Assert.*;

public class TestGeodetic2DEllipse {

	private static final double EPSILON = 1E-5;

	@Test
	public void testCreation() {
		Geodetic2DEllipse ellipse = new Geodetic2DEllipse();
		assertNotNull(ellipse.getCenter());
		assertNotNull(ellipse.toString());

		Geodetic2DPoint boston =
                new Geodetic2DPoint("42\u00B0 22' 11.77\" N, 71\u00B0 1' 40.30\" W");

		Geodetic2DEllipse geo = new Geodetic2DEllipse(boston, 4000, 2500, new Angle(45, Angle.DEGREES));
		assertEquals(boston,  geo.getCenter());
		assertEquals(4000.0, geo.getSemiMajorAxis(), EPSILON);
		assertEquals(2500.0, geo.getSemiMinorAxis(), EPSILON);
		assertEquals(45.0, geo.getOrientation().inDegrees(), EPSILON);

		Geodetic2DEllipse geo2 = new Geodetic2DEllipse(boston, 4000.0, 2500.0, geo.getOrientation());
		assertEquals(geo, geo2);
		assertEquals(geo.hashCode(), geo2.hashCode());
		assertFalse(ellipse.equals(geo));
	}

	@Test
	public void testCreateAndSet() {
		Geodetic2DEllipse ellipse = new Geodetic2DEllipse();
		Geodetic2DPoint center = new Geodetic2DPoint(new Random());
		ellipse.setCenter(center);
		ellipse.setSemiAxes(1000, 500);
		ellipse.setOrientation(new Angle(45, Angle.DEGREES));
	}

	@Test
	public void testEquals() {
		Geodetic2DEllipse ellipse1 = new Geodetic2DEllipse();
		// 180 difference in orientation is normalized as same
		Geodetic2DEllipse ellipse2 = new Geodetic2DEllipse(
				ellipse1.getCenter(), 0, 0, new Angle(180, Angle.DEGREES));
		assertEquals(ellipse1, ellipse2);

		// same except for the orientation
		Geodetic2DEllipse ellipse3 = new Geodetic2DEllipse(
				ellipse1.getCenter(), 0, 0, new Angle(45, Angle.DEGREES));
		assertFalse(ellipse1.equals(ellipse3));			// compare using equals(Geodetic2DEllipse)
		assertFalse(ellipse1.equals((Object)ellipse3));	// compare using equals(Object)
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetInvalidAxes() {
		Geodetic2DEllipse ellipse = new Geodetic2DEllipse();
		// set minor > major
		ellipse.setSemiAxes(500, 1000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetInvalidMinor() {
		Geodetic2DEllipse ellipse = new Geodetic2DEllipse();
		ellipse.setSemiMajorAxis(10);
		// set minor > major
		ellipse.setSemiMinorAxis(100);
	}

	@Test
	public void testNullEllipseCompare() {
        Geodetic2DEllipse ellipse = new Geodetic2DEllipse();
		Geodetic2DEllipse other = null;
		assertFalse(ellipse.equals(other));
	}
	
	@Test
	public void testNonEllipseEquals() {
		Geodetic2DEllipse ellipse = new Geodetic2DEllipse();
		Object other = new Object();
		assertFalse(ellipse.equals(other));
	}

	@Test
	public void testBounds() {
		Geodetic2DPoint center = new Geodetic2DPoint(
				"0\u00B0 0' 0\" N, 0\u00B0 0' 0\" W");

		Geodetic2DEllipse ellipse = new Geodetic2DEllipse(
				center, 4000, 1000, new Angle(0, Angle.DEGREES));
		final Iterable<Geodetic2DPoint> boundary = ellipse.boundary(4);
		assertCount(boundary, 4); // check once to initialize bounds
		// check again to verify the same bounds is returned
		assertSame(boundary, ellipse.boundary(4));

		Geodetic2DBounds bounds = new Geodetic2DBounds(ellipse);
		assertEquals(2000.0, calculateEWDistance(bounds), 60.0);
		assertEquals(8000.0, calculateNSDistance(bounds), 60.0);
	}

	private void assertCount(Iterable<Geodetic2DPoint> boundary, int expected) {
		int count = 0;
		for(Geodetic2DPoint pt : boundary) {
			count++;
		}
		assertEquals("boundary count does not match", expected, count);
	}

	private double calculateNSDistance(Geodetic2DBounds bounds) {
		Longitude centerlon = new Longitude(
				bounds.getEastLon().inDegrees() + bounds.getWestLon().inDegrees() / 2.0,
				Angle.DEGREES);
		return calculateDistance(centerlon, bounds.getNorthLat(), centerlon, bounds.getSouthLat());
	}
	
	private double calculateEWDistance(Geodetic2DBounds bounds) {
		Latitude centerlat = new Latitude(
				bounds.getNorthLat().inDegrees() + bounds.getSouthLat().inDegrees() / 2.0,
				Angle.DEGREES);
		return calculateDistance(bounds.getEastLon(), centerlat, bounds.getWestLon(), centerlat);
	}
	
	private double calculateDistance(Longitude lon2, Latitude lat2, Longitude lon1, Latitude lat1) {
		Geodetic2DPoint point1 = new Geodetic2DPoint(lon1, lat1);
		Geodetic2DPoint point2 = new Geodetic2DPoint(lon2, lat2);
		Geodetic2DArc arc = new Geodetic2DArc(point1, point2);
		return arc.getDistanceInMeters();
	}
	
}
