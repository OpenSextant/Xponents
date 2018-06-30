
/*
 *   __________   ____   ____   ____  ____  
 *  /  ___/  _ \_/ ___\ / ___\_/ __ \/  _ \ 
 *  \___ (  <_> )  \___/ /_/  >  ___(  <_> )
 * /____  >____/ \___  >___  / \___  >____/ 
 *      \/           \/_____/      \/       
 *
 *      Social Media Geo-Inferencing
 *             OpenSextant
 */
package org.opensextant.extractors.geo.social;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.ConfigException;
import org.opensextant.data.Country;
import org.opensextant.data.Geocoding;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.data.social.Tweet;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.extractors.xcoord.GeocoordPrecision;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base-class that has the various hooks for logging, dev/test/evaluation,
 * common dictionaries/resources, and helpful connectivity items.
 * 
 * @author ubaldino
 *
 */
public abstract class SocialGeo {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected boolean evalMode = log.isDebugEnabled();

    public String inferencerID = null;
    public String inferencerDescription = null;

    protected GeonamesUtility countries = null;
    /** If you populate allCountries with */
    protected Map<String, Country> allCountries = null;

    /** A particular hashing of the list of country names. */
    protected Map<String, Country> basicCountryNames = null;

    public SocialGeo() {
    }

    /**
     * Configure your implementation.
     * 
     * @throws ConfigException
     */
    public abstract void configure() throws ConfigException;

    /**
     * Release resources quietly.
     */
    public abstract void close();

    /**
     * Generally useful test of string values.
     * 
     * @param v
     * @return
     */
    public static boolean isValue(String v) {
        return isNotBlank(v);
    }

    /**
     * This score as a boost for any sort of disambiguation of ties or close scores in predictions.
     * 
     * <pre>
     *  
     * Points:  A Country may score in 0 or more of these three categories: TZ, UTC, LANG.
     * 
     *      TZ
     * +3 - Country contains timezone named by Tweet.timezone
     * 
     *      UTC
     * +3 - Country contains UTC offset named by Tweet.utcOffset (Hours); 
     * +4 - Or if Tweet is in period of DST and Country observes that DST offset.
     *      This is slightly less believable because users apparently do not always adjust TZ and time on devices.
     *      Just the same, if country uses DST and so is user, then that is more significant than without 
     *      
     *      LANG
     * +3 - Language of User and of Text are both Primary language of Country
     * +2 - either language is Primary language of Country
     * +1 - language of text is spoken in Country
     * 
     *      LON
     *      TODO:  consider (Country.LatLon ~ Tweet.UTC) ? within 5 degrees.  Countries vary by size this
     *      makes little sense.  But for Cities and States it makes more sense.
     * 
     * MAX score is 3 + 4 + 3 = 10
     * </pre>
     * 
     * @param C
     *            a country prediction for the tweet.
     * @param tw
     *            the tweet
     * @return score 1 to ~20
     */
    public int scoreCountryPrediction(Country C, Tweet tw) {

        if (C == null || tw == null) {
            return 0;
        }

        int score = 0;

        // We value the positional information from TZ
        // However this provides some notion of latitude/longitude, 
        // Where TZ offers mainly just longitude.
        // 
        if (isValue(tw.timezone)) {
            if (C.containsTimezone(tw.timezone)) {
                score += 3;
            }
        }

        // We value the positional information from UTC offset
        // Between DST and non-DST periods in the year,
        // There is only a slight difference in weight: 
        // If a user's clock changes with DST, then we favor countries
        // that recognize the time change over ones that don't.
        // 
        if (Tweet.validateUTCOffset(tw.utcOffset)) {
            if (tw.isDST && C.containsDSTOffset(tw.utcOffsetHours)) {
                score += 4;
            } else if (C.containsUTCOffset(tw.utcOffsetHours)) {
                score += 3;
            }
        }

        // Languages such as Arabic, English, etc. are world languages.
        // They are less specific to a country. But may help disambiguate ties.
        boolean ulang1 = C.isPrimaryLanguage(tw.userLang);
        boolean lang1 = C.isPrimaryLanguage(tw.lang);
        if (ulang1 && lang1) {
            score += 3;
        } else if (ulang1 || lang1) {
            score += 2;
        } else if (C.isSpoken(tw.lang)) {
            score += 1;
        }

        return score;
    }

    /**
     * Create a lookup of the most common country names.
     * This is just a pure ASCII listing... of ISO country names.
     * To get more country names, populateAllCountries() should be used.
     */
    public void populateBasicCountryNames() {
        basicCountryNames = new HashMap<>();
        for (Country c : countries.getCountries()) {
            basicCountryNames.put(c.getNamenorm(), c);
        }
        basicCountryNames.put("united states", countries.getCountry("US"));
    }

    /**
     * Populate the allCountries listing. Not all pipeline apps make use of SolrGazetteer or do geo work
     * so this is not part of setup.
     * 
     * @param gaz
     */
    public void populateAllCountries(SolrGazetteer gaz) {
        // SolrGazetteer has full Country metadata, like 
        // GeonamesUtility lists offical countries (with TZ, region, aliases, codes)
        // SolrGazetteer lists more aliases/name variants.  
        // So have Map( variant =&gt; Country ), where Country is official one.
        // 
        allCountries = new HashMap<>();

        // Gather 'Official names'
        for (Country C : countries.getCountries()) {
            // NAME: 'United States of America'
            allCountries.put(C.getName().toLowerCase(), C);
            // ISO alpha-2  'US'
            allCountries.put(C.CC_ISO2.toLowerCase(), C);
            // ISO alpha-3  'USA'
            allCountries.put(C.CC_ISO3.toLowerCase(), C);
        }

        // Gather name variants from anything that looks like PCLI or PCLI* in gazetteer:
        // 
        for (String cc : gaz.getCountries().keySet()) {
            Country alt = gaz.getCountries().get(cc);
            Country C = countries.getCountry(cc);
            // NAME: 'United States of America'
            allCountries.put(alt.getName().toLowerCase(), C);
            // ISO alpha-2  'US'
            allCountries.put(alt.CC_ISO2.toLowerCase(), C);
            // ISO alpha-3  'USA'
            allCountries.put(alt.CC_ISO3.toLowerCase(), C);
            for (String a : alt.getAliases()) {
                // Aliases:  'U.S.A', 'US of A', 'America', etc.
                //
                allCountries.put(a.toLowerCase(), C);
            }
        }
    }

    /**
     * Geonames Helpers.  Attach Province name if useful. 
     * Ideally keep data coded in databases, and render name at presentation or export time, if needed.
     * But no need to store superfluous name data that is just a reflection of things that are coded.
     * 
     * @throws IOException
     */
    public void loadProvinceNames() throws IOException {
        if (countries == null) {
            countries = new GeonamesUtility();
        }
        countries.loadWorldAdmin1Metadata();
    }

    protected Map<String, Place> US_STATES = new HashMap<>();

    /**
     * Lookup US States.
     * 
     * @param name
     * @return
     */
    public Place getUSStateByName(String name) {
        return US_STATES.get(name.toLowerCase());
    }

    /**
     * A dot-separated code, country code + FIPS numeric
     * 
     * @param code
     *            CC.FF
     * @return
     */
    public Place getUSStateByCode(String code) {
        return US_STATES.get(code.toUpperCase());
    }

    /**
     * CAVEAT:  For now this only loads US states, despite us loading
     * @throws IOException
     */
    public void loadUSStates() throws IOException {

        if (countries == null) {
            countries = new GeonamesUtility();
        }

        for (Place p : countries.getUSStateMetadata()) {
            US_STATES.put(p.getPlaceName().toLowerCase(), p);
            US_STATES.put(p.getAdmin1PostalCode().toUpperCase(), p);
        }
    }

    protected double getConfidence(double c) {
        return new BigDecimal(c).setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /**
     * 
     * @param nm
     * @return
     */
    public Country getCountryNamed(String nm) {
        if (nm == null) {
            return null;
        }
        if (allCountries == null) {
            return null;
        }
        Country c = allCountries.get(nm.toLowerCase());
        return c;
    }

    public Place inferPlaceRecursively(SolrGazetteer gaz, Geocoding poi) throws SolrServerException, IOException {
        return inferPlaceRecursively(gaz, poi, false);
    }

    /**
     * set Province name from given codes on somePlace.
     * @param somePlace Place object with CC and ADM1 codes set. 
     */
    public void setProvinceName(Place somePlace) {
        /* don't need it. */
        if (somePlace.getAdmin1Name() != null) {
            return;
        }
        /* can't get it. no code */
        if (somePlace.getAdmin1() == null) {
            return;
        }

        Place adm1 = countries.getProvince(somePlace.getCountryCode(), somePlace.getAdmin1());
        if (adm1 != null) {
            somePlace.setAdmin1Name(adm1.getPlaceName());
            somePlace.setAdminName(adm1.getPlaceName());
        }
    }

    /**
     * Try to find closest P/PPL* (city or village) within 5 km. Or a local
     * site or landmark.
     * 
     * If not try at a radius = 10, then at 30 KM, and still if not, try a region, say ADM1
     * or ADM2 place boundary if one is nearby within 100 KM. If NOT, ... then
     * maybe you are in a remote, sparse territory or over water.
     * 
     * TODO: Province ID is helpful for many things -- missing ADM1 codes is a general problem.
     *   Fix missing ADM1 codes in gazetteer, e.g., use ESRI free data, geonames.org, etc.
     *   NOTE: there are not any missing ADM1 codes; USGS is solid.
     * 
     * TODO: Solr 4.x has a major memory problem when trying to find closest points.
     * In theory it should be indexed rather well, however for geodetic search it tries to load ALL 
     * the index for that 'geo' field into RAM (based on experience).  From there it can sort geodetically
     * to find a closest point.  This is not helpful -- so as a work around this recursive search outward
     * finds any items close by (SolrGazetteer.placeAt() sorts results outside of Solr). The issue is
     * that the search returns only first 25 rows of unsorted results, then sorts geodetically.
     * So for this work around try to minimize results by select feature types or something.
     * 
     *      
     * @param gaz
     *            an intialized SolrGazetteer
     * @param poi
     *            point of interest
     * @param requireADM1 true if ADM1 level resolution is desired.
     * @return a single place that appears to be closest to POI
     * 
     * @throws SolrServerException
     * @throws IOException
     */
    public Place inferPlaceRecursively(SolrGazetteer gaz, Geocoding poi, boolean requireADM1)
            throws SolrServerException, IOException {

        /*
         * Find city within 5KM
         */
        Place city = gaz.placeAt(poi, 5, "P");

        if (!requireADM1 && city != null) {
            return city;
        }
        /* 
         * Looking for a positive Province ID using ADM1, if requested.
         * This searches recursively until a good entry is found.
         */
        if (requireADM1 && city != null) {
            if (city.getAdmin1() != null) {
                return city;
            }
        }

        /*
         * Find site, place or admin boundary within 10KM
         */
        Place city2 = gaz.placeAt(poi, 10, "(P A)");

        /* Found something */
        if (!requireADM1 && city2 != null) {
            return city2;
        }

        /* Found something, but improve Province ID */
        if (requireADM1 && city2 != null) {
            if (city2.getAdmin1() != null && city != null) {
                city.setAdmin1(city2.getAdmin1());
                return city;
            }
        }

        /*
         * Find city within 30KM  -- If previous searches did not succeed with ADM1, 
         * use previously found entry by add ADM1 from here if possible.
         */
        Place city3 = gaz.placeAt(poi, 30, "(P A)");
        if (!requireADM1 && city3 != null) {
            return city3;
        }

        if (requireADM1 && city3 != null) {
            if (city3.getAdmin1() != null && city != null) {
                /*
                 * We've gone too far afield in search of province ID. 
                 * Return original city, which was closest. Caller may have to use ADM1=0 (country default)
                 */
                if (city.getCountryCode() != null) {
                    // At 30 KM, we're more cautious about inheriting CC across entries.
                    if (city.getCountryCode().equals(city3.getCountryCode())) {
                        city.setAdmin1(city3.getAdmin1());
                    }
                    // Otherwise ADM1 is not set, it was missing in original query.
                    // Return first choice as-is.
                    return city;
                }
            }
        }

        if (city != null) {
            return city;
        }

        /*
         * Anything? within 50km. Gave up on ADM1 requirement here... 
         */
        List<Place> anyPlaces = gaz.placesAt(poi, 50);
        if (anyPlaces != null) {
            city = SolrGazetteer.closest(poi, anyPlaces);
            if (city != null) {
                return city;
            }
        }

        /* Admin Region?
         */
        Place region = gaz.placeAt(poi, 100, "A");
        return region;
    }

    /**
     * facilitate getting a simple precision metric. +/- 1m is sufficient for
     * tracking points extracted from text.
     * 
     * @param geo
     * @param prec
     */
    protected static void flattenPrecision(Geocoding geo, GeocoordPrecision prec) {
        if (prec.precision < 1 && prec.precision > 0) {
            geo.setPrecision(1);
        } else {
            geo.setPrecision((int) prec.precision);
        }
    }
}
