/**
 Copyright 2009-2013 The MITRE Corporation.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 ** **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government
 * under Contract No. W15P7T-12-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
 **************************************************   */
package org.opensextant.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

import org.apache.commons.lang.StringUtils;
import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

/**
 * 
 * @author ubaldino
 */
public class GeonamesUtility {

    private Map<String, Country> country_lookup = null;
    private Map<String, String> iso2fips = new HashMap<String, String>();
    private Map<String, String> fips2iso = new HashMap<String, String>();
    /**
     * Feature map is a fast lookup F/CODE ==&gt; description or name
     */
    private Map<String, String> features = new HashMap<String, String>();

    private Map<String, String> _default_country_names = new HashMap<String, String>();

    /**
     * A utility class that offers many static routines; If you instantiate this
     * class it will require metadata files for country-names and feature-codes
     * in your classpath
     * @throws IOException if metadata files are not found or do not load.
     */
    public GeonamesUtility() throws IOException {

        this.loadCountryNameMap();
        this.loadFeatureMetaMap();
    }

    /**
     * This may help revert to a more readable country name, e.g., if you are given upper case name and you want some version of it as a proper name
     * But no need to use this if you have good reference data.
     * @param c country name
     * @return capitalize the name of a country
     */
    public static String normalizeCountryName(String c) {
        return StringUtils.capitalize(c.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Find a readable name or description of a class/code
     * 
     * @param cls  feature class, e.g., P
     * @param code feature code, e.g., PPL
     * @return name for a feature/code pair
     */
    public String getFeatureName(String cls, String code) {
        String lookup = String.format("%s/%s", cls, code);
        return features.get(lookup);
    }

    private void loadFeatureMetaMap() throws IOException {
        java.io.InputStream io = getClass().getResourceAsStream("/feature-metadata-2013.csv");
        java.io.Reader featIO = new InputStreamReader(io);
        CsvMapReader featreader = new CsvMapReader(featIO, CsvPreference.EXCEL_PREFERENCE);
        String[] columns = featreader.getHeader(true);
        Map<String, String> featMap = null;

        // Feature Metadata is from Universal Gazetteer
        // -----------------------------------
        // Feature Designation Code, CODE
        // Feature Designation Name, DESC
        // Feature Designation Text, -
        // Feature Class CLASS
        //
        while ((featMap = featreader.read(columns)) != null) {
            String feat_code = featMap.get("Feature Designation Code");
            String desc = featMap.get("Feature Designation Name");
            String feat_class = featMap.get("Feature Class");

            if (feat_code == null) {
                continue;
            }

            if (feat_code.startsWith("#")) {
                continue;
            }
            features.put(String.format("%s/%s", feat_class, feat_code), desc);
        }
        featreader.close();
    }

    private void loadCountryNameMap() throws IOException {
        java.io.InputStream io = getClass().getResourceAsStream("/country-names-2014.csv");
        java.io.Reader countryIO = new InputStreamReader(io);
        CsvMapReader countryMap = new CsvMapReader(countryIO, CsvPreference.EXCEL_PREFERENCE);
        String[] columns = countryMap.getHeader(true);
        Map<String, String> country_names = null;

        country_lookup = new HashMap<String, Country>();

        while ((country_names = countryMap.read(columns)) != null) {
            String n = country_names.get("country_name");
            String cc = country_names.get("ISO2_cc");
            String iso3 = country_names.get("ISO3_cc");
            String fips = country_names.get("FIPS_cc");
            iso2fips.put(cc, fips);
            iso2fips.put(iso3, fips);
            fips2iso.put(fips, cc);

            if (n == null || cc == null) {
                continue;
            }

            cc = cc.toUpperCase(Locale.ENGLISH);
            fips = fips.toUpperCase(Locale.ENGLISH);
            
            // Unique Name?  E.g., "Georgia" country name is not unique. 
            // This flag helps inform Disambiguation choose countries and places.
            boolean isUniq = Boolean.parseBoolean(country_names.get("is_unique_name"));

            // FIPS could be *, but as long as we use ISO2, we're fine. if
            // ("*".equals(cc)){ cc = fips.toUpperCase(); }

            // Normalize: "US" =&gt; "united states of america"
            _default_country_names.put(cc, n.toLowerCase(Locale.ENGLISH));

            Country C = new Country(cc, n);
            C.CC_FIPS = fips;
            C.CC_ISO2 = cc;
            C.CC_ISO3 = iso3;
            C.setUniqueName(isUniq);

            // TOOD: resolve overwriting some key conflicts ISO2 codes may also
            // be FIPS
            country_lookup.put(cc, C);
            country_lookup.put(iso3, C);
            if (!fips.equals("*")) {
                country_lookup.put(fips, C);
            }
        }

        countryMap.close();

        if (_default_country_names.isEmpty()) {
            throw new IOException("No data found in country name map");
        }
    }

    /**
     * Finds a default country name for a CC if one exists.
     * 
     * @param cc_iso2  country code.
     * @return name of country
     */
    public String getDefaultCountryName(String cc_iso2) {
        return _default_country_names.get(cc_iso2);
    }

    /**
     * List all country names, official and variant names.
     * @return map of countries, keyed by ISO country code
     */
    public Map<String, Country> getCountries() {
        return country_lookup;
    }

    public static final Country UNK_Country = new Country("UNK", "invalid");

    /**
     * Get Country by the default ISO digraph returns the Unknown country if you
     * are not using an ISO2 code.
     * 
     * TODO: throw a GazetteerException of some sort. for null query or invalid
     * code.
     * @param isocode ISO code
     * @return Country object
     */
    public Country getCountry(String isocode) {
        if (isocode == null) {
            return null;
        }
        return country_lookup.get(isocode);
    }

    /**
     * @param fips FIPS code
     * @return Country object
     */
    public Country getCountryByFIPS(String fips) {
        String isocode = fips2iso.get(fips);
        return getCountry(isocode);
    }
    
    /**
     * Find an ISO code for a given FIPS entry.
     * @param fips
     * @return null if key does not exist.
     */
    public String FIPS2ISO(String fips){
        return fips2iso.get(fips);        
    }

    /**
     *
     */
    public static final Set<String> COUNTRY_ADM0 = new HashSet<String>();
    /**
     *
     */
    public static final String COUNTRY_ADM0_NORM = "0";

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
     * @param v admin code
     * @return fixed admin code
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
            // Nothing.
        }

        // Otherwise it is an alpha numeric code -- all of which
        // typically allow upper/lower casing. We choose UPPER case as
        // normalization.
        //
        return v.toUpperCase(Locale.ENGLISH);
    }

    /**
     * Get a hiearchical path for a boundar or a place.
     * This presumes you have already normalized these values.
     * <pre>
     *    CC.ADM1.ADM2.ADM3... etc. for example:
     *    
     *    'US.48.201'  ... some county in Texas.
     *    
     * </pre>
     * 
     * @param c  country code
     * @param adm1 ADM1 code
     * @return HASC path
     */
    public static String getHASC(String c, String adm1) {
        return String.format("%s.%s", c, adm1);
    }

    /**
     * Experimental. A trivial way of looking at mapping well-known name
     *               collisions to country codes
     */
    public static final Map<String, String> KNOWN_NAME_COLLISIONS = new HashMap<String, String>();

    static {

        // Mapping:  A well-known place ===>  country code for country that could be confused with that place name.
        KNOWN_NAME_COLLISIONS.put("new mexico", "MX");
        KNOWN_NAME_COLLISIONS.put("savannah", "GG");
        KNOWN_NAME_COLLISIONS.put("atlanta", "GG");  // Georgia, USA  ==> Georgia (country)
        KNOWN_NAME_COLLISIONS.put("new jersey", "JE");
        KNOWN_NAME_COLLISIONS.put("new england", "UK");
        KNOWN_NAME_COLLISIONS.put("british columbia", "UK");
    }

    /**
     * Experimental. 
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
     * @param nm  country name
     * @return if country name is ambiguous and collides with other name  
     */
    public static boolean isCountryNameCollision(String nm) {

        // If Name is found, then you can safely ignore the country found, given
        // the
        // returned Country code.
        return (KNOWN_NAME_COLLISIONS.get(nm) != null);
    }

    public static final char ABBREVIATION_TYPE = 'A';
    public static final char NAME_TYPE = 'N';

    /**
     * Check if name type is an Abbreviation
     * 
     * @param name_type code
     * @return true if code is abbreviation
     */
    public static boolean isAbbreviation(char name_type) {
        return name_type == ABBREVIATION_TYPE;
    }

    /**
     * Check if name type is an Abbreviation
     * 
     * @param name_type  OpenSextant code
     * @return true if code is abbreviation
     */
    public static boolean isAbbreviation(String name_type) {
        return name_type.charAt(0) == ABBREVIATION_TYPE;
    }

    /**
     * Is this Place a Country?
     * @param featCode feat code or designation
     * @return - true if this is a country or "country-like" place
     */
    public static boolean isCountry(String featCode) {
        return (featCode!=null ? featCode.startsWith("PCL") : false);
    }

    /**
     * Is this Place a State or Province?
     * @param featCode feature code
     * @return - true if this is a State, Province or other first level admin area
     */
    public static boolean isAdmin1(String featCode) {
        return "ADM1".equalsIgnoreCase(featCode);
    }

    /**
     * Is this Place a National Capital?
     * @param featCode feature code
     * @return - true if this is a a national Capital area
     */
    public static boolean isNationalCapital(String featCode) {
        return "PPLC".equalsIgnoreCase(featCode);
    }

    /**
     * Wrapper for isAbbreviation(name type)
     * 
     * @param p place
     * @return true if is coded as abbreviation 
     */
    public static boolean isAbbreviation(Place p) {
        return isAbbreviation(p.getName_type());
    }

    /**
     * Wrapper for isCountry(feat code)
     * 
     * @param p  place
     * @return true if is Country, e.g., PCLI
     */
    public static boolean isCountry(Place p) {
        return isCountry(p.getFeatureCode());
    }

    /**
     * wrapper for isNationalCaptial( feat code )
     * 
     * @param p place
     * @return true if is PPLC or similar
     */
    public static boolean isNationalCapital(Place p) {
        return isNationalCapital(p.getFeatureCode());
    }

    /**
     * @param p place
     * @return true if is ADM1
     */
    public static boolean isAdmin1(Place p) {
        return "ADM1".equalsIgnoreCase(p.getFeatureCode());
    }

    /**
     * if a place or feature represents an administrative boundary.
     * 
     * @param featClass feature type in question
     * @return true if is admin
     */
    public static boolean isAdministrative(String featClass) {
        return "A".equalsIgnoreCase(featClass);
    }
}
