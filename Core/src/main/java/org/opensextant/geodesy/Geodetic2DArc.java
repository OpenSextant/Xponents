/****************************************************************************************
 *  Geodetic2DArc.java
 *
 *  Created: Oct 31, 2007
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

import org.jetbrains.annotations.NotNull;

/**
 * The Geodetic2DArc class represents two geodetic points on the surface of an
 * Ellipsoid, the distance in meters on the surface between them, and the forward
 * azimuth (Angle from North to use as heading from point 1 to point 2). It includes
 * a constructor that takes the two points and then calculates the distance and
 * azimuth, as well as a constructor that takes the first point, distance, and
 * azimuth and computes the second point. There are variations that assume the
 * WGS-84 Ellipsoid and ones that allow the user to specify which Ellipsoid to use.
 * This code was adapted from the open source GeoTools project, having been
 * translated to Java from original Fortran published by NOAA.
 */
public class Geodetic2DArc implements Serializable, Comparable<Geodetic2DArc> {
	
    private static final long serialVersionUID = 1L;

    private static final Ellipsoid WGS84 = Ellipsoid.getInstance("WGS 84");
    private static final String NOCONVERGE = "Failed to converge for arc from ";

    // Tolerance factors from the strictest to the most relaxed
    private static final double TOLERANCE_0 = 5.0e-15,
            TOLERANCE_1 = 5.0e-14,
            TOLERANCE_2 = 5.0e-13,
            TOLERANCE_3 = 7.0e-3;

    @NotNull private final Ellipsoid ellip;
    @NotNull private final Geodetic2DPoint point1;
    @NotNull private Geodetic2DPoint point2;
    @NotNull private Angle forwardAzimuth;
    private double distanceInMeters;

    private double semiMajorAxis, semiMinorAxis, eccentricitySquared;
    private double f, fo;
    private double A, B, C, D, E, F;
    private double maxOrthodromicDistance;
    private double T1, T2, T4, T6;
    private double a01, a02, a03, a21, a22, a23, a42, a43, a63;

    /**
     * The default constructor creates a degenerate Geodetic2DArc with both
     * points at (0,0).
     */
    public Geodetic2DArc() {
        Geodetic2DPoint pt = new Geodetic2DPoint();
        this.ellip = WGS84;
        init();

        this.point1 = pt;
        setPoint2(pt); // updates state (distance and azimuth)
    }

    /**
     * This constructor assumes the WGS-84 Ellipsoid model of the earth, takes two geodetic
     * points, and computes the forward azimuth and distance in meters between them.
     *
     * @param point1 first Geodetic2DPoint (fixed)
     * @param point2 second Geodetic2DPoint (re-settable)
	 * @throws NullPointerException if point1 or point2 are null
     */
    public Geodetic2DArc(Geodetic2DPoint point1, Geodetic2DPoint point2) {
        this(WGS84, point1, point2);
    }

    /**
     * This constructor takes the Ellipsoid and two geodetic points, and computes the
     * forward azimuth and distance in meters between them.
     *
     * @param ellip  Ellipsoid model of the earth to use
     * @param point1 first Geodetic2DPoint (fixed)
     * @param point2 second Geodetic2DPoint (re-settable)
	 * @throws NullPointerException if ellip, point1 or point2 are null
     */
    public Geodetic2DArc(Ellipsoid ellip, Geodetic2DPoint point1, Geodetic2DPoint point2) {
        this.ellip = ellip;
        init();

        this.point1 = point1;
        setPoint2(point2); // updates state (distance and azimuth)
    }

    /**
     * This constructor assumes the WGS-84 Ellipsoid model of the earth, takes the first
     * geodetic point, an Angle of forward azimuth, and a distance in meters, and then
     * computes the second geodetic point that lies at that distance along that azimuth.
     *
     * @param point1           first Geodetic2DPoint (fixed)
     * @param forwardAzimuth   Angle from North of azimuth from point 1 to point 2
     * @param distanceInMeters double distance in meters on surface of Ellipsoid
     * @throws IllegalArgumentException if error in argument values is detected
     */
    public Geodetic2DArc(Geodetic2DPoint point1, double distanceInMeters,
                         Angle forwardAzimuth) {
        this(WGS84, point1, distanceInMeters, forwardAzimuth);
    }

    /**
     * This constructor takes the Ellipsoid model of the earth, the first geodetic point,
     * an Angle of forward azimuth, and a distance in meters, and then computes the second
     * geodetic point that lies at that distance along that azimuth.
     *
     * @param ellip            Ellipsoid model of the earth to use
     * @param point1           first Geodetic2DPoint (fixed)
     * @param distanceInMeters double distance in meters on surface of Ellipsoid
     * @param forwardAzimuth   Angle from North of azimuth from point 1 to point 2
     * @throws IllegalArgumentException if error in argument values is detected
	 * @throws NullPointerException if ellip, point1 or forwardAzimuth are null
     */
    public Geodetic2DArc(Ellipsoid ellip, Geodetic2DPoint point1, double distanceInMeters,
                         Angle forwardAzimuth) {
        this.ellip = ellip;
        init();

        this.point1 = point1;
        setDistanceAndAzimuth(distanceInMeters, forwardAzimuth); // updates state (point2)
    }

    // Initialization of parameters based on the Ellipsoid
    private void init() {
        semiMajorAxis = ellip.getEquatorialRadius();
        semiMinorAxis = ellip.getPolarRadius();
        eccentricitySquared = ellip.getEccentricitySquared();

        // Calculate needed parameters based on the ellipsoid
        f = ellip.getFlattening();
        fo = 1.0 - f;
        double f2 = f * f;
        double f3 = f * f2;
        double f4 = f * f3;

        final double E2 = eccentricitySquared;
        final double E4 = E2 * E2;
        final double E6 = E4 * E2;
        final double E8 = E6 * E2;
        final double EX = E8 * E2;

        A = 1.0 + 0.75 * E2 + 0.703125 * E4 + 0.68359375 * E6 + 0.67291259765625 * E8 + 0.6661834716796875 * EX;
        B = 0.75 * E2 + 0.9375 * E4 + 1.025390625 * E6 + 1.07666015625 * E8 + 1.1103057861328125 * EX;
        C = 0.234375 * E4 + 0.41015625 * E6 + 0.538330078125 * E8 + 0.63446044921875 * EX;
        D = 0.068359375 * E6 + 0.15380859375 * E8 + 0.23792266845703125 * EX;
        E = 0.01922607421875 * E8 + 0.0528717041015625 * EX;
        F = 0.00528717041015625 * EX;

        maxOrthodromicDistance = semiMajorAxis * (1.0 - E2) * Math.PI * A - 1.0;

        T1 = 1.0;
        T2 = -0.25 * f * (1.0 + f + f2);
        T4 = 0.1875 * f2 * (1.0 + 2.25 * f);
        T6 = 0.1953125 * f3;

        final double a = f3 * (1.0 + 2.25 * f);
        a01 = -f2 * (1.0 + f + f2) / 4.0;
        a02 = 0.1875 * a;
        a03 = -0.1953125 * f4;
        a21 = -a01;
        a22 = -0.25 * a;
        a23 = 0.29296875 * f4;
        a42 = 0.03125 * a;
        a43 = 0.05859375 * f4;
        a63 = 5.0 * f4 / 768.0;
    }

    /**
     * Calculate the meridian arc length between two points in the same meridian
     * in the referenced ellipsoid.
     *
     * @param P1 The latitude of the first  point (in radians).
     * @param P2 The latitude of the second point (in radians).
     * @return Returned the meridian arc length between P1 and P2
     */
    private double getMeridianArcLengthRadians(final double P1, final double P2) {
        /*
         * Latitudes P1 and P2 in radians positive North and East.
         * Forward azimuths at both points returned in radians from North.
         *
         * Source: ftp://ftp.ngs.noaa.gov/pub/pcsoft/for_inv.3d/source/inverse.for
         *         subroutine GPNARC
         *         version    200005.26
         *         written by Robert (Sid) Safford
         *
         * Ported from Fortran to Java by Daniele Franzoni.
         */
        double S1 = Math.abs(P1);
        double S2 = Math.abs(P2);
        double DA = (P2 - P1);
        // Check for a 90 degree lookup
        if (S1 > TOLERANCE_0 || S2 <= (Math.PI / 2 - TOLERANCE_0) || S2 >= (Math.PI / 2 + TOLERANCE_0)) {
            final double DB = Math.sin(P2 * 2.0) - Math.sin(P1 * 2.0);
            final double DC = Math.sin(P2 * 4.0) - Math.sin(P1 * 4.0);
            final double DD = Math.sin(P2 * 6.0) - Math.sin(P1 * 6.0);
            final double DE = Math.sin(P2 * 8.0) - Math.sin(P1 * 8.0);
            final double DF = Math.sin(P2 * 10.0) - Math.sin(P1 * 10.0);
            // Compute the S2 part of the series expansion
            S2 = -DB * B / 2.0 + DC * C / 4.0 - DD * D / 6.0 + DE * E / 8.0 - DF * F / 10.0;
        }
        // Compute the S1 part of the series expansion
        S1 = DA * A;
        // Compute the arc length
        return Math.abs(semiMajorAxis * (1.0 - eccentricitySquared) * (S1 + S2));
    }

    final float FACTOR_3div2 = (float)3/2;

    private void calcDistanceAndAzimuth() {
        final double lat1 = point1.getLatitude().inRadians();
        final double lat2 = point2.getLatitude().inRadians();

        Angle lon1 = point1.getLongitude();
        Angle lon2 = point2.getLongitude();
        final double dlon = lon1.difference(lon2).inRadians();

        /*
         * Solution of the geodetic inverse problem after T.Vincenty.
         * Modified Rainsford's method with Helmert's elliptical terms.
         * Effective in any azimuth and at any distance short of antipodal.
         *
         * Latitudes and longitudes in radians positive North and East.
         * Forward azimuths at both points returned in radians from North.
         *
         * Programmed for CDC-6600 by LCDR L.Pfeifer NGS ROCKVILLE MD 18FEB75
         * Modified for IBM SYSTEM 360 by John G.Gergen NGS ROCKVILLE MD 7507
         * Ported from Fortran to Java by Daniele Franzoni.
         *
         * Source: ftp://ftp.ngs.noaa.gov/pub/pcsoft/for_inv.3d/source/inverse.for
         *         subroutine GPNHRI
         *         version    200208.09
         *         written by robert (sid) safford
         */
        final double ss = Math.abs(dlon);
        if (ss < TOLERANCE_1) {
            distanceInMeters = getMeridianArcLengthRadians(lat1, lat2);
            forwardAzimuth = new Angle((lat2 > lat1) ? 0.0 : Math.PI);
            return;
        }
        /*
         * Compute the limit in longitude (alimit), it is equal
         * to twice  the distance from the equator to the pole,
         * as measured along the equator
         */
        //test for antinodal difference
        final double ESQP = eccentricitySquared / (1.0 - eccentricitySquared);
        final double alimit = Math.PI * fo;
        if (ss >= alimit &&
                lat1 < TOLERANCE_3 && lat1 > -TOLERANCE_3 &&
                lat2 < TOLERANCE_3 && lat2 > -TOLERANCE_3) {
            // Compute an approximate AZ
            final double CONS = (Math.PI - ss) / (Math.PI * f);
            double AZ = Math.asin(CONS);
            double AZ_TEMP, S, AO;
            int iter = 0;
            do {
                if (++iter > 8) {
                    String pts = point1 + " to " + point2;
                    throw new ArithmeticException(NOCONVERGE + pts);
                }
                S = Math.cos(AZ);
                final double C2 = S * S;
                // Compute new AO
                AO = T1 + T2 * C2 + T4 * C2 * C2 + T6 * C2 * C2 * C2;
                final double CS = CONS / AO;
                S = Math.asin(CS);
                AZ_TEMP = AZ;
                AZ = S;
            } while (Math.abs(S - AZ_TEMP) >= TOLERANCE_2);

            final double AZ1 = (dlon < 0.0) ? 2.0 * Math.PI - S : S;
            forwardAzimuth = new Angle(AZ1);
            // not sure what AZ2 is or why it is not used (could it be reverseAzimuth?)
            //final double AZ2 = 2.0 * Math.PI - AZ1;
            S = Math.cos(AZ1);

            // Equatorial - geodesic(S-s) SMS
            final double U2 = ESQP * S * S;
            final double U4 = U2 * U2;
            final double U6 = U4 * U2;
            final double U8 = U6 * U2;
            final double BO = 1.0 +
                    0.25 * U2 +
                    0.046875 * U4 +
                    0.01953125 * U6 +
                    -0.01068115234375 * U8;
            S = Math.sin(AZ1);
            final double SMS = semiMajorAxis * Math.PI * (1.0 - f * Math.abs(S) * AO - BO * fo);
            distanceInMeters = semiMajorAxis * ss - SMS;
            return;
        }

        // the reduced latitudes
        final double u1 = Math.atan(fo * Math.sin(lat1) / Math.cos(lat1));
        final double u2 = Math.atan(fo * Math.sin(lat2) / Math.cos(lat2));
        final double su1 = Math.sin(u1);
        final double cu1 = Math.cos(u1);
        final double su2 = Math.sin(u2);
        final double cu2 = Math.cos(u2);
        double xy, w, q2, q4, q6, r2, r3, sig, ssig, slon, clon, sinalf, ab = dlon;
        int kcount = 0;
        do {
            if (++kcount > 8) {
                String pts = point1 + " to " + point2;
                throw new ArithmeticException(NOCONVERGE + pts);
            }
            clon = Math.cos(ab);
            slon = Math.sin(ab);
            final double csig = su1 * su2 + cu1 * cu2 * clon;
            ssig = Math.sqrt(slon * cu2 * slon * cu2 + (su2 * cu1 - su1 * cu2 * clon) * (su2 * cu1 - su1 * cu2 * clon));
            sig = Math.atan2(ssig, csig);
            sinalf = cu1 * cu2 * slon / ssig;
            w = (1.0 - sinalf * sinalf);
            final double t4 = w * w;
            final double t6 = w * t4;

            // the coefficents of type a
            final double ao = f + a01 * w + a02 * t4 + a03 * t6;
            final double a2 = a21 * w + a22 * t4 + a23 * t6;
            final double a4 = a42 * t4 + a43 * t6;
            final double a6 = a63 * t6;

            // the multiple angle functions
            double qo = 0.0;
            if (w > TOLERANCE_0) {
                qo = -2.0 * su1 * su2 / w;
            }
            q2 = csig + qo;
            q4 = 2.0 * q2 * q2 - 1.0;
            q6 = q2 * (4.0 * q2 * q2 - 3.0);
            r2 = 2.0 * ssig * csig;
            r3 = ssig * (3.0 - 4.0 * ssig * ssig);

            // the longitude difference
            final double s = sinalf * (ao * sig + a2 * ssig * q2 + a4 * r2 * q4 + a6 * r3 * q6);
            double xz = dlon + s;
            xy = Math.abs(xz - ab);
            ab = dlon + s;
        } while (xy >= TOLERANCE_1);

        final double z = ESQP * w;
        final double bo = 1.0 + z * (1.0 / 4.0 + z * (-3.0 / 64.0 + z * (5.0 / 256.0 - z * (175.0 / 16384.0))));
        final double b2 = z * (-1.0 / 4.0 + z * (1.0 / 16.0 + z * (-15.0 / 512.0 + z * (35.0 / 2048.0))));
        final double b4 = z * z * (-1.0 / 128.0 + z * (3.0 / 512.0 - z * (35.0 / 8192.0)));
        final double b6 = z * z * z * (-1.0 / 1536.0 + z * (5.0 / 6144.0));

        // The distance in ellispoid axis units.
        distanceInMeters = semiMinorAxis * (bo * sig + b2 * ssig * q2 + b4 * r2 * q4 + b6 * r3 * q6);
        double az1 = (dlon < 0) ? Math.PI * FACTOR_3div2 : Math.PI / 2;

        // now compute the az1 & az2 for latitudes not on the equator
        if ((Math.abs(su1) >= TOLERANCE_0) || (Math.abs(su2) >= TOLERANCE_0)) {
            final double tana1 = slon * cu2 / (su2 * cu1 - clon * su1 * cu2);
            final double sina1 = sinalf / cu1;

            // azimuths from north, longitudes positive east
            az1 = Math.atan2(sina1, sina1 / tana1);
        }
        forwardAzimuth = new Angle(az1);
    }

    private void calcPoint2() {
        final double azimuth = forwardAzimuth.inRadians();

        final double lat1 = point1.getLatitude().inRadians();
        final double lon1 = point1.getLongitude().inRadians();
        final double lat2;
        final double lon2;

        /*
        * Solution of the geodetic direct problem after T.Vincenty.
        * Modified Rainsford's method with Helmert's elliptical terms.
        * Effective in any azimuth and at any distance short of antipodal.
        *
        * Latitudes and longitudes in radians positive North and East.
        * Forward azimuths at both points returned in radians from North.
        *
        * Programmed for CDC-6600 by LCDR L.Pfeifer NGS ROCKVILLE MD 18FEB75
        * Modified for IBM SYSTEM 360 by John G.Gergen NGS ROCKVILLE MD 7507
        * Ported from Fortran to Java by Daniele Franzoni.
        *
        * Source: ftp://ftp.ngs.noaa.gov/pub/pcsoft/for_inv.3d/source/forward.for
        *         subroutine DIRECT1
        */
        double TU = fo * Math.sin(lat1) / Math.cos(lat1);
        double SF = Math.sin(azimuth);
        double CF = Math.cos(azimuth);
        double BAZ = (CF != 0) ? Math.atan2(TU, CF) * 2.0 : 0;
        double CU = 1 / Math.sqrt(TU * TU + 1.0);
        double SU = TU * CU;
        double SA = CU * SF;
        double C2A = 1.0 - SA * SA;
        double X = Math.sqrt((1.0 / fo / fo - 1) * C2A + 1.0) + 1.0;
        X = (X - 2.0) / X;
        double C = 1.0 - X;
        C = (X * X / 4.0 + 1.0) / C;
        double D = (0.375 * X * X - 1.0) * X;
        TU = distanceInMeters / fo / semiMajorAxis / C;
        double Y = TU;
        double SY, CY, CZ, E;
        do {
            SY = Math.sin(Y);
            CY = Math.cos(Y);
            CZ = Math.cos(BAZ + Y);
            E = CZ * CZ * 2.0 - 1.0;
            C = Y;
            X = E * CY;
            Y = E + E - 1.0;
            Y = (((SY * SY * 4.0 - 3.0) * Y * CZ * D / 6.0 + X) * D / 4.0 - CZ) * SY * D + TU;
        } while (Math.abs(Y - C) > TOLERANCE_1);
        BAZ = CU * CY * CF - SU * SY;
        C = fo * Math.sqrt(SA * SA + BAZ * BAZ);
        D = SU * CY + CU * SY * CF;
        lat2 = Math.atan2(D, C);
        C = CU * CY - SU * SY * CF;
        X = Math.atan2(SY * SF, C);
        C = ((-3.0 * C2A + 4.0) * f + 4.0) * C2A * f / 16.0;
        D = ((E * CY * C + CZ) * SY * C + Y) * SA;
        lon2 = lon1 + X - (1.0 - C) * D * f;
        point2 = new Geodetic2DPoint(new Longitude(lon2), new Latitude(lat2));
    }

    // Valid range checker for distance in meters
    private void validateDistance(double distanceInMeters) {
        if (distanceInMeters < 0.0 || distanceInMeters > maxOrthodromicDistance)
            throw new IllegalArgumentException("Distance is out of legal range (0 .. " +
                    maxOrthodromicDistance + ")");
    }

    /**
     * This method returns the fixed geodetic point 1 of this Geodetic2DArc object.
     *
     * @return Geodetic2DPoint point 1 (fixed)
     */
	@NotNull
    public Geodetic2DPoint getPoint1() {
        return point1;
    }

    /**
     * This method returns the re-settable geodetic point 2 of this Geodetic2DArc object.
     *
     * @return Geodetic2DPoint point 1 (re-settable)
     */
	@NotNull
    public Geodetic2DPoint getPoint2() {
        return point2;
    }

    /**
     * This method is used to re-set the second geodetic point of this Geodetic2DArc
     * object, resulting in the recalculation of forward azimuth and distance.
     *
     * @param point2 second Geodetic2DPoint (re-settable)
	 * @throws NullPointerException if point2 is null
     */
    public void setPoint2(Geodetic2DPoint point2) {
        this.point2 = point2;
        calcDistanceAndAzimuth();
    }

    /**
     * This method returns the Angle of forward azimuth from point 1 and point 2.
     *
     * @return Angle from North of forward azimuth (shortest distance direction from point1)
     */
	@NotNull
    public Angle getForwardAzimuth() {
        return forwardAzimuth;
    }

    /**
     * This method is used to set the Angle of forward azimuth from point 1, resulting in
     * point 2 to be recalculated at the current distance in meters.
     *
     * @param forwardAzimuth Angle from North of forward azimuth
	 * @throws NullPointerException if forwardAzimuth is null
     */
    public void setForwardAzimuth(Angle forwardAzimuth) {
        this.forwardAzimuth = forwardAzimuth;
        calcPoint2();
    }

    /**
     * This method returns the shortest distance in meters between the geodetic points
     * 1 and 2 of this Geodetic2DArc object.
     *
     * @return double shortest distance in meters on Ellipsoid between points 1 and 2
     */
    public double getDistanceInMeters() {
        return distanceInMeters;
    }

    /**
     * This method is used to set a new distance in meters for this Geodetic2DArc object,
     * resulting in the calculation of a new second point.
     *
     * @param distanceInMeters double distance from point 1 to new point 2
     * @throws IllegalArgumentException if distance is out of legal range
     */
    public void setDistanceInMeters(double distanceInMeters) {
        validateDistance(distanceInMeters);
        this.distanceInMeters = distanceInMeters;
        calcPoint2();
    }

    /**
     * This method is used to simultaneously set a new distance in meters and forward
     * azimuth for this Geodetic2DArc object, resulting in the calculation of a new second
     * point.
     *
     * @param distanceInMeters double distance from point 1 to new point 2
     * @param forwardAzimuth   Angle from North of forward azimuth
     * @throws IllegalArgumentException if distance is out of legal range
	 * @throws NullPointerException if forwardAzimuth is null
     */
    public void setDistanceAndAzimuth(double distanceInMeters, Angle forwardAzimuth)
            throws IllegalArgumentException {
        validateDistance(distanceInMeters);
        this.distanceInMeters = distanceInMeters;
        this.forwardAzimuth = forwardAzimuth;
        calcPoint2();
    }

    /**
     * Compares this Geodetic2DArc with the specified Geodetic2DArc for order. Returns a
     * negative integer, zero, or a positive integer as this arc's length is less than,
     * equal to, or greater than the length of the specified arc.
     *
     * @param that Geodetic2DArc to compare lengths with this one
     * @return a negative integer, zero, or a positive integer as this object is less than,
     *         equal to, or greater than the specified object.
	 * @throws NullPointerException if that is null
     */
    public int compareTo(Geodetic2DArc that) {
        Double thisLen = this.getDistanceInMeters();
        Double thatLen = that.getDistanceInMeters();
        return thisLen.compareTo(thatLen);
    }

    /**
     * This method returns a hash code for this Geodetic2DArc object.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return 31 * point1.hashCode() + point2.hashCode();
    }

    /**
     * This method is used to compare this arc to another Geodetic2DArc object.
     *
     * @param arc Geodetic2DArc to compare to this one
     * @return boolean indicating whether this arc is equal to the specified arc
     */
    public boolean equals(Geodetic2DArc arc) {
        return arc != null && point1.equals(arc.getPoint1()) && point2.equals(arc.getPoint2());
    }

    /**
     * This method is used to compare this arc to another Geodetic2DArc object.
     *
     * @param that Geodetic2DArc to compare to this one
     * @return boolean indicating whether this arc is equal to the specified object
     */
    @Override
    public boolean equals(Object that) {
        return that instanceof Geodetic2DArc && equals((Geodetic2DArc) that);
    }

    /**
     * The toString method formats the arc for printing.
     *
     * @return String representation of points, distance, and heading
     */
    @Override
    public String toString() {
        return "(" + point2 + " is " + distanceInMeters + "m from " +
                point1 + " at heading " + forwardAzimuth + ")";
    }
}
