/****************************************************************************************
 *  TestGeodetic2DCircle.java
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

import junit.framework.TestCase;
import org.opensextant.geodesy.Geodetic2DCircle;
import org.opensextant.geodesy.Geodetic2DPoint;

public class TestGeodetic2DCircle extends TestCase {

	private static final double EPSILON = 1E-5;

	public void testCreation() {
		Geodetic2DCircle circle = new Geodetic2DCircle();
		Geodetic2DPoint cp = circle.getCenter();
		assertNotNull(cp);

		Geodetic2DPoint boston =
                new Geodetic2DPoint("42\u00B0 22' 11.77\" N, 71\u00B0 1' 40.30\" W");

		Geodetic2DCircle geo = new Geodetic2DCircle(boston, 100);
		assertEquals(boston, geo.getCenter());
		assertEquals(100.0, geo.getRadius(), EPSILON);
		assertFalse(circle.equals(geo));

		Geodetic2DCircle geo2 = new Geodetic2DCircle(boston, 100.0);
		assertEquals(geo, geo2);
		assertEquals(geo.hashCode(), geo2.hashCode());

		int count = 0;
		for (Geodetic2DPoint pt : geo.boundary(8)) {
			assertNotNull(pt);
			count++;
		}
		assertEquals(8, count);
	}

	public void testNullCircleCompare() {
		Geodetic2DCircle circle = new Geodetic2DCircle();
		Geodetic2DCircle other = null;
		assertFalse(circle.equals(other));
	}
	
}
