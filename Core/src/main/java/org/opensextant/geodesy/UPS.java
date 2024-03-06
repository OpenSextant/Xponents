/****************************************************************************************
 *  UPS.java
 *
 *  Created: Jan 20, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2007
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
package org.opensextant.geodesy;

import java.io.Serializable;
import java.text.DecimalFormat;

import org.jetbrains.annotations.NotNull;

/**
 * UPS coordinates are based on a family of two Polar Stereographic map projections,
 * one for each pole. The origin of the UPS coordinate system is the pole (north or
 * south), where X=2,000,000m and Y=2,000,000m.
 * <p>
 * The X-axis lies along the meridians 90&deg;;E and 90&deg;;W.
 * Moving from the pole (north or south), X-values (Eastings) increase along
 * the 90&deg;;E meridian.
 * The Y-axis lies along the meridians 0&deg;; and 180&deg;;.
 * Moving from the north pole, Y-values (Northings) increase along the 180&deg;
 * meridian.  Moving from the south pole, Y-values (Northings) increase along
 * the 0&deg; meridian.
 */
public class UPS implements Serializable {
	private static final long serialVersionUID = 1L;
	
	// Static Class Constants
    public static final double MIN_NORTH_LATDEG = 83.5;
    public static final double MAX_SOUTH_LATDEG = -79.5;
    public static final int FALSE_EASTING = 2000000;
    public static final int FALSE_NORTHING = 2000000;

    private static final double MIN_EASTING = 0.0;
    private static final double MAX_EASTING = 4000000.0;
    private static final double MIN_NORTHING = 0.0;
    private static final double MAX_NORTHING = 4000000.0;
    private static final Latitude NORTH_POLAR = new Latitude(+81, 6, 52.3);
    private static final Latitude SOUTH_POLAR = new Latitude(-81, 6, 52.3);

    // Static Class Methods

    /**
     * Validate UPS polar zone to see if it is valid (by itself). Used in place of UTM
     * lat band char.
     *
     * @param polarZone UPS Polar Zone ('A' or 'B' for Southern, 'Y' or 'Z' for Northern)
     * @throws IllegalArgumentException error if UPS Polar Zone is invalid
     */
    public static void validatePolarZone(char polarZone) {
        if ((polarZone != 'A') && (polarZone != 'B') && (polarZone != 'Y') && (polarZone != 'Z'))
            throw new IllegalArgumentException("UPS polar zone '" + polarZone + "' is not valid");
    }

    /*
     * Validate hemisphere character to see if it is valid (by itself).
     *
     * @param hemisphere UPS Hemisphere character ('N' for North, 'S' for South)
     * @throws IllegalArgumentException error if hemisphere character is invalid
     */
    private static void validateHemisphere(char hemisphere) {
        if ((hemisphere != 'N') && (hemisphere != 'S'))
            throw new IllegalArgumentException("Invalid hemisphere '" +
                    hemisphere + "', should be 'N' or 'S'");
    }

    /*
     * Validate easting value to see if it is within allowed positive numerical range.
     *
     * @param easting UPS Easting value in meters
     * @throws IllegalArgumentException error if easting value is out of range
     */
    private static void validateEasting(double easting) {
        if ((easting < MIN_EASTING) || (MAX_EASTING < easting))
            throw new IllegalArgumentException("Easting value '" + easting +
                    "' is outside of valid range (0 to 4,000,000 meters)");
    }

    /*
     * Validate northing value to see if it is within allowed positive numerical range.
     *
     * @param northing UPS Northing value in meters
     * @throws IllegalArgumentException error if northing value is out of range
     */
    private static void validateNorthing(double northing) {
        if ((northing < MIN_NORTHING) || (MAX_NORTHING < northing))
            throw new IllegalArgumentException("Northing value '" + northing +
                    "' is outside of valid range (0 to 4,000,000 meters)");
    }

    /*
     * Validate latitude value to see if it is within allowed numerical range.
     *
     * @param lat - latitude
     * @throws IllegalArgumentException error if latitude value is out of range
     */
    private static void validateLatitude(Latitude lat) {
        double latDeg = lat.inDegrees();
        if ((0.0 <= latDeg) && (latDeg < MIN_NORTH_LATDEG)) {
            throw new IllegalArgumentException("Latitude value '" + latDeg +
                    "' is out of legal range (+83.5 deg to +90 deg) for UPS Northern Hemisphere");
        } else if ((MAX_SOUTH_LATDEG < latDeg) && (latDeg < 0.0)) {
            throw new IllegalArgumentException("Latitude value '" + latDeg +
                    "' is out of legal range (-79.5 deg to -90 deg) for UPS Southern Hemisphere");
        }
    }

    // *************************** End of Static Definitions ********************************

    // Instance Variables
    @NotNull private PolarStereographic ps;  // Polar Stereographic projection object (keeps ellipsoid)
    private char hemisphere;        // Hemisphere char ('N' for Northern, 'S' for Southern)
    private char polarZone;         // East-West zone identifier character (like UTM lat band)
    private double easting;         // positive meters east of false adjusted pole
    private double northing;        // positive meters north of false adjusted pole

    @NotNull private Geodetic2DPoint lonLat; // Longitude & Latitude coordinates for this UPS position

    // private initializer method for geodetic constructors
    private void initGeodetic(Ellipsoid ellip, Longitude lon, Latitude lat)
            throws IllegalArgumentException {
        hemisphere = lat.getHemisphere();
        Latitude latOfTrueScale = (hemisphere == 'N') ? NORTH_POLAR : SOUTH_POLAR;
        ps = new PolarStereographic(latOfTrueScale);
        if (ellip != null) ps.setEllipsoid(ellip);
        lonLat = new Geodetic2DPoint(lon, lat);
        fromGeodetic();
    }

    // private initializer method for UPS parameter constructors
    private void initUPS(Ellipsoid ellip, char hemisphere, double easting, double northing)
            throws IllegalArgumentException {
        Latitude latOfTrueScale = (hemisphere == 'N') ? NORTH_POLAR : SOUTH_POLAR;
        ps = new PolarStereographic(latOfTrueScale);
        if (ellip != null) ps.setEllipsoid(ellip);
        this.hemisphere = hemisphere;
        this.easting = easting;
        this.northing = northing;
        // Determine polar zone and cache value in instance variable
        if (hemisphere == 'S') polarZone = (easting < 0.0) ? 'A' : 'B';
        else polarZone = (easting < 0.0) ? 'Y' : 'Z';
        toGeodetic();
    }

    /**
     * This constructor takes an ellipsoid and Geodetic2DPoint object.  If WGS 84 is desired,
     * the caller should instead use one of the constructors without the ellipsoid parameter,
     * since WGS 84 is the default. The specified Geodetic2DPoint object (lon-lat point) is
     * converted to its UPS equivalent.
     *
     * @param ellip  Ellipsoid data model for earth
     * @param lonLat Geodetic2DPoint coordinates (lon-lat) to be converted to UPS
     * @throws IllegalArgumentException error if arguments are invalid or out of legal range
     */
    public UPS(Ellipsoid ellip, Geodetic2DPoint lonLat) {
        initGeodetic(ellip, lonLat.getLongitude(), lonLat.getLatitude());
    }

    /**
     * This constructor takes an ellipsoid, Longitude and Latitude Objects.
     * If WGS 84 is desired, the caller should instead use one of the constructors without
     * the ellipsoid parameter, since WGS 84 is the default. The specified longitude and
     * latitude object values are converted to their UPS equivalent.
     *
     * @param ellip Ellipsoid data model for earth
     * @param lon   Longitude of point to convert to UPS
     * @param lat   Latitude of point to convert to UPS
     * @throws IllegalArgumentException error if arguments are invalid or out of legal range
     */
    public UPS(Ellipsoid ellip, Longitude lon, Latitude lat) {
        initGeodetic(ellip, lon, lat);
    }

    /**
     * This constructor takes an ellipsoid and the UPS parameters for hemisphere, easting, and
     * northing.  If WGS 84 is desired, the caller should instead use one of the constructors
     * without the ellipsoid parameter, since WGS 84 is the default.  The UPS parameters are
     * converted to their equivalent geodetic (lon-lat) position, along with the correct polar
     * zone.
     *
     * @param ellip      Ellipsoid data model for earth
     * @param hemisphere character 'N' for Northern or 'S' for Southern hemisphere
     * @param easting    positive meters east of false adjusted pole
     * @param northing   positive meters north of false adjusted pole
     * @throws IllegalArgumentException error if arguments are invalid or out of legal range
     */
    public UPS(Ellipsoid ellip, char hemisphere, double easting, double northing)
            throws IllegalArgumentException {
        initUPS(ellip, hemisphere, easting, northing);
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for PolarStereographic).
     * The specified Geodetic2DPoint object (lon-lat point) is converted to its UPS equivalent.
     *
     * @param lonLat Geodetic2DPoint coordinates (lon-lat) to be converted to UPS
     * @throws IllegalArgumentException error if arguments are invalid or out of legal range
     */
    public UPS(Geodetic2DPoint lonLat) {
        initGeodetic(null, lonLat.getLongitude(), lonLat.getLatitude());
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for PolarStereographic).
     * The specified longitude and latitude object values are converted to their UPS equivalent.
     *
     * @param lon Longitude of point to convert to UPS
     * @param lat Latitude of point to convert to UPS
     * @throws IllegalArgumentException error if latitude is not close enough to a pole
     */
    public UPS(Longitude lon, Latitude lat) {
        initGeodetic(null, lon, lat);
    }

    /**
     * This constructor assumes the WGS 84 Ellipsoid (default for PolarStereographic).
     * The UPS parameters are converted to their equivalent geodetic (lon-lat) position,
     * along with the correct polar zone.
     *
     * @param hemisphere character 'N' for Northern or 'S' for Southern hemisphere
     * @param easting    positive meters east of false adjusted central meridian for lonZone
     * @param northing   positive meters north of false adjusted origin (equator for 'N' hemisphere)
     * @throws IllegalArgumentException error if arguments are invalid or out of legal range
     */
    public UPS(char hemisphere, double easting, double northing) {
        initUPS(null, hemisphere, easting, northing);
    }

    /*
     * This method converts UPS projection (hemisphere, easting and northing)
     * coordinates to geodetic (longitude and latitude) coordinates, based on the
     * current ellipsoid model.
     *
     * @throws IllegalArgumentException input parameter error(s)
     */
    private void toGeodetic() {
        // Validate input parameters
        validateHemisphere(hemisphere);
        validateEasting(easting);
        validateNorthing(northing);

        double e = easting - FALSE_EASTING;
        double n = northing - FALSE_NORTHING;

        // Un-project to geodetic coordinates
        lonLat = ps.toGeodetic(e, n);
    }

    /*
     * This method converts from Geodetic2DPoint coordinates (lon-lat) to UPS parameters.
     */
    private void fromGeodetic() {
        Longitude lon = lonLat.getLongitude();
        Latitude lat = lonLat.getLatitude();

        // Validate latitude
        validateLatitude(lat);
        hemisphere = lat.getHemisphere();

        Topocentric2DPoint en = ps.toPolarStereographic(lon, lat);
        easting = en.getEasting() + UPS.FALSE_EASTING;
        northing = en.getNorthing() + UPS.FALSE_NORTHING;

        // Determine polar zone and cache value in instance variable
        if (hemisphere == 'S') polarZone = (easting < UPS.FALSE_EASTING) ? 'A' : 'B';
        else polarZone = (easting < UPS.FALSE_EASTING) ? 'Y' : 'Z';
    }

    /**
     * This accessor method is used to get the currently set Ellipsoid earth model for
     * making projections during conversion to and from geodetic coordinates.
     *
     * @return the currently set Ellipsoid earth model being used by this UPS coordinate
     */
    public Ellipsoid getEllipsoid() {
        return ps.getEllipsoid();
    }

    /**
     * This accessor method returns the geodetic (lon-lat) point for this UPS coordinate.
     *
     * @return Geodetic2DPoint object (lon-lat) coordinates
     */
	@NotNull
    public Geodetic2DPoint getGeodetic() {
        return lonLat;
    }

    /**
     * This accessor method returns the Longitude part of the geodetic coordinates for
     * this UPS coordinate.
     *
     * @return Longitude coordinate of the geodetic (lon-lat) point for this UPS position
     */
	@NotNull
    public Longitude getLongitude() {
        return lonLat.getLongitude();
    }

    /**
     * This accessor method returns the Latitude part of the geodetic coordinates for
     * this UPS coordinate.
     *
     * @return Latitude coordinate of the geodetic (lon-lat) point for this UPS position
     */
	@NotNull
    public Latitude getLatitude() {
        return lonLat.getLatitude();
    }

    /**
     * This accessor method returns the easting part of this UPS coordinate.
     *
     * @return number of meters east of polar false easting
     */
    public double getEasting() {
        return easting;
    }

    /**
     * This accessor method returns the northing part of this UPS coordinate.
     *
     * @return number of meters north of polar false easting
     */
    public double getNorthing() {
        return northing;
    }

    /**
     * This accessor method returns the hemisphere character for this UPS coordinate ('N' or 'S').
     *
     * @return hemisphere character ('N' or 'S')
     */
    public char getHemisphere() {
        return hemisphere;
    }

    /**
     * This accessor method returns the polar zone character for this UPS coordinate
     * ('A' or 'B' for South Pole, 'Y' or 'Z' for North Pole).
     *
     * @return polar zone character ('A' or 'B' for South Pole, 'Y' or 'Z' for North Pole)
     */
    public char getPolarZone() {
        return polarZone;
    }

	@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UPS ups = (UPS) o;
		if (hemisphere != ups.hemisphere) return false;
        if (Double.compare(ups.easting, easting) != 0) return false;
        if (Double.compare(ups.northing, northing) != 0) return false;
        return lonLat.equals(ups.lonLat);
    }

    @Override
    public int hashCode() {
        // int result;
        long temp;
		int result = hemisphere;
        temp = easting != 0.0d ? Double.doubleToLongBits(easting) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = northing != 0.0d ? Double.doubleToLongBits(northing) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + lonLat.hashCode();
        return result;
    }

    /**
     * This method converts the UPS object to a printable string, using the specified number of
     * fractional digits for the easting and northing offsets.
     *
     * @param fractDig number of digits past the decimal point for easting and northing
     * @return String formatted UPS coordinate
     */
    public String toString(int fractDig) {
        String fract = (fractDig > 0) ? "." : "";
        while (fractDig-- > 0) fract += "0";
        DecimalFormat offFmt = new DecimalFormat("0" + fract);
        return (getEllipsoid().getName() + " UPS " + polarZone + " " +
                offFmt.format(easting) + "m E, " + offFmt.format(northing) + "m N");
    }

    /**
     * This method converts the UPS object to a printable string, with easting and northing offsets
     * given in integer meters.
     *
     * @return String formatted UPS coordinate
     */
    public String toString() {
        return toString(0);
    }
}
