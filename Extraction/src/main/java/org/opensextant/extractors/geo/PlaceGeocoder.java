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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.ConfigException;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.ExtractionMetrics;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.rules.CoordinateAssociationRule;
import org.opensextant.extractors.geo.rules.CountryRule;
import org.opensextant.extractors.geo.rules.GeocodeRule;
import org.opensextant.extractors.geo.rules.NameCodeRule;
import org.opensextant.extractors.geo.rules.PersonNameFilter;
import org.opensextant.extractors.geo.rules.ProvinceAssociationRule;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.processing.Parameters;
import org.opensextant.util.GeonamesUtility;
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
public class PlaceGeocoder extends GazetteerMatcher implements Extractor {

    /**
     *
     */
    // private final TextUtils utility = new TextUtils();
    protected Logger log = LoggerFactory.getLogger(getClass());
    private XCoord xcoord = null;
    private PersonNameFilter personNameRule = null;
    private TaxonMatcher personMatcher = null;
    private final ExtractionMetrics taggingTimes = new ExtractionMetrics("tagging");
    private final ExtractionMetrics retrievalTimes = new ExtractionMetrics("retrieval");
    private final ExtractionMetrics matcherTotalTimes = new ExtractionMetrics("matcher-total");
//     private final ExtractionMetrics processingMetric = new ExtractionMetrics("processing");
    // private ProgressMonitor progressMonitor;

    private CountryRule countryRule = null;
    private CoordinateAssociationRule coordRule = null;
    private ProvinceAssociationRule adm1Rule = null;

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
     * @param lowercaseAllowed if lower case abbreviations are allowed. See GazetteerMatcher
     * @throws ConfigException if resource files could not be found in CLASSPATH
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
     * We have some emerging metrics to report out... As these metrics are
     * volatile, I'm not changing imports.
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
        // rules.add();

        countryRule = new CountryRule();
        // rules.add(countryRule); /* assess country names and codes */
        rules.add(new NameCodeRule()); /* assess NAME, CODE patterns */

        if (xcoord == null && (isCoordExtractionEnabled())) {
            xcoord = new XCoord();
            xcoord.configure();

            /*
             * assess coordinates related to ADM1, CC
             */
            coordRule = new CoordinateAssociationRule();
            /*
             * assess ADM1 related to found NAMES as a result of coordinates
             */
            adm1Rule = new ProvinceAssociationRule();

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
    public void shutdown(){
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
        this.locationBias.clear();
        this.personNameRule.reset();
    }

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
        if (isCoordExtractionEnabled()) {
            coordinates = xcoord.extract(input);
        }

        LinkedList<PlaceCandidate> candidates = tagText(input.buffer, input.id);

        // Tagger has already marked candidates as name of Country or not.
        //
        // countryRule.evaluate(candidates);

        if (coordinates != null && !coordinates.isEmpty()) {
            matches.addAll(coordinates);

            // First assess names matched
            // If names are to be completely filtered out, filter them out first
            // or remove from candiate list.
            // Then apply rules.

            // IFF you have coordinates extracted or given:
            // 1. Identify all hard geographic entities: coordinates; coded
            // places (other patterns provided by your domain, etc.)
            // 1a. identify country + AMD1 for each coordinate; summarize
            // distinct country + ADM1 as evidence
            // XY => geohash, query geohash w/fq.fields = cc,adm1,adm2
            //
            // 2. Tagger Post-processing rules: Generate Country, Nat'l Capitals
            // and Admin names
            List<Place> relevantProvinces = new ArrayList<>();
            if (coordRule != null && coordinates != null) {
                coordRule.reset();
                try {
                    for (TextMatch g : coordinates) {
                        if (g instanceof GeocoordMatch) {
                            Place province = evaluateCoordinate((GeocoordMatch) g);
                            if (province != null) {
                                relevantProvinces.add(province);
                            }
                        }
                        coordRule.addCoordinate((Geocoding) g);
                    }
                } catch (Exception err) {
                    log.error("Problem doing spatial lookup", err);
                }

                if (adm1Rule != null && !relevantProvinces.isEmpty()) {
                    adm1Rule.setProvinces(relevantProvinces);
                }
            }
        }

        // 3. Tag person and org names to negate celebrity names or well-known
        // individuals who share a city name. "Tom Jackson", "Bill Clinton"
        //
        if (isPersonNameMatchingEnabled()) {
            List<TextMatch> nonPlaces = personMatcher.extract(input.buffer);
            if (!nonPlaces.isEmpty()) {
                List<TaxonMatch> persons = new ArrayList<>();
                List<TaxonMatch> orgs = new ArrayList<>();
                for (TextMatch tm : nonPlaces) {
                    if (!(tm instanceof TaxonMatch)) {
                        continue;
                    }

                    TaxonMatch tag = (TaxonMatch) tm;
                    //
                    // For the purposes of geocoding/geoparsing filter out
                    // ALL
                    // TaxonMatches. Any place names should reside back in
                    // gazetteer.
                    // If XTax does have place or location data, that would
                    // be new.
                    //
                    tm.setFilteredOut(true);
                    for (Taxon taxon : tag.getTaxons()) {
                        String node = taxon.name.toLowerCase();
                        // If you matched a Person name or an Organization
                        // ACRONYM
                        // add the TaxonMatch (TextMatch) to negate place
                        // name spans
                        // that are not places.
                        if (node.startsWith("person.")) {
                            persons.add(tag);
                            break;
                        }
                        if (node.startsWith("org.") && tm.isUpper()) {
                            orgs.add(tag);
                            break;
                        }
                    }
                }
                personNameRule.evaluateNamedEntities(candidates, persons, orgs);
                matches.addAll(persons);
                matches.addAll(orgs);
            }
        }
        
        // Measure duration of tagging.
        this.taggingTimes.addTimeSince(t1);

        countryRule.evaluate(candidates);
        personNameRule.evaluate(candidates);

        /*
         * Rule evaluation: accumulate all the evidence.
         */
        for (GeocodeRule r : rules) {
            r.evaluate(candidates);
        }

        /*
         * Knit it all together.
         */
        geocode(candidates);

        // For each candidate, if PlaceCandidate.chosen is not null,
        // add chosen (Geocoding) to matches
        // Otherwise add PlaceCandidates to matches.
        // non-geocoded matches will appear in non-GIS formats.
        //
        // Downstream recipients of 'matches' must know how to parse through
        // evaluated
        // place candidates. We send the candidates and all evidence.
        matches.addAll(candidates);

        // Measure full processing duration for this doc.
        this.matcherTotalTimes.addBytes(input.buffer.length());
        this.matcherTotalTimes.addTimeSince(t1);

        return matches;
    }

    @Override
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extract(new TextInput(null, input_buf));
    }

    Map<String, Integer> locationBias = new HashMap<>();

    /**
     * By now, all raw rules should have fired, adding their most basic metadata
     * to each candidate.
     *
     * @param candidates
     *            list of PlaceCandidate (TextMatch + Place list)
     * @param coordinates
     *            list of GeoocordMatch (yes, cast of (GeoocordMatch)TextMatch
     *            is tested and used).
     */
    private void geocode(List<PlaceCandidate> candidates) {

        /*
         * Count all entries of evidence.
         */
        for (PlaceCandidate pc : candidates) {
            if (pc.isFilteredOut()) {
                continue;
            }

            for (PlaceEvidence ev : pc.getEvidence()) {
                String key = null;
                if (ev.isCountry()) {
                    key = ev.getCountryCode();
                } else if (ev.isAdministrative()) {
                    key = GeonamesUtility.getHASC(ev.getCountryCode(), ev.getAdmin1());
                }
                if (key != null) {
                    Integer count = locationBias.get(key);
                    if (count == null) {
                        count = new Integer(0);
                    }
                    locationBias.put(key, count + 1);
                }
            }
        }
        // 3. Disambiguation
        // 4. Geocoding -- choosing and setting final entry data.

    }

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
    public Place evaluateCoordinate(GeocoordMatch g) throws SolrServerException {
        Place found = getGazetteer().placeAt(g, 25, "P");
        if (found == null) {
            found = getGazetteer().placeAt(g, 50, "A");
            if (found == null) {
                return null;
            }
        }

        g.setRelatedPlace(found);

        return found;
    }

    @Override
    public void updateProgress(double progress) {
        // if (this.progressMonitor != null) {
        // progressMonitor.updateStepProgress(progress);
        // }
    }

    @Override
    public void markComplete() {
        // if (this.progressMonitor != null) {
        // progressMonitor.completeStep();
        // }
    }
}
