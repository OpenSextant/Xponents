/*
 Copyright 2012-2013 The MITRE Corporation.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package org.opensextant.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.Country;
import org.opensextant.data.Language;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.opensextant.util.GeodeticUtility.geohash;

/**
 * @author ubaldino
 */
public class GeonamesUtility {

    private static final Logger logger = LoggerFactory.getLogger(GeonamesUtility.class);

    private final Map<String, Country> isoCountries = new HashMap<>();
    private final Map<String, Country> fipsCountries = new HashMap<>();
    private final Map<String, String> iso2fips = new HashMap<>();
    private final Map<String, String> fips2iso = new HashMap<>();
    private final List<Country> countries = new ArrayList<>();
    /**
     * Feature map is a fast lookup F/CODE ==&gt; description or name
     */
    private final Map<String, String> features = new HashMap<>();

    private final Map<String, String> defaultCountryNames = new HashMap<>();

    /**
     * A utility class that offers many static routines; If you instantiate this
     * class it will require
     * metadata files for country-names and feature-codes in your classpath
     *
     * @throws IOException if metadata files are not found or do not load.
     */
    public GeonamesUtility() throws IOException {
        this.loadCountryNameMap();
        this.loadFeatureMetaMap();
        this.loadUSStateMetadata();
    }

    /**
     * This may help revert to a more readable country name, e.g., if you are given
     * upper case name and
     * you want some version of it as a proper name But no need to use this if you
     * have good reference
     * data.
     *
     * @param c country name
     * @return capitalize the name of a country
     */
    public static String normalizeCountryName(String c) {
        return StringUtils.capitalize(c.toLowerCase(Locale.ENGLISH));
    }

    public static String getFeatureDesignation(String cls, String code) {
        if (cls == null && code == null) {
            return "";
        }
        if (code == null) {
            return cls;
        }
        return String.format("%s/%s", cls, code);
    }

    /**
     * Find a readable name or description of a class/code
     *
     * @param cls  feature class, e.g., P
     * @param code feature code, e.g., PPL
     * @return name for a feature/code pair
     */
    public String getFeatureName(String cls, String code) {
        return features.get(getFeatureDesignation(cls, code));
    }

    /**
     * Load "feature-metadata" CSV
     *
     * @throws IOException
     */
    private void loadFeatureMetaMap() throws IOException {
        String uri = "/feature-metadata-2013.csv";
        try (Reader featIO = new InputStreamReader(GeonamesUtility.class.getResourceAsStream(uri))) {
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
    }

    /**
     * Pase geonames.org TZ table.
     *
     * @throws IOException if timeZones.txt is not found or has an issue.
     */
    private void loadCountryTimezones() throws IOException {
        try(InputStream io = getClass().getResourceAsStream("/geonames.org/timeZones.txt")) {
            java.io.Reader tzReader = new InputStreamReader(io);
            CsvMapReader tzMap = new CsvMapReader(tzReader, CsvPreference.TAB_PREFERENCE);
            String[] columns = tzMap.getHeader(true);
            Map<String, String> tzdata = null;
            String gmtCol = null;
            String dstCol = null;
            for (String col : columns) {
                if (col.startsWith("GMT ")) {
                    gmtCol = col;
                } else if (col.startsWith("DST ")) {
                    dstCol = col;
                }
            }
            if (dstCol == null || gmtCol == null) {
                throw new IOException("Bad Timezone file format from geonames.org -- changes yearly");
            }
            while ((tzdata = tzMap.read(columns)) != null) {
                String cc = tzdata.get("CountryCode");
                if (cc.trim().startsWith("#")) {
                    continue;
                }

                Country C = getCountry(cc);
                if (C == null) {
                    continue;
                }

                Country.TZ tz = new Country.TZ(tzdata.get("TimeZoneId"), tzdata.get(gmtCol), tzdata.get(dstCol),
                        tzdata.get("rawOffset (independant of DST)"));
                C.addTimezone(tz);
            }
        }

        // Add all TZ to countries;
        for (String cc : isoCountries.keySet()) {
            if (cc.length() > 2) {
                continue;
            }
            Country C = isoCountries.get(cc);
            for (String tmzn : C.getAllTimezones().keySet()) {
                addTimezone(tmzn, cc);
            }
            for (Country.TZ tz : C.getTZDatabase().values()) {
                addTZOffset(utc2cc, tz.utcOffset, cc);
                addTZOffset(dst2cc, tz.dstOffset, cc);
            }
        }
    }

    /**
     * Build out a global map of TZ to CC to find which countries are in a
     * particular TZ quickly
     *
     * @param tz TZ name
     * @param cc ISO country code for lookup
     */
    private void addTimezone(String tz, String cc) {
        Set<String> ccset = tz2cc.computeIfAbsent(tz, k -> new HashSet<>());
        ccset.add(cc);
    }

    private static final int ONE_HR = 3600; /* seconds */

    /**
     * Build out a global map of UTC offset to CC to find which countries are in a
     * particular TZ+UTC
     * offset quickly
     *
     * @param utc query in hours (-12.0 to 12.0) or seconds (-43,200 to 43,200) in
     *            the respective 30 or
     *            15 minute increments. Caveat -- some timezones are up to +14h,
     *            e.g., Pacific islands.
     * @param cc  ISO country code
     */
    private void addTZOffset(Map<Double, Set<String>> offset2cc, Double utc, String cc) {
        Set<String> ccset = offset2cc.get(utc);
        if (ccset == null) {
            ccset = new HashSet<>();
            offset2cc.put(utc, ccset);

            /*
             * if offset is is less than 15h
             * then assume it is hours; Otherwise add the value in seconds as well.
             */
            if (Math.abs(utc) <= 15.0) {
                offset2cc.put(utc * ONE_HR, ccset);
            }
        }
        ccset.add(cc);
    }

    /**
     * This helps get the general area +/-5 degrees for a given UTC offset. UTC
     * offsets range from
     * -12.00 to +14.00. This covers 360deg of planet over a 24 hour day. Each
     * offset hour covers about
     * 15deg. Any answer you get here is likely best used with a range of fuziness,
     * e.g., +/- 5deg.
     * REFERENCE: <a href="https://en.wikipedia.org/wiki/List_of_UTC_time_offsets">...</a>
     *
     * @param utc UTC offset
     * @return approximated longitude, in degrees
     */
    public static int approximateLongitudeForUTCOffset(final int utc) {
        int normalized = (utc > 12 ? utc - 24 : utc);

        return 15 * normalized; /* 360 deg / 24 hr = 15deg per UTC offset hour */
    }

    /**
     * Fast TZ/Country lookup by label
     */
    private final Map<String, Set<String>> tz2cc = new HashMap<>();
    /**
     * Fast TZ/Country lookup by UTC offset
     */
    private final Map<Double, Set<String>> utc2cc = new HashMap<>();
    /**
     * Fast TZ/Country lookup by DST offset.
     */
    private final Map<Double, Set<String>> dst2cc = new HashMap<>();

    /**
     * List all countries in a particular TZ
     *
     * @param tz TZ name
     * @return list of country codes
     */
    public Collection<String> countriesInTimezone(String tz) {
        return tz2cc.get(tz.toLowerCase());
    }

    /**
     * List all countries in a particular UTC offset; These are usually -15.0 to
     * 15.0 every 0.5 or 0.25
     * hrs.
     *
     * @param utc offset in decimal hours
     * @return list of country codes found with the offset
     */
    public Collection<String> countriesInUTCOffset(double utc) {
        return utc2cc.get(utc);
    }

    /**
     * This check only makes sense if you have date/time which is in a period of
     * daylight savings. Then
     * the UTC offset for that period can be used to see which countries adhere to
     * that DST convention.
     * E.g., Boston and New York US standard time: GMT-0500, DST: GMT-0400
     *
     * @param dst DST offset
     * @return list of country codes observing DST at that offset
     * @see #countriesInUTCOffset(double)
     */
    public Collection<String> countriesInDSTOffset(double dst) {
        return dst2cc.get(dst);
    }

    public static boolean isValue(final String s) {
        return StringUtils.isNotBlank(s);
    }

    /**
     * Geonames.org data set: citiesN.txt
     *
     * @param resourcePath CLASSPATH location of a resource.
     * @return list of places
     * @throws IOException if resource file is not found
     */
    public static List<Place> loadMajorCities(String resourcePath) throws IOException {
        try {
            return loadMajorCities(GeonamesUtility.class.getResourceAsStream(resourcePath));
        } catch (Exception rareErr) {
            throw new IOException("Major cities data not found for " + resourcePath, rareErr);
        }
    }

    /**
     * Convenience: prepare a map for lookup by ID. If these are geonames.org place
     * objects then the
     * geonames IDs do not line up with those curated within OpenSextant Gazetteer
     * geonames.org cities data is usually unique by row, so if you provide 1000
     * cities in a list your
     * map will have 1000 city place IDs in the map. No duplicates expected.
     *
     * @param cities arra of Place objects
     * @return map of place ID to Place object
     */
    public static Map<String, Place> mapMajorCityIDs(List<Place> cities) {
        Map<String, Place> mapped = new HashMap<>();
        for (Place geo : cities) {
            mapped.put(geo.getPlaceID(), geo);
        }
        return mapped;
    }

    /**
     * See mapPopulationByLocation(list, int). Default geohash prefix length is 5,
     * which yields about
     * 6km grids or so.
     *
     * @param cities list of major cities
     * @return map of population summation over geohash grids.
     */
    public static Map<String, Integer> mapPopulationByLocation(List<Place> cities) {
        return mapPopulationByLocation(cities, 5);
    }

    /**
     * This organizes population data by geohash. Geohash of N-char prefixes. If
     * multiple cities are
     * located on top of that grid, then the populations Geohash prefix = 4, yields
     * about 30 KM, and 5
     * yields 6 KM.
     *
     * @param cities       list of major cities
     * @param ghResolution number of geohash chars in prefix, for keys in map.
     *                     Higher resolution means
     *                     finer geohash grid
     * @return map of population summation over geohash grids.
     */
    public static Map<String, Integer> mapPopulationByLocation(List<Place> cities, int ghResolution) {
        Map<String, Integer> populationGrid = new HashMap<>();
        for (Place geo : cities) {
            if (geo.getPopulation() <= 0) {
                continue;
            }
            if (geo.getGeohash() == null) {
                continue;
            }

            // Build up the population raster.
            //
            String gh = geo.getGeohash().substring(0, ghResolution);
            Integer pop = populationGrid.get(gh);
            if (pop == null) {
                pop = geo.getPopulation();
            } else {
                pop += geo.getPopulation();
            }
            populationGrid.put(gh, pop);
        }
        return populationGrid;
    }

    /**
     * Load the Geonames.org majorcities data file. Mainly to acquire population and
     * other metrics.
     *
     * <pre>
     *  Schema: <a href="http://download.geonames.org/export/dump/">...</a> pass in the files
     * formatted in geonames.org format, and named citiesNNNN.zip (.txt) where
     * NNNN is the population threshold.
     * </pre>
     *
     * @param strm input stream for geonames.org cities file
     * @return list of xponents Place obj
     * @throws IOException if parsing goes wrong.
     */
    public static List<Place> loadMajorCities(InputStream strm) throws IOException {

        // IMPLEMENTATION QUIRK: SuperCSV failed to parse the rows of data
        // as they contain unescaped " chars. TAB delimited file, but cells contain quotes (").
        // Hmm.
        List<Place> cities = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(strm, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {

                String[] cells = line.split("\t");
                if (cells.length < 19) {
                    continue;
                }

                // ID, Name
                Place p = new Place(cells[0], cells[1]);

                // Population
                String pop = cells[14];
                if (StringUtils.isNotBlank(pop)) {
                    p.setPopulation(Integer.parseInt(pop));
                }

                if (isValue(cells[4])) {
                    try {
                        LatLon xy = GeodeticUtility.parseLatLon(cells[4], cells[5]);
                        p.setLatLon(xy);
                    } catch (ParseException e) {
                        // TODO Auto-generated catch block
                        // e.printStackTrace();
                    }
                }
                // Feature Class
                if (isValue(cells[6])) {
                    p.setFeatureClass(cells[6]);
                }
                // Feature Code
                if (isValue(cells[7])) {
                    p.setFeatureCode(cells[7]);
                }

                // Feature Code
                if (isValue(cells[8])) {
                    p.setCountryCode(cells[8]);
                }

                // ADM1
                if (isValue(cells[10])) {
                    p.setAdmin1(cells[10]);
                }
                // ADM2
                if (isValue(cells[11])) {
                    p.setAdmin2(cells[11]);
                }

                if (p.hasCoordinate()) {
                    // Performance issue -- geohash has limited use, so this is done only for
                    // specific use cases.
                    // Place.geohash is not always set, even if lat/lon is.
                    p.setGeohash(geohash(p));
                }
                cities.add(p);
            }
        }
        return cities;
    }

    private void loadCountryNameMap() throws IOException {
        try (InputStream io = getClass().getResourceAsStream("/country-names-2021.csv")) {
            java.io.Reader countryIO = new InputStreamReader(io);
            CsvMapReader countryMap = new CsvMapReader(countryIO, CsvPreference.EXCEL_PREFERENCE);
            String[] columns = countryMap.getHeader(true);
            Map<String, String> country_names = null;
            while ((country_names = countryMap.read(columns)) != null) {
                String n = country_names.get("country_name");
                String cc = country_names.get("ISO2_cc");
                String iso3 = country_names.get("ISO3_cc");
                String fips = country_names.get("FIPS_cc");

                if (n == null || cc == null) {
                    continue;
                }

                double lat = Double.parseDouble(country_names.get("latitude"));
                double lon = Double.parseDouble(country_names.get("longitude"));

                cc = cc.toUpperCase();
                fips = fips.toUpperCase();

                // Unique Name? E.g., "Georgia" country name is not unique.
                // This flag helps inform Disambiguation choose countries and places.
                boolean isUniq = Boolean.parseBoolean(country_names.get("is_unique_name"));
                boolean isTerr = Boolean.parseBoolean(country_names.get("territory"));

                // FIPS could be *, but as long as we use ISO2, we're fine. if
                // ("*".equals(cc)){ cc = fips.toUpperCase(); }

                // Normalize: "US" => "united states of america"
                defaultCountryNames.put(cc, n.toLowerCase(Locale.ENGLISH));

                Country C = new Country(cc, n);
                C.CC_FIPS = fips;
                C.CC_ISO2 = cc;
                C.CC_ISO3 = iso3;
                C.setUniqueName(isUniq);
                C.isTerritory = isTerr;
                C.setLatitude(lat);
                C.setLongitude(lon);


                // TOOD: Resolve the code mapping situation for simple lookups.
                // FIPS -> ISO mapping is 1:1
                fips2iso.put(fips, cc);
                if (!C.isTerritory || (!iso2fips.containsKey(cc) && !iso2fips.containsKey(iso3) && C.isTerritory)) {
                    // ISO -> FIPS is 1 : many, so only map it here if it is unique.
                    iso2fips.put(cc, fips); // ISO2
                    iso2fips.put(iso3, fips);
                } else {
                    logger.debug("Territory not mapped in iso/fips {}, {}", fips, cc);
                }

                // ISO
                if (!C.isTerritory) {
                    isoCountries.put(cc, C);
                    isoCountries.put(iso3, C);
                }

                // FIPS -- mostly unique.
                if (!fips.equals("*")) {
                    fipsCountries.put(fips, C);
                }

                countries.add(C);
            }
        }

        if (defaultCountryNames.isEmpty()) {
            throw new IOException("No data found in country name map");
        }

        // Less standard country/territory codes:
        // Kosovo
        isoCountries.put("XKS", isoCountries.get("XKX"));
        // Jan Mayen / Svalbaard
        isoCountries.put("XJM", isoCountries.get("SJM"));
        isoCountries.put("XSV", isoCountries.get("SJM"));
        // Gaza Strip, GAZ is occassionally used.
        isoCountries.put("GAZ", isoCountries.get("PS"));
        // East Timor-Leste;
        isoCountries.put("TMP", isoCountries.get("TLS"));

        // Missing Country.
        // Valid value for country code, just means no geo-political affiliation.
        // Note, Undersea Features and International waters are mapped to ZZ.
        // This overrides those settings.
        Country noCountryAffiliation = new Country("ZZ", "(No Country)");
        noCountryAffiliation.CC_FIPS = "ZZ";
        noCountryAffiliation.CC_ISO3 = "ZZZ";

        isoCountries.put(noCountryAffiliation.CC_ISO2, noCountryAffiliation);
        isoCountries.put(noCountryAffiliation.CC_ISO3, noCountryAffiliation);

        /*
         * Once all country data is known, associate territories appropriately.
         */
        for (Country C : countries) {
            if (C.isTerritory) {
                Country owningCountry = isoCountries.get(C.getCountryCode());
                if (owningCountry != null) {
                    owningCountry.addTerritory(C);
                } else {
                    // This territory is unique, and country code does not conflict with anything
                    // else.
                    //
                    isoCountries.put(C.getCountryCode(), C);
                    isoCountries.put(C.CC_ISO3, C);
                }
            }
        }

        /*
         * Important data for many tools where time-of-day or other metadata is
         * meaningful.
         */
        loadCountryTimezones();
    }

    /**
     * Provides access to a array of ADM1 metadata. This is a mutable list -- if you
     * want to add MORE
     * admin metadata (entries, postal code mappings, etc) then have at it. For now
     * this is US +
     * territories only (as of v2.8.17)
     *
     * @return List of Admin Level 1 Place objects
     * @since 2.8.17
     * @deprecated Use getUSStateMetadata
     */
    @Deprecated
    public List<Place> getAdmin1Metadata() {
        return getUSStateMetadata();
    }

    /**
     * Provides access to a array of ADM1 metadata. This is a mutable list -- if you
     * want to add MORE
     * admin metadata (entries, postal code mappings, etc) then have at it. For now
     * this is US +
     * territories only (as of v2.8.17)
     *
     * @return List of US States (Admin Level 1) Place objects
     */
    public List<Place> getUSStateMetadata() {
        return usStateMetadata;
    }

    private final List<Place> usStateMetadata = new ArrayList<>();
    private final List<Place> admin1Metadata = new ArrayList<>();
    private final Map<String, Place> admin1MetadataMap = new HashMap<>();

    /**
     * Alias for getWorldAdmin1Metadata.
     *
     * @return list of Places.
     */
    public List<Place> getProvinceMetadata() {
        return admin1Metadata;
    }

    /**
     * Get the array of Place objects representing ADM1 level boundaries. This is
     * literally just Names,
     * ADM1 codes and Country code data. No location information, except for US
     * States.
     * To get a province by code, use
     *
     * @return list of Places
     */
    public List<Place> getWorldAdmin1Metadata() {
        return admin1Metadata;
    }

    /**
     * Source: geonames.org ADM1 codes/names in anglo/ASCII form. These codes do NOT
     * contain geodetic
     * information (lat/lon, etc) CAVEAT -- using such Place metadata will provide a
     * coordinate of (0,0)
     *
     * @throws IOException if geonames.org table cannot be found in classpath
     */
    public void loadWorldAdmin1Metadata() throws IOException {
        String uri = "/geonames.org/admin1CodesASCII.txt";
        Pattern ccSplit = Pattern.compile("\\.");
        try (Reader fio = new InputStreamReader(GeonamesUtility.class.getResourceAsStream(uri))) {
            CsvListReader adm1CSV = new CsvListReader(fio, CsvPreference.TAB_PREFERENCE);
            List<String> adm1;
            while ((adm1 = adm1CSV.read()) != null) {
                String[] path = ccSplit.split(adm1.get(0), 2);
                String placeID = adm1.get(3);
                if ("US".equals(path[0])) {
                    placeID = String.format("USGS%s", placeID);
                } else {
                    placeID = String.format("NGA%s", placeID);
                }

                Place p = new Place(placeID, adm1.get(1));

                p.setFeatureClass("A");
                p.setFeatureCode("ADM1");
                p.setSource("geonames.org");
                p.setName_type('N');

                p.setCountryCode(path[0]);
                p.setAdmin1(path[1]);
                p.defaultHierarchicalPath();
                admin1Metadata.add(p);
            }
            adm1CSV.close();

            if (admin1Metadata.isEmpty()) {
                return;
            }
            /*
             * NOTE: US data is coded in FIPS ADM1 codes, but in geonames data in next loop
             * it is keyed by US Postal code. we get both.
             */
            for (Place p : admin1Metadata) {
                admin1MetadataMap.put(p.getHierarchicalPath(), p);
            }
            for (Place p : usStateMetadata) {
                String key = getHASC(p.getCountryCode(), p.getAdmin1PostalCode());
                Place geonamesADM1 = admin1MetadataMap.get(key);
                if (geonamesADM1 == null) {
                    continue;
                }
                p.setPlaceName(geonamesADM1.getPlaceName());
                p.setPlaceID(geonamesADM1.getPlaceID());

                admin1MetadataMap.put(key, p);
                admin1MetadataMap.put(p.getHierarchicalPath(), p);
            }
        } catch (IOException err) {
            throw err;
        } catch (Exception err) {
            throw new IOException("Failure to parse ADM1 data from geonames.org");
        }
    }

    /**
     * Retrieve a Place object with the semi-official name (in Latin/Anglo terms)
     * given CC and ADM1
     * code. You must load World ADM1 data first; use loadWorldAdmin1Metadata()
     *
     * @param cc   ISO country code
     * @param adm1 ISO province code
     * @return Place or null.
     */
    public Place getAdmin1Place(String cc, String adm1) {
        return admin1MetadataMap.get(GeonamesUtility.getHASC(cc, adm1));
    }

    /**
     * Lookup by coded path, CC.ADM1. You must load World ADM1 data first; use
     * loadWorldAdmin1Metadata()
     * These Admin Places do NOT contain geodetic information (lat/lon, etc) CAVEAT
     * -- using such Place
     * metadata will provide a coordinate of (0,0) Alias for
     * {@link #getAdmin1Place(String, String)}
     *
     * @param cc   country code
     * @param adm1 ADM level 1 code
     * @return Place for province
     */
    public Place getProvince(String cc, String adm1) {
        return getAdmin1Place(cc, adm1);
    }

    /**
     * Lookup by coded path, CC.ADM1. You must load World ADM1 data first; use
     * loadWorldAdmin1Metadata()
     *
     * @param path hierarchical path
     * @return adm1 place obj
     */
    public Place getAdmin1PlaceByHASC(String path) {
        // if (admin1Metadata.isEmpty()) {
        // throw new ConfigException("You must load World ADM1 data first; use
        // loadWorldAdmin1Metadata()");
        // }
        return admin1MetadataMap.get(path);
    }

    /**
     * <pre>
     *  TODO: This is mildly informed by geonames.org, however even there
     * we are still missing a mapping between ADM1 FIPS/ISO codes for a state
     * and the Postal codes/abbreviations.
     *
     * Aliases for the same US province: "US.25" = "MA" = "US.MA" =
     * "Massachussetts" = "the Bay State"
     *
     * Easily mapping the coded data (e.g., 'MA' = '25') worldwide would be
     * helpful.
     *
     * TODO: Make use of geonames.org or other sources for ADM1 postal code
     * listings at top level.
     * </pre>
     *
     * @throws IOException if CSV file not found in classpath
     */
    public void loadUSStateMetadata() throws IOException {
        String uri = "/us-state-metadata.csv";
        try (Reader fio = new InputStreamReader(GeonamesUtility.class.getResourceAsStream(uri))) {
            CsvMapReader adm1CSV = new CsvMapReader(fio, CsvPreference.EXCEL_PREFERENCE);
            String[] columns = adm1CSV.getHeader(true);
            Map<String, String> stateRow = null;

            // -----------------------------------
            // "POSTAL_CODE","ADM1_CODE","STATE","LAT","LON","FIPS_CC","ISO2_CC"
            //
            while ((stateRow = adm1CSV.read(columns)) != null) {

                String roughID = String.format("%s.%s", stateRow.get("ISO2_CC"), stateRow.get("POSTAL_CODE"));
                Place s = new Place(roughID, stateRow.get("STATE"));
                s.setFeatureClass("A");
                s.setFeatureCode("ADM1");
                s.setAdmin1(stateRow.get("ADM1_CODE").substring(2));
                s.setCountryCode(stateRow.get("ISO2_CC"));
                s.defaultHierarchicalPath();
                LatLon yx = GeodeticUtility.parseLatLon(stateRow.get("LAT"), stateRow.get("LON"));
                s.setLatLon(yx);

                s.setAdmin1PostalCode(stateRow.get("POSTAL_CODE"));
                usStateMetadata.add(s);
            }
            adm1CSV.close();
        } catch (Exception err) {
            throw new IOException("Could not load US State data" + uri, err);
        }
    }

    /**
     * Finds a default country name for a CC if one exists.
     *
     * @param cc_iso2 country code.
     * @return name of country
     */
    public String getDefaultCountryName(String cc_iso2) {
        return defaultCountryNames.get(cc_iso2);
    }

    /**
     * List all country names, official and variant names. This does not key any
     * Territories.
     * Territories that carry another nation's country code are attached to that
     * country. Territories
     * assigned their own ISO code are listed/keyed as Countries here.
     *
     * @return map of countries, keyed by ISO country code
     */
    public Map<String, Country> getISOCountries() {
        return isoCountries;
    }

    public List<Country> getCountries() {
        return countries;
    }

    public static final Country UNK_Country = new Country("UNK", "invalid");

    /**
     * Get Country by the default ISO digraph returns the Unknown country if you are
     * not using an ISO2
     * code.
     * TODO: throw a GazetteerException of some sort. for null query or invalid
     * code.
     *
     * @param isocode ISO code
     * @return Country object
     */
    public Country getCountry(String isocode) {
        if (isocode == null) {
            return null;
        }
        return isoCountries.get(isocode);
    }

    /**
     * Find distinct country object by a code. Ambiguous codes will not do anything.
     * This is really
     * useful only if you have no idea what standard your data uses -- FIPS or
     * ISO2/ISO3. If you know
     * then use the API method corresponding to that standard. getCountry() is ISO
     * by default.
     *
     * @param cc country code from any standard.
     * @return found country object
     */
    public Country getCountryByAnyCode(String cc) {
        if (isoCountries.containsKey(cc) && fipsCountries.containsKey(cc)) {
            return null;
        }
        if (isoCountries.containsKey(cc)) {
            return isoCountries.get(cc);
        }
        if (fipsCountries.containsKey(cc)) {
            return fipsCountries.get(cc);
        }
        // Not a country.
        return null;
    }

    /**
     * @param fips FIPS code
     * @return Country object
     */
    public Country getCountryByFIPS(String fips) {
        if (fips == null) {
            return null;
        }
        return fipsCountries.get(fips);
    }

    /**
     * Find an ISO code for a given FIPS entry.
     *
     * @param fips FIPS code
     * @return null if key does not exist.
     */
    public String FIPS2ISO(String fips) {
        return fips2iso.get(fips);
    }

    /**
     *
     */
    public static final Set<String> COUNTRY_ADM0 = new HashSet<>();
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
     * Convert and ADM1 or ADM2 id to a normalized form. US.44 or US.00 gives you 44
     * or 00 for id part.
     * In this case upper case code is returned. if code is a number alone, "0" is
     * returned for "00",
     * "000", etc. And other numbers are 0-padded as 2-digits
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
     * Get a hiearchical path for a boundar or a place. This presumes you have
     * already normalized these
     * values.
     *
     * <pre>
     *  CC.ADM1.ADM2.ADM3... etc. for example:
     *
     * 'US.48.201' ... some county in Texas.
     * </pre>
     *
     * @param c    country code
     * @param adm1 ADM1 code
     * @return HASC path
     */
    public static String getHASC(String c, String adm1) {
        return String.format("%s.%s", c, adm1);
    }

    public static String getHASC(String c, String adm1, String adm2) {
        return String.format("%s.%s.%s", c, adm1, adm2);
    }

    /**
     * Experimental. A trivial way of looking at mapping well-known name collisions
     * to country codes
     */
    public static final Map<String, String> KNOWN_NAME_COLLISIONS = new HashMap<>();

    static {

        // Mapping: A well-known place ===> country code for country that could be
        // confused with that place name.
        KNOWN_NAME_COLLISIONS.put("new mexico", "MX");
        KNOWN_NAME_COLLISIONS.put("savannah", "GG");
        KNOWN_NAME_COLLISIONS.put("atlanta", "GG"); // Georgia, USA ==> Georgia (country)
        KNOWN_NAME_COLLISIONS.put("new jersey", "JE");
        KNOWN_NAME_COLLISIONS.put("new england", "UK");
        KNOWN_NAME_COLLISIONS.put("british columbia", "UK");
    }

    /**
     * Experimental. Given a normalized name phrase, does it collide with country
     * name?
     * Usage: Savannah is a great city. Georgia is lucky it has 10 Chic-fil-a
     * restraunts in that metro
     * area.
     * Georgia is not a country, but a US State. So the logic caller might take: If
     * "savannah" is found,
     * then ignore georgia("GG") as a possible country
     * isCountryNameCollision -- is intending to be objective. If you choose to
     * ignore the country or
     * not is up to caller. Hence this function is not "ignoreCountry(placenm)"
     * TODO: replace with simple config file of such rules that are objective and
     * can be generalized
     *
     * @param nm country name
     * @return if country name is ambiguous and collides with other name
     */
    public static boolean isCountryNameCollision(String nm) {

        // If Name is found, then you can safely ignore the country found, given
        // the
        // returned Country code.
        return (KNOWN_NAME_COLLISIONS.get(nm) != null);
    }

    public static final char ABBREV_TYPE = 'A';
    public static final char NAME_TYPE = 'N';
    public static final char CODE_TYPE = 'C';


    /**
     * Check if name type is an Abbreviation
     *
     * @param name_type code
     * @return true if code is abbreviation
     */
    public static boolean isName(char name_type) {
        return NAME_TYPE == name_type;
    }

    public static boolean isCode(char name_type) {
        return CODE_TYPE == name_type;
    }

    /**
     * Check if name type is an Abbreviation
     *
     * @param name_type OpenSextant code
     * @return true if code is abbreviation
     */
    public static boolean isAbbreviation(char name_type) {
        return ABBREV_TYPE == name_type;
    }

    /**
     * Is this Place a Country?
     *
     * @param featCode feat code or designation
     * @return - true if this is a country or "country-like" place
     */
    public static boolean isCountry(String featCode) {
        return "PCLI".equals(featCode);
    }

    /**
     * Test if a feature is a political entity ~ country, territory, sovereign land
     *
     * @param featCode
     * @return
     */
    public static boolean isPoliticalEntity(String featCode) {
        return featCode != null && featCode.startsWith("PCL");
    }

    /**
     * Is this Place a State or Province?
     *
     * @param featCode feature code
     * @return - true if this is a State, Province or other first level admin area
     */
    public static boolean isAdmin1(String featCode) {
        return "ADM1".equalsIgnoreCase(featCode);
    }

    public static boolean isAdmin2(String featCode) {
        return "ADM2".equalsIgnoreCase(featCode);
    }

    /**
     * Macro for reasoning with upper common levels of boundaries - province, districts.
     *
     * @param featCode
     * @return
     */
    public static boolean isUpperAdminLevel(String featCode) {
        return isAdmin1(featCode) || isAdmin2(featCode);
    }


    /**
     * Is this Place a National Capital?
     *
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
     * @param p place
     * @return true if is Country, e.g., PCLI
     */
    public static boolean isCountry(Place p) {
        return isCountry(p.getFeatureCode());
    }

    /**
     * Test is Place feature is coded as PCL* (PCL, PCLIX, PCLH, PCLD, PCLF, PCLS, etc)
     * @param p Place
     * @return true if place is a political boundary feature
     */
    public static boolean isPoliticalEntity(Place p) {
        return isPoliticalEntity(p.getFeatureCode());
    }

    /**
     * wrapper for isNationalCaptial( feat code )
     *
     * @param p place
     * @return true if is PPLC or similar
     */
    public static boolean isNationalCapital(final Place p) {
        return isNationalCapital(p.getFeatureCode());
    }

    /**
     * @param p place
     * @return true if is ADM1
     */
    public static boolean isAdmin1(final Place p) {
        return "ADM1".equalsIgnoreCase(p.getFeatureCode());
    }

    /**
     * if a place or feature represents an administrative boundary.
     *
     * @param featClass feature type in question
     * @return true if is admin
     */
    public static boolean isAdministrative(final String featClass) {
        return "A".equalsIgnoreCase(featClass);
    }

    /**
     * Administrative feat class + code test.
     *
     * @param featClass
     * @param featCode
     * @return
     */
    public static boolean isAdministrative(final String featClass, final String featCode) {
        if ( "A".equalsIgnoreCase(featClass)){
            return true;
        }
        return featCode != null && "P".equalsIgnoreCase(featClass) && featCode.startsWith("PPLA");
    }

    /**
     * @param featClass geonames feature class, e.g., A, P, H, L, V, T, R
     * @return true if P.
     */
    public static boolean isPopulated(final String featClass) {
        return "P".equalsIgnoreCase(featClass);
    }

    public static boolean isSpot(final String fc) {
        return "S".equalsIgnoreCase(fc);
    }

    public static boolean isLand(final String fc) {
        return "L".equalsIgnoreCase(fc);
    }

    public static boolean isPostal(final Place g) {
        return "POST".equals(g.getFeatureCode()) && "A".equals(g.getFeatureClass());
    }

    public static boolean isPostal(final String fc) {
        return "POST".equals(fc);
    }

    /*
     * Language Support
     */

    /**
     * Given key LangID, list all countries that speak that language. langID's are
     * locales and reduced
     * primary language without locale.
     * MAP: lang_id | locale_id ===&gt; set( cc1, cc2, cc3,...) KEYS: lowercase, ie.
     * 'fr' or 'fr-fr'
     */
    private final HashMap<String, Set<String>> languageInCountries = new HashMap<>();

    public final HashSet<String> unknownLanguages = new HashSet<>();

    /**
     * Is language spoken in country ID'd by cc? See TextUtils for list of langauges
     * provided by Library
     * of Congress.
     *
     * @param lang mixed case langID or langID+Locale.
     * @param cc   UPPERCASE country code.
     * @return false if language is not known or not spoken in that country.
     */
    public boolean countrySpeaks(String lang, String cc) {
        if (lang == null) {
            return false;
        }
        Country C = isoCountries.get(cc);
        if (C != null) {
            return C.isSpoken(lang);
        }
        return false;
    }

    /**
     * If lang is primary lang.
     *
     * @param lang Lang ID
     * @param cc   Country code
     * @return true if lang is the primary language of country named by cc
     */
    public boolean isPrimaryLanguage(String lang, String cc) {
        if (lang == null) {
            return false;
        }
        Country C = isoCountries.get(cc);
        if (C != null) {
            return C.isPrimaryLanguage(lang);
        }
        return false;
    }

    /**
     * When lang ID will do. see primaryLanguage() if Language object is desired.
     *
     * @param cc Country code
     * @return Lang ID
     */
    public String primaryLangID(String cc) {
        Country C = isoCountries.get(cc);
        if (C == null) {
            return null;
        }

        return C.getPrimaryLanguage();
    }

    /**
     * Primary language for a given country. By our convention, this will be the
     * major language family,
     * not the locale. E.g., primary language of Australia? 'en', not 'en_AU'; The
     * hashmap records the
     * first entry only which is language.
     *
     * @param cc Country code
     * @return Language object
     */
    public Language primaryLanguage(String cc) {
        Country C = isoCountries.get(cc);
        if (C == null) {
            return null;
        }

        String lid = C.getPrimaryLanguage();
        if (lid == null) {
            return null;
        }
        Language L = TextUtils.getLanguage(lid);
        if (L != null) {
            return L;
        }
        return new Language(lid, lid, lid); // What language?
    }

    /**
     * Examples:
     * what countries speak french (fr)? what countries speak Rwandan French?
     * (fr-RW)?
     *
     * @param lang lang ID
     * @return list of country codes speaking lang
     */
    public Collection<String> countriesSpeaking(String lang) {
        if (lang == null) {
            return null;
        }
        return languageInCountries.get(lang.toLowerCase());
    }

    /**
     * @param cc UPPERCASE country code.
     * @return ISO Language codes for a country
     */
    public Collection<String> languagesInCountry(String cc) {
        if (cc == null) {
            return null;
        }

        Country C = isoCountries.get(cc);
        if (C != null) {
            return C.getLanguages();
        }
        return null;
    }

    /**
     * Parse metadata from geonames.org (file in CLASSPATH @
     * /geonames.org/countryInfo.txt) and populate
     * existing Country objects with language metadata. By the time you call this
     * method Countries have
     * names, codes, regions, aliases, timezones.
     *
     * @throws IOException if geonames.org resource file is not found
     */
    public void loadCountryLanguages() throws IOException {
        final String uri = "/geonames.org/countryInfo.txt";
        // 0-9
        // #ISO ISO3 ISO-Numeric fips Country Capital Area(in sq km) Population
        // Continent tld
        // 10-18
        // CurrencyCode CurrencyName Phone Postal Code Format Postal Code Regex
        // Languages geonameid neighbours EquivalentFipsCode
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(GeonamesUtility.class.getResourceAsStream(uri), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#")) {
                    continue;
                }
                String[] cells = line.split("\t");
                if (cells.length < 16) {
                    continue;
                }

                String langs = cells[15];
                if (isBlank(langs) || isBlank(cells[0])) {
                    continue;
                }
                String cc = cells[0].toUpperCase().trim();
                String[] langIDs = langs.toLowerCase().trim().split(",");

                // log.info("Country is {}", cc);

                for (String l : langIDs) {
                    String lid = getLang(l);

                    // Push primary language family as first.
                    // e.g. 'es-ES' is primary locale for Spain, but we want to say primary language
                    // for Spain is Spanish('es').
                    // For simplicity sake.
                    // TODO: If we want also the primary Locale, then we would track primary locales
                    // separately.
                    if (!l.equals(lid)) {

                        // Adding lang-ID as first and primary for a given country.
                        addLang(lid, cc);
                    }
                    addLang(l, cc);
                }
            }

            for (String cc : isoCountries.keySet()) {
                if (cc.length() > 2) {
                    continue;
                }
                Country C = isoCountries.get(cc);
                for (String lang : C.getLanguages()) {
                    Set<String> ccset = languageInCountries.computeIfAbsent(lang, l -> new HashSet<>());
                    ccset.add(cc);
                }
            }
        } catch (Exception err) {
            throw new IOException("Did not find Country Metadata at " + uri, err);
        }
    }

    /**
     * @param langOrLocale lang code
     * @param cc           country code
     */
    protected void addLang(String langOrLocale, String cc) {

        if (langOrLocale.length() == 3) {
            Language L = TextUtils.getLanguage(langOrLocale);

            if (L == null) {
                unknownLanguages.add(langOrLocale);
            }
        }

        /*
         * Special case: First language / country pairs seen, denote those as PRIMARY
         * language. As
         * geonames.org lists languages spoken by order of population of speakers.
         */
        Country C = this.isoCountries.get(cc);
        if (C != null) {
            C.addLanguage(langOrLocale);
        }
    }

    private static final Pattern langidSplit = Pattern.compile("[-_]");

    /**
     * Parse lang ID from Locale. Internal method; Ensure argument is not null;
     *
     * @param langid lang ID
     * @return language family
     */
    protected static String getLang(final String langid) {
        return langidSplit.split(langid)[0];
    }
}
