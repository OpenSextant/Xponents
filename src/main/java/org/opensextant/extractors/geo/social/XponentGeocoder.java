package org.opensextant.extractors.geo.social;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.ConfigException;
import org.opensextant.data.*;
import org.opensextant.data.social.Message;
import org.opensextant.data.social.MessageParseException;
import org.opensextant.data.social.Tweet;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.extractors.geo.rules.GeocodeRule;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xcoord.XConstants;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.processing.Parameters;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static org.opensextant.util.GeodeticUtility.getFeaturePrecision;
import static org.opensextant.util.GeodeticUtility.isCoord;

/**
 * Pipeline focused on improving the location metadata for Tweets or Weibo or
 * other social media that has metadata about user or messaging location.
 * Assumptions: - microblog message has a User Profile or some subset of DeepEye
 * social media fields: 'ugeo*', 'geo*', etc.; See DeepEye social API for Tweet.
 * Tweet tw = DataUtility.fromDeepeye(R);
 *
 * @author ubaldino
 */
public class XponentGeocoder extends GeoInferencer {

    /**
     * For now "XpMeta" = geo processing tweets for province normalization. Any
     * possible geo indication is resolved down to a Province code.
     * "XpGeotag" = full text geotagging/geocoding.
     */
    public XponentGeocoder() {
        inferencerID = "XpMeta";
        inferencerDescription = "Geoparsing, Geocoding and ProvinceID of user and messages";
        infersAuthors = true;
        infersStatus = true;
        infersPlaces = false;
    }

    /**
     * does not infer place mentions from free text
     */
    @Override
    public Collection<GeoInference> geoinferencePlaceMentions(Tweet tw)
            throws MessageParseException, ExtractionException {
        return null;
    }

    /**
     * Renders a string buffer with a final report -- provided you set or
     * increment the totalRecords value.
     */
    @Override
    public String report() {
        return String.format("%s Total Records:%d, Has Coords:%2.0f%%, Has TZ:%2.0f%%, Has Place: %2.0f%%",
                this.inferencerID, totalRecords,
                pct(totalRecords, recordsWithCoord),
                pct(totalRecords, recordsWithTZ),
                pct(totalRecords, recordsWithPlace));
    }

    protected SolrGazetteer gazetteer = null;
    protected XCoord userlocX = null;
    protected PlaceGeocoder tagger = null;
    protected long recordsWithCoord = 0;
    protected long recordsWithTZ = 0;
    protected long recordsWithPlace = 0;

    /**
     * Xponents user "match filter" for PlaceGeocoder: Quickly filter out adhoc
     * social media noise. Items matched in tagger will be ignored as soon as
     * possible in pipeline hierarchy.
     */
    protected MatchFilter profilePlaceFilter = null;

    /**
     * Xponents user "geocoding rule" for PlaceGeocoder: custom metadata is fed
     * to tagger using this rule. Evaluation of match/geo candidates is done
     * here as we control tweet metadata evidence such as TZ, UTC offset,
     * obscure country evidence, Language possibilities, etc.
     */
    protected UserProfileLocationRule profileRule = null;

    /**
     * Makes use of a number of APIs:
     * <ul>
     * <li>XCoord to parse out additional coordinates not normalized</li>
     * <li>Gazetteer/GazetteerMatcher to resolve place identities by querying
     * them directly</li>
     * <li>PlaceGeocoder to parse longer phrases of multiple words, tagging
     * places so advanced rules could be applied to them.</li>
     * <li>LangID identifies language of text, although for short texts it does
     * not work reliably.</li>
     * <li>GeonamesUtility and TextUtils provide metadata lookup for countries,
     * timezone, language codes, etc.</li>
     * </ul>
     */
    @Override
    public void configure() throws ConfigException {

        /*
         * DeepEye Text provides a wrapper around Cybozu LangDetect
         */
        // langidTool = new LangDetect(AVERAGE_TEXT_SIZE);

        tagger = new PlaceGeocoder(true);// new GazetteerMatcher(true /*allow
                                         // lowercase tagging*/);
        tagger.enablePersonNameMatching(true);
        // If you really do not want to miss anything -- look at this flag:
        // tagger.setAllowLowerCaseAbbreviations(true);
        Parameters xponentsParams = new Parameters();
        xponentsParams.tag_coordinates = false; // Coords extracted separately
                                                // here by XCoord.

        /* "resolve_provinces" is equivalent to setProvinceName(Place) */
        xponentsParams.resolve_localities = true;
        tagger.setParameters(xponentsParams);
        tagger.setMatchFilter(profilePlaceFilter);
        tagger.configure();

        // Configure tagger first. Then add your custom rules on top of other
        // default rules.
        profileRule = new UserProfileLocationRule();
        tagger.addRule(profileRule);

        // Coordinate Extraction from tweets such as Uber e.g., "üt:lat,lon"
        // Or from coords in user profile.
        userlocX = new XCoord();

        /**
         * Xcoord parser; We expect only decimal degree (DD) patterns to appear
         * in text. If other patterns are found, then enable more. See Xponents
         * XCoord.
         */
        userlocX.configure(getClass().getResource("/twitter/tweet-xcoord.cfg"));
        userlocX.disableAll();
        // Explicitly enable DD

        // Note -- for parsing coordinates in Tweet metadata
        // we need to turn off the normal Decimal degree filters.
        // Decimal degrees are really the only thing we want out of tweets,
        // so we need to carefully undo DD filters.
        //
        userlocX.match_DD(true);
        XCoord.RUNTIME_FLAGS ^= XConstants.DD_FILTERS_ON; // Be less strict
        // with Decimal
        // degrees.
        // ignore
        // text
        // context.
        XCoord.RUNTIME_FLAGS ^= XConstants.FLAG_EXTRACT_CONTEXT;

        try {
            profilePlaceFilter = new MatchFilter("/twitter/exclude-tweet-profile-placenames.txt");

            // Country Metadata:
            /*
             * Xponents country listing is used throughout. (~ 250 )
             */
            countries = new GeonamesUtility();
            countries.loadCountryLanguages();
            countries.loadWorldAdmin1Metadata();

            /*
             * Pull in ALL country name variations from gazetteer ( ~2000 ).
             * NOTE: You still have 250 or so countries, but they now have more
             * complete name aliases/abbrev. in other languages.
             */
            gazetteer = tagger.getGazetteer();
            this.populateAllCountries(gazetteer);

        } catch (IOException err) {
            throw new ConfigException("IO Problems, possibly missing resource files.", err);
        }
    }

    @Override
    public void close() {
        this.tagger.close();
    }

    /**
     * Geoinference user/author profile. Standard 'deepeye' annotation is "ugeo"
     * or "country"
     */
    @Override
    public GeoInference geoinferenceTweetAuthor(Tweet tw) throws MessageParseException, ExtractionException {
        return processLocation(tw, (Place) tw.authorGeo, tw.id, "ugeo");
    }

    /**
     * Geoinference the location of the message, e.g., where the message was
     * sent from. Standard 'deepeye' annotation is "geo"; most message locations
     * are coordinates or hard locations.
     *
     * @param tw
     *           tweet as parsed by DeepEye
     * @return Geo or Country annotation
     * @throws MessageParseException
     *                               on parsing the tweet or JSON
     * @throws ExtractionException
     *                               on running geolocation routines
     */
    @Override
    public GeoInference geoinferenceTweetStatus(Tweet tw) throws MessageParseException, ExtractionException {
        return processLocation(tw, (Place) tw.statusGeo, tw.id, "geo");
    }

    /**
     * Not common, but useful. Improve location resolution via various tricks
     *
     * @param g
     */
    public void parseFreeTextCoordinates(Place g) {
        /*
         * Render obvious coordinates
         */
        if (g.getPlaceName() == null) {
            return;
        }
        List<TextMatch> coords = userlocX.extract(g.getPlaceName());
        if (coords != null && coords.size() > 0) {
            if (coords.size() > 1) {
                log.error("Incorrect assumption: Found multiple coordinates; Using first.");
            }

            for (TextMatch tm : coords) {
                if (tm.is_submatch) {
                    // Ignore partial match if block of text was long enough
                    // to have multiple potential coordinates.
                    continue;
                }
                GeocoordMatch xy = (GeocoordMatch) tm;

                g.setMethod(xy.getMethod());
                g.setLatLon(xy);

                flattenPrecision(g, xy.precision);
                break;
            }
        }
    }

    /**
     * Derive the Province ID if given a hard location.
     *
     * @param g
     * @return true if Place object was embued with a Province ID and Country ID
     *         if relevant.
     */
    public boolean provinceID(Place g) {
        try {
            // Find Cities located at or near XY -- infer from their ADM1 code,
            // the province in which they are located.
            Place city = inferPlaceRecursively(gazetteer, g, true);
            if (city == null) {
                log.debug("Location not found {}", g);
                return false;
            }
            // Backfill missing metadata.???
            g.setAdmin1(city.getAdmin1());
            g.setMethod("proximity-provinceID");
            if (g.getCountryCode() == null) {
                g.setCountryCode(city.getCountryCode());
                log.debug("Back fill empty CC on coordinate. {}", g);
            }
            return true;
        } catch (Exception err) {
            log.error("Geocoding Bug! " + g, err);
        }
        return false;
    }

    public int inferCountryTimezone(Tweet tw, Place g) throws ExtractionException {
        // Parse Timezone to get a city name/country.
        //
        // RESET?
        if (tw.timezone != null) {
            List<TextMatch> entities = tagger.extract(tw.timezone);
            if (entities != null) {
                Place chosen = null;
                int conf = -1;
                for (TextMatch e : entities) {
                    if (e.isFilteredOut()) {
                        continue;
                    }
                    if (!(e instanceof PlaceCandidate)) {
                        continue;
                    }
                    // Expecting exactly one choice.
                    // Break out of loop after getting a non-null choice.
                    PlaceCandidate chooser = (PlaceCandidate) e;
                    Place geo = chooser.getChosenPlace();
                    if (geo != null) {
                        chosen = geo;
                        conf = chosen.isCountry() ? DEFAULT_COUNTRY_CONF : chooser.getConfidence();
                        if (chosen.isCountry()) {
                            g.setMethod("geotag/tz/country");
                        } else {
                            g.setMethod("geotag/tz");
                        }
                        geocode(g, chosen);
                        return conf;
                        // NOTE: Accept only first match for Timezone
                    }
                }
            }
        }

        if (profileRule.inferredCountries == null) {
            return -1;
        }

        for (InferredCountry possibleCountry : profileRule.inferredCountries.values()) {
            // Hoaky, that we take the first found...
            if (possibleCountry.validMatch) {
                g.setMethod("tz/lang");
                g.setCountry(possibleCountry.country);
                return 25 + possibleCountry.score;
            }
        }
        return -1;
    }

    /**
     * Determine a starting set of countries -- if TZ/UTC is set, then use
     * that,... then improve scores where tweet language is spoken. Otherwise,
     * try where tweet lang is spoken.
     *
     * @param t
     * @return
     */
    public Map<String, InferredCountry> getInferredCountry(Tweet t) {
        Collection<String> countrySet = null;

        if (Message.validateUTCOffset(t.utcOffset)) {
            if (t.isDST) {
                countrySet = this.countries.countriesInDSTOffset(t.utcOffset);
            } else {
                countrySet = this.countries.countriesInUTCOffset(t.utcOffset);
            }
        } else if (t.timezone != null) {
            countrySet = this.countries.countriesInTimezone(t.timezone);
        } else {
            // Possibly lots of countries. E.g. lang = 'en' | 'fr' is not
            // helpful.
            if (t.lang.equals(t.userLang)) {
                countrySet = this.countries.countriesSpeaking(t.lang);
            } else {
                // Otherwise prefer the User-declared language.s
                countrySet = this.countries.countriesSpeaking(t.userLang);
            }
        }

        if (countrySet == null) {
            return null;
        }

        // TODO: Disambiguate multiple countries with same primary language and
        // TZ
        // That is, mark the highest scoring countries inferred by
        // TZ+UTC+Language
        //
        Map<String, InferredCountry> inferredCountry = new HashMap<>();

        int highScore = 0;
        for (String cc : countrySet) {

            Country c = this.countries.getCountry(cc);
            int cScore = this.scoreCountryPrediction(c, t);
            if (cScore > 0) {
                InferredCountry ic = new InferredCountry();
                ic.id = c.getCountryCode();
                ic.score = cScore;
                ic.country = c;
                inferredCountry.put(ic.id, ic);
                if (cScore > highScore) {
                    highScore = cScore;
                }
            }
        }

        if (inferredCountry.isEmpty()) {
            return null;
        }

        for (String c : inferredCountry.keySet()) {
            InferredCountry ic = inferredCountry.get(c);
            ic.validMatch = (ic.score == highScore);
        }
        return inferredCountry;
    }

    static class InferredCountry {
        String id = null;
        Country country = null;
        int score = 0;
        boolean validMatch = false;

        @Override
        public String toString() {
            return String.format("%s %d %s", id, score, validMatch);
        }
    }

    /**
     * Employ PlaceGeocoder rule(s): When this rule is added to PlaceGeocoder,
     * it can disambiguate and score found places in free text. Alternatively
     * the rule can be used directly with a particular PlaceCandidate.
     *
     * @author ubaldino
     */
    class UserProfileLocationRule extends GeocodeRule {

        public Map<String, InferredCountry> inferredCountries = null;
        public String timezone = null;
        public int utcOffset = Tweet.UNSET_UTC_OFFSET;
        public Tweet currentTweet = null;
        public String cc = null;
        public String adm1 = null;
        public boolean validTZ = false;
        public Place currentGeo = null;

        public void resetBefore(Tweet t, Place g) {
            currentTweet = t;
            inferredCountries = null;
            timezone = null;
            validTZ = Message.validTZ(currentTweet);
            if (g != null) {
                currentGeo = g;
                cc = g.getCountryCode();
                adm1 = g.getAdmin1();
                if (cc == null) {
                    if (validTZ) {
                        inferredCountries = getInferredCountry(currentTweet);
                    }
                }
            } else {
                cc = null;
                adm1 = null;
            }
        }

        final int UTC_LON_WINDOW = 5;

        /**
         * Custom rule evaluation. See comments below.
         *
         * @param name
         *                        - the candidate that builds up rules and scores.
         * @param geo
         *                        - the individual gazetteer entry being considered.
         */
        @Override
        public void evaluate(PlaceCandidate name, Place geo) {

            /*
             * DEFAULT SCORE. ============= see PlaceCandidate defaultScore() --
             * the score of geo relative to the name and apriori score impact
             * this default score, ie. PlaceCandidate.addPlace(geo) creates the
             * default score.
             */

            /*
             * FEATURE SCORE: ================ Rank higher places that are
             * P/PPL, A/ADM*, or L/land/area, etc. Ignore all other feature
             * types.
             * Rationale: Users type in short city or region name in user
             * profile. These are the most common features classes. H, R, T, V,
             * S can be ignored
             * POINTS: +5
             */
            if (isUsableFeature(geo.getFeatureClass())) {
                name.incrementPlaceScore(geo, 5.0, "SocGeo:Feature");
            } else {
                return;
            }

            log.debug("Place {} ", geo);

            /*
             * UTC + LON SCORE: ================= Places that are close (~ +/-10
             * deg) to the UTC offset in the tweet are more likely to be the
             * user location. UTC offset is approx 1 hr per 15 deg longitude.
             * Ex. Boston (71W), GMT-0500 = 5 * -15 = -75. This is about within
             * 5 deg.
             * If date of tweet occurs during Daylight savings (DST), then we
             * would want to try to use the normal UTC offset, e.g., GMT+0500
             * not GMT+0400. DST would yield a lon of 60 deg in this case. This
             * affects 6 months of the year.
             * TOOD: Use of DST is country and TZ specific... so given the
             * country of the geo, find the timezone (using TZ label and DST
             * offset). If the current date/time is DST, then use the UTC offset
             * on that TZ instead of that from the tweet.
             * For worldwide coverage this is more than using DST - 1hr.
             * POINTS +3
             */
            if (this.validTZ) {
                int utc_lon = GeonamesUtility.approximateLongitudeForUTCOffset(currentTweet.utcOffsetHours);
                if (Math.abs(geo.getLongitude() - utc_lon) < UTC_LON_WINDOW) {
                    name.incrementPlaceScore(geo, 3.0, "SocGeo:TZ+Lon");
                }
            }

            /*
             * GEOGRAPHIC SCORE: ================= If tweet contains metadata
             * indicating country/state or country/state/county for example,
             * then any gazetteer entry matching that profile scores very high.
             * The hierarchical path is /CC/ADM1.
             * If only country is given, then simply score gazetteer entries
             * from that country higher.
             * POINTS +15 for /CC/ADM1 match + 5 for /CC match
             */
            if (currentGeo != null) {
                if (currentGeo.getHierarchicalPath() != null) {
                    if (currentGeo.getHierarchicalPath().equals(geo.getHierarchicalPath())) {
                        name.incrementPlaceScore(geo, 15.0, "SocGeo:Country+Admin1");
                        log.debug("\tadd HASC evidence");
                    }
                }
            } else if (this.cc != null) {
                if (cc.equals(geo.getCountryCode())) {
                    name.incrementPlaceScore(geo, 5.0, "SocGeo:Country");
                    log.debug("\tadd CC");
                }
            }

            /*
             * TZ+LANG+COUNTRY SCORE: ====================== Where only language
             * (of user and of text) are given and/or Timezone/UTC, we can infer
             * a narrow set of countries. That is:
             * - Countries whose primary language matches that of the Tweet -
             * Countries that contain the Timezone/UTC offset of the Tweet
             * POINTS +1 to +11, based on evidence
             */
            if (this.inferredCountries != null) {
                InferredCountry ic = inferredCountries.get(geo.getCountryCode());
                if (ic != null && ic.validMatch) {
                    name.incrementPlaceScore(geo, (double) ic.score, "SocGeo:TZ+Lang");
                    log.debug("\tadd TZ+Lang evidence");
                }
            }
        }
    }

    /**
     * Use geographic hierarchy to find province related to this place. When
     * standard hierarchy look fails, try tagging name value as free text
     *
     * @param g
     *          place that has some name/prov/country or name/ADM1/CC hiearchy
     * @return confidence. greater than 0 means something was found.
     * @throws ExtractionException
     */
    public int inferProvinceByHierarchy(Tweet tw, Place g) throws ExtractionException {

        String cc = g.getCountryCode();
        String adm1 = g.getAdmin1();
        String prov = g.getAdminName();
        if (prov == null) {
            prov = g.getAdmin1Name();
        }
        String name = g.getPlaceName();

        // Filter for lack of evidence:
        //
        if (adm1 == null && prov == null && name == null) {
            // log.debug("Looks like a country and that is it? {}", g);
            // No info to work with.
            // These cases fall back to ADNA or other means.
            return 0;
        }

        // Filter for name:
        if (name != null) {
            name = filterOutName(name.toLowerCase());
        }
        if (name == null) {
            return 0;
        }

        // Using original text as user entered.
        // Above non-sense was just to filter out noise.
        name = g.getPlaceName();

        /*
         * Try different parametric searches. Remember you are here because we
         * have no coordinate. Inferring actual location is a fuzzy business.
         */
        /*
         * First query: Find Province.
         */

        int conf = -1;
        Place chosen = null;
        List<TextMatch> entities = null;
        /*
         * LANG ID of profile geography
         */
        // Cleanup name -- remove punct and white space.
        boolean hasDigits = TextUtils.hasDigits(name);
        boolean isASCII = TextUtils.isASCII(name);
        boolean isCJK = false;
        boolean isArabic = false;

        String langOfName = "xx";

        if (!isASCII) {
            isCJK = TextUtils.hasCJKText(name);

            if (!isCJK) {
                Language L = this.langidTool.detectSocialMediaLang(null, name, true);
                if (L != null) {
                    langOfName = L.getCode();
                    isArabic = "ar".equalsIgnoreCase(L.getCode());
                }
            }
        }

        /*
         * PROVINCE ID -- make use of given province name to help narrow down
         * possible city or other matches Rare.
         */
        if (prov != null && adm1 == null) {
            try {
                /*
                 * This logic is geared towards semi-structured location
                 * metadata from soc media, e.g., Weibo or other data feeds from
                 * providers. Language of Province name requires special
                 * handling to get right.
                 */
                adm1 = inferProvinceByName(prov, cc);
                if (adm1 != null) {
                    g.setAdmin1(adm1); // Found likely province.
                }
            } catch (Exception err) {
                log.error("Failed to find province ID given metadata", err);
            }
        }

        // Assemble custom profile location rules prior to geocoding.
        // This only applies to use of PlaceGeocoder.
        this.profileRule.resetBefore(tw, g);

        /*
         * PLACE GEOTAGGING: identify possible places
         * Language-specific
         */

        // Still searching. Parse name as a very short document.
        //
        // TODO: Bug? NAME1.NAME2.NAME3 yields nothing, as solrtextagger does
        // not parse internally "TEXT.TEXT" is one token.
        // new: NAME1. NAME2. NAME3 is three tokens now. Litterally, '.' is the
        // only special char here.
        // NAME1/NAME2/NAME3 is three separate tokens.
        // NAME1,NAME2,NAME3 is also three separate tokens, but ',' is special
        // separator for geocoding.
        //
        String nameAdjusted = TextUtils.fast_replace(name, ".", ". ").replace('\\', ' ');
        TextInput lastTry = new TextInput(tw.id, nameAdjusted);

        String gazNameField = "name";
        if (isCJK) {
            lastTry.langid = "zh"; // Should verify language and use zh, ja, ko
                                   // instead of 'cjk' or 'zh' for all.
            gazNameField = "name_cjk";
        } else if (isArabic) {
            lastTry.langid = "ar"; // langid tools may return 'msa' or other
                                   // MidEast script/langIDs.
            gazNameField = "name_ar";
        }

        if (!hasDigits) {
            conf = inferPlaceByName(nameAdjusted, g, gazNameField);
            if (conf > 0) {
                return conf;
            }
        }

        conf = -1;

        /*
         * Other direct inferencing has not worked, so tree the text of the
         * user's location as free text. It likely contains multiple names,
         * numbers, non-places, and other descriptive words and symbols.
         */
        try {
            entities = tagger.extract(lastTry);
            if (entities != null) {
                for (TextMatch e : entities) {

                    if (e.isFilteredOut()) {
                        continue;
                    }
                    if (!(e instanceof PlaceCandidate)) {
                        continue;
                    }
                    PlaceCandidate chooser = (PlaceCandidate) e;

                    if (chosen != null) {
                        log.debug("Ignored 2nd place in free text:{}@{}", e.getText(), chooser.getChosen());
                        continue;
                    }

                    chosen = chooser.getChosenPlace();
                    if (chosen != null) {
                        conf = chosen.isCountry() ? DEFAULT_COUNTRY_CONF : chooser.getConfidence();
                        g.setMethod(chosen.isCountry() ? "geotag/freetext/country" : "geotag/freetext");
                    }
                    // TODO: ACCEPT ALL matched place names in free-text
                    // descriptions
                }
            }
        } catch (Exception err) {
            log.error(String.format("Unable to parse %s / name=%s", g, name), err);
        }

        // Hard stop. Nothing found yet, and we are out of metadata to test and
        // parse.
        if (chosen == null) {
            return -1;
        }

        log.debug("Search for {};  inferred {} with rules {}", name, chosen, g.getMethod());

        // NOTE -- Reuse the given geocoding, g, and enrich with matching
        // gazetteer entry

        // Final geocoding: CC, ADM1, feature class, feature type (from
        // gazeetter)
        geocode(g, chosen);

        /*
         * VALIDATION ========== if chosen place does not line up with inferred
         * countries based on TZ/lang/cc then highlight that here.
         */
        if (profileRule.inferredCountries != null) {
            InferredCountry ic = profileRule.inferredCountries.get(chosen.getCountryCode());
            boolean mismatch = (ic == null);
            boolean valid = (ic != null && ic.validMatch);
            if (!valid) {
                if (!"US".equalsIgnoreCase(chosen.getCountryCode())) {
                    log.debug("\t Stop here, tweet={}", tw);
                }
                log.debug("\tSurprise! R={} (lang={}, tz={}) Chosen country does not match inferred {}", tw.id, tw.lang,
                        tw.timezone, chosen.getCountryCode());
            } else if (mismatch) {
                log.info("\tOdd! Chosen R={}, geo={} was not inferred at all.", tw.id, chosen);
            }
        }

        return conf;
    }

    /**
     * Special prequisites -- profileRule.reset() must be done before this is called.
     *
     * @param nmGiven
     *                     the name parse and cleaned ahead of time.
     * @param g
     *                     the place to be geocoded.
     * @param fld
     *                     the solr schema name to be used.
     * @return
     */
    private int inferPlaceByName(String nmGiven, Place g, String fld) {
        int conf = -1;
        try {

            String q = null;
            /*
             * Taking the liberty to use arbitrary punctuation ; to replace special chars
             * Even if there are special chars we can still get something.
             * ... But seriously, if you have a lot of punctuation in a given name,
             * this is not likely to end with anything meaningful. Just sayin'
             * special crap chars;
             * \
             * "
             */
            String nm = TextUtils.fast_replace(nmGiven, "\\\"", ";");
            if (g.getCountryCode() != null) {
                q = String.format("%s:\"%s\" AND feat_class:(P A L) AND cc:%s", fld, nm, g.getCountryCode());
            } else {
                q = String.format("%s:\"%s\" AND feat_class:(P A L)", fld, nm);
            }

            List<Place> places = tagger.searchAdvanced(q, true, nm.length() + 3);
            if (!places.isEmpty()) {
                PlaceCandidate pc = new PlaceCandidate();
                pc.setText(nm);
                // TOOD: assess text sense -- if context is UPPER, lower or Mixed.
                pc.inferTextSense(false, false);
                ScoredPlace chosenScore = null;

                for (Place p : places) {
                    ScoredPlace entry = new ScoredPlace(p.getPlaceID(), p.getName());
                    entry.setPlace(p);
                    pc.addPlace(entry); /* Review if addPlace(ScoredPlace) is needed for this eval */
                    profileRule.evaluate(pc, p);
                }

                /*
                 * Once we choose() we can then get the outcome from that
                 * scoring and choosing...
                 */
                pc.choose();
                chosenScore = pc.getChosen();

                if (chosenScore != null) {

                    geocode(g, chosenScore.getPlace());
                    g.setMethod("geotag/lookup");

                    // Starting confidence.
                    conf = (g.getCountryCode() != null ? DEFAULT_COUNTRY_CONF : 50);
                    // Confidence detractors: lots of places Detract a portion
                    // of confidence for lots of matches.
                    conf = conf - (int) (0.1 * places.size());
                    if (profileRule.inferredCountries != null) {
                        conf = conf - (int) (0.1 * profileRule.inferredCountries.size());
                    }

                    conf += chosenScore.getScore();
                    return conf;
                }
            }

        } catch (Exception err) {
            log.debug("Query Parsing Error", err);
        }
        return conf;
    }

    /**
     * Copy relevant metadata onto the place. E.g., the given place is to be
     * geocoded with the chosen place.
     */
    private void geocode(Place g, Place chosen) {
        g.setCountryCode(chosen.getCountryCode());
        g.setCountry(chosen.getCountry());
        g.setAdmin1(chosen.getAdmin1());
        g.setFeatureClass(chosen.getFeatureClass());
        g.setFeatureCode(chosen.getFeatureCode());

        g.setPlaceID(chosen.getPlaceID());
        g.setPrecision(getFeaturePrecision(g.getFeatureClass(), g.getFeatureCode()));
        if (isCoord(chosen) && !chosen.isCountry()) {
            // Some gazetteer guesses are composite and may not have a location
            // at all. Just best guess at country and name.
            g.setLatLon(chosen);
        }
    }

    /**
     * Trivial name filters.
     *
     * @param lowercaseName
     * @return
     */
    private String filterOutName(String lowercaseName) {
        if (lowercaseName.contains("http")) {
            return null;
        }

        if (profilePlaceFilter.filterOut(lowercaseName)) {
            log.debug("Excluded place {}", lowercaseName);
            return null;
        }

        if (lowercaseName.contains(":")) {
            // Name has obscure punctuation...not worth the time.
            return null;
        }
        // If content is mainly digits or contains say 6 or more digits, its not
        // likely to be geo information
        // If it was something like an app geo in place field e.g. "XX lat,
        // lon", then we'd have it from XCoord parsing above.
        //
        if (TextUtils.countDigits(lowercaseName) > 5) {
            return null;
        }

        String newName = TextUtils.squeeze_whitespace(TextUtils.fast_replace(lowercaseName, "/\\#;", " ")).trim();
        if (newName.length() < 2) {
            return null;
        }

        return newName;
    }

    /**
     * This is used only if we have a province name and no ADM1 code. Try to
     * find the ADM1 code for the user profile, first. then that will help
     * refine actual selection of a city of residence.
     *
     * @param prov province ADM1 query
     * @param cc   country code
     * @return Province code, ADM1
     * @throws SolrServerException
     */
    private String inferProvinceByName(String prov, String cc) throws SolrServerException, IOException {
        StringBuilder query = new StringBuilder();

        query.append(" +feat_class:A AND +feat_code:ADM1");
        if (TextUtils.hasCJKText(prov)) {
            query.append(String.format(" AND +name_cjk:\"%s\"", prov));
        } else {
            query.append(String.format(" AND +name:\"%s\"", prov));
        }
        if (cc != null) {
            query.append(String.format(" AND +cc:%s", cc));
        }
        List<Place> matches = gazetteer.search(query.toString(), true); // Full
                                                                        // solr
                                                                        // query
        for (Place adm1Place : matches) {
            log.debug("Matched PROV {} =? {}", prov, adm1Place);

            if (adm1Place.getName().equalsIgnoreCase(prov)) {
                /*
                 * TODO: evaluate all matches before returning or setting a
                 * value.
                 */
                return adm1Place.getAdmin1();
            }
        }
        return null;
    }

    private static final Set<String> usableFeatures = new HashSet<>();

    static {
        usableFeatures.add("A");
        usableFeatures.add("P");
        usableFeatures.add("S");
        usableFeatures.add("L");
    }

    private static boolean isUsableFeature(String f) {
        return usableFeatures.contains(f);
    }

    static Set<String> allowedCountryCodeNames = new HashSet<>();
    static Set<String> disallowedCountryNames = new HashSet<>();

    static {
        allowedCountryCodeNames.add("us");
        allowedCountryCodeNames.add("usa");
        allowedCountryCodeNames.add("uae");
        allowedCountryCodeNames.add("uk");
        allowedCountryCodeNames.add("gb");
        allowedCountryCodeNames.add("gbr");

        // As ASCII names, do not code these as countries and exit early.
        // Assess all other geo metadata before making a conclusion.
        disallowedCountryNames.add("georgia");
        disallowedCountryNames.add("jersey");
    }

    public static final int DEFAULT_COUNTRY_CONF = 75;

    /**
     * Trivial test to see if provided place description is as simple as a
     * country name, rather than a description of a place or non-place. This is
     * just a lookup, not a tagger.
     * Place g is coded with the found country.
     *
     * @param g
     *          given geo text
     * @return if g could be geocoded with country.
     */
    public int inferCountryName(Geocoding g) {
        if (!isValue(g.getPlaceName())) {
            return -1;
        }
        /*
         * For this situation, if the actual name given is just a country code
         * or name, we will use that. If the name is something else it should
         * pass through here quickly.
         */
        String namenorm = g.getPlaceName().trim().toLowerCase();
        if (disallowedCountryNames.contains(namenorm)) {
            // Small chance this place name is ambiguously a country name.
            // testLikelyCountry = false;
            return 0;
        }
        // Country ID by name is lower case
        Country C = getCountryNamed(namenorm);
        if (C != null) {
            g.setMethod("name/country");
            g.setCountry(C);
            return DEFAULT_COUNTRY_CONF;
        }
        namenorm = removePunct(namenorm);
        C = getCountryNamed(namenorm);
        if (C != null) {
            g.setMethod("name/country");
            g.setCountry(C);
            return DEFAULT_COUNTRY_CONF;
        }
        return 0;
    }

    private static final Pattern remove_punct = Pattern.compile("\\.");

    public static final String removePunct(String s) {
        return remove_punct.matcher(s).replaceAll("");
    }

    /**
     * Detailed routine to uncover additional location information in tweet noise.
     * Since SocGeo 1.13.8 we try to set a Province name in addition to ADM1 ID
     *
     * @param tw
     *                  tweet
     * @param g
     *                  a location on tweet, geo or user geo (ugeo)
     * @param rid
     *                  Record ID from deepeye or other data ID.
     * @param annotName
     *                  annotation type to store.
     * @throws ExtractionException
     */
    public GeoInference processLocation(Tweet tw, Place g, String rid, String annotName) throws ExtractionException {
        /*
         * GIVEN
         * =====================
         * Place g represents the given geo metadata.
         * The objective is to geocode g directly, that is reset geocoding fields.
         */
        if (g == null) {
            return null;
        }
        // This data is the given geo information
        // The final method will be set to the name of the rule that was best suited to
        // he geo-inference.
        //
        g.setMethod("given");

        int confidence = 0;
        boolean resolved = false;

        /*
         * INFER: COUNTRY NAME from free text. And optionally capture Lat/Lon if
         * possible.
         * =====================
         */
        if ((confidence = inferCountryName(g)) > 0) {
            GeoInference G = new GeoInference();
            G.recordId = rid;
            G.contributor = inferencerID;
            if (isCoord(g)) {
                ++recordsWithCoord;
                G.confidence += 10;
                /*
                 * INFER: PROVINCE ID
                 * =====================
                 */
                if (provinceID(g)) {
                    // Note - confidence here is not that high, if user
                    // enters "name of country" in their profile, and have GPS turned on or not
                    // then they may have just chosen center-of-country as lat/lon location.
                    G.confidence += 10;
                    setProvinceName(g);
                    resolved = true;
                }
                G.inferenceName = annotName;
                G.geocode = g;
                return G;
            }

            G.confidence = confidence;
            G.inferenceName = "country";
            G.geocode = g;
            log.debug("Lookup Chooser: Chose country {} ", g);
            return G;
        }

        /*
         * PARSE: COORDINATES
         * =====================
         */
        parseFreeTextCoordinates(g);

        /*
         * Resolve coordinates to a location.. near a city,... in a province... in a
         * country
         * Possibly over water, e.g, I'm on a cruise.
         * Objective -- Province ID, via reverse lookup.
         */
        if (isCoord(g)) {
            ++recordsWithCoord;
            /*
             * INFER: PROVINCE ID
             * =====================
             */
            if (provinceID(g)) {
                confidence = 90;
                resolved = true;
            }
        } else {
            /*
             * INFER and/or PARSE: Given the geo, TZ, text language and user language. and
             * other data.
             * =====================
             * Note -- this works if name of place and province are simple phrases.
             * If it is a description, then the place must be tagged. Choosing the best
             * location
             * involves looking at TZ, UTC, and langauge where possible.
             */
            confidence = inferProvinceByHierarchy(tw, g);
            resolved = confidence > 0;
            if (resolved) {
                ++this.recordsWithPlace;
            }
        }

        /*
         * INFER: last resort, guess at country at least using TZ and Language.
         * =====================
         */
        if (Message.validTZ(tw)) {
            ++this.recordsWithTZ;
            if (!resolved) {
                confidence = inferCountryTimezone(tw, g);
                resolved = confidence > 0;
            }
        }

        // A mere convention -- if method yields only a country, then emit only a
        // Country annot, not a full geocode.
        if (g.getMethod().endsWith("country")) {
            annotName = "country";
        }

        /*
         * Only if you were able to resolve something then should you create an
         * annotation.
         */
        if (!resolved) {
            log.debug("\tUnresolved geo for: R={}, geo={}, tz={}", tw.id, g, tw.timezone);
            return null;
        }

        setProvinceName(g);

        GeoInference G = new GeoInference();
        G.contributor = inferencerID;
        G.recordId = rid;
        G.inferenceName = annotName;
        G.confidence = confidence;
        G.geocode = g;

        return G;
    }

    /**
     * Geocoder does not return Additional matches.
     */
    @Override
    public Collection<TextMatch> getAdditionalMatches() {
        return null;
    }

}
