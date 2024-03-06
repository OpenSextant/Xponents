/****************************************************************************************
 *  TestGeodetic2DBounds.java
 *
 *  Created: Mar 29, 2007
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

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.opensextant.geodesy.*;

public class TestGeodetic2DBounds extends TestCase {
    private static final String DEGSYM = Character.toString('\u00B0');

	private final Random r = new Random(123L);

    private void evaluateBBox(Geodetic2DBounds bbox, double radius) {
        FrameOfReference f = new FrameOfReference();
        Ellipsoid e = f.getEllipsoid();

        double diag1a, diag1b, diag2, diff, err;
        Geodetic2DPoint sw, ne;

        sw = new Geodetic2DPoint(bbox.getWestLon(), bbox.getSouthLat());
        ne = new Geodetic2DPoint(bbox.getEastLon(), bbox.getNorthLat());
        diag1a = e.orthodromicDistance(sw, ne);
        System.out.println("Diagonal by distance method 1: " + diag1a);
        Geodetic2DArc arc = new Geodetic2DArc(sw, ne);
        diag1b = arc.getDistanceInMeters();
        System.out.println("Diagonal by distance method 2: " + diag1b);

        diag2 = 2.0 * Math.sqrt(2.0 * radius * radius);
        diff = Math.abs(diag2 - diag1a);
        err = Math.max((diff / diag2), (diff / diag1a));
        // Look for large errors
        if (Math.abs(err) >= 0.00) {
            System.out.println(bbox.toString());
            System.out.println("Error in Diagonal = " + (100.0 * err) + " Percent");
            System.out.println("Diagonal = " + diag1a + ", expecting " + diag2);
            System.out.println("--------------");
        }
    }

    /**
     * This method tests the point-radius bounding box constructor
     */
    public void testPointRadius() {
        Geodetic2DPoint pt;
        double radius;
        Geodetic2DBounds bbox;

        for (int i = 0; i < 1; i++) {
            try {
                pt = TestGeoPoint.randomGeodetic2DPoint(r);
                radius = 1.0 * r.nextInt(1000000);      // stress test at 1,000 km.

                bbox = new Geodetic2DBounds(pt, radius);
                evaluateBBox(bbox, radius);

            } catch (IllegalArgumentException ex) {
                ex.printStackTrace(System.out);
				// TODO: is this a failed test or not. what indicates a failed test?
            }
        }
        System.out.println();
    }

	public void testCircleBounds() {
		Geodetic2DPoint pt = TestGeoPoint.randomGeodetic2DPoint(r);
		double radius = 1 + 1.0 * r.nextInt(1000000);      // stress test at 1,000 km.
		// note if center point near the poles then # points generating circle make big difference
		Geodetic2DBounds bbox = new Geodetic2DBounds(pt, radius, 4);
		Geodetic2DBounds bbox2 = new Geodetic2DBounds(new Geodetic2DCircle(pt, radius)); // uses default nPoints = 4
		assertEquals(bbox, bbox2);
	}

    public void testGeodetic2DArc() {
        Geodetic2DPoint pt = new Geodetic2DPoint(new Longitude("12,34,56E"), new Latitude("45,34,23N"));
        double distance = 123456.0;
        Angle azimuth = new Angle(33.0, Angle.DEGREES);
        Geodetic2DArc arc = new Geodetic2DArc(pt, distance, azimuth);
        System.out.println(arc.getPoint1().toString(5)); // (12� 34' 56.00000" E, 45� 34' 23.00000" N)
        System.out.println(arc.getPoint2().toString(5)); // (13� 27' 29.36001" E, 46� 30' 4.47423" N)
		assertEquals(pt, arc.getPoint1());
		assertEquals(new Geodetic2DPoint(new Longitude("13,27,29.36001"),
				new Latitude("46,30,4.47423")), arc.getPoint2());
		assertEquals(123456.0, arc.getDistanceInMeters(), 1e-6);
    }

	public void testInvalidArcCreation() {
		Geodetic2DPoint pt = TestGeoPoint.randomGeodetic2DPoint(r);
		double distance = 123456.0;
		Angle azimuth = new Angle(0.0, Angle.DEGREES);
        Geodetic2DArc arc = new Geodetic2DArc(pt, distance, azimuth);

		try {
			// circumference of earth = 40,075.16km or 4e+7 meters => max distance ~2E+7 meters
			arc.setDistanceInMeters(2E+8);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Distance is out of legal range
		}

		try {
			new Geodetic2DArc(pt, 2E+8, azimuth);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Distance is out of legal range
		}
	}

    public void testBBox() {
        Geodetic2DPoint west = new Geodetic2DPoint("(161" + DEGSYM + " 54' 44\" E, 85" + DEGSYM + " 41' 54\" S)");
        Geodetic2DPoint east = new Geodetic2DPoint("(99" + DEGSYM + " 8' 8\" E, 79" + DEGSYM + " 39' 57\" N)");
        Geodetic2DBounds bbox1 = new Geodetic2DBounds(west, east);

        Geodetic2DPoint c = bbox1.getCenter();
        assertTrue(east.getLatitude().inDegrees() >= c.getLatitude().inDegrees());
        assertTrue(west.getLongitude().inRadians() >= c.getLongitude().inRadians());

        Geodetic2DPoint west2 = new Geodetic2DPoint("(91" + DEGSYM + " 4' 4\" E, 89" + DEGSYM + " 57' 12\" S)");
        Geodetic2DPoint east2 = new Geodetic2DPoint("(0" + DEGSYM + " 13' 54\" W, 87" + DEGSYM + " 50' 13\" N)");
        Geodetic2DBounds bbox2 = new Geodetic2DBounds(west2, east2);

        bbox1.include(bbox2);
        System.out.println();
        System.out.println(bbox1);
        
        Geodetic2DPoint outside = new Geodetic2DPoint(new Longitude(172, 54, 44),
                new Latitude(89, 41, 54));
        assertFalse(bbox1.contains(outside));		

		Geodetic2DBounds readonlyCopy = new UnmodifiableGeodetic2DBounds(bbox1);
		assertEquals(bbox1, readonlyCopy);
		try {
			readonlyCopy.include(outside);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.include(bbox1);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.setEastLon(east.getLongitude());
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.setWestLon(west.getLongitude());
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.setNorthLat(east.getLatitude());
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.setSouthLat(east.getLatitude());
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.grow(10);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		assertEquals(bbox1, readonlyCopy);
    }

	public void testGeodetic2DArcNullCompare() {
		Geodetic2DArc arc = new Geodetic2DArc();
		Geodetic2DArc other = null;
		assertFalse(arc.equals(other));
	}

	public void testNullBBoxCompare() {
        Geodetic2DBounds bbox1 = new Geodetic2DBounds();
		Geodetic2DBounds bbox2 = null;
		assertFalse(bbox1.equals(bbox2));
	}

    public void testRandomBBox() {
        FrameOfReference f = new FrameOfReference();
        GeoPoint pt = TestGeoPoint.randomGeodetic2DPoint(r);
        Geodetic2DBounds bbox1 = new Geodetic2DBounds(pt.toGeodetic3D(f));
        for (int i = 0; i < 10; i++) {
            pt = TestGeoPoint.randomGeodetic2DPoint(r);
            System.out.print(pt + " -> ");
            bbox1.include(pt.toGeodetic3D(f));
            System.out.println(bbox1);
			// TODO: assertion ?
        }
        System.out.println();
        Geodetic2DBounds bbox2 = new Geodetic2DBounds(pt.toGeodetic3D(f));
        for (int i = 0; i < 10; i++) {
            pt = TestGeoPoint.randomGeodetic2DPoint(r);
            System.out.print(pt + " -> ");
            bbox2.include(pt.toGeodetic3D(f));
            System.out.println(bbox2);
			// TODO: assertion ?
        }
        System.out.println();
        bbox1.include(bbox2);
        System.out.println(bbox1);
		// TODO: assertion ?
    }

	public void testIncludePoint() {
		Geodetic2DPoint c = new Geodetic2DPoint(r);
        Geodetic2DBounds bbox = new Geodetic2DBounds(c);
		// first grow north latitude by 1 degree
		double lat = c.getLatitudeAsDegrees();
		Geodetic2DPoint c2 = new Geodetic2DPoint(
				c.getLongitude(), new Latitude(lat < 89 ? lat + 1 : lat - 1, Angle.DEGREES));
		bbox.include(c2);
		double diag1 = bbox.getDiagonal();
		double lon = c.getLongitudeAsDegrees();
		// first grow east longitude by 1 degree
		Geodetic2DPoint c3 = new Geodetic2DPoint(
				new Longitude(lon < 179 ? lon + 1 : lon - 1, Angle.DEGREES), c.getLatitude());
		bbox.include(c3);
		assertTrue(bbox.contains(c2));
		assertTrue(bbox.contains(c3));
		// diagonal distance increases
		assertTrue(bbox.getDiagonal() > diag1);
	}

    /**
     * Test bounds that wraps Longitude at the International Date Line (IDL)
     */
    public void testIDL() {
        Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(-Math.PI),
                new Latitude(0)); // -180 0
        Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
                new Latitude(60, Angle.DEGREES));
        Geodetic2DBounds bbox = new Geodetic2DBounds(west, east);
        Geodetic2DPoint c = bbox.getCenter();
        System.out.println(c + " -> " + bbox);
        assertTrue(east.getLatitude().inDegrees() >= c.getLatitude().inDegrees());
        assertTrue(bbox.contains(west));
    }

    /**
     * Test that growing a bounding box around a random point increases it's
     * diameter by at least two times the specified amount of growth.
     */
    public void testGrow2() {
        Geodetic2DPoint c = new Geodetic2DPoint(r);
        Geodetic2DBounds bbox = new Geodetic2DBounds(c);

        double r1, r2;

        r1 = bbox.getDiagonal() / 2.0;
        System.out.println(bbox.getCenter() + " " + r1 + " meters radius");
        double g = 1000.0;
        for (int i = 1; i < 10; i++) {
            bbox.grow(g);
            r2 = bbox.getDiagonal() / 2.0;
            System.out.println(bbox.getCenter() + " " + r2 + " meters radius");
            assertTrue(r2 >= (r1 + g));
            r1 = r2;
        }
    }

    /**
     * Test bounds that grow
     */
    public void testGrow() {
        Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
                new Latitude(30, Angle.DEGREES));
        Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(31, Angle.DEGREES),
                new Latitude(31, Angle.DEGREES));
        Geodetic2DBounds bbox = new Geodetic2DBounds(west, east);

		// test zero growth
		Geodetic2DBounds bboxCopy = new Geodetic2DBounds(bbox);
		bboxCopy.grow(0);
		assertEquals(bbox, bboxCopy);
		
        Geodetic2DPoint c = bbox.getCenter();
		System.out.println(c + " -> " + bbox);
        FrameOfReference fR = new FrameOfReference();
        double oDist = fR.orthodromicDistance(west, east);
        bbox.grow(1000);
        Geodetic2DPoint nWest = new Geodetic2DPoint(bbox.getWestLon(), bbox.getSouthLat());
		Geodetic2DPoint nEast = new Geodetic2DPoint(bbox.getEastLon(), bbox.getNorthLat());
		double nDist = fR.orthodromicDistance(nWest, nEast);
        double diff = nDist - oDist;
		assertTrue(oDist < nDist);
		double shouldBe = Math.sqrt((1000*1000*2)) * 2;
		assertTrue(diff <= shouldBe);
    }

	public void testApproxEquals() {
		Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
                new Latitude(30, Angle.DEGREES));
        Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(32, Angle.DEGREES),
                new Latitude(32, Angle.DEGREES));
        Geodetic2DBounds bbox = new Geodetic2DBounds(west, east);

		Geodetic2DPoint west2 = new Geodetic2DPoint(new Longitude(30.001, Angle.DEGREES),
                new Latitude(30.001, Angle.DEGREES));
        Geodetic2DPoint east2 = new Geodetic2DPoint(new Longitude(31.999, Angle.DEGREES),
                new Latitude(31.999, Angle.DEGREES));
        Geodetic2DBounds bbox2 = new Geodetic2DBounds(west2, east2);

		assertFalse(bbox.equals(bbox2));
		/*
		double delta = 1;
		for (int i=0;i < 8;i++) {
			System.out.printf("%b %f%n", bbox.equals(bbox2, delta), delta);
			delta /= 10.0;
		}
		*/
		assertTrue(bbox.equals(bbox2, 0.001));
		assertTrue(bbox2.equals(bbox, 0.001));
	}

	public void testIntersect() {
		// test 1: bounds overlap each other
		Geodetic2DPoint west = new Geodetic2DPoint(new Longitude(30, Angle.DEGREES),
                new Latitude(30, Angle.DEGREES));
        Geodetic2DPoint east = new Geodetic2DPoint(new Longitude(32, Angle.DEGREES),
                new Latitude(32, Angle.DEGREES));
        Geodetic2DBounds bbox = new Geodetic2DBounds(west, east);

		Geodetic2DPoint west2 = new Geodetic2DPoint(new Longitude(31, Angle.DEGREES),
                new Latitude(31, Angle.DEGREES));
        Geodetic2DPoint east2 = new Geodetic2DPoint(new Longitude(33, Angle.DEGREES),
                new Latitude(33, Angle.DEGREES));
        Geodetic2DBounds bbox2 = new Geodetic2DBounds(west2, east2);

		assertTrue(bbox.intersects(bbox2));
		assertTrue(bbox2.intersects(bbox));

		// test 2: bounds non-overlapping
		Geodetic2DPoint west3 = new Geodetic2DPoint(new Longitude(40, Angle.DEGREES),
                new Latitude(40, Angle.DEGREES));
        Geodetic2DPoint east3 = new Geodetic2DPoint(new Longitude(50, Angle.DEGREES),
                new Latitude(50, Angle.DEGREES));
        Geodetic2DBounds bbox3 = new Geodetic2DBounds(west3, east3);
		assertFalse(bbox.intersects(bbox3));
		assertFalse(bbox3.intersects(bbox));
		// test 3: bbox contains box4

		Geodetic2DPoint east4 = new Geodetic2DPoint(new Longitude(31.5, Angle.DEGREES),
                	new Latitude(31.5, Angle.DEGREES));
		Geodetic2DBounds bbox4 = new Geodetic2DBounds(west2, east4);

		assertTrue(bbox.intersects(bbox4));
		assertTrue(bbox4.intersects(bbox));

		// test 4: bbox touches (non-overlapp) box5 along an edge

		Geodetic2DPoint east5 = new Geodetic2DPoint(new Longitude(34, Angle.DEGREES),
                	new Latitude(30, Angle.DEGREES));
		Geodetic2DPoint west5 = east; // lon=32, lat=32
		Geodetic2DBounds bbox5 = new Geodetic2DBounds(west5, east5);

		assertFalse(bbox.intersects(bbox5));
		// assertFalse(bbox5.intersects(bbox)); // this assertion fails

		// test 5: bbox touches at a single point

		Geodetic2DPoint east6 = east2; // lon=33, lat=33
		Geodetic2DPoint west6 = east;  // lon=32, lat=32
		Geodetic2DBounds bbox6 = new Geodetic2DBounds(west6, east6);

		assertFalse(bbox.intersects(bbox6));
		assertFalse(bbox6.intersects(bbox));
	}

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestGeodetic2DBounds.class);
        new TestRunner().doRun(suite);
    }
}
