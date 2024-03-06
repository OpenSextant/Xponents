/****************************************************************************************
 *  TestWKTInputStream.java
 *
 *  Created: Jan 13, 2012
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2012
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
package org.opensextant.giscore.test.input;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.junit.Test;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.geometry.GeometryBag;
import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.MultiLine;
import org.opensextant.giscore.geometry.MultiPoint;
import org.opensextant.giscore.geometry.MultiPolygons;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.geometry.Polygon;
import org.opensextant.giscore.input.IGISInputStream;
import static org.junit.Assert.*;

public class TestWKTInputStream {
	public final static String base_path = "data/wkt";
	
	// @Test
	public void testWKTInputBasic() throws IOException {
		IGISInputStream gis = GISFactory.getInputStream(DocumentType.WKT, new File(base_path, "basic.wkt"));
		
		IGISObject ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof Point);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof Line);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof Point);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof Line);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof Polygon);
		Polygon p = (Polygon) ob;
		assertTrue(p.getOuterRing() != null);
		assertTrue(p.getLinearRings().size() == 0);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof Polygon);
		p = (Polygon) ob;
		assertTrue(p.getOuterRing() != null);
		assertTrue(p.getLinearRings().size() == 2);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof MultiPoint);
		MultiPoint mp = (MultiPoint) ob;
		Point pt = mp.getPoints().get(0);
		Geodetic2DPoint gp = pt.getCenter();
		assertTrue(gp instanceof Geodetic3DPoint);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof MultiLine);
		MultiLine ml = (MultiLine) ob;
		int count = 0;
		Iterator<Line> li = ml.iterator();
		while(li.hasNext()) {
			Line l = li.next();
			assertEquals(2, l.getPoints().size());
			count++;
		}
		assertEquals(2, count);
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof MultiPolygons);
		MultiPolygons mpl = (MultiPolygons) ob;
		assertEquals(2, mpl.getPolygons().size());
		
		ob = gis.read();
		assertNotNull(ob);
		assertTrue(ob instanceof GeometryBag);
		GeometryBag gb = (GeometryBag) ob;
		assertEquals(4, gb.size());
		assertTrue(gb.getPart(0) instanceof Point);
		assertTrue(gb.getPart(1) instanceof Line);
		assertTrue(gb.getPart(2) instanceof Polygon);
		assertTrue(gb.getPart(3) instanceof Polygon);
	}

	@Test
	@SuppressWarnings("empty-statement")
	public void testEndOfStream() throws IOException {		
		InputStream is = new ByteArrayInputStream("POLYGON((112.1 33, 112.1 44, -10 44, -10 33, 112.1 33))".getBytes());
		IGISInputStream gis = GISFactory.getInputStream(DocumentType.WKT, is);
		for (IGISObject obj = gis.read(); obj != null; obj = gis.read());
		gis.close();
		is = new ByteArrayInputStream("   ".getBytes());
		gis = GISFactory.getInputStream(DocumentType.WKT, is);
		for (IGISObject obj = gis.read(); obj != null; obj = gis.read());
		gis.close();
	}
}
