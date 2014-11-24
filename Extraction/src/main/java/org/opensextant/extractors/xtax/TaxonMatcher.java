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
package org.opensextant.extractors.xtax;

import org.opensextant.ConfigException;
import org.opensextant.processing.progress.ProgressMonitor;
import org.opensextant.util.SolrProxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.SolrMatcherSupport;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extraction.Extractor;

/**
 * TaxonMatcher uses SolrTextTagger to tag mentions of phrases in documents.
 * The phrases can be from simple word lists or they can connect to a taxonomy of sorts -- 
 * the "taxcat" solr core (see Xponents/solr/taxcat and Xponents/XTax for implementation) 
 * 
 * Set either JVM arg solr.solr.home, solr.url (for server) to point to Solr index 
 * 
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class TaxonMatcher extends SolrMatcherSupport implements Extractor {

    private static ModifiableSolrParams params;
    static {
        params = new ModifiableSolrParams();
        //params.set(CommonParams.QT, requestHandler);
        params.set(CommonParams.FL, "id,catalog,taxnode,phrase,tag");

        params.set("tagsLimit", 100000);
        params.set("subTags", false);
        params.set("matchText", true);

        /*
         * Possible overlaps: ALL, NO_SUB, LONGEST_DOMINANT_RIGHT See Solr Text
         * Tagger documentation for details.
         */
        params.set("overlaps", "NO_SUB");

    }
    private boolean tag_all = true;
    private ProgressMonitor progressMonitor;

    /**
     * 
     * @throws IOException
     */
    public TaxonMatcher() throws IOException {
        // TaxonMatcher.initialize();

        // Instance variable that will have the transient payload to tag
        // this is not thread safe and is not static:

        // Pre-loading the Solr FST
        //
    }

    /**
     * Extractor interface.
     */
    public void cleanup(){
        this.shutdown();        
    }
    
    
    /**
     * Be explicit about the solr core to use for tagging
     */
    public String getCoreName() {
        return "taxcat";
    }

    /**
     * Return the Solr Parameters for the tagger op.
     * 
     * @return
     */
    public SolrParams getMatcherParameters() {
        return params;
    }

    /**
     * Caller must implement their domain objects, POJOs... this callback
     * handler only hashes them.
     * 
     * @param doc
     * @return
     */
    public Object createTag(SolrDocument refData) {

        String _cat = SolrProxy.getString(refData, "catalog");

        // Filter out unused matching records.
        if (!tag_all && !this.catalogs.contains(_cat)) {
            return null;
        }
        Taxon label = new Taxon();

        label.catalog = _cat;
        label.name = SolrProxy.getString(refData, "taxnode");
        label.addTerm(SolrProxy.getString(refData, "phrase"));
        label.addTags(refData.getFieldValues("tag"));
        return label;
    }

    /**
     * Extractor interface: getName
     * 
     * @return
     */
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
     * @param patfile
     *            configuration file path
     */
    @Override
    public void configure(String patfile) throws ConfigException {
        throw new ConfigException("Not a valid configuration routine");
    }

    /**
     * Configure an Extractor using a config file named by a URL
     * 
     * @param patfile
     *            configuration URL
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
        tag_all = false;
        // reset();
    }

    public void addCatalogFilter(String cat) {
        catalogs.add(cat);
        tag_all = false;
    }

    public void removeFilters() {
        catalogs.clear();
        tag_all = true;
    }
    
    /**
     * Light-weight usage: text in, matches out.
     */
    public List<TextMatch> extract(String input_buf) throws ExtractionException {
        return extractorImpl(null, input_buf);
    }
        
    /**
     * Implementation details -- use with or without the formal ID/buffer pairing.
     * 
     * @param id
     * @param buf
     * @return
     * @throws ExtractionException
     */
    private List<TextMatch> extractorImpl(String id, String buf) throws ExtractionException {
        List<TextMatch> matches = new ArrayList<TextMatch>();
        String docid = (id !=null ? id : NO_DOC_ID );

        Map<Integer, Object> beanMap = new HashMap<Integer, Object>(100);
        QueryResponse response = tagTextCallSolrTagger(buf, docid, beanMap);

        @SuppressWarnings("unchecked")
        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");

        log.debug("TAGS SIZE = {}", tags.size());

        /*
         * Retrieve all offsets into a long list.
         */
        TaxonMatch m = null;
        //int x1 = -1, x2 = -1;
        int tag_count = 0;
        String id_prefix = docid + "#";

        for (NamedList<?> tag : tags) {
            m = new TaxonMatch();
            m.start = (Integer) tag.get("startOffset");
            m.end = (Integer) tag.get("endOffset");// +1 char after last matched
            m.pattern_id = "taxtag";
            ++tag_count;
            m.match_id = id_prefix + tag_count;

            // Could have enabled the "matchText" option from the tagger to get
            // this, but since we already have the content as a String then
            // we might as well not make the tagger do any more work.
            m.setText(buf.substring(m.start, m.end));

            @SuppressWarnings("unchecked")
            List<Integer> taxonIDs = (List<Integer>) tag.get("ids");

            for (Integer solrId : taxonIDs) {
                Object refData = beanMap.get(solrId);
                if (refData != null)
                    m.addTaxon((Taxon) refData);
            }

            // If the match has valid taxons add the match to the
            // accumulator for this document.
            //
            if (m.hasTaxons()) {
                matches.add(m);
            }
        }

        log.debug("FOUND LABELS count={}", matches.size());

        markComplete();

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

    @Override
    public void setProgressMonitor(ProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    @Override
    public void updateProgress(double progress) {
        if (this.progressMonitor != null)
            progressMonitor.updateStepProgress(progress);
    }

    @Override
    public void markComplete() {
        if (this.progressMonitor != null)
            progressMonitor.completeStep();
    }

}
