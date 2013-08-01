/*
 * Copyright 2013 ubaldino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensextant.util;

import java.util.HashMap;
import java.util.Map;
import org.opensextant.data.GeoBase;

/**
 * A collection of geodetic routines used within OpenSextant.
 * This is a light wrapper around the most common routines - a full API exists 
 * in other APIs such as GISCore, Geodesy, or Spatial4J
 * 
 * @author ubaldino
 */
public class GeodeticUtility {

        /**
     *
     */
    public final static int LAT_MAX = 90;
    /**
     *
     */
    public final static int LON_MAX = 180;

    /**
     * TODO: consider using geodesy, however that API has no obvious simple
     * validator.
     *
     * @param lat
     * @param lon
     * @return
     */
    public static boolean validateCoordinate(double lat, double lon) {
        // Java behavior for NaN -- use object/class routines to compare.
        // 
        if (Double.isNaN(lon) | Double.isNaN(lat)) {
            return false;
        }
        if (Math.abs(lat) >= LAT_MAX) {
            return false;
        }
        if (Math.abs(lon) >= LON_MAX) {
            return false;
        }
        return true;
    }
    
    
    /**
     * This returns distance in degrees, e.g., this is a Cartesian distance.
     * Only to be used for fast comparison of two locations relatively close
     * together, e.g., within the same 1 or 2 degrees of lat or lon. Beyond that
     * there can be a lot of distortion in the physical distance.
     *
     * @return distance between p1 and p2 in degrees.
     */
    public static double distanceDegrees(GeoBase p1, GeoBase p2) {
        if (p1 == null || p2 == null){
            return Double.MAX_VALUE;
        }
        return Math.sqrt(Math.pow((p1.getLatitude() - p2.getLatitude()), 2)
                + Math.pow((p1.getLongitude() - p2.getLongitude()), 2));
    }

    /**
     * This returns distance in degrees, e.g., this is a Cartesian distance.
     * Only to be used for fast comparison of two locations relatively close
     * together, e.g., within the same 1 or 2 degrees of lat or lon. Beyond that
     * there can be a lot of distortion in the physical distance.
     *
     * @return distance between p1 and p2 in degrees.
     */
    public static double distanceDegrees(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow((lat1 - lat2), 2)
                + Math.pow((lon1 - lon2), 2));
    }
    
    /**
     * Precision -- this is a first draft attempt at assigning some error bars
     * to geocoding results.
     *
     * TODO: move this to a configuration file
     *
     * feat/code: prec # precision is meters of error for a given gazetteer
     * entry with feat/code)
     *
     * A/ADM1: 50000 # ADM1 is generally +/- 50km, world wide P/PPL: 1000 # city
     * is generally +/- 1km within center point P/PPLC: 10000 # major capital
     * city is 10km of error, etc.
     *
     */
    public final static Map<String, Integer> FEATURE_PRECISION = new HashMap<String, Integer>();
    public final static Map<String, Integer> FEATURE_GEOHASH_PRECISION = new HashMap<String, Integer>();
    public final static int DEFAULT_PRECISION = 50000; // +/- 50KM
    public final static int DEFAULT_GEOHASH_PRECISION = 5;
    
    static {
        FEATURE_PRECISION.put("P", 5000);
        FEATURE_PRECISION.put("A", DEFAULT_PRECISION);
        FEATURE_PRECISION.put("S", 1000);

        FEATURE_PRECISION.put("A/ADM1", DEFAULT_PRECISION);
        FEATURE_PRECISION.put("A/ADM2", 20000);
        FEATURE_PRECISION.put("P/PPL", 5000);
        FEATURE_PRECISION.put("P/PPLC", 10000);

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
     * @return precision approx error in meters for a given feature. -1 if no
     * feature type given.
     */
    public static int getFeaturePrecision(String feat_type, String feat_code) {

        if (feat_type == null && feat_code == null) {
            // Unknown, uncategorized feature
            return DEFAULT_PRECISION;
        }

        String lookup = (feat_code != null
                ? feat_type + "/" + feat_code : feat_type);

        Integer prec = FEATURE_PRECISION.get(lookup);

        if (prec != null) {
            return prec.intValue();
        }

        prec = FEATURE_PRECISION.get(feat_type);
        if (prec != null) {
            return prec.intValue();
        }

        return DEFAULT_PRECISION;
    }

    /** For a given Geonames feature class/designation provide a guess about how long
     * geohash should be.
     */
    public static int getGeohashPrecision(String feat_type, String feat_code) {
        if (feat_type == null && feat_code == null) {
            // Unknown, uncategorized feature
            return DEFAULT_GEOHASH_PRECISION;
        }

        String lookup = (feat_code != null
                ? feat_type + "/" + feat_code : feat_type);

        Integer prec = FEATURE_GEOHASH_PRECISION.get(lookup);

        if (prec != null) {
            return prec.intValue();
        }

        prec = FEATURE_GEOHASH_PRECISION.get(feat_type);
        if (prec != null) {
            return prec.intValue();
        }

        return DEFAULT_GEOHASH_PRECISION;
    }        
}
