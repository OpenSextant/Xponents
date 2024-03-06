/****************************************************************************************
 *  TestBoundingBoxes.java
 *
 *  Created: May 17, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
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
package org.opensextant.giscore.test.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.giscore.geometry.*;
import static org.junit.Assert.*;

/**
 * Test bounding box results for different geometries
 * 
 * @author DRAND
 */
public class TestBoundingBoxes {
	private static final double EPSILON = 1E-5;

	@Test public void testPointBB() throws Exception {
		Point p = new Point(1.0, 1.0);
		Geodetic2DBounds bbox = p.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(1.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(1.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(1.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(1.0, bbox.getWestLon().inDegrees(), EPSILON);
        // bounding box should be unmodifiable
        try {
            bbox.setEastLon(bbox.getWestLon());
            fail("bounding should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
	}
	
	@Test public void testLineBB() throws Exception {
		List<Point> points = new ArrayList<>();
		points.add(new Point(0.0, 0.0));
		points.add(new Point(0.0, 1.0));
		points.add(new Point(1.0, 0.0));
		Line l = new Line(points); 
		Geodetic2DBounds bbox = l.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(1.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(1.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
	
	@Test public void testCircleBB() throws Exception {
		Point p = new Point(1.0, 1.0);
		Circle c = new Circle(p.getCenter(), 10000.0);
		Geodetic2DBounds bbox = c.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(1.0904366453343193, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.9095633046406, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(1.0898451206524653, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.9101548793475347, bbox.getWestLon().inDegrees(), EPSILON);
	}	
	
	@Test public void testMultiPointBB() throws Exception {
		List<Point> pts = new ArrayList<>();
		pts.add(new Point(0.0, 0.0));
		pts.add(new Point(0.0, 1.0));
		pts.add(new Point(1.0, 2.0));
		pts.add(new Point(2.0, 1.0));
		pts.add(new Point(1.0, 0.0));
		pts.add(new Point(0.0, 0.0));
		MultiPoint mp = new MultiPoint(pts);
		Geodetic2DBounds bbox = mp.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(2.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(2.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
	
	@Test public void testRingBB() throws Exception {
		List<Point> pts = new ArrayList<>();
		pts.add(new Point(0.0, 0.0));
		pts.add(new Point(0.0, 1.0));
		pts.add(new Point(1.0, 2.0));
		pts.add(new Point(2.0, 1.0));
		pts.add(new Point(1.0, 0.0));
		pts.add(new Point(0.0, 0.0));
		LinearRing geo = new LinearRing(pts, true);
		Geodetic2DBounds bbox = geo.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(2.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(2.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
		
	@Test public void testPolyBB() throws Exception {
		List<Point> pts = new ArrayList<>();
        // Outer LinearRing in Polygon must be in clockwise point order
		pts.add(new Point(0.0, 0.0));
        pts.add(new Point(1.0, 0.0));
        pts.add(new Point(2.0, 1.0));
        pts.add(new Point(1.0, 2.0));
        pts.add(new Point(0.0, 1.0));
        pts.add(new Point(0.0, 0.0));
		LinearRing or = new LinearRing(pts, true);
		pts = new ArrayList<>();
		pts.add(new Point(0.2, 0.2));
		pts.add(new Point(0.2, 0.8));
		pts.add(new Point(0.8, 0.8));
		pts.add(new Point(0.8, 0.2));
		pts.add(new Point(0.2, 0.2));
		LinearRing ir = new LinearRing(pts, true);
		Polygon geo = new Polygon(or, Collections.singletonList(ir), true);
		Geodetic2DBounds bbox = geo.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(2.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(2.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
	
	@Test public void testMultiLinearRingsBB() throws Exception {
		List<Point> pts = new ArrayList<>();
		pts.add(new Point(0.0, 0.0));
		pts.add(new Point(0.0, 1.0));
		pts.add(new Point(1.0, 2.0));
		pts.add(new Point(2.0, 1.0));
		pts.add(new Point(1.0, 0.0));
		pts.add(new Point(0.0, 0.0));
		LinearRing r1 = new LinearRing(pts);
		MultiLinearRings geo = new MultiLinearRings(Collections.singletonList(r1));
		Geodetic2DBounds bbox = geo.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(2.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(2.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
	
	@Test public void testMultiPolygonsBB() throws Exception {
		List<Point> pts = new ArrayList<>();
		pts.add(new Point(0.0, 0.0));
		pts.add(new Point(0.0, 1.0));
		pts.add(new Point(1.0, 2.0));
		pts.add(new Point(2.0, 1.0));
		pts.add(new Point(1.0, 0.0));
		pts.add(new Point(0.0, 0.0));
		LinearRing or = new LinearRing(pts);
		pts = new ArrayList<>();
		pts.add(new Point(0.2, 0.2));
		pts.add(new Point(0.2, 0.8));
		pts.add(new Point(0.8, 0.8));
		pts.add(new Point(0.8, 0.2));
		pts.add(new Point(0.2, 0.2));
		LinearRing ir = new LinearRing(pts);
		Polygon p = new Polygon(or, Collections.singletonList(ir));
		MultiPolygons geo = new MultiPolygons(Collections.singletonList(p));
		Geodetic2DBounds bbox = geo.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(2.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(2.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
	
	@Test public void testGeometryBagBB() throws Exception {
		List<Geometry> geometries = new ArrayList<>();
		geometries.add(new Point(2.0, 2.0));
		List<Point> points = new ArrayList<>();
		points.add(new Point(0.0, 0.0));
		points.add(new Point(0.0, 1.0));
		points.add(new Point(1.0, 0.0));
		geometries.add(new Line(points)); 
		GeometryBag geo = new GeometryBag(geometries);
		Geodetic2DBounds bbox = geo.getBoundingBox();
        assertNotNull(bbox);
		assertEquals(2.0, bbox.getNorthLat().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getSouthLat().inDegrees(), EPSILON);
		assertEquals(2.0, bbox.getEastLon().inDegrees(), EPSILON);
		assertEquals(0.0, bbox.getWestLon().inDegrees(), EPSILON);
	}
}

