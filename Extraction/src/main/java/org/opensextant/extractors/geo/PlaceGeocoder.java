/**
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
 *
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 *
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
//_____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
//\ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//\ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
// \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//  \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//          \ \_\
//           \/_/
//
// OpenSextant PlaceGeocoder (Xponents)
//*  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//*/
package org.opensextant.extractors.geo;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.ConfigException;
import org.opensextant.data.Country;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.ExtractionMetrics;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.rules.CoordinateAssociationRule;
import org.opensextant.extractors.geo.rules.CountryRule;
import org.opensextant.extractors.geo.rules.GeocodeRule;
import org.opensextant.extractors.geo.rules.LocationChooserRule;
import org.opensextant.extractors.geo.rules.MajorPlaceRule;
import org.opensextant.extractors.geo.rules.NameCodeRule;
import org.opensextant.extractors.geo.rules.PersonNameFilter;
import org.opensextant.extractors.geo.rules.ProvinceAssociationRule;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.processing.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple variation on the geocoding algorithms: geotag all possible things
 * and determine a best geo-location for each tagged item. This uses the
 * following components:
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

    /**
     * Resources and Taggers
     */
    protected Logger log = LoggerFactory.getLogger(getClass());
    private XCoord xcoord = null;
    private PersonNameFilter personNameRule = null;
    private TaxonMatcher personMatcher = null;
    private static Map<String, Country> countryCatalog = null;

    private final ExtractionMetrics taggingTimes = new ExtractionMetrics("tagging");
    private final ExtractionMetrics retrievalTimes = new ExtractionMetrics("retrieval");
    private final ExtractionMetrics matcherTotalTimes = new ExtractionMetrics("matcher-total");

    /**
     * Rules -- specific ones that are globals.
     * Generic rules that have no state or order are added to this.rules array
     */
    private CountryRule countryRule = null;
    private CoordinateAssociationRule coordRule = null;
    private ProvinceAssociationRule adm1Rule = null;
    private NameCodeRule nameWithAdminRule = null;
    private MajorPlaceRule majorPlaceRule = null;

    /**
     * A default Geocoding app that demonstrates how to invoke the geocoding
     * pipline start to finish. It makes use of XCoord to parse/geocode
     * coordinates, SolrGazetteer/GazetteerMatcher to match named places, XTax
     * to tag person names. Match Filters and rules work in conjunction to
     * filter and tag further any candidates.
     * 
     *
     * @throws ConfigException
     *             if resource files could not be found in CLASSPATH
     */
    public PlaceGeocoder() throws ConfigException {
        super();
    }

    /**
     * 
     * @param lowercaseAllowed
     *            if lower case abbreviations are allowed. See GazetteerMatcher
     * @throws ConfigException
     *             if resource files could not be found in CLASSPATH
     */
    public PlaceGeocoder(boolean lowercaseAllowed) throws ConfigException {
        super(lowercaseAllowed);
    }

    /*
     * ordered list of rules.
     */
    private List<GeocodeRule> rules = new ArrayList<>();

    @Override
    public String getName() {
        return "Advanced PlaceGeocoder";
    }

    /**
     * Configure an Extractor using a config file named by a path.
     *
     * @param patfile
     *            configuration file path
     * @throws ConfigException
     *             on err
     */
    @Override
    public void configure(String patfile) throws ConfigException {
        throw new ConfigException("Configure by path Not available");
    }

    /**
     * Configure an Extractor using a config file named by a URL
     *
     * @param patfile
     *            configuration URL
     */
    @Override
    public void configure(java.net.URL patfile) throws ConfigException {
        throw new ConfigException("Configure by URL Not available");
    }

    public void reportMemory() {
        Runtime R = Runtime.getRuntime();
        long usedMemory = R.totalMemory() - R.freeMemory();
        log.info("CURRENT MEM USAGE(K)=" + (int) (usedMemory / 1024));
    }

    /**
     * We have some emerging metrics to report out...
     */
    public void reportMetrics() {
        log.info("=======================\nTAGGING METRICS");
        log.info(taggingTimes.toString());
        log.info(retrievalTimes.toString());
        log.info(matcherTotalTimes.toString());
    }

    /**
     * We do whatever is needed to init resources... that varies depending on
     * the use case.
     * 
     * Guidelines: this class is custodian of the app controller, Corpus feeder,
     * and any Document instances passed into/out of the feeder.
     * 
     * This geocoder requires a default /exclusions/person-name-filter.txt,
     * which can be empty, but most often it will be a list of person names
     * (which are non-place names)
     *
     * @throws ConfigException
     *             on err
     */
    @Override
    public void configure() throws ConfigException {

        // ==============
        // Rule setup:  Create GeocodeRules, add them to this.rules if they can be evaluated generically
        // on a list of place tags.
        // Otherwise such rules are configured, set during the request, and evaluated adhoc as you need.
        // 
        /* assess country names and codes */
        countryRule = new CountryRule();
        countryRule.setCountryObserver(this);

        /* assess NAME, CODE patterns */
        nameWithAdminRule = new NameCodeRule();
        nameWithAdminRule.setBoundaryObserver(this);

        /**
         * Files for Place Name filter are editable, as you likely have
         * different ideas of who are "person names" to exclude when they
         * conflict with place names. If you are filtering out such things, then
         * it makes sense to filter them out earliest and not incorporate them
         * in geocoding.
         * 
         */
        URL p1 = PlaceGeocoder.class.getResource("/filters/person-name-filter.txt");
        URL p2 = PlaceGeocoder.class.getResource("/filters/person-title-filter.txt");
        URL p3 = PlaceGeocoder.class.getResource("/filters/person-suffix-filter.txt");
        personNameRule = new PersonNameFilter(p1, p2, p3);
        rules.add(personNameRule);

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

            rules.add(coordRule);
            rules.add(adm1Rule);
        }

        if (isPersonNameMatchingEnabled()) {
            try {
                personMatcher = new TaxonMatcher();
                personMatcher.configure();
                /*
                 * Default catalog must be built. Extraction ./XTax folder has
                 * script for populating a catalog.
                 */
                personMatcher.addCatalogFilter("JRC");

            } catch (IOException err) {
                throw new ConfigException("XTax resource not available.");
            }
        }

        // Major Places
        majorPlaceRule = new MajorPlaceRule();
        majorPlaceRule.setCountryObserver(this);
        rules.add(majorPlaceRule);

        LocationChooserRule chooser = new LocationChooserRule();
        chooser.setCountryObserver(this);
        chooser.setBoundaryObserver(this);
        chooser.setLocationObserver(this);
        rules.add(chooser);
        countryCatalog = this.getGazetteer().getCountries();
    }

    /**
     * Please shutdown the application cleanly when done.
     */
    @Override
    public void cleanup() {
        reportMetrics();
        shutdown();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (personMatcher != null) {
            personMatcher.shutdown();
        }
    }

    private Parameters params = new Parameters();

    public void setParameters(Parameters p) {
        params = p;
        params.isdefault = false;
    }

    public boolean isCoordExtractionEnabled() {
        return params.tag_coordinates;
    }

    boolean tagPersonNames = false;

    public boolean isPersonNameMatchingEnabled() {
        return tagPersonNames;
    }

    public void enablePersonNameMatching(boolean b) {
        tagPersonNames = b;
    }

    private void reset() {
        this.relevantCountries.clear();
        this.relevantProvinces.clear();
        this.relevantLocations.clear();

        personNameRule.reset();
        coordRule.reset();
        countryRule.reset();
        adm1Rule.reset();
        majorPlaceRule.reset();
        for (GeocodeRule r : rules) {
            r.reset();
        }
    }

    /**
     * Countries mentioned, inferred, or otherwise relevant
     */
    private Map<String, CountryCount> relevantCountries = new HashMap<>();
    /**
     * Provinces mentioned, inferred or otherwise relevant.
     */
    private Map<String, PlaceCount> relevantProvinces = new HashMap<>();
    /**
     * Places inferred by their proximity to concrete coordinate references.
     */
    private Map<String, Place> relevantLocations = new HashMap<>();

    /**
     * Unfinished Beta; ready for experimentation and improvement on rules.
     *
     * Extractor.extract() calls first XCoord to get coordinates, then
     * PlacenameMatcher In the end you have all geo entities ranked and scored.
     * 
     * <pre>
     * Use TextMatch.getType()
     * to determine how to interpret TextMatch / Geocoding results:
     *
     * Given TextMatch match
     *
     *    Place tag:   ((PlaceCandiate)match).getGeocoding()
     *    Coord tag:   (Geocoding)match
     *
     * Both methods yield a geocoding.
     * </pre>
     *
     * @param input
     *            input buffer
     * @return TextMatch instances which are all PlaceCandidates.
     * @throws ExtractionException
     *             on err
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        long t1 = System.currentTimeMillis();
        reset();

        List<TextMatch> matches = new ArrayList<TextMatch>();
        List<TextMatch> coordinates = null;

        // 0. GEOTAG raw text. Flag tag-only = false, in otherwords do extra work for geocoding.
        //
        LinkedList<PlaceCandidate> candidates = tagText(input.buffer, input.id,
                false);

        // 1. COORDINATES. If caller thinks their data may have coordinates, then attempt to parse
        // lat/lon.  Any coordinates found fire rules for resolve lat/lon to a Province/Country if possible.
        //
        coordinates = parseGeoCoordinates(input);
        if (coordinates != null) {
            matches.addAll(coordinates);
        }

        /*
         * 3.RULE EVALUATION: accumulate all the evidence from everything found so far.
         * Assemble some histograms to support some basic counts, weighting and sorting.
         * 
         * Rules:  Work with observables first, then move onto associations between candidates and more obscure fine tuning. 
         * 1a.  Country - named country weighs heavily; 
         * 1b.  Place, Boundary -- a city or location, followed/qualified by a geopolitical boundary name or code. Paris, France; Paris, Texas.
         * 1c.  Coordinate rule -- coordinates emit Province ID and Country ID if possible. So inferred Provinces are weighted heavily.
         * b.  Person name rule - filters out heavily, making use of JRC Names and your own data sets as a TaxCat catalog/tagger.
         * d.  Major Places rule -- well-known large cities, capitals or provinces are weighted moderately.
         * e.  Province association rule -- for each found place, weight geos falling in Provinces positively ID'd.
         * f.  Location Chooser rule -- assemble all evidence and account for weights.
         */
        countryRule.evaluate(candidates);
        nameWithAdminRule.evaluate(candidates);

        // 2. NON-PLACE ID. Tag person and org names to negate celebrity names or well-known
        // individuals who share a city name. "Tom Jackson", "Bill Clinton"
        //
        parseKnownNonPlaces(input, candidates, matches);

        // Measure duration of tagging.
        this.taggingTimes.addTimeSince(t1);

        for (GeocodeRule r : rules) {
            r.evaluate(candidates);
        }

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
     * If no geo matches are found, we still parse the data if person name matching is enabled.
     * Poor-man's named-entity extraction
     * 
     * @throws ExtractionException
     * 
     */
    private void parseKnownNonPlaces(TextInput input, List<PlaceCandidate> candidates, List<TextMatch> matches) {

        if (!isPersonNameMatchingEnabled()) {
            return;
        }

        // If this step fails miserably, do not raise error. Log the error and return nothing found.
        // 
        List<TextMatch> nonPlaces = null;
        try {
            nonPlaces = personMatcher.extract(input.buffer);
            if (nonPlaces.isEmpty()) {
                return;
            }
        } catch (Exception err) {
            log.error(err.getMessage());
            return;
        }

        List<TaxonMatch> persons = new ArrayList<>();
        List<TaxonMatch> orgs = new ArrayList<>();
        log.debug("Matched {}", nonPlaces.size());

        for (TextMatch tm : nonPlaces) {
            if (!(tm instanceof TaxonMatch)) {
                continue;
            }

            TaxonMatch tag = (TaxonMatch) tm;
            //
            // For the purposes of geocoding/geoparsing filter out ALL
            // TaxonMatches. Any place names should reside back in
            // gazetteer. If XTax does have place or location data, that would be new.
            //
            tm.setFilteredOut(true);
            for (Taxon taxon : tag.getTaxons()) {
                String node = taxon.name.toLowerCase();
                // If you matched a Person name or an Organization ACRONYM
                // add the TaxonMatch (TextMatch) to negate place
                // name spans that are not places.
                if (node.startsWith("person.")) {
                    persons.add(tag);
                    break;
                }
                if (node.startsWith("org.")) {
                    if (taxon.isAcronym && !tm.isUpper()) {
                        continue;
                    }
                    orgs.add(tag);
                    break;
                }
            }
        }
        personNameRule.evaluateNamedEntities(candidates, persons, orgs);
        matches.addAll(persons);
        matches.addAll(orgs);
    }

    /**
     * Concrete lat/lon or MGRS grid locations infer location, city, province, country
     */
    private List<TextMatch> parseGeoCoordinates(TextInput input) {
        if (!isCoordExtractionEnabled()) {
            return null;
        }

        List<TextMatch> coords = xcoord.extract(input);
        if (!coords.isEmpty()) {
            coordRule.addCoordinates(coords);
            adm1Rule.setProvinces(relevantProvinces.values());
            return coords;
        }
        return null;
    }

    /**
     * Record how often country references are made.
     * 
     * @param c
     *            country obj
     */
    @Override
    public void countryInScope(Country c) {
        if (c == null) {
            return;
        }
        CountryCount counter = relevantCountries.get(c.getCountryCode());
        if (counter == null) {
            counter = new CountryCount();
            counter.country = c;
            relevantCountries.put(c.getCountryCode(), counter);
        } else {
            ++counter.count;
        }
    }

    public int countryCount() {
        return relevantCountries.size();
    }

    /**
     * Calculate country mention totals and ratios. These ratios help qualify
     * what the document is about. These may be mentions in text or inferred mentions to
     * the countries listed, e.g., a coord infers a particular country.
     */
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
    public Map<String, PlaceCount> placeMentionCount() {
        int total = 0;
        // Accumulate total.
        for (PlaceCount cnt : relevantProvinces.values()) {
            total += cnt.count;
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
        CountryCount counter = relevantCountries.get(cc);
        if (counter == null) {
            Country C = countryCatalog.get(cc);
            counter = new CountryCount();
            // Well, we must deal with a potential unknown country.  
            // Historical differences, XK = Kosovo, YU = Yugoslavia; 
            // FIPS vs. ISO differences, etc.  Some country codes may not resolve cleanly.
            if (C != null) {
                counter.country = C;
            }
            relevantCountries.put(cc, counter);
        } else {
            ++counter.count;
        }
    }

    public boolean countryObserved(String cc) {
        if (cc == null) {
            return false;
        }
        return relevantCountries.containsKey(cc);
    }

    public boolean countryObserved(Country C) {
        if (C == null) {
            return false;
        }
        return relevantCountries.containsKey(C.getCountryCode());
    }

    /**
     * When coordinates are found track them. A coordinate is critical -- it informs us
     * of city, province, and country. If the location is off shore or in no-mans' land,
     * these chains of observers should respect that and fail quietly.
     */
    @Override
    public void locationInScope(Geocoding geo) {
        try {
            Place cityOrProv = evaluateCoordinate(geo);
            if (cityOrProv != null) {
                // TODO: for provinces found by proximity here
                // play off distinct province vs. city features.  relevantProvinces allows overwriting existing key.
                // We may need to track all key/value pairs.
                cityOrProv.defaultHierarchicalPath();
                relevantLocations.put(cityOrProv.getPlaceID(), cityOrProv);
                boundaryLevel1InScope(cityOrProv);
                countryInScope(cityOrProv.getCountryCode());
            }
        } catch (Exception err) {
            log.error("Spatial search error", err);
        }
    }

    /**
     * Observer pattern that sees any time a possible boundary (state, province, district, etc) is mentioned.
     * 
     * @param p
     *            ID of a boundary.
     */
    @Override
    public void boundaryLevel1InScope(Place p) {
        PlaceCount counter = relevantProvinces.get(p.getHierarchicalPath());
        if (counter == null) {
            counter = new PlaceCount();
            counter.place = p;
            relevantProvinces.put(p.getHierarchicalPath(), counter);
        } else {
            ++counter.count;
        }
    }

    @Override
    public void boundaryLevel2InScope(Place p) {
        // NOT Implmemented.
    }

    @Override
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extract(new TextInput(null, input_buf));
    }

    Map<String, Integer> locationBias = new HashMap<>();

    /**
     * Find nearest city within r=25 KM to infer geography of a given coordinate, e.g.,
     * What state is (x,y) in? Found locations are sorted by distance to point.
     */
    public static int COORDINATE_PROXIMITY_CITY_THRESHOLD = 25 /*km*/ ;
    public static int COORDINATE_PROXIMITY_ADM1_THRESHOLD = 50 /*km*/;

    /**
     * A method to retrieve one or more distinct admin boundaries containing the
     * coordinate. This depends on resolution of gazetteer at hand.
     *
     * @param g
     *            geocoordinate found in text.
     * @return Place object near the geocoding.
     * @throws SolrServerException
     *             a query against the Solr index may throw a Solr error.
     */
    public Place evaluateCoordinate(Geocoding g) throws SolrServerException {
        Place found = getGazetteer().placeAt(g, COORDINATE_PROXIMITY_CITY_THRESHOLD, "P");
        if (found != null) {
            if (g instanceof GeocoordMatch) {
                ((GeocoordMatch) g).setRelatedPlace(found);
            }
            return found;
        }

        found = getGazetteer().placeAt(g, COORDINATE_PROXIMITY_ADM1_THRESHOLD, "A");
        if (found != null) {
            if (g instanceof GeocoordMatch) {
                ((GeocoordMatch) g).setRelatedPlace(found);
            }
            return found;
        }

        return null;
    }

    /**
     * Tell us if this place P was inferred by hard location mentions
     * 
     */
    @Override
    public boolean placeObserved(Place p) {
        return this.relevantLocations.containsKey(p.getKey());
    }
}
