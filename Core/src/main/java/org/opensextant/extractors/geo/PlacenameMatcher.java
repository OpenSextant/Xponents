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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
//import org.apache.solr.client.solrj.SolrRequest;
//import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opensextant.data.Place;
import org.opensextant.util.TextUtils;
import org.opensextant.util.SolrProxy;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.SolrTaggerRequest;

/**
 *
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 * <p />
 * This class is not thread-safe. It could be made to be with little effort.
 *
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class PlacenameMatcher {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /*
     * Marc: In the interest of optimization we made the Solr instance a static class
     * attribute that should be thread safe and shareable across instances of
     * SolrMatcher.
     * David: TODO bad design; don't make static
     */
    private static String requestHandler = "/tag";
    private static String coreName = "gazetteer";
    private static SolrProxy solr = null;
    private static SolrParams params = null;

    /*
     * Gazetteer specific stuff:
     */

    private static final String APRIORI_NAME_RULE = "AprioriNameBias";
    //private SolrTaggerRequest tag_request = null;
    private Map<Integer, Place> beanMap = new HashMap<Integer, Place>(100); // initial size

    private MatchFilter filter = null;
    private boolean allowLowercaseAbbrev = false;

    //updated after each call to getText();
    private int tagNamesTime;
    private int getNamesTime;
    private int totalTime;

    /**
     *
     * @throws IOException
     */
    public PlacenameMatcher() throws IOException {
        PlacenameMatcher.initialize();

        // Instance variable that will have the transient payload to tag
        // this is not thread safe and is not static:
        //tag_request = new SolrTaggerRequest(params, SolrRequest.METHOD.POST);

        // Pre-loading the Solr FST
        //
        try {
            tagText("trivial priming of the solr pump", "__initialization___");
        } catch (ExtractionException initErr) {
            throw new IOException("Unable to prime the tagger", initErr);
        }
    }

    /**
     * A flag that will allow us to tag "in" or "in."
     * as a possible abbreviation. By default such things are not abbreviations,
     * e.g., Indiana is typically IN or In. or Ind., for example. Oregon, OR or
     * Ore. etc.
     *
     * but almost never in or or for those cases.
     */
    public void setAllowLowerCaseAbbreviations(boolean b) {
        allowLowercaseAbbrev = b;
    }

    /**
     * Close solr resources.
     */
    public static void shutdown() {
        if (solr != null) {
            solr.close();
        }
    }

    public void setMatchFilter(MatchFilter f) {
        filter = f;
    }

    /**
     */
    protected static void initialize() throws IOException {

        if (solr != null) {
            return;
        }

        // NOTE: This is set via opensextant.apps.Config or by some other means
        // But it is required to intialize.  "gazetteer" is the core name of interest.
        // Being explicit here about the core name allows integrator to field multiple cores
        // in the same gazetteer.
        //
        String configSolrHome = System.getProperty("solr.solr.home");
        if (configSolrHome != null) {
            solr = new SolrProxy(configSolrHome, coreName);
        } else {
            solr = new SolrProxy(System.getProperty("solr.url"));//e.g. http://localhost:8983/solr/gazetteer/
        }
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set(CommonParams.QT, requestHandler);
        //request all fields in the Solr index
        // Do we need specific fields or *?  If faster use specific fields. TODO.
        //params.set(CommonParams.FL, "*,score");
        // Note -- removed score for now, as we have not evaluated how score could be used in this sense.
        // Score depends on FST creation and other factors.
        //
        // TODO: verify that all the right metadata is being retrieved here
        params.set(CommonParams.FL, "id,name,cc,adm1,adm2,feat_class,feat_code,geo,place_id," +
                "name_bias,id_bias,name_type");

        params.set("tagsLimit", 100000);
        params.set(CommonParams.ROWS, 100000);
        params.set("subTags", false);
        params.set("matchText", false);//we've got the input doc as a string instead

        /* Possible overlaps: ALL, NO_SUB, LONGEST_DOMINANT_RIGHT
         * See Solr Text Tagger documentation for details.
         */
        params.set("overlaps", "LONGEST_DOMINANT_RIGHT");
        //params.set("overlaps", "NO_SUB");

        PlacenameMatcher.params = params;
    }

    /**
     * Emphemeral metric for the current tagText() call. Caller must get these
     * numbers immediately after call.
     *
     * @return time to tag
     */
    public int getTaggingNamesTime() {
        return tagNamesTime;
    }

    /**
     * @return time to get gazetteer records.
     */
    public int getRetrievingNamesTime() {
        return getNamesTime;
    }

    /**
     * @return time to get gazetteer records.
     */
    public int getTotalTime() {
        return totalTime;
    }

    /**
     * Tag a document, returning PlaceCandidates for the mentions in document.
     * Converts a GATE document to a string and passes it to the Solr server via
     * HTTP POST. The tokens and featureName parameters are not used.
     *
     * @param buffer
     * @param docid
     *
     * @return place_candidates List of place candidates
     * @throws ExtractionException
     */
    public List<PlaceCandidate> tagText(String buffer, String docid) throws ExtractionException {
        // "tagsCount":10, "tags":[{ "ids":[35], "endOffset":40, "startOffset":38},
        // { "ids":[750308, 2769912, 2770041, 10413973, 10417546], "endOffset":49,
        // "startOffset":41},
        // ...
        // "matchingDocs":{"numFound":75, "start":0, "docs":[ {
        // "place_id":"USGS1992921", "name":"Monterrey", "cc":"PR"}, {
        //"place_id":"USGS1991763", "name":"Monterrey", "cc":"PR"}, ]

        long t0 = System.currentTimeMillis();
        log.debug("TEXT SIZE = {}", buffer.length());

        beanMap.clear();

        List<PlaceCandidate> candidates = new ArrayList<PlaceCandidate>();

        // Setup request to tag...
        final AtomicLong startStreamingTime = new AtomicLong();
        SolrTaggerRequest tagRequest = new SolrTaggerRequest(params, buffer);
        // Stream the response to avoid serialization and to save memory by
        // only keeping one SolrDocument materialized at a time
        tagRequest.setStreamingResponseCallback(new StreamingResponseCallback() {
            @Override
            public void streamDocListInfo(long numFound, long start, Float maxScore) {
                startStreamingTime.set(System.currentTimeMillis());
            }

            //Future optimization: it would be nice if Solr could give us the doc id without
            // giving us a SolrDocument, allowing us to conditionally get it.
            // it would save disk IO & speed, at the expense of putting ids into memory.
            @Override
            public void streamSolrDocument(final SolrDocument solrDoc) {
                Integer id = (Integer) solrDoc.getFirstValue("id");
                beanMap.put(id, createPlace(solrDoc));
            }
        });

        QueryResponse response;
        try {
            response = tagRequest.process(solr.getInternalSolrServer());
        } catch (Exception err) {
            throw new ExtractionException("Failed to tag document=" + docid, err);
        }

        this.tagNamesTime = response.getQTime();
        long t1 = startStreamingTime.get();
        long t2 = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");

        log.debug("DOC={} TAGS SIZE={}", docid, tags.size());

        /*
         * Retrieve all offsets into a long list.  These offsets will report
         * a text span and all the gazetteer record IDs that are associated to that span.
         * The text could either be a name, a code or some other abbreviation.
         *
         * For practical reasons the default behavior is to filter trivial spans given
         * the gazetteer data that is returned for them.
         *
         * WARNING: lots of optimizations occur here due to the potentially large volume of tags
         * and gazetteer data that is involved.  And this is relatively early in the pipline.
         *
         */
        PlaceCandidate pc;
        Place pGeo;
        int x1, x2;
        Set<String> seenPlaces = new HashSet<String>();
        Double nameBias;

        String matchText;
        for (NamedList<?> tag : tags) {
            x1 = (Integer) tag.get("startOffset");
            x2 = (Integer) tag.get("endOffset");//+1 char after last matched
            matchText = buffer.substring(x1, x2);

            /**
             * We can filter out trivial place name matches that we know to be
             * close to false positives 100% of the time. E.g,. "way", "back",
             * "north" You might consider two different stop filters, Is "North"
             * different than "north"? This first pass filter should really
             * filter out only text we know to be false positives regardless of
             * case. deprecated: use of filters here. Filter out unwanted tags
             * via GazetteerETL data model if
             */
            if (filter != null) {
                if (filter.filterOut(matchText.toLowerCase())) {
                    continue;
                }
            }

            pc = new PlaceCandidate();
            pc.start = x1;
            pc.end = x2;

            // Could have enabled the "matchText" option from the tagger to get
            // this, but since we already have the content as a String then
            // we might as well not make the tagger do any more work.
            pc.setText(matchText); //
            nameBias = 0.0;

            @SuppressWarnings("unchecked")
            List<Integer> placeRecordIds = (List<Integer>) tag.get("ids");
            //clear out places seen for the next candidate
            seenPlaces.clear();
            boolean isValid = true;
            boolean isLower = StringUtils.isAllLowerCase(pc.getText());

            for (Integer solrId : placeRecordIds) {
                pGeo = beanMap.get(solrId);

                //if (pGeo == null) {
                //    continue;
                //}

                // Optimization:  abbreviation filter.
                //
                // Do not add PlaceCandidates for lower case tokens that are marked as Abbreviations
                // Unless flagged to do so.
                // DEFAULT behavior is to avoid lower case text that is tagged as an abbreviation in gazetteer,
                //
                // Common terms:  in, or, oh, me, us, we,
                //   etc.
                // Are all not typically place names or valid abbreviations in text.
                //
                if (!allowLowercaseAbbrev) {
                    if (pGeo.isAbbreviation() && isLower) {
                        isValid = false;
                        log.debug("Ignore lower case term={}", pc.getText());

                        break;
                    }

                }
                // Optimization: Add distinct place objects once.
                //   don't add Place if null or already added to this instance of PlaceCandidate
                //
                if (!seenPlaces.contains(pGeo.getPlaceID())) {
                    pc.addPlace(pGeo);
                    seenPlaces.add(pGeo.getPlaceID());

                    // get max name bias
                    nameBias = Math.max(nameBias, pGeo.getName_bias());
                }
            }

            /**
             * Some rule above triggered a flag that indicates this
             * token/place/name is not valid.
             *
             */
            if (!isValid || !pc.hasPlaces()) {
                continue;
            }

            // if the max name bias seen >0; add apriori evidence
            //if (nameBias != 0.0) {
            if (nameBias > 0) {
                pc.addRuleAndConfidence(APRIORI_NAME_RULE, nameBias);
            }

            candidates.add(pc);
        }
        long t3 = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            summarizeExtraction(candidates, docid);
        }


        //this.tagNamesTime = (int)(t1 - t0);
        this.getNamesTime = (int) (t2 - t1);
        this.totalTime = (int) (t3 - t0);

        return candidates;
    }

    public static Place createPlace(SolrDocument gazEntry) {

        // Creates for now org.opensextant.placedata.Place
        Place bean = SolrProxy.createPlace(gazEntry);

        bean.setName_bias(SolrProxy.getDouble(gazEntry, "name_bias"));
        bean.setId_bias(SolrProxy.getDouble(gazEntry, "id_bias"));

        return bean;
    }

    /**
     * Debugging
     */
    private void summarizeExtraction(List<PlaceCandidate> candidates, String docid) {
        if (candidates == null) {
            log.error("Something is very wrong.");
            return;
        }

        log.debug("DOC=" + docid + " PLACE CANDIDATES SIZE = " + candidates.size());
        Map<String, Integer> countries = new HashMap<String, Integer>();
        int nullCount = 0;

        // This loops through findings and reports out just Country names for now.
        for (PlaceCandidate candidate : candidates) {
            boolean dobreak = false;
            String namekey = TextUtils.normalizeTextEntity(candidate.getText()); // .toLowerCase();
            if (namekey == null) {
                // Why is this Null?
                countries.put("null", ++nullCount);
                continue;
            } else {
                namekey = namekey.toLowerCase();
            }

            for (Place p : candidate.getPlaces()) {
                if (p == null) {
                    continue;
                }

                if (p.isAbbreviation()) {
                    log.debug("Ignore all abbreviations for now " + candidate.getText());
                    dobreak = true;
                    break;
                }
                if (p.isCountry()) {
                    Integer count = countries.get(namekey);
                    if (count == null) {
                        count = new Integer(1);
                        countries.put(namekey, count);
                    }
                    ++count;
                    countries.put(namekey, count);
                    dobreak = true;
                    break;
                }
            }
            if (dobreak) {
                continue;
            }
        }
        log.debug("Countries found:" + countries.toString());
    }

    /**
     * Do a basic test
     */
    public static void main(String[] args) throws Exception {
        //String solrHome = args[0];

        PlacenameMatcher sm = new PlacenameMatcher();

        try {
            String docContent = "We drove to Sin City. The we drove to -$IN ĆITŸ .";

            System.out.println(docContent);

            List<PlaceCandidate> matches = sm.tagText(docContent, "main-test");

            for (PlaceCandidate pc : matches) {
                System.out.println(pc.toString());
            }

            sm.shutdown();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
