/****************************************************************************************
 *  TestShapefileBase.java
 *
 *  Created: Dec 10, 2009
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
package org.opensextant.giscore.test.output;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.events.AltitudeModeEnumType;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.MultiLine;
import org.opensextant.giscore.geometry.MultiPolygons;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.geometry.Polygon;
import org.opensextant.giscore.input.shapefile.SingleShapefileInputHandler;
import org.opensextant.giscore.output.shapefile.SingleShapefileOutputHandler;
import org.opensextant.giscore.test.TestGISBase;
import org.opensextant.giscore.utils.ObjectBuffer;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opensextant.giscore.test.TestSupport.OUTPUT;

/**
 *
 * @author DRAND
 *
 */
public abstract class TestShapefileBase extends TestGISBase {
	protected static final File shapeOutputDir = new File(OUTPUT + "/shptest");
	static {
		shapeOutputDir.mkdirs();
	}
	UniformRandomProvider RandomUtils = RandomSource.XO_RO_SHI_RO_128_PP.create();

	public TestShapefileBase() {
		super();
	}

	protected void writeShapefile(Schema schema, ObjectBuffer buffer,
			List<? extends Geometry> geometries, String file)
			throws IOException, XMLStreamException,
			ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		// now read shape file back in and test against what we wrote
		System.out.println("Test " + file);

		SingleShapefileOutputHandler soh = new SingleShapefileOutputHandler(
				schema, null, buffer, shapeOutputDir, file, null);
		try {
			soh.process();
		} finally {
			buffer.close();
		}
		// System.out.println("Verify " + file);
		SingleShapefileInputHandler handler = new SingleShapefileInputHandler(
				shapeOutputDir, file);
		try {
			IGISObject ob = handler.read();
			assertTrue(ob instanceof Schema);
			int count = 0;
			while ((ob = handler.read()) != null) {
				assertTrue(ob instanceof Feature);
				if (geometries == null) {
					count++;
					continue;
				}
				Feature f = (Feature) ob;
				Geometry geom = f.getGeometry();
				Geometry expectedGeom = geometries.get(count++);
				// flatten geometry
				if (geom instanceof MultiLine) {
					MultiLine ml = (MultiLine) geom;
					if (ml.getNumParts() == 1)
						geom = ml.getPart(0);
				} else if (geom instanceof MultiPolygons) {
					MultiPolygons mp = (MultiPolygons) geom;
					if (mp.getNumParts() == 1) {
						Polygon poly = (Polygon) mp.getPart(0);
						// reader turns rings into MultiPolygons with single
						// Polygon having single outer ring
						if (expectedGeom instanceof LinearRing) {
							LinearRing ring = poly.getOuterRing();
							/*
							 * for (Point pt : ring) {
							 * System.out.format("%f %f%n",
               * pt.getCenter().getLongitudeAsDegrees(),
               * pt.getCenter().getLatitudeAsDegrees()); }
							 */
							if (!ring.clockwise())
								System.out
										.println("imported rings must be in clockwise point order");
							geom = ring;
						} else
							geom = poly;
					}
				}
				assertEquals(expectedGeom, geom);
			}
			if (geometries != null) {
				assertEquals(geometries.size(), count);
			}
			System.out.println("  count=" + count);
		} finally {
			handler.close();
		}
	}

	protected Point getRingPointZ(Point cp, int n, int total, double size,
			double min) {
    double lat = cp.getCenter().getLatitudeAsDegrees();
    double lon = cp.getCenter().getLongitudeAsDegrees();
		double theta = Math.toRadians(360.0 * n / total);
		double magnitude = min + RandomUtils.nextDouble() * size;
		double dy = magnitude * Math.sin(theta);
		double dx = magnitude * Math.cos(theta);
		Point pt = new Point(lat + dy, lon + dx, true);
		pt.setAltitudeMode(AltitudeModeEnumType.absolute);
		return pt;
	}

	protected Point getRandomPointZ() {
		double lat = 40.0 + (5.0 * RandomUtils.nextDouble());
		double lon = 40.0 + (5.0 * RandomUtils.nextDouble());
		double elt = RandomUtils.nextInt(200);
		return new Point(new Geodetic3DPoint(new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES), elt));
	}

}