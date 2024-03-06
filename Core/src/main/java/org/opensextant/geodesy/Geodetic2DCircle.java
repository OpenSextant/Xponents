/******************************************************************************
 * Geodetic2DCircle.java Nov 3, 2009 8:20:19 PM psilvey
 *
 * (C) Copyright MITRE Corporation 2009
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
 *****************************************************************************/
package org.opensextant.geodesy;

import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;

/**
 * The Geodetic2DCircle class represents an circle on the surface of the earth
 * (itself modeled as an Ellipsoid). Similar to the Geodetic2DPoint class,
 * instances of this object are lightweight in the sense that they represent
 * circles in terms of simple parameters: center and radius. The center point
 * is specified as a Geodetic2DPoint, and the radius in meters. Just as with
 * the Geodetic2DPoint class, the reference Ellipsoid earth model is not
 * explicitly carried with the objects, but may be needed for some uses of the
 * circle.
 */
public class Geodetic2DCircle {

    @NotNull private Geodetic2DPoint center;

    private double radius;

    /**
     * Default constructor makes a geodetic circle at the central meridian on
     * the equator (0, 0), with a radius of 0.
     */
    public Geodetic2DCircle() {
        this.center = new Geodetic2DPoint();
        this.radius = 0.0;
    }

    /**
     * This constructor takes center Geodetic2DPoint and the radius in meters.
     *
     * @param center Geodetic2DPoint of the center of this circle
     * @param radius length in meters of the radius of this circle
	 * @throws NullPointerException if center is null
     */
    public Geodetic2DCircle(Geodetic2DPoint center, double radius) {
		setCenter(center);
        this.radius = radius;
    }

    /**
     * Getter method for accessing the center of this circle object.
     *
     * @return Geodetic2DPoint at the center of this circle
     */
	@NotNull
    public Geodetic2DPoint getCenter() {
        return center;
    }

    /**
     * Setter method to assign the center Geodetic2DPoint of this circle object.
     *
     * @param center Geodetic2DPoint to make the center of this circle
	 * @throws NullPointerException if center is null
     */
    public void setCenter(Geodetic2DPoint center) {
		if (center == null) throw new NullPointerException();
        this.center = center;
    }

    /**
     * Getter method for accessing the radius of this circle object.
     *
     * @return double number of meters of the radius of this circle
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Setter method to assign the radius of this circle object.
     *
     * @param radius double number of meters of this circle
     */
    public void setRadius(double radius) {
        this.radius = radius;
    }

    /**
     * Convenience method to iterate over boundary points of circle at nPoints
     * resolution.
     *
     * @param nPoints int number of points on boundary to use (1st is due South),
	 * 					should be greater than 0
     * @return Iterable collection of nPoints Geodetic2DPoints on circle boundary
	 * @throws ArithmeticException if nPoints = 0
     */
	@NotNull
    public Iterable<Geodetic2DPoint> boundary(int nPoints) {
        ArrayList<Geodetic2DPoint> ptList = new ArrayList<>(nPoints);
        Angle compassDirection = new Angle(-Math.PI); // Start from South direction
        Angle inc = new Angle(2.0 * Math.PI / (double) nPoints); // degree increments
        Geodetic2DArc arc = new Geodetic2DArc(center, radius, compassDirection);
        for (int i = 0; i < nPoints; i++) {
            ptList.add(arc.getPoint2());
            compassDirection = compassDirection.add(inc);
            arc.setForwardAzimuth(compassDirection);
        }
        return ptList;
    }

    /**
     * This method returns a hash code for this Geodetic2DCircle object.
     * Circles that have equal parameters have the same hash code, since
     * they are considered equal objects.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return 31 * center.hashCode() + Double.valueOf(radius).hashCode();
    }

    /**
     * This method is used to compare this circle to another Geodetic2DCircle
     * object. Circles that have equal parameters are considered equal objects.
     *
     * @param circle Geodetic2DCircle to compare to this one
     * @return boolean indicating if this circle is equal to specified one
     */
    public boolean equals(Geodetic2DCircle circle) {
        return circle != null && this.radius == circle.radius &&
				this.getCenter().equals(circle.getCenter());
    }

    /**
     * This method is used to compare this circle to another Geodetic2DCircle
     * object. Circles that have equal parameters are considered equal objects.
     *
     * @param that Geodetic2DCircle to compare to this one
     * @return boolean indicating if this circle is equal to specified one
     */
    @Override
    public boolean equals(Object that) {
        return that instanceof Geodetic2DCircle &&
                equals((Geodetic2DCircle) that);
    }

    /**
     * The toString method formats the circle for printing.
     *
     * @return String representation of center point + radius
     */
    @Override
    public String toString() {
        return "(within " + radius + " meters of " + center + ")";
    }
}
