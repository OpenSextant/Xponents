package org.opensextant.geodesy.test;

import junit.framework.TestCase;
import org.opensextant.geodesy.FrameOfReference;
import org.opensextant.geodesy.GeoPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Topocentric2DPoint;
import org.opensextant.geodesy.Topocentric3DPoint;

/**
 * User: MATHEWS
 * Date: 6/20/11 2:20 PM
 */
public class TestTopocentric2DPoint extends TestCase {

	public void testCreate() {
		double easting = 630084;
		double northing = 4833439;
		Topocentric2DPoint tp = new Topocentric2DPoint(easting, northing);
		Topocentric3DPoint point = tp.toTopocentric3D();
		assertNotNull(point); // (630084m East, 4833439m North) @ 0m
		assertEquals(tp.getEasting(), point.getEasting(), 1e-6);
		assertEquals(tp.getNorthing(), point.getNorthing(), 1e-6);
		FrameOfReference f = new FrameOfReference();
		Geodetic3DPoint pt = tp.toGeodetic3D(f); // (5� 38' 31" E, 37� 10' 6" N) @ 1657072m
		assertNotNull(pt);
	}

	public void testEquals() {
		double easting = 630084;
		double northing = 4833439;
		Topocentric2DPoint tp = new Topocentric2DPoint(easting, northing);
		Topocentric2DPoint tp2 = new Topocentric2DPoint(easting, northing);
		assertEquals(tp, tp2);
		assertEquals(tp.hashCode(), tp2.hashCode());

		tp2.setEasting(easting + 123);
		tp2.setNorthing(northing  - 123);
		assertFalse(tp.equals(tp2));

		Object other = new Object();
		assertFalse(tp.equals(other));
	}

    public void testNullCompare() throws Exception {
		double easting = 630084;
		double northing = 4833439;
		Topocentric2DPoint tp = new Topocentric2DPoint(easting, northing);
		Topocentric2DPoint tp2 = null;
		assertFalse(tp.equals(tp2));
	}

	public void testConversion() {
		double easting = 630084.12345;
		double northing = 4833439.6789;
		FrameOfReference f = new FrameOfReference();
		Topocentric2DPoint tp = new Topocentric2DPoint(easting, northing);
		GeoPoint a2 = f.toTopocentric(tp);
		GeoPoint a3 = f.toGeocentric(tp);
		assertTrue(f.proximallyEquals(tp, a2));
		assertTrue(f.proximallyEquals(tp, a3));
	}

	public void testToString() {
		double easting = 630084.12345;
		double northing = 4833439.6789;
		Topocentric2DPoint tp = new Topocentric2DPoint(easting, northing);

		// tp.toString(0) (630084m East, 4833439m North)
		// tp.toString(8) (630084.00000000m East, 4833439.00000000m North)

        String base = tp.toString();
		assertEquals(base, tp.toString(0)); // toString() same as toString(0)
		int prevLen = base.length();
        String prefix = base.substring(0, base.indexOf('m'));   // e.g. "100m
        // String suffix = base.substring(base.length() - 2);      // e.g. "m)"
		// System.out.println(prefix + " " + suffix);

		for(int i=1; i < 6; i++) {
            String s = tp.toString(i);
            int len = s.length();
            assertTrue(len >= prevLen + 2);
            // simple tests: each should start with same easting whole number
            assertTrue(s.startsWith(prefix));
            // assertTrue(s.endsWith(suffix));
            prevLen = len;
		}
	}
}
