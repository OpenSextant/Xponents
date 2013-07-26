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
package org.mitre.opensextant.data;

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
}
