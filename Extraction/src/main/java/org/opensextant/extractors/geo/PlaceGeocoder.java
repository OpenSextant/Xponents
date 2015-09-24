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
 */
package org.opensextant.extractors.geo;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.ConfigException;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
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
import org.opensextant.extractors.xcoord.XCoord;
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
    //private final TextUtils utility = new TextUtils();
    protected Logger log = LoggerFactory.getLogger(getClass());
    private XCoord xcoord = null;
    private MatchFilter personFilter = null;
    private final ExtractionMetrics taggingTimes = new ExtractionMetrics("tagging");
    private final ExtractionMetrics retrievalTimes = new ExtractionMetrics("retrieval");
    private final ExtractionMetrics matcherTotalTimes = new ExtractionMetrics("matcher-total");
    private final ExtractionMetrics processingMetric = new ExtractionMetrics("processing");
    //private ProgressMonitor progressMonitor;

    private CountryRule countryRule = null;
    private CoordinateAssociationRule coordRule = null;
    private ProvinceAssociationRule adm1Rule = null;

    /**
     * A default Geocoding app that demonstrates how to invoke the geocoding
     * pipline start to finish.
     *
     */
    public PlaceGeocoder() throws ConfigException {
        super();
    }

    /*
     * ordered list of rules.
     */
    private List<GeocodeRule> rules = new ArrayList<>();

    @Override
    public String getName() {
        return "SimpleGeocoder";
    }

    /**
     * Configure an Extractor using a config file named by a path
     *
     * @param patfile configuration file path
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
        log.info("=================\nSimple Geocoder");
        log.info(this.processingMetric.toString());

    }

    /**
     * We do whatever is needed to init resources... that varies depending on
     * the use case.
     *
     * Guidelines: this class is custodian of the app controller, Corpus feeder,
     * and any Document instances passed into/out of the feeder.
     *
     * This geocoder requires a default /exclusions/person-name-filter.txt,
     * which can be empty, but most often it will be a list of person names (which are non-place names)
     *
     */
    @Override
    public void configure() throws ConfigException {

        /** Files for Place Name filter are editable, as you likely have different ideas of who are "person names" to exclude
         * when they conflict with place names.  If you are filtering out such things,
         * then it makes sense to filter them out earliest and not incorporate them in geocoding.
         * 
         */
        URL p1 = PlaceGeocoder.class.getResource("/filters/person-name-filter.txt");
        URL p2 = PlaceGeocoder.class.getResource("/filters/person-title-filter.txt");
        URL p3 = PlaceGeocoder.class.getResource("/filters/person-suffix-filter.txt");
        rules.add(new PersonNameFilter(p1, p2, p3));

        countryRule = new CountryRule();
        // rules.add(countryRule); /* assess country names and codes */
        rules.add(new NameCodeRule()); /* assess NAME, CODE patterns */

        if (xcoord == null && (isCoordExtractionEnabled())) {
            xcoord = new XCoord();
            xcoord.configure();

            coordRule = new CoordinateAssociationRule(); /* assess coordinates related to ADM1, CC; Haversine is default. */
            adm1Rule = new ProvinceAssociationRule(); /* assess ADM1 related to found NAMES as a result of coordinates */

            rules.add(coordRule);
            rules.add(adm1Rule);
        }
    }

    /**
     * Please shutdown the application cleanly when done.
     */
    @Override
    public void cleanup() {
        reportMetrics();
        this.shutdown();
    }

    private Parameters params = new Parameters();

    public void setParameters(Parameters p) {
        params = p;
        params.isdefault = false;
    }

    public boolean isCoordExtractionEnabled() {
        return params.tag_coordinates;
    }

    /**
     * Unfinished Beta; ready for experimentation and improvement on rules.
     *
     * Extractor.extract() calls first XCoord to get coordinates, then
     * PlacenameMatcher In the end you have all geo entities ranked and scored.
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
     * @return TextMatch instances which are all PlaceCandidates.
     * @throws ExtractionException
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        List<TextMatch> matches = new ArrayList<TextMatch>();

        List<TextMatch> coordinates = null;
        if (isCoordExtractionEnabled()) {
            coordinates = xcoord.extract(input);
        }

        LinkedList<PlaceCandidate> candidates = tagText(input.buffer, input.id);

        // Tagger has already marked candidates as name of Country or not.
        //
        // countryRule.evaluate(candidates);

        if (coordinates != null) {
            matches.addAll(coordinates);

            // First assess names matched
            // If names are to be completely filtered out, filter them out first or remove from candiate list.
            // Then apply rules.

            // IFF you have coordinates extracted or given:
            // 1. Identify all hard geographic entities:  coordinates; coded places (other patterns provided by your domain, etc.)
            // 1a. identify country + AMD1 for each coordinate; summarize distinct country + ADM1 as evidence
            //     XY => geohash, query geohash w/fq.fields = cc,adm1,adm2
            //
            // 2. Tagger Post-processing rules: Generate Country, Nat'l Capitals and Admin names
            List<Place> relevantProvinces = new ArrayList<>();
            if (coordRule != null && coordinates != null) {
                coordRule.reset();
                try {
                    for (TextMatch g : coordinates) {
                        if (g instanceof Geocoding) {
                            Place province = evaluateCoordinate((Geocoding) g);
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
        //    add chosen (Geocoding) to matches
        // Otherwise add PlaceCandidates to matches.
        //    non-geocoded matches will appear in non-GIS formats.
        //
        // Downstream recipients of 'matches' must know how to parse through evaluated
        // place candidates.  We send the candidates and all evidence.
        matches.addAll(candidates);

        return matches;
    }

    @Override
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extract(new TextInput(null, input_buf));
    }

    Map<String, Integer> locationBias = new HashMap<>();

    /**
     * By now, all raw rules should have fired, adding their most basic metadata to each candidate.
     *
     * @param candidates list of PlaceCandidate (TextMatch + Place list)
     * @param coordinates list of GeoocordMatch (yes, cast of (GeoocordMatch)TextMatch is tested and used).
     */
    private void geocode(List<PlaceCandidate> candidates) {

        /*
         * Count all entries of evidence.
         */
        for (PlaceCandidate pc : candidates) {
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
     * A method to retrieve one or more distinct admin boundaries containing the coordinate.
     * This depends on resolution of gazetteer at hand.
     *
     * @param g
     * @return
     * @throws SolrServerException   a query against the Solr index may throw a Solr error.
     */
    public Place evaluateCoordinate(Geocoding g) throws SolrServerException {
        // Solr geospatial lookup required;  we use RPT -- recursive prefix filter field type.
        // TOOD: implement spatial query against gazetter to do XY=>Location(feat_type=*, facet field=adm1)
        //      report Distinct CC.ADM1.ADM2 paths where Geocoding points.
        List<Place> found = placesAt(g);
        if (found == null || found.isEmpty()) {
            return null;
        }

        Set<String> distinctADM1 = new HashSet<>();
        Set<String> distinctCC = new HashSet<>();

        for (Place p : found) {
            distinctADM1.add(GeonamesUtility.getHASC(p.getCountryCode(), p.getAdmin1()));
            distinctCC.add(p.getCountryCode());
        }

        Place boundary = new Place();
        //
        if (distinctADM1.size() == 1) {
            /* Objective here is to report a single place closest to coordinate
             * that represents the general boundary that contains the point
             */
            Place p = found.get(0);
            boundary.setCountryCode(p.getCountryCode());
            boundary.setAdmin1(p.getAdmin1());
            return boundary;
        }
        // TODO: return admin code for best match for the given coord.
        return found.get(0);
    }

    //    @Override
    //    public void setProgressMonitor(ProgressMonitor progressMonitor) {
    //        this.progressMonitor = progressMonitor;
    //    }

    @Override
    public void updateProgress(double progress) {
        //        if (this.progressMonitor != null) {
        //            progressMonitor.updateStepProgress(progress);
        //        }
    }

    @Override
    public void markComplete() {
        //        if (this.progressMonitor != null) {
        //            progressMonitor.completeStep();
        //        }
    }
}
