/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *               http://www.apache.org/licenses/LICENSE-2.0
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
 * Continue contributions:
 *    Copyright 2013-2015 The MITRE Corporation.
 */
package org.opensextant.extractors.geo;

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
// OpenSextant GazetteerMatcher
//*  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//*/
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
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
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.SolrMatcherSupport;
import org.opensextant.util.SolrProxy;
import org.opensextant.util.TextUtils;
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
    /**
     * invocation counts:  default filter count and user filter count
     */
    private long defaultFilterCount = 0;
    private long userFilterCount = 0;
    /**
     * lifecycle counts:  default filter and matched counts.
     * Ratio of filtered to (filter+match) gives an idea of false positive rates.
     */
    private long filteredTotal = 0;
    private long matchedTotal = 0;
    private boolean allowLowercaseAbbrev = false;
    private boolean allowLowerCase = false; /* enable trure for data such as tweets, blogs, etc. where case varies or does not exist */
    // All of these Solr-parameters for tagging are not user-tunable.
    private final ModifiableSolrParams params = new ModifiableSolrParams();
    private SolrGazetteer gazetteer = null;

    public GazetteerMatcher() throws ConfigException {
        this(false);
    }

    /**
     * 
     * @param lowercaseAllowed  variant is case insensitive.
     * 
     * @throws ConfigException
     */
    public GazetteerMatcher(boolean lowercaseAllowed) throws ConfigException {
        initialize();
        allowLowerCase = lowercaseAllowed;

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
    }

    @Override
    public void initialize() throws ConfigException {

        super.initialize();

        /*
         * Setup matcher params.
         */
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

        gazetteer = new SolrGazetteer(this.solr);

    }

    @Override
    public String getCoreName() {
        return "gazetteer";
    }

    @Override
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
     * User-provided filters to filter out matched names immediately.
     * Avoid filtering out things that are indeed places, but require disambiguation or refinement.
     *
     * @param f a match filter
     */
    public void setMatchFilter(MatchFilter f) {
        userfilter = f;
    }

    /**
     * Geotag a buffer and return all candidates of gazetteer entries whose name matches phrases in the buffer.
     *
     * @param buffer
     * @param docid
     * @return
     * @throws ExtractionException
     */
    public LinkedList<PlaceCandidate> tagText(String buffer, String docid)
            throws ExtractionException {
        return tagText(buffer, docid, false);
    }

    /**
     * Tag names specifically with Chinese tokenizaiton
     * 
     * @param buffer
     * @param docid
     * @return
     * @since 2.7.11
     * @throws ExtractionException
     * 
     */
    public LinkedList<PlaceCandidate> tagCJKText(String buffer, String docid)
            throws ExtractionException {
        return tagText(buffer, docid, false, CJK_TAG_FIELD);
    }

    /**
     * Tag place names in arabic.
     * 
     * @param buffer
     * @param docid
     * @return
     * @throws ExtractionException
     */
    public LinkedList<PlaceCandidate> tagArabicText(String buffer, String docid)
            throws ExtractionException {
        return tagText(buffer, docid, false, AR_TAG_FIELD);
    }
    /** Most languages */
    public static final String DEFAULT_TAG_FIELD = "name_tag";

    /** Use /tag ? field = name_tag_cjk to tag in Asian scripts.
     * 
     */
    public static final String CJK_TAG_FIELD = "name_tag_cjk";

    public static final String AR_TAG_FIELD = "name_tag_ar";

    public LinkedList<PlaceCandidate> tagText(String buffer, String docid, boolean tagOnly)
            throws ExtractionException {
        return tagText(buffer, docid, tagOnly, DEFAULT_TAG_FIELD);
    }

    /**
     * Geotag a document, returning PlaceCandidates for the mentions in document.
     * Optionally just return the PlaceCandidates with name only and no Place objects attached.
     *
     * @param buffer text
     * @param docid  identity of the text
     * @param tagOnly True if you wish to get the matched phrases only. False if you want the full list of Place Candidates.
     *
     * @return place_candidates List of place candidates
     * @throws ExtractionException
     */
    public LinkedList<PlaceCandidate> tagText(String buffer, String docid, boolean tagOnly, String fld)
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

        // Reset counts.
        this.defaultFilterCount = 0;
        this.userFilterCount = 0;

        long t0 = System.currentTimeMillis();
        log.debug("TEXT SIZE = {}", buffer.length());

        params.set("field", fld);
        Map<Integer, Object> beanMap = new HashMap<Integer, Object>(100);
        QueryResponse response = tagTextCallSolrTagger(buffer, docid, beanMap);

        @SuppressWarnings("unchecked")
        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");

        this.tagNamesTime = response.getQTime();
        long t1 = t0 + tagNamesTime;
        long t2 = System.currentTimeMillis();
        boolean geocode = !tagOnly;

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

        tagLoop: for (NamedList<?> tag : tags) {

            int x1 = (Integer) tag.get("startOffset");
            int x2 = (Integer) tag.get("endOffset");
            int len = x2 - x1;
            if (len == 1) {
                // Ignoring place names whose length is less than 2 chars
                ++this.defaultFilterCount;
                continue;
            }
            // +1 char after last  matched
            // Could have enabled the "matchText" option from the tagger to get
            // this, but since we already have the content as a String then
            // we might as well not make the tagger do any more work.

            String matchText = (String) tag.get("matchText");
            // Then filter out trivial matches.
            if (len < 3 && !StringUtils.isAllUpperCase(matchText) && !allowLowercaseAbbrev) {
                ++this.defaultFilterCount;
                continue;
            }
            // Eliminate any newlines and extra whitespace in match
            matchText = TextUtils.squeeze_whitespace(matchText);

            /**
             * Filter out trivial tags. Due to normalization, we tend to get
             * lots of false positives that can be eliminated early.
             */
            if (filter.filterOut(matchText)) {
                ++this.defaultFilterCount;
                continue;
            }

            PlaceCandidate pc = new PlaceCandidate();
            pc.start = x1;
            pc.end = x2;
            pc.setText(matchText);

            /* Filter out tags that user determined ahead of time as
             * not-places for their context.
             *
             */
            if (userfilter != null) {
                if (userfilter.filterOut(pc.getTextnorm())) {
                    log.debug("User Filter:{}", matchText);
                    ++this.userFilterCount;
                    continue;
                }
            }

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
                // assert pGeo != null;

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

                /**
                 * Country names are the only names you can reasonably set ahead of time.
                 * All other names need to be assessed in context.  Negate country names,
                 * e.g., "Georgia", by exception.
                 */
                if (pGeo.isCountry()) {
                    pc.isCountry = true;
                }

                if (geocode) {
                    pc.addPlace(pGeo);
                }

                maxNameBias = Math.max(maxNameBias, pGeo.getName_bias());
            }// for place in tag

            // If geocoding, skip this PlaceCandidate if has no places (e.g. due to filtering)
            if (geocode && !pc.hasPlaces()) {
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

        this.filteredTotal += this.defaultFilterCount + this.userFilterCount;
        this.matchedTotal += list.size();

        return list;
    }

    /**
     * This computes the cumulative filtering rate of user-defined and other non-place name
     * patterns
     *
     * @return
     */
    public double getFiltrationRatio() {
        return (double) this.filteredTotal / (filteredTotal + matchedTotal);
    }

    @Override
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
     * false positives regardless of case.

     * Filter out unwanted tags via GazetteerETL data model or in Solr index. If
     * you believe certain items will always be filtered then set name_bias >
     * 0.0
     */
    class TagFilter extends MatchFilter {
        /**
         * This may need to be turned off for processing lower-case or dirty
         * data.
         */
        boolean filter_stopwords = true;
        boolean filter_on_case = true;
        Set<String> stopTerms = null;

        public TagFilter() throws ConfigException {
            super();
            stopTerms = GazetteerMatcher.loadExclusions(GazetteerMatcher.class
                    .getResource("/filters/non-placenames.csv"));
        }

        public void enableStopwordFilter(boolean b) {
            filter_stopwords = b;
        }

        public void enableCaseSensitive(boolean b) {
            filter_on_case = b;
        }

        @Override
        public boolean filterOut(String t) {
            if (filter_on_case && StringUtils.isAllLowerCase(t)) {
                return true;
            }

            if (filter_stopwords) {
                if (stopTerms.contains(t.toLowerCase())) {
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
     * @throws SolrServerException
     * @deprecated  Use SolrGazetteer directly
     */
    @Deprecated
    public List<Place> placesAt(LatLon yx) throws SolrServerException {
        return gazetteer.placesAt(yx, 50);
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
        if (file == null) {
            return null;
        }
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
