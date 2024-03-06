/***************************************************************************
 * $Id$
 *
 * (C) Copyright MITRE Corporation 2007-2008
 *
 * The program is provided "as is" without any warranty express or implied,
 * including the warranty of non-infringement and the implied warranties of
 * merchantability and fitness for a particular purpose.  The Copyright
 * owner will not be liable for any damages suffered by you as a result of
 * using the Program.  In no event will the Copyright owner be liable for
 * any special, indirect or consequential damages or lost profits even if
 * the Copyright owner has been advised of the possibility of their
 * occurrence.
 *
 ***************************************************************************/
package org.opensextant.geodesy;

import java.io.Serializable;
import java.text.DecimalFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MGRS class represents a coordinate on the surface of the earth, encoded in the
 * Military Grid Reference System (MGRS).  MGRS is a notation for locating points on a
 * square grid overlayed on maps. The system uses Universal Transverse Mercator (UTM)
 * to project points between -80&deg; and +84&deg; latitude, in longitudinal zones that are
 * nominally 6&deg; wide (with a few exceptions). For the polar regions, a different
 * grid system is overlayed on a Polar Stereographic projection, defining what is called
 * Universal Polar Stereographic (UPS) coding. See web resources for MGRS, UTM, and UPS
 * for more information, or consult the source code for this package. This class provides
 * a way to create MGRS coordinates, validate them, and map them to precise Geodetic2DPoint
 * coordinate equivalents (accuracy to nearest meter grid lines). MGRS notation can
 * be abbreviated to represent larger grid cells, whose size is 1m, 10m, 100m, 1000m, or
 * 10000m on each side. If an MGRS object is created from String notation, the precision
 * (grid cell size) is determined by the number of digits provided for the easting and
 * northing values. In this case, the toGeodetic method will return a point that is at the
 * center of the appropriately sized cell, and the Geodetic2DBounds will be the cell itself and
 * can therefore be used to get particular corner points if needed. If the MGRS object is
 * constructed from a Geodetic2DPoint object, the toGeodetic method will return that same
 * point, and the Geodetic2DBounds will be a 1 meter square cell aligned to the MGRS grid,
 * containing that point. The toString method with specified precision can be used to
 * decrease the precision to larger MGRS cell sizes if needed.
 *
 * @author Paul Silvey
 */
public class MGRS implements GeoPoint, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(MGRS.class);

    private static final int ONEHT = 100000;
    private static final int TWOMIL = 2000000;

    private static final Ellipsoid WGS_84 = Ellipsoid.getInstance("WGS 84");
    private static final Ellipsoid CLARKE_1866 = Ellipsoid.getInstance("Clarke 1866");
    private static final Ellipsoid CLARKE_1880 = Ellipsoid.getInstance("Clarke 1880");
    private static final Ellipsoid BESSEL_1841 = Ellipsoid.getInstance("Bessel 1841");
    private static final Ellipsoid BESSEL_1841_NAMIBIA = Ellipsoid.getInstance("Bessel Namibia");

    private static final Latitude NORTH_POLE = new Latitude(+90.0, Angle.DEGREES);
    private static final Latitude SOUTH_POLE = new Latitude(-90.0, Angle.DEGREES);
    private static final Longitude PRIME_MERIDIAN = new Longitude(0.0, Angle.DEGREES);

    // UPS MGRS Grid identifiers for 100,000 x 100,000 meter grids
    // Each index position (minus half the array length) represents 100,000 meters from pole
    // in the direction indicated by the numeric sign.  These are just big square arrays,
    // centered at each pole.  1st array is x (E-W direction), 2nd is y (N-S)
    //
    // MGRS Square letters for North Polar Region
    private static final char[][] UPS_NorthGrid = {
            {'R', 'S', 'T', 'U', 'X', 'Y', 'Z', 'A', 'B', 'C', 'F', 'G', 'H', 'J'},
            {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P'}
    };
    // MGRS Square letters for South Polar Region
    private static final char[][] UPS_SouthGrid = {
            {'J', 'K', 'L', 'P', 'Q', 'R', 'S', 'T', 'U', 'X', 'Y', 'Z',
                    'A', 'B', 'C', 'F', 'G', 'H', 'J', 'K', 'L', 'P', 'Q', 'R'},
            {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
                    'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'}
    };

    // This helper method returns the MGRS square identifier character for easting direction
    // (called xSquare in this software), given the latBand and upsEasting.  It assumes
    // upsEasting is always positive (i.e. has been false adjusted by adding UPS.FALSE_EASTING
    // value of 2,000,000 to the projected signed offset from the pole.  This is the way it
    // comes back from the UPS class methods.
    private static char xSquare4UPS(char latBand, int upsEasting) {
        char[] eastingGrid = (latBand >= 'Y') ? UPS_NorthGrid[0] : UPS_SouthGrid[0];
        int j = (int) Math.floor((upsEasting - UPS.FALSE_EASTING) / (double) ONEHT);
        int k = eastingGrid.length / 2;
        return eastingGrid[j + k];
    }

    // This helper method returns the MGRS square identifier character for northing direction
    // (called ySquare in this software), given the hemisphere and upsNorthing.  It assumes
    // upsNorthing is always positive (i.e. has been false adjusted by adding UPS.FALSE_NORTHING
    // value of 2,000,000 to the projected signed offset from the pole.  This is the way it
    // comes back from the UPS class methods.
    private static char ySquare4UPS(char latBand, int upsNorthing) {
        char[] northingGrid = (latBand >= 'Y') ? UPS_NorthGrid[1] : UPS_SouthGrid[1];
        int j = (int) Math.floor((upsNorthing - UPS.FALSE_NORTHING) / (double) ONEHT);
        int k = northingGrid.length / 2;
        return northingGrid[j + k];
    }

    // UTM MGRS Grid identifiers for 100,000 x 100,000 meter grids
    //
    // MGRS Square letters for eastings (xSquare identifiers):
    // The first index is determined by (lonZone % 3), where 0 is for MRGS sets 3 and 6,
    // 1 is for MGRS sets 1 and 4, and 2 is for MGRS sets 2 and 5.  Each second index position
    // plus 1, when multiplied by 100,000, gives the UTM false easting for the appropriate lonZone
    // set grid. If you subtract the UTM.FALSE_EASTING value of 500,000 from that number, you
    // get the signed easting from the lonZone's central meridian.  In other words, the first
    // letter of each set represents a 100,000 meter false easting reference point, increasing by
    // 100,000 for each letter and ending each set at 800,000.
    //
    private static final char[][] UTM_EastingGrid = {
            {'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'},       // Set 3 and 6
            {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'},       // Set 1 and 4
            {'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R'}        // Set 2 and 5
    };

    // This helper method returns the MGRS square identifier character for easting direction
    // (called xSquare in this software), given the lonZone and utmEasting.  It assumes utmEasting
    // is always positive (i.e. has been false adjusted by adding UTM.FALSE_EASTING value of 500,000
    // to the projected signed offset from the zone's central meridian.  This is the way it comes
    // back from the UTM class methods.
    private static char xSquare4UTM(int lonZone, int utmEasting) {
        char[] eastingGrid = UTM_EastingGrid[(lonZone % 3)];
        return eastingGrid[(utmEasting - ONEHT) / ONEHT];
    }

    // MGRS Square letters for northings (ySquare identifiers):
    // The northing grid follows the same repeating pattern of 20 letters for all cases, but the
    // index position in the sequence corresponding to the Equator changes depending on whether
    // the lonZone is even or odd, and also depending on whether the Ellipsoid is for older
    // commonly used datums or not.  For example, the North American Datum 83 (NAD83), which is based
    // on the GRS 1980 ellipsoid model, is considered the "new" MGRS grid, while the NAD27 datum based
    // on the Clarke 1866 ellipsoid model is an example of the "old" MGRS grid starting point.
    private static final char[] UTM_NorthingGrid = {
            'A', 'B', 'C', 'D', 'E',    // Odd lonZones using new datums place 'A' at Equator
            'F', 'G', 'H', 'J', 'K',    // Even lonZones using new datums place 'F' at Equator
            'L', 'M', 'N', 'P', 'Q',    // Odd lonZones using old datums place 'L' at Equator
            'R', 'S', 'T', 'U', 'V'     // Even lonZones using old datums place 'R' at Equator
    };

    // This helper method returns the UTM_NorthingGrid index at the Equator for this ellipsoid and lonZone
    private static int equatorIndex(Ellipsoid ellip, int lonZone) {
        // Find the equator starting index
        boolean newDatum = ((ellip == WGS_84) ||
                ((ellip != CLARKE_1866) && (ellip != CLARKE_1880) &&
                        (ellip != BESSEL_1841) && (ellip != BESSEL_1841_NAMIBIA)));
        boolean oddLZone = ((lonZone % 2) != 0);
        return (newDatum ? (oddLZone ? 0 : 5) : (oddLZone ? 10 : 15));
    }

    // This helper method returns the MGRS square identifier character for northing direction
    // (called ySquare in this software), given the ellipsoid, lonZone, and utmNorthing.
    private static char ySquare4UTM(Ellipsoid ellip, int lonZone, int utmNorthing) {
        return UTM_NorthingGrid[(equatorIndex(ellip, lonZone) + (utmNorthing / ONEHT)) % 20];
    }

    // This helper method is used to determine if a latitude is sufficiently close to a Pole to
    // justify using UPS projections.  It returns true if UTM is appropriate and false for UPS.
    private static boolean utmCoord(Latitude lat) {
        double latDeg = lat.inDegrees();
        return ((UTM.MIN_SOUTH_LATDEG <= latDeg) && (latDeg < UTM.MAX_NORTH_LATDEG));
    }

    // MGRS Object instance variables
    private Ellipsoid ellipsoid = WGS_84;

    // MGRS parsed string components
    private int lonZone;             // 0 means this is a UPS coordinate, 1..60 means UTM coordinate
    private char latBand;
    private char xSquare;
    private char ySquare;
    private int easting;
    private int northing;
    private int precision;           // one of {100000, 10000, 1000, 100, 10, 1} cell side in meters

    private Geodetic2DPoint pointInCell;    // Initial precise point or center of cell
    private Geodetic2DPoint llCorner;       // Lower-Left corner of most precise containing cell
    private Geodetic2DPoint urCorner;       // Upper-Right corner of most precise containing cell
    private Geodetic2DBounds bbox;          // Bounding box of MGRS cell (contains precise coordinate)

    private static final DecimalFormat FMT = new DecimalFormat("00000"); // Instance Formatter for eastings & northings

    // Initialize this MGRS object from String notation
    // If argument 'strict' is true, exception will be thrown for non optimal projection encodings

	/**
	 * @throws IllegalArgumentException if MGRS value is invalid
	 */
    private void initFromString(final CharSequence mgrs, boolean strict)
            throws IllegalArgumentException {
        if (mgrs == null) {
            throw new IllegalArgumentException("null value for MGRS String is invalid");
        }

        // Normalize the string by removing separators and converting case
        // Remove embedded whitespace, non-breaking spaces, slashes, dashes, null characters, and convert to uppercase
        final StringBuilder mgrsBuf = new StringBuilder(mgrs.length());
        for (int i = 0; i < mgrs.length(); i++) {
            final char c = mgrs.charAt(i);
            if ((!Character.isWhitespace(c)) && (!Character.isSpaceChar(c)) && (c != '-') && (c != '/') && (((int) c) != 0))
                mgrsBuf.append(Character.toUpperCase(c));
        }
        if (mgrsBuf.length() == 0) {
            throw new IllegalArgumentException("empty value for MGRS String is invalid");
        }

        // Parse leading digits as UTM lon zone (1 to 60), if present
        int i = 0;

        while (Character.isDigit(mgrsBuf.charAt(i))) {
            i++;
            if (i >= mgrsBuf.length()) {
                throw new IllegalArgumentException("MGRS String parse error, string was entirely numeric: " + mgrsBuf);
            }
        }

        if ((1 <= i) && (i <= 2)) {
            lonZone = Integer.parseInt(mgrsBuf.substring(0, i));
            UTM.validateLonZone(lonZone);
        } else if (i == 0) {
            lonZone = 0; // UPS parameters
        } else {
            throw new IllegalArgumentException("MGRS String parse error, " + i + " digit number '" +
                    mgrsBuf.substring(0, i) + "' is too large for UTM longitudinal zone");
        }
        // UTM coordinates are signaled by the presence of a longitudinal zone number
        final boolean utmCoord = (lonZone > 0);

        // Parse next letter as lat band, validate latBand alone & with lonZone, if present
        if (i >= mgrsBuf.length()) {
            // NOTE: this should never occur with pre-tests above
            throw new IllegalArgumentException("MGRS String parse error, " +
                    "expecting letter for UTM or UPS latitudinal band, found end of string");
        } else {
            latBand = mgrsBuf.charAt(i++);
            if (utmCoord) UTM.validateZoneAndBand(lonZone, latBand);
            else UPS.validatePolarZone(latBand);
        }

        // Now parse the MGRS square's x and y identifiers, which we require for both UTM and UPS
        // Note however, that this could be modified to accept UTM cells without MGRS square ids.
        if (mgrsBuf.length() - i < 2) {
            // MGRS square needed for poles, and we also choose to require it for UTM coordinates
            throw new IllegalArgumentException("MGRS String parse error," +
                    " expecting 2 alpha characters for MGRS square, found only one, or end of string: " +
                    mgrsBuf.subSequence(i, mgrsBuf.length()));
        } else {
            // Set instance variables based on MGRS square identifiers, and validate syntax
            xSquare = mgrsBuf.charAt(i++);
            if (!Character.isLetter(xSquare)) {
                throw new IllegalArgumentException("xSquare character was not a letter: " + xSquare);
            }
            ySquare = mgrsBuf.charAt(i++);
            if (!Character.isLetter(ySquare)) {
                throw new IllegalArgumentException("ySquare character was not a letter: " + ySquare);
            }
        }

        // Finally, if present, parse next string of digits as easting & northing; compute precision
        easting = 0;
        northing = 0;
        precision = ONEHT;
        final String en = mgrsBuf.substring(i);
        int n = en.length();
        if (n > 0) {
            if (n > 10) {
                throw new IllegalArgumentException("Length of easting/northing values exceeded 10: " + n + ": " + en);
            } else if ((n % 2) != 0) {
                throw new IllegalArgumentException("Length of easting/northing values was odd: " + n + ": " + en);
            }
            int k = n / 2;
            precision = (int) Math.pow(10.0, 5.0 - k);
            easting = Integer.parseInt(en.substring(0, k)) * precision;
            northing = Integer.parseInt(en.substring(k)) * precision;
        }

        // Now, convert UTM or UPS parameters into a Geodetic2DPoint for the Southwest corner point
        if (utmCoord) {
            // Convert the MGRS parameters for a UTM projection (longitudinal zone, hemisphere,
            // easting and northing) to a geodetic coordinate for the southwestern corner of the MGRS square.
            char hemisphere = UTM.getHemisphere(latBand);

            // Compute utmEasting and validate
            int utmEasting = ONEHT + easting;
            boolean found = false;
            for (char c : UTM_EastingGrid[(lonZone % 3)]) {
                found = (c == xSquare);
                if (found) break;
                else utmEasting += ONEHT;
            }
            if (!found) throw new IllegalArgumentException("Invalid MGRS easting square identifier '" +
                    xSquare + "' for longitudinal zone " + lonZone);

            // Compute utmNorthing and validate
            int utmNorthing = northing;
            int y0 = equatorIndex(ellipsoid, lonZone);
            int yi = y0;
            while (UTM_NorthingGrid[yi] != ySquare) {
                utmNorthing += ONEHT;
                yi = (yi + 1) % 20;
                if (yi == y0) {
                    // Error if we wrap around the sequence without finding the ySquare character
                    throw new IllegalArgumentException("Invalid MGRS northing square identifier '" + ySquare +
                            "' for longitudinal zone " + lonZone);
                }
            }
            // Adjust the northing to be at least beyond the min Northing for the latBand
            int minNorthing = UTM.minNorthing(latBand);
            while (utmNorthing < minNorthing) utmNorthing += TWOMIL;

            // Validate that the utmNorthing within range for the lonZone, latBand pair
            if (utmNorthing >= UTM.maxNorthing(lonZone, latBand)) {
                throw new IllegalArgumentException("MGRS northing out of range for square identifier '" + ySquare +
                        "' in longitudinal zone " + lonZone);
            }

            // Now, un-project from the UTM parameters back to geodetic coordinates
            UTM llUTM = new UTM(lonZone, hemisphere, utmEasting, utmNorthing);
            llCorner = llUTM.getGeodetic();
            double delta = precision / 2.0;
            UTM cpUTM = new UTM(lonZone, hemisphere, utmEasting + delta, utmNorthing + delta);
            pointInCell = cpUTM.getGeodetic();
            UTM urUTM = new UTM(lonZone, hemisphere, utmEasting + precision, utmNorthing + precision);
            urCorner = urUTM.getGeodetic();

            // Validate that the easting's longitude is within the lonZone specified
            double lonDeg = pointInCell.getLongitude().inDegrees();
            double minLonDeg = UTM.minLonDegrees(lonZone, latBand);
            double maxLonDeg = UTM.maxLonDegrees(lonZone, latBand);
            if ((lonDeg < minLonDeg) || (maxLonDeg <= lonDeg)) {
                // Allow if we're within about a meter of one of the boundaries
                Latitude lat = pointInCell.getLatitude();
                Geodetic2DPoint pMin = new Geodetic2DPoint(new Longitude(minLonDeg, Angle.DEGREES), lat);
                if (ellipsoid.orthodromicDistance(pMin, pointInCell) > 1.0) {
                    Geodetic2DPoint pMax = new Geodetic2DPoint(new Longitude(maxLonDeg, Angle.DEGREES), lat);
                    if (ellipsoid.orthodromicDistance(pMax, pointInCell) > 1.0) {
                        String msg = "MGRS easting out of range for square identifier '" +
                                xSquare + "' in longitudinal zone " + lonZone;
                        if (strict) throw new IllegalArgumentException(msg);
                        else log.debug(msg);
                    }
                }
            }
        } else {
            // Convert the MGRS parameters for a UPS projection (polar latitude band, easting and
            // northing) to a geodetic coordinate for the southwestern corner of the MGRS square
            char hemisphere;
            int gridEasting = 0;        /* Easting for 100,000 meter grid square      */
            int gridNorthing = 0;       /* Northing for 100,000 meter grid square     */

            char[][] gridLetters;

            // UPS latBand already validated ("Y' or 'Z' for North Pole, 'A' or 'B' for South)
            if (latBand >= 'Y') {
                // North Polar Region
                hemisphere = 'N';
                gridLetters = UPS_NorthGrid;
            } else {
                // South Polar Region
                hemisphere = 'S';
                gridLetters = UPS_SouthGrid;
            }
            // See if xSquare is valid and compute gridEasting
            // Use latBand to restrict our search to the appropriate half of the letter table
            boolean west = ((latBand == 'A') || (latBand == 'Y'));
            n = gridLetters[0].length / 2;
            int x0 = west ? 0 : n;
            int xn = x0 + n;
            boolean found = false;
            for (i = x0; i < xn; i++) {
                if (found = (xSquare == gridLetters[0][i])) {
                    gridEasting = ONEHT * (i - n);
                    break;
                }
            }
            if (!found) {
                // One legal case is S Polar region along lon 180 deg exactly, where xSquare
                // should be an 'A' even though it is not found by the above search method
                if (west && (xSquare == 'A') && (easting == 0)) {
                    gridEasting = 0;
                } else {
                    throw new IllegalArgumentException("First letter of MGRS square identifier ('" +
                            xSquare + "') is invalid for UPS " + hemisphere + " Polar Region");
                }
            }
            // See if ySquare is valid and compute gridNorthing
            n = gridLetters[1].length;
            found = false;
            for (i = 0; i < n; i++) {
                if (found = (ySquare == gridLetters[1][i])) {
                    gridNorthing = ONEHT * (i - (n / 2));
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Second letter of MGRS square identifier ('" +
                        ySquare + "') is invalid for UPS " + hemisphere + " Polar Region");
            }

            double upsEasting = UPS.FALSE_EASTING + gridEasting + easting;
            double upsNorthing = UPS.FALSE_NORTHING + gridNorthing + northing;
            UPS llUPS = new UPS(hemisphere, upsEasting, upsNorthing);
            llCorner = llUPS.getGeodetic();
            double delta = precision / 2.0;
            UPS cpUPS = new UPS(hemisphere, upsEasting + delta, upsNorthing + delta);
            pointInCell = cpUPS.getGeodetic();
            UPS urUPS = new UPS(hemisphere, upsEasting + precision, upsNorthing + precision);
            urCorner = urUPS.getGeodetic();

            // Validate that the latitude is within the polar regions
            if (utmCoord(pointInCell.getLatitude())) {
                String msg = "MGRS coordinate corresponds to a UPS " +
                        "point outside a polar region";
                if (strict) throw new IllegalArgumentException(msg);
                else log.debug(msg);
            }
        }
        bbox = new Geodetic2DBounds(llCorner, urCorner);
    }

    /*
     * This method is used to initialize an MGRS coordiante using Geodetic2DPoint lon and lat values
     */
    private void initFromGeodetic(Longitude lon, Latitude lat) {
        pointInCell = new Geodetic2DPoint(lon, lat);
        if (utmCoord(lat)) {
            // Converts the MGRS geodetic coordinate to its nearest MGRS grid cell,
            // using a UTM projection to derive lonZone, hemisphere, easting and northing (& latBand)
            UTM cpUTM = (ellipsoid != WGS_84) ? new UTM(ellipsoid, pointInCell) : new UTM(pointInCell);
            lonZone = cpUTM.getLonZone();
            latBand = cpUTM.getLatBand();
            char hemisphere = UTM.getHemisphere(latBand);

            // Determine xSquare identifier and adjust UTM easting to be relative to be MGRS square
            int utmEasting = (int) Math.floor(cpUTM.getEasting());
            xSquare = xSquare4UTM(lonZone, utmEasting);
            easting = utmEasting - ((utmEasting / ONEHT) * ONEHT);

            // Determine ySquare identifier and adjust UTM northing to be relative to be MGRS square
            int utmNorthing = (int) Math.floor(cpUTM.getNorthing());
            ySquare = ySquare4UTM(ellipsoid, lonZone, utmNorthing);
            northing = utmNorthing - ((utmNorthing / ONEHT) * ONEHT);

            // Create the geodetic points for the smallest containing cell (1 meter per side)
            precision = 1;
            UTM llUTM = new UTM(lonZone, hemisphere, utmEasting, utmNorthing);
            llCorner = llUTM.getGeodetic();
            UTM urUTM = new UTM(lonZone, hemisphere, utmEasting + precision, utmNorthing + precision);
            urCorner = urUTM.getGeodetic();
        } else {
            // If sufficiently close to a pole, longitude should be zero (by convention)
            if (((Math.abs(lat.difference(NORTH_POLE).inDegrees()) < 1e-8) ||
                    (Math.abs(lat.difference(SOUTH_POLE).inDegrees()) < 1e-8)) &&
                    (Math.abs(lon.difference(PRIME_MERIDIAN).inDegrees()) > 1e-8))
                throw new IllegalArgumentException("Longitude should be zero at a Pole, lon: " + lon.inDegrees());

            // Converts the MGRS geodetic coordinate to its nearest MGRS grid cell,
            // using a UPS projection to derive latBand, easting and northing
            UPS cpUPS = (ellipsoid != WGS_84) ? new UPS(ellipsoid, pointInCell) : new UPS(pointInCell);
            lonZone = 0;
            latBand = cpUPS.getPolarZone();
            char hemisphere = cpUPS.getHemisphere();

            // Determine xSquare identifier and adjust UPS zone easting to be relative to MGRS square
            int upsEasting = (int) Math.floor(cpUPS.getEasting());
            xSquare = xSquare4UPS(latBand, upsEasting);
            easting = upsEasting - ((upsEasting / ONEHT) * ONEHT);

            // Determine ySquare identifier and adjust UTM northing to be relative to be MGRS square
            int upsNorthing = (int) Math.floor(cpUPS.getNorthing());
            ySquare = ySquare4UPS(latBand, upsNorthing);
            northing = upsNorthing - ((upsNorthing / ONEHT) * ONEHT);

            // Create the geodetic points for the smallest containing cell (1 meter per side)
            precision = 1;
            UPS llUPS = new UPS(hemisphere, upsEasting, upsNorthing);
            llCorner = llUPS.getGeodetic();
            UPS urUPS = new UPS(hemisphere, upsEasting + precision, upsNorthing + precision);
            urCorner = urUPS.getGeodetic();
        }
        bbox = new Geodetic2DBounds(llCorner, urCorner);
    }

    /**
     * This constructor takes an Ellipsoid object and an MGRS coordinate CharSequence.
     * It uses the default non strict parsing rules, which will result in only a
     * logged warning when the given encoding is not optimal for the
     * projected point (easting out of range or UPS outside of polar region).
     *
     * @param ellip Ellipsoid model of the earth to use in projections
     * @param mgrs  Military Grid Reference System coordinate CharSequence
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Ellipsoid ellip, CharSequence mgrs) {
        ellipsoid = ellip;
        initFromString(mgrs, false);
    }

    /**
     * This constructor takes an Ellipsoid object and an MGRS coordinate string.
     * It uses the default non strict parsing rules, which will result in only a
     * logged warning when the given encoding is not optimal for the
     * projected point (easting out of range or UPS outside of polar region).
     *
     * @param ellip Ellipsoid model of the earth to use in projections
     * @param mgrs  Military Grid Reference System coordinate string
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Ellipsoid ellip, String mgrs) {
        this(ellip, (CharSequence) mgrs);
    }

    /**
     * This constructor takes an Ellipsoid object, an MGRS coordinate string, and
     * a boolean flag indicating whether strict parsing rules should be followed, which
     * can result in an IllegalArgumentException when the given encoding is not optimal
     * for the projected point (easting out of range or UPS outside of polar region).
     *
     * @param ellip  Ellipsoid model of the earth to use in projections
     * @param mgrs   Military Grid Reference System coordinate string
     * @param strict boolean indicating if parsing rules should be strictly enforced
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Ellipsoid ellip, String mgrs, boolean strict)
            throws IllegalArgumentException {
        this(ellip, (CharSequence) mgrs, strict);
    }

    /**
     * This constructor takes an Ellipsoid object, an MGRS coordinate CharSequence, and
     * a boolean flag indicating whether strict parsing rules should be followed, which
     * can result in an IllegalArgumentException when the given encoding is not optimal
     * for the projected point (easting out of range or UPS outside of polar region).
     *
     * @param ellip  Ellipsoid model of the earth to use in projections
     * @param mgrs   Military Grid Reference System coordinate CharSequence
     * @param strict boolean indicating if parsing rules should be strictly enforced
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Ellipsoid ellip, CharSequence mgrs, boolean strict)
            throws IllegalArgumentException {
        ellipsoid = ellip;
        initFromString(mgrs, strict);
    }

    /**
     * This constructor takes an Ellipsoid object and a Geodetic2DPoint object (lon-lat point).
     *
     * @param ellip  Ellipsoid model of the earth to use in projections
     * @param lonLat Geodetic2DPoint coordinate (lon-lat point)
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Ellipsoid ellip, Geodetic2DPoint lonLat) {
        ellipsoid = ellip;
        initFromGeodetic(lonLat.getLongitude(), lonLat.getLatitude());
    }

    /**
     * This constructor takes an Ellipsoid object, Longitude and Latitude object values.
     *
     * @param ellip Ellipsoid model of the earth to use in projections
     * @param lon   Longitude of geodetic coordinate point
     * @param lat   Latitude of geodetic coordinate point
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Ellipsoid ellip, Longitude lon, Latitude lat) {
        ellipsoid = ellip;
        initFromGeodetic(lon, lat);
    }

    /**
     * This constructor takes an MGRS coordinate CharSequence, assuming WGS 84 Ellipsoid.
     * It uses the default non strict parsing rules, which will result in only a
     * logged debug level warning when the given encoding is not optimal for the
     * projected point (easting out of range or UPS outside of polar region).
     *
     * @param mgrs Military Grid Reference System coordinate CharSequence
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(CharSequence mgrs) {
        initFromString(mgrs, false);
    }

    /**
     * This constructor takes an MGRS coordinate string, assuming WGS 84 Ellipsoid.
     * It uses the default non strict parsing rules, which will result in only a
     * logged debug level warning when the given encoding is not optimal for the
     * projected point (easting out of range or UPS outside of polar region).
     *
     * @param mgrs Military Grid Reference System coordinate string
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(String mgrs) {
        this((CharSequence) mgrs);
    }

    /**
     * This constructor takes an MGRS coordinate CharSequence and a boolean flag
     * indicating if strict parsing rules should be followed, which can result in
     * an IllegalArgumentException when given encoding is not optimal for the
     * projected point (easting out of range or UPS outside of polar region).
     * It assumes the WGS84 Ellipsoid model of the Earth.
     *
     * @param mgrs   Military Grid Reference System coordinate string
     * @param strict boolean indicating if parsing rules should be strictly enforced
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(CharSequence mgrs, boolean strict) {
        initFromString(mgrs, strict);
    }

    /**
     * This constructor takes an MGRS coordinate String and a boolean flag
     * indicating if strict parsing rules should be followed, which can result in
     * an IllegalArgumentException when given encoding is not optimal for the
     * projected point (easting out of range or UPS outside of polar region).
     * It assumes the WGS84 Ellipsoid model of the Earth.
     *
     * @param mgrs   Military Grid Reference System coordinate string
     * @param strict boolean indicating if parsing rules should be strictly enforced
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(String mgrs, boolean strict) {
        this((CharSequence) mgrs, strict);
    }

    /**
     * This constructor takes a Geodetic2DPoint object (lon-lat point), assuming WGS 84 Ellipsoid.
     *
     * @param lonLat Geodetic2DPoint coordinate (lon-lat point)
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Geodetic2DPoint lonLat) {
        initFromGeodetic(lonLat.getLongitude(), lonLat.getLatitude());
    }

    /**
     * This constructor takes Longitude and Latitude object values, assuming WGS 84 Ellipsoid.
     *
     * @param lon Longitude of geodetic coordinate point
     * @param lat Latitude of geodetic coordinate point
     * @throws IllegalArgumentException error if parameters are invalid
     */
    public MGRS(Longitude lon, Latitude lat) {
        initFromGeodetic(lon, lat);
    }

    /**
     * This constructor takes a Geodetic2DBounds bounding box and tries to construct the appropriately
     * precise MGRS coordinate that it corresponds to.  Since MGRS cells make up a fixed grid on the
     * surface of the Ellipsoid, not all bounding boxes will properly snap to it.  This method is
     * designed to reverse the mapping from a valid MGRS string to a bounding box, to recover the
     * MGRS string that was used to create the bounding box. It throws a NotAnMGRSBoxException
     * when the fit is not adequate.
     *
     * @param bbox Geodetic2DBBounds bounding box to try to convert back to an MGRS coordinate
     * @throws IllegalArgumentException if there is another problem initializing from the derived string.
     * @throws NotAnMGRSBoxException    if the bounding box doesn't fit to the MGRS grid properly
     */
    public MGRS(Geodetic2DBounds bbox) throws NotAnMGRSBoxException {
        Geodetic2DPoint sw = new Geodetic2DPoint(bbox.getWestLon(), bbox.getSouthLat());
        Geodetic2DPoint ne = new Geodetic2DPoint(bbox.getEastLon(), bbox.getNorthLat());
        Geodetic2DArc d = new Geodetic2DArc(sw, ne);
        long diagonal = Math.round(d.getDistanceInMeters());
        int precision;
        if (diagonal == 1) precision = 5;
        else if (diagonal == 14) precision = 4;
        else if (diagonal == 141) precision = 3;
        else if (diagonal == 1414) precision = 2;
        else if (diagonal == 14142) precision = 1;
        else
            throw new NotAnMGRSBoxException("Bounding box doesn't fit the MGRS grid properly: " + bbox + " diagonal was: " + diagonal);

        MGRS m = new MGRS(sw);
        initFromString(m.toString(precision), true);
    }

    /**
     * This method returns the Geodetic2DPoint coordinate object corresponding to either the
     * SouthWest corner of the MGRS cell (if the MGRS Object was constructed from a MGRS String),
     * or the precise point used to construct the MGRS object (which is contained inside the
     * smallest possible MGRS grid cell).
     *
     * @return Geodetic2DPoint object (lon-lat point) for South West corner of this MGRS coordinate
     */
    public Geodetic2DPoint toGeodetic2DPoint() {
        return pointInCell;
    }

    /**
     * This method abstracts this MGRS object as a general purpose GeoPoint.
     *
     * @param fRef the FrameOfReference in which to interpret this coordinate.
     * @return the equivalent Geodetic3DPoint
     */
    public Geodetic3DPoint toGeodetic3D(FrameOfReference fRef) {
        return fRef.toGeodetic(pointInCell);
    }

    /**
     * This method returns the Geodetic2DBounds for this MGRS cell.
     *
     * @return Geodetic2DBounds for MGRS cell, containing precise geodetic if used for construction
     */
    public Geodetic2DBounds getBoundingBox() {
        return bbox;
    }

    /**
     * Get precision of coordinate which is the cell side in meters.
     * For example, 100,000 meters => 0 digits, ..., 1 meter => 5 digit precision.
     * @return precision One of {100000, 10000, 1000, 100, 10, 1}
     */
    public int getPrecision() {
        return precision;
    }

    /**
     * This method returns a hash code for this MGRS object. It only depends on the
     * MGRS point in cell (which is either the Geodetic2DPoint used to construct this
     * object or one at the center of this MGRS cell when constructed from a String).
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return this.pointInCell.hashCode();
    }

    /**
     * This method is used to test whether two MGRS points are equal in the sense that have
     * the same Geodetic2DPoint coordinate (point in cell).
     *
     * @param that MGRS point to compare against this one.
     * @return true if specified MGRS point is equal in value to this one.
     */
    public boolean equals(MGRS that) {
        return that != null && this.pointInCell.equals(that.pointInCell);
    }

    /**
     * This method is used to test whether two MGRS points are equal in the sense that have
     * the same Geodetic2DPoint coordinate (point in cell).
     *
     * @param that MGRS point to compare against this one.
     * @return true if specified MGRS point is equal in value to this one.
     */
    @Override
    public boolean equals(Object that) {
        return that instanceof MGRS && equals((MGRS) that);
    }

    /**
     * This version of the toString method allows the user to specify the number of digits of
     * precision in the easting and northing components.  Precision values of 0 result in the
     * MGRS square identifiers only (implied 0 values for easting and northing offsets from
     * southwest corner of MGRS square), resulting in 100,000 meter accuracy.  Precision values
     * 1..5 result in that many digits of easting and northing values.  As a result, precision
     * digit values have the following accuracy:
     * <pre>
     * 5 has 1 meter accuracy
     * 4 has 10 meter accuracy
     * 3 has 100 meter accuracy
     * 2 has 1,000 meter accuracy
     * 1 has 10,000 meter accuracy
     * </pre>
     *
     * @param precisionDigits int value from 0 to 5, inclusive
     * @return MGRS coordinate string at the specified precision level
     * @throws IllegalArgumentException - exception if precision is out of range (0..5)
     */
    public String toString(int precisionDigits) {
        if (precisionDigits < 0 || precisionDigits > 5)
            throw new IllegalArgumentException("Precision must be an integer in the range 0..5");

        StringBuilder mgrsStr = new StringBuilder();
        // Include UTM lon Zone if this square is not in a polar region
        if (utmCoord(pointInCell.getLatitude())) mgrsStr.append(lonZone);
        // Include the 3 letter MGRS square identifiers
        mgrsStr.append(latBand);
        mgrsStr.append(xSquare);
        mgrsStr.append(ySquare);
        synchronized(FMT) {
            // Add the correct easting and northing values truncated at the specified precision
            mgrsStr.append(FMT.format(easting), 0, precisionDigits);
            mgrsStr.append(FMT.format(northing), 0, precisionDigits);
        }
        return mgrsStr.toString();
    }

    /**
     * This default toString method returns the MGRS string at its natural precision
     *
     * @return MGRS coordinate string at the most appropriate precision value
     */
    @Override
    public String toString() {
        int precisionDigits = 5;
        if (precision == 100000) precisionDigits = 0;
        else if (precision == 10000) precisionDigits = 1;
        else if (precision == 1000) precisionDigits = 2;
        else if (precision == 100) precisionDigits = 3;
        else if (precision == 10) precisionDigits = 4;
        else if (precision == 1) precisionDigits = 5;
        else log.error("Invalid MGRS precision - shouldn't happen");
        return toString(precisionDigits);
    }
}
