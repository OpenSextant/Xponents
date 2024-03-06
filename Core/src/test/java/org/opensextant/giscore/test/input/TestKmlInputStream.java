/****************************************************************************************
 *  TestKmlInputStream.java
 *
 *  Created: Jan 27, 2009
 *
 *  @author DRAND
 *  @author Mathews
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
package org.opensextant.giscore.test.input;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.KmlInputStream;
import org.opensextant.giscore.test.TestGISBase;
import static org.junit.Assert.*;

/**
 * Test KMLInputStream and helper date parsing methods.
 *
 * @author DRAND
 * @author Mathews
 */
public class TestKmlInputStream extends TestGISBase {

	@Test
	public void testTinySample() throws IOException {
		InputStream stream = getStream("7084.kml");
		try {
			IGISInputStream kis = GISFactory.getInputStream(DocumentType.KML, stream);

			IGISObject firstN[] = new IGISObject[6];
			for(int i = 0; i < firstN.length; i++) {
				firstN[i] = kis.read();
			}
			assertNotNull(firstN[4]); // ContainerEnd
			assertNull(firstN[5]);
			assertTrue(firstN[0] instanceof DocumentStart);
			DocumentStart ds = (DocumentStart) firstN[0];
			assertEquals(DocumentType.KML, ds.getType());
			assertTrue(firstN[1] instanceof ContainerStart);
			ContainerStart cs = (ContainerStart) firstN[1];
			assertEquals(IKml.DOCUMENT, cs.getType());

			List<StyleSelector> styles = cs.getStyles();
			assertEquals(3, styles.size());
			assertTrue(styles.get(0) instanceof StyleMap);
			assertTrue(styles.get(1) instanceof Style);
			assertTrue(styles.get(2) instanceof Style);

			assertTrue(firstN[2] instanceof Feature);
			assertTrue(firstN[3] instanceof Feature);
			assertTrue(firstN[4] instanceof ContainerEnd);
		} finally {
		    IOUtils.closeQuietly(stream);
		}
	}

	/**
	 * Test calling close() multiple times does not throw an exception
	 * as well as calling read() after null is first returned.
     *
     * @throws IOException  if an I/O error occurs
	 */
	@Test
	public void testClose() throws IOException {
		InputStream stream = getStream("7084.kml");
		try {
			IGISInputStream kis = GISFactory.getInputStream(DocumentType.KML, stream);
			while (kis.read() != null) {
				// nothing
			}
			assertNull(kis.read());
			kis.close();
			assertNull(kis.read());
			kis.close();
		} finally {
		    IOUtils.closeQuietly(stream);
		}
	}

	/* @Test */public void testLargerSample() throws IOException {
		InputStream stream = getStream("KML_sample1.kml");
		try {
			IGISInputStream kis = GISFactory.getInputStream(DocumentType.KML, stream);

			IGISObject firstN[] = new IGISObject[100];
			for(int i = 0; i < firstN.length; i++) {
				firstN[i] = kis.read();
			}
			//System.out.println(firstN);
		} finally {
		    IOUtils.closeQuietly(stream);
		}
	}

	/* @Test */public void testSchemaSample() throws IOException {
		InputStream stream = getStream("schema_example.kml");
		try {
			IGISInputStream kis = GISFactory.getInputStream(DocumentType.KML, stream);

			IGISObject firstN[] = new IGISObject[10];
			Schema s = null;
			Feature f = null;
			for(int i = 0; i < firstN.length; i++) {
				IGISObject obj = kis.read();
				if (s == null && obj instanceof Schema) {
					s = (Schema) obj;
				} else if (f == null && s != null && obj instanceof Feature) {
					f = (Feature) obj;
				}
				firstN[i] = obj;
			}
			Collection<SimpleField> fields = f.getFields();
			assertNotNull(fields);
			for(SimpleField field : fields) {
				assertEquals(field, s.get(field.getName()));
			}
			//System.out.println(firstN);
		} finally {
		    IOUtils.closeQuietly(stream);
		}
	}

    /* @Test */public void testParseDate() {
        SimpleDateFormat df = new SimpleDateFormat(IKml.ISO_DATE_FMT);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        // allow lax date parsing
        // dateTime (YYYY-MM-DDThh:mm:ssZ) -> (YYYY-MM-DDThh:mm[:ss][Z])
        String[] timestamps = {
            "500",                  "0500-01-01T00:00:00.000Z", // gYear (YYYY)
            "2001",                 "2001-01-01T00:00:00.000Z", // gYear (YYYY)
            "3500",                 "3500-01-01T00:00:00.000Z", // gYear (YYYY)
            "2009-03-14T21:06:45",  "2009-03-14T21:06:45.000Z", // when  'Z' suffix omitted
            "2009-03-14T21:06Z",    "2009-03-14T21:06:00.000Z", // seconds field omitted
            "2009-03-14T21:06",     "2009-03-14T21:06:00.000Z", // seconds + 'Z' suffix omitted
        };
        // does not handle alternate time zones format with missing second field: e.g. 2009-03-14T16:10-05:00
        for (int i = 0; i < timestamps.length; i += 2) {
            Date date = null;
            try {
                date = KmlInputStream.parseDate(timestamps[i]);
            } catch (ParseException e) {
                Assert.fail("failed to parse " + timestamps[i]);
            }
            assertEquals(timestamps[i+1], df.format(date));
            System.out.format("DEBUG %s => %s%n", timestamps[i], timestamps[i+1]);
        }
    }

	/**
	 * Test KmlInputStream.parseCoord() for all variations of valid and invalid coordinate strings.
	 */
	/* @Test */public void testParseCoord() {

		Geodetic2DPoint[] points3d = {
				makePoint(-81.9916466079043, 29.9420387052815, 0.0),
				makePoint(-81.9980316162109, 29.9407501220703, 567.794982910156)
		};
		Geodetic2DPoint[] points2d = {
				makePoint(-81.9916466079043, 29.9420387052815),
				makePoint(-81.9980316162109, 29.9407501220703)
		};

		// valid format: 2 points both with lon,lat,elev
		checkCoordString("-81.9916466079043,29.9420387052815,0.0 -81.9980316162109,29.9407501220703,567.794982910156",
				points3d);

	  	// valid format: 2 points both with lon,lat
		checkCoordString("-81.9916466079043,29.9420387052815 -81.9980316162109,29.9407501220703",
				points2d);

		// coordinate strings with extra whitespace
		checkCoordString(" -81.9916466079043, 29.9420387052815, 0.0  -81.9980316162109, 29.9407501220703, 567.794982910156 ",
				points3d);
		checkCoordString("\t-81.9916466079043,\t29.9420387052815,\t0.0\n-81.9980316162109,\t29.9407501220703,\t567.794982910156\n",
				points3d);
		checkCoordString(" -81.9916466079043, 29.9420387052815  -81.9980316162109, 29.9407501220703 ",
				points2d);

		checkCoordString("+10,+20,+30",
				new Geodetic2DPoint[] {
					makePoint(10, 20, 30),
				}
		);

		// check values with exponents
		checkCoordString("1,2,7.76166643845e-007",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 7.76166643845e-007),
				}
		);
		checkCoordString("1,2,7.76166643845e",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 7.76166643845),
				}
		);
		checkCoordString("1,2,7.76166643845E+0",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 7.76166643845),
				}
		);
		checkCoordString("1,2,7.76166643845e+3",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 7761.66643845),
				}
		);

		//  [1 2 3]: point -> 1,0,0; line -> [1,0,0] [2,0,0] [3,0,0]
		checkCoordString("1 2 3",
				new Geodetic2DPoint[] {
					makePoint(1, 0),
					makePoint(2, 0),
					makePoint(3, 0),
				}
		);

		// [10 20 30]: point -> 10,0,0; line-> [10,0,0] [20,0,0] [30,0,0]
		checkCoordString("10 20 30 1,2,3",
				new Geodetic2DPoint[] {
					makePoint(10, 0),
					makePoint(20, 0),
					makePoint(30, 0),
					makePoint(1, 2, 3),
				}
		);

        // interpreted as  [1,2,3 4,5,6]
		checkCoordString("1,2,3, 4,5,6",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 3),
                    makePoint(4, 5, 6),
				}
		);

		// interpreted as [1,2,3 4,0,0]
		checkCoordString("1,2,3,4",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 3),
                    makePoint(4, 0),
				}
		);

		// point -> [1,2,3] line as [1,2,3] [5,6,7] -- extra values after 3rd value ignored
		checkCoordString("1,2,3  4,5,6",
				new Geodetic2DPoint[] {
					makePoint(1, 2, 3),
					makePoint(4, 5, 6),
				}
		);

        // extraneous whitespace and CR-LF in tuple
        checkCoordString("-10,\n\t10",   // interpreted as [-10,10,0] by GoogleEarth
				new Geodetic2DPoint[]{
						makePoint(-10, 10),
				}
		);

		// invalid lat/lon range
		checkCoordString("5000,1,0", new Geodetic2DPoint[] { } );
		// note Google Earth interprets this as 180,1,0 but giscore is stricter on valid ranges
		checkCoordString("0,500,0", new Geodetic2DPoint[] { } );
		// note Google Earth interprets this as 0,180,0 but giscore is stricter on valid ranges

		// interpret missing/bogus values as 0's
		checkCoordString("xxx,20,300",   // interpreted as [0,20,300] by GoogleEarth
				new Geodetic2DPoint[]{
						makePoint(0, 20, 300),
				}
		);
		checkCoordString("10,xxx,300",	// interpreted as [20,0,300] by GoogleEarth
				new Geodetic2DPoint[]{
						makePoint(10, 0, 300),
				}
		);
		checkCoordString("xxx,yyy,300",	// interpreted as [20,0,300] by GoogleEarth
				new Geodetic2DPoint[]{
						makePoint(0, 0, 300),
				}
		);

        checkCoordString("xxx,-8.6",	// interpreted as [0,-8.6,0] by GoogleEarth
				new Geodetic2DPoint[] {
					makePoint(0, -8.6),
				}
		);

		// partial numbers: ignore non-numeric text

		checkCoordString("1,2,3dd",		// interpreted as [1,2,3] by GoogleEarth
				new Geodetic2DPoint[] {
					makePoint(1, 2, 3),
				}
		);

		checkCoordString("xx10,20,300",	// interpreted as [0,20,300] by GoogleEarth
				new Geodetic2DPoint[] {
					makePoint(0, 20, 300),
				}
		);
		checkCoordString("10xx,20,300",	// interpreted as [10,20,300] by GoogleEarth
				new Geodetic2DPoint[] {
					makePoint(10, 20, 300),
				}
		);
	}

	private void checkCoordString(String coord, Geodetic2DPoint[] geoPoints) {
		List<Point> list = KmlInputStream.parseCoord(coord);
		if (list.isEmpty() && geoPoints.length == 0) return;
		if (list.size() != geoPoints.length) {
			System.out.println("Coord = "+ coord);
			System.out.println("actual list:");
			if (list.isEmpty())
				System.out.println("\t*empty*");
			else
				for (Point point : list) {
					Geodetic2DPoint pt = point.asGeodetic2DPoint();
          System.out.format("\t%f %f %s%n", pt.getLongitudeAsDegrees(), pt.getLatitudeAsDegrees(),
						point.is3D() ? Double.toString(((Geodetic3DPoint)pt).getElevation()) : "");
				}
			System.out.println("expected list:");
			if (geoPoints.length == 0)
				System.out.println("\t*empty*");
			else
				for (Geodetic2DPoint pt : geoPoints) {
					printPoint(pt);
				}
			Assert.fail("number of coordinates do not match: expected:<" +
					geoPoints.length + "> but was:<" + list.size() + ">");
		}

		for (int i = 0; i < geoPoints.length; i++) {
			Point point = list.get(i);
			Geodetic2DPoint pt = point.asGeodetic2DPoint();
			Geodetic2DPoint expPt = geoPoints[i];
			if (pt.getClass() != expPt.getClass() || !pt.equals(expPt)) {
				System.out.println("Coord = "+ coord);
				//if (pt.getClass() != expPt.getClass()) System.out.format("%s : %s%n", pt.getClass().getName(), expPt.getClass().getName());
        System.out.format("actual coord: %f %f %s%n", pt.getLongitudeAsDegrees(), pt.getLatitudeAsDegrees(),
						point.is3D() ? Double.toString(((Geodetic3DPoint)pt).getElevation()) : "");
        System.out.format("expected coord: %f %f %s%n", expPt.getLongitudeAsDegrees(), expPt.getLatitudeAsDegrees(),
							expPt instanceof Geodetic3DPoint ? Double.toString(((Geodetic3DPoint)expPt).getElevation()) : "");
				Assert.fail("Coordinate " + i + " does not match");
			}
		}
	}

	private static void printPoint(Geodetic2DPoint pt) {
    System.out.format("\t%f %f %s%n", pt.getLongitudeAsDegrees(), pt.getLatitudeAsDegrees(),
							pt instanceof Geodetic3DPoint ? Double.toString(((Geodetic3DPoint)pt).getElevation()) : "");
	}

	private static Geodetic2DPoint makePoint(double lon, double lat, double elev) {
		return new Geodetic3DPoint(
				new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES),
				elev);
	}

	private static Geodetic2DPoint makePoint(double lon, double lat) {
		return new Geodetic2DPoint(
				new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES));
	}

	private InputStream getStream(String filename) throws FileNotFoundException {
		File file = new File(getTestDir() + "/input/" + filename);
		if (file.exists()) return new FileInputStream(file);
		System.out.println("File does not exist: " + file);
		return getClass().getResourceAsStream(filename);
	}

}
