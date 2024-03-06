/****************************************************************************************
 *  Ellipsoid.java
 *
 *  Created: Dec 29, 2006
 *
 *  @author Paul Silvey (derived from API found at
 *    "http://www.inf.ufsc.br/grafos/represen/java-or/drasys/or/geom/geo/Ellipsoid.html")
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * The Ellipsoid class maintains an object pool of Singleton Ellipsoid objects (used as
 * models of a slightly flattened spherical earth), indexed by String names.  The user
 * can create additional named Ellipsoids if needed, or retrieve one of the statically
 * defined ones that are common or in historic use by geographic information and mapping
 * systems. There is an accessor method to get the list of available cached Ellipsoid
 * names, and there are accessor methods for the commonly used mathematical parameters
 * of an Ellipsoid model. <p>
 * 
 * TODO: implement equals() and hashCode() methods.
 */
public class Ellipsoid implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final String name;
    private final double a;       // Radius in m of circle on equitorial plane (parameter "a")
    private final double b;       // Distance from ellipsoid center to a pole (parameter "b")
    private final double f;       // Flattening ratio f = (a - b) / a
    private final double es;      // Eccentricity of ellipsoid, squared
    private final double ebs;     // Second Eccentricity of ellipsoid, squared

    // Cache of frequently used singleton Ellipsoids (comment out if not needed by app)
    private static final Map<String, Ellipsoid> namedEllipsoids;

    static {
        namedEllipsoids = new HashMap<>();

        new Ellipsoid("WGS 84", 6378137.000, 1.0 / 298.25722356299722);
        new Ellipsoid("WGS 72", 6378135.000, 1.0 / 298.26);
        new Ellipsoid("WGS 66", 6378145.000, 1.0 / 298.25);
        new Ellipsoid("WGS 60", 6378165.000, 1.0 / 298.30);
        new Ellipsoid("South American 1969", 6378160.000, 1.0 / 298.25);
        new Ellipsoid("Krassovsky 1940", 6378245.000, 1.0 / 298.30);
        new Ellipsoid("Hough 1956", 6378270.000, 1.0 / 297.00);
        new Ellipsoid("GRS 1980", 6378137.000, 1.0 / 298.257222101);
        new Ellipsoid("GRS 1975", 6378140.000, 1.0 / 298.257);
        new Ellipsoid("GRS 1967", 6378160.000, 1.0 / 298.247167427);
        new Ellipsoid("Fischer 1968", 6378150.000, 1.0 / 298.30);
        new Ellipsoid("Fischer 1960", 6378166.000, 1.0 / 298.30);
        new Ellipsoid("Everest 1830", 6377276.345, 1.0 / 300.8017);
        new Ellipsoid("Clarke 1880", 6378249.145, 1.0 / 293.465);
        new Ellipsoid("Clarke 1866", 6378206.400, 1.0 / 294.9786982);
        new Ellipsoid("Bessel 1841", 6377397.155, 1.0 / 299.1528128);
        new Ellipsoid("Bessel 1841 Namibia", 6377483.865, 1.0 / 299.1528128);
        new Ellipsoid("Airy 1830", 6377563.396, 1.0 / 299.3249646);
    }

    /**
     * This is the constructor for a custom ellipsoid to be created and cached in the
     * object pool. If the name has not been used, the new Ellipsoid will be cached as
     * a singleton for future access. If already defined, an IllegalArgumentException
     * is thrown. Commonly used Ellipsoids are defined in a static block of this class
     * at class load time, so the constructor is typically not needed by client programs.
     * Use the getInstance method to retrieve by name instead.
     *
     * @param name             String name used to get this singleton Ellipsoid instance
     * @param equatorialRadius radius in meters of circle on equitorial plane, a
     * @param flattening       ratio of axes difference to equatorial radius, f = (a - b) / a
     * @throws IllegalArgumentException error if named ellipoid has been previously defined
     */
    public Ellipsoid(String name, double equatorialRadius, double flattening)
            throws IllegalArgumentException {
        // User can't redefine initial named ellipsoids using standard names
        if (namedEllipsoids.get(name) != null)
            throw new IllegalArgumentException("Ellipsoid " + name + " is already defined");

        // Do error checking on arguments
        if (equatorialRadius <= 0.0)
            throw new IllegalArgumentException("Ellipsoid semi-major axis (equatorialRadius)" +
                    " must be greater than zero");

        double inv_f = 1.0 / flattening;
        if ((inv_f < 250.0) || (350.0 < inv_f))
            throw new IllegalArgumentException("Ellipsoid's inverse flattening (1/f)" +
                    " must be between 250 and 350");

        this.name = name;
        a = equatorialRadius;
        f = flattening;
        b = equatorialRadius * (1.0 - flattening);

        es = (2.0 * f) - (f * f);
        ebs = (1.0 / (1.0 - es)) - 1.0;

        namedEllipsoids.put(name, this);
    }

    /**
     * This method returns a Set of String names of defined Ellipsoids available in the
     * object pool.
     *
     * @return the Set of String names for currently defined Ellipsoid singletons
     */
    @NotNull
    public static Set<String> getEllipsoidNames() {
        return namedEllipsoids.keySet();
    }

    /**
     * This method will retrieve the named singleton Ellipsoid, if defined.
     *
     * @param name String used to identify the Ellipsoid (e.g. "WGS 84")
     * @return Ellipsoid singleton with given name, retrieved from cache
     */
    public static Ellipsoid getInstance(String name) {
        return namedEllipsoids.get(name);
    }

    /**
     * This accessor method returns the name of this Ellipsoid.
     *
     * @return name String used to identify this ellipsoid
     */
    public String getName() {
        return name;
    }

    /**
     * This accessor method returns the equatorialRadius, ellipsoid parameter 'a'
     * (semi-major axis).
     *
     * @return radius in meters of circle on equitorial plane
     */
    public double getEquatorialRadius() {
        return a;
    }

    /**
     * This accessor method returns the polarRadius, ellipsoid parameter 'b'
     * (semi-minor axis).
     *
     * @return distance in meters from center to pole
     */
    public double getPolarRadius() {
        return b;
    }

    /**
     * This method returns the radius of curvature of this Ellipsoid, in meters,
     * at the given Latitude.  It is also called the radius of curvature in the prime
     * vertical.
     *
     * @param lat the Latitude for which the ellipsoid's radius is desired
     * @return the radius of the ellipsoid at the given Latitude, in meters.
     */
    public double getRadius(Latitude lat) {
        double sinLat = Math.sin(lat.inRadians());
        return a / Math.sqrt(1.0 - es * sinLat * sinLat);
    }

    /**
     * This accessor method returns the ellipsoid flattening parameter,
     * 'f' = (a - b) / a.
     *
     * @return flattening ratio of axes difference to equatorial radius
     */
    public double getFlattening() {
        return f;
    }

    /**
     * This accessor method returns the ellipsoid eccentricity parameter,
     * 'e' = sqrt(1 - (b^2 / a^2)).
     *
     * @return eccentricity of the ellipsoid
     */
    public double getEccentricity() {
        return Math.sqrt(es);
    }


    /**
     * This accessor method returns the ellipsoid eccentricity value squared.
     *
     * @return eccentricity of the ellipsoid, squared
     */
    public double getEccentricitySquared() {
        return es;
    }

    /**
     * This accessor method returns the ellipsoid second eccentricity value squared.
     *
     * @return second eccentricity of the ellipsoid, squared
     */
    public double get2ndEccentricitySquared() {
        return ebs;
    }

    /**
     * This method calculates the orthodromic distance in meters between two points, which
     * is the shortest distance between two geodetic points on the surface of this ellipsoid.
     *
     * @param p1 geodetic point 1
     * @param p2 geodetic point 2
     * @return distance in meters
     */
    public double orthodromicDistance(Geodetic2DPoint p1, Geodetic2DPoint p2) {
        Geodetic2DArc arc = new Geodetic2DArc(this, p1, p2);
        return arc.getDistanceInMeters();
    }

    /**
     * This method returns the name of this Ellipsoid.
     *
     * @return name String used to identify this Ellipsoid in the object pool.
     */
    public String toString() {
        return getName();
    }
}
