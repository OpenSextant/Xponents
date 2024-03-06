/************************************************************************************
 *  UTM.java
 *
 *  Created: Dec 21, 2006
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2007
 *
 * The program is provided "as is" without any warranty express or implied, including
 * the warranty of non-infringement and the implied warranties of merchantability and
 * fitness for a particular purpose.  The Copyright owner will not be liable for any
 * damages suffered by you as a result of using the Program. In no event will the
 * Copyright owner be liable for any special, indirect or consequential damages or
 * lost profits even if the Copyright owner has been advised of the possibility of
 * their occurrence.
 *
 ***********************************************************************************/
package org.opensextant.geodesy;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * <P>
 * The UTM (Universal Transverse Mercator) class contains methods to parse and format
 * between UTM coordinate strings and their geodetic (longitude and latitude)
 * equivalents. A UTM object is defined only in terms of the Ellipsoid data model
 * against which projections are made. The default constructor uses the WGS 84
 * ellipsoid.
 * <p>
 * Notes:
 * <p>
 * Projection: Transverse Mercator (Gauss-Kruger type) in zones 6 deg wide.
 * <p>
 * Longitude of Origin: Central meridian (CM) of each projection zone (degrees of 3,
 * 9, 15, 21, 27, 33, 39, 45, 51, 57, 63, 69, 75, 81, 87, 93, 99, 105, 111, 117, 123,
 * 129, 135, 141, 147, 153, 159, 165, 171, 177, E and W).
 * <p>
 * Latitude of Origin: 0 deg (the Equator).
 * <p>
 * Unit: Meter.
 * <p>
 * False Northing: 0 meters at the Equator for the Northern Hemisphere;
 * 10,000,000 meters at the Equator for the Southern Hemisphere. 
 * <p>
 * False Easting: 500,000 meters at the Central Meridian (CM) of each zone. 
 * <p>
 * Scale Factor at the Central Meridian: 0.9996. 
 * <p>
 * Latitude Limits of System: From 80 deg S to 84 deg N.
 * </P><P>
 * Overlap: On large-scale maps and trig lists, the data for each zone, datum, or
 * ellipsoid overlaps the adjacent zone, datum, or ellipsoid a minimum of 40
 * kilometers. The UTM grid extends to 80.5 deg S and 84.5 deg N, providing a
 * 30-minute overlap with the UPS grid.
 * <p>
 * Limits of Projection Zones: The zones are bounded by meridians, the longitudes
 * of which are multiples of 6 deg east and west of the prime meridian.
 * </P><P>
 * Universal Transverse Mercator (UTM) coordinates define two dimensional,
 * horizontal, positions. The sixty UTM zone numbers designate 6 degree wide
 * longitudinal strips extending from 80 degrees South latitude to 84 degrees North
 * latitude. UTM zone characters are letters which designate 8 degree zones
 * extending north and south from the equator. Beginning at 80 deg south and
 * proceeding northward, twenty bands are lettered C through X, omitting I and O.
 * These bands are all 8 deg wide except for band X which is 12 deg wide (between
 * 72-84 deg N).
 * </P><P>
 * There are special UTM zones between 0 degrees and 36 degrees longitude above 72
 * degrees latitude and a special zone 32 between 56 degrees and 64 degrees north
 * latitude: </P>
 * <P>
 * UTM Zone 32 has been widened to 9 deg (at the expense of zone 31) between
 * latitudes 56 deg and 64 deg (band V) to accommodate southwest Norway. Thus zone
 * 32 it extends westwards to 3 deg E in the North Sea.
 * </P><P>
 * Similarly, between 72 deg and 84 deg (band X), zones 33 and 35 have been widened
 * to 12 deg to accommodate Svalbard. To compensate for these 12 deg wide zones,
 * zones 31 and 37 are widened to 9 deg and zones 32, 34, and 36 are eliminated.
 * Thus the W and E boundaries of zones are 31: 0 - 9 deg E, 33: 9 - 21 deg E,
 * 35: 21 - 33 deg E and 37: 33 - 42 deg E.
 * </P>
 */
public class UTM implements Serializable {
    private static final long serialVersionUID = 1L;

    // Static Class Constants
    public static final double MAX_NORTH_LATDEG = 84.0;
    public static final double MIN_SOUTH_LATDEG = -80.0;
    private static final double MIN_EASTING = 100000.0;
    private static final double MAX_EASTING = 900000.0;
    private static final double MIN_NORTHING = 0.0;
    private static final double MAX_NORTHING = 10000000.0;
    private static final double SCALE = 0.9996;
    private static final Latitude EQUATOR = new Latitude(0.0);

    public static final double FALSE_EASTING = 500000.0;
    public static final double FALSE_NORTHING = 10000000.0;

    private static final double ROUNDING_POS = 1e+6;
    private static final double ROUNDING_NEG = 1e-6;

    // Cached Central Meridian values to avoid object creation and computation at run-time
    private static final Longitude[] CENTRAL_MERIDIAN = new Longitude[61];

    static {
        for (int lonZone = 1; lonZone <= 60; lonZone++) {
            CENTRAL_MERIDIAN[lonZone] =
                    new Longitude((6.0 * lonZone + ((lonZone >= 31) ? -183.0 : 177.0)),
                            Angle.DEGREES);
        }
    }

    // Initialize min northings for lat bands
    // These tables are used to help assign and validate MGRS square identifiers for northings
    private static final Map<Character, Integer> MIN_NORTHINGS =
            new HashMap<>();
    private static final Map<Character, Integer> MAX_NORTHINGS =
            new HashMap<>();

    static {
        Longitude lon;
        Longitude centLon = new Longitude(-3.0, Angle.DEGREES);
        Longitude edgeLon = new Longitude(-6.0, Angle.DEGREES);

        for (char latBand = 'C'; latBand <= 'X'; latBand++) {
            if ((latBand == 'I') || (latBand == 'O')) continue;

            // min Northings are inclusive (northing must be greater than or equal to this min)
            // min Northings for Northern Hemisphere are along a central meridian, but for
            // the Southern Hemisphere, they occur at the edges of a lonZone (+/- 3 deg from CM)
            lon = (latBand < 'N') ? edgeLon : centLon;
            Latitude minLat = new Latitude(UTM.minLatDegrees(latBand), Angle.DEGREES);
            double minNorthing = new UTM(new Geodetic2DPoint(lon, minLat)).getNorthing();
            MIN_NORTHINGS.put(latBand, (int) Math.round(minNorthing));

            // max Northings are exclusive (northing must be less than this max)
            // max Northings for Southern Hemisphere are along a central meridian, but for
            // the Northern Hemisphere, they occur at the edges of a lonZone (typically +/- 3 deg
            // from CM). Special case at equator for southern hemisphere latBand 'M' is
            // FALSE_NORTHING
            double maxNorthing;
            if (latBand == 'M') {
                maxNorthing = UTM.FALSE_NORTHING;
            } else {
                lon = (latBand < 'N') ? centLon : edgeLon;
                Latitude maxLat = new Latitude(UTM.maxLatDegrees(latBand), Angle.DEGREES);
                maxNorthing = new UTM(new Geodetic2DPoint(lon, maxLat)).getNorthing();
            }
            MAX_NORTHINGS.put(latBand, (int) Math.round(maxNorthing));
        }
    }

    private static final Longitude V31_CENTRAL_MERIDIAN = new Longitude(1.5, Angle.DEGREES);
    private static final Longitude X31_CENTRAL_MERIDIAN = new Longitude(4.5, Angle.DEGREES);
    private static final Longitude V32_CENTRAL_MERIDIAN = new Longitude(7.5, Angle.DEGREES);
    private static final Longitude X37_CENTRAL_MERIDIAN = new Longitude(37.5, Angle.DEGREES);

    private static final int V31_MAX_NORTHING = (int) Math.round(
            new UTM(new Longitude(0.0, Angle.DEGREES), new Latitude(63, 59, 59.99)).getNorthing());
    private static final int V32_MAX_NORTHING = (int) Math.round(
            new UTM(new Longitude(3.0, Angle.DEGREES), new Latitude(63, 59, 59.99)).getNorthing());
    private static final int X31_X37_MAX_NORTHING = (int) Math.round(
            new UTM(new Longitude(0.0, Angle.DEGREES), new Latitude(83, 59, 59.99)).getNorthing());
    private static final int X33_X35_MAX_NORTHING = (int) Math.round(
            new UTM(new Longitude(21.0, Angle.DEGREES), new Latitude(83, 59, 59.99)).getNorthing());

    // Static Class Methods

    /**
     * This method determines the UTM longitudinal zone number for a given lon (in decimal degrees).
     *
     * @param lonDeg  degrees of longitude to map to UTM longitudinal zone number
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return int for longitudinal zone number (1 to 60)
     */
    public static int getLonZone(double lonDeg, char latBand) {
        // Round value to correct for accumulation of numerical error in UTM projection
        lonDeg = Math.floor(Math.round(lonDeg * ROUNDING_POS) * ROUNDING_NEG);

        // Normalize the longitude in degrees if necessary
        while (lonDeg < -180.0) lonDeg += 360.0;
        while (lonDeg >= 180.0) lonDeg -= 360.0;
        // Compute nominal zone value for most cases
        int lonZone = 1 + (int) ((lonDeg + 180.0) / 6.0);
        // Five zones have special boundaries to be checked, override nominal if necessary
        if (latBand == 'V') {
            if ((3.0 <= lonDeg) && (lonDeg < 12.0)) lonZone = 32;
        } else if (latBand == 'X') {
            if ((0.0 <= lonDeg) && (lonDeg < 9.0)) lonZone = 31;
            else if ((9.0 <= lonDeg) && (lonDeg < 21.0)) lonZone = 33;
            else if ((21.0 <= lonDeg) && (lonDeg < 33.0)) lonZone = 35;
            else if ((33.0 <= lonDeg) && (lonDeg < 42.0)) lonZone = 37;
        }
        return lonZone;
    }

    /**
     * This method determines the minimum longitude (in degrees) for a given lon zone
     * and lat band.
     *
     * @param lonZone UTM Longitudinal Zone (1 to 60)
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return minimum longitude (in degrees) for this UTM cell (lon zone and lat band)
     */
    public static double minLonDegrees(int lonZone, char latBand) {
        double lonDeg = (lonZone * 6.0) - 186.0;
        // Four zones have min lon overrides
        if ((latBand == 'V') && (lonZone == 32)) lonDeg = 3.0;
        else if (latBand == 'X') {
            if (lonZone == 33) lonDeg = 9.0;
            else if (lonZone == 35) lonDeg = 21.0;
            else if (lonZone == 37) lonDeg = 33.0;
        }
        return lonDeg;
    }

    /**
     * This accessor method gets the appropriate central meridian for the given UTM lon zone
     * and lat band.
     *
     * @param lonZone UTM Longitudinal Zone (1 to 60)
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return Longitude of appropriate central meridian for the given UTM cell (zone and band)
     */
    public static Longitude getCentralMeridian(int lonZone, char latBand) {
        Longitude cm = CENTRAL_MERIDIAN[lonZone];
        if (latBand == 'V') {
            if (lonZone == 31) cm = V31_CENTRAL_MERIDIAN;
            else if (lonZone == 32) cm = V32_CENTRAL_MERIDIAN;
        } else if (latBand == 'X') {
            if (lonZone == 31) cm = X31_CENTRAL_MERIDIAN;
            else if (lonZone == 37) cm = X37_CENTRAL_MERIDIAN;
        }
        return cm;
    }

    /**
     * This method determines the maximum longitude (in degrees) for a given lon zone &amp; lat band.
     *
     * @param lonZone UTM Longitudinal Zone (1 to 60)
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return maximum longitude (in degrees) for this UTM cell (lon zone and lat band)
     */
    public static double maxLonDegrees(int lonZone, char latBand) {
        double lonDeg = (lonZone * 6.0) - 180.0;
        // Four zones have max lon overrides
        if ((latBand == 'V') && (lonZone == 31)) lonDeg = 3.0;
        else if (latBand == 'X') {
            if (lonZone == 31) lonDeg = 9.0;
            else if (lonZone == 33) lonDeg = 21.0;
            else if (lonZone == 35) lonDeg = 33.0;
        }
        return lonDeg;
    }

    /**
     * This method determines the UTM latitudinal band char for a given lat (in decimal degrees).
     *
     * @param latDeg degrees of latitude to map to UTM latitude band character
     * @return character representing latitude band ('C' to 'X', skipping 'I' and 'O')
     * @throws IllegalArgumentException error if latitude value is out of valid range
     */
    public static char getLatBand(double latDeg) {
        // validate that latitude is within proper range (allow half degree overlap with UPS)
        if ((latDeg < -80.5) || (84.5 < latDeg))
            throw new IllegalArgumentException("Latitude value '" + latDeg +
                    "' is out of legal range (-80 deg to 84 deg) for UTM");

        // Round value to correct for accumulation of numerical error in UTM projection
        latDeg = Math.floor(Math.round(latDeg * ROUNDING_POS) * ROUNDING_NEG);

        // Restrict calculated index to 20 8 deg-wide bands between -80 deg and +80 deg
        int i;
        if (latDeg < -80.0) i = 0;                  // correct for extra 'C' band range
        else if (80.0 <= latDeg) i = 19;            // correct for extra 'X' band range
        else i = (int) (10.0 + (latDeg / 8.0));     // index values for all others

        char latBand = (char) ('C' + i);
        if (i > 10) latBand += 2;
        else if (i > 5) latBand += 1;               // adjust for missing 'I' and 'O'
        return latBand;
    }

    /**
     * This method returns the inclusive minimum latitude (in decimal degrees)
     * for the given UTM latitude band char (extra half degree of allowance not included).
     *
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return minimum latitude for the given band, in decimal degrees
     * @throws IllegalArgumentException - error if lat band char is out of range
     */
    public static double minLatDegrees(char latBand) {
        validateLatBand(latBand);

        int i = (int) latBand - (int) 'C';
        if (latBand > 'N') i -= 2;
        else if (latBand > 'H') i -= 1;             // adjust for missing 'I' and 'O'
        return (8.0 * (i - 10.0));
    }

    /**
     * This method returns the exclusive maximum latitude (in decimal degrees)
     * for the given UTM latitude band char (extra half degree of allowance not included).
     *
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return maximum latitude for the given band, in decimal degrees
     * @throws IllegalArgumentException - error if lat band char is out of range
     */
    public static double maxLatDegrees(char latBand) {
        validateLatBand(latBand);

        int i = (int) latBand - (int) 'C';
        if (latBand > 'N') i -= 2;
        else if (latBand > 'H') i -= 1;             // adjust for missing 'I' and 'O'
        double w = (latBand == 'X') ? 12.0 : 8.0;   // Band 'X' has extra 4.0 deg N
        return ((8.0 * (i - 10.0)) + w);
    }

    /**
     * This method returns the minimum northing value (in meters) for the specified
     * latitude band.
     *
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return minimum northing value for the specified latitude band
     * @throws IllegalArgumentException - error if the latitude band is invalid
     */
    public static int minNorthing(char latBand) {
        Integer minNorthing = UTM.MIN_NORTHINGS.get(latBand);
        if (minNorthing == null)
            throw new IllegalArgumentException("Invalid latitude band '" + latBand + "'");
        return minNorthing;
    }

    /**
     * This method returns the maximum northing value (in meters) for the specified
     * latitude band.
     *
     * @param lonZone UTM Longitudinal Zone (1 to 60)
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return maximum northing value for the specified latitude band
     * @throws IllegalArgumentException error if the latitude band is invalid
     */
    public static int maxNorthing(int lonZone, char latBand) {
        Integer maxNorthing = UTM.MAX_NORTHINGS.get(latBand);
        if (maxNorthing == null)
            throw new IllegalArgumentException("Invalid latitude band '" + latBand + "'");
        // See if we need to override for special exception cells
        if (latBand == 'V') {
            if (lonZone == 31) maxNorthing = V31_MAX_NORTHING;
            else if (lonZone == 32) maxNorthing = V32_MAX_NORTHING;
        } else if (latBand == 'X') {
            if ((lonZone == 31) || (lonZone == 37)) maxNorthing = X31_X37_MAX_NORTHING;
            else if ((lonZone == 33) || (lonZone == 35)) maxNorthing = X33_X35_MAX_NORTHING;
        }
        return maxNorthing;
    }

    /**
     * This method determines the hemisphere ('N' or 'S') for the given lat band.
     *
     * @param latBand UTM latitude band character ('C' to 'X", not including 'I' or 'O')
     * @return hemisphere character ('N' for Northern, or 'S' for Southern)
     * @throws IllegalArgumentException error if lat band char is out of range
     */
    public static char getHemisphere(char latBand) {
        validateLatBand(latBand);
        return (latBand < 'N') ? 'S' : 'N';
    }

    /**
     * This method tests longitudinal zone to see if it is valid (by itself).
     *
     * @param lonZone UTM Longitudinal Zone (1 to 60)
     * @throws IllegalArgumentException error if UTM Longitudinal Zone is invalid
     */
    public static void validateLonZone(int lonZone) {
        if ((lonZone < 1) || (60 < lonZone))
            throw new IllegalArgumentException("UTM longitudinal zone '" + lonZone +
                    "' is outside of valid range (1 to 60)");
    }

    /**
     * This method tests latitudinal zone to see if it is valid (by itself).
     *
     * @param latBand UTM Latitudinal Band ('C' to 'X', but not 'I' or 'O')
     * @throws IllegalArgumentException error if UTM Latitudinal Band is invalid
     */
    public static void validateLatBand(char latBand) {
        if ((latBand < 'C') || ('X' < latBand) || (latBand == 'I') || (latBand == 'O'))
            throw new IllegalArgumentException("UTM latitudinal band '" + latBand +
                    "' is not valid");
    }

    /**
     * This method tests longitudinal zone and latitudinal band to make sure they are
     * consistent together.
     *
     * @param lonZone UTM Longitudinal Zone (1 to 60)
     * @param latBand UTM Latitudinal Band ('C' to 'X', but not 'I' or 'O')
     * @throws IllegalArgumentException error if Zone and Band combination is invalid
     */
    public static void validateZoneAndBand(int lonZone, char latBand)
            throws IllegalArgumentException {
        validateLonZone(lonZone);
        validateLatBand(latBand);
        if ((latBand == 'X') && ((lonZone == 32) || (lonZone == 34) || (lonZone == 36)))
            throw new IllegalArgumentException("Invalid longitude zone '" +
                    lonZone + "' in latitude band 'X'");
    }

    /*
     * This method tests hemisphere character to see if it is valid (by itself).
     *
     * @param hemisphere UTM Hemisphere character ('N' for North, 'S' for South)
     * @throws IllegalArgumentException error if hemisphere character is invalid
     */
    private static void validateHemisphere(char hemisphere) {
        if ((hemisphere != 'N') && (hemisphere != 'S'))
            throw new IllegalArgumentException("Invalid hemisphere '" +
                    hemisphere + "', should be 'N' or 'S'");
    }

    /*
     * This method tests easting value to see if it is within allowed positive numerical range.
     *
     * @param easting UTM Easting value in meters
     * @throws IllegalArgumentException error if easting value is out of range
     */
    private static void validateEasting(double easting) {
        if ((easting < MIN_EASTING) || (MAX_EASTING < easting))
            throw new IllegalArgumentException("Easting value '" + easting +
                    "' is outside of valid range (100,000 to 900,000 meters)");
    }

    /*
     * This method tests northing value to see if it is within allowed positive numerical range.
     *
     * @param northing UTM Northing value in meters
     * @throws IllegalArgumentException error if northing value is out of range
     */
    private static void validateNorthing(double northing) {
        if ((northing < MIN_NORTHING) || (MAX_NORTHING < northing))
            throw new IllegalArgumentException("Northing value '" + northing +
                    "' is outside of valid range (0 to 10,000,000 meters)");
    }

    // *************************** End of Static Definitions ********************************

    // Instance Variables
    @NotNull
    private final TransverseMercator tm;  // Transverse Mercator projection object (keeper of ellipsoid)
    private int lonZone;            // UTM Longitudinal Zone number (1 to 60)
    private char latBand;           // UTM Lat Band char('C' to 'X', not including 'I' or 'O')
    private char hemisphere;        // Hemisphere char ('N' for Northern, 'S' for Southern)
    private double easting;         // meters E of false easting origin (relative to lonZone's CM)
    private double northing;        // meters N of false northing origin (equator for 'N' hemisphere)

    @NotNull
    private Geodetic2DPoint lonLat; // Longitude and Latitude coordinates for this UTM position

    /**
     * This constructor takes an ellipsoid.  If WGS 84 is desired, instead use one of
     * the constructors without the ellipsoid parameter, since WGS 84 is the default.
     * The specified Geodetic2DPoint (lon-lat) point is converted to its UTM equivalent.
     *
     * @param ellip  Ellipsoid data model for earth
     * @param lonLat Geodetic2DPoint coordinates (lon-lat) to be converted to UTM
     * @throws NullPointerException if ellip or lonLat are null
     */
    public UTM(Ellipsoid ellip, Geodetic2DPoint lonLat) {
        tm = new TransverseMercator(true);    // Distortion warning will cause exception
        tm.setOriginLatitude(EQUATOR);
        tm.setScaleFactor(SCALE);
        tm.setEllipsoid(ellip);
        this.lonLat = lonLat;
        fromGeodetic();
    }

    /**
     * This constructor takes an ellipsoid.  If WGS 84 is desired, instead use one of
     * the constructors without the ellipsoid parameter, since WGS 84 is the default.
     * The specified longitude and latitude values are converted to a UTM equivalent.
     *
     * @param ellip Ellipsoid data model for earth
     * @param lon   Longitude of point to convert to UTM
     * @param lat   Latitude of point to convert to UTM
     * @throws NullPointerException if ellip is null
     */
    public UTM(Ellipsoid ellip, Longitude lon, Latitude lat) {
        tm = new TransverseMercator(true);    // Distortion warning will cause exception
        tm.setOriginLatitude(EQUATOR);
        tm.setScaleFactor(SCALE);
        tm.setEllipsoid(ellip);
        lonLat = new Geodetic2DPoint(lon, lat);
        fromGeodetic();
    }

    /**
     * This constructor takes an ellipsoid.  If WGS 84 is desired, instead use one of
     * the constructors without the ellipsoid parameter, since WGS 84 is the default.
     * Convert UTM parameters to equivalent geodetic (lon-lat) position, compute lat band.
     *
     * @param ellip      Ellipsoid data model for earth
     * @param lonZone    UTM longitudinal zone (1 to 60)
     * @param hemisphere character 'N' for Northern or 'S' for Southern hemisphere
     * @param easting    positive meters east of false adjusted central meridian for lonZone
     * @param northing   positive meters north of false adjusted origin (equator for 'N' hemisphere)
     * @throws NullPointerException     if ellip is null
     * @throws IllegalArgumentException input parameter error(s)
     */
    public UTM(Ellipsoid ellip, int lonZone, char hemisphere, double easting, double northing) {
        tm = new TransverseMercator(true);    // Distortion warning will cause exception
        tm.setOriginLatitude(EQUATOR);
        tm.setScaleFactor(SCALE);
        tm.setEllipsoid(ellip);
        this.lonZone = lonZone;
        this.hemisphere = hemisphere;
        this.easting = easting;
        this.northing = northing;
        toGeodetic(true);                     // Validate easting and northing values
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for TransverseMercator).
     * The specified Geodetic2DPoint (lon-lat) point is converted to its UTM equivalent.
     *
     * @param lonLat Geodetic2DPoint coordinates (lon-lat) to be converted to UTM
     * @throws NullPointerException if lonLat is null
     */
    public UTM(Geodetic2DPoint lonLat) {
        tm = new TransverseMercator(true);
        tm.setOriginLatitude(EQUATOR);
        tm.setScaleFactor(SCALE);
        this.lonLat = lonLat;
        fromGeodetic();
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for TransverseMercator).
     * The specified longitude and latitude values are converted to a UTM equivalent.
     *
     * @param lon Longitude of point to convert to UTM
     * @param lat Latitude of point to convert to UTM
     * @throws NullPointerException if lat or lon are null
     */
    public UTM(Longitude lon, Latitude lat) {
        tm = new TransverseMercator(true);    // Distortion warning will cause exception
        tm.setOriginLatitude(EQUATOR);
        tm.setScaleFactor(SCALE);
        lonLat = new Geodetic2DPoint(lon, lat);
        fromGeodetic();
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for TransverseMercator).
     * Convert UTM parameters to equivalent geodetic (lon-lat) position, compute lat band,
     * and allows projection distortion warnings to optionally cause an Exception.
     *
     * @param lonZone             UTM longitudinal zone (1 to 60)
     * @param hemisphere          character 'N' for Northern or 'S' for Southern hemisphere
     * @param easting             positive meters east of false adjusted central meridian for lonZone
     * @param northing            positive meters north of false adjusted origin (equator for 'N' hemisphere)
     * @param distortionException boolean indicating whether distortion exceptions should be thrown
     * @throws IllegalArgumentException input parameter error(s)
     */
    public UTM(int lonZone, char hemisphere, double easting, double northing,
               boolean distortionException) {
        tm = new TransverseMercator(distortionException);    // Distortion warning exception?
        tm.setOriginLatitude(EQUATOR);
        tm.setScaleFactor(SCALE);
        this.lonZone = lonZone;
        this.hemisphere = hemisphere;
        this.easting = easting;
        this.northing = northing;
        toGeodetic(distortionException);                     // if distortion OK, skip validation too
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for TransverseMercator).
     * Convert UTM parameters to equivalent geodetic (lon-lat) position, compute lat band.
     *
     * @param lonZone    UTM longitudinal zone (1 to 60)
     * @param hemisphere character 'N' for Northern or 'S' for Southern hemisphere
     * @param easting    positive meters east of false adjusted central meridian for lonZone
     * @param northing   positive meters north of false adjusted origin (equator for 'N' hemisphere)
     * @throws IllegalArgumentException input parameter error(s)
     */
    public UTM(int lonZone, char hemisphere, double easting, double northing) {
        this(lonZone, hemisphere, easting, northing, true);
    }

    /*
     * This method converts UTM projection (zone, hemisphere, easting and northing)
     * coordinates to geodetic (longitude and latitude) coordinates, based on the
     * current ellipsoid model.
     *
     * @throws IllegalArgumentException input parameter error(s)
     */
    private void toGeodetic(boolean validate) {
        // Validate input parameters
        validateLonZone(lonZone);
        validateHemisphere(hemisphere);
        if (validate) {
            validateEasting(easting);
            validateNorthing(northing);
        }

        // set nominal central meridian & adjust false values to regain signed offsets
        Longitude cm = CENTRAL_MERIDIAN[lonZone];
        double e = easting - FALSE_EASTING;
        double n = (hemisphere == 'S') ? northing - FALSE_NORTHING : northing;

        // Un-project to geodetic coordinates, assume no special zone override necessary
        tm.setCentralMeridian(cm);
        lonLat = tm.toGeodetic(e, n);

        // Determine lat band and validate cell combo
        // (lon zones 32, 34, & 36 are illegal in band 'X')
        latBand = getLatBand(lonLat.getLatitude().inDegrees());
//        if (validate) 
        validateZoneAndBand(lonZone, latBand);

        // Determine if special zone override makes it necessary to un-project again
        // with adjustments
        if (hemisphere == 'N') {
            // Use the un-projected northing latitude band to determine special case
            // lon zone CM values. Un-project again if central meridian has changed due
            // to special zones
            Longitude ocm = getCentralMeridian(lonZone, latBand);
            if (ocm != cm) {
                tm.setCentralMeridian(ocm);
                lonLat = tm.toGeodetic(e, n);
            }
        }
    }

    /*
     * This method converts from Geodetic2DPoint coordinates (lon-lat) to UTM parameters.
     */
    private void fromGeodetic() {
        Longitude lon = lonLat.getLongitude();
        Latitude lat = lonLat.getLatitude();

        latBand = UTM.getLatBand(lat.inDegrees());
        lonZone = UTM.getLonZone(lon.inDegrees(), latBand);

        hemisphere = getHemisphere(latBand);

        tm.setCentralMeridian(getCentralMeridian(lonZone, latBand));
        Topocentric2DPoint en = tm.toTransverseMercator(lon, lat);

        easting = en.getEasting() + FALSE_EASTING;
        double n = en.getNorthing();
        // Correct northing for latitudes barely south of the equator
        if ((n < 0.0) && (hemisphere == 'N')) n = 0.0;
        northing = (n < 0.0) ? n + FALSE_NORTHING : n;
    }

    /**
     * This method is used to get the currently set Ellipsoid earth model for making
     * projections during conversion to and from geodetic coordinates.
     *
     * @return the currently set Ellipsoid earth model being used by this UTM object
     */
    public Ellipsoid getEllipsoid() {
        return tm.getEllipsoid();
    }

    /**
     * This method returns the geodetic (lon-lat) point for this UTM position.
     *
     * @return Geodetic2DPoint object (lon-lat) coordinates
     */
    @NotNull
    public Geodetic2DPoint getGeodetic() {
        return lonLat;
    }

    /**
     * This method returns the Longitude part of the geodetic coordinates for this UTM object.
     *
     * @return Longitude coordinate of the geodetic (lon-lat) point for this UTM position
     */
    @NotNull
    public Longitude getLongitude() {
        return lonLat.getLongitude();
    }

    /**
     * This method returns the Latitude part of the geodetic coordinates for this UTM object.
     *
     * @return Latitude coordinate of the geodetic (lon-lat) point for this UTM position
     */
    @NotNull
    public Latitude getLatitude() {
        return lonLat.getLatitude();
    }

    /**
     * This method returns the UTM longitudinal zone (1 to 60) for this UTM object.
     *
     * @return int longitude zone number (1 to 60)
     */
    public int getLonZone() {
        return lonZone;
    }

    /**
     * This method returns the UTM latitudinal band character ('C' to 'X', not
     * including 'I' or 'O') for this UTM object.
     *
     * @return char for latitudinal band ('C' to 'X', not including 'I' or 'O')
     */
    public char getLatBand() {
        return latBand;
    }

    /**
     * This method returns the UTM hemisphere character ('N' for Northern,
     * 'S' for Southern) for this UTM object.
     *
     * @return char for hemisphere ('N' for Northern, 'S' for Southern)
     */
    public char getHemisphere() {
        return hemisphere;
    }

    /**
     * This method returns the UTM easting value (number of meters east of the central
     * meridian for the longitudinal zone, false adjusted to make this number always
     * positive).
     *
     * @return double easting value for this UTM object in positive meters
     */
    public double getEasting() {
        return easting;
    }

    /**
     * This method returns the UTM northing value (number of meters north of the equator or
     * falsely adjusted equator, to make this number always positive)
     *
     * @return double northing value for this UTM object in positive meters
     */
    public double getNorthing() {
        return northing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UTM utm = (UTM) o;

        if (Double.compare(utm.easting, easting) != 0) return false;
        if (hemisphere != utm.hemisphere) return false;
        if (latBand != utm.latBand) return false;
        if (lonZone != utm.lonZone) return false;
        if (Double.compare(utm.northing, northing) != 0) return false;
        return lonLat.equals(utm.lonLat);
        // TODO: following fails since TransverseMercator does not implement Object.equals(Object) so skip it
        // if (tm != null ? !tm.equals(utm.tm) : utm.tm != null) return false;
    }

    @Override
    public int hashCode() {
        // int result;
        long temp;
        int result = lonZone;
        result = 31 * result + (int) latBand;
        result = 31 * result + (int) hemisphere;
        temp = easting != 0.0d ? Double.doubleToLongBits(easting) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = northing != 0.0d ? Double.doubleToLongBits(northing) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + lonLat.hashCode();
        return result;
    }

    /**
     * This method formats the UTM object as a printable string, using the specified number of
     * fractional digits for the easting and northing offsets.
     *
     * @param fractDig number of digits past the decimal point for easting and northing
     * @return String formatted UTM coordinate
     */
    public String toString(int fractDig) {
        StringBuilder fract = new StringBuilder("0");
        if (fractDig > 0) {
            fract.append('.');
            while (fractDig-- > 0) fract.append('0');
        }
        DecimalFormat offFmt = new DecimalFormat(fract.toString());
        return (getEllipsoid().getName() + " UTM " + lonZone + " " + hemisphere +
                " hemisphere " + offFmt.format(easting) + "m E, " +
                offFmt.format(northing) + "m N");
    }

    /**
     * This method formats the UTM object to a printable string, with easting and northing offsets
     * displayed to the nearest integer number of meters.
     *
     * @return String formatted UTM coordinate
     */
    public String toString() {
        return toString(0);
    }
}
