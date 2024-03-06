/****************************************************************************************
 *  TestCreateTestShapefilesForQueries.java
 *
 *  Created: Sep 23, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
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
package org.opensextant.giscore.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.MultiLinearRings;
import org.opensextant.giscore.geometry.MultiPolygons;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.geometry.Polygon;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.output.FeatureKey;
import org.opensextant.giscore.output.IContainerNameStrategy;
import org.opensextant.giscore.output.IGISOutputStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static org.opensextant.giscore.test.TestSupport.OUTPUT;
/**
 * Not so much a test as something that will create test shapes to make sure
 * that programs that uses shapefiles to create accept areas are working.
 *
 * @author DRAND
 */
public class TestCreateTestShapefilesForQueries {

	static class CNS implements IContainerNameStrategy {
		public String deriveContainerName(List<String> path, FeatureKey feature) {
			return feature.getGeoclass().getSimpleName();
		}
	}

	@Test
	public void createShapefiles() throws IOException {
		int count = 5;
		final long timestamp = System.currentTimeMillis();
		File temp = new File(OUTPUT + "/tst/tmp" + timestamp);
		temp.mkdirs();
		File zip = new File(OUTPUT + "/tst/shapes" + timestamp
				+ ".zip");
		OutputStream os = new FileOutputStream(zip);
		ZipOutputStream zos = new ZipOutputStream(os);
		IGISOutputStream str = GISFactory.getOutputStream(
				DocumentType.Shapefile, zos, temp, new CNS());
		// Make one of each of the possible shapes
		outputgeo(str, new Line(getPointsAround(33.0, 44.0, .5, count, false)));
		outputgeo(str,
				new LinearRing(getPointsAround(33.0, 44.0, 1.0, count, true)));
		outputgeo(str, new Polygon(new LinearRing(getPointsAround(33.0, 44.0,
				2.0, count, true))));

		List<LinearRing> rings = new ArrayList<>();
		rings.add(new LinearRing(getPointsAround(32., 45., 1.0, count, true)));
		MultiLinearRings mlr = new MultiLinearRings(rings);
		outputgeo(str, mlr);

		List<Polygon> polys = new ArrayList<>();
		polys.add(new Polygon(new LinearRing(getPointsAround(34.0, 41.0, 1.0,
				count, true))));
		MultiPolygons mlp = new MultiPolygons(polys);
		outputgeo(str, mlp);
		str.close(); // Write it all out

		IOUtils.closeQuietly(zos);
		IOUtils.closeQuietly(os);

		// Read and verify the resulting shapefiles
		IGISInputStream istr = GISFactory.getInputStream(
				DocumentType.Shapefile, temp);
		int schema_count = 0;
		int feature_count = 0;
		IGISObject obj = istr.read();
		while (obj != null) {
			if (obj instanceof Schema) {
				schema_count++;
			} else if (obj instanceof Feature) {
				feature_count++;
				Geometry geo = ((Feature) obj).getGeometry();
				assertNotNull(geo);
				// Lines have count points, all others have count+1 points
				if (geo instanceof Line)
					assertEquals(count, geo.getPoints().size());
				else
					assertEquals(count + 1, geo.getPoints().size());
				for(Point p : geo.getPoints()) {
          assertEquals(42.0, p.getCenter().getLatitudeAsDegrees(), 4.0); // Near
          assertEquals(32.0, p.getCenter().getLongitudeAsDegrees(), 4.0); // Near
				}
			}
			obj = istr.read();
		}
		assertEquals(5, schema_count);
		assertEquals(5, feature_count);
	}

	private void outputgeo(IGISOutputStream str, Geometry geo) {
		Feature f = new Feature();
		f.setGeometry(geo);
		str.write(f);
	}

	/**
	 * Get a ring
	 *
	 * @param lon
	 * @param lat
	 * @param radius
	 * @param count
	 * @return
	 */
	List<Point> getPointsAround(double lon, double lat, double radius,
			int count, boolean closed) {
		double deltaangle = 2.0 * Math.PI / count;
		double angle = 0.0;
		List<Point> rval = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double x = Math.cos(angle) * radius + lon;
			double y = Math.sin(angle) * radius + lat;
			Point pt = new Point(y, x);
			rval.add(pt);
			angle += deltaangle;
		}
		if (closed)
			rval.add(rval.get(0));
		return rval;
	}
}