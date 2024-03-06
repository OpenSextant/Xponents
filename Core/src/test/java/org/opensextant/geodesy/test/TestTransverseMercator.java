/****************************************************************************************
 *  TestTransverseMercator.java
 *
 *  Created: Mar 28, 2007
 *
 *  @author Paul Silvey
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
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.geodesy.Topocentric2DPoint;
import org.opensextant.geodesy.TransverseMercator;

public class TestTransverseMercator extends TestCase {

    /**
     * This method tests TransverseMercator projections along the Prime Meridian,
     * the Equator, and over randomly selected points within a typical UTM zone.
     */
    public void testProjections() {
        // Circumference of the earth at the Equator is 40,075.16 kilometers
        // so 1 arc minute along the Equator should equal 1855.3314815 meters
        // (note that 1852m in the official length for a standard nautical mile)
        // 6' == 0.1 degree is 11,131.989 meters
        Longitude lon;
        Latitude lat;
        Topocentric2DPoint en;
        TransverseMercator tm = new TransverseMercator(false);

        // Longitude tests vary central meridian around the globe at the Equator
        // and test for a consistent offset at 1° east of the central meridian.
        // Eastings should all be nearly 111325.18096 m, and Northings 0 m.
        lat = new Latitude(0.0, Angle.DEGREES);
        for (int cm = -181; cm <= 180; cm++) {
            tm.setCentralMeridian(new Longitude(cm, Angle.DEGREES));
            lon = new Longitude(cm + 1.0, Angle.DEGREES);
            en = tm.toTransverseMercator(lon, lat);
            assertEquals(en.getEasting(), 111325.18096, 0.00001);
            assertEquals(en.getNorthing(), 0.0);
        }

        // Latitude tests vary the Latitude from -90° to +88° by steps of 1°
        // Northings should differ from previous value by no less than 110574m and
        // by no more than 111693m. This is just a sanity range check, since precisely
        // correct values would require a table to compare against
        int prevNorthing = -10001405;
        lon = new Longitude(0.0, Angle.DEGREES);
        tm.setCentralMeridian(lon);
        for (int ol = -90; ol <= 88; ol++) {
            lat = new Latitude(ol + 1.0, Angle.DEGREES);
            en = tm.toTransverseMercator(lon, lat);
            int currNorthing = (int) en.getNorthing();
            assertEquals(en.getEasting(), 0.0);
            assertEquals(currNorthing - prevNorthing, 111134, 560);
            prevNorthing = currNorthing;
        }

        // Random point tests project and unproject to test for equality in transform
        // This is based on default Central Meridian (at the Prime Meridian), so
        // Longitude values are restricted to be within 5° of the Prime Meridian, and
        // Latitude values are restricted to be between -89° and +89°.  Accuracy should
        // be to hundredths of an arc second (2 fractional digits in toString format).
        Geodetic2DPoint p1, p2;
        Random r = new Random();
        int fractDig = 2;
        for (int i = 0; i < 1000; i++) {
            lon = new Longitude(r.nextDouble() * 5.0 *
                    ((r.nextBoolean()) ? -1 : +1), Angle.DEGREES);
            lat = new Latitude(r.nextDouble() * 89.0 *
                    ((r.nextBoolean()) ? -1 : +1), Angle.DEGREES);
            p1 = new Geodetic2DPoint(lon, lat);
            en = tm.toTransverseMercator(lon, lat);
            p2 = tm.toGeodetic(en.getEasting(), en.getNorthing());
			assertEquals("Geodetic2DPoint.toString()", p1.toString(fractDig), p2.toString(fractDig));
        }

    }

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestTransverseMercator.class);
        new TestRunner().doRun(suite);
    }
}
