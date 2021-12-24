/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
package org.opensextant.util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.opensextant.ConfigException;
import org.opensextant.data.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * As Xponents is a multi-core instance of Solr, a single default solr home and
 * default solr core does not make sense. Each wrapper around Solr (via
 * SolrProxy) needs to name the solr home and an explicit core name.
 *
 * @author ubaldino
 */
public class SolrProxy extends SolrUtil {

    /**
     * A single method to help find a suitable value for SOLR HOME
     * If given is null, then system variables are checked.
     *
     * @param given solr home.
     */
    public static String deriveSolrHome(String given) throws ConfigException {
        if (given != null) {
            return given;
        }

        String solrHome = System.getProperty("opensextant.solr");
        if (solrHome != null) {
            return solrHome;
        }
        solrHome = System.getProperty("solr.solr.home");

        if (solrHome != null) {
            return solrHome;
        }

        solrHome = System.getProperty("solr.url");
        if (solrHome != null) {
            return solrHome;
        }

        throw new ConfigException(
                "A non-null value for SOLR HOME is required; Either pass to constructor, set opensextant.solr, set solr.solr.home, or solr.url");

    }

    protected String solrHome = null;
    protected String coreName = null;

    /**
     * Initializes a Solr server from a URL
     *
     * @throws IOException err
     */
    public SolrProxy(URL url) throws IOException {
        this.server_url = url;
        solrClient = initializeHTTP(this.server_url);
    }

    /**
     * Initializes a Solr server from the SOLR_HOME environment variable.
     *
     * @param core name of solr core
     * @throws ConfigException cfg err
     */
    public SolrProxy(String core) throws ConfigException {
        this.server_url = null;
        this.solrHome = deriveSolrHome(null);
        this.coreName = core;
        solrClient = setupCore(solrHome, core);
    }

    /**
     * Initializes a Solr server from the SOLR_HOME environment variable.
     *
     * @param solr_home the solr_home
     * @param core      name of solr core
     * @throws ConfigException cfg err
     */
    public SolrProxy(String solr_home, String core) throws ConfigException {
        this.server_url = null;
        solrHome = solr_home;
        this.coreName = core;
        solrClient = setupCore(solrHome, core);
    }

    protected Logger logger = LoggerFactory.getLogger(SolrProxy.class);
    protected SolrClient solrClient;
    private UpdateRequest solrUpdate = null;
    protected URL server_url;
    private boolean writable = false;

    public void setWritable(boolean b) {
        writable = b;
    }

    /**
     * Is Solr server instance allowed to write to index?
     *
     * @return true if index is intended to be writable.
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Get an HTTP server for Solr.
     *
     * @param url server represented by URL
     * @return Instance of a Solr server
     */
    public static SolrClient initializeHTTP(URL url) {

        HttpSolrClient client = new HttpSolrClient.Builder(url.toString()).allowCompression(true).build();
        return client;

    }

    /**
     * Creates an EmbeddedSolrServer given solr home &amp; the core to use.
     * These may be null and you get the default.
     *
     * @param _solrHome solr home
     * @param _coreName name of core
     * @return the embedded solr server
     * @throws ConfigException on err
     */
    public static EmbeddedSolrServer setupCore(String _solrHome, String _coreName) throws ConfigException {

        try {
            CoreContainer solrContainer;

            if (_solrHome == null) {
                solrContainer = new CoreContainer();
            } else {
                solrContainer = new CoreContainer(_solrHome);
            }
            solrContainer.load();// since Solr 4.4

            return new EmbeddedSolrServer(solrContainer, _coreName);

        } catch (Exception err) {
            throw new ConfigException("Failed to set up Embedded Solr at " + _solrHome + " CORE:" + _coreName, err);
        }
    }

    /**
     * Search an OpenSextant solr gazetteer.
     *
     * @param index   solr server handle
     * @param qparams search parameters
     * @return list of places
     * @throws SolrServerException on err
     * @throws IOException
     */
    public static List<Place> searchGazetteer(SolrClient index, SolrParams qparams)
            throws SolrServerException, IOException {

        QueryResponse response = index.query(qparams, SolrRequest.METHOD.GET);

        List<Place> places = new ArrayList<>();
        SolrDocumentList docList = response.getResults();

        for (SolrDocument solrDoc : docList) {
            places.add(SolrUtil.createPlace(solrDoc));
        }

        return places;
    }

    /**
     * Add one solr record.
     *
     * @param solrRecord document/gazetteer or other entry to add to index
     * @throws Exception on err ???
     */
    public void add(SolrInputDocument solrRecord) throws Exception {

        if (!this.writable) {
            throw new Exception("This instance is not configured for writing to index");
        }

        // Initialize per batch if nec.y
        if (solrUpdate == null) {
            solrUpdate = new UpdateRequest();
        }

        // Initialize per record
        // .. add data to record
        // .. add record to batch request
        solrUpdate.add(solrRecord);
    }

    /**
     * Add many solr records.
     *
     * @param solrRecords array of records to add
     * @throws Exception on err
     */
    public void add(java.util.Collection<SolrInputDocument> solrRecords) throws Exception {

        if (!this.writable) {
            throw new Exception("This instance is not configured for writing to index");
        }

        // Initialize per batch if nec.y
        if (solrUpdate == null) {
            solrUpdate = new UpdateRequest();
        }

        // Initialize per record
        // .. add data to record
        // .. add record to batch request
        solrUpdate.add(solrRecords);
    }

    /**
     * Reopen an existing solr proxy.
     *
     * @throws ConfigException the config exception
     * @throws IOException     Signals that an I/O exception has occurred.
     */
    public void openIndex() throws ConfigException, IOException {
        if (solrClient == null) {
            if (server_url != null) {
                solrClient = initializeHTTP(server_url);
            } else {
                solrClient = setupCore(solrHome, coreName);
            }
        }
    }

    /**
     * Optimizes the Solr server.
     *
     * @throws IOException         on err
     * @throws SolrServerException the solr server exception
     */
    public void optimize() throws IOException, SolrServerException {
        solrClient.optimize(true, false); // Don't wait'
    }

    /**
     * Invokes <code>saveIndex(false)</code>
     */
    public void saveIndex() {
        saveIndex(false);
    }

    /**
     * Save and optionally records to server or index On failure, current
     * accumulating request is cleared and nullified to avoid retransmitting bad
     * data.
     * In the event of a failure all records since last "saveIndex" would be
     * lost and should be resubmitted.
     *
     * @param commit true, if we should commit updates
     */
    public void saveIndex(boolean commit) {
        if (solrUpdate == null) {
            return;
        }

        logger.info("Saving records to index");
        try {
            solrClient.request(solrUpdate);
            if (commit) {
                solrClient.commit();
            }
            solrUpdate.clear();
            solrUpdate = null;
        } catch (Exception filex) {
            logger.error("Index failed during indexing", filex);
            solrUpdate.clear();
            solrUpdate = null;
        }
    }

    /**
     * Save and reopen.
     *
     * @throws ConfigException the config exception
     * @throws IOException     on err
     */
    public void saveAndReopen() throws ConfigException, IOException {
        saveIndex(/* commit = */true);
        openIndex();
    }

    /**
     * @throws IOException
     */
    public void close() throws IOException {
        if (isWritable()) {
            saveIndex();
        }

        if (solrClient != null) {
            solrClient.close();
            solrClient = null;
        }
    }

    /**
     * @deprecated using SolrClient terminology now.
     * @return
     */
    @Deprecated
    public SolrClient getInternalSolrServer() {
        return solrClient;
    }

    public SolrClient getInternalSolrClient() {
        return solrClient;
    }

}
