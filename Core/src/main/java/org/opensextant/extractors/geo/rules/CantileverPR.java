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
package org.opensextant.extractors.geo.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.PlaceEvidence.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Port of CantileverPR (GATE processing resource) to non-GATE version This is
 * the GATE ProcessingResource wrapper for the Cantilever class. It performs a
 * geospatial-specific form of co-referencing. It also propagates evidence from
 * a geospatial entity to co-references of that entity.
 */
public class CantileverPR {

    //private Cantilever cntlvr = new Cantilever();
    private Scorer scr = new Scorer();
    static Logger log = LoggerFactory.getLogger(CantileverPR.class);

    /**
     * This does the actual work of analyzing the evidence attached to the place
     * candidates. It invokes Cantilever's
     * <code>propagateEvidence()</code> for the place candidates found in each
     * document.
     *
     */
    public void processCandiates(List<PlaceCandidate> pcList) {

        // do the coreferencing and propagate the evidence amongst the PCs
        Cantilever.propagateEvidence(pcList);

        // collect the document level evidence
        List<PlaceEvidence> docEvidList = collectDocumentEvidence(pcList);

        // attach document level evidence to scorer
        scr.setDocumentLevelEvidence(docEvidList);

        // score and rank the Places in each PC according to the evidence
        scr.score(pcList);

    }// end execute

    private List<PlaceEvidence> collectDocumentEvidence(List<PlaceCandidate> pcList) {

        Double countryWeight = 0.1;
        Double adminWeight = 0.05;


        // document level country and admin1  evidence
        List<PlaceEvidence> docEvidList = new ArrayList<PlaceEvidence>();

        // collect all of the countries, capitals and admin1s mentioned

        // how many times has a country been mentioned
        Map<String, Counter> countryCounts = new HashMap<String, Counter>();
        Map<String, Double> countryBiases = new HashMap<String, Double>();
        int countryTotal = 0;

        // how many times has an admin1 been mentioned
        Map<String, Counter> adminCounts = new HashMap<String, Counter>();
        Map<String, Double> adminBiases = new HashMap<String, Double>();
        int adminTotal = 0;

        // collect country counts
        for (PlaceCandidate pc : pcList) {
            for (Place geo : pc.getPlaces()) {
                int len = geo.getPlaceName().length();
                String tmpCC = geo.getCountryCode();
                boolean notShort = (len > 3);

                if ((geo.isCountry() || geo.isNationalCapital() || geo.isAdmin1()) && notShort) {
                    Counter count = countryCounts.get(tmpCC);
                    if (count == null) {
                        count = new Counter(tmpCC);
                        countryCounts.put(tmpCC, count);
                    }
                    count.increment();
                    ++countryTotal;

                    // Specifically for ADM1 names -- 
                    if (geo.isAdmin1()) {
                        String adminKey = String.format("%s/%s",tmpCC, geo.getAdmin1());
                        Counter adm1 = adminCounts.get(adminKey);
                        if (adm1 == null) {
                            adm1 = new Counter(adminKey);
                            adminCounts.put(adminKey, adm1);
                        }
                        adm1.increment();
                        ++adminTotal;
                    }
                }
            }
        }

        // normalize country and admin counts by total seen
        for (Counter ctry : countryCounts.values()) {
            countryBiases.put(ctry.code, (double)ctry.count / countryTotal);
        }

        for (Counter adm1 : adminCounts.values()) {
            adminBiases.put(adm1.code, (double) adm1.count / adminTotal);
        }

        //create document level evidence based on the countries and admin1s seen

        for (String cc : countryBiases.keySet()) {
            PlaceEvidence ccEvid = new PlaceEvidence();
            ccEvid.setCountryCode(cc);
            ccEvid.setScope(Scope.DOCUMENT);
            ccEvid.setWeight(countryWeight * countryBiases.get(cc));
            ccEvid.setRule("CountryBias");
            docEvidList.add(ccEvid);
        }

        for (String adminKey : adminBiases.keySet()) {
            PlaceEvidence acEvid = new PlaceEvidence();
            String[] pieces = adminKey.split("/");
            acEvid.setCountryCode(pieces[0]);
            acEvid.setAdmin1(pieces[1]);
            acEvid.setScope(Scope.DOCUMENT);
            acEvid.setWeight(adminWeight * adminBiases.get(adminKey));
            acEvid.setRule("AdminBias");
            docEvidList.add(acEvid);
        }

        return docEvidList;
    }

    class Counter {

        String code = null;
        int count = 0;

        public void increment() {
            ++count;
        }

        public Counter(String c) {
            code = c;
        }
    }
} // end class
