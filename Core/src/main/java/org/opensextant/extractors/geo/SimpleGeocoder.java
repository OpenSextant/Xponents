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
import org.opensextant.extractors.geo.rules.*;

/**
 * This is the simplest geocoding API we could devise to support embedding
 * OpenSextant into your Java applications directly. You instantiate it and can
 * geocode text rapidly and repeatedly. You, the caller is responsible for
 * managing storage of results and formatting of results.
 *
 * Our other API classes demonstrate how to format results, and eventually how
 * to store them.
 *
 * In no way does this SimpleGeocoder compromise on the quality or thoroughness
 * of the geocoding processing. It is the same processing, just a lighter weight
 * method than say using the WS or GATE Runner versions.
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
    private static ExtractionMetrics taggingTimes = new ExtractionMetrics("tagging");
    private static ExtractionMetrics retrievalTimes = new ExtractionMetrics("retrieval");
    private static ExtractionMetrics matcherTotalTimes = new ExtractionMetrics("matcher-total");
    private ExtractionMetrics processingMetric = new ExtractionMetrics("processing");

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

        if (xcoord == null && (isCoordExtractionEnabled()) ) {
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
        PlacenameMatcher.shutdown();
    }

    private Parameters params = new Parameters();
    
    public void setParameters(Parameters p){
        params = p;
        params.isdefault = false;
    }
    
    public boolean isCoordExtractionEnabled(){
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
        matches.addAll( candidates );

        return matches;
    }
    
    
    private CantileverPR cantilever = new CantileverPR();
    private  void chooseCandidates(List<PlaceCandidate> candidates, List<TextMatch> coordinates){
        // First assess names matched 
        // If names are to be completely filtered out, filter them out first or remove from candiate list.
        // Then apply rules.

        // 1. Tagger Post-processing rules: Generate Country, Nat'l Capitals and Admin names
        // 2. Cantilever rules:        
        cantilever.processCandiates(candidates);
        

        // If valid places are to be ignored in output, then allow them as evidence
        // but mark them as filtered out (from output).
        
    }
}
