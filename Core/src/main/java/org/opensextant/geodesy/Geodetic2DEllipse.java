/****************************************************************************************
 *  Geodetic2DEllipse.java
 *
 *  Created: Nov 2, 2007
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * The Geodetic2DEllipse class represents an ellipse on the surface of the earth
 * (itself modeled as an Ellipsoid). Similar to the Geodetic2DPoint class, instances
 * of this object are lightweight in the sense that they represent ellipses in terms
 * of simple parameters: center, semiMajorAxis, semiMinorAxis, and orientation.
 * The center point is specified as a Geodetic2DPoint, the axes are given in meters,
 * and the orientation is an Angle (clockwise positive from North to the semiMajor
 * axis). Just as with the Geodetic2DPoint class, the reference Ellipsoid earth model
 * is not explicitly carried with the objects, but may be needed for some uses of the
 * ellipse. Because an ellipse is symmetrical along either axis, two ellipses whose
 * orientations differ by 180 degrees are equal if all the other parameters are equal.
 */
public class Geodetic2DEllipse implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull private Geodetic2DPoint center;
    private double semiMajorAxis;
    private double semiMinorAxis;
    @NotNull private Angle orientation;
    private transient WeakReference<List<Geodetic2DPoint>> boundary;
    
    private static final String NULL_ERROR = "Ellipse parameters can not be null";
    private static final String AXIS_ERROR = "Semi-major axis must be greater than semi-minor axis";

    /**
     * Default constructor makes a geodetic ellipse at the central meridian on the
     * equator (0, 0), with 0 semi major and minor axes, and 0 degrees orientation.
     */
    public Geodetic2DEllipse() {
        this.center = new Geodetic2DPoint();
        this.semiMajorAxis = 0.0;
        this.semiMinorAxis = 0.0;
        this.orientation = new Angle();
    }

    /**
     * The constructor takes a center point, semi major and minor axes in meters,
     * and angle or orientation from North to the semi major axis.
     *
     * @param center        Geodetic2DPoint at the center of the ellipse
     * @param semiMajorAxis length in meters of the ellipse's semi major axis
     * @param semiMinorAxis length in meters of the ellipse's semi minor axis
     * @param orientation   Angle from North to semi major axis for this ellipse
     * @throws IllegalArgumentException if null parameter or axis ordering error
     */
    public Geodetic2DEllipse(Geodetic2DPoint center, double semiMajorAxis,
                             double semiMinorAxis, Angle orientation)
            throws IllegalArgumentException {
        if ((center == null) || (orientation == null))
            throw new IllegalArgumentException(NULL_ERROR);
        if (semiMajorAxis < semiMinorAxis)
            throw new IllegalArgumentException(AXIS_ERROR);
        this.center = center;
        this.semiMajorAxis = semiMajorAxis;
        this.semiMinorAxis = semiMinorAxis;
        this.orientation = orientation;
    }

    
    /**
     * Get the boundary list for the ellipse
     * @param count the number of slices that the list should include, non zero
     * and should be a multiple of four for best results.
     * @return the boundary iterator
     */
	@NotNull
    public Iterable<Geodetic2DPoint> boundary(int count) {
    	List<Geodetic2DPoint> blist;
    	if (boundary != null) {
    		blist = boundary.get();
    		if (blist != null && blist.size() == count) {
    			return blist;
    		}
    	}
    	blist = Collections.unmodifiableList(makeBoundary(count));
    	boundary = new WeakReference<>(blist);
    	return blist;
    }
    
    /**
     * Make a boundary list for a given count
     * @param count the number of slices in the boundary list
     * @return the boundary list, never {@code null} or empty
     */
	@NotNull
    private List<Geodetic2DPoint> makeBoundary(int count) {
    	// omega is the ellipse orientation angle (final rotation adjustment)
        Angle omega = new Angle(getOrientation().inRadians());
        // theta is the normalized circular sweep angle that
        // varys from -180 to +180 degrees
        Angle theta = new Angle(-Math.PI);
        // delta is the variable angular increment value
        Angle delta;

        // Use polar coordinate equation for an ellipse, where e is the
        // eccentricity.  Angle (theta) sweeps around the circle, and r
        // changes as a function of it
        double t, r;
        double a = getSemiMajorAxis();
        double b = getSemiMinorAxis();
        double e = Math.sqrt(1.0 - ((b * b) / (a * a)));

        // nominal delta in degrees (average angle for n steps around circle)
        double nDelta = 360.0 / count;
        Angle nominalDelta = new Angle(nDelta, Angle.DEGREES);

        // We compute r as a function of theta,
        // then rotate by ellipse orientation amount
        Geodetic2DArc arc = new Geodetic2DArc(getCenter(), 0.0, omega);
        Angle stepAngle = new Angle(0.0, Angle.DEGREES);
        List<Geodetic2DPoint> rval = new ArrayList<>();
        for (int step = 0; step < count; step++) {
            // y cycles from -1 to +1 to -1 from 0 deg to 180 deg, repeats
            double y = ((1.0 - Math.cos(stepAngle.inRadians() * 2.0)) - 1.0);
            // delta varies from 0 to 2 * nominal delta
            // to make small steps near major axis and larger ones near minor
            delta = new Angle(nDelta + (nDelta * y), Angle.DEGREES);
            theta = theta.add(delta);
            t = Math.cos(theta.inRadians());
            r = b / (Math.sqrt(1.0 - (e * e * t * t)));
            arc.setDistanceAndAzimuth(r, theta.add(omega));
            rval.add(arc.getPoint2());
            stepAngle = stepAngle.add(nominalDelta);
        }
        return rval;
    }
    
    /**
     * This method returns the Geodetic2DPoint at the center of this ellipse.
     *
     * @return Geodetic2DPoint center of ellipse
     */
	@NotNull
    public Geodetic2DPoint getCenter() {
        return center;
    }

    /**
     * This method is used to set the geodetic center point of this ellipse.
     *
     * @param center new Geodetic2DPoint center of this ellipse
     * @throws IllegalArgumentException error if center is null
     */
    public void setCenter(Geodetic2DPoint center) {
        if (center == null)
            throw new IllegalArgumentException(NULL_ERROR);
        this.center = center;
    }

    /**
     * This method returns the semi major axis of this ellipse, in meters.
     *
     * @return double distance in meters of this ellipse's semi major axis
     */
    public double getSemiMajorAxis() {
        return semiMajorAxis;
    }

    /**
     * This method is used to set the semi major axis (in meters) for this ellipse.
     *
     * @param semiMajorAxis double distance in meters of this ellipse's semi major axis
     * @throws IllegalArgumentException if axis ordering error
     */
    public void setSemiMajorAxis(double semiMajorAxis) {
        if (semiMajorAxis < this.semiMinorAxis)
            throw new IllegalArgumentException(AXIS_ERROR);
        this.semiMajorAxis = semiMajorAxis;
    }

    /**
     * This method returns the semi minor axis of this ellipse, in meters.
     *
     * @return double distance in meters of this ellipse's semi minor axis
     */
    public double getSemiMinorAxis() {
        return semiMinorAxis;
    }

    /**
     * This method is used to set the semi minor axis (in meters) for this ellipse.
     *
     * @param semiMinorAxis double distance in meters of this ellipse's semi minor axis
     * @throws IllegalArgumentException if axis ordering error
     */
    public void setSemiMinorAxis(double semiMinorAxis) {
        if (this.semiMajorAxis < semiMinorAxis)
            throw new IllegalArgumentException(AXIS_ERROR);
        this.semiMinorAxis = semiMinorAxis;
    }

    /**
     * This method is used to set the semi major and minor axes of this ellipse, in meters.
     *
     * @param semiMajorAxis length in meters of the ellipse's semi major axis
     * @param semiMinorAxis length in meters of the ellipse's semi minor axis
     * @throws IllegalArgumentException if axis ordering error
     */
    public void setSemiAxes(double semiMajorAxis, double semiMinorAxis)
            throws IllegalArgumentException {
        if (semiMajorAxis < semiMinorAxis)
            throw new IllegalArgumentException(AXIS_ERROR);
        this.semiMajorAxis = semiMajorAxis;
        this.semiMinorAxis = semiMinorAxis;
    }

    /**
     * This method returns the Angle of orientation from North to the semi major axis
     * of this ellipse.
     *
     * @return Angle of orientation of this ellipse
     */
	@NotNull
    public Angle getOrientation() {
        return orientation;
    }

    /**
     * This method is used to set the Angle of orientation from North to the semi major
     * axis for this ellipse.
     *
     * @param orientation Angle from North to the semi major axis for this ellipse
     * @throws IllegalArgumentException error if orientation is null
     */
    public void setOrientation(Angle orientation) {
        if (orientation == null)
            throw new IllegalArgumentException(NULL_ERROR);
        this.orientation = orientation;
    }

    /**
     * This method returns a hash code for this Geodetic2DEllipse object. Ellipses that
     * have equal parameters (or only differ in orientation by 180 degrees) have the
     * same hash code, since they are considered equal objects.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        // Normalize orientation in radians to between -PI and 0.0 inclusive
        double normAng = orientation.inRadians;
        if (normAng > 0) normAng = normAng - Math.PI;
        return 31 * center.hashCode() +
                Double.valueOf(semiMajorAxis).hashCode() ^
                Double.valueOf(semiMinorAxis).hashCode() ^
                Double.valueOf(normAng).hashCode();
    }

    /**
     * This method is used to compare this ellipse to another Geodetic2DEllipse object.
     * Ellipses that have equal parameters (or only differ in orientation by 180 degrees)
     * are considered equal objects.
     *
     * @param ellipse Geodetic2DEllipse to compare to this one
     * @return boolean indicating whether this ellipse is equal to the specified ellipse
     */
    public boolean equals(Geodetic2DEllipse ellipse) {
        if (this == ellipse) {
            return true;
        }
        boolean result = false;
        if (ellipse != null && this.getCenter().equals(ellipse.getCenter())) {
            if ((this.semiMajorAxis == ellipse.semiMajorAxis) &&
                    (this.semiMinorAxis == ellipse.semiMinorAxis)) {
                if (this.orientation.equals(ellipse.orientation))
                    result = true;
                else {
                    Angle temp = new Angle(Math.PI).add(this.orientation);
                    if (temp.equals(ellipse.orientation)) result = true;
                }
            }
        }
        return result;
    }

    /**
     * This method is used to compare this ellipse to another Geodetic2DEllipse object.
     * Ellipses that have equal parameters (or only differ in orientation by 180 degrees)
     * are considered equal objects.
     *
     * @param that Geodetic2DEllipse to compare to this one
     * @return boolean indicating whether this ellipse is equal to the specified ellipse
     */
    @Override
    public boolean equals(Object that) {
        return that instanceof Geodetic2DEllipse && equals((Geodetic2DEllipse) that);
    }

    /**
     * The toString method formats the ellipse for printing.
     *
     * @return String representation of center point, axes, and orientation
     */
    @Override
    public String toString() {
        return "(" + center + "," + semiMajorAxis + "m by " +
                semiMinorAxis + "m at " + orientation + ")";
    }
}
