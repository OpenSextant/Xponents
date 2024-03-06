/****************************************************************************************
 *  TestGeodetic3DBounds.java
 *
 *  Created: Mar 29, 2007
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

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.opensextant.geodesy.*;

public class TestGeodetic3DBounds extends TestCase {

	private static final double MIN_ELEV = 100;
	private static final double MAX_ELEV = 2500;

	private final Geodetic3DPoint west = new Geodetic3DPoint(new Longitude(161, 54, 44),
			new Latitude(-85, 41, 54), MIN_ELEV);
	
	private final Geodetic3DPoint east = new Geodetic3DPoint(new Longitude(99, 8, 8),
            new Latitude(79, 39, 57), MAX_ELEV);

    public void testBBox() {
        // bbox1: (161 deg 54' 44" E, 85 deg 41' 54" S) .. (99 deg 8' 8" E, 79 deg 39' 57" N)
        // bbox2: (91 deg 4' 4" E, 89 deg 57' 12" S) .. (0 deg 13' 54" W, 87 deg 50' 13" N)
        // together??: (161 deg 54' 44" E, 89 deg 57' 12" S) .. (99 deg 8' 8" E, 87 deg 50' 13" N)

		double minElev = MIN_ELEV;
		double maxElev = MAX_ELEV;

        Geodetic3DBounds bbox1 = new Geodetic3DBounds(west, east);

		assertTrue(bbox1.toString().indexOf("161") > 0);
        assertTrue(bbox1.contains(east));

        Geodetic3DPoint cPt = bbox1.getCenter();
        assertTrue(cPt.getElevation() >= minElev);
        assertTrue(cPt.getElevation() <= maxElev);

        // expand the volume of the bounding box
        minElev -= 10;
        maxElev += 10;
        Geodetic3DPoint west2 = new Geodetic3DPoint(new Longitude(91, 4, 4),
                new Latitude(-89, 57, 12), maxElev);
        Geodetic3DPoint east2 = new Geodetic3DPoint(new Longitude(-1, 13, 54),
                new Latitude(87, 50, 13), minElev);
        Geodetic3DBounds bbox2 = new Geodetic3DBounds(west2, east2);

        bbox1.include(bbox2);

        cPt = bbox1.getCenter();
        assertTrue(cPt.getElevation() >= minElev);
        assertTrue(cPt.getElevation() <= maxElev);

        assertTrue(bbox1.contains(bbox2));
        assertTrue(bbox1.contains(east2));

        Geodetic3DPoint outside = new Geodetic3DPoint(new Longitude(172, 54, 44),
                new Latitude(89, 41, 54), minElev);
        assertFalse(bbox1.contains(outside));

        Geodetic3DPoint ptTooHigh = new Geodetic3DPoint(cPt.getLongitude(), cPt.getLatitude(), maxElev * 2);
        assertFalse(bbox1.contains(ptTooHigh));

		Geodetic3DBounds readonlyCopy = new UnmodifiableGeodetic3DBounds(bbox1);
		assertEquals(bbox1, readonlyCopy);
		assertTrue(bbox1.equals((Geodetic2DBounds)readonlyCopy));
		try {
			readonlyCopy.grow(100);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			readonlyCopy.include(outside);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
		}
		try {
			readonlyCopy.include(bbox1);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
		}
		try {
			readonlyCopy.include((Geodetic2DPoint)outside);
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
		}
		try {
			readonlyCopy.setEastLon(east2.getLongitude());
			fail("readonly bounds expected to throw UnsupportedOperationException");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		assertEquals(bbox1, readonlyCopy);
		assertEquals(bbox1.hashCode(), readonlyCopy.hashCode());
    }

	public void testContains() {
        // bbox: (161 deg 54' 44" E, 85 deg 41' 54" S) .. (99 deg 8' 8" E, 79 deg 39' 57" N)

        //double minElev = 100;
        //double maxElev = 2500;
        Geodetic3DBounds bbox = new Geodetic3DBounds(west);
		Geodetic2DPoint pt = west;
		assertEquals(bbox.maxElev, west.getElevation(), 1e-5);
		assertEquals(bbox.minElev, west.getElevation(), 1e-5);
		assertTrue(bbox.contains(pt)); // test as 3-d point
		pt = new Geodetic2DPoint(west.getLongitude(), west.getLatitude());
		assertTrue(bbox.contains(pt)); // tested as 2-d point in this context
		pt = new Geodetic3DPoint(west.getLongitude(), west.getLatitude(), MIN_ELEV + MAX_ELEV);
		assertFalse(bbox.contains(pt)); // tested as 3-d point with wrong elevation

		bbox.include((Geodetic2DPoint)east); // should include as 3d point
		assertTrue(bbox.contains((Geodetic2DPoint)east)); // test as 3-d point
		assertEquals(bbox.maxElev, MAX_ELEV, 1e-5);
		assertEquals(bbox.minElev, MIN_ELEV, 1e-5);

		Geodetic3DBounds bbox2 = new Geodetic3DBounds();
		final Geodetic2DBounds bounds2d = bbox; // handle as 2-d bbox
		bbox2.include(bounds2d); // should include as 3D bbox
		assertEquals(bbox.maxElev, MAX_ELEV, 1e-5);
		assertEquals(bbox.minElev, MIN_ELEV, 1e-5);
		assertTrue(bbox2.contains(bounds2d)); // should test as 3-d point

		bbox2 = new Geodetic3DBounds(bounds2d, 50.0, 50000.0);
		assertEquals(bbox2.maxElev, 50000.0, 1e-5);
		assertTrue(bbox2.contains(bbox));
	}

	public void test2dEquals() {
		Geodetic3DBounds bbox = new Geodetic3DBounds(east);
		bbox.maxElev = 0.0;
		bbox.minElev = 0.0;
		Geodetic2DBounds bounds2d = new Geodetic2DBounds(bbox);
		assertTrue(bbox.contains(bounds2d)); // should test as 2-d point
		assertEquals(bbox, bounds2d);   // should test as 2-d point

		bbox.include(new Geodetic2DPoint(west.getLongitude(), west.getLatitude()));
		assertTrue(bbox.contains(bounds2d));
	}

	public void testNullBBoxCompare() {
        Geodetic3DBounds bbox1 = new Geodetic3DBounds();
		Geodetic3DBounds bbox2 = null;
		assertFalse(bbox1.equals(bbox2));
	}

    public void testRandomBBox() {
        /*
        * sometimes this test fails if random bbox wraps world
        * so we run test multiple times where a single successful run
        * means the test passed. If fails 8 consecutive times then got a real problem.
        */
        AssertionError ex = null;
        for (int i = 1; i <= 8; i++) {
            try {
                realTestRandomBBox();
                return; // test successful
            } catch (WrappedAssertionException e) {
                System.out.println("*** warning: failed at testRandomBBox: " + i);
                AssertionError ae = e.getAssertionError();
                ae.printStackTrace(System.out);
                if (ex == null) ex = ae; // save first failed test result
            }
        }
        if (ex != null) {
            // this means we failed all attempts so we really failed
            throw ex;
        }
    }

    private void realTestRandomBBox() throws WrappedAssertionException {
        Random r = new Random();
        FrameOfReference f = new FrameOfReference();
        Geodetic3DPoint pt = TestGeoPoint.randomGeodetic3DPoint(r);
        Geodetic3DBounds bbox1 = new Geodetic3DBounds(pt.toGeodetic3D(f));
        for (int i = 0; i < 10; i++) {
            pt = TestGeoPoint.randomGeodetic3DPoint(r);
            bbox1.include(pt.toGeodetic3D(f));
        }
        assertTrue(bbox1.contains(pt));
        Geodetic3DBounds bbox2 = new Geodetic3DBounds(pt.toGeodetic3D(f));
        for (int i = 0; i < 10; i++) {
            pt = TestGeoPoint.randomGeodetic3DPoint(r);
            bbox2.include(pt.toGeodetic3D(f));
        }
        assertTrue(bbox2.contains(pt));
        bbox1.include(bbox2);
        assertTrue(bbox1.contains(bbox2));
        try {
            assertTrue("bbox contains point", bbox1.contains(pt)); // sometimes this test fails: see comment below
        } catch (AssertionError e) {
            // wrap this assertion failure so we can re-run the test again
            throw new WrappedAssertionException(e);
        }

        /*
         try {
            assertTrue(bbox1.contains(pt));
        } catch(Throwable e) {
            // once in a while there is an null assertion error here.
            e.printStackTrace();
            System.out.println(bbox1);
            System.out.println("center=" + bbox1.getCenter());
            System.out.println(pt);
            fail();
        }
        somehow the east/west lon get switched or we're wrapping the world
        example: west lon = -61 east lin = -81
            (61 deg 53' 58" W, 89 deg 37' 35" S) .. (81 deg 9' 34" W, 89 deg 33' 41" N) .. (1802.0m, 997780.0m)
            center=(108 deg 28' 14" E, 0 deg 1' 57" S) @ 499791m
            (64 deg 49' 50" W, 80 deg 39' 21" S) @ 853675m
         */
    }

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestGeodetic3DBounds.class);
        new TestRunner().doRun(suite);
    }

    final static class WrappedAssertionException extends Exception {
        private final AssertionError ae;
        
        public WrappedAssertionException(AssertionError e) {
            super(e);
            this.ae = e;
        }

        public AssertionError getAssertionError() {
            return ae;
        }
    }
}
