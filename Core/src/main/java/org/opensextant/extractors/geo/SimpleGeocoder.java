/**
 * Copyright 2009-2013 The MITRE Corporation.
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

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.opensextant.extraction.ExtractionMetrics;
import org.opensextant.extraction.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.ConfigException;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.progress.ProgressMonitor;
import org.opensextant.extractors.geo.rules.*;

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
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class SimpleGeocoder implements Extractor {

    /**
     *
     */
    //private final TextUtils utility = new TextUtils();
    protected Logger log = LoggerFactory.getLogger(SimpleGeocoder.class);
    private XCoord xcoord = null;
    private PlacenameMatcher tagger = null;
    private final ExtractionMetrics taggingTimes = new ExtractionMetrics("tagging");
    private final ExtractionMetrics retrievalTimes = new ExtractionMetrics("retrieval");
    private final ExtractionMetrics matcherTotalTimes = new ExtractionMetrics("matcher-total");
    private final ExtractionMetrics processingMetric = new ExtractionMetrics("processing");
    private ProgressMonitor progressMonitor;

    /**
     * A default Geocoding app that demonstrates how to invoke the geocoding
     * pipline start to finish.
     *
     */
    public SimpleGeocoder() {
    }

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
     */
    @Override
    public void configure() throws ConfigException {

        if (xcoord == null && (isCoordExtractionEnabled())) {
            xcoord = new XCoord();
            xcoord.configure();
        }
        try {
            if (tagger == null) {
                tagger = new PlacenameMatcher();
            }
        } catch (IOException ioerr) {
            throw new ConfigException("Init err w/Solr", ioerr);
        }

    }

    /**
     * Please shutdown the application cleanly when done.
     */
    public void shutdown() {

        reportMetrics();
        if (tagger != null) {
            tagger.shutdown();
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

    /**
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
     * @return
     * @throws ExtractionException
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        List<TextMatch> matches = new ArrayList<TextMatch>();

        List<TextMatch> coordinates = null;
        if (isCoordExtractionEnabled()) {
            coordinates = xcoord.extract(input);
        }

        List<PlaceCandidate> candidates = tagger.tagText(input.buffer, input.id);

        if (coordinates != null) {
            matches.addAll(coordinates);
        }

        chooseCandidates(candidates, coordinates);
        matches.addAll(candidates);

        return matches;
    }

    private final CantileverPR cantilever = new CantileverPR();

    private void chooseCandidates(List<PlaceCandidate> candidates, List<TextMatch> coordinates) {
        // First assess names matched
        // If names are to be completely filtered out, filter them out first or remove from candiate list.
        // Then apply rules.

        // 1. Identify all hard geographic entities:  coordinates; coded places (other patterns provided by your domain, etc.)
        // 1a. identify country + AMD1 for each coordinate; summarize distinct country + ADM1 as evidence
        // 2. Tagger Post-processing rules: Generate Country, Nat'l Capitals and Admin names
        // 3. Cantilever rules: General disambiguation
        cantilever.processCandiates(candidates);

        // If valid places are to be ignored in output, then allow them as evidence
        // but mark them as filtered out (from output).
    }

    @Override
    public void setProgressMonitor(ProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    @Override
    public void updateProgress(double progress) {
        if (this.progressMonitor != null) {
            progressMonitor.updateStepProgress(progress);
        }
    }

    @Override
    public void markComplete() {
        if (this.progressMonitor != null) {
            progressMonitor.completeStep();
        }
    }

}
