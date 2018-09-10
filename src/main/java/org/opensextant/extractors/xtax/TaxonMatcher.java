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

///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
//_____                                ____                     __                       __
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
//*  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//*/

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.opensextant.util.SolrProxy;
import org.opensextant.util.SolrUtil;
import org.opensextant.util.TextUtils;

/**
 * TaxonMatcher uses SolrTextTagger to tag mentions of phrases in documents. The
 * phrases can be from simple word lists or they can connect to a taxonomy of
 * sorts -- the "taxcat" solr core (see Xponents/solr/taxcat and Xponents/XTax
 * for implementation)
 *
 * JVM arg to use is "opensextant.solr" to point to the local path Less tested:
 * solr.solr.home might conflict with a Solr document server instead of this
 * tagger. solr.url is good for RESTful integration, but not recommended
 *
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class TaxonMatcher extends SolrMatcherSupport implements Extractor {

    private static ModifiableSolrParams params;

    static {
        params = new ModifiableSolrParams();
        // params.set(CommonParams.QT, requestHandler);
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
    private boolean filterNonAcronyms = true;
    // private ProgressMonitor progressMonitor;

    /**
     *
     * @throws IOException
     * @throws ConfigException
     */
    public TaxonMatcher() throws IOException, ConfigException {
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
     * Create a Taxon tag, which is filtered based on established catalog
     * filters.
     *
     * Caller must implement their domain objects, POJOs... this callback
     * handler only hashes them.
     *
     * @param refData solr doc
     * @return tag data
     */
    @Override
    public Object createTag(SolrDocument refData) {

        String _cat = SolrProxy.getString(refData, "catalog");

        // Filter out unused matching records.
        if (!tagAll && !this.catalogs.contains(_cat)) {
            return null;
        }
        if (!taxonExclusionFilter.isEmpty()) {
            String name = SolrUtil.getString(refData, "taxnode").toLowerCase();
            for (String t : taxonExclusionFilter) {
                if (name.startsWith(t)) {
                    return null;
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
     * @return
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
     * Catalogs is a list of catalogs caller wants to tag for. If set, only
     * taxon matches with the catalog ID in this list will be returned by
     * tagText()
     */
    public Set<String> catalogs = new HashSet<String>();

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

    private Set<String> taxonExclusionFilter = new HashSet<String>();

    private TaxonFilter ruleFilter = new TaxonFilter();

    /**
     * Add prefixes of types of taxons you do not want returned. e.g.,
     * "Place...." // exlclude will allow "Org" and "Person" taxons to pass on
     * thru
     * 
     * @param prefix
     */
    public void excludeTaxons(String prefix) {
        taxonExclusionFilter.add(prefix.toLowerCase());
    }

    /**
     * Light-weight usage: text in, matches out. Behaviors: ACRONYMS matching
     * lower case terms will automatically be omitted from results.
     *@return Null if nothing found, otherwise a list of TextMatch objects
     */
    @Override
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extractorImpl(null, input_buf);
    }

    /**
     * Implementation details -- use with or without the formal ID/buffer
     * pairing.
     *
     * @param id doc id
     * @param buf input text
     * @return list of matches or Null
     * @throws ExtractionException
     */
    private List<TextMatch> extractorImpl(String id, String buf) throws ExtractionException {
        String docid = (id != null ? id : NO_DOC_ID);

        Map<Object, Object> beanMap = new HashMap<Object, Object>(100);
        QueryResponse response = tagTextCallSolrTagger(buf, docid, beanMap);
        /* Exit early if catalog or taxon filters yield no entries */
        if (beanMap.isEmpty()) {
            return null;
        }

        List<TextMatch> matches = new ArrayList<TextMatch>();

        @SuppressWarnings("unchecked")
        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");

        log.debug("TAGS SIZE = {}", tags.size());

        /*
         * Retrieve all offsets into a long list.
         */
        TaxonMatch m = null;
        // int x1 = -1, x2 = -1;
        int tag_count = 0;
        String id_prefix = docid + "#";

        for (NamedList<?> tag : tags) {
            m = new TaxonMatch();
            m.start = ((Integer) tag.get("startOffset")).intValue();
            m.end = ((Integer) tag.get("endOffset")).intValue();// +1 char after
                                                                // last matched
                                                                // m.pattern_id
                                                                // = "taxtag";
            ++tag_count;
            m.match_id = id_prefix + tag_count;
            // m.setText((String) tag.get("matchText")); // Not reliable.
            // matchText can be null.
            if (TextUtils.countFormattingSpace(buf.substring(m.start, m.end)) > 1) {
                // Phrases with words broken across more than one line are not
                // valid matches.
                // Phrase with a single TAB is okay
                continue;
            }
            /*
             * Set Text and immediately determine if there is some validity 
             * to this match
             */
            m.setText(buf.substring(m.start, m.end));
            m.setFilteredOut(ruleFilter.filterOut(m.getText()));

            List<?> taxonIDs = tag.getAll("ids");
            //NamedList taxonIDs2 =  tag.get("ids");

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
     * "tags" are instances of the matching text spans from your input buffer
     * "matchingDocs" are records from the taxonomy catalog. They have all the
     * metadata.
     *
     * tags' ids array are pointers into matchingDocs, by Solr record ID.
     *
     * // "tagsCount":10, "tags":[{ "ids":[35], "endOffset":40,
     * "startOffset":38}, // { "ids":[750308, 2769912, 2770041, 10413973,
     * 10417546], "endOffset":49, // "startOffset":41}, // ... //
     * "matchingDocs":{"numFound":75, "start":0, "docs":[ // {records matching}]
     *
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        return extractorImpl(input.id, input.buffer);
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
     * @throws IOException
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
     * @throws IOException
     */
    public List<Taxon> search(SolrParams qparams) throws SolrServerException, IOException {
        return search(this.solr.getInternalSolrClient(), qparams);
    }
}
