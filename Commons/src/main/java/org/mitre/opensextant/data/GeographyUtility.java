/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mitre.opensextant.data;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 *
 * @author ubaldino
 */
public class GeographyUtility {

    /**
     *
     */
    public final static Set<String> COUNTRY_ADM0 = new HashSet<String>();
    /**
     *
     */
    public final static String COUNTRY_ADM0_NORM = "0";

    static {
        COUNTRY_ADM0.add("0");
        COUNTRY_ADM0.add("00");
        COUNTRY_ADM0.add("000");
    }

    /**
     * Convert and ADM1 or ADM2 id to a normalized form. US.44 or US.00 gives
     * you 44 or 00 for id part. In this case upper case code is returned. if
     * code is a number alone, "0" is returned for "00", "000", etc. And other
     * numbers are 0-padded as 2-digits
     *
     * @param v
     * @return
     */
    public static String normal_ADM_code(String v) {

        if (v == null) {
            return null;
        }

        // Normalize all Country "ADM0" variations to "0"
        if (COUNTRY_ADM0.contains(v)) {
            return COUNTRY_ADM0_NORM;
        }

        // Normalize all ADM codes to at least two digits
        try {
            int x = Integer.parseInt(v);
            return String.format("%02d", x);
        } catch (Exception parserr) {
            //Nothing.   
        }

        // Otherwise it is an alpha numeric code -- all of which 
        // typically allow upper/lower casing.  We choose UPPER case as
        // normalization.
        // 
        return v.toUpperCase();
    }

    /**
     * This presumes you have already normalized these values
     *
     * @param c
     * @param adm1
     * @return
     */
    public static String get_HASC(String c, String adm1) {
        return c + "." + adm1;
    }
    /**
     * @experimental A trivial way of looking at mapping well-known name
     * collisions to country codes
     */
    public final static Map<String, String> KNOWN_NAME_COLLISIONS = new HashMap<String, String>();

    static {

        KNOWN_NAME_COLLISIONS.put("new mexico", "MX");
        KNOWN_NAME_COLLISIONS.put("savannah", "GG");
        KNOWN_NAME_COLLISIONS.put("atlanta", "GG");
        KNOWN_NAME_COLLISIONS.put("new jersey", "JE");
        KNOWN_NAME_COLLISIONS.put("new england", "UK");
        KNOWN_NAME_COLLISIONS.put("british columbia", "UK");
    }

    /**
     * Given a normalized name phrase, does it collide with country name?
     *
     * Usage: Savannah is a great city. Georgia is lucky it has 10 Chic-fil-a
     * restraunts in that metro area.
     *
     * Georgia is not a country, but a US State. So the logic caller might take:
     * If "savannah" is found, then ignore georgia("GG") as a possible country
     *
     * isCountryNameCollision -- is intending to be objective. If you choose to
     * ignore the country or not is up to caller. Hence this function is not
     * "ignoreCountry(placenm)"
     *
     * TODO: replace with simple config file of such rules that are objective
     * and can be generalized
     *
     * @param nm
     * @return CountryCode
     */
    public static boolean isCountryNameCollision(String nm) {

        // If Name is found, then you can safely ignore the country found, given the 
        // returned Country code.
        return (KNOWN_NAME_COLLISIONS.get(nm) != null);
    }
    
    
    /**
     *
     */
    public final static int LAT_MAX = 90;
    
    /**
     *
     */
    public final static int LON_MAX = 180;

    /**
     * TODO: consider using geodesy, however that API has no obvious simple validator.
     * 
     * @param lat 
     * @param lon
     * @return
     */
    public static boolean validateCoordinate(double lat, double lon) {
        if (Math.abs(lat) >= LAT_MAX) {
            return false;
        }
        if (Math.abs(lon) >= LON_MAX) {
            return false;
        }
        return true;
    }
}
