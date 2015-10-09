/**
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
package org.opensextant.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
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
 * As Xponents is a multi-core instance of Solr, a single default solr home and default solr core
 * does not make sense.  Each wrapper around Solr (via SolrProxy) needs to name the solr home and an explicit core name.
 *
 * @author ubaldino
 */
public class SolrProxy extends SolrUtil {

    /**A single method to help find a suitable value for SOLR HOME
     *
     * If given is null, then system variables are checked.
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
        solrServer = initializeHTTP(this.server_url);
    }

    /**
     * Initializes a Solr server from the SOLR_HOME environment variable.
     *
     * @param core  name of solr core
     * @throws ConfigException cfg err
     */
    public SolrProxy(String core) throws ConfigException {
        this.server_url = null;
        this.solrHome = deriveSolrHome(null);
        this.coreName = core;
        solrServer = setupCore(solrHome, core);
    }

    /**
     * Initializes a Solr server from the SOLR_HOME environment variable.
     *
     * @param solr_home the solr_home
     * @param core  name of solr core
     * @throws ConfigException cfg err
     */
    public SolrProxy(String solr_home, String core) throws ConfigException {
        this.server_url = null;
        solrHome = solr_home;
        this.coreName = core;
        solrServer = setupCore(solrHome, core);
    }

    protected Logger logger = LoggerFactory.getLogger(SolrProxy.class);
    protected SolrServer solrServer = null;
    private UpdateRequest solrUpdate = null;
    protected URL server_url = null;
    private boolean writable = false;

    public void setWritable(boolean b) {
        writable = b;
    }

    /**
     *
     * Is Solr server instance allowed to write to index?
     * @return true if index is intended to be writable.
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Get an HTTP server for Solr.
     *
     * @param url
     * @return Instance of a Solr server
     * @throws MalformedURLException
     */
    public static SolrServer initializeHTTP(URL url) throws MalformedURLException {

        HttpSolrServer server = new HttpSolrServer(url.toString());
        server.setAllowCompression(true);

        return server;

    }

    /**
     * Creates an EmbeddedSolrServer given solr home &amp; the core to use.
     * These may be null and you get the default.
     *
     * @param _solrHome  solr home
     * @param _coreName  name of core
     * @return the embedded solr server
     * @throws ConfigException on err
     */
    public static EmbeddedSolrServer setupCore(String _solrHome, String _coreName)
            throws ConfigException {

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
            throw new ConfigException("Failed to set up Embedded Solr at " + _solrHome + " CORE:"
                    + _coreName, err);
        }
    }

    /**
     * Creates the bare minimum Gazetteer Place record
     * @param gazEntry a solr document of key/value pairs
     * @return Place obj
     */
    public static Place createPlace(SolrDocument gazEntry) {

        Place bean = new Place(SolrUtil.getString(gazEntry, "place_id"), SolrProxy.getString(
                gazEntry, "name"));

        String nt = SolrUtil.getString(gazEntry, "name_type");
        if (nt != null) {
            if ("code".equals(nt)) {
                bean.setName_type('A');
            } else {
                bean.setName_type(nt.charAt(0));
            }
        }

        bean.setCountryCode(SolrUtil.getString(gazEntry, "cc"));

        // Other metadata.
        bean.setAdmin1(SolrUtil.getString(gazEntry, "adm1"));
        bean.setAdmin2(SolrUtil.getString(gazEntry, "adm2"));
        bean.setFeatureClass(SolrUtil.getString(gazEntry, "feat_class"));
        bean.setFeatureCode(SolrUtil.getString(gazEntry, "feat_code"));

        // Geo field is specifically Spatial4J lat,lon format.
        // Value should have already been validated as it was stored in index
        double[] xy = SolrUtil.getCoordinate(gazEntry, "geo");
        bean.setLatitude(xy[0]);
        bean.setLongitude(xy[1]);

        bean.setName_bias(SolrUtil.getDouble(gazEntry, "name_bias"));
        bean.setId_bias(SolrUtil.getDouble(gazEntry, "id_bias"));

        return bean;
    }

    /**
     * Search an OpenSextant solr gazetteer.
     *
     * @param index solr server handle
     * @param qparams search parameters
     * @return list of places
     * @throws SolrServerException on err
     */
    public static List<Place> searchGazetteer(SolrServer index, SolrParams qparams)
            throws SolrServerException {

        QueryResponse response = index.query(qparams, SolrRequest.METHOD.GET);

        List<Place> places = new ArrayList<>();
        SolrDocumentList docList = response.getResults();

        for (SolrDocument solrDoc : docList) {
            places.add(SolrProxy.createPlace(solrDoc));
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
     *  Reopen an existing solr proxy.
     *
     * @throws ConfigException the config exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void openIndex() throws ConfigException, IOException {
        if (solrServer == null) {
            if (server_url != null) {
                solrServer = initializeHTTP(server_url);
            } else {
                solrServer = setupCore(solrHome, coreName);
            }
        }
    }

    /**
     * Optimizes the Solr server.
     *
     * @throws IOException on err
     * @throws SolrServerException the solr server exception
     */
    public void optimize() throws IOException, SolrServerException {
        solrServer.optimize(true, false); // Don't wait'
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
     * 
     * In the event of a failure all records since last "saveIndex" would be
     * lost and should be resubmitted.
     *
     * @param commit  true, if we should commit updates
     */
    public void saveIndex(boolean commit) {
        if (solrUpdate == null) {
            return;
        }

        logger.info("Saving records to index");
        try {
            solrServer.request(solrUpdate);
            if (commit) {
                solrServer.commit();
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
     * @throws IOException on err
     */
    public void saveAndReopen() throws ConfigException, IOException {
        saveIndex(/* commit = */true);
        openIndex();
    }

    /**
     *
     */
    public void close() {
        if (isWritable()) {
            saveIndex();
        }

        if (solrServer != null) {
            solrServer.shutdown();
            solrServer = null;
        }
    }

    public SolrServer getInternalSolrServer() {
        return solrServer;
    }

}
