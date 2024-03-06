/****************************************************************************************
 *  Geodetic3DPoint.java
 *
 *  Created: Mar 12, 2007
 *
 *  @author Paul Silvey, based on work by Curtis Brown, Debbie Pierce, and Jason Mathews
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
package org.opensextant.geodesy;

import java.text.DecimalFormat;

import org.jetbrains.annotations.NotNull;

/**
 * The Geodetic3DPoint class extends the simple Geodetic2DPoint class by adding an explicit
 * elevation value, in meters. To be correctly interpreted, one needs a particular Ellipsoid
 * model of the earth (see FrameOfReference class for ways to define and use this in coordinate
 * conversions). This class is simple in the interest of efficient storage and processing.
 * A Geodetic3DPoint can be converted to a GeocentricPoint and back with loss of precision
 * to within a tenth of an arc second, for any points on the surface of the earth and any
 * ocean depths, as well as up to altitudes that include those achievable by "air-breather"
 * aircraft (tested up to 26km).
 */
public class Geodetic3DPoint extends Geodetic2DPoint {

	private static final long serialVersionUID = 1L;

    private double elevation;

    /**
     * This constructor takes a Longitude, Latitude, and an elevation value in meters.
     * The default elevation reference point (vertical datum) is the tangent surface plane
     * touching the WGS-84 Ellipsoid at the given longitude and latitude.  The elevation
     * distance is measured along the normal vector through the surface plane. Note that for
     * Geodetic representations on an Ellipsoid earth model, this vector does not generally
     * pass through the center of the Ellipsoid.
     *
     * @param lon       Longitude of this Geodetic3DPoint.
     * @param lat       Latitude of this Geodetic3DPoint.
     * @param elevation elevation in meters from the assumed reference point
	 * @throws NullPointerException if lon or lat are null
     */
    public Geodetic3DPoint(Longitude lon, Latitude lat, double elevation) {
        super(lon, lat);
        this.elevation = elevation;
    }

    /**
     * Default constructor makes a geodetic point at the central meridian on the equator (0, 0, 0).
     */
    public Geodetic3DPoint() {
        super();
    }

    /**
     * This accessor method is used to get the elevation (assumed in meters).
     *
     * @return elevation of this Geodetic3DPoint point (in meters).
     */
    public double getElevation() {
        return elevation;
    }

    /**
     * This settor method is used to update the elevation value for this Geodetic3DPoint.
     *
     * @param elevation elevation of this Geodetic3DPoint point (in meters).
     */
    public void setElevation(double elevation) {
        this.elevation = elevation;
    }

    // using 1e-3 meters for elevation equality gives a precision up to 1 millimeter
    private static final double DELTA = 1e-3;

    /**
     * This method returns a hash code for this Geodetic3DPoint object. The
     * result is the exclusive OR of the component values to maintain the general
     * contract for the hashCode method, which states that equal objects must have
     * equal hash codes.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        // Note we're using approximate equals vs absolute equals on floating point numbers
        // so we ignore beyond ~3 decimal places in computing the hashCode, otherwise
        // we break the equals-hashCode contract. Changing EPSILON or equals(Geodetic3DPoint)
        // may require changing the logic used here also.
        return 31 * lon.hashCode() + lat.hashCode() ^ ((int) (elevation / DELTA));
    }

   /**
     * This method is used to test whether two points are equal in the sense that have
     * the same angular coordinate values and elevations, to within epsilon. See also
     * {@code proximallyEquals} method in the {@link FrameOfReference} class for
     * additional equality tests.
     *
     * @param that Geodetic3DPoint point to compare against this one.
     * @return {@code true} if specified Geodetic3DPoint point is equal in value to this
     *			Geodetic3DPoint. If point is a {@link Geodetic2DPoint} then 0 elevation
     * 			is used for comparison.
     */
    public boolean equals(Object that) {
		if (this == that) {
	    	return true;
		}
        if (that instanceof Geodetic3DPoint)
			return this.eq((Geodetic3DPoint) that);
		if (that instanceof Geodetic2DPoint) {
			Geodetic2DPoint pt = (Geodetic2DPoint)that;
			return eq(pt.lon, pt.lat, 0);  // test with 0 elevation
		}
		return false;
    }

	// Inherited Javadoc
    private boolean eq(Geodetic3DPoint that) {
        // angular diff on surface Earth quite different than Earth to nearest star
        return that != null && eq(that.lon, that.lat, that.elevation);
    }

	// Inherited Javadoc
	private boolean eq(Longitude otherLon, Latitude otherLat, double otherElevation) {
		return Angle.equals(this.lon.inRadians, otherLon.inRadians) &&
                Angle.equals(this.lat.inRadians, otherLat.inRadians) &&
                (Double.compare(this.elevation, otherElevation) == 0 ||
                        Math.abs(this.elevation - otherElevation) <= DELTA);
	}

    /**
     * This method abstracts this Geodetic3DPoint object as a general purpose GeoPoint.
     *
     * @param fRef the FrameOfReference in which to interpret this coordinate.
     * @return the equivalent Geodetic3DPoint
     */
	@NotNull
    public Geodetic3DPoint toGeodetic3D(FrameOfReference fRef) {
        return this;
    }

    /**
     * This toString method returns a string formatted version of this Geodetic3DPoint,
     * as '(lon, lat) @ h', lon and lat are printed with the specified number of decimal
     * digits of arc seconds, and h is printed as fractional meters with the number of
     * digits of precision specified.
     *
     * @param fractDig number of digits to the right of the decimal point to be formatted.
     * @return string version of this Geodetic3DPoint, suitable for debugging purposes.
     */
	@NotNull
    public String toString(int fractDig) {
        String result = super.toString(fractDig);
        String fract = (fractDig > 0) ? "." : "";
        while (fractDig-- > 0) fract += "0";
        DecimalFormat fmt = new DecimalFormat("0" + fract);
        return result + " @ " + fmt.format(elevation) + "m";
    }

    /**
     * The toString method returns a string formatted version of this Geodetic3DPoint,
     * as '(lon, lat) @ h', where lon and lat are printed in integer degrees, minutes,
     * and seconds, and elevation h is rounded to integer meters.
     *
     * @return string version of this Geodetic3DPoint, suitable for debugging purposes.
     */
	@NotNull
    public String toString() {
        return toString(0);
    }
}
