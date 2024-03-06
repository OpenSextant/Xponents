/****************************************************************************************
 *  TestEllipsoid.java
 *
 *  Created: Mar 27, 2007
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

import java.util.Set;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.opensextant.geodesy.Ellipsoid;
import org.opensextant.geodesy.Geodetic2DPoint;

public class TestEllipsoid extends TestCase {

    /**
     * This method exercises some of the Ellipsoid API, including performing
     * some surface distance calculation tests between airports of major cities.
     */
    public void testDistances() {
        Ellipsoid ellip = null;
        Set<String> names = Ellipsoid.getEllipsoidNames();
        for (String name : names) {
            ellip = Ellipsoid.getInstance(name);
            if (name.equals("WGS 84")) break;
        }
        assertNotNull(ellip);
        assertEquals(ellip, Ellipsoid.getInstance("WGS 84"));

        double distInM;
        long distInKm;

        // Do some distance calculations
        // Note: for Angle constructor parsing, degree symbol can be encoded in either
        // ISO-8859-1 (Latin-1) or unicode, but toString formatting always uses unicode
        Geodetic2DPoint London =
                new Geodetic2DPoint("51 deg 28' 15.19\" N, 0 deg 27' 33.41\" W");
        Geodetic2DPoint Boston =
                new Geodetic2DPoint("42\u00B0 22' 11.77\" N, 71\u00B0 1' 40.30\" W");
        Geodetic2DPoint Honolulu =
                new Geodetic2DPoint("21 deg 19' 55.36\" N, 157 deg 55' 11.08\" W");
        Geodetic2DPoint Sydney =
                new Geodetic2DPoint("33\u00B0 56' 43.70\" S, 151\u00B0 10' 51.43\" E");

        distInM = ellip.orthodromicDistance(London, Boston);
        assertEquals((long) distInM, (long) ellip.orthodromicDistance(Boston, London)); // round off to nearest meter
        distInKm = Math.round(distInM / 1000.0);
        assertEquals(5256L, distInKm);

        distInM = ellip.orthodromicDistance(Honolulu, Sydney);
        assertEquals(distInM, ellip.orthodromicDistance(Sydney, Honolulu));
        distInKm = Math.round(distInM / 1000.0);
        assertEquals(8155L, distInKm);

        distInM = ellip.orthodromicDistance(Honolulu, London);
        assertEquals(distInM, ellip.orthodromicDistance(London, Honolulu));
        distInKm = Math.round(distInM / 1000.0);
        assertEquals(11646L, distInKm);
    }

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        TestSuite suite = new TestSuite(TestEllipsoid.class);
        new TestRunner().doRun(suite);
    }
}
