/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
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
 */
package org.opensextant.extractors.xtax;

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
//  _____                               ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//              \ \_\
//               \/_/
//
//OpenSextant TaxonMatcher
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//

import java.io.IOException;
import java.util.*;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.opensextant.ConfigException;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.SolrMatcherSupport;
import org.opensextant.extraction.TextMatch;
import org.opensextant.processing.Parameters;
import org.opensextant.util.SolrUtil;
import org.opensextant.util.TextUtils;

/**
 * TaxonMatcher uses SolrTextTagger to tag mentions of phrases in documents. The phrases can be
 * from simple word lists or they can connect to a taxonomy of sorts. the "taxcat" solr core (see Xponents/solr/taxcat)
 *
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class TaxonMatcher extends SolrMatcherSupport implements Extractor {

    private static final ModifiableSolrParams params;

    static {
        params = new ModifiableSolrParams();
        params.set(CommonParams.FL, "id,catalog,taxnode,phrase,tag,name_type");

        params.set("tagsLimit", 100000);
        params.set("subTags", false);
        params.set("matchText", false);
        params.set(CommonParams.FQ, "valid:true");

        /*
         * Possible overlaps: ALL, NO_SUB, LONGEST_DOMINANT_RIGHT See Solr Text
         * Tagger documentation for details.
         */
        params.set("overlaps", "NO_SUB");
    }

    private boolean tagAll = true;
    private boolean tagLowerCase = false;

    /* acronym filter May change later: */
    private final boolean filterNonAcronyms = true;

    /** Caller can adjust this default constant if shorter tags are desired. */
    public static final int DEFAULT_MIN_LENGTH = 3;

    /**
     * @throws ConfigException errors related to configuration, resource files or Solr setup
     */
    public TaxonMatcher() throws ConfigException {
        configure();
    }

    /**
     * Extractor interface.
     */
    @Override
    public void cleanup() {
        this.close();
    }

    /**
     * Be explicit about the solr core to use for tagging
     */
    @Override
    public String getCoreName() {
        return "taxcat";
    }

    /**
     * Return the Solr Parameters for the tagger op.
     *
     * @return solr params
     */
    @Override
    public SolrParams getMatcherParameters() {
        return params;
    }

    /**
     * Create a Taxon tag, which is filtered based on established catalog filters.
     * Caller must implement their domain objects, POJOs... this callback handler
     * only hashes them.
     *
     * @param refData solr doc
     * @return tag data
     */
    @Override
    public Object createTag(SolrDocument refData) {

        String _cat = SolrUtil.getString(refData, "catalog");

        // Filter out unused matching records.
        if (!tagAll && !this.catalogs.contains(_cat)) {
            return null;
        }
        if (!taxonExclusionFilter.isEmpty()) {
            String name = SolrUtil.getString(refData, "taxnode");
            if (name != null) {
                name = name.toLowerCase();
                for (String t : taxonExclusionFilter) {
                    if (name.startsWith(t)) {
                        return null;
                    }
                }
            }
        }
        return createTaxon(refData);
    }

    /**
     * Parse the taxon reference data from a solr doc and return Taxon obj.
     *
     * @param refData solr doc
     * @return taxon obj
     */
    public static Taxon createTaxon(SolrDocument refData) {
        Taxon label = new Taxon();

        label.name = SolrUtil.getString(refData, "taxnode");

        label.isAcronym = "A".equals(SolrUtil.getString(refData, "name_type"));
        label.catalog = SolrUtil.getString(refData, "catalog");

        label.addTerm(SolrUtil.getString(refData, "phrase"));
        label.addTags(SolrUtil.getStrings(refData, "tag"));
        return label;
    }

    /**
     * Extractor interface: getName
     *
     * @return Extractor name
     */
    @Override
    public String getName() {
        return "XTax";
    }

    @Override
    public void configure() throws ConfigException {
        try {
            initialize();
            extract(new TextInput("__initialization___", "trivial priming of the solr pump"));
        } catch (Exception err) {
            throw new ConfigException("Failed to configure TaxMatcher", err);
        }
    }

    /**
     * Configure an Extractor using a config file named by a path
     *
     * @param patfile configuration file path
     */
    @Override
    public void configure(String patfile) throws ConfigException {
        throw new ConfigException("Not a valid configuration routine");
    }

    /**
     * Configure an Extractor using a config file named by a URL
     *
     * @param patfile configuration URL
     */
    @Override
    public void configure(java.net.URL patfile) throws ConfigException {
        throw new ConfigException("Not a valid configuration routine");

    }

    /**
     * Catalogs is a list of catalogs caller wants to tag for. If set, only taxon
     * matches with the
     * catalog ID in this list will be returned by tagText()
     */
    public final Set<String> catalogs = new HashSet<>();

    public void addCatalogFilters(String[] cats) {
        catalogs.addAll(Arrays.asList(cats));
        tagAll = false;
        // reset();
    }

    public void addCatalogFilter(String cat) {
        catalogs.add(cat);
        tagAll = false;
    }

    public void removeFilters() {
        catalogs.clear();
        taxonExclusionFilter.clear();
        tagAll = true;
    }

    private final Set<String> taxonExclusionFilter = new HashSet<>();

    private final TaxonFilter ruleFilter = new TaxonFilter();

    /**
     * Add prefixes of types of taxons you do not want returned. e.g., "Place...."
     * exlclude will allow "Org" and "Person" taxons to pass on thru
     *
     * @param prefix taxon name prefix
     */
    public void excludeTaxons(String prefix) {
        taxonExclusionFilter.add(prefix.toLowerCase());
    }

    /**
     * Light-weight usage: text in, matches out. Behaviors: ACRONYMS matching lower
     * case terms will
     * automatically be omitted from results.
     *
     * @return Null if nothing found, otherwise a list of TextMatch objects
     */
    @Override
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extractorImpl(null, input_buf, null);
    }

    /**
     * Parameterized extraction, e.g., for REST service or other fine tuning.
     * @param input text to tag
     * @param params tagging parameters
     * @return array of TextMatch
     * @throws ExtractionException on Solr Tagger error
     */
    public List<TextMatch> extract(TextInput input, Parameters params) throws ExtractionException {
        return extractorImpl(input.id, input.buffer, params);
    }

    public void setAllowLowerCase(boolean b) {
        this.tagLowerCase = b;
    }

    protected static final String[] commonTaxonLabels = {"org", "person", "nationality"};

    private static void assignType(TaxonMatch m, Taxon node) {
        if (!m.isDefault()) {
            return;
        }
        String taxon_name = node.name.toLowerCase();
        for (String l : commonTaxonLabels) {
            if (taxon_name.startsWith(l)) {
                m.setType(l);
                break;
            }
        }
    }

    /**
     * @param id  doc id
     * @param buf input text
     * @return list of matches or Null
     * @throws ExtractionException on Solr Tagger error
     */
    @SuppressWarnings("unchecked")
    private List<TextMatch> extractorImpl(String id, String buf, Parameters params) throws ExtractionException {
        /*
         * Implementation notes:
         * "tags" are instances of the matching text spans from your input buffer
         * "matchingDocs" are records from the taxonomy catalog. They have all the
         * metadata.
         * tags' ids array are pointers into matchingDocs, by Solr record ID.
         * "tagsCount":10, "tags":[
         * { "ids":[35], "endOffset":40, "startOffset":38},
         * { "ids":[750308, 2769912, 2770041, 10413973, 10417546], "endOffset":49,
         * "startOffset":41},
         * "matchingDocs":{
         * "numFound":75, "start":0,
         * "docs":[
         * {records matching}]
         */

        String docid = (id != null ? id : NO_DOC_ID);
        List<TextMatch> matches = new ArrayList<>();
        if (params != null) {
            this.setAllowLowerCase(params.tag_lowercase);
        }

        Map<Object, Object> beanMap = new HashMap<>(100);
        QueryResponse response = tagTextCallSolrTagger(buf, docid, beanMap);
        /* Exit early if catalog or taxon filters yield no entries */
        if (beanMap.isEmpty()) {
            return matches;
        }

        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");
        log.debug("TAGS SIZE = {}", tags.size());

        TaxonMatch m;
        int tag_count = 0;

        for (NamedList<?> tag : tags) {
            ++tag_count;
            int x1 = (Integer) tag.get("startOffset");
            int x2 = (Integer) tag.get("endOffset");
            if (x2 - x1 < DEFAULT_MIN_LENGTH) {
                // Trivial length filter
                continue;
            }
            String matchtext = buf.substring(x1, x2);
            /*
            Major pre-filters:  Avoid tagging these situations, for performance and quality reasons:

              "Word    Word"        Valid match, but whitespace separation is abnormal
              "word word"           Valid match, but content is all lowercase.  Lower case matching is OFF by default
             */
            // matchText can be null.
            if (TextUtils.countFormattingSpace(matchtext) > 1) {
                // Phrases with words broken across more than one line are not valid matches.
                // Phrase with a single TAB is okay
                continue;
            }

            if (!tagLowerCase && TextUtils.isLower(matchtext)) {
                continue;
            }

            m = new TaxonMatch(x1, x2);
            m.match_id = String.format("taxon@%d", tag_count);

            /*
             * Set Text and immediately determine if there is some validity
             * to this match
             */
            m.setText(matchtext);
            m.setFilteredOut(ruleFilter.filterOut(m.getText()));

            List<?> taxonIDs = (List<?>) tag.get("ids");
            for (Object solrId : taxonIDs) {
                Object refData = beanMap.get(solrId);
                if (refData == null) {
                    continue;
                }

                /*
                 * Filter out non-Acronyms. e.g., 'who' is not a match for 'WHO'
                 */
                Taxon tx = (Taxon) refData;
                if (this.filterNonAcronyms) {
                    if (tx.isAcronym && !m.isUpper()) {
                        continue;
                    }
                }

                m.addTaxon(tx);
                assignType(m, tx);
            }

            // If the match has valid taxons add the match to the
            // accumulator for this document.
            //
            if (m.hasTaxons()) {
                matches.add(m);
            }
        }

        log.debug("FOUND LABELS count={}", matches.size());
        return matches;
    }


    /**
     * Tag the input
     *
     * @param input TextInput
     * @return array of TextMatch or Null
     * @throws ExtractionException the extraction exception
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        return extractorImpl(input.id, input.buffer, null);
    }

    public static List<Taxon> search(SolrClient index, String query) throws SolrServerException, IOException {
        ModifiableSolrParams qp = new ModifiableSolrParams();
        qp.set(CommonParams.FL, "id,catalog,taxnode,phrase,tag,name_type");
        qp.set(CommonParams.Q, query);
        return search(index, qp);
    }

    public static List<Taxon> search(SolrClient index, SolrParams qparams) throws SolrServerException, IOException {

        QueryResponse response = index.query(qparams, SolrRequest.METHOD.GET);

        List<Taxon> taxons = new ArrayList<>();
        SolrDocumentList docList = response.getResults();

        for (SolrDocument solrDoc : docList) {
            taxons.add(createTaxon(solrDoc));
        }

        return taxons;
    }

    /**
     * search the current taxonomic catalog.
     *
     * @param query Solr "q" parameter only
     * @return list of taxons
     * @throws SolrServerException on err
     * @throws IOException on err
     */
    public List<Taxon> search(String query) throws SolrServerException, IOException {
        return search(this.solr.getInternalSolrClient(), query);
    }

    /**
     * search the current taxonomic catalog.
     *
     * @param qparams Solr parameters in full.
     * @return list of taxons
     * @throws SolrServerException on err
     * @throws IOException on err
     */
    public List<Taxon> search(SolrParams qparams) throws SolrServerException, IOException {
        return search(this.solr.getInternalSolrClient(), qparams);
    }
}
