/*
 *
 * Copyright 2013-2021 The MITRE Corporation.
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
///~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
//  _____                                ____                     __                       __
// /\  __`\                             /\  _`\                  /\ \__                   /\ \__
// \ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
//  \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//   \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//    \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//     \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//               \ \_\
//                \/_/
//
// OpenSextant PlaceGeocoder (Xponents)
// Copyright MITRE 2013-2021
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */
package org.opensextant.extractors.geo;

import java.io.IOException;
import java.util.*;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.ConfigException;
import org.opensextant.data.*;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.ExtractionMetrics;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.rules.*;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.processing.Parameters;
import org.opensextant.util.GeonamesUtility;
import org.slf4j.LoggerFactory;

/**
 * A simple variation on the geocoding algorithms: geotag all possible things
 * and determine a best
 * geo-location for each tagged item. This uses the following components:
 * <ul>
 * <li>PlacenameMatcher: place name tagging and gazetteering</li>
 * <li>XCoord: coordinate extraction</li>
 * <li>geo.rules.* pkg: disambiguation rules to choose the best location for
 * tagged names</li>
 * </ul>
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class PlaceGeocoder extends GazetteerMatcher
        implements Extractor, CountryObserver, BoundaryObserver, LocationObserver {

    public static final String VERSION = "3.6";
    public static final String METHOD_DEFAULT = String.format("PlaceGeocoder v%s", VERSION);

    /**
     * Resources and Taggers
     */
    private XCoord xcoord = null;
    private PersonNameFilter personNameRule = null;
    private TaxonMatcher taxonTagger = null;
    private Map<String, Country> countryCatalog = null;
    private GeonamesUtility nameHelper = null;
    public final Set<String> taxonCatalogs = new HashSet<>();
    private final ExtractionMetrics taggingTimes = new ExtractionMetrics("tagging");
    private final ExtractionMetrics matcherTotalTimes = new ExtractionMetrics("matcher-total");

    /**
     * Rules -- specific ones that are globals. Generic rules that have no state or
     * order are added to this.rules array
     */
    private CountryRule countryRule = null;
    private CoordinateAssociationRule coordRule = null;
    private ProvinceAssociationRule adm1Rule = null;
    private NameCodeRule nameWithAdminRule = null;
    private LocationChooserRule chooser = null;
    private ProvinceNameSetter provinceNameSetter = null;

    /**
     * A default Geocoding app that demonstrates how to invoke the geocoding pipline
     * start to finish. It makes use of XCoord to parse/geocode coordinates,
     * SolrGazetteer/GazetteerMatcher to match named
     * places, XTax to tag person names. Match Filters and rules work in conjunction
     * to filter and tag further any candidates.
     *
     * @throws ConfigException if resource files could not be found in CLASSPATH
     */
    public PlaceGeocoder() throws ConfigException {
        super();
        log = LoggerFactory.getLogger(getClass());
    }

    /**
     * @param lowercaseAllowed if lower case abbreviations are allowed. See
     *                         GazetteerMatcher
     * @throws ConfigException if resource files could not be found in CLASSPATH
     */
    public PlaceGeocoder(boolean lowercaseAllowed) throws ConfigException {
        super(lowercaseAllowed);
        log = LoggerFactory.getLogger(getClass());
    }

    /*
     * ordered list of rules.
     */
    private final List<GeocodeRule> rules = new ArrayList<>();

    @Override
    public String getName() {
        return "Advanced PlaceGeocoder";
    }

    /**
     * Configure an Extractor using a config file named by a path.
     *
     * @param patfile configuration file path
     * @throws ConfigException on err
     */
    @Override
    public void configure(String patfile) throws ConfigException {
        throw new ConfigException("Configure by path Not available");
    }

    /**
     * Configure an Extractor using a config file named by a URL
     *
     * @param patfile configuration URL
     */
    @Override
    public void configure(java.net.URL patfile) throws ConfigException {
        throw new ConfigException("Configure by URL Not available");
    }

    /**
     * We have some emerging metrics to report out...
     */
    public void reportMetrics() {
        log.info("=======================\nTAGGING METRICS");
        log.info(taggingTimes.toString());
        log.info(matcherTotalTimes.toString());
    }

    /**
     * We do whatever is needed to init resources... that varies depending on the
     * use case.
     * Guidelines: this class is custodian of the app controller, Corpus feeder, and
     * any Document instances passed into/out of the feeder.
     * This geocoder requires a default /exclusions/person-name-filter.txt, which
     * can be empty, but most often it will be a list of person names (which are non-place names)
     * Rules Configured in approximate order:
     * <ul>
     * <li>CountryRule -- tag all country names</li>
     * <li>NameCodeRule -- parse any Name, CODE, or Name1, Name2 patterns for
     * "Place, AdminPlace" evidence</li>
     * <li>PersonNameRule -- annotate, negate any patterns or matches that appear to
     * be known celebrity
     * persons or organizations. Qualified places are not negated, e.g., "Euguene,
     * Oregon" is a place;
     * "Euguene" with no other evidence is a person name.</li>
     * <li>CoordRule -- if requested, parse any coordinate patterns; Reverse geocode
     * Country + Province.</li>
     * <li>ProvinceAssociationRule -- associate places with Province inferred by
     * coordinates.</li>
     * <li>MajorPlaceRule -- identify major places by feature type, class or
     * location population.</li>
     * <li>LocationChooserRule -- final rule that assigns confidence and chooses
     * best location(s)</li>
     * </ul>
     * Your Rule Here -- use addRule( GeocodeRule ) to add a rule on the stack. It
     * will be evaluated just before the final LocationChooserRule. your rule should improve Place
     * scores on PlaceCandidates and name the rules that fire.
     *
     * @throws ConfigException on err
     */
    @Override
    public void configure() throws ConfigException {

        // ==============
        // Rule setup: Create GeocodeRules, add them to this.rules if they can be
        // evaluated generically on a list of place tags.
        // Otherwise such rules are configured, set during the request, and evaluated
        // adhoc as you need.
        //
        /* assess country names and codes */
        countryRule = new CountryRule();
        countryRule.setCountryObserver(this);

        /* assess NAME, CODE patterns */
        nameWithAdminRule = new NameCodeRule(this.taggingParams);
        nameWithAdminRule.setBoundaryObserver(this);
        nameWithAdminRule.setCountryObserver(this);

        // Nonsense is filtered out, rather than scored and ranked low.
        addRule(new NonsenseFilter());

        /*
         * Files for Place Name filter are editable, as you likely have different ideas of who are
         * "person names" to exclude when they conflict with place names. If you are filtering
         * out such things, then it makes sense to filter them out earliest and not incorporate them in geocoding.
         */
        personNameRule = new PersonNameFilter("/filters/person-name-filter.txt",
                "/filters/person-title-filter.txt",
                "/filters/person-suffix-filter.txt");

        /*
         * assess coordinates related to ADM1, CC
         */
        coordRule = new CoordinateAssociationRule();
        coordRule.setCountryObserver(this);
        coordRule.setLocationObserver(this);

        if (xcoord == null && (isCoordExtractionEnabled())) {
            xcoord = new XCoord();
            xcoord.configure();

            /*
             * assess ADM1 related to found NAMES as a result of coordinates
             */
            adm1Rule = new ProvinceAssociationRule();
            adm1Rule.setCountryObserver(this);

            addRule(coordRule);
            addRule(adm1Rule);
        }

        // Major Places
        try {
            Map<String, Integer> popstats = GeonamesUtility
                    .mapPopulationByLocation(GeonamesUtility.loadMajorCities("/geonames.org/cities15000.txt"));
            MajorPlaceRule majorPlaceRule = new MajorPlaceRule(popstats);
            majorPlaceRule.setCountryObserver(this);
            majorPlaceRule.setBoundaryObserver(this);
            addRule(majorPlaceRule);
        } catch (IOException err) {
            throw new ConfigException("Missing City population data", err);
        }

        /*
         * Account for situations like "Eugene, OR" person name followed by a stopword.
         * Valid, fully-qualified city name so we have to allow it to be evaluated first
         * before filtering it out.
         */
        addRule(personNameRule);

        // Names of Orgs and Persons.
        //
        try {
            // This matcher will return everything, but for Geotags it
            // will filter out Persons & Orgs from catalogs below.
            taxonTagger = new TaxonMatcher();
            taxonTagger.excludeTaxons("place."); /* but allow org., person., etc. */
            /*
             * Default catalog must be built. Extraction ./XTax folder has script for
             * populating a catalog.
             */
            taxonCatalogs.add("JRC");
            taxonCatalogs.add("nationality");
            taxonCatalogs.add("person_names");

        } catch (IOException err) {
            throw new ConfigException("XTax resource not available.");
        }

        // Un-filter city names that can be resolved if other ADMIN places line up.
        // E.g., "Cleveland Caveliers" filters out Cleveland, but if Cleveland is
        // mentioned alone then Cleveland itself will be promoted to a location. E.g., sports teams
        // travel so mention of "Cleveland Caveliers visiting Seattle" would not geolocate this
        // to Ohio unless the city or state was mentioned separately.
        //
        ContextualOrganizationRule placeInOrgRule = new ContextualOrganizationRule();
        placeInOrgRule.setBoundaryObserver(this);
        addRule(placeInOrgRule);

        // Simple patterns such as city of x or abc county.
        //
        addRule(new NameRule());

        HeatMapRule heatmapper = new HeatMapRule();
        addRule(heatmapper);
        heatmapper.setCountryObserver(this);

        chooser = new LocationChooserRule();
        chooser.setDefaultMethod(METHOD_DEFAULT);
        chooser.setCountryObserver(this);
        chooser.setBoundaryObserver(this);
        chooser.setLocationObserver(this);

        countryCatalog = this.getGazetteer().getCountries();

        if (taggingParams.resolve_localities) {
            try {
                nameHelper = new GeonamesUtility();
                nameHelper.loadWorldAdmin1Metadata();
                provinceNameSetter = new ProvinceNameSetter(nameHelper);
            } catch (Exception namesErr) {
                throw new ConfigException("Failed to load names of ADM1 boundaries", namesErr);
            }
        }
    }

    /**
     * Add your own geocode rules to enable you to add evidence, adjust score,
     * outright choose Place instances on PlaceCandidates, etc. As long as your rule implements or
     * overrides GeocodeRule.evaluate() methods candidate tags will be fully evaluated.
     *
     * @param r a rule
     */
    public void addRule(GeocodeRule r) {
        rules.add(r);
    }

    /**
     * You don't like the default rule set,.. add your own
     */
    public void setRules(List<GeocodeRule> rlist) {
        rules.clear();
        rules.addAll(rlist);
    }

    /**
     * Please shutdown the application cleanly when done.
     */
    @Override
    public void cleanup() {
        reportMetrics();
        close();
    }

    @Override
    public void close() {
        super.close();
        if (taxonTagger != null) {
            taxonTagger.close();
        }
    }

    private Parameters taggingParams = new Parameters();

    public void setParameters(Parameters p) {
        taggingParams = p;
        taggingParams.isdefault = false;
    }

    public boolean isCoordExtractionEnabled() {
        return taggingParams.tag_coordinates;
    }

    /**
     * Person name matching is ALWAYS on,
     * this flag indicates if results are reported in returned array
     * @deprecated This has no effect. Tagging Parameters here are mainly considered for filtering output.
     * @return
     */
    @Deprecated
    public boolean isPersonNameMatchingEnabled() {
        return taggingParams.tag_names;
    }

    /**
     * @deprecated
     * @param b
     */
    @Deprecated
    public void enablePersonNameMatching(boolean b) {
        taggingParams.tag_names = b;
    }

    private void reset() {
        this.relevantCountries.clear();
        this.relevantProvinces.clear();
        this.relevantLocations.clear();

        personNameRule.reset();
        countryRule.reset();
        chooser.reset();
        for (GeocodeRule r : rules) {
            r.reset();
        }
    }

    /**
     * Countries mentioned, inferred, or otherwise relevant
     */
    private final Map<String, CountryCount> relevantCountries = new HashMap<>();
    /**
     * Provinces mentioned, inferred or otherwise relevant.
     */
    private final Map<String, PlaceCount> relevantProvinces = new HashMap<>();
    /**
     * Places inferred by their proximity to concrete coordinate references.
     */
    private final Map<String, Place> relevantLocations = new HashMap<>();

    /**
     * See {@link #extract(TextInput, Parameters)} below.
     * This is the default extraction routine. If you need to tune extraction call
     * <code>extract( input, parameters ) </code>
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        return extract(input, null);
    }

    /**
     * Extractor.extract() calls first XCoord to get coordinates, then
     * PlacenameMatcher In the end you
     * have all geo entities ranked and scored.
     * LangID can be set on TextInput input.langid. Only lowercase langIDs please:
     * 'zh', 'ar', tag text
     * for those languages in particular. Null and Other values are treated as
     * generic as of v2.8.
     * <p>
     * Use TextMatch.getType() to determine how to interpret TextMatch / Geocoding
     * results:
     * <ul>
     * <li>Given TextMatch match, then</li>
     * <li>Place tag: ((PlaceCandiate)match).getChosen() OR</li>
     * <li>Coord tag: (Geocoding)match, OR</li>
     * <li>Other tag: match might be TaxonMatch or Pattern (PoliMatch)</li>
     * </ul>
     * Both methods yield a geocoding.
     *
     * @param input input buffer, doc ID, and optional langID.
     * @return TextMatch instances which are all PlaceCandidates.
     * @throws ExtractionException on err
     */
    public List<TextMatch> extract(TextInput input, Parameters jobParams) throws ExtractionException {
        boolean tagOnly = false;
        /*
        tagOnly:
        TODO: an option is to report TextMatches only and not geocode. This is not practical and not
        the main purpose of this API.  Straight up GazetteerMatcher could do this.
        */

        long t1 = System.currentTimeMillis();
        reset();

        if (jobParams != null) {
            this.setAllowLowerCase(jobParams.tag_lowercase);
        }

        List<TextMatch> matches = new ArrayList<>();

        // 0. GEOTAG raw text. Flag tag-only = false, in otherwords do extra work for
        // geocoding.
        //
        List<PlaceCandidate> candidates = tagText(input, tagOnly);

        // 1. COORDINATES. If caller thinks their data may have coordinates, then  attempt to parse lat/lon.
        // Any coordinates found fire rules for resolve lat/lon to a Province/Country if possible.
        //
        matches.addAll(parseGeoCoordinates(input));

        if (candidates == null) {
            return matches;
        }

        /*
         * 3.RULE EVALUATION: accumulate all the evidence from everything found so far.
         * Assemble some histograms to support some basic counts, weighting and sorting.
         * Rules: Work with observables first, then move onto associations between
         * candidates and more obscure fine tuning.
         * a. Country - named country weighs heavily;
         * b. Place, Boundary -- a city or location, followed/qualified by a
         *    geopolitical boundary name or code. Paris, France; Paris, Texas.
         * c. Coordinate rule -- coordinates emit Province ID and Country ID if
         *    possible. So inferred Provinces are weighted heavily.
         * d. Person name rule - filters out heavily, making use of JRC Names and your
         *    own data sets as a TaxCat catalog/tagger.
         * e. Major Places rule -- well-known large cities, capitals or provinces are weighted moderately.
         * f. Province association rule -- for each found place, weight geos falling in Provinces positively ID'd.
         * g. Location Chooser rule -- assemble all evidence and account for weights.
         */
        countryRule.evaluate(candidates);
        nameWithAdminRule.evaluate(candidates);

        // 2. NON-PLACE ID. Tag person and org names to negate celebrity names or
        // well-known individuals who share a city name. "Tom Jackson", "Bill Clinton"
        //
        parseKnownNonPlaces(input, candidates, matches, jobParams);

        // Measure duration of tagging.
        this.taggingTimes.addTimeSince(t1);

        if (candidates.isEmpty()) {
            // May contain found taxons from known places step above.
            return matches;
        }

        // Evaluate independent rules, and any that user has added.
        //
        for (GeocodeRule r : rules) {
            r.evaluate(candidates);
        }

        // Last rule: score, choose, add confidence.
        //
        chooser.setTextCase(input);
        chooser.evaluate(candidates, jobParams);
        if (provinceNameSetter != null) {
            provinceNameSetter.evaluate(candidates);
        }
        // Leverage the resulting state of the Chooser -- at the mention level and the document level
        // To further refine filterable nonsense.
        updateRelatedNames(candidates);

        // For each candidate, if PlaceCandidate.chosen is not null,
        // add chosen (Geocoding) to matches
        // Otherwise add PlaceCandidates to matches.
        // non-geocoded matches will appear in non-GIS formats.
        //
        // Downstream recipients of 'matches' must know how to parse through
        // evaluated place candidates. We send the candidates and all evidence.
        matches.addAll(candidates);

        // Measure full processing duration for this doc.
        this.matcherTotalTimes.addBytes(input.buffer.length());
        this.matcherTotalTimes.addTimeSince(t1);

        return matches;
    }

    /**
     * Reconnect match sequences "NAME, ADMIN"  or "NAME, CODE"
     * <p>
     * Omit matches for country codes that have no connection to anything, e.g., "CO" appears at random,
     * but implies "COmpany" not "Colombia", for example.  The filter out trivial country codes.  Yeah, even "US"
     * Abbreviations are fine.
     */
    private void updateRelatedNames(List<PlaceCandidate> candidates) {
        for (PlaceCandidate pc : candidates) {
            if (!pc.isFilteredOut() && pc.isCountry && pc.getChosen() != null) {
                Place C = pc.getChosenPlace();
                /* TODO: confusion between territory abbreviations and owning country may need to be resolved, but a truly minor point. */
                if (C.isCode() && chooser.getInferredCountryCount(C.getCountryCode()) == 0) {
                    pc.setFilteredOut(true);
                    continue;
                }
            }
            if (pc.getRelated() == null) {
                continue;
            }

            Place geo = pc.getChosenPlace();
            for (PlaceCandidate related : pc.getRelated()) {
                if (related.getChosenPlace() == null) {
                    // This happens rarely if a name is marked as a Taxon, Person or Org.
                    continue;
                }
                Place relatedGeo = related.getChosenPlace();

                String admHierarchy = geo.getHierarchicalPath();
                if (admHierarchy.equals(relatedGeo.getHierarchicalPath())
                        && !geo.isAdmin1()
                        && relatedGeo.isAdministrative()) {
                    pc.end = related.end;
                    related.setFilteredOut(true);
                    pc.setTextOnly(String.format("%s, %s", pc.getText(), related.getText()));
                    break;
                }
            }
        }
    }

    /**
     * If no geo matches are found, we still parse the data if person name matching
     * is enabled. Poor-man's named-entity extraction
     *
     * @throws ExtractionException
     */
    private void parseKnownNonPlaces(TextInput input, List<PlaceCandidate> candidates, List<TextMatch> matches,
                                     Parameters params) throws ExtractionException {

        List<TextMatch> nonPlaces = taxonTagger.extract(input, params);
        if (nonPlaces.isEmpty()) {
            return;
        }
        /* Taxon Matcher tags for all taxons in your own `taxcat` tagger index
         * The default one has JRC, WFB, person names, nationalities.
         * ALL these taxons will be tagged and compared against geogrpahic names.
         *
         * Only if caller requests `taxons` (or persons, orgs, etc) in service will these be output.
         * The primary purpose here is to account for things that are NOT geographic names.
         */

        List<TaxonMatch> persons = new ArrayList<>();
        List<TaxonMatch> orgs = new ArrayList<>();
        List<TaxonMatch> others = new ArrayList<>();

        log.debug("Matched {}", nonPlaces.size());

        /*
          Step 1 - Filter all TaxonMatches, based on the individual Taxons found.
          One Match may be linked to multiple Taxons.  E.g., "The Feds" text may be resolved/tagged as
             "org.Federal Government" (catalog JRC),
             "org.musical.Feds_Band" (catalog BandGuide)

          Only one conditional is needed to filter in/out the TextMatch based on one taxon.
         */
        for (TextMatch tm : nonPlaces) {
            if (!(tm instanceof TaxonMatch)) {
                continue;
            }

            // Ignore lower case match when lower case tagging is not enabled
            // Allow lower case match if input is entirely lower case
            if (tm.isLower() && !input.isLower && !taggingParams.tag_lowercase) {
                tm.setFilteredOut(true);
                continue;
            }

            boolean sillyOrShort = tm.isLower() || tm.getLength() < 4;
            TaxonMatch tag = (TaxonMatch) tm;

            for (Taxon taxon : tag.getTaxons()) {
                String node = taxon.name.toLowerCase();
                // Match negation based on entity class Place, Person, Org, Nationality, Other Taxon
                // ==============================
                if (node.startsWith("person")) {
                    if (node.startsWith("person_name.")) {
                        // Filter out common lowercase first names  or last names that are also stopwords.
                        // ==============================
                        if (this.filter.filterOut(input.langid, tag.getTextnorm()) && sillyOrShort) {
                            continue;
                        }
                    }
                    persons.add(tag);
                    break;
                } else if (node.startsWith("nationality.")) {
                    // Nationality infers ==> Country.  Grab country code from Taxon
                    // ==============================
                    if (taxon.hasTags()) {
                        for (String t : taxon.tagset) {
                            int x = t.indexOf("cc+");
                            if (x >= 0) {
                                String isocode = t.substring(x + 3);
                                this.countryInScope(isocode);
                            }
                        }
                    }
                    others.add(tag);
                    break;
                } else {
                    if (taxon.isAcronym && !tm.isUpper()) {
                        // Mismatch  ABC vs. Abc
                        continue;
                    }
                    if (node.startsWith("org.")) {
                        orgs.add(tag);
                    } else {
                        others.add(tag);
                    }
                    break;
                }
            }
        }

        /* Step 2.  Separate groups of entities, but add all salient ones to Matches.
        Note - filtered out Taxons/TaxonMatches are not included in final output,
        So caller looking for "filtered out" will not see trivial taxons.

        Then all non-Places entities are found, line them up against existing Place Candidates.
         */
        personNameRule.evaluateNamedEntities(input, candidates, persons, orgs, others);
        matches.addAll(persons);
        matches.addAll(orgs);
        matches.addAll(others);
    }

    /**
     * Concrete lat/lon or MGRS grid locations infer location, city, province,
     * country
     */
    private List<TextMatch> parseGeoCoordinates(TextInput input) {
        List<TextMatch> coords = new ArrayList<>();
        if (!isCoordExtractionEnabled()) {
            return coords;
        }

        coords = xcoord.extract(input);
        if (!coords.isEmpty()) {
            coordRule.addCoordinates(coords);
            adm1Rule.setProvinces(relevantProvinces.values());
        }
        return coords;
    }

    /**
     * Record how often country references are made.
     *
     * @param c country obj
     */
    @Override
    public void countryInScope(Country c) {
        if (c == null) {
            return;
        }
        CountryCount counter = relevantCountries.computeIfAbsent(c.getCountryCode(), newCount -> new CountryCount(c));
        ++counter.count;
    }

    @Override
    public int countryCount() {
        return relevantCountries.size();
    }

    /**
     * Calculate country mention totals and ratios. These ratios help qualify what
     * the document is about. These may be mentions in text or inferred mentions to the countries
     * listed, e.g., a coord infers a particular country.
     */
    @Override
    public Map<String, CountryCount> countryMentionCount() {
        int total = 0;
        for (CountryCount cnt : relevantCountries.values()) {
            total += cnt.count;
        }
        for (CountryCount cnt : relevantCountries.values()) {
            cnt.total = total;
        }
        return relevantCountries;
    }

    /**
     * Weight mentions or indirect references to Provinces in the document
     *
     * @return
     */
    @Override
    public Map<String, PlaceCount> placeMentionCount() {
        int total = 0;
        // Accumulate total.
        for (PlaceCount cnt : relevantProvinces.values()) {
            total += cnt.getCount();
        }

        // One more time to set totals.
        for (PlaceCount cnt : relevantProvinces.values()) {
            cnt.total = total;
        }
        return relevantProvinces;
    }

    /**
     * Record how often country references are made.
     *
     * @param cc
     */
    @Override
    public void countryInScope(String cc) {
        Country ctry = countryCatalog.get(cc);
        if (ctry == null) {
            log.debug("Unknown country code {}", cc);
            return;
        }
        CountryCount counter = relevantCountries.computeIfAbsent(ctry.getCountryCode(), newCount -> new CountryCount(ctry));
        ++counter.count;
    }

    @Override
    public boolean countryObserved(String cc) {
        if (cc == null) {
            return false;
        }
        return relevantCountries.containsKey(cc);
    }

    @Override
    public boolean countryObserved(Country C) {
        if (C == null) {
            return false;
        }
        return relevantCountries.containsKey(C.getCountryCode());
    }

    /**
     * When coordinates are found track them. A coordinate is critical -- it informs
     * us of city,  province, and country. If the location is off shore or in no-mans' land,
     * these chains of observers should respect that and fail quietly.
     * There are at least two opportunities here:
     * 1. Given a geo coordinate, use that hard location to disambiguate other named places
     * 2. Given a geo coordinate identify the nearest known place(s).
     * Such places may not be presented in the document or text. The first improves overall
     * location accuracy, the second offers location enrichment and discovery.
     */
    @Override
    public void locationInScope(Geocoding geo) {
        try {
            Place cityOrProv = evaluateCoordinate(geo);
            if (cityOrProv == null) {
                return;
            }

            // IF FOUND, this relates to "resolve_provinces" parameter.
            //
            // TODO: for provinces found by proximity here
            // play off distinct province vs. city features. relevantProvinces allows
            // overwriting existing key.
            // We may need to track all key/value pairs.
            cityOrProv.defaultHierarchicalPath();
            relevantLocations.put(cityOrProv.getPlaceID(), cityOrProv);
            // for coordinates, the text that infers the location is not relevant in boundaryLevel1InScope()
            boundaryLevel1InScope("coordinate", cityOrProv);
            countryInScope(cityOrProv.getCountryCode());
        } catch (Exception err) {
            log.error("Spatial search error", err);
        }
    }

    /**
     * Observer pattern that sees any time a possible boundary (state, province, district, etc) is mentioned.
     * Example:  mention "Florida"  linked to location Florida(ADM1, FL, US) infers the boundary "US.FL"
     * As would  "Miami" (PPL, FL, US) also infer "US.FL".  We care more about the distinct and various mentions more
     * than the location counts.  I.e., "Florida" has 185 locations worldwide, multiples in some countries.
     *
     * @param nameNorm text or name related to the place, p
     * @param p        ID of a boundary.
     */
    @Override
    public void boundaryLevel1InScope(String nameNorm, Place p) {
        String key = p.getHierarchicalPath();
        if (key == null) {
            return;
        }

        PlaceCount counter = relevantProvinces.computeIfAbsent(key, newCounter -> new PlaceCount(key));
        counter.add(nameNorm);
    }

    @Override
    public void boundaryLevel2InScope(String nameNorm, Place p) {
        // NOT Implmemented.
    }

    /**
     * Generic tagging. No doc ID or language ID given. Nothing language specific
     * will be done here.
     */
    @Override
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extract(new TextInput(null, input_buf));
    }

    Map<String, Integer> locationBias = new HashMap<>();

    /**
     * Find nearest city within r=25 KM to infer geography of a given coordinate,
     * e.g., What state is
     * (x,y) in? Found locations are sorted by distance to point.
     */
    public static final int COORDINATE_PROXIMITY_CITY_THRESHOLD = 25 /* km */;
    public static final int COORDINATE_PROXIMITY_ADM1_THRESHOLD = 50 /* km */;

    /**
     * Internal convenience wrapper
     */
    private Place getProvinceFor(Place loc) throws IOException {
        if (nameHelper == null) {
            throw new IOException("GeonamesUtility was not initialized");
        }
        return nameHelper.getProvince(loc.getCountryCode(), loc.getAdmin1());
    }

    /**
     * Compund-method that is crucial in reverse geocoding COORDINATE to KNOWN PLACE.
     * <p>
     * A method to retrieve one or more distinct admin boundaries containing the
     * coordinate. This depends on resolution of gazetteer at hand. Secondarily as nearby places are
     * encountered they are added to a coordinate providing a basic reverse-geocoding solution.
     *
     * @param g geo coordinate found in text.
     * @return Place object near the geocoding.
     * @throws SolrServerException a query against the Solr index may throw a Solr error.
     */
    public Place evaluateCoordinate(Geocoding g) throws SolrServerException, IOException {
        /*
         * Given geo location L, find ALL places near it. The closest place informs us
         * of its Province code which contains it. That place could be any feature type.
         * Continue to search for the closest PPL within reason.
         * If after searching a first round and nothing is found, widen the radius and
         * search for at least a province boundary.
         */
        List<Place> nearest = getGazetteer().placesAt(g, COORDINATE_PROXIMITY_CITY_THRESHOLD);

        if (!(g instanceof GeocoordMatch)) {
            return null;
        }
        // Only found coordinates are supported in this routine, ... not any Geocoding.
        GeocoordMatch geo = (GeocoordMatch) g;

        /*
         * Our usage of GeocoordMatch: + Related Place = P/PPL place (a city or town) +
         * Nearby Places = first 5 closest places of any type.
         */
        int count = 0;
        int max = 5;
        Place nearestPlace = null;
        for (Place found : nearest) {
            count += 1;
            if (count <= max) {
                geo.addNearByPlace(found);
            }
            if (count == 1) {
                nearestPlace = found;
            }

            if (found.isPopulated()) {
                /*
                 * We stop here -- found a populated locality.
                 */
                Place adm1 = getProvinceFor(found);
                if (adm1 != null) {
                    found.setAdmin1Name(adm1.getName());
                }
                geo.setRelatedPlace(found);
                return found;
            }
        }

        /*
         * No locality found, but we can infer the ADM1 boundary containing the
         * coordinate from the nearest location
         * ((Not 100% guarantee, but close))
         */
        if (nearestPlace != null && nameHelper != null) {
            Place adm1 = getProvinceFor(nearestPlace);
            if (adm1 != null) {
                nearestPlace.setAdmin1Name(adm1.getName());
                geo.setRelatedPlace(nearestPlace);
                return adm1;
            }
            // Set related place anyway.
            geo.setRelatedPlace(nearestPlace);
        }

        /*
         * Keep searching, e.g., Coord in a desert...; Just looking for closest.
         */
        Place found = getGazetteer().placeAt(g, COORDINATE_PROXIMITY_CITY_THRESHOLD, "P");
        if (found == null) {
            found = getGazetteer().placeAt(g, COORDINATE_PROXIMITY_ADM1_THRESHOLD, "A");
        }
        if (found != null) {
            Place adm1 = getProvinceFor(found);
            /*
             * IF found, then this COORD is likely very remote. If still not found, then
             * COORD is possibly over deep water or near poles.
             */
            if (adm1 != null) {
                found.setAdmin1Name(adm1.getName());
            }
            geo.setRelatedPlace(found);
        }
        return found;
    }

    /**
     * Tell us if this place P was inferred by hard location mentions
     */
    @Override
    public boolean placeObserved(Place p) {
        return this.relevantLocations.containsKey(p.getKey());
    }
}
