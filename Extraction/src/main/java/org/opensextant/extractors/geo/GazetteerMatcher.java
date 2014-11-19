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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.opensextant.ConfigException;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.util.SolrProxy;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.SolrMatcherSupport;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

/**
 * 
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 * <p >
 * This class is not thread-safe. It could be made to be with little effort.
 * 
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class GazetteerMatcher extends SolrMatcherSupport {

    /*
     * Gazetteer specific stuff:
     */
    private static final String APRIORI_NAME_RULE = "AprioriNameBias";
    private TagFilter filter = null;
    private MatchFilter userfilter = null;
    private boolean allowLowercaseAbbrev = false;
    private boolean allowLowerCase = false; /* enable trure for data such as tweets, blogs, etc. where case varies or does not exist */
    private ModifiableSolrParams geoLookup = new ModifiableSolrParams();

    /**
     * 
     * @throws IOException
     */
    public GazetteerMatcher() throws ConfigException {
        initialize();

        // Instance variable that will have the transient payload to tag
        // this is not thread safe and is not static:
        // tag_request = new SolrTaggerRequest(params, SolrRequest.METHOD.POST);
        // Pre-loading the Solr FST
        //
        try {
            filter = new TagFilter();
            filter.enableStopwordFilter(true); /* Stop words should be filtered out by tagger/gazetteer filter query */
            filter.enableCaseSensitive(!allowLowerCase);
            tagText("trivial priming of the solr pump", "__initialization___");
        } catch (ExtractionException initErr) {
            throw new ConfigException("Unable to prime the tagger", initErr);
        }

        /* Basic parameters for geospatial lookup.
         * These are reused, and only pt and d are set for each lookup.
         * 
         */
        geoLookup.set(CommonParams.FL, "id,name,cc,adm1,adm2,feat_class,feat_code,"
                + "geo,place_id,name_bias,id_bias,name_type");
        geoLookup.set(CommonParams.ROWS, 10);

        geoLookup.set(CommonParams.Q, "*:*");
        geoLookup.set(CommonParams.FQ, "{!geofilt}");
        geoLookup.set("spatial", true);
        geoLookup.set("sfield", "geo");

        //geoLookup.set("facet", true);
        //geoLookup.add("facet.field", "cc"); // List found country codes.
        //geoLookup.add("facet.field", "adm1"); // List found ADM1
        geoLookup.add("sort geodist asc"); // Find closest places first.
    }

    public String getCoreName() {
        return "gazetteer";
    }

    // All of these Solr-parameters for tagging are not user-tunable.
    private static final ModifiableSolrParams params = new ModifiableSolrParams();
    static {
        params.set(CommonParams.FL,
                "id,name,cc,adm1,adm2,feat_class,feat_code,geo,place_id,name_bias,id_bias,name_type");
        params.set("tagsLimit", 100000);
        params.set(CommonParams.ROWS, 100000);
        params.set("subTags", false);

        // we've got the input doc as a string instead; matchText=false means
        // the tagger will not report the text, just span offsets.
        params.set("matchText", false);

        /*
         * Possible overlaps: ALL, NO_SUB, LONGEST_DOMINANT_RIGHT See Solr Text
         * Tagger documentation for details.
         */
        params.set("overlaps", "LONGEST_DOMINANT_RIGHT");
        // params.set("overlaps", "NO_SUB");
    }

    public SolrParams getMatcherParameters() {
        return params;
    }

    /**
     * A flag that will allow us to tag "in" or "in." as a possible
     * abbreviation. By default such things are not abbreviations, e.g., Indiana
     * is typically IN or In. or Ind., for example. Oregon, OR or Ore. etc.
     * 
     * but almost never 'in' or 'or' for those cases.
     *
     * @param b flag true = allow lower case abbreviations to be tagged, e.g., as in social media or 
     */
    public void setAllowLowerCaseAbbreviations(boolean b) {
        allowLowercaseAbbrev = b;
    }

    /**
     * Currently unused user filter; default TagFilter is used internally.
     * 
     * @param f a match filter
     */
    public void setMatchFilter(MatchFilter f) {
        userfilter = f;
    }

    /**
     * Tag a document, returning PlaceCandidates for the mentions in document.
     * 
     * @param buffer text
     * @param docid  identity of the text
     * 
     * @return place_candidates List of place candidates
     * @throws ExtractionException
     */
    public LinkedList<PlaceCandidate> tagText(String buffer, String docid)
            throws ExtractionException {
        // "tagsCount":10, "tags":[{ "ids":[35], "endOffset":40,
        // "startOffset":38},
        // { "ids":[750308, 2769912, 2770041, 10413973, 10417546],
        // "endOffset":49,
        // "startOffset":41},
        // ...
        // "matchingDocs":{"numFound":75, "start":0, "docs":[ {
        // "place_id":"USGS1992921", "name":"Monterrey", "cc":"PR"}, {
        // "place_id":"USGS1991763", "name":"Monterrey", "cc":"PR"}, ]

        long t0 = System.currentTimeMillis();
        log.debug("TEXT SIZE = {}", buffer.length());

        Map<Integer, Object> beanMap = new HashMap<Integer, Object>(100);
        QueryResponse response = tagTextCallSolrTagger(buffer, docid, beanMap);

        @SuppressWarnings("unchecked")
        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");

        this.tagNamesTime = response.getQTime();
        long t1 = t0 + tagNamesTime;
        long t2 = System.currentTimeMillis();

        /*
         * Retrieve all offsets into a long list. These offsets will report a
         * text span and all the gazetteer record IDs that are associated to
         * that span. The text could either be a name, a code or some other
         * abbreviation.
         * 
         * For practical reasons the default behavior is to filter trivial spans
         * given the gazetteer data that is returned for them.
         * 
         * WARNING: lots of optimizations occur here due to the potentially
         * large volume of tags and gazetteer data that is involved. And this is
         * relatively early in the pipline.
         */
        log.debug("DOC={} TAGS SIZE={}", docid, tags.size());

        TreeMap<Integer, PlaceCandidate> candidates = new TreeMap<Integer, PlaceCandidate>();

        // names matched is used only for debugging, currently.
        Set<String> namesMatched = new HashSet<>();
        int docSize = buffer.length();

        tagLoop: for (NamedList<?> tag : tags) {

            int x1 = (Integer) tag.get("startOffset");
            int x2 = (Integer) tag.get("endOffset");
            int len = x2 - x1;
            if (len == 1) {
                // Ignoring place names whose length is less than 2 chars 
                continue;
            }
            // +1 char after last  matched
            // Could have enabled the "matchText" option from the tagger to get
            // this, but since we already have the content as a String then
            // we might as well not make the tagger do any more work.

            //String matchText = buffer.substring(x1, x2);
            String matchText = (String) tag.get("matchText");
            if (len < 3 && !StringUtils.isAllUpperCase(matchText) && !allowLowercaseAbbrev) {
                continue;
            }

            /**
             * Filter out trivial tags. Due to normalization, we tend to get
             * lots of false positives that can be eliminated early.
             */
            if (filter.filterOut(matchText)) {
                continue;
            }

            PlaceCandidate pc = new PlaceCandidate();
            pc.start = x1;
            pc.end = x2;
            pc.setText(matchText);
            pc.setSurroundingTokens(buffer);

            @SuppressWarnings("unchecked")
            List<Integer> placeRecordIds = (List<Integer>) tag.get("ids");

            /* This assertion is helpful in debugging:
            assert placeRecordIds.size() == new HashSet<Integer>(placeRecordIds).size() : "ids should be unique";
            */
            assert !placeRecordIds.isEmpty();
            namesMatched.clear();

            double maxNameBias = 0.0;
            for (Integer solrId : placeRecordIds) {
                // Yes, we must cast here.  
                // As long as createTag generates the correct type stored in beanMap we are fine.
                Place pGeo = (Place) beanMap.get(solrId);
                assert pGeo != null;

                // Optimization: abbreviation filter.
                //
                // Do not add PlaceCandidates for lower case tokens that are
                // marked as Abbreviations, unless flagged to do so.
                //
                // DEFAULT behavior is to avoid lower case text that is tagged
                // as an abbreviation in gazetteer,
                //
                // Common terms: in, or, oh, me, us, we, etc. Are all not typically place names or valid abbreviations in text.
                //
                if (!allowLowercaseAbbrev && pGeo.isAbbreviation() && pc.isLower()) {
                    log.debug("Ignore lower case term={}", pc.getText());
                    // DWS: TODO what if there is another pGeo for this pc that
                    // isn't an abbrev? Therefore shouldn't we continue this loop and not
                    // tagLoop?
                    continue tagLoop;
                }

                if (log.isDebugEnabled()) {
                    namesMatched.add(pGeo.getName());
                }

                pc.addPlace(pGeo);

                maxNameBias = Math.max(maxNameBias, pGeo.getName_bias());
            }// for place in tag

            // Skip this PlaceCandidate if has no places (e.g. due to filtering)
            if (!pc.hasPlaces()) {
                log.debug("Place has no places={}", pc.getText());
                continue;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Text {} matched {}", pc.getText(), namesMatched);
                }
            }

            // if the max name bias seen >0, add apriori evidence
            if (maxNameBias > 0) {
                pc.addRuleAndConfidence(APRIORI_NAME_RULE, maxNameBias);
            }

            candidates.put(pc.start, pc);
        }// for tag
        long t3 = System.currentTimeMillis();

        // this.tagNamesTime = (int)(t1 - t0);
        this.getNamesTime = (int) (t2 - t1);
        this.totalTime = (int) (t3 - t0);

        if (log.isDebugEnabled()) {
            summarizeExtraction(candidates.values(), docid);
        }

        LinkedList<PlaceCandidate> list = new LinkedList<>();
        list.addAll(candidates.values());
        return list;
    }

    public Object createTag(SolrDocument tag) {
        return createPlace(tag);
    }

    /**
     * Adapt the SolrProxy method for creating a Place object. Here, for
     * disambiguation down stream gazetteer metrics are added.
     * 
     * @param gazEntry a solr record from the gazetteer
     * @return Place (Xponents) object
     */
    public static Place createPlace(SolrDocument gazEntry) {

        // Creates for now org.opensextant.placedata.Place
        Place bean = SolrProxy.createPlace(gazEntry);

        bean.setName_bias(SolrProxy.getDouble(gazEntry, "name_bias"));
        bean.setId_bias(SolrProxy.getDouble(gazEntry, "id_bias"));

        return bean;
    }

    private void summarizeExtraction(Collection<PlaceCandidate> candidates, String docid) {
        if (candidates == null) {
            log.error("Something is very wrong.");
            return;
        }

        log.debug("DOC=" + docid + " PLACE CANDIDATES SIZE = " + candidates.size());
        Map<String, Integer> countries = new HashMap<String, Integer>();
        Map<String, Integer> places = new HashMap<String, Integer>();
        int nullCount = 0;

        // This loops through findings and reports out just Country names for
        // now.
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

                /*
                 * if (p.isAbbreviation()) {
                 * log.debug("Ignore all abbreviations for now " +
                 * candidate.getText()); dobreak = true; break; }
                 */
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
                } else {
                    Integer count = places.get(namekey);
                    if (count == null) {
                        count = new Integer(1);
                        places.put(namekey, count);
                    }
                    ++count;
                    places.put(namekey, count);
                }
            }
            if (dobreak) {
                continue;
            }
        }
        log.debug("Countries found:" + countries.toString());
        log.debug("Places found:" + places.toString());
    }

    /*
     * We can filter out trivial place name matches that we know to be close to
     * false positives 100% of the time. E.g,. "way", "back", "north" You might
     * consider two different stop filters, Is "North" different than "north"?
     * This first pass filter should really filter out only text we know to be
     * false positives regardless of case. deprecated: use of filters here.
     * Filter out unwanted tags via GazetteerETL data model or in Solr index if
     * you believe certain items will always be filtered. Then set name_bias <
     * 0.0
     */
    class TagFilter extends MatchFilter {

        static final int MIN_WORD_LEN = 3;

        /**
         * This may need to be turned off for processing lower-case or dirty
         * data.
         */
        boolean filter_stopwords = true;
        boolean filter_on_case = true;
        Set<String> stopTerms = null;

        public TagFilter() throws ConfigException {
            super();
            stopTerms = GazetteerMatcher.loadExclusions(GazetteerMatcher.class.getResource("/exclusions/non-placenames.csv"));
        }

        public void enableStopwordFilter(boolean b) {
            filter_stopwords = b;
        }

        public void enableCaseSensitive(boolean b) {
            filter_on_case = b;
        }

        @Override
        public boolean filterOut(String t) {
            if (filter_on_case && StringUtils.isAllLowerCase(t) /*&& t.length() < MIN_WORD_LEN*/) {
                return true;
            }

            if (filter_stopwords) {
                if (stopTerms.contains(t.toLowerCase())){
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Find places located at a particular location.
     * 
     * @param yx
     * @return
     */
    public List<Place> placesAt(LatLon yx) {

        /* URL as such:
         * Find just Admin places and country codes for now.
        /solr/gazetteer/select?q=*%3A*&fq=%7B!geofilt%7D&rows=100&wt=json&indent=true&facet=true&facet.field=cc&facet.mincount=1&facet.field=adm1&spatial=true&pt=41%2C-71.5&sfield=geo&d=100&sort geodist asc
         * 
         */
        geoLookup.set("pt", GeodeticUtility.formatLatLon(yx)); // The point in question.
        geoLookup.set("d", 50); // Find places within 50 KM, but only first is really used.

        try {
            List<Place> places = SolrGazetteer.search(this.solr.getInternalSolrServer(), geoLookup);
            return places;
        } catch (SolrServerException e) {
            this.log.error("Failed to search gazetter by location");
        }
        return null;
    }

    /**
     * Exclusions have two columns in a CSV file.
     * 'exclusion', 'category'
     * 
     * "#" in exclusion column implies a comment.
     * @param file
     * @return
     * @throws ConfigException
     */
    public static Set<String> loadExclusions(URL file) throws ConfigException {
        /* Load the exclusion names -- these are terms that are 
         * gazeteer entries, e.g., gazetteer.name = <exclusion term>,
         * that will be marked as search_only = true.
         * 
         */
        InputStream io = null;
        CsvMapReader termreader = null;
        try {
            io = file.openStream();
            java.io.Reader termsIO = new InputStreamReader(io);
            termreader = new CsvMapReader(termsIO, CsvPreference.EXCEL_PREFERENCE);
            String[] columns = termreader.getHeader(true);
            Map<String, String> terms = null;
            HashSet<String> stopTerms = new HashSet<String>();
            while ((terms = termreader.read(columns)) != null) {

                String term = terms.get("exclusion");
                if (StringUtils.isBlank(term) || term.startsWith("#")) {
                    continue;
                }
                stopTerms.add(term.toLowerCase().trim());
            }
            return stopTerms;
        } catch (Exception err) {
            throw new ConfigException("Could not load exclusions.", err);
        } finally {
            if (termreader != null) {
                try {
                    termreader.close();
                    io.close();
                } catch (IOException err2) {

                }
            }
        }

    }
}
