/****************************************************************************************
 *  TransverseMercator.java
 *
 *  Created: Dec 21, 2006
 *
 *  @author Paul Silvey (Re-written in Java based closely on GeoTrans 2.4 C code)
 *
 *  TRANSVERSE MERCATOR originated from :
 *      U.S. Army Topographic Engineering Center
 *      Geospatial Information Division
 *      7701 Telegraph Road
 *      Alexandria, VA  22310-3864
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The TransverseMercator class implements a map projection of the same name, and is used by
 * the Universal Transverse Mercator (UTM) coordinate encoding system for points in the middle
 * Latitudes around the globe.
 */
public class TransverseMercator implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(TransverseMercator.class);

    // Constants
    private static final double PI_OVER_2 = Math.PI / 2.0;
    private static final double MAX_LAT = Math.toRadians(89.99);
    private static final double MAX_DELTA_LON = Math.toRadians(9.0);
    private static final double MIN_EASTING = -40000000.0;
    private static final double MAX_EASTING = 40000000.0;
    private static final double MIN_NORTHING = -20000000.0;
    private static final double MAX_NORTHING = 20000000.0;
    private static final double MIN_SCALE = 0.3;
    private static final double MAX_SCALE = 3.0;
    private static final String DISTORTION_WARNING = "Distortion will result if Longitude is " +
            "more than 9 degrees from the Central Meridian";

    // Instance variables
    private final boolean distortionWarningException;

    @NotNull private Ellipsoid ellipsoid = Ellipsoid.getInstance("WGS 84");

    // Ellipsoid Parameters, default to WGS 84
    private double TranMerc_a = 6378137.0;              // Semi-major axis of ellipsoid in meters
    private double TranMerc_es = 0.0066943799901413800; // Eccentricity (0.08181919084262188000) squared
    private double TranMerc_ebs = 0.0067394967565869;   // Second Eccentricity squared

    // Isometeric to geodetic latitude parameters, default to WGS 84
    private double TranMerc_ap = 6367449.1458008;
    private double TranMerc_bp = 16038.508696861;
    private double TranMerc_cp = 16.832613334334;
    private double TranMerc_dp = 0.021984404273757;
    private double TranMerc_ep = 3.1148371319283e-005;

    // Transverse_Mercator projection Parameters
    private Longitude centralMeridian;                  // Central Meridian longitude (in radians)
    private double originLatitude;                      // Latitude (in radians) touching projection plane
    private double scale;                               // Scale factor

    // Utiltiy Functions
    private double SPHTMD(double lat) {
        return (TranMerc_ap * lat
                - TranMerc_bp * Math.sin(2.e0 * lat) + TranMerc_cp * Math.sin(4.e0 * lat)
                - TranMerc_dp * Math.sin(6.e0 * lat) + TranMerc_ep * Math.sin(8.e0 * lat));
    }

    private double SPHSN(double lat) {
        return (TranMerc_a / Math.sqrt(1.e0 - TranMerc_es * Math.pow(Math.sin(lat), 2.0)));
    }

    private double DENOM(double lat) {
        return (Math.sqrt(1.e0 - TranMerc_es * Math.pow(Math.sin(lat), 2.0)));
    }

    private double SPHSR(double lat) {
        return (TranMerc_a * (1.e0 - TranMerc_es) / Math.pow(DENOM(lat), 3.0));
    }

    /**
     * This constructor makes an instance of TransverseMercator (initialized with WGS84 ellipsoid
     * projection centered on Prime Meridian and the Equator).  These can be changed using
     * the setEllipsoid, setCentralMeridian, setOriginLatitude, and setScaleFactor methods.
     *
     * @param distortionWarningException should an exception be thrown for > 9&deg; diff from CM?
     */
    public TransverseMercator(boolean distortionWarningException) {
        this.distortionWarningException = distortionWarningException;
        setCentralMeridian(new Longitude(0.0));
        setOriginLatitude(new Latitude(0.0));
        setScaleFactor(1.0);
    }

    /**
     * This method is used to set the Ellipsoid model parameters.  This is only needed if
     * the default WGS84 ellipsoid is not being used, since WGS84 values are the default.
     *
     * @param ellip Ellipsoid object to use
	 * @throws NullPointerException if ellip is null
     */
    public void setEllipsoid(Ellipsoid ellip) {
        // Only update parameters if ellip has changed
        if (ellipsoid != ellip) {
            ellipsoid = ellip;
            TranMerc_a = ellipsoid.getEquatorialRadius();         // a, Semi-major axis of ellipsoid, in meters
            double TranMerc_b = ellipsoid.getPolarRadius();       // b, Semi-minor axis of ellipsoid, in meters

            TranMerc_es = ellipsoid.getEccentricitySquared();
            TranMerc_ebs = ellipsoid.get2ndEccentricitySquared();

            // True meridianal constants
            double tn = (TranMerc_a - TranMerc_b) / (TranMerc_a + TranMerc_b);
            double tn2 = tn * tn;
            double tn3 = tn2 * tn;
            double tn4 = tn3 * tn;
            double tn5 = tn4 * tn;

            TranMerc_ap = TranMerc_a * (1.e0 - tn + 5.e0 * (tn2 - tn3) / 4.e0 + 81.e0 * (tn4 - tn5) / 64.e0);
            TranMerc_bp = 3.e0 * TranMerc_a * (tn - tn2 + 7.e0 * (tn3 - tn4) / 8.e0 + 55.e0 * tn5 / 64.e0) / 2.e0;
            TranMerc_cp = 15.e0 * TranMerc_a * (tn2 - tn3 + 3.e0 * (tn4 - tn5) / 4.e0) / 16.0;
            TranMerc_dp = 35.e0 * TranMerc_a * (tn3 - tn4 + 11.e0 * tn5 / 16.e0) / 48.e0;
            TranMerc_ep = 315.e0 * TranMerc_a * (tn4 - tn5) / 512.e0;
        }
    }

    /**
     * This method will return the currently set Ellipsoid for this TransverseMercator instance.
     *
     * @return ellipsoid model of the earth currently being used by this TransverseMercator object
     */
	@NotNull
    public Ellipsoid getEllipsoid() {
        return ellipsoid;
    }

    /**
     * This method is used to set the origin longitude (point where tangential plane
     * touches ellipsoid) for this TransverseMercator projection object.
     *
     * @param centralMeridian - longitude at which tangential plane touches ellipsoid
     */
    public void setCentralMeridian(Longitude centralMeridian) {
        this.centralMeridian = centralMeridian;
    }

    /**
     * This method is used to set the origin latitude (point where tangential plane
     * touches ellipsoid) for this TransverseMercator projection object.
     *
     * @param originLatitude latitude at which tangential plane touches ellipsoid
	 * @throws NullPointerException if originLatitude is null
     */
    public void setOriginLatitude(Latitude originLatitude) {
        this.originLatitude = originLatitude.inRadians();
    }

    /**
     * This method is used to set the scale factor for this TransverseMercator projection object.
     *
     * @param scale scaling factor (0.3 .. 3.0)
     * @throws IllegalArgumentException error if scale is out of legal range
     */
    public void setScaleFactor(double scale) {
        if ((scale < MIN_SCALE) || (MAX_SCALE < scale))
            throw new IllegalArgumentException("Scale factor outside of valid range (0.3 to 3.0)");
        this.scale = scale;
    }

    /**
     * This method converts geodetic (latitude and longitude) coordinates
     * to Transverse Mercator projection (easting and northing) coordinates, according
     * to the current ellipsoid and Transverse Mercator projection coordinates.
     *
     * @param lon Longitude to project
     * @param lat Latitude to project
     * @return Topocentric2DPoint object containing Easting (x coord) and Northing (y coord) in meters
     * @throws IllegalArgumentException error if longitude too far away from origin's central meridian
	 * @throws NullPointerException if lon or lat are null
     */
    public Topocentric2DPoint toTransverseMercator(Longitude lon, Latitude lat)
            throws IllegalArgumentException {

        double latitude = lat.inRadians();
        if (Math.abs(latitude) > MAX_LAT)
            throw new IllegalArgumentException("Latitude is too close to a Pole");

        // Delta Longitude
        double dlam = centralMeridian.difference(lon).inRadians();

        // Invalid longitude if greater than 90 degrees from central meridian
        if (Math.abs(dlam) > PI_OVER_2)
            throw new IllegalArgumentException("Longitude is more than 90 deg from central meridian");

        // Warn if distortion will occur (when Longitude is more than 9 degrees from the Central Meridian)
        if (Math.abs(dlam) > MAX_DELTA_LON) {
            if (distortionWarningException) throw new IllegalArgumentException(DISTORTION_WARNING);
            else log.debug(DISTORTION_WARNING);
        }

        // Recognize near zero delta condition and correct
        if (Math.abs(dlam) < 2.e-10) dlam = 0.0;

        double c;       /* Cosine of latitude                              */
        double c2;
        double c3;
        double c5;
        double c7;
        double eta;     /* constant - TranMerc_ebs *c *c                   */
        double eta2;
        double eta3;
        double eta4;
        double s;       /* Sine of latitude                                */
        double sn;      /* Radius of curvature in the prime vertical       */
        double t;       /* Tangent of latitude                             */
        double tan2;
        double tan3;
        double tan4;
        double tan5;
        double tan6;
        double t1;      /* Term in coordinate conversion formula - GP to Y */
        double t2;      /* Term in coordinate conversion formula - GP to Y */
        double t3;      /* Term in coordinate conversion formula - GP to Y */
        double t4;      /* Term in coordinate conversion formula - GP to Y */
        double t5;      /* Term in coordinate conversion formula - GP to Y */
        double t6;      /* Term in coordinate conversion formula - GP to Y */
        double t7;      /* Term in coordinate conversion formula - GP to Y */
        double t8;      /* Term in coordinate conversion formula - GP to Y */
        double t9;      /* Term in coordinate conversion formula - GP to Y */
        double tmd;     /* True Meridional distance                        */
        double tmdo;    /* True Meridional distance for latitude of origin */

        s = Math.sin(latitude);
        c = Math.cos(latitude);
        c2 = c * c;
        c3 = c2 * c;
        c5 = c3 * c2;
        c7 = c5 * c2;
        t = Math.tan(latitude);
        tan2 = t * t;
        tan3 = tan2 * t;
        tan4 = tan3 * t;
        tan5 = tan4 * t;
        tan6 = tan5 * t;
        eta = TranMerc_ebs * c2;
        eta2 = eta * eta;
        eta3 = eta2 * eta;
        eta4 = eta3 * eta;

        /* radius of curvature in prime vertical */
        sn = SPHSN(latitude);

        /* True Meridianal Distances */
        tmd = SPHTMD(latitude);

        /*  Origin  */
        tmdo = SPHTMD(originLatitude);

        /* Northing */
        t1 = (tmd - tmdo) * scale;
        t2 = sn * s * c * scale / 2.e0;
        t3 = sn * s * c3 * scale * (5.e0 - tan2 + 9.e0 * eta
                + 4.e0 * eta2) / 24.e0;

        t4 = sn * s * c5 * scale * (61.e0 - 58.e0 * tan2
                + tan4 + 270.e0 * eta - 330.e0 * tan2 * eta + 445.e0 * eta2
                + 324.e0 * eta3 - 680.e0 * tan2 * eta2 + 88.e0 * eta4
                - 600.e0 * tan2 * eta3 - 192.e0 * tan2 * eta4) / 720.e0;

        t5 = sn * s * c7 * scale * (1385.e0 - 3111.e0 *
                tan2 + 543.e0 * tan4 - tan6) / 40320.e0;

        double northing = t1 + Math.pow(dlam, 2.e0) * t2
                + Math.pow(dlam, 4.e0) * t3 + Math.pow(dlam, 6.e0) * t4
                + Math.pow(dlam, 8.e0) * t5;

        /* Easting */
        t6 = sn * c * scale;
        t7 = sn * c3 * scale * (1.e0 - tan2 + eta) / 6.e0;
        t8 = sn * c5 * scale * (5.e0 - 18.e0 * tan2 + tan4
                + 14.e0 * eta - 58.e0 * tan2 * eta + 13.e0 * eta2 + 4.e0 * eta3
                - 64.e0 * tan2 * eta2 - 24.e0 * tan2 * eta3) / 120.e0;
        t9 = sn * c7 * scale * (61.e0 - 479.e0 * tan2
                + 179.e0 * tan4 - tan6) / 5040.e0;

        double easting = dlam * t6 + Math.pow(dlam, 3.e0) * t7
                + Math.pow(dlam, 5.e0) * t8 + Math.pow(dlam, 7.e0) * t9;

        return (new Topocentric2DPoint(easting, northing));
    }

    /**
     * This method converts Transverse Mercator projection (easting and northing)
     * coordinates to geodetic (latitude and longitude) coordinates, according to the current
     * ellipsoid and Transverse Mercator projection parameters.
     *
     * @param easting  X in meters
     * @param northing Y in meters
     * @return Geodetic2DPoint - object containing un-projected Longitude and Latitude
     * @throws IllegalArgumentException error if Easting or Northing are out of legal range
     */
    public Geodetic2DPoint toGeodetic(double easting, double northing)
            throws IllegalArgumentException {
        // Test to make sure signed Easting and Northing values are within valid range
        // Note: These limits are based on approximate 40,000 km earth circumference
        if ((easting < MIN_EASTING) || (MAX_EASTING < easting))
            throw new IllegalArgumentException
                    ("Easting value is out of legal range (-10,000,000 .. +10,000,000 m)");
        if ((northing < MIN_NORTHING) || (MAX_NORTHING < northing))
            throw new IllegalArgumentException
                    ("Northing value is out of legal range (-5,000,000 .. +5,000,000 m)");

        double c;       /* Cosine of latitude                                   */
        double de;      /* Delta easting - Difference in Easting (Easting-Fe)   */
        double dlam;    /* Delta longitude - Difference in Longitude            */
        double eta;     /* constant - TranMerc_ebs *c *c                        */
        double eta2;
        double eta3;
        double eta4;
        double ftphi;   /* Footpoint latitude                                   */
        double sn;      /* Radius of curvature in the prime vertical            */
        double sr;      /* Radius of curvature in the meridian                  */
        double t;       /* Tangent of latitude                                  */
        double tan2;
        double tan4;
        double t10;     /* Term in coordinate conversion formula - GP to Y      */
        double t11;     /* Term in coordinate conversion formula - GP to Y      */
        double t12;     /* Term in coordinate conversion formula - GP to Y      */
        double t13;     /* Term in coordinate conversion formula - GP to Y      */
        double t14;     /* Term in coordinate conversion formula - GP to Y      */
        double t15;     /* Term in coordinate conversion formula - GP to Y      */
        double t16;     /* Term in coordinate conversion formula - GP to Y      */
        double t17;     /* Term in coordinate conversion formula - GP to Y      */
        double tmd;     /* True Meridional distance                             */
        double tmdo;    /* True Meridional distance for latitude of origin      */

        /* True Meridinal Distances for latitude of origin */
        tmdo = SPHTMD(originLatitude);

        /*  Origin  */
        tmd = tmdo + northing / scale;

        /* First Estimate */
        sr = SPHSR(0.e0);
        ftphi = tmd / sr;

        for (int i = 0; i < 5; i++) {
            t10 = SPHTMD(ftphi);
            sr = SPHSR(ftphi);
            ftphi = ftphi + (tmd - t10) / sr;
        }

        /* Radius of Curvature in the meridian */
        sr = SPHSR(ftphi);

        /* Radius of Curvature in the meridian */
        sn = SPHSN(ftphi);

        /* Sine Cosine terms */
        c = Math.cos(ftphi);

        /* Tangent Value  */
        t = Math.tan(ftphi);
        tan2 = t * t;
        tan4 = tan2 * tan2;
        eta = TranMerc_ebs * Math.pow(c, 2);
        eta2 = eta * eta;
        eta3 = eta2 * eta;
        eta4 = eta3 * eta;
        de = easting;

        /* Latitude */
        t10 = t / (2.e0 * sr * sn * Math.pow(scale, 2));
        t11 = t * (5.e0 + 3.e0 * tan2 + eta - 4.e0 * Math.pow(eta, 2)
                - 9.e0 * tan2 * eta) / (24.e0 * sr * Math.pow(sn, 3)
                * Math.pow(scale, 4));
        t12 = t * (61.e0 + 90.e0 * tan2 + 46.e0 * eta + 45.E0 * tan4
                - 252.e0 * tan2 * eta - 3.e0 * eta2 + 100.e0
                * eta3 - 66.e0 * tan2 * eta2 - 90.e0 * tan4
                * eta + 88.e0 * eta4 + 225.e0 * tan4 * eta2
                + 84.e0 * tan2 * eta3 - 192.e0 * tan2 * eta4)
                / (720.e0 * sr * Math.pow(sn, 5) * Math.pow(scale, 6));
        t13 = t * (1385.e0 + 3633.e0 * tan2 + 4095.e0 * tan4 + 1575.e0
                * Math.pow(t, 6)) / (40320.e0 * sr * Math.pow(sn, 7) * Math.pow(scale, 8));

        // Round value to be accurate to nearest meter in northing
        double latRad = ftphi - Math.pow(de, 2) * t10 + Math.pow(de, 4) *
                t11 - Math.pow(de, 6) * t12 + Math.pow(de, 8) * t13;
        Latitude lat = new Latitude(latRad); // vs. Math.round(latRad * 1e+7) * 1e-7

        t14 = 1.e0 / (sn * c * scale);

        t15 = (1.e0 + 2.e0 * tan2 + eta) / (6.e0 * Math.pow(sn, 3) * c * Math.pow(scale, 3));

        t16 = (5.e0 + 6.e0 * eta + 28.e0 * tan2 - 3.e0 * eta2
                + 8.e0 * tan2 * eta + 24.e0 * tan4 - 4.e0
                * eta3 + 4.e0 * tan2 * eta2 + 24.e0
                * tan2 * eta3) / (120.e0 * Math.pow(sn, 5) * c
                * Math.pow(scale, 5));

        t17 = (61.e0 + 662.e0 * tan2 + 1320.e0 * tan4 + 720.e0
                * Math.pow(t, 6)) / (5040.e0 * Math.pow(sn, 7) * c
                * Math.pow(scale, 7));
        /* Difference in Longitude */
        dlam = de * t14 - Math.pow(de, 3) * t15 + Math.pow(de, 5) * t16 - Math.pow(de, 7) * t17;
        if (Math.abs(dlam) > MAX_DELTA_LON) {
            if (distortionWarningException) throw new IllegalArgumentException(DISTORTION_WARNING);
            else log.debug(DISTORTION_WARNING);
        }

        /* Longitude */
        // Round value to be accurate to nearest meter in easting
        double lonRad = centralMeridian.inRadians() + dlam;
        Longitude lon = new Longitude(lonRad); // vs. Math.round(lonRad * 1e+7) * 1e-7

        return (new Geodetic2DPoint(lon, lat));
    }
}
