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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrRequest;
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
import org.opensextant.util.SolrUtil;
import org.opensextant.util.TextUtils;

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
    private TagFilter filter = null;
    private MatchFilter userfilter = null;
    private MatchFilter continents = null;

    /**
     * invocation counts: default filter count and user filter count
     */
    private long defaultFilterCount = 0;
    private long userFilterCount = 0;
    /**
     * lifecycle counts: default filter and matched counts. Ratio of filtered to
     * (filter+match) gives an idea of false positive rates.
     */
    private long filteredTotal = 0;
    private long matchedTotal = 0;
    private boolean allowLowercaseAbbrev = false;
    /*
     * enable trure for data such as tweets, blogs, etc. where case varies or
     * does not exist
     */
    private boolean allowLowerCase = false;
    // All of these Solr-parameters for tagging are not user-tunable.
    private final ModifiableSolrParams params = new ModifiableSolrParams();
    private SolrGazetteer gazetteer = null;

    public GazetteerMatcher() throws ConfigException {
        this(false);
    }

    /**
     * 
     * @param lowercaseAllowed
     *            variant is case insensitive.
     * 
     * @throws ConfigException
     *             on err
     */
    public GazetteerMatcher(boolean lowercaseAllowed) throws ConfigException {
        initialize();
        allowLowerCase = lowercaseAllowed;

        try {
            continents = new MatchFilter("/filters/continent-filter.txt");
        } catch (IOException err) {
            throw new ConfigException("Could not find continent list.", err);
        }
        // Instance variable that will have the transient payload to tag
        // this is not thread safe and is not static:
        // tag_request = new SolrTaggerRequest(params, SolrRequest.METHOD.POST);
        // Pre-loading the Solr FST
        //
        try {
            filter = new TagFilter();
            /*
             * Stop words should be filtered out by tagger/gazetteer filter
             */
            filter.enableStopwordFilter(true);
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
     * For use within package or by subclass
     * 
     * @return internal gazetteer instance
     */
    public SolrGazetteer getGazetteer() {
        return this.gazetteer;
    }

    /**
     * A flag that will allow us to tag "in" or "in." as a possible
     * abbreviation. By default such things are not abbreviations, e.g., Indiana
     * is typically IN or In. or Ind., for example. Oregon, OR or Ore. etc.
     *
     * but almost never 'in' or 'or' for those cases.
     *
     * @param b
     *            flag true = allow lower case abbreviations to be tagged, e.g.,
     *            as in social media or
     */
    public void setAllowLowerCaseAbbreviations(boolean b) {
        allowLowercaseAbbrev = b;
    }

    /**
     * User-provided filters to filter out matched names immediately. Avoid
     * filtering out things that are indeed places, but require disambiguation
     * or refinement.
     *
     * @param f
     *            a match filter
     */
    public void setMatchFilter(MatchFilter f) {
        userfilter = f;
    }

    /**
     * Default advanced search.
     * 
     * @see #searchAdvanced(String, boolean, int)
     * 
     * @param place
     * @param as_solr
     * @return
     * @throws SolrServerException
     */
    public List<ScoredPlace> searchAdvanced(String place, boolean as_solr) throws SolrServerException {
        return searchAdvanced(place, as_solr, -1);
    }

    /**
     * This is a variation on SolrGazetteer.search(), just this creates ScoredPlace which is
     * immediately usable with scoring and ranking matches. The score for a ScoredPlace is
     * created when added to PlaceCandidate: a default score is created for the place.
     * 
     * <pre>
     *    Usage:
     *    pc = PlaceCandidate();
     *    list = gaz.searchAdvanced("name:Boston", true)  // solr fielded query used as-is.
     *    for ScoredPlace p: list:
     *        pc.addPlace( p )
     * </pre>
     * 
     * @param place
     *            the place string or text; or a Solr query
     * @param as_solr
     *            the as_solr
     * @param maxLen
     *            max length of gazetteer place names.
     * @return places List of scoreable place entries
     * @throws SolrServerException
     *             the solr server exception
     */
    public List<ScoredPlace> searchAdvanced(String place, boolean as_solr, int maxLen) throws SolrServerException {

        if (as_solr) {
            params.set("q", place);
        } else {
            // Bare keyword query needs to be quoted as "word word word"
            params.set("q", "\"" + place + "\"");
        }

        QueryResponse response = solr.getInternalSolrServer().query(params, SolrRequest.METHOD.GET);

        List<ScoredPlace> places = new ArrayList<>();
        for (SolrDocument solrDoc : response.getResults()) {
            /*
             * Length Filter.  Alternative: store name as string in solr, vice full text field 
             */
            if (maxLen > 0) {
                String nm = SolrProxy.getString(solrDoc, "name");
                if (nm.length() > maxLen) {
                    continue;
                }
            }

            places.add(createPlace(solrDoc));
        }

        return places;
    }

    /**
     * Geotag a buffer and return all candidates of gazetteer entries whose name
     * matches phrases in the buffer.
     *
     * @param buffer
     *            text
     * @param docid
     *            ID
     * @return list of place candidates
     * @throws ExtractionException
     *             on err
     */
    public List<PlaceCandidate> tagText(String buffer, String docid) throws ExtractionException {
        return tagText(buffer, docid, false);
    }

    /**
     * Tag names specifically with Chinese tokenizaiton
     * 
     * @param buffer
     *            text
     * @param docid
     *            ID
     * @return list of place candidates
     * @since 2.7.11
     * @throws ExtractionException
     *             on err
     * 
     */
    public List<PlaceCandidate> tagCJKText(String buffer, String docid) throws ExtractionException {
        return tagText(buffer, docid, false, CJK_TAG_FIELD);
    }

    public List<PlaceCandidate> tagCJKText(String buffer, String docid, boolean tagOnly)
            throws ExtractionException {
        return tagText(buffer, docid, tagOnly, CJK_TAG_FIELD);
    }

    /**
     * Tag place names in arabic.
     * 
     * @param buffer
     *            text
     * @param docid
     *            ID
     * @since 2.7.11
     * @return list of place candidates
     * @throws ExtractionException
     *             on err
     */
    public List<PlaceCandidate> tagArabicText(String buffer, String docid) throws ExtractionException {
        return tagText(buffer, docid, false, AR_TAG_FIELD);
    }

    public List<PlaceCandidate> tagArabicText(String buffer, String docid, boolean tagOnly)
            throws ExtractionException {
        return tagText(buffer, docid, tagOnly, AR_TAG_FIELD);
    }

    /** Most languages */
    public static final String DEFAULT_TAG_FIELD = "name_tag";

    /**
     * Use Solr param 'field' = name_tag_cjk to tag in Asian scripts.
     */
    public static final String CJK_TAG_FIELD = "name_tag_cjk";

    /**
     * Use Solr param 'field = name_tag_ar for Arabic.
     */
    public static final String AR_TAG_FIELD = "name_tag_ar";

    public List<PlaceCandidate> tagText(String buffer, String docid, boolean tagOnly) throws ExtractionException {
        return tagText(buffer, docid, tagOnly, DEFAULT_TAG_FIELD);
    }

    /**
     * Geotag a document, returning PlaceCandidates for the mentions in
     * document. Optionally just return the PlaceCandidates with name only and
     * no Place objects attached. Names of contients are passed back as matches,
     * with geo matches. Continents are filtered out by default.
     *
     * @param buffer
     *            text
     * @param docid
     *            identity of the text
     * @param tagOnly
     *            True if you wish to get the matched phrases only. False if you
     *            want the full list of Place Candidates.
     * @param fld
     *            gazetteer field to use for tagging
     * @return place_candidates List of place candidates
     * @throws ExtractionException
     *             on err
     */
    public List<PlaceCandidate> tagText(String buffer, String docid, boolean tagOnly, String fld)
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
        // during post-processing tags we may have to distinguish between tagging/tokenizing 
        // general vs. cjk vs. ar. But not yet though.
        // boolean useGeneralMode = DEFAULT_TAG_FIELD.equals(fld);

        long t0 = System.currentTimeMillis();
        log.debug("TEXT SIZE = {}", buffer.length());
        int[] textMetrics = TextUtils.measureCase(buffer);
        boolean isUpperCase = TextUtils.isUpperCaseDocument(textMetrics);

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
            // +1 char after last matched
            // Could have enabled the "matchText" option from the tagger to get
            // this, but since we already have the content as a String then
            // we might as well not make the tagger do any more work.

            String matchText = (String) tag.get("matchText");
            // Get char immediately following match, for light NLP rules.
            char postChar = 0;
            if (x2 < buffer.length()) {
                postChar = buffer.charAt(x2);
            }

            // Then filter out trivial matches. E.g., Us is filtered out. vs. US would. 
            // be allowed. If lowercase abbreviations are allowed, then all matches are passed.               
            if (len < 3) {
                if (!allowLowercaseAbbrev) {
                    if (TextUtils.isASCII(matchText) && !StringUtils.isAllUpperCase(matchText)) {
                        ++this.defaultFilterCount;
                        continue;
                    }
                }
            }

            if (TextUtils.countFormattingSpace(matchText) > 1) {
                // Phrases with words broken across more than one line are not
                // valid matches.
                // Phrase with a single TAB is okay
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

            /*
             * Filter out tags that user determined ahead of time as not-places
             * for their context.
             *
             */
            if (userfilter != null) {
                if (userfilter.filterOut(pc.getTextnorm())) {
                    log.debug("User Filter:{}", matchText);
                    ++this.userFilterCount;
                    continue;
                }
            }

            /*
             * Continent filter is needed, as many mentions of contients confuse
             * real geotagging/geocoding.
             * 
             */
            if (continents.filterOut(pc.getTextnorm())) {
                pc.isContinent = true;
                pc.setFilteredOut(true);
                candidates.put(pc.start, pc);
                continue;
            }
            /*
             * Everything Else.
             * ============================
             */
            /*
             * Found UPPER CASE text in a mixed-cased document.
             * Conservatively, this is likely an acronym or some heading.
             * But possibly still a valid place name.
             * HEURISTIC: acronyms are relatively short. 
             * HEURISTIC: region codes can be acronyms and are valid places
             * 
             * using such place candidates you may score short acronym matches lower than fully named ones.
             * when inferring boundaries (states, provinces, etc)
             */
            if (!isUpperCase && pc.isUpper() && len < 5) {
                pc.isAcronym = true;
            }
            pc.hasDiacritics = TextUtils.hasDiacritics(pc.getText());

            pc.setSurroundingTokens(buffer);

            @SuppressWarnings("unchecked")
            List<Integer> placeRecordIds = (List<Integer>) tag.get("ids");

            /*
             * This assertion is helpful in debugging: assert
             * placeRecordIds.size() == new
             * HashSet<Integer>(placeRecordIds).size() : "ids should be unique";
             */
            // assert!placeRecordIds.isEmpty();
            namesMatched.clear();

            //double maxNameBias = 0.0;
            for (Integer solrId : placeRecordIds) {
                // Yes, we must cast here.
                // As long as createTag generates the correct type stored in
                // beanMap we are fine.
                ScoredPlace pGeo = (ScoredPlace) beanMap.get(solrId);
                // assert pGeo != null;

                // Optimization: abbreviation filter.
                //
                // Do not add PlaceCandidates for lower case tokens that are
                // marked as Abbreviations, unless flagged to do so.
                //
                // DEFAULT behavior is to avoid lower case text that is tagged
                // as an abbreviation in gazetteer,
                //
                // Common terms: in, or, oh, me, us, we, etc. Are all not
                // typically place names or valid abbreviations in text.
                //
                if (!allowLowercaseAbbrev && pGeo.isAbbreviation() && pc.isLower()) {
                    log.debug("Ignore lower case term={}", pc.getText());
                    // DWS: TODO what if there is another pGeo for this pc that
                    // isn't an abbrev? Therefore shouldn't we continue this
                    // loop and not tagLoop?
                    continue tagLoop;
                }

                /*
                 * If text match contains "." and it matches any abbreviation,
                 * mark the candidate as an abbrev. TODO: Possibly best confirm
                 * this by sentence detection, as well. However, this pertains
                 * to text spans that contain "." within the bounds, and not
                 * likely an ending. E.g., "U.S." or "U.S" are trivial examples;
                 * "US" is more ambiguous, as we need to know if document is
                 * upperCase.
                 * 
                 * Any place abbreviation will trigger isAbbreviation = true
                 * 
                 * "IF YOU FIND US HERE"  the term 'US' is ambiguous here, so 
                 * it is not classified as an abbreviation. Otherwise if you have
                 * "My organization YAK happens to coincide with a place named Yak.
                 * But we first must determine if 'YAK' is a valid abbreviation for an actual place.
                 * HEURISTIC: place abbreviations are relatively short, e.g. one word(len=7 or less)
                 */
                if (len < 8 && !pc.isAbbreviation) {
                    assessAbbreviation(pc, pGeo, postChar, isUpperCase);
                }

                if (log.isDebugEnabled()) {
                    namesMatched.add(pGeo.getName());
                }

                /**
                 * Country names are the only names you can reasonably set ahead
                 * of time. All other names need to be assessed in context.
                 * Negate country names, e.g., "Georgia", by exception.
                 */
                if (pGeo.isCountry()) {
                    pc.isCountry = true;
                }

                if (geocode) {
                    pGeo.defaultHierarchicalPath();
                    // Default score for geo will be calculated in PlaceCandidate
                    pc.addPlace(pGeo);
                }
            }

            // If geocoding, skip this PlaceCandidate if has no places (e.g. due
            // to filtering)
            if (geocode && !pc.hasPlaces()) {
                log.debug("Place has no places={}", pc.getText());
                continue;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Text {} matched {}", pc.getText(), namesMatched);
                }
            }

            candidates.put(pc.start, pc);
        } // for tag
        long t3 = System.currentTimeMillis();

        // this.tagNamesTime = (int)(t1 - t0);
        this.getNamesTime = (int) (t2 - t1);
        this.totalTime = (int) (t3 - t0);

        if (log.isDebugEnabled()) {
            summarizeExtraction(candidates.values(), docid);
        }

        this.filteredTotal += this.defaultFilterCount + this.userFilterCount;
        this.matchedTotal += candidates.size();

        return new ArrayList<PlaceCandidate>(candidates.values());
    }

    private void assessAbbreviation(PlaceCandidate pc, ScoredPlace pGeo, char postChar, boolean docIsUPPER) {
        /* - Block re-entry to this logic. If Match is already marked as ABBREV, 
         * then no need to review
         * - We don't consider abbreviations longer than N=7 chars.
         * - If matched geo-location does not represent an abbreviation than this does not apply.
         * 
         * - postchar = 0 (null) means there is no chars after the match because Match is at end of text buffer.
         */
        if (!pGeo.isAbbreviation() || postChar == 0) {
            return;
        }

        if (postChar == '.') {
            // Add the post-punctuation to the match ONLY if a potential GEO matches. 
            pc.isAbbreviation = true;
            pc.end += 1;
            pc.setTextOnly(String.format("%s.", pc.getText()));
        } else if (pc.getText().contains(".")) {
            /* TODO: contains abbreviation. E.g. ,'St. Paul' is not fully 
             * an abbreviation.
             */
            pc.isAbbreviation = true;
        } else if (!docIsUPPER && pc.isUpper()) {
            /* Hack Warning: NOT everything UPPERCASE in a document
             * is an abbrev. 
             */
            // Upper case place matched
            pc.isAbbreviation = true;
            // Matched text is UPPER in a non-upper case document                        
            pc.isAcronym = true;
        }
        // Lower or mixed-case abbreviations without "." are not
        // tagged Mr, Us, etc.

    }

    /**
     * This computes the cumulative filtering rate of user-defined and other
     * non-place name patterns
     *
     * @return filtration ratio
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
     * @param gazEntry
     *            a solr record from the gazetteer
     * @return Place (Xponents) object
     */
    public static ScoredPlace createPlace(SolrDocument gazEntry) {

        // Creates for now org.opensextant.placedata.Place
        //Place bean = SolrProxy.createPlace(gazEntry);
        String plid = SolrUtil.getString(gazEntry, "place_id");
        String nm = SolrProxy.getString(gazEntry, "name");
        ScoredPlace bean = new ScoredPlace(plid, nm);
        SolrProxy.populatePlace(gazEntry, bean);

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
                 * if (p.isAbbreviation()) { log.debug(
                 * "Ignore all abbreviations for now " + candidate.getText());
                 * dobreak = true; break; }
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

    /**
     * Find places located at a particular location.
     *
     * @param yx
     *            location
     * @return list of places near location
     * @throws SolrServerException
     *             on err
     * @deprecated Use SolrGazetteer directly
     */
    @Deprecated
    public List<Place> placesAt(LatLon yx) throws SolrServerException {
        return gazetteer.placesAt(yx, 50);
    }

}
