/****************************************************************************************
 *  FrameOfReference.java
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

import java.io.Serializable;

import org.jetbrains.annotations.NotNull;

/**
 * The FrameOfReference class is a contextual class to allow our geo-spatial point
 * objects to remain lightweight and context free by themselves, yet allow them to
 * be accurately converted between formats or compared when necessary. This class
 * holds the global and local reference points and mathematical modeling assumptions
 * for converting geo-spatial points from one representational system to another.
 * In particular, it holds the Ellipsoid model of the earth and the Topocentric point
 * of origin, along with methods to convert points between Geodetic, Geocentric, and
 * Topocentric point formats, given this FrameOfReference. The default Ellipsoid is
 * WGS-84, and the default Topocentric point of origin is where the equator meets the
 * prime meridian (0 deg E, 0 deg N). All of the point types are objects, and there are
 * three different kinds of equivalence tests that may be applied to them. As objects
 * with unique handles, they will only be '=' if they map to the same address in
 * memory (i.e. are the exact same object in the Java Virtual Machine). If they have
 * the same values for their components but different addresses in memory, they are
 * considered 'equal' (each individual point class has an 'equals' method to test this).
 * This is the sense of equality that is used for their hashcode identity. Finally,
 * since coordinate conversion is subject to some loss of precision and round-off error,
 * the FrameOfReference class provides a method (called proximallyEquals) to compare
 * points for near-equality in space. As currently coded, points are considered equal
 * if they are within 1 meter of each other on the surface of the Ellipsoid, and also
 * within 1 meter of each other in elevation value from the surface.  As a result, this
 * equality test requires that non-Geodetic representations be first converted to their
 * Geodetic form, and surface distance is measured by the distance method of the
 * Ellipsoid class (which is a more accurate form of surface distance than Greater
 * Circle distances on a Sphere).
 */
public class FrameOfReference implements Serializable {

	private static final long serialVersionUID = 1L;
	
	@NotNull private Ellipsoid ellip;
	@NotNull private Geodetic3DPoint topoOrigLle;
	@NotNull private GeocentricPoint topoOrigEcf;

    private static final Ellipsoid DEFAULT_ELLIPSOID =
            Ellipsoid.getInstance("WGS 84");
    private static final Geodetic3DPoint DEFAULT_TOPOORIG =
            new Geodetic3DPoint(new Longitude(0.0), new Latitude(0.0), 0.0);

    /*
     * This method converts a Geodetic3DPoint to a GeocentricPoint (lle 2 ecf).
     *
     * @param lle the Geodetic3DPoint (Longitude, Latitude, Elevation or lle)
     * @return the GeocentricPoint (Earth Centered Fixed or ecf)
     * @throws NullPointerException if ellip or lle are null
     */
    private GeocentricPoint toGeocentric(Geodetic3DPoint lle) {
        double lambda = lle.getLongitude().inRadians();
        Latitude lat = lle.getLatitude();
        double phi = lat.inRadians();
        double h = lle.getElevation();

        double N = ellip.getRadius(lat);

        double cosPhi = Math.cos(phi);
        double X = (N + h) * cosPhi * Math.cos(lambda);
        double Y = (N + h) * cosPhi * Math.sin(lambda);
        double Z = ((N * (1.0 - ellip.getEccentricitySquared())) + h) * Math.sin(phi);

        return new GeocentricPoint(X, Y, Z);
    }

    /*
     * This method converts a GeocentricPoint to a TopocentricPoint (ecf 2 tcs).
     *
     * @param ecf the GeocentricPoint (Earth Centered Fixed or ecf)
     * @return the TopocentricPoint (Topo Centric System or tcs)
     * @throws NullPointerException if ecf is null
     */
    private Topocentric3DPoint toTopocentric(GeocentricPoint ecf) {
        double x0 = ecf.getX() - topoOrigEcf.getX();
        double y0 = ecf.getY() - topoOrigEcf.getY();
        double z0 = ecf.getZ() - topoOrigEcf.getZ();

        double lonRad = topoOrigLle.getLongitude().inRadians();
        double cosOrLon = Math.cos(lonRad);
        double sinOrLon = Math.sin(lonRad);

        double latRad = topoOrigLle.getLatitude().inRadians();
        double cosOrLat = Math.cos(latRad);
        double sinOrLat = Math.sin(latRad);

        double x = cosOrLon * x0 + sinOrLon * y0;
        double tcsX = -sinOrLon * x0 + cosOrLon * y0;
        double tcsY = -sinOrLat * x + cosOrLat * z0;
        double tcsZ = cosOrLat * x + sinOrLat * z0;
        return new Topocentric3DPoint(tcsX, tcsY, tcsZ);
    }

    /*
     * This method converts a Geodetic3DPoint to a TopocentricPoint (lle 2 ecf 2 tcs).
     * It does this via an intermediate conversion using a GeocentricPoint (ecf).
     *
     * @param lle the Geodetic3DPoint (Longitude, Latitude, Elevation or lle)
     * @return the TopocentricPoint (Topo Centric System or tcs)
     */
    private Topocentric3DPoint toTopocentric(Geodetic3DPoint lle) {
        GeocentricPoint ecf = this.toGeocentric(lle);
        return this.toTopocentric(ecf);
    }

    /*
     * This method converts a TopocentricPoint to a GeocentricPoint (tcs 2 ecf).
     *
     * @param tcs the TopocentricPoint (Topo Centric System or tcs)
     * @return the GeocentricPoint (Earth Centered Fixed or ecf)
     */
    private GeocentricPoint toGeocentric(Topocentric3DPoint tcs) {
        double lonRad = topoOrigLle.getLongitude().inRadians();
        double sinLambda = Math.sin(lonRad);
        double cosLambda = Math.cos(lonRad);

        double latRad = topoOrigLle.getLatitude().inRadians();
        double sinPhi = Math.sin(latRad);
        double cosPhi = Math.cos(latRad);

        double x = (-1.0 * sinPhi * tcs.getNorthing() + cosPhi * tcs.getElevation());
        double y = tcs.getEasting();

        double ecfX = cosLambda * x - sinLambda * y + topoOrigEcf.getX();
        double ecfY = sinLambda * x + cosLambda * y + topoOrigEcf.getY();
        double ecfZ = (cosPhi * tcs.getNorthing() +
                sinPhi * tcs.getElevation() + topoOrigEcf.getZ());
        return new GeocentricPoint(ecfX, ecfY, ecfZ);
    }

    /*
     * This method converts a GeocentricPoint to a Geodetic3DPoint (ecf 2 lle).
     *
     * @param ecf the GeocentricPoint (Earth Centered Fixed or ecf)
     * @return the Geodetic3DPoint (Longitude, Latitude, Elevation or lle)
     */
    private Geodetic3DPoint toGeodetic(GeocentricPoint ecf) {
        double X = ecf.getX();
        double Y = ecf.getY();
        double Z = ecf.getZ();
        double a = ellip.getEquatorialRadius();
        double b = ellip.getPolarRadius();

        double p = Math.sqrt((X * X) + (Y * Y));
        Longitude lon;
        Latitude lat;
        double h;
        if (p == 0.0) {
            // At a Pole all Longitude values are at the same place, so we might normalize
            // lon to be zero, but we're using the Topocentric Origin's lon instead
            lon = topoOrigLle.getLongitude();
            lat = new Latitude((Z > 0.0) ? +90.0 : -90.0, Angle.DEGREES);
            h = (Z > 0.0) ? Z - b : b - Z;
        } else {
            lon = new Longitude(Math.atan2(Y, X));
            double theta = Math.atan((Z * a) / (p * b));
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);
            double phi = Math.atan(
                    (Z + (ellip.get2ndEccentricitySquared() * b * sinTheta * sinTheta * sinTheta)) /
                            (p - (ellip.getEccentricitySquared() * a * cosTheta * cosTheta * cosTheta)));
            lat = new Latitude(phi);
            h = (p / Math.cos(phi)) - ellip.getRadius(lat);
        }
        return new Geodetic3DPoint(lon, lat, h);
    }

    /*
     * This method converts a TopocentricPoint to a Geodetic3DPoint (tcs 2 lle).
     * It does this via an intermediate conversion using a GeocentricPoint (ecf).
     *
     * @param tcs the TopocentricPoint (Topo Centric System or tcs)
     * @return the Geodetic3DPoint (Longitude, Latitude, Elevation or lle)
     */
    private Geodetic3DPoint toGeodetic(Topocentric3DPoint tcs) {
        GeocentricPoint ecf = this.toGeocentric(tcs);
        return this.toGeodetic(ecf);
    }

    /**
     * This constructor takes an Ellipsoid object earth model and a Geodetic3DPoint
     * to use as the Topographic Origin point for coordinate conversions.
     *
     * @param ellip    the Ellipsoid model of the earth to use in conversions
     * @param topoOrig the topographic origin point as a Geodetic3DPoint object.
	 * @throws NullPointerException if ellip or topoOrig are null
     */
    public FrameOfReference(Ellipsoid ellip, Geodetic3DPoint topoOrig) {
        this.ellip = ellip;
        this.topoOrigLle = topoOrig;
        this.topoOrigEcf = this.toGeocentric(topoOrig);
    }

    /**
     * This constructor takes an Ellipsoid object earth model and defaults the
     * Topographic Origin to be (0&deg; N, 0&deg; E, 0m).
     *
     * @param ellip the Ellipsoid model of the earth to use in conversions.
	 * @throws NullPointerException if ellip is null
     */
    public FrameOfReference(Ellipsoid ellip) {
        this(ellip, DEFAULT_TOPOORIG);
    }

    /**
     * This constructor takes a Geodetic3DPoint to use as the Topographic Origin
     * point for coordinate conversions, and defaults the Ellipsoid to be WGS-84.
     *
     * @param topoOrig the topographic origin point as a Geodetic3DPoint object.
	 * @throws NullPointerException if topoOrig is null
	 */
    public FrameOfReference(Geodetic3DPoint topoOrig) {
        this(DEFAULT_ELLIPSOID, topoOrig);
    }

    /**
     * This constructor uses the default WGS-84 Ellipsoid and the default
     * Topographic origin of (0&deg; N, 0&deg; E, 0m) for conversions.
     */
    public FrameOfReference() {
        this(DEFAULT_ELLIPSOID, DEFAULT_TOPOORIG);
    }

    /**
     * This accessor method is used to get the current Ellipsoid model of the
     * earth for this FrameOfReference.
     *
     * @return Ellipsoid object being used.
     */
    @NotNull
	public Ellipsoid getEllipsoid() {
        return ellip;
    }

    /**
     * This settor method is used to update the current Ellipsoid model of the
     * earth for this FrameOfReference.
     *
     * @param ellip the new Ellipsoid object to be used.
	 * @throws NullPointerException if ellip is null
     */
    public void setEllipsoid(Ellipsoid ellip) {
		if (ellip == null) throw new NullPointerException();
        this.ellip = ellip;
    }

    /**
     * This accessor method is used to get the current Topographic Origin point
     * (as a Geodetic3DPoint object) for this FrameOfReference.
     *
     * @return the topographic origin point as a Geodetic3DPoint object.
     */
    public Geodetic3DPoint getTopographicOrigin() {
        return topoOrigLle;
    }

    /**
     * This settor method is used to update the current Topographic Origin point
     * (as a Geodetic3DPoint object) for this FrameOfReference.
     *
     * @param topoOrig the new topographic origin point as a Geodetic3DPoint object.
	 * @throws NullPointerException if topoOrig is null
	 */
    public void setTopographicOrigin(Geodetic3DPoint topoOrig) {
        this.topoOrigLle = topoOrig;
        this.topoOrigEcf = this.toGeocentric(topoOrig);
    }

    /**
     * This method calculates the orthodromic distance in meters between two GeoPoints, which
     * is the shortest distance between two geodetic points on the surface of the ellipsoid
     * used by this FrameOfReference. Note that any elevation values are ignored as the
     * points are projected onto the ellipsoid at their geodetic (lon-lat) coordinates.
     *
     * @param p1 the starting GeoPoint.
     * @param p2 the ending GeoPoint.
     * @return the approximate distance in surface meters between the two specified points.
     */
    public double orthodromicDistance(GeoPoint p1, GeoPoint p2) {
        return ((p1 instanceof Geodetic2DPoint) && (p2 instanceof Geodetic2DPoint)) ?
                ellip.orthodromicDistance((Geodetic2DPoint) p1, (Geodetic2DPoint) p2) :
                ellip.orthodromicDistance(this.toGeodetic(p1), this.toGeodetic(p2));
    }

    /**
     * This method computes the shortest straight line distance (Euclidean Distance)
     * through space between the specified GeoPoints. Note that it may pass through the
     * earth.
     *
     * @param p1 the starting GeoPoint.
     * @param p2 the ending GeoPoint.
     * @return the shortest straight line distance between the two specified points.
     */
    public double euclideanDistance(GeoPoint p1, GeoPoint p2) {
        GeocentricPoint g1 = this.toGeocentric(p1);
        GeocentricPoint g2 = this.toGeocentric(p2);
        double xDiff = g1.getX() - g2.getX();
        double yDiff = g1.getY() - g2.getY();
        double zDiff = g1.getZ() - g2.getZ();
        return (Math.sqrt((xDiff * xDiff) + (yDiff * yDiff) + (zDiff * zDiff)));
    }

    /**
     * This predicate method is used to determine if two GeoPoints are close enough
     * in this FrameOfReference to be considered 'equal'.  The criteria is that they
     * are within a meter of each other on the surface of the Ellipsoid, and also
     * within a meter of each other in elevation.
     *
     * @param p1 the first GeoPoint to be compared.
     * @param p2 the second GeoPoint to be compared.
     * @return true if the two points are within a meter of each other on the surface and
     *         also in elevation.
     */
    public boolean proximallyEquals(GeoPoint p1, GeoPoint p2) {
        boolean closeEnough;
        if ((p1 instanceof Geodetic2DPoint) && (p2 instanceof Geodetic2DPoint)) {
            double dist = ellip.orthodromicDistance((Geodetic2DPoint) p1, (Geodetic2DPoint) p2);
            closeEnough = (dist < 1.0);
        } else {
            Geodetic3DPoint g1 = this.toGeodetic(p1);
            Geodetic3DPoint g2 = this.toGeodetic(p2);
            closeEnough = ((this.orthodromicDistance(g1, g2) < 1.0) &&
                    (Math.abs(g1.getElevation() - g2.getElevation()) < 1.0));
        }
        return closeEnough;
    }

    /**
     * This method is the public conversion method for using this FrameOfReference
     * to convert any GeoPoint object into a Geodetic3DPoint.
     *
     * @param gp the GeoPoint to convert.
     * @return the equivalent Geodetic3DPoint.
     * @throws IllegalArgumentException error if the GeoPoint instance is not recognized.
     */
    public Geodetic3DPoint toGeodetic(GeoPoint gp) {
        if (gp instanceof Geodetic3DPoint)
            return (Geodetic3DPoint) gp;
        else if ((gp instanceof Geodetic2DPoint) || (gp instanceof MGRS))
            return gp.toGeodetic3D(this);
        else if (gp instanceof GeocentricPoint)
            return this.toGeodetic((GeocentricPoint) gp);
        else if (gp instanceof Topocentric3DPoint)
            return this.toGeodetic((Topocentric3DPoint) gp);
        else if (gp instanceof Topocentric2DPoint)
            return this.toGeodetic(((Topocentric2DPoint) gp).toTopocentric3D());
        else throw new IllegalArgumentException("unknown GeoPoint instance");
    }

    /**
     * This method is the public conversion method for using this FrameOfReference
     * to convert any GeoPoint object into a GeocentricPoint.
     *
     * @param gp the GeoPoint to convert.
     * @return the equivalent GeocentricPoint.
     * @throws IllegalArgumentException error if the GeoPoint instance is not recognized.
     */
    public GeocentricPoint toGeocentric(GeoPoint gp) {
        if (gp instanceof GeocentricPoint)
            return (GeocentricPoint) gp;
        else if (gp instanceof Geodetic3DPoint)
            return this.toGeocentric((Geodetic3DPoint) gp);
        else if ((gp instanceof Geodetic2DPoint) || (gp instanceof MGRS))
            return this.toGeocentric(gp.toGeodetic3D(this));
        else if (gp instanceof Topocentric3DPoint)
            return this.toGeocentric((Topocentric3DPoint) gp);
        else if (gp instanceof Topocentric2DPoint)
            return this.toGeocentric(((Topocentric2DPoint) gp).toTopocentric3D());
        else throw new IllegalArgumentException("unknown GeoPoint instance");
    }

    /**
     * This method is the public conversion method for using this FrameOfReference
     * to convert any GeoPoint object into a Topocentric3DPoint.
     *
     * @param gp the GeoPoint to convert.
     * @return the equivalent Topocentric3DPoint.
     * @throws IllegalArgumentException error if the GeoPoint instance is not recognized.
     */
    public Topocentric3DPoint toTopocentric(GeoPoint gp) {
        if (gp instanceof Topocentric3DPoint)
            return (Topocentric3DPoint) gp;
        else if (gp instanceof Topocentric2DPoint)
            return ((Topocentric2DPoint) gp).toTopocentric3D();
        else if (gp instanceof Geodetic3DPoint)
            return this.toTopocentric((Geodetic3DPoint) gp);
        else if ((gp instanceof Geodetic2DPoint) || (gp instanceof MGRS))
            return this.toTopocentric(gp.toGeodetic3D(this));
        else if (gp instanceof GeocentricPoint)
            return this.toTopocentric((GeocentricPoint) gp);
        else throw new IllegalArgumentException("unknown GeoPoint instance");
    }
}
