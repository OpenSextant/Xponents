///~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
//  ______                                ____                     __                       __
// \ \  __`\                             /\  _`\                  /\ \__                   /\ \__
// \ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
//  \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//   \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//    \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//     \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//               \ \_\
//                \/_/
//
// OpenSextant (Xponents)
// Copyright MITRE 2013
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//

package org.opensextant.extraction;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.opensextant.ConfigException;
import org.opensextant.util.SolrProxy;
import org.opensextant.util.SolrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 * <p >
 *
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public abstract class SolrMatcherSupport implements Closeable {

    protected Logger log = LoggerFactory.getLogger(getClass());

    protected String requestHandler = "/tag";
    protected SolrProxy solr = null;
    protected int tagNamesTime = 0;
    protected int getNamesTime = 0;
    protected int totalTime = 0;

    /**
     * Use this if you intend to set a non-default tagger path. E.g., /tag1
     * /tag-lang1 etc.
     *
     * @param nonDefault path of tagger.
     */
    public void setTaggerHandler(String nonDefault) {
        requestHandler = nonDefault;
    }

    /**
     * Close solr resources.
     */
    @Override
    public void close() {
        if (solr != null) {
            try {
                solr.close();
            } catch (IOException err) {
                this.log.error("Rare failure closing Solr I/O");
            }
        }
    }

    /**
     * Be explicit about the solr core to use for tagging.
     *
     * @return the core name
     */
    public abstract String getCoreName();

    /**
     * Return the Solr Parameters for the tagger op.
     *
     * @return SolrParams
     */
    public abstract SolrParams getMatcherParameters();

    /**
     * Caller must implement their domain objects, POJOs... this callback
     * handler only hashes them.
     *
     * @param doc record to convert to Place record
     * @return object representing a Place
     */
    public abstract Object createTag(SolrDocument doc);

    /**
     * Initialize. This capability is not supporting taggers/matchers using HTTP
     * server. For now, it is intedended to be in-memory, local embedded solr
     * server.
     *
     * @throws ConfigException if solr server cannot be established from local
     *                         index or from http server
     */
    public void initialize() throws ConfigException {
        solr = new SolrProxy(getCoreName());
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
     * @return time to get reference records.
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
     * Solr call: tag input buffer, returning all candiate reference data that
     * matched during tagging.
     *
     * @param buffer     text to tag
     * @param docid      id for text, only for tracking purposes
     * @param refDataMap - a map of reference data in solr, It will store
     *                   caller's domain objects. e.g., rec.id =&gt; domain(rec)
     * @return solr response
     * @throws ExtractionException tagger error
     */
    protected QueryResponse tagTextCallSolrTagger(String buffer, String docid, final Map<Object, Object> refDataMap)
            throws ExtractionException {
        SolrTaggerRequest tagRequest = new SolrTaggerRequest(getMatcherParameters(), buffer);
        tagRequest.setPath(requestHandler);
        // Stream the response to avoid serialization and to save memory by
        // only keeping one SolrDocument materialized at a time
        tagRequest.setStreamingResponseCallback(new StreamingResponseCallback() {
            @Override
            public void streamDocListInfo(long numFound, long start, Float maxScore) {
                // implementation not needed
            }

            // Future optimization: it would be nice if Solr could give us the
            // doc id without giving us a SolrDocument, allowing us to
            // conditionally get it. It would save disk IO & speed, at the
            // expense of putting ids into memory.
            @Override
            public void streamSolrDocument(final SolrDocument solrDoc) {
                String id = SolrUtil.getString(solrDoc, "id");
                // create a domain object for the given tag;
                // this callback handler caches such domain obj in simple k/v map.
                Object domainObj = createTag(solrDoc);
                if (domainObj != null) {
                    refDataMap.put(id, domainObj);
                }
            }
        });

        QueryResponse response;
        try {
            response = tagRequest.process(solr.getInternalSolrClient());
        } catch (Exception err) {
            throw new ExtractionException("Failed to tag document=" + docid, err);
        }

        // see https://issues.apache.org/jira/browse/SOLR-5154
        SolrDocumentList docList = response.getResults();
        if (docList != null) {
            StreamingResponseCallback callback = tagRequest.getStreamingResponseCallback();
            callback.streamDocListInfo(docList.getNumFound(), docList.getStart(), docList.getMaxScore());
            for (SolrDocument solrDoc : docList) {
                callback.streamSolrDocument(solrDoc);
            }
        }

        return response;
    }
}
