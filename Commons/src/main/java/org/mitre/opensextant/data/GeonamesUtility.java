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
public class GeonamesUtility {

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
    public static String normalizeAdminCode(String v) {

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
    public static String getHASC(String c, String adm1) {
        return String.format("%s.%s", c, adm1);
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

    public final static char ABBREVIATION_TYPE = 'A';
    public final static char NAME_TYPE = 'N';

    /**
     * Check if name type is an Abbreviation
     *
     * @param name_type code
     */
    public static boolean isAbbreviation(char name_type) {
        return name_type == ABBREVIATION_TYPE;
    }

    /**
     * Check if name type is an Abbreviation
     *
     * @param name_type code
     */
    public static boolean isAbbreviation(String name_type) {
        return name_type.charAt(0) == ABBREVIATION_TYPE;
    }

    /**
     * Is this Place a Country?
     *
     * @return - true if this is a country or "country-like" place
     */
    public static boolean isCountry(String featCode) {
        return featCode.startsWith("PCL");
    }

    /**
     * Is this Place a State or Province?
     *
     * @return - true if this is a State, Province or other first level admin
     * area
     */
    public static boolean isAdmin1(String featCode) {
        return "ADM1".equalsIgnoreCase(featCode);
    }

    /**
     * Is this Place a National Capital?
     *
     * @return - true if this is a a national Capital area
     */
    public static boolean isNationalCapital(String featCode) {
        return "PPLC".equalsIgnoreCase(featCode);
    }

    /**
     * Wrapper for isAbbreviation(name type)
     * @param p place
     */
    public static boolean isAbbreviation(Place p) {
        return isAbbreviation(p.getName_type());
    }

    /**
     * Wrapper for isCountry(feat code)
     * @param p place
     */
    public static boolean isCountry(Place p) {
        return isCountry(p.getFeatureCode());
    }

    /** wrapper for isNationalCaptial( feat code )
     * 
     * @param p place
     * @return 
     */
    public static boolean isNationalCapital(Place p) {
        return isNationalCapital(p.getFeatureCode());
    }

    /**
     * @param p place
     */
    public static boolean isAdmin1(Place p) {
        return "ADM1".equalsIgnoreCase(p.getFeatureCode());
    }
}
