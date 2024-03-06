/****************************************************************************************
 *  TestMGRS.java
 *
 *  Created: Mar 27, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2006
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
package org.opensextant.geodesy.test;

import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.opensextant.geodesy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestMGRS {
    private final static Class thisClass = TestMGRS.class;
    private final static Logger log = LoggerFactory.getLogger(thisClass);

    private final static String MGRS_washington_monument = "18SUJ2348306479";

	private static final Latitude NORTH_POLE = new Latitude(+90.0, Angle.DEGREES);
	private static final Latitude SOUTH_POLE = new Latitude(-90.0, Angle.DEGREES);
	private static final Longitude PRIME_MERIDIAN = new Longitude(0.0, Angle.DEGREES);

    /**
     * This method does an exhaustive test of possible MGRS square values
     */
    @Test
    public void testStringCombos() {
        int valid, total;
        String ms;

        // Many possible cells will not be legal, for a variety of reasons
        // (see thrown Exceptions for specifics; e.g., 1AAA, 54WZZ)
        // Considering (60 * 26 * 26 * 26) = 1,054,560 strings
        valid = 0;
        total = 0;
        for (int lonZone = 1; lonZone <= 60; lonZone++) {
            for (char latBand = 'A'; latBand <= 'Z'; latBand++) {
                for (char xSquare = 'A'; xSquare <= 'Z'; xSquare++) {
                    for (char ySquare = 'A'; ySquare <= 'Z'; ySquare++) {
                        try {
                            // UTM version has lonZone
                            total++;
                            ms = "" + lonZone + latBand + xSquare + ySquare;
                            new MGRS(ms);
                            valid++;
                        } catch (Exception ex) {
                            //System.err.println(ex.toString());
                        }
                    }
                }
            }
        }
        // There are 49422 valid UTM grid cells out of a possible 1054560
        // (only 4.686504324078289%)
        Assert.assertEquals(1054560, total);
        Assert.assertTrue(valid == 80491 || valid == 49422);
        //Assert.assertEquals(49422, valid);

        // Considering (26 * 26 * 26) = 17,576 strings
        // Many invalid values (e.g., ABA, YXY)
        valid = 0;
        total = 0;
        // UPS MGRS only valid for latBand with A,B,Y,Z
        char[] latBands = { 'A','B','Y','Z' };
        for (int latBand = 0; latBand < 4; latBand++) {
            for (char xSquare = 'A'; xSquare <= 'Z'; xSquare++) {
                for (char ySquare = 'A'; ySquare <= 'Z'; ySquare++) {
                    try {
                        // UPS version has no lonZone
                        total++;
                        ms = "" + latBands[latBand] + xSquare + ySquare;
                        new MGRS(ms);
                        valid++;
                    } catch (Exception ex) {
                        //System.err.println(ex.toString());
                    }
                }
            }
        }
        // There are 568 valid UPS grid cells out of a possible 2704
        // (only 21%). Would be total of 17576 possible values if
        // tried all possible latBands A..Z
        Assert.assertEquals(2704, total);
        Assert.assertTrue(valid == 568 || valid == 810);
        //Assert.assertEquals(568, valid);
    }

    @Test
    public void testGeodetic2DPoint() {
	MGRS mgrs = new MGRS(new Geodetic2DPoint()); // 31NAA6602100000
	Assert.assertEquals(mgrs.toString(2), "31NAA6600");
   }

    /**
     * This method generates a random sample of Geodetic points, converting them to MGRS
     * Strings and back into Geodetic form, to compare the two forms for proximal equality
     */
    @Test
    public void testGeodeticSample() {
        Random r = new Random();
        FrameOfReference f = new FrameOfReference();
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            Geodetic2DPoint g1 = TestGeoPoint.randomGeodetic2DPoint(r);
            Geodetic2DPoint g2 = new MGRS(new MGRS(g1).toString(5)).toGeodetic2DPoint();
            Assert.assertTrue(f.proximallyEquals(g1, g2));
        }
    }

    @Test
    public void testMGRSNullCompare() {
        MGRS mgrs = new MGRS(MGRS_washington_monument);
        MGRS other = null;
        Assert.assertFalse(mgrs.equals(other));
    }

	@Test
	public void testMgrsAtPoles() {
		Geodetic2DPoint northPole = new Geodetic2DPoint(PRIME_MERIDIAN, NORTH_POLE);
		MGRS mgrs = new MGRS(northPole);
		Assert.assertEquals("ZAH0000", mgrs.toString(2));
		Geodetic2DPoint southPole = new Geodetic2DPoint(PRIME_MERIDIAN, SOUTH_POLE);
		mgrs = new MGRS(southPole);
		// System.out.println(" -> " + mgrs.toString(2));
		Assert.assertEquals("BAN0000", mgrs.toString(2));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMgrsAtPoleNonZeroLon() {
		// java.lang.IllegalArgumentException: Longitude should be zero at a Pole, lon: -77.03524166666666
		Geodetic2DPoint northPole = new Geodetic2DPoint(
			new Longitude(-77, 2, 6.87), NORTH_POLE);
		new MGRS(northPole); // must throw exception
	}

	@Test
	public void testMgrsInvalidPrecision() {
		MGRS mgrs = new MGRS(MGRS_washington_monument);
		try {
			mgrs.toString(6);
			Assert.fail("Expected exception: java.lang.IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
		try {
			mgrs.toString(-1);
			Assert.fail("Expected exception: java.lang.IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

    /**
     * This method is used to test some specific landmark points around the globe
     */
    @Test
    public void testLandmarks() {
        // washington_monument == "18SUJ2348306479" -> (77° 2' 6.87" W, 38° 53' 22.07" N)
        // String MGRS_washington_monument = "18SUJ2348306479";
        Geodetic2DPoint Geod_washington_monument = new Geodetic2DPoint(
                new Longitude(-77, 2, 6.87), new Latitude(38, 53, 22.07));

        // statue_of_liberty == "18TWL8073104699" -> (74° 2' 40.41" W, 40° 41' 21.25" N)
        String MGRS_statue_of_liberty = "18TWL 80731\t04699";
        Geodetic2DPoint Geod_statue_of_liberty = new Geodetic2DPoint(
                new Longitude(-74, 2, 40.41), new Latitude(40, 41, 21.25));

        // toronto_cn_tower == "17TPJ3008533438" -> (79° 23' 13.67" W, 43° 38' 33.22" N)
        String MGRS_toronto_cn_tower = "17T PJ3008533438";
        Geodetic2DPoint Geod_toronto_cn_tower = new Geodetic2DPoint(
                new Longitude(-79, 23, 13.67), new Latitude(43, 38, 33.22));

        // eiffel_tower == "31UDQ4825211938' -> (2° 17' 40.21" E, 48° 51' 29.69" N)
        String MGRS_eiffel_tower = "31UDQ48252 11938";
        Geodetic2DPoint Geod_eiffel_tower = new Geodetic2DPoint(
                new Longitude(2, 17, 40.21), new Latitude(48, 51, 29.69));

        // great_pyramid == "36RUU1965817595" -> (31° 7' 50.78" E, 29° 58' 33.58" N)
        String MGRS_great_pyramid = "36RUU\r1965817595";
        Geodetic2DPoint Geod_great_pyramid = new Geodetic2DPoint(
                new Longitude(31, 7, 50.78), new Latitude(29, 58, 33.58));

        // christo_redentor == "23KPQ8345460723" -> (43° 12' 38.60" W, 22° 57' 5.69" S)
        String MGRS_christo_redentor = "23KPQ8345460723";
        Geodetic2DPoint Geod_christo_redentor = new Geodetic2DPoint(
                new Longitude(-43, 12, 38.60), new Latitude(-22, 57, 5.69));

        // pearl_harbor == "4QFJ0886462894" -> (157° 56' 59.94" W, 21° 21' 53.55" N)
        String MGRS_pearl_harbor = "4Q FJ 08864 62894";
        Geodetic2DPoint Geod_pearl_harbor = new Geodetic2DPoint(
                new Longitude(-157, 56, 59.94), new Latitude(21, 21, 53.55));

        // taj_mahal == "44RKR0691109266" -> (78° 2' 31.57" E, 27° 10' 29.69" N)
        String MGRS_taj_mahal = "44R\tKR0691109266";
        Geodetic2DPoint Geod_taj_mahal = new Geodetic2DPoint(
                new Longitude(78, 2, 31.57), new Latitude(27, 10, 29.69));

        // sydney_opera == "56HLH3488352274" -> (151° 12' 54.39" E, 33° 51' 24.96" S)
        String MGRS_sydney_opera = "56HLH3488352274";
        Geodetic2DPoint Geod_sydney_opera = new Geodetic2DPoint(
                new Longitude(151, 12, 54.39), new Latitude(-33, 51, 24.96));

        // roundoff_case == "31WCM5330500467" -> (0° 0' 0.00" E, 64° 0' 0.00" S)
        String MGRS_roundoff_case = "31WCM5330500467";
        Geodetic2DPoint Geod_roundoff_case = new Geodetic2DPoint(
                new Longitude(0, 0, 0.00), new Latitude(64, 0, 0.00));

        // sPolarTest == "ATN2097136228" -> (85° 40' 30.00" W, 85° 40' 30.00" S)
        String MGRS_sPolarTest = "ATN2097136228";
        Geodetic2DPoint Geod_sPolarTest = new Geodetic2DPoint(
                new Longitude(-85, 40, 30.25), new Latitude(-85, 40, 30.00));

        // nPolarTest == "ZGG7902863771" -> (85° 40' 30.00" E, 85° 40' 30.00" N)
        String MGRS_nPolarTest = "ZGG7902863771";
        Geodetic2DPoint Geod_nPolarTest = new Geodetic2DPoint(
                new Longitude(85, 40, 29.79), new Latitude(85, 40, 30.00));

        String[] mArray = {
                MGRS_washington_monument, MGRS_statue_of_liberty, MGRS_toronto_cn_tower,
                MGRS_eiffel_tower, MGRS_great_pyramid, MGRS_christo_redentor, MGRS_pearl_harbor,
                MGRS_taj_mahal, MGRS_sydney_opera, MGRS_roundoff_case, MGRS_sPolarTest,
                MGRS_nPolarTest
        };
        Geodetic2DPoint[] gArray = {
                Geod_washington_monument, Geod_statue_of_liberty, Geod_toronto_cn_tower,
                Geod_eiffel_tower, Geod_great_pyramid, Geod_christo_redentor, Geod_pearl_harbor,
                Geod_taj_mahal, Geod_sydney_opera, Geod_roundoff_case, Geod_sPolarTest,
                Geod_nPolarTest};

        FrameOfReference f = new FrameOfReference();
        for (int i = 0; i < mArray.length; i++) {
            Assert.assertTrue(f.proximallyEquals(new MGRS(mArray[i]).toGeodetic2DPoint(), gArray[i]));
//            String ms = mArray[i];
//            MGRS mc = new MGRS(ms);
//            Geodetic2DPoint gc = mc.getGeodetic();
//            System.out.println(ms + " -> " + gc.toString(2));
//
//            gc = gArray[i];
//            mc = new MGRS(gc);
//            System.out.println(mc + " <- " + gc.toString(2));
//            System.out.println();
        }
    }

    @Test
    public void testBadCoords() {
        String[] coords = {
                null,	  // null value for MGRS String is invalid
                "",	  // empty value for MGRS String is invalid
                "11",     // MGRS String parse error, string was entirely numeric
                "999AA",  // MGRS String parse error, 3 digit number '999' is too large for UTM longitudinal zone
                "1CD",    // MGRS String parse error, expecting 2 alpha characters for MGRS square, found only one, or end of string: D
                "1C11",   // xSquare character was not a letter: 1
                "1CD1",   // ySquare character was not a letter: 1
                "31UIO",  // Invalid MGRS easting square identifier 'I' for longitudinal zone 31
                "31UDO",  // Invalid MGRS northing square identifier 'O' for longitudinal zone 31
                "31UDQ4", // Length of easting/northing values was odd: 1: 4
                "31UDQ482521193",     // Length of easting/northing values was odd: 9: 482521193
                "31UDQ482521193800",  // Length of easting/northing values exceeded 10: 12: 482521193800
                "8LMS 36294 99126",   // MGRS northing out of range for square identifier 'S' in longitudinal zone 8
                "ZOH",	// First letter of MGRS square identifier ('O') is invalid for UPS N Polar Region
                "ZJO"   // Second letter of MGRS square identifier ('O') is invalid for UPS N Polar Region
        };
        for (String coord : coords) {
            try {
                new MGRS(coord);
                Assert.fail("expected to throw IllegalArgumentException for invalid MGRS: " + coord);
            } catch (IllegalArgumentException ex) {
                // expected result
                // ex.printStackTrace();
            }
        }
    }

    @Test
    public void testToString() {
        StringBuilder m = new StringBuilder("31UDQ");
        int prevLen = 0;
        for (int precision = 0; precision <= 5; precision++) {
            int len = new MGRS(m).toString().length();
            // each successive MGRS should have two more decimals in length
            Assert.assertTrue(len > prevLen);
            prevLen = len;
            m.append("11");
        }
    }

    @Test
    public void testHashSet() {
        StringBuilder m = new StringBuilder("31UDQ");
        Set<MGRS> set = new java.util.HashSet<>();
        for (int precision = 0; precision <= 5; precision++) {
            Assert.assertTrue(set.add(new MGRS(m)));
            m.append("11");
        }
        Assert.assertEquals(6, set.size());
    }

    @Test
    public void testStrictCheck() {
        new MGRS("1CBA"); // valid
        try {
            // 1CBA => MGRS easting out of range for square identifier 'B' in longitudinal zone 1 [strict mode]
            new MGRS("1CBA", true); // invalid
            Assert.fail("expected to throw IllegalArgumentException for non-strict MGRS: 1CBA");
        } catch (IllegalArgumentException ex) {
            // expected result
        }

        new MGRS("AAA"); // valid
        try {
            new MGRS("AAA", true); // invalid
            // AAA -> MGRS coordinate corresponds to a UPS point outside a polar region [strict mode]
            Assert.fail("expected to throw IllegalArgumentException for non-strict MGRS: AAA");
        } catch (IllegalArgumentException ex) {
            // expected result
        }
    }

    @Test
    public void testBoundary() {
        Geodetic2DPoint point = new Geodetic2DPoint(new Longitude(-0.00000019, Angle.DEGREES),
                new Latitude(6.40175, Angle.DEGREES));
        MGRS mgrs = new MGRS(point);
        Geodetic2DPoint pt = mgrs.toGeodetic2DPoint();
        Assert.assertEquals(point, pt);

        point = new Geodetic2DPoint(new Longitude(6.40175, Angle.DEGREES),
                new Latitude(-0.00000019, Angle.DEGREES));
        mgrs = new MGRS(point);
        pt = mgrs.toGeodetic2DPoint();
        Assert.assertEquals(point, pt);
    }

    @Test
    public void testFormatAndParse() throws Exception {
        MGRS m1, m2;
        String s1, s2;
        int p;
        final int n = 10;
        Random r = new Random();

        int count = 0;
        for (int i = 0; i < n; i++) {
            // Generate a random point
            Geodetic2DPoint p1 = new Geodetic2DPoint(r);
            m1 = new MGRS(p1);
            // Reduce precision
            p = r.nextInt(3) + 2;
            s1 = m1.toString(p);
            // Re-parse original point strictly using less precision, count errors
            try {
                new MGRS(s1, true);
            } catch (Exception ex) {
                count++;
            }
            // Re-parse original point non-strictly using less precision
            m2 = new MGRS(s1);
            // Increase precision
            s2 = m2.toString(5);
            log.debug(m1 + " vs. " + s2);
            // TODO: is there any assertion to be made here?
        }
        log.info("Total parse error rate after imprecise formatting: " +
                count + " out of " + n);
    }

    @Test
    public void testCreateFromLatLon() {
        MGRS mgrs = new MGRS(MGRS_washington_monument);
        Geodetic2DBounds bbox = mgrs.getBoundingBox();
        Geodetic2DPoint center = bbox.getCenter();
        MGRS mgrs2 = new MGRS(center.getLongitude(), center.getLatitude());
        Assert.assertEquals(mgrs, mgrs2);
    }

    @Test
    public void testCreateFromBoundingBox() {
        // test decreasing precision/increasing grid range from 18SUJ2348306479 (precision=5) to 18SUJ23 (precision=1)
        CharSequence seq = MGRS_washington_monument;
        double prev = 0;
        for (int i = 0; ; i++ ) {
            MGRS mgrs = new MGRS(seq);
            final Geodetic2DBounds bbox = mgrs.getBoundingBox();
            new MGRS(bbox);
            double diag = bbox.getDiagonal();
            Assert.assertTrue(diag > prev);
            if (i >= 4) break;
            prev = diag;
            seq = seq.subSequence(0, seq.length() - 2);
        }
    }

    @Test
    public void testCreateFromBounds() {
        MGRS mgrs = new MGRS(MGRS_washington_monument);
        Geodetic2DBounds bbox = mgrs.getBoundingBox();
        MGRS mgrs2 = new MGRS(bbox);
        // due to lose of precision in conversion the MGRS is not exact
        // at 5 digit precision.
        /*
          for (int i=0; i <= 5; i++) {
              System.out.format("%s\t%s%n",  mgrs.toString(i), mgrs2.toString(i));
          }
          */
        Assert.assertEquals(mgrs.toString(4), mgrs2.toString(4));

        // create non-square bounding box
        //System.out.println(bbox);
        Geodetic2DPoint west = new Geodetic2DPoint(bbox.getWestLon(), bbox.getSouthLat());
        Geodetic2DPoint east = new Geodetic2DPoint(bbox.getEastLon(),
                new Latitude(bbox.getNorthLat().inDegrees() + 10, Angle.DEGREES));
        Geodetic2DBounds bbox2 = new Geodetic2DBounds(west, east);
        //System.out.println(bbox2);
        try {
            new MGRS(bbox2);
            Assert.fail("Expected to throw NotAnMGRSBoxException");
        } catch (NotAnMGRSBoxException e) {
            // expected
        }
    }

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        log.info("start");
        JUnitCore.runClasses(thisClass);
        log.info("end");
    }
}
