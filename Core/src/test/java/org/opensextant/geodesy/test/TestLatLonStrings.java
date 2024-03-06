/*
 * TestLatLonStrings.java
 *
 * Created on January 17, 2008, 5:15 PM
 *
 * $Id$
 */
package org.opensextant.geodesy.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.LatLonParser;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TestLatLonStrings {

	private static final String[] testStringsEtrex = {
    		/* DD-MM.MMMH,DDD-MM.MMMH) */
			
			"36-22.123,048-05.881",

			"36-22.123N,048-05.881E",
			"36-22.123n,048-05.881e",
			"36-22.123N,048-05.881W",
			"36-22.123n,048-05.881w",
			
			"36-22.123S,048-05.881E",
			"36-22.123s,048-05.881e",
			"36-22.123S,048-05.881W",
			"36-22.123s,048-05.881w",
			
			"36-22.123456s,048-05.8811111111111111111111111111111w",
			"36-22.1233s,048-05.88166666666666666666666666666666w"
	};
	
    private static final String[] testStrings = {
 
    		/*Lat/Lon hddd.ddddd	*/ "S26.25333 E27.92333",
    		/*Lat/Lon hddd degmm.mmm*/	// "N33 56.539 W118 24.471", // this test pattern fails
    		/*Lat/Lon hddd degmm'ss.s" */ "N30 56 12.3 W118 24 12.3",
    		"77d02m06.00\"38dd53m20.76\"",
    		"W77d02m06.00\"38d53m20.76\"",
    		"77d02m06.00\"38d53m20.76\"N",
    		"77d02m06.00\"N38d53m20.76\"",
    		
    		"-314/61239",
    		"+38 -77",
    		"38+ 77-",
    		"+38 77-",
    		"38+ -77",
    		"38 77",
    		"+38 77",
    		"38 77-",
    		"38+ 77",
    		"38 -77",
    		
    		"W77d02'06.00\"38d53'20.76\"N",
    		"-38.889097 77.035000",
    		"38.889097 -77.035000",
    		"-0770206.00+385320.76",
    		"385320.76N 0770206.00W",
    		"+385320.76 -0770206.00",
    		"0770206.00W N385320.76",
    		"N385320.76 W0770206.00",
    		"385320.76N0770206.00W",

    		
      		".76E 0750206004797S",
      		"1.76E 0750206004797S",
      		"16.76E 0750206004797S",
      		"165.76E 0750206004797S",
    	  	"1653.76E 0750206004797S",
    		"16532.76E 0750206004797S",
    		"165320.76E 0750206004797S",
    		"1653201.76E 0750206004797S",
       		"385320S 0770206E",
       		"385320.76N 0770206.00W",
       		"385320.76S 0770206.004797E",
       		"3853207623S 0770206004797E",
         		
    		// ERROR Cases
    		"38.889097 -+",
    		"++38.889097",
    		"-+-38.889097",

    		// DD
    		"38d53m20.76sN 77d2m6.00sW",
    		"38.889097 -77.035000",
    		"+38.889097 -77.035000",
    		"W77.035000 38.889097N",
    		"W77.035000 N38.889097",    		
    		"77.035000W 38.889097N",
    		"77.035000W N38.889097",
    		"N38.889097 77.035000W",
    		"N38.889097 W77.035000",
    		"38.889097N 77.035000W",
    		"38.889097N W77.035000",
    		"38.889097S 77.035000W",
    		"38.889097S 77.035000E",
    		"38.889097N77.035000W",
    		"38.889097�-77.035000�",
    		"38.889097,-77.035000",
    		"38.889097/-77.035000",
    		"38.889097|-77.035000",
    		"38.8891023 -77.0350654",
    		"38.89 -77.0350",
    		"38 -77",

    		// DMS
    		"385320.76N 0770206.00W",
    		"+385320.76 -0770206.00",
    		"0770206.00W N385320.76",
    		"N385320.76 W0770206.00",
    		"385320.76N0770206.00W",
    		"W77d02'06.00\"38d53'20.76\"N",
    		"385320.76 N;0770206.00 W",

    		"38d53m20.76sS 77d2m6.00sW",
    		"38d53m20.76sN W77d2m6.00s",
    		"38 53 20.76N 77 2 6.00W",
    		"38:53:20.76N:077:02:06.00W",
    		"38/53/20.76N/77/2/6.00W",
    		"W77,2,6,N38,53,20.76",
    		"-0770206.00+385320.76",
    		"385320.7623N 0770206W",
    		"385320.76N 0770206.004797W",
    		"385320N 0770206W",
    		"3853207623N 0770206004797W",
    		"38532076N077020600W"
    };
    
	private static final Set badStrings;
	static {
		badStrings = new HashSet<>(
                Arrays.asList(new String[]{
                        "38.889097 -+",
                        "++38.889097",
                        "-+-38.889097",
                }));
	}

    /**
     * Test the LatLonParser class with test strings
     */
	@Test
    public void testLatLonParserEtrex() {
       	System.out.print("\n\n LatLonParser Test (Etrex unique format)\n\n");
    	LatLonParser parser = new LatLonParser();
    	for (String testStringEtrex : testStringsEtrex) {
    		try {
    			String latLon = parser.parseEtrexString(testStringEtrex);
    			System.out.println(testStringEtrex + " ----> " + latLon);
    		} catch (IllegalArgumentException e) {
    			// System.out.println(testStringEtrex + " Illegal Argument Exception");
				fail("unexpected exception for " + testStringEtrex);
    		}
    	}
    }
    
    /**
     * Test the LatLonParser class with test strings
     */
	@Test
    public void testLatLonParser() {
       	System.out.print("\n\n LatLonParser Test \n\n");
    	LatLonParser parser = new LatLonParser();
    	for (String testString : testStrings) {
    		try {
    			String latLon = parser.parseString(testString);
    			System.out.println(testString + " ----> " + latLon);
				if (badStrings.contains(testString))
					fail("expected IllegalArgumentException for " + testString);
    		} catch (IllegalArgumentException e) {
				if (!badStrings.contains(testString))
					fail("unexpected exception for " + testString);
    			// System.out.println(testString  + " Illegal Argument Exception");
    		}
    	}
    }
  
    /**
     * Test the LatLonParser class and Geodetic2DPoint class with test strings
     */
	@Test
    public void testLatLonParserGeodetic2DPoint() {
    	System.out.println("\n\n LatLonParserGeodetic2DPoint Test \n");
    	String latLon;
    	
    	LatLonParser parser = new LatLonParser();
    	Geodetic2DPoint point;
    	
       	for (String testString : testStrings) {   		
			try {
				latLon = parser.parseString(testString);
				if (badStrings.contains(testString))
					fail("expected IllegalArgumentException for " + testString);
			} catch (IllegalArgumentException e) {
				if (!badStrings.contains(testString))
					fail("unexpected exception for " + testString);
    			//System.out.println("\nError: parse exception with  " + testString);
    			continue;
    		}
			
			try {
				point = new Geodetic2DPoint(latLon);
			} catch (Exception e) {
				System.out.println("\nError: Geodetic2DPoint with " + latLon);
				continue;
			}
			
			System.out.println(testString + " ----> " +
					latLon + " ----> " +
					point.toString());
    	}
    }

	@Test(expected = IllegalArgumentException.class)
	public void testNullLatitude() {
		new Latitude((String)null);
		// expected: latStr cannot be null
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullLongitude() {
		new Longitude((String)null);
		// expected: latStr cannot be null
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyStringLatitude() {
		new Latitude("");
		// expected: Latitude must be non-empty value
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShortLatitude() {
		new Latitude("N"); //
		// expected: No valid tokens found in Angle string "+"
	}

	@Test
	public void testInvalidCreation() {
		try {
			// out of legal latitude range (-90 deg to +90 deg)
			new Latitude(200, Angle.DEGREES);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Latitude value exceeds pole value
		}

		try {
			new Latitude("200E"); // must end with N or S
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Latitude with E or W direction indicator
		}

		try {
			new Latitude("+200N");
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Latitude with N or S direction indicator should not have numeric sign prefix
		}

		try {
			// out of legal latitude range (-180 deg to +180 deg)
			new Longitude(2000, Angle.DEGREES);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Angle radians is too big
		}

		try {
			new Longitude("200N"); // must end with N or S
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Longitude with N or S direction indicator
		}

		try {
			new Longitude("+200E");
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected Longitude with E or W direction indicator should not have numeric sign prefix
		}
	}

	@Test
	public void testNullLatCompare() {
		Latitude lat = new Latitude(Math.PI/2);
		Latitude other = null;
		assertFalse(lat.equals(other));
	}

	@Test
	public void testNullLonCompare() {
		Longitude lon = new Longitude(Math.PI/2);
		Longitude other = null;
		assertFalse(lon.equals(other));
	}
  
	/**
	 * Main method for running class tests.
	 *
	 * @param args standard command line arguments - ignored.
	 */
	public static void main(String[] args) {
		TestSuite suite = new TestSuite(TestLatLonStrings.class);
		new TestRunner().doRun(suite);
	}
}
