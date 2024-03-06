package org.opensextant.giscore.test.output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensextant.giscore.events.AltitudeModeEnumType;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.GeometryBag;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Model;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.test.TestGISBase;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import static junit.framework.Assert.*;

/**
 * Test reading/writing core geometry classes.
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: Oct 5, 2009 3:12:57 PM
 */
public class TestGeometryOutput extends TestGISBase {

    // @Test
    public void testPointCreation() throws Exception {
        Point cp = getRandomPoint();
        List<Point> pts = new ArrayList<>();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SimpleObjectOutputStream os = new SimpleObjectOutputStream(bos);
        for (int i = 0; i < 5; i++) {
            Point pt = getRingPoint(cp, i, 5, .3, .4);
            pts.add(pt);
            pt.writeData(os);
        }
        Point pt2 = new Point();
        SimpleObjectInputStream is = new SimpleObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        for (Point pt : pts) {
            pt2.readData(is);
            assertEquals(pt, pt2);
        }
    }

    @Test
    public void testRingCreation() throws Exception {
        Point cp = getRandomPoint();
        List<Point> pts = new ArrayList<>();
        pts.add(getRingPoint(cp, 4, 5, .3, .4));
        pts.add(getRingPoint(cp, 3, 5, .3, .4));
        pts.add(getRingPoint(cp, 2, 5, .3, .4));
        pts.add(getRingPoint(cp, 1, 5, .3, .4));
        pts.add(getRingPoint(cp, 0, 5, .3, .4));
        pts.add(pts.get(0)); // ring should start and end with the same point
        LinearRing ring = new LinearRing(pts, true);
        ring.setDrawOrder(2);
        ring.setTessellate(true);
        ring.setExtrude(true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SimpleObjectOutputStream os = new SimpleObjectOutputStream(bos);
        ring.writeData(os);
        LinearRing ring2 = new LinearRing();
        SimpleObjectInputStream is = new SimpleObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        ring2.readData(is);
        assertTrue(2 == ring2.getDrawOrder());
        assertTrue(ring2.getTessellate());
        assertTrue(ring2.getExtrude());
        assertEquals(ring, ring2);
    }

    @Test
    public void testModelCreation() throws Exception {
        Model model = new Model();
        model.setLocation(random3dGeoPoint());
        model.setAltitudeMode(AltitudeModeEnumType.absolute);
        testModel(model);

        // test with AltitudeMode = null, location = 2d point
        AltitudeModeEnumType altMode = null;
        model.setLocation(getRandomPoint().asGeodetic2DPoint());
        model.setAltitudeMode(altMode);
        testModel(model);

        // test with location = null
        model.setLocation(null);
        model.setAltitudeMode(AltitudeModeEnumType.relativeToGround);
        testModel(model);
    }

    private void testModel(Model model) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SimpleObjectOutputStream os = new SimpleObjectOutputStream(bos);
        model.writeData(os);

        SimpleObjectInputStream is = new SimpleObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        Model geo2 = new Model();
        geo2.readData(is);
        assertEquals(model, geo2);
    }

    @Test
    public void testGeometryBagCreation() throws Exception {
        // create GeometryBag containing: MultiPoint, MultiLine, MultiLinearRings, MultiPolygons, and GeometryBag geometries 
        List<Feature> features = getMultiGeometries();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SimpleObjectOutputStream os = new SimpleObjectOutputStream(bos);
        GeometryBag bag = new GeometryBag();
        for (Feature f : features) {
            final Geometry g = f.getGeometry();
            if (g != null) bag.add(g);
        }
        bag.writeData(os);
        assertFalse(bag.isEmpty());
        assertNotNull(bag.getPoints());
        assertNotNull(bag.getPart(0));

        SimpleObjectInputStream is = new SimpleObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        GeometryBag geo2 = new GeometryBag();
        geo2.readData(is);
        assertEquals(bag, geo2);
    }
}
