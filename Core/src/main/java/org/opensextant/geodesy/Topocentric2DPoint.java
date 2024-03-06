/****************************************************************************************
 *  Topocentric2DPoint.java
 *
 *  Created: Mar 14, 2007
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

import java.io.Serializable;
import java.text.DecimalFormat;

import org.jetbrains.annotations.NotNull;

/**
 * The Topocentric2DPoint class is used to represent a point in Cartesian coordinate
 * space, relative to a plane that is tangent to the surface of an Ellipsoid at a
 * particular Geodetic Latitude and Longitude point, called the topocentric origin.
 * To be correctly interpreted, one needs a particular reference Topocentric origin
 * point and Ellipsoid model of the earth (see FrameOfReference class for ways to
 * define and use this in coordinate conversions). The coordinate system used here is
 * consistent with the one defined by Nato Standards for Airborne Ground Survelliance
 * (AGS) data. In particular, the x-axis is positive in the Easterly direction, the
 * y-axis is positive in the Northly direction. All units of length are specified in
 * meters. Note that there are other commonly used systems of topocentric reference
 * that make different assumptions about the directional orientation of the axes.
 * The x and y values are called easting and northing here, but they may not actually
 * follow the corresponding compass directions, depending on how close you are to the
 * polar regions. For example, the MGRS system uses a PolarStereographic projection
 * for the poles, and the easting and northing values of a topocentric point there
 * would follow the MGRS grid layed over that projection.
 */
public class Topocentric2DPoint implements GeoPoint, Serializable {
	private static final long serialVersionUID = 1L;
	
	protected double easting;     // x-axis
    protected double northing;    // y-axis

    /**
     * The constructor takes double precision floating point values for easting and
     * northing distances in meters from the topographic origin.  The origin itself
     * is a contextual variable set in the FrameOfReference object and used there by
     * the coordinate transform methods that need it.
     *
     * @param easting  number of meters along x-axis from topographic origin
     * @param northing number of meters along y-axis from topographic origin
     */
    public Topocentric2DPoint(double easting, double northing) {
        this.easting = easting;
        this.northing = northing;
    }

    /**
     * This accessor method is used to get the number of meters from the topographic
     * origin, along the x-axis or easting direction.
     *
     * @return number of meters along x-axis from topographic origin (easting)
     */
    public double getEasting() {
        return easting;
    }

    /**
     * This settor method is used to update the number of meters from the topographic
     * origin, along the x-axis or easting direction.
     *
     * @param easting number of meters along x-axis from topographic origin
     */
    public void setEasting(double easting) {
        this.easting = easting;
    }

    /**
     * This accessor method is used to get the number of meters from the topographic
     * origin, along the y-axis or northing direction.
     *
     * @return number of meters along y-axis from topographic origin (northing)
     */
    public double getNorthing() {
        return northing;
    }

    /**
     * This settor method is used to update the number of meters from the topographic
     * origin, along the y-axis or northing direction.
     *
     * @param northing number of meters along y-axis from topographic origin
     */
    public void setNorthing(double northing) {
        this.northing = northing;
    }

    /**
     * This method returns a hash code for this Topocentric2DPoint object. The
     * result is the exclusive OR of the component values to maintain the general
     * contract for the hashCode method, which states that equal objects must have
     * equal hash codes.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        return 31 * Double.valueOf(easting).hashCode() +
                Double.valueOf(northing).hashCode();
    }

    /**
     * This method is used to test whether two points are equal in the sense that have
     * the same coordinate value.
     *
     * @param that Topocentric2DPoint point to compare against this one.
     * @return true if specified Topocentric2DPoint point is equal in value to this
     *         Topocentric2DPoint point.
     */
    public boolean equals(Topocentric2DPoint that) {
        return that != null && this.easting == that.easting &&
				this.northing == that.northing;
    }

    /**
     * This method is used to test whether two points are equal in the sense that have
     * the same coordinate value.
     *
     * @param that Topocentric2DPoint point to compare against this one.
     * @return true if specified Topocentric2DPoint point is equal in value to this
     *         Topocentric2DPoint point.
     */
    public boolean equals(Object that) {
        return that instanceof Topocentric2DPoint && equals((Topocentric2DPoint) that);
    }

    /**
     * This method converts this Topocentric2DPoint to a Topocentric3DPoint, by assuming
     * an elevation (z-axis) value of zero meters from the Topographic point of origin.
     *
     * @return the equivalent Topocentric3DPoint
     */
    @NotNull
    public Topocentric3DPoint toTopocentric3D() {
        return new Topocentric3DPoint(easting, northing, 0.0);
    }

    /**
     * This method abstracts this Topocentric2DPoint object as a general purpose GeoPoint.
     *
     * @param fRef the FrameOfReference in which to interpret this coordinate.
     * @return the equivalent Geodetic3DPoint
     */
    @NotNull
    public Geodetic3DPoint toGeodetic3D(FrameOfReference fRef) {
        return fRef.toGeodetic(this.toTopocentric3D());
    }

    /**
     * This method formats the Topocentric2DPoint point as a parenthezised String containing
     * easting and northing values with the number of fractional digits of precision specified.
     *
     * @param fractDig number of fractional digits to display
     * @return String representation of this coordinate suitable for debugging
     */
    public String toString(int fractDig) {
        String fract = (fractDig > 0) ? "." : "";
        while (fractDig-- > 0) fract += "0";
        DecimalFormat fmt = new DecimalFormat("0" + fract);
        return "(" + fmt.format(easting) + "m East, " + fmt.format(northing) + "m North)";
    }

    /**
     * This method formats the Topocentric2DPoint point as a parenthezised String containing
     * easting and northing values in integer meters.
     *
     * @return String representation of this coordinate suitable for debugging
     */
    public String toString() {
        return toString(0);
    }

}
