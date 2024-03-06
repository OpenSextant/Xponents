/****************************************************************************************
 *  Topocentric3DPoint.java
 *
 *  Created: Mar 14, 2007
 *
 *  @author Paul Silvey, based on work by Curtis Brown, Debbie Pierce, and Jason Mathews
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

import java.text.DecimalFormat;

/**
 * The Topocentric3DPoint class extends the simple Topocentric2DPoint class by adding an
 * explicit elevation value, in meters. To be correctly interpreted, one needs a particular
 * reference Topocentric origin point and Ellipsoid model of the earth (see FrameOfReference
 * class for ways to define and use this in coordinate conversions). The coordinate system
 * used here is the one defined by Nato Standards for Airborne Ground Survelliance (AGS)
 * data. In particular, the x-axis is positive in the East direction, the y-axis is
 * positive in the North direction, and the z-axis is positive above the tangent plane that
 * touches the surface of the Ellipsoid at the topocentric origin point. All units of length
 * are specified in meters. Note that there are other systems of topocentric reference that
 * make different assumptions about the directional positioning of the x, y, and z axis.
 * This class is lightweight and simple in the interest of efficient storage and processing.
 */
public class Topocentric3DPoint extends Topocentric2DPoint {
	
	private static final long serialVersionUID = 1L;
	
    private double elevation;

    /**
     * The constructor takes double precision floating point values for easting and
     * northing distances in meters from the topographic origin, along with an elevation
     * value in meters.  The origin itself is a contextual variable set in the
     * FrameOfReference object and used there by the coordinate transform methods that
     * need it.
     *
     * @param easting   number of meters along x-axis from topographic origin
     * @param northing  number of meters along y-axis from topographic origin
     * @param elevation number of meters along z-axis coordinate from topographic origin
     */
    public Topocentric3DPoint(double easting, double northing, double elevation) {
        super(easting, northing);
        this.elevation = elevation;
    }

    /**
     * This accessor method is used to get the elevation (assumed in meters).
     *
     * @return elevation of this Topocentric3DPoint point (in meters).
     */
    public double getElevation() {
        return elevation;
    }

    /**
     * This settor method is used to update the elevation value for this Topocentric3DPoint point.
     *
     * @param elevation height of this Topocentric3DPoint point above reference plane (in meters).
     */
    public void setElevation(double elevation) {
        this.elevation = elevation;
    }

    /**
     * This method returns a hash code for this Topocentric3DPoint object. The
     * result is the exclusive OR of the component values to maintain the general
     * contract for the hashCode method, which states that equal objects must have
     * equal hash codes.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        return 31 * super.hashCode() +
                Double.valueOf(elevation).hashCode();
    }

    /**
     * This method is used to test whether two points are equal in the sense that
     * have the same coordinate value.
     *
     * @param that Topocentric3DPoint point to compare against this one.
     * @return true if specified Topocentric3DPoint point is equal in value to this
     *         Topocentric3DPoint point.
     */
    public boolean equals(Topocentric3DPoint that) {
        return that != null && this.easting == that.easting &&
				this.northing == that.northing &&
				this.elevation == that.elevation;
    }

    /**
     * This method is used to test whether two points are equal in the sense that
     * have the same coordinate value.
     *
     * @param that Topocentric3DPoint point to compare against this one.
     * @return true if specified Topocentric3DPoint point is equal in value to this
     *         Topocentric3DPoint point.
     */
    public boolean equals(Object that) {
        return that instanceof Topocentric3DPoint && equals((Topocentric3DPoint) that);
    }

    /**
     * This method abstracts this Topocentric3DPoint object as a general purpose GeoPoint.
     *
     * @param fRef the FrameOfReference in which to interpret this coordinate.
     * @return the equivalent Geodetic3DPoint
     */
    public Geodetic3DPoint toGeodetic3D(FrameOfReference fRef) {
        return fRef.toGeodetic(this);
    }

    /**
     * This toString method returns a string formatted version of this Topocentric3DPoint,
     * printed with the specified number of decimal digits for each component.
     *
     * @param fractDig number of digits to the right of the decimal point to be formatted.
     * @return string version of this Topocentric3DPoint, suitable for debugging purposes.
     */
    public String toString(int fractDig) {
        String result = super.toString(fractDig);
        String fract = (fractDig > 0) ? "." : "";
        while (fractDig-- > 0) fract += "0";
        DecimalFormat fmt = new DecimalFormat("0" + fract);
        return (result + " @ " + fmt.format(elevation) + "m");
    }

    /**
     * This toString method returns a string formatted version of this Topocentric3DPoint,
     * printed with integer values for each component.
     *
     * @return string version of this Topocentric3DPoint, suitable for debugging purposes.
     */
    public String toString() {
        return toString(0);
    }
}
