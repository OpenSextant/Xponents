/****************************************************************************************
 *  GeocentricPoint.java
 *
 *  Created: Mar 13, 2007
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

/**
 * The GeocentricPoint class is used to represent a Earth-Centered, Earth-Fixed (ECEF)
 * point in 3D Cartesian coordinate space. The origin of the 3-space is the center of
 * the earth, which coincides with Ellipsoid model center points like WGS-84. The x-axis
 * lies on the Equitorial Plane and passes through the Prime Meridian in the positive
 * direction.  The y-axis is likewise in the Equitorial Plane, 90&deg; East of the x-axis.
 * The z-axis passes through the North Pole in the positive direction, making this
 * reference system a right-hand-system.
 */
public class GeocentricPoint implements GeoPoint, Serializable {
	private static final long serialVersionUID = 1L;
	
	private double x;
    private double y;
    private double z;

    /**
     * The constructor takes the 3 coordinate points, measured in meters from the center
     * of the earth, ordered x, y, and z.
     *
     * @param x double distance in meters along the geocentric x-axis
     * @param y double distance in meters along the geocentric y-axis
     * @param z double distance in meters along the geocentric z-axis
     */
    public GeocentricPoint(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * This accessor method returns the x coordinate (in meters) for this GeocentricPoint.
     *
     * @return double distance in meters along the geocentric x-axis
     */
    public double getX() {
        return x;
    }

    /**
     * This settor method updates the x coordinate (in meters) for this GeocentricPoint.
     *
     * @param x double distance in meters along the geocentric x-axis
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * This accessor method returns the y coordinate (in meters) for this GeocentricPoint.
     *
     * @return double distance in meters along the geocentric y-axis
     */
    public double getY() {
        return y;
    }

    /**
     * This settor method updates the y coordinate (in meters) for this GeocentricPoint.
     *
     * @param y double distance in meters along the geocentric y-axis
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * This accessor method returns the z coordinate (in meters) for this GeocentricPoint.
     *
     * @return double distance in meters along the geocentric z-axis
     */
    public double getZ() {
        return z;
    }

    /**
     * This settor method updates the z coordinate (in meters) for this GeocentricPoint.
     *
     * @param z double distance in meters along the geocentric z-axis
     */
    public void setZ(double z) {
        this.z = z;
    }

    /**
     * This method returns a hash code for this GeocentricPoint object. The
     * result is the exclusive OR of the component values to maintain the general
     * contract for the hashCode method, which states that equal objects must have
     * equal hash codes.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        return 31 * Double.valueOf(x).hashCode() +
                Double.valueOf(y).hashCode() ^
                Double.valueOf(z).hashCode();
    }

    /**
     * This method is used to test whether two points are equal in the sense that
     * have the same coordinate value.
     *
     * @param that GeocentricPoint point to compare against this one.
     * @return true if specified GeocentricPoint point is equal in value to this
     *         GeocentricPoint point.
     */
    public boolean equals(GeocentricPoint that) {
        return that != null && ((this.x == that.x) && (this.y == that.y) && (this.z == that.z));
    }

    /**
     * This method is used to test whether two points are equal in the sense that
     * have the same coordinate value.
     *
     * @param that GeocentricPoint point to compare against this one.
     * @return true if specified GeocentricPoint point is equal in value to this
     *         GeocentricPoint point.
     */
    public boolean equals(Object that) {
        return that instanceof GeocentricPoint && equals((GeocentricPoint) that);
    }

    /**
     * This method abstracts this Geocentric object as a general purpose GeoPoint.
     *
     * @param fRef the FrameOfReference in which to interpret this coordinate.
     * @return the equivalent Geodetic3DPoint
     */
    public Geodetic3DPoint toGeodetic3D(FrameOfReference fRef) {
        return fRef.toGeodetic(this);
    }

    /**
     * This toString method returns a string formatted version of this GeocentricPoint,
     * as '(x, y, z)', where each component is printed with the specified number of decimal
     * digits.
     *
     * @param fractDig number of digits to the right of the decimal point to be formatted.
     * @return string version of this GeocentricPoint, suitable for debugging purposes.
     */
    public String toString(int fractDig) {
        String fract = (fractDig > 0) ? "." : "";
        while (fractDig-- > 0) fract += "0";
        DecimalFormat fmt = new DecimalFormat("0" + fract);
        return ("(" + fmt.format(x) + "m, " + fmt.format(y) + "m, " + fmt.format(z) + "m)");
    }

    /**
     * The toString method returns a string formatted version of this GeocentricPoint,
     * as '(x, y, z)', where each component is rounded to integer meters.
     *
     * @return string version of this GeocentricPoint, suitable for debugging purposes.
     */
    public String toString() {
        return toString(0);
    }

}
