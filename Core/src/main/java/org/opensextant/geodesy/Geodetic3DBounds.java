/****************************************************************************************
 *  Geodetic3DBounds.java
 *
 *  Created: Apr 3, 2007
 *
 *  @author Paul Silvey
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

import org.jetbrains.annotations.NotNull;
/**
 * The Geodetic3DBounds class extends the simple Geodetic2DBounds class by adding explicit
 * min and max elevation values, in meters.
 */
public class Geodetic3DBounds extends Geodetic2DBounds {
	
	private static final long serialVersionUID = 1L;

    // using 1e-3 meters for elevation equality gives a precision up to 1 millimeter
    private static final double DELTA = 1e-3;

    public double minElev;
    public double maxElev;

    /**
     * The default constructor returns a bounding box containing only the 0-0-0 point.
     */
    public Geodetic3DBounds() {

    }

    /**
     * This constructor will accept west and east Geodetic3DPoint coordinates and set the
     * bounding box limits accordingly.  Note that the source coordinate components are
     * used by reference (i.e. they are not cloned).  The first coordinate is used to set
     * the west Longitude even if its value is greater than the east, so boxes that span
     * the international date line can be accommodated by this convention of listing lons
     * in reverse order.  On the other hand, latitude values are normalized so that south
     * is always less than north. Min and Max elevation are set accordingly.
     *
     * @param westCoordinate western most point
     * @param eastCoordinate eastern most point
     */
    public Geodetic3DBounds(Geodetic3DPoint westCoordinate, Geodetic3DPoint eastCoordinate) {
        super(westCoordinate, eastCoordinate);
        // Normalize elevation values so min < max
        if (westCoordinate.getElevation() < eastCoordinate.getElevation()) {
            minElev = westCoordinate.getElevation();
            maxElev = eastCoordinate.getElevation();
        } else {
            minElev = eastCoordinate.getElevation();
            maxElev = westCoordinate.getElevation();
        }
    }

    /**
     * This constructor takes a single Geodetic3DPoint seed point and constructs a degenerate
     * bounding box that contains it.  It is primarily used with the include method to grow a
     * bounding box around a set of points.
     *
     * @param seedPoint Initial point to use as limits of bounding box.
     */
    public Geodetic3DBounds(Geodetic3DPoint seedPoint) {
        super(seedPoint);
        minElev = seedPoint.getElevation();
        maxElev = minElev;
    }

    /**
     * This constructor takes a single Geodetic3DBounds as a seed and constructs a new
     * bounding box by cloning its values. It is primarily used with the include methods
     * to grow a bounding box around a set of points or other bounding boxes.
     *
     * @param seedBBox Initial bounding box to use as limits of bounding box.
     */
    public Geodetic3DBounds(Geodetic3DBounds seedBBox) {
        super(seedBBox);
        minElev = seedBBox.minElev;
        maxElev = seedBBox.maxElev;
    }

    /**
     * This constructor takes a {@code Geodetic2DBounds} as a seed and constructs a new
     * bounding box by cloning its values in addition to the min/max elevation bounds.
     *
     * @param seedBBox Initial bounding box to use as limits of bounding box
     * @param minElev min elevation (in meters)
     * @param maxElev max elevation (in meters)
     */
    public Geodetic3DBounds(Geodetic2DBounds seedBBox, double minElev, double maxElev) {
        super(seedBBox);
        if (minElev > maxElev) {
            // min/max elevations in reverse order. swap values.
            this.maxElev = minElev;
            this.minElev = maxElev;
        } else {
            this.minElev = minElev;
            this.maxElev = maxElev;
        }
    }

    /**
     * This method is used to extend this bounding box to include a new point. If the new
     * point is already inside the existing bounding box, no change will result. The bounding
     * box is always extended by the smallest amount necessary to include the new point.
     * Sometimes this will cause the bounding box to wrap around the international date line.
     *
     * @param newPoint new Geodetic3DPoint point to include in this bounding box.
     */
    public void include(Geodetic3DPoint newPoint) {
        super.include(newPoint);
        double elev = newPoint.getElevation();
        if (elev < minElev) minElev = elev;
        else if (elev > maxElev) maxElev = elev;
    }

    /**
     * This method is used to extend this bounding box to include a new point. If the new
     * point is already inside the existing bounding box, no change will result. The bounding
     * box is always extended by the smallest amount necessary to include the new point.
     * Sometimes this will cause the bounding box to wrap around the international date line.
     *
     * @param newPoint new Geodetic2DPoint point to include in this bounding box.
     */
    public void include(Geodetic2DPoint newPoint) {
        if (newPoint instanceof Geodetic3DPoint)
            include((Geodetic3DPoint)newPoint);
        else
            super.include(newPoint);
    }

    /**
     * This method is used to extend this bounding box to include another bounding box. If the
     * newly specified bounding box is inside the existing bounding box, no change will result.
     * This bounding box is always extended by the smallest amount necessary to include the new
     * bounding box. Sometimes this will cause this bounding box to wrap around the international
     * date line.
     *
     * @param bbox additional Geodetic3DBounds bounding box to include in this bounding box.
     */
    public void include(Geodetic3DBounds bbox) {
        this.include(new Geodetic3DPoint(bbox.getWestLon(), bbox.getSouthLat(), bbox.minElev));
        this.include(new Geodetic3DPoint(bbox.getEastLon(), bbox.getNorthLat(), bbox.maxElev));
    }

    /**
     * This method is used to extend this bounding box to include another bounding box. If the
     * newly specified bounding box is inside the existing bounding box, no change will result.
     * This bounding box is always extended by the smallest amount necessary to include the new
     * bounding box. Sometimes this will cause this bounding box to wrap around the international
     * date line.
     *
     * @param bbox additional Geodetic2DBounds bounding box to include in this bounding box.
     */
	public void include(Geodetic2DBounds bbox) {
		if (bbox instanceof Geodetic3DBounds)
            include((Geodetic3DBounds)bbox);
        else
            super.include(bbox);
	}

    /**
     * This predicate method determines whether the specified geodetic point is
     * contained within this bounding box.
     *
     * @param testPoint Geodetic3DPoint to test for containment within this bounding box
     * @return true if specified point is within this Geodetic3DBounds, false otherwise.
     */
    public boolean contains(Geodetic3DPoint testPoint) {
        double testElev = testPoint.getElevation();
        return (super.contains(testPoint) && (minElev <= testElev) && (testElev <= maxElev));
    }

    /**
     * This predicate method determines whether the specified geodetic point is
     * contained within this bounding box.
     *
     * @param testPoint Geodetic2DPoint to test for containment within this bounding box
     * @return true if specified point is within this Geodetic3DBounds, false otherwise.
     */
    public boolean contains(Geodetic2DPoint testPoint) {
        if (testPoint instanceof Geodetic3DPoint)
            return contains((Geodetic3DPoint)testPoint);
        else
            return super.contains(testPoint);
    }

    /**
     * This predicate method determines whether the specified geodetic bounding box is
     * contained within this bounding box.
     *
     * @param testBox Geodetic3DBounds to test for containment within this bounding box
     * @return true if the specified box is contained within this bounding box
     */
    public boolean contains(Geodetic3DBounds testBox) {
        double testMinElev = testBox.minElev;
        double testMaxElev = testBox.maxElev;
        return (super.contains(testBox) &&
                (minElev <= testMinElev) && (testMinElev <= maxElev) &&
                (minElev <= testMaxElev) && (testMaxElev <= maxElev));
    }

    /**
     * This predicate method determines whether the specified geodetic bounding box is
     * contained within this bounding box.
     *
     * @param testBox Geodetic2DBounds to test for containment within this bounding box
     * @return true if the specified box is contained within this bounding box
     */
    public boolean contains(Geodetic2DBounds testBox) {
        if (testBox instanceof Geodetic3DBounds)
            return contains((Geodetic3DBounds)testBox);
        else
            return super.contains(testBox);
    }

    /**
     * This method is used to determine the geodetic point that lies at the center of
     * this bounding box.
     *
     * @return Geodetic3DPoint that lies at the center of this Geodetic2DBounds box
     */
    @NotNull
    public Geodetic3DPoint getCenter() {
        double westLonRad = this.getWestLon().inRadians;
        double eastLonRad = this.getEastLon().inRadians;
        // If longitudes wrap, adjust east to be greater than west before calc
        if (westLonRad > eastLonRad) eastLonRad += Angle.TWO_PI;
        double centLonRad = westLonRad + ((eastLonRad - westLonRad) / 2.0);
        double southLatRad = this.getSouthLat().inRadians;
        double northLatRad = this.getNorthLat().inRadians;
        double centLatRad = southLatRad + ((northLatRad - southLatRad) / 2.0);
        double centElev = minElev + ((maxElev - minElev) / 2.0);
        // Note that Longitude constructor will re-normalize angle if > 360 deg
        return new Geodetic3DPoint(new Longitude(centLonRad), new Latitude(centLatRad), centElev);
    }

    /**
     * Returns a hash code for this {@code Geodetic3DBounds} object. The
     * result is the exclusive OR of the latitude, longitude, and elevation values to maintain
     * the general contract for the hashCode method, which states
     * that equal objects must have equal hash codes.
     *
     * @return a {@code hash code} value for this object.
     */
    public int hashCode() {
        return 31 * super.hashCode() + Double.valueOf(maxElev).hashCode() ^ Double.valueOf(minElev).hashCode();
    }

    /**
     * Compares this object against the specified object.  The result
     * is {@code true} if and only if the argument is not
     * {@code null} and is a {@code Geodetic3DBounds} object that
     * has the same latitude, longitude, and elevation values.
     *
     * @param that the object to compare with.
     * @return {@code true} if the objects are the same;
     *         {@code false} otherwise.
     */
    public boolean equals(Object that) {
		if (that instanceof Geodetic3DBounds)
			return equals((Geodetic3DBounds) that);
		return that instanceof Geodetic2DBounds && equals((Geodetic2DBounds) that);
	}

    /**
     * The equals method tests for bounding box coordinate numeric equality
     *
     * @param that Geodetic3DBounds object to compare to this one
     * @return true if specified Geodetic3DBounds is spatially equivalent to this one
     */
    public boolean equals(Geodetic3DBounds that) {
        return this == that || that != null && equals(that, that.minElev, that.maxElev);
    }

	/**
     * The equals method tests for bounding box coordinate numeric equality
     *
     * @param that Geodetic3DBounds object to compare to this one
     * @return true if specified Geodetic3DBounds is spatially equivalent to this one
     */
    public boolean equals(Geodetic2DBounds that) {
	if (that instanceof Geodetic3DBounds)
		return equals((Geodetic3DBounds) that);
	return equals(that, 0, 0);
    }

    private boolean equals(Geodetic2DBounds that, double minElev, double maxElev) {
	return super.equals(that) && Math.abs(this.maxElev - maxElev) < DELTA &&
			Math.abs(this.minElev - minElev) < DELTA;
    }

    /**
     * The toString method formats the bounding box coordinates for printing,
     * as SW .. NE points, followed by min and max elevation in meters.
     *
     * @return String representation of corner points of bounding box plus elevation range.
     */
    public String toString() {
        String result = super.toString();
        return (result + " .. (" + minElev + "m, " + maxElev + "m)");
    }
}
