/****************************************************************************************
 *  TestPropertyEditors.java
 *
 *  Created: Jul 9, 2009
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
package org.opensextant.giscore.test.editors;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.editors.CirclePropertyEditor;
import org.opensextant.giscore.editors.LinearRingPropertyEditor;
import org.opensextant.giscore.editors.PointPropertyEditor;
import org.opensextant.giscore.geometry.Circle;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;
import static org.junit.Assert.*;

import static org.opensextant.giscore.test.TestSupport.nextDouble;

public class TestPropertyEditors {
	private static final double EPSILON = 1e-5;

	public Geodetic2DPoint getRandomPoint() {
		double lat = 180.0 * nextDouble() - 90.0;
		double lon = 360.0 * nextDouble() - 180.0;
		Geodetic2DPoint rval = new Geodetic2DPoint();
		rval.setLatitude(new Latitude(lat, Angle.DEGREES));
		rval.setLongitude(new Longitude(lon, Angle.DEGREES));
		return rval;
	}

	public void checkApproximatelyEqual(Point a, Point b) {
    assertEquals(a.getCenter().getLatitudeAsDegrees(), b.getCenter().getLatitudeAsDegrees(), EPSILON);
    assertEquals(a.getCenter().getLongitudeAsDegrees(), b.getCenter().getLongitudeAsDegrees(), EPSILON);
	}

	public void checkApproximatelyEqual(Geodetic2DPoint a, Geodetic2DPoint b) {
    assertEquals(a.getLatitudeAsDegrees(), b.getLatitudeAsDegrees(), EPSILON);
    assertEquals(a.getLongitudeAsDegrees(), b.getLongitudeAsDegrees(), EPSILON);
	}

	@Test public void testPointEditor() throws Exception {
		Point orig = new Point(getRandomPoint());
		PointPropertyEditor pp = new PointPropertyEditor();
		pp.setValue(orig);
		String str = pp.getAsText();
		assertNotNull(str);
		assertTrue(str.contains("/"));
		pp.setAsText(str);
		Point read = (Point) pp.getValue();
		checkApproximatelyEqual(orig, read);
	}

	@Test public void testCircleEditor() throws Exception {
		Geodetic2DPoint orig = getRandomPoint();
		CirclePropertyEditor cpe = new CirclePropertyEditor();
		Circle c = new Circle(orig, RandomUtils.nextDouble() * 1500.0);
		cpe.setValue(c);
		String str = cpe.getAsText();
		System.out.println("Circle text: " + str);
		cpe.setAsText(str);
		Circle b = (Circle) cpe.getValue();
		checkApproximatelyEqual(c.getCenter(), b.getCenter());
		assertEquals(c.getRadius(), b.getRadius(), EPSILON);
	}

	@Test public void testLinearRingEditor() throws Exception {
		List<Point> pts = new ArrayList<>(6);
		for(int i = 0; i < 5; i++) {
			pts.add(new Point(getRandomPoint()));
		}
		// LinearRing must start and end with same point
		pts.add(pts.get(0));
		LinearRing lr = new LinearRing(pts);
		LinearRingPropertyEditor lrpe = new LinearRingPropertyEditor();
		lrpe.setValue(lr);
		String str = lrpe.getAsText();
		System.out.println("Ring text: " + str);
		lrpe.setAsText(str);
		LinearRing b = (LinearRing) lrpe.getValue();
		assertEquals(6, b.getPoints().size());
		for(int i = 0; i < 5; i++) {
			checkApproximatelyEqual(lr.getPoints().get(i), b.getPoints().get(i));
		}
	}
}