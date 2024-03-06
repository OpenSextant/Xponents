package org.opensextant.geodesy.test;

import junit.framework.TestCase;
import org.opensextant.geodesy.Ellipsoid;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.PolarStereographic;

public class TestPolarStereographic extends TestCase {

	private static final Latitude NORTH_POLAR = new Latitude(+81, 6, 52.3);
	private static final PolarStereographic ps = new PolarStereographic(NORTH_POLAR);

	// TODO: need some more useful tests

	public void testCreate() {
		assertNotNull(ps);
		Ellipsoid el = ps.getEllipsoid();
		assertNotNull(el);
		assertTrue(el.getPolarRadius() > 0);
	}

	public void testNullCompare() {
		PolarStereographic other = null;
		assertFalse(ps.equals(other));
	}
}
