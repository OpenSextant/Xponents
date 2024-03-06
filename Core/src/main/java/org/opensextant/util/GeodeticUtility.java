/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
package org.opensextant.util;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.locationtech.spatial4j.io.GeohashUtils;
import org.opensextant.data.GeoBase;
import org.opensextant.data.Geocoding;
import org.opensextant.data.LatLon;
import static org.opensextant.util.GeonamesUtility.getFeatureDesignation;

/**
 * A collection of geodetic routines used within OpenSextant. This is a light
 * wrapper around the most common routines - a full API exists in other APIs
 * such as GISCore, Geodesy, or Spatial4J
 *
 * @author ubaldino
 */
public class GeodeticUtility {

    /**
     *
     */
    public static final int LAT_MAX = 90;
    /**
     *
     */
    public static final int LON_MAX = 180;

    private GeodeticUtility(){}

    /**
     * TODO: consider using geodesy, however that API has no obvious simple
     * validator.
     *
     * @param lat latitude
     * @param lon longitude
     * @return if lat/lon is valid
     */
    public static boolean validateCoordinate(double lat, double lon) {
        // Java behavior for NaN -- use object/class routines to compare.
        //
        if (Double.isNaN(lon) || Double.isNaN(lat)) {
            return false;
        }
        if (Math.abs(lat) >= LAT_MAX) {
            return false;
        }
        return !(Math.abs(lon) >= LON_MAX);
    }

    /**
     * A common check required by practical applications -- 0,0 is not
     * interesting, so this is a simple java-based check. double (and all
     * number) values by default have a value = 0. This appears to be true for
     * class attributes, but not for locals. Hence the NaN check in
     * validateCoordinate.
     *
     * @param lat in degrees
     * @param lon in degress
     * @return true if coordinate is non-zero (0.000, 0.000) AND is valid
     *         abs(lon) &lt; 180.0, etc.
     */
    public static boolean isValidNonZeroCoordinate(double lat, double lon) {
        return isCoord(lat, lon);
    }

    /**
     * @param xy a geocoding
     * @return true if geocoding has a non-zero coordinate
     */
    public static boolean isCoord(Geocoding xy) {
        return isCoord(xy.getLatitude(), xy.getLongitude());
    }

    /**
     * Just tests if location is not 0,0 ... if provided as floating point objects
     * Java has rounding error off in 0.0000001 place. Close enough to 0,0 counts as
     * zero-coordinate.
     *
     * @param lat latitude
     * @param lon longitude
     * @return true if coordinate is set and is other than (0,0)
     */
    public static boolean isZeroCoord(double lat, double lon) {
        return (Math.abs(lat) < 0.00001 && Math.abs(lon) < 0.00001);
    }

    public static boolean isCoord(double lat, double lon) {
        return validateCoordinate(lat, lon) && !isZeroCoord(lat, lon);
    }

    public static boolean isCoord(final LatLon p) {
        return isValidNonZeroCoordinate(p.getLatitude(), p.getLongitude());
    }

    /**
     * This returns distance in degrees, e.g., this is a Cartesian distance.
     * Only to be used for fast comparison of two locations relatively close
     * together, e.g., within the same 1 or 2 degrees of lat or lon. Beyond that
     * there can be a lot of distortion in the physical distance.
     *
     * @param p1 point
     * @param p2 point
     * @return distance between p1 and p2 in degrees.
     */
    public static double distanceDegrees(GeoBase p1, GeoBase p2) {
        if (p1 == null || p2 == null) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(Math.pow((p1.getLatitude() - p2.getLatitude()), 2)
                + Math.pow((p1.getLongitude() - p2.getLongitude()), 2));
    }

    public static final long EARTH_RADIUS = 6372800L; // In meters

    /**
     * Haversine distance using LL1 to LL2;
     *
     * @param p1 geodesy API LatLon
     * @param p2 geodesy API LatLon
     * @return distance in meters.
     */
    public static long distanceMeters(LatLon p1, LatLon p2) {
        double lat1 = p1.getLatitude();
        double lon1 = p1.getLongitude();
        double lat2 = p2.getLatitude();
        double lon2 = p2.getLongitude();

        /* Courtesy of http://rosettacode.org/wiki/Haversine_formula#Java */
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return (long) (EARTH_RADIUS * c);
    }

    /**
     * This returns distance in degrees, e.g., this is a Cartesian distance.
     * Only to be used for fast comparison of two locations relatively close
     * together, e.g., within the same 1 or 2 degrees of lat or lon. Beyond that
     * there can be a lot of distortion in the physical distance.
     *
     * @param lat1 P1.lat
     * @param lon1 P1.lon
     * @param lat2 P2.lat
     * @param lon2 P2.lon
     * @return distance between p1 and p2 in degrees.
     */
    public static double distanceDegrees(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow((lat1 - lat2), 2) + Math.pow((lon1 - lon2), 2));
    }

    /**
     * Precision -- this is a first draft attempt at assigning some error bars to geocoding results.
     * TODO: move this to a configuration file
     * feat/code: prec # precision is meters of error for a given gazetteer  entry with feat/code)
     * A/ADM1:  50000 # ADM1 is generally +/- 50km, world wide
     * P/PPL:    1000 # city is generally +/- 1km within center point
     * P/PPLC:  10000 # major capital city is 10km of error, etc.
     */
    public static final Map<String, Integer> FEATURE_PRECISION = new HashMap<>();
    public static final Map<String, Integer> FEATURE_GEOHASH_PRECISION = new HashMap<>();
    public static final int DEFAULT_PRECISION = 50000; // +/- 50KM
    public static final int DEFAULT_GEOHASH_PRECISION = 5;

    static {
        FEATURE_PRECISION.put("", 2 * DEFAULT_PRECISION);
        FEATURE_PRECISION.put("P", 5000);
        FEATURE_PRECISION.put("A", DEFAULT_PRECISION);
        FEATURE_PRECISION.put("S", 1000);

        FEATURE_PRECISION.put("A/ADM1", DEFAULT_PRECISION);
        FEATURE_PRECISION.put("A/ADM2", 20000);
        FEATURE_PRECISION.put("P/PPL", 5000);
        FEATURE_PRECISION.put("P/PPLC", 10000);
        FEATURE_PRECISION.put("A/POST", 5000); /* POSTAL support as of v3.5 */

        // This helps guage how long should a geohash be for a given feature.
        FEATURE_GEOHASH_PRECISION.put("A/PCLI", 3);
        FEATURE_GEOHASH_PRECISION.put("CTRY", 3);
        FEATURE_GEOHASH_PRECISION.put("P", 6);
        FEATURE_GEOHASH_PRECISION.put("A", 4);
        FEATURE_GEOHASH_PRECISION.put("S", 8);
        FEATURE_GEOHASH_PRECISION.put("A/ADM2", 5);
    }

    /**
     * For a given feature type and code, determine what sort of resolution or
     * precision should be considered for that place, approximately.
     *
     * @param feat_type major feature type
     * @param feat_code minor feature type or designation
     * @return precision approx error in meters for a given feature. -1 if no
     *         feature type given.
     */
    public static int getFeaturePrecision(final String feat_type, final String feat_code) {

        if (feat_type == null && feat_code == null) {
            // Unknown, uncategorized feature
            return DEFAULT_PRECISION;
        }

        String lookup = getFeatureDesignation(feat_type, feat_code);
        Integer prec = FEATURE_PRECISION.get(lookup);

        if (prec != null) {
            return prec;
        }

        prec = FEATURE_PRECISION.get(feat_type);
        if (prec != null) {
            return prec;
        }

        return DEFAULT_PRECISION;
    }

    /**
     * For a given Geonames feature class/designation provide a guess about how
     * long geohash should be. Geohash in this use is very approximate
     *
     * @param feat_type major feature type
     * @param feat_code minor feature type or designation
     * @return prefix length for a geohash, e.g., for a province in general is 3
     *         chars of geohash sufficient?
     */
    public static int getGeohashPrecision(String feat_type, String feat_code) {
        if (feat_type == null && feat_code == null) {
            // Unknown, uncategorized feature
            return DEFAULT_GEOHASH_PRECISION;
        }

        String lookup = getFeatureDesignation(feat_type, feat_code);
        Integer prec = FEATURE_GEOHASH_PRECISION.get(lookup);

        if (prec != null) {
            return prec;
        }

        prec = FEATURE_GEOHASH_PRECISION.get(feat_type);
        if (prec != null) {
            return prec;
        }

        return DEFAULT_GEOHASH_PRECISION;
    }

    /**
     * The most simplistic parsing and validation of "lat lon" or "lat, lon" any
     * amount of whitespace is allowed, provided the lat lon order is there.
     *
     * @param lat_lon string form of a simple lat/lon, e.g., "Y X"; No symbols
     * @return LatLon object
     * @throws ParseException if string is unparsable
     */
    public static LatLon parseLatLon(String lat_lon) throws ParseException {
        if (StringUtils.isBlank(lat_lon)) {
            return null;
        }
        String delim = lat_lon.contains(",") ? "," : " ";

        List<String> LL = TextUtils.string2list(lat_lon, delim);
        LatLon geo = null;
        try {
            geo = new GeoBase(null, lat_lon);
            geo.setLatitude(Double.parseDouble(LL.get(0)));
            geo.setLongitude(Double.parseDouble(LL.get(1)));

        } catch (Exception parseerr) {
            throw new ParseException("Unable to Parse text as XY:" + parseerr.getMessage(), 0);
        }

        if (!validateCoordinate(geo.getLatitude(), geo.getLongitude())) {
            throw new ParseException("Invalid Coordinate values", 0);
        }
        return geo;
    }

    /**
     * Parse coordinate from object
     *
     * @param lat latitude
     * @param lon longitude
     * @return LatLon object
     * @throws ParseException if objects are not valid numbers
     */
    public static LatLon parseLatLon(Object lat, Object lon) throws ParseException {
        if (lat == null || lon == null) {
            // incomplete data.
            // Caller should test
            throw new ParseException("Incomplete data, null lat or lon", 0);
        }

        LatLon yx = new GeoBase();
        yx.setLatitude(Double.parseDouble(lat.toString()));
        yx.setLongitude(Double.parseDouble(lon.toString()));

        return yx;
    }

    /**
     * Create a string representation of a decimal lat/lon.
     *
     * @param yx LatLon object
     * @return "lat, lon" formatted with 4 decimal places; that is an average
     *         amount of precision for common XY=&gt; String uses.
     */
    public static String formatLatLon(final LatLon yx) {
        return String.format("%2.4f,%3.4f", yx.getLatitude(), yx.getLongitude());
    }

    /**
     * @param yx lat,lon obj
     * @return geohash representation of the lat,lon
     */
    public static String geohash(final LatLon yx) {
        return GeohashUtils.encodeLatLon(yx.getLatitude(), yx.getLongitude());
    }

    public static String geohash(double lat, double lon) {
        return GeohashUtils.encodeLatLon(lat, lon);
    }

}
