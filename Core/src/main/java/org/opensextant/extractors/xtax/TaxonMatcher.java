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

import org.opensextant.util.SolrProxy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.SolrTaggerRequest;
import org.opensextant.extraction.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extraction.Extractor;
import org.opensextant.util.FileUtility;
import org.opensextant.extraction.ConfigException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 *
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class TaxonMatcher implements Extractor {

    private static String requestHandler = "/tag";
    private static ModifiableSolrParams params;
    //private final String fields = "";
    private static SolrProxy solr = null;
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private boolean debug = log.isDebugEnabled();
    private boolean tag_all = true;
    // private SolrTaggerRequest tag_request = null;

    /**
     *
     * @throws IOException
     */
    public TaxonMatcher() throws IOException {
        TaxonMatcher.initialize();

        // Instance variable that will have the transient payload to tag
        // this is not thread safe and is not static:

        // Pre-loading the Solr FST
        //
        try {
            extract(new TextInput("trivial priming of the solr pump", "__initialization___"));
        } catch (ExtractionException initErr) {
            throw new IOException("Unable to prime the tagger", initErr);
        }
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
            TaxonMatcher.initialize();
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

    protected static void initialize() throws IOException {

        if (solr != null) {
            return;
        }

        String config_solr_home = System.getProperty("solr.solr.home");
        solr = new SolrProxy(config_solr_home, "taxcat");

        params = new ModifiableSolrParams();
        params.set(CommonParams.QT, requestHandler);
        params.set(CommonParams.FL, "id,catalog,taxnode,phrase,tag");

        params.set("tagsLimit", 100000);
        params.set("subTags", false);
        params.set("matchText", false);//we've got the input doc as a string instead

        /* Possible overlaps: ALL, NO_SUB, LONGEST_DOMINANT_RIGHT
         * See Solr Text Tagger documentation for details.
         */
        params.set("overlaps", "NO_SUB");

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
        //reset();
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
     * Close solr resources.
     */
    public static void shutdown() {
        if (solr != null) {
            solr.close();
        }
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

        List<TextMatch> matches = new ArrayList<TextMatch>();

        SolrTaggerRequest tag_request = new SolrTaggerRequest(params, input.buffer);

        String docid = input.id;

        QueryResponse response = null;
        try {
            response = tag_request.process(solr.getInternalSolrServer());
        } catch (Exception err) {
            throw new ExtractionException("Failed to tag document", err);
        }

        // -- Process Solr Response

        SolrDocumentList docList = (SolrDocumentList) response.getResponse().get("matchingDocs");
        Map<Integer, Taxon> labelMap = new HashMap<Integer, Taxon>(docList.size());
        for (SolrDocument solrDoc : docList) {

            String _cat = SolrProxy.getString(solrDoc, "catalog");

            // Filter out unused matching records.
            if (!tag_all && !this.catalogs.contains(_cat)) {
                continue;
            }
            Taxon label = new Taxon();

            label.catalog = _cat;
            label.name = SolrProxy.getString(solrDoc, "taxnode");
            label.addTerm(SolrProxy.getString(solrDoc, "phrase"));
            label.addTags(solrDoc.getFieldValues("tag"));

            // Hashed on "id"
            Integer id = (Integer) solrDoc.getFirstValue("id");
            labelMap.put(id, label);
        }


        @SuppressWarnings("unchecked")
        List<NamedList<?>> tags = (List<NamedList<?>>) response.getResponse().get("tags");

        if (debug) {
            log.debug("TAGS SIZE = " + tags.size());
        }

        /**
         * Retrieve all offsets into a long list.
         */
        TaxonMatch m = null;
        int x1 = -1, x2 = -1;
        int tag_count = 0;
        String id_prefix = docid + "#";

        for (NamedList<?> tag : tags) {
            m = new TaxonMatch();
            x1 = (Integer) tag.get("startOffset");
            x2 = (Integer) tag.get("endOffset");//+1 char after last matched
            m.start = x1;
            m.end = x2;
            m.pattern_id = "taxtag";
            ++tag_count;
            m.match_id = id_prefix + tag_count;

            // Could have enabled the "matchText" option from the tagger to get
            // this, but since we already have the content as a String then
            // we might as well not make the tagger do any more work.
            m.setText(input.buffer.substring(x1, x2));

            @SuppressWarnings("unchecked")
            List<Integer> taxonIDs = (List<Integer>) tag.get("ids");

            for (Integer solrId : taxonIDs) {
                m.addTaxon(labelMap.get(solrId));
            }

            // If the match has valid taxons add the match to the
            // accumulator for this document.
            //
            if (m.hasTaxons()) {
                matches.add(m);
            }
        }

        if (debug) {
            log.debug("FOUND LABELS count=" + matches.size());
        }

        return matches;
    }

    public void testDoc(String buf) throws ExtractionException {
        List<TextMatch> matches = this.extract(new TextInput(buf, "test"));

        for (TextMatch tx : matches) {
            System.out.println(tx.toString());
        }
    }

    /**
     * Do a basic test
     */
    public static void main(String[] args) throws Exception {
        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("TaxTagger", args, "f:");

        int c = -1;
        String file = null;
        while ((c = opts.getopt()) != -1) {

            switch (c) {
                case 'f':
                    file = opts.getOptarg();
                    break;

                default:
                    System.out.println("Usage  -f filename ");
                    System.exit(-1);
            }

        }
        TaxonMatcher taxtag = new TaxonMatcher();

        try {
            //String doc = "Fruits of paradise are like pineapple, guava, passion fruit. "+
            //        " You may abandon the calories by eating fewer than one a day";

            String doc = FileUtility.readFile(file);

            // No filters.
            taxtag.testDoc(doc);

            // Invalid filter
            System.out.println("Testing invalid catalog");
            taxtag.addCatalogFilter("Boo");
            //taxtag.reset();
            taxtag.testDoc(doc);

            // Invalid filter + valid filter.
            System.out.println("Testing a valid catalog");
            taxtag.addCatalogFilter("CWMD");
            //taxtag.reset();
            taxtag.testDoc(doc);

            TaxonMatcher.shutdown();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
