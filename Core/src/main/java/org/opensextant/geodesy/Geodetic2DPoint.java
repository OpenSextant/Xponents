/****************************************************************************************
 *  Geodetic2DPoint.java
 *
 *  Created: Dec 29, 2006
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
import java.util.Random;

import org.jetbrains.annotations.NotNull;

/**
 * The Geodetic2DPoint class represents a point on the surface of the earth, given in terms
 * of angle of Geodetic Latitude from the Equatorial Plane, and angle of Longitude from the
 * Prime Meridian. To be correctly interpreted, one needs a particular Ellipsoid model of
 * the earth (see FrameOfReference class for ways to define and use this in coordinate
 * conversions).<p>Note that Geodetic Latitude is defined as the angle of incidence with
 * the equatorial plane of a vector that is normal to a plane tangent to the ellipsoid at
 * the surface point. Such a vector does not in general pass through the center of the
 * Ellipsoid. Geodetic Latitude is different from the GeocentricPoint Latitude, which is
 * defined as the angle from the equatorial plane of a vector that passes through the center
 * of the ellipsoid and the point on the surface (the two kinds of latitude are the same if
 * the ellipsoid is a perfect sphere).<p>This class is extended as Geodetic3DPoint to include
 * height (i.e. altitude, elevation, depth) above or below the Ellipsoid surface.  When
 * converting from this type of two dimensional Geodetic2DPoint to a 3D-based coordinate
 * (including the GeocentricPoint or Earth-Centric, Earth-Fixed model used by the Global
 * Positioning System), the elevation of this object is assumed to be zero meters from the
 * surface of an Ellipsoid, which either must be specified or assumed to be WGS-84 as a
 * default.<p>This class is a convenience class to simply bundle angular surface coordinates
 * as a single object, useful when doing bounding box GIS comparisons, coordinate conversions,
 * map projections, or surface distance calculations. We chose to keep this class simple to
 * save memory when dealing with many geographic surface points or shapes, since an assumption
 * of 0 meters height from the WGS-84 Ellipsoid is often sufficient.
 */
public class Geodetic2DPoint implements GeoPoint, Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    protected Longitude lon;

    @NotNull
    protected Latitude lat;

    /**
     * This constructor takes a Longitude object and a Latitude object.
     *
     * @param lon Longitude of this Geodetic2DPoint point
     * @param lat Latitude of this Geodetic2DPoint point
     * @throws NullPointerException if latitude or longitude are null
     */
    public Geodetic2DPoint(Longitude lon, Latitude lat) {
        if (lon == null || lat == null) throw new NullPointerException();
        this.lon = lon;
        this.lat = lat;
    }

    /**
     * Default constructor makes a geodetic point at the central meridian on the equator (0, 0).
     */
    public Geodetic2DPoint() {
        this(new Longitude(), new Latitude());
    }

    /**
     * This constructor takes a String and parses as a lon-lat or lat-lon pair.
     * lon and lat must be separated by a comma, and the pair optionally enclosed within parentheses.
     * <pre>
     * Examples:
     *  (122� 5' 6" W, 37� 25' 19" N)
     *  37 25 19.07 N, 122 05 06.24 W
     *  37 25'19.07"N, 122 05'06.24"W
     *  (42� 22' 11.77" N, 71� 1' 40.30" W)
     *  (51 deg 28' 15.19" N, 0 deg 27' 33.41" W)
     *  51 deg 28' 15.19" N, 0 deg 27' 33.41" W
     *  (12 34 56E, 45 34 23N)
     *  12 34 56E, 45 34 23N
     * </pre>
     *
     * @param geoStr Normal form is same as toString output.
     * @throws IllegalArgumentException error if syntax is invalid
     */
    public Geodetic2DPoint(String geoStr) {
        // First look for and remove enclosing parentheses
        geoStr = geoStr.trim().toUpperCase();
        int n = geoStr.length() - 1;
        if ((geoStr.charAt(0) == '(') && (geoStr.charAt(n) == ')'))
            geoStr = geoStr.substring(1, n);
        // Now, break into two parts based on comma delimiter
        n = geoStr.indexOf(',');
        if (n == -1)
            throw new IllegalArgumentException("Geodetic2DPoint string (" +
                    geoStr + ") is missing comma lon-lat separator");
        String part1 = geoStr.substring(0, n);
        String part2 = geoStr.substring(n + 1);
        try {
            lon = new Longitude(part1);
            lat = new Latitude(part2);
        } catch (IllegalArgumentException ex) {
            lat = new Latitude(part1);
            lon = new Longitude(part2);
        }
    }

    /**
     * This constructor generates a new Geodetic2DPoint drawn from a distribution
     * that is uniform over the surface of a sphere. See the following web site
     * for a proof: "<a href="http://mathworld.wolfram.com/SpherePointPicking.html">...</a>"
     *
     * @param r Random object to draw samples from
     */
    public Geodetic2DPoint(Random r) {
        // Generate a random point, uniformly distributed over spherical surface
        lon = new Longitude(2.0 * Math.PI * r.nextDouble());
        lat = new Latitude(Math.acos((2.0 * r.nextDouble()) - 1.0) - (Math.PI / 2.0));
    }

    /**
     * This accessor method returns the Longitude component of this Geodetic2DPoint.
     *
     * @return Longitude of this Geodetic2DPoint point
     */
    @NotNull
    public Longitude getLongitude() {
        return lon;
    }

    /**
     * Get Longitude in decimal degrees.
     *
     * @return Longitude in decimal degrees.
     */
    public double getLongitudeAsDegrees() {
        return lon.inDegrees();
    }

    /**
     * This accessor method returns the Latitude component of this Geodetic2DPoint.
     *
     * @return Latitude of this Geodetic2DPoint point
     */
    @NotNull
    public Latitude getLatitude() {
        return lat;
    }

    /**
     * Get Latitude in decimal degrees.
     *
     * @return Latitude in decimal degrees.
     */
    public double getLatitudeAsDegrees() {
        return lat.inDegrees();
    }

    /**
     * This method is used to set or change the Longitude component of this Geodetic2DPoint.
     *
     * @param lon Longitude of this Geodetic2DPoint point
     * @throws NullPointerException if longitude is null
     */
    public void setLongitude(Longitude lon) {
        if (lon == null) throw new NullPointerException();
        this.lon = lon;
    }

    /**
     * This method is used to set or change the Latitude component of this Geodetic2DPoint.
     *
     * @param lat Latitude of this Geodetic2DPoint point
     * @throws NullPointerException if latitude is null
     */
    public void setLatitude(Latitude lat) {
        if (lat == null) throw new NullPointerException();
        this.lat = lat;
    }

    /**
     * This method returns a hash code for this Geodetic2DPoint object. The
     * result is the exclusive OR of the component values to maintain the general
     * contract for the hashCode method, which states that equal objects must have
     * equal hash codes.
     *
     * @return a hash code value for this object.
     */
    public int hashCode() {
        return 31 * lon.hashCode() + lat.hashCode();
    }

    // Inherited Javadoc
    private boolean eq(Geodetic2DPoint that) {
        // angular diff on surface Earth quite different than Earth to nearest star
        return Angle.equals(this.lon.inRadians, that.lon.inRadians) &&
                Angle.equals(this.lat.inRadians, that.lat.inRadians);
    }

    /**
     * This method is used to test whether two points are equal in the sense that have
     * the same angular coordinate values to within epsilon. See also
     * {@code proximallyEquals} method in the {@link FrameOfReference} class for
     * additional equality tests.
     *
     * @param that Geodetic2DPoint point to compare against this one.
     * @return {@code true} if specified Geodetic2DPoint point is equal in value to
     *         this Geodetic2DPoint. If point is a Geodetic3DPoint
     *         then {@link Geodetic3DPoint#equals(Object)} is used for equality with
     *         this Geodetic2DPoint having a zero elevation.
     */
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that instanceof Geodetic3DPoint) {
            Geodetic3DPoint pt = (Geodetic3DPoint) that;
            return pt.equals(this);
        }
        return (that instanceof Geodetic2DPoint) && this.eq((Geodetic2DPoint) that);
    }

    /**
     * This method abstracts this Geodetic2DPoint object as a general purpose GeoPoint.
     * It does the conversion by assuming an elevation value of zero meters.
     *
     * @param fRef the FrameOfReference in which to interpret this coordinate.
     * @return the equivalent Geodetic3DPoint
     */
    @NotNull
    public Geodetic3DPoint toGeodetic3D(FrameOfReference fRef) {
        return new Geodetic3DPoint(lon, lat, 0.0);
    }

    /**
     * This method formats the Geodetic2DPoint point as a parenthezised String containing Longitude
     * (in degrees, minutes, seconds) and Latitude (in degrees, minutes, seconds), displaying
     * the seconds components with the number of fractional digits of precision specified.
     * This format is parsable by the String constructor.
     *
     * @param fractDigOfsec number of fractional digits of arc seconds to display
     * @return geodeticString e.g. (123deg 45' 6.78" W, 9&deg; 8' 7.65" N)
     */
    public String toString(int fractDigOfsec) {
        return "(" + lon.toString(fractDigOfsec) + ", " + lat.toString(fractDigOfsec) + ")";
    }

    /**
     * This method formats the Geodetic2DPoint point as a parenthezised String containing Longitude
     * (in degrees, minutes, seconds) and Latitude (in degrees, minutes, seconds), separated
     * by a single comma.  This format is parsable by the String constructor.
     *
     * @return geodeticString e.g. (123deg 45' 6" W, 9&deg; 8' 7" N)
     */
    public String toString() {
        return toString(0);
    }
}
