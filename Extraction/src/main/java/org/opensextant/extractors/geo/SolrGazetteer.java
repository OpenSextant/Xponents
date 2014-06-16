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

import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.SolrProxy;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.opensextant.data.Place;
import org.opensextant.data.Country;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

/**
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 *
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class SolrGazetteer {

    //private final Logger log = LoggerFactory.getLogger(this.getClass());
    //private final boolean debug = log.isDebugEnabled();
    /**
     * In the interest of optimization we made the Solr instance a static class
     * attribute that should be thread safe and shareable across instances of
     * SolrMatcher
     */
    private ModifiableSolrParams params = new ModifiableSolrParams();
    private SolrProxy solr = null;
    private Map<String, Country> country_lookup = null;
    private Map<String, String> iso2fips = new HashMap<String, String>();
    private Map<String, String> fips2iso = new HashMap<String, String>();

    /**
     *
     * @throws IOException
     */
    public SolrGazetteer() throws IOException {
        initialize();
    }


    /** Returns the SolrProxy used internally. */
    public SolrProxy getSolrProxy() {
        return solr;
    }

    /**
     */
    public static String normalizeCountryName(String c) {
        return StringUtils.capitalize(c.toLowerCase());
    }


    private GeonamesUtility geodataUtil = null;
    
    /**
     */
    private void initialize() throws IOException {

        geodataUtil = new GeonamesUtility();
        
        //loadCountryNameMap();
        //loadFeatureMetaMap();

        String config_solr_home = System.getProperty("solr.solr.home");
        solr = new SolrProxy(config_solr_home, "gazetteer");

        params.set(CommonParams.Q, "*:*");
        params.set(CommonParams.FL,
                "id,name,cc,adm1,adm2,feat_class,feat_code,geo,place_id,name_bias,id_bias,name_type");
        try {
            loadCountries();
        } catch (SolrServerException loadErr) {
            throw new IOException(loadErr);
        }
    }

    
    /**
     * Close or release all resources.
     */
    public void shutdown() {
        if (solr != null) {
            solr.close();
        }
    }

    /**
     * List all country names, official and variant names.
     */
    public Map<String, Country> getCountries() {
        return country_lookup;
    }

    public static final Country UNK_Country = new Country("UNK", "invalid");

    /**
     * Get Country by the default ISO digraph returns the Unknown country if you
     * are not using an ISO2 code.
     *
     * TODO: throw a GazetteerException of some sort. for null query or invalid
     * code.
     */
    public Country getCountry(String isocode) {
        if (isocode == null) {
            return null;
        }
        if (country_lookup.containsKey(isocode)) {
            return country_lookup.get(isocode);
        }
        return UNK_Country;
    }

    /**
     *
     */
    public Country getCountryByFIPS(String fips) {
        String isocode = fips2iso.get(fips);
        return getCountry(isocode);
    }

    /**
     * This only returns Country objects that are names; It does not produce any
     * abbreviation variants.
     *
     * TODO: allow caller to get all entries, including abbreviations.
     */
    protected void loadCountries() throws SolrServerException {
        country_lookup = new HashMap<String, Country>();

        ModifiableSolrParams ctryparams = new ModifiableSolrParams();
        ctryparams.set(CommonParams.FL, "id,name,cc,FIPS_cc,ISO3_cc,adm1,adm2,feat_class,feat_code,geo,name_type");

        ctryparams.set("q", "+feat_class:A +feat_code:PCL* +name_type:N");
        ctryparams.set("rows", 2000);

        QueryResponse response = solr.getInternalSolrServer().query(ctryparams);

        // -- Process Solr Response;  This for loop matches the one in SolrMatcher
        //
        SolrDocumentList docList = response.getResults();
        for (SolrDocument gazEntry : docList) {
            String code = SolrProxy.getString(gazEntry, "cc");
            //String fips = SolrProxy.getString(solrDoc, "cc_fips");
            String name = SolrProxy.getString(gazEntry, "name");

            // NOTE: FIPS could be "*", where ISO2 column is always non-trivial. if ("*".equals(code)){code = fips; }

            Country C = country_lookup.get(code);
            if (C != null) {
                C.addAlias(name); // all other metadata is same.
                continue;
            }

            C = new Country(code, name);
            C.setName_type(SolrProxy.getChar(gazEntry, "name_type"));

            // Geo field is specifically Spatial4J lat,lon format.
            double[] xy = SolrProxy.getCoordinate(gazEntry, "geo");
            C.setLatitude(xy[0]);
            C.setLongitude(xy[1]);

            C.addAlias(C.getName()); // don't loose this entry as a likely variant.

            country_lookup.put(code, C);
        }

        /**
         * Finally choose default official names given the map of name:iso2
         */
        for (Country C : country_lookup.values()) {
            String n = geodataUtil.getDefaultCountryName(C.getCountryCode());
            if (n != null) {
                for (String alias : C.getAliases()) {
                    if (n.equalsIgnoreCase(alias)) {
                        C.setName(alias);
                    }
                }
            }
        }
    }

    /**
     * <pre>
     * Search the gazetteer using a phrase.
     * The phrase will be quoted internally as it searches Solr
     *
     *  e.g., search( "\"Boston City\"" )
     *
     * Solr Gazetteer uses OR as default joiner for clauses.  Without quotes
     * the above search would be "Boston" OR "City" effectively.
     *
     * </pre>
     *
     * @return places List of place entries
     * @throws MatcherException
     */
    public List<Place> search(String place_string) throws SolrServerException {
        return search(place_string, false);
    }

    /**
     * <pre>
     * Search the gazetteer using one of the following:
     *
     *   a name or keyword
     *   a Solr style fielded query, which by default includes bare keyword searches
     *
     *  search( "\"Boston City\"" )
     *
     * Solr Gazetteer uses OR as default joiner for clauses.
     *
     * </pre>
     *
     * @return places List of place entries
     * @throws MatcherException
     */
    public List<Place> search(String place, boolean as_solr) throws SolrServerException {

        List<Place> places = new ArrayList<Place>();
        if (as_solr) {
            params.set("q", place);
        } else {
            params.set("q", "\"" + place + "\""); // Bare keyword query needs to be quoted as "word word word"
        }

        QueryResponse response = solr.getInternalSolrServer().query(params);

        // -- Process Solr Response;  This for loop matches the one in SolrMatcher
        //
        SolrDocumentList docList = response.getResults();

        for (SolrDocument gazEntry : docList) {
            Place bean = SolrProxy.createPlace(gazEntry);

            places.add(bean);
        }

        return places;
    }

    /**
     * Do a basic test -- This main prog makes use of the default JVM arg for solr:  -Dsolr.solr.home = /path/to/solr
     */
    public static void main(String[] args) throws Exception {
        //String solrHome = args[0];
        /*
        String OPENSEXTANT_HOME = System.getProperty("opensextant.home");
        String SOLR_HOME = OPENSEXTANT_HOME + File.separator + ".." + File.separator + "opensextant-solr";
        System.setProperty("solr.solr.home", SOLR_HOME);
         */

        SolrGazetteer gaz = new SolrGazetteer();
        GeonamesUtility geodataUtil = new GeonamesUtility();

        try {

            // Try to get countries
            Map<String, Country> countries = gaz.getCountries();
            for (Country c : countries.values()) {
                System.out.println(c.getKey() + " = " + c.name + "\t  Aliases: " + c.getAliases().toString());
            }

            List<Place> matches = gaz.search("+Boston +City");

            for (Place pc : matches) {
                System.out.println(pc.toString() + " which is categorized as: "
                        + geodataUtil.getFeatureName(pc.getFeatureClass(), pc.getFeatureCode()));
            }

        } catch (Exception err) {
            err.printStackTrace();
        }
        gaz.shutdown();
        System.exit(0);

    }
}
