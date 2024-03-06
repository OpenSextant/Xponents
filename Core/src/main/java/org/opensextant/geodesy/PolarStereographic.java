/****************************************************************************************
 *  PolarStereographic.java
 *
 *  Created: Jan 18, 2007
 *
 *  @author Paul Silvey (Re-written in Java based closely on GeoTrans 2.4 C code)
 *
 *  POLAR STEREOGRAPHIC originated from :
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

/**
 * The PolarStereographic class implements a map projection of the same name, and is used by
 * the Universal Polar Stereographic (UPS) coordinate encoding system for points near the
 * North or South Poles.
 *
 * TODO: implement equals() and hashCode() methods.
 */
public class PolarStereographic implements Serializable {
	private static final long serialVersionUID = 1L;
	
	// Constants
    private static final double PI_OVER_2 = Math.PI / 2.0;
    private static final double PI_OVER_4 = Math.PI / 4.0;
    private static final double EPSILON = 1.0e-2;

    // Instance variables
    @NotNull private Ellipsoid ellipsoid = Ellipsoid.getInstance("WGS 84");

    // Ellipsoid Parameters, default to WGS 84
    private double Polar_a = 6378137.0;                 // Semi-major axis of ellipsoid in meters
    private double Polar_e = 0.08181919084262188000;    // Eccentricity of ellipsoid
    private double Polar_eh = .040909595421311;         // Eccentricity over 2.0 (e half)
    private double Polar_2a = 12756274.0;               // 2.0 * Polar_a Semi-major axis

    private double Polar_tc = 1.0;
    private double Polar_e4 = 1.0033565552493;
    private double Polar_a_mc = 6378137.0;              // Polar_a * mc

    // Polar Stereographic projection Parameters
    private double Polar_Origin_Lat = PI_OVER_2;        // Latitude of true scale (init at N Pole)
    private final boolean Southern_Hemisphere;          // Depends on sign of latitude of true scale at construction
    private boolean True_Scale_At_Pole = true;          // Calculation method differs when true scale is at pole
    private double Polar_Origin_Long = 0.0;             // Longitude down from pole (northings decrease along)

    // Maximum variance for easting and northing values for WGS 84.
    private double Polar_Delta_Easting = 12713601.0;
    private double Polar_Delta_Northing = 12713601.0;

    // Utiltiy Function
    private double POLAR_POW(double esSin) {
        return (Math.pow((1.0 - esSin) / (1.0 + esSin), Polar_eh));
    }

    // Initialization method to update derivative parameters when ellipsoid or origin has changed
    private void init() {
        True_Scale_At_Pole = Math.abs(Math.abs(Polar_Origin_Lat) - PI_OVER_2) <= 1.0e-10;
        if (True_Scale_At_Pole) {
            double ope = 1.0 + Polar_e;    // One Plus Eccentricity
            double ome = 1.0 - Polar_e;    // One Minus Eccentricity
            Polar_e4 = Math.sqrt(Math.pow(ope, ope) * Math.pow(ome, ome));
        } else {
            double esSin = Polar_e * Math.sin(Polar_Origin_Lat);
            double mc = Math.cos(Polar_Origin_Lat) / Math.sqrt(1.0 - (esSin * esSin));
            Polar_a_mc = Polar_a * mc;
            Polar_tc = Math.tan(PI_OVER_4 - (Polar_Origin_Lat / 2.0)) / POLAR_POW(esSin);
        }
        Topocentric2DPoint en =
                toPolarStereographic(new Longitude(Polar_Origin_Long), new Latitude(0.0));
        Polar_Delta_Northing = Math.abs(en.getNorthing()) + EPSILON;
        Polar_Delta_Easting = Polar_Delta_Northing;
    }

    /**
     * This constructor makes an instance of PolarStereographic (initialized with WGS84 ellipsoid,
     * origin at North Pole Prime Meridian).  Ellipsoid can be changed with call to setEllipsoid.
     *
     * @param latOfTrueScale Latitude of True Scale (sign determines hemisphere of projection)
     * @throws NullPointerException if latOfTrueScale is null
     */
    public PolarStereographic(Latitude latOfTrueScale) {
        Polar_Origin_Lat = latOfTrueScale.inRadians;
        Southern_Hemisphere = (latOfTrueScale.inRadians < 0.0);
        if (Southern_Hemisphere) {
            Polar_Origin_Lat = -Polar_Origin_Lat;
            Polar_Origin_Long = -Polar_Origin_Long;
        }
        init();
    }

    /**
     * This method is used to set the Ellipsoid model parameters.  This is only needed if
     * the default WGS84 ellipsoid is not being used, since WGS84 values are the default.
     *
     * @param ellip Ellipsoid object to use
     * @throws NullPointerException if ellip is null
     */
    public void setEllipsoid(Ellipsoid ellip) {
        // Only update ellipsoid and other parameters if ellip has changed
        if (ellipsoid != ellip) {
            ellipsoid = ellip;

            Polar_a = ellip.getEquatorialRadius();
            Polar_e = ellip.getEccentricity();

            Polar_eh = Polar_e / 2.0;
            Polar_2a = Polar_a * 2.0;
            init();
        }
    }

    /**
     * This method will return the currently set Ellipsoid for this PolarStereographic instance.
     *
     * @return ellipsoid model of the earth currently being used by this PolarStereographic object
     */
    @NotNull
    public Ellipsoid getEllipsoid() {
        return ellipsoid;
    }

    /**
     * This method converts geodetic (latitude and longitude) coordinates
     * to Polar Stereographic projection (easting and northing) coordinates, according
     * to the current ellipsoid and Polar Stereographic projection coordinates.
     *
     * @param lon Longitude to project
     * @param lat Latitude to project
     * @return Topocentric2DPoint object containing Easting (x coord) and Northing (y coord) in meters
     * @throws IllegalArgumentException error if latitude is in different hemisphere from origin
     */
    @NotNull
    public Topocentric2DPoint toPolarStereographic(Longitude lon, Latitude lat)
            throws IllegalArgumentException {

        double latRad = lat.inRadians;
        double lonRad = lon.inRadians;
        if ((latRad < 0.0 && !Southern_Hemisphere) || (latRad > 0.0 && Southern_Hemisphere)) {
            throw new IllegalArgumentException("Latitude in different hemisphere " +
                    "from Polar Stereographic origin");
        }
        double easting = 0.0;
        double northing = 0.0;
        // Don't project if we're sufficiently near a pole
        if (Math.abs(Math.abs(latRad) - PI_OVER_2) >= 1.0e-10) {
            if (Southern_Hemisphere) {
                lonRad *= -1.0;
                latRad *= -1.0;
            }

            double t = Math.tan(PI_OVER_4 - (latRad / 2.0)) / POLAR_POW(Polar_e * Math.sin(latRad));
            // Compute differently if latitude of true scale is sufficiently away from pole
            double rho = (True_Scale_At_Pole) ? (Polar_2a * t / Polar_e4) : (Polar_a_mc * t / Polar_tc);
            double dlam = Angle.normalize(lonRad - Polar_Origin_Long);
            easting = rho * Math.sin(dlam);
            northing = -rho * Math.cos(dlam);

            if (Southern_Hemisphere) {
                easting *= -1.0;
                northing *= -1.0;
            }
        }
        return new Topocentric2DPoint(easting, northing);
    }

    /**
     * This method converts Polar Stereographic projection (easting and northing)
     * coordinates to geodetic (latitude and longitude) coordinates, according to the current
     * ellipsoid and Polar Stereographic projection parameters.
     *
     * @param easting  X in meters
     * @param northing Y in meters
     * @return Geodetic2DPoint object containing un-projected Longitude and Latitude
     * @throws IllegalArgumentException error if Easting or Northing are out of legal range
     */
    @NotNull
    public Geodetic2DPoint toGeodetic(double easting, double northing) {
        double lonRad;
        double latRad;

        if ((easting < -Polar_Delta_Easting) || (Polar_Delta_Easting < easting)) {
            throw new IllegalArgumentException("Polar Stereographic Easting value is out of range");
        }
        if ((northing < -Polar_Delta_Northing) || (Polar_Delta_Northing < northing)) {
            throw new IllegalArgumentException("Polar Stereographic Northing value is out of range");

        }
        double temp = Math.sqrt((easting * easting) + (northing * northing));
        if ((temp < -Polar_Delta_Easting) || (Polar_Delta_Easting < temp) ||
                (temp < -Polar_Delta_Northing) || (Polar_Delta_Northing < temp)) {
            throw new IllegalArgumentException("Polar Stereographic point is outside of projection area");
        }

        if ((northing == 0.0) && (easting == 0.0)) {
            latRad = PI_OVER_2;
            lonRad = Polar_Origin_Long;
        } else {
            if (Southern_Hemisphere) {
                northing *= -1.0;
                easting *= -1.0;
            }
            double rho = Math.sqrt((easting * easting) + (northing * northing));
            // Compute differently if latitude of true scale is sufficiently away from pole
            double t = rho * ((True_Scale_At_Pole) ? (Polar_e4 / Polar_2a) : (Polar_tc / Polar_a_mc));
            double Phi = PI_OVER_2 - (2.0 * Math.atan(t));
            double tempPhi = 0.0;
            while (Math.abs(Phi - tempPhi) > 1.0e-10) {
                tempPhi = Phi;
                Phi = PI_OVER_2 - (2.0 * Math.atan(t * POLAR_POW(Polar_e * Math.sin(Phi))));
            }
            latRad = Phi;
            lonRad = Angle.normalize(Polar_Origin_Long + Math.atan2(easting, -northing));

            // force distorted values to 90, -90 degrees
            if (latRad > PI_OVER_2) latRad = PI_OVER_2;
            else if (latRad < -PI_OVER_2) latRad = -PI_OVER_2;
            // force distorted values to 180, -180 degrees
            if (lonRad > Math.PI) lonRad = Math.PI;
            else if (lonRad < -Math.PI) lonRad = -Math.PI;
        }
        if (Southern_Hemisphere) {
            latRad *= -1.0;
            lonRad *= -1.0;
        }

        return new Geodetic2DPoint(new Longitude(lonRad), new Latitude(latRad));
    }
}
