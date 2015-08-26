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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.opensextant.ConfigException;
import org.opensextant.data.Country;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.SolrProxy;

/**
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 *
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class SolrGazetteer {

    /**
     * In the interest of optimization we made the Solr instance a static class
     * attribute that should be thread safe and shareable across instances of
     * SolrMatcher
     */
    private ModifiableSolrParams params = new ModifiableSolrParams();
    private SolrProxy solr = null;

    /**
     * fast lookup by ISO2 country code.
     */
    private Map<String, Country> countryCodes = null;

    /**
     * Default country code in solr gazetteer is ISO, so if given a FIPS code, we need
     * a helpful lookup to get ISO code for lookup.
     */
    private Map<String, String> countryFIPS_ISO = new HashMap<String, String>();

    /**
     * Geodetic search parameters.
     */
    private ModifiableSolrParams geoLookup = createGeodeticLookupParams();

    /**
     * Instantiates a new solr gazetteer.
     *
     * @throws ConfigException Signals that a configuration exception has occurred.
     */
    public SolrGazetteer() throws ConfigException {
        this((String) null);
    }

    /**
     * Instantiates a new solr gazetteer with the specified Solr Home location.
     *
     * @param solrHome the location of solrHome.
     * @throws ConfigException Signals that a configuration exception has occurred.
     */
    public SolrGazetteer(String solrHome) throws ConfigException {
        initialize(solrHome);
    }

    public SolrGazetteer(SolrProxy currentIndex) throws ConfigException {
        //initialize();
        solr = currentIndex;

        try {
            this.countryCodes = loadCountries(solr.getInternalSolrServer());
        } catch (SolrServerException loadErr) {
            throw new ConfigException(
                    "SolrGazetteer is unable to load countries due to Solr error", loadErr);
        } catch (IOException ioErr) {
            throw new ConfigException(
                    "SolrGazetteer is unable to load countries due to IO/file error", ioErr);
        }

    }

    /**
     *  Returns the SolrProxy used internally.
     *
     * @return the solr proxy
     */
    public SolrProxy getSolrProxy() {
        return solr;
    }

    /**
     * Normalize country name.
     *
     * @param c the c
     * @return the string
     */
    public static String normalizeCountryName(String c) {
        return StringUtils.capitalize(c.toLowerCase());
    }

    /**
     * 
     * @return
     * @deprecated DO NOT USE.  Keeping this as a reminder of what not to do.
     * This will load entire index into memory.
     */
    @Deprecated
    private static ModifiableSolrParams createGeodeticLookupParamsXX() {
        /* Basic parameters for geospatial lookup.
         * These are reused, and only pt and d are set for each lookup.
         *
         */
        ModifiableSolrParams p = new ModifiableSolrParams();
        p.set(CommonParams.FL, "id,name,cc,adm1,adm2,feat_class,feat_code,"
                + "geo,place_id,name_bias,id_bias,name_type");
        p.set(CommonParams.ROWS, 25);
        p.set(CommonParams.Q, "*:*");
        p.set(CommonParams.FQ, "{!geofilt}");
        p.set("spatial", true);
        p.set("sfield", "geo");
        p.set(CommonParams.SORT, "geodist() asc"); // Find closest places first.
        return p;
    }

    /**
     * Creates a generic spatial query for up to first 25 rows.
     * @return
     */
    protected static ModifiableSolrParams createGeodeticLookupParams() {
        return createGeodeticLookupParams(25);
    }

    /**
     * For larger areas choose a higher number of Rows to return.
     * If you choose to use  Solr spatial score-by-distance for sorting or anything, then
     * Solr appears to want to load entire index into memory.  So this sort mechanism is off by default.
     * 
     * @param rows
     * @return
     */
    protected static ModifiableSolrParams createGeodeticLookupParams(int rows) {
        /* Basic parameters for geospatial lookup.
         * These are reused, and only pt and d are set for each lookup.
         *
         */
        ModifiableSolrParams p = new ModifiableSolrParams();
        p.set(CommonParams.FL, "id,name,cc,adm1,adm2,feat_class,feat_code,"
                + "geo,place_id,name_bias,id_bias,name_type");
        p.set(CommonParams.ROWS, rows);
        p.set(CommonParams.Q, "{!geofilt sfield=geo}");
        //p.set(CommonParams.SORT, "score desc");
        p.set("spatial", "true");

        return p;
    }

    /**
     * Initialize.
     * Cascading env variables:  First use value from constructor,
     * then opensextant.solr, then solr.solr.home
     *
     * @throws ConfigException Signals that a configuration exception has occurred.
     */
    private void initialize(String solrHome) throws ConfigException {

        solr = solrHome != null ? new SolrProxy(solrHome, "gazetteer") : new SolrProxy("gazetteer");

        params.set(CommonParams.Q, "*:*");
        params.set(CommonParams.FL,
                "id,name,cc,adm1,adm2,feat_class,feat_code,geo,place_id,name_bias,id_bias,name_type");
        try {
            this.countryCodes = loadCountries(solr.getInternalSolrServer());
        } catch (SolrServerException loadErr) {
            throw new ConfigException(
                    "SolrGazetteer is unable to load countries due to Solr error", loadErr);
        } catch (IOException ioErr) {
            throw new ConfigException(
                    "SolrGazetteer is unable to load countries due to IO/file error", ioErr);
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
     *
     * @return the countries
     */
    public Map<String, Country> getCountries() {
        return countryCodes;
    }

    /** The Constant UNK_Country. */
    public static final Country UNK_Country = new Country("UNK", "invalid");

    /**
     * Get Country by the default ISO digraph returns the Unknown country if you
     * are not using an ISO2 code.
     *
     * TODO: throw a GazetteerException of some sort. for null query or invalid
     * code.
     *
     * @param isocode the isocode
     * @return the country
     */
    public Country getCountry(String isocode) {
        if (isocode == null) {
            return null;
        }
        if (countryCodes.containsKey(isocode)) {
            return countryCodes.get(isocode);
        }
        return UNK_Country;
    }

    /**
     * Gets the country by fips.
     *
     * @param fips the fips
     * @return the country by fips
     */
    public Country getCountryByFIPS(String fips) {
        String isocode = countryFIPS_ISO.get(fips);
        return getCountry(isocode);
    }

    /**
     * This only returns Country objects that are names; It does not produce any
     * abbreviation variants.
     *
     * TODO: allow caller to get all entries, including abbreviations.
     *
     * @throws SolrServerException the solr server exception
     * @throws IOException
     */
    public static HashMap<String, Country> loadCountries(SolrServer index)
            throws SolrServerException, IOException {
        HashMap<String, Country> countryCodeMap = new HashMap<String, Country>();

        ModifiableSolrParams ctryparams = new ModifiableSolrParams();
        ctryparams.set(CommonParams.FL,
                "id,name,cc,FIPS_cc,ISO3_cc,adm1,adm2,feat_class,feat_code,geo,name_type");

        ctryparams.set("q", "+feat_class:A +feat_code:PCL* +name_type:N");
        ctryparams.set("rows", 2000);

        QueryResponse response = index.query(ctryparams);

        // -- Process Solr Response;  This for loop matches the one in SolrMatcher
        //
        SolrDocumentList docList = response.getResults();
        for (SolrDocument gazEntry : docList) {
            String code = SolrProxy.getString(gazEntry, "cc");
            String fips = SolrProxy.getString(gazEntry, "FIPS_cc");
            String iso3 = SolrProxy.getString(gazEntry, "ISO3_cc");
            String name = SolrProxy.getString(gazEntry, "name");

            // NOTE: FIPS could be "*", where ISO2 column is always non-trivial. if ("*".equals(code)){code = fips; }

            Country C = countryCodeMap.get(code);
            if (C != null) {
                C.addAlias(name); // all other metadata is same.
                continue;
            }

            C = new Country(code, name);
            C.setName_type(SolrProxy.getChar(gazEntry, "name_type"));
            C.CC_FIPS = fips;
            C.CC_ISO3 = iso3;

            // Geo field is specifically Spatial4J lat,lon format.
            double[] xy = SolrProxy.getCoordinate(gazEntry, "geo");
            C.setLatitude(xy[0]);
            C.setLongitude(xy[1]);

            C.addAlias(C.getName()); // don't loose this entry as a likely variant.

            countryCodeMap.put(code, C);
        }

        GeonamesUtility geodataUtil = new GeonamesUtility();

        /**
         * Finally choose default official names given the map of name:iso2
         */
        for (Country C : countryCodeMap.values()) {
            String n = geodataUtil.getDefaultCountryName(C.getCountryCode());
            if (n != null) {
                for (String alias : C.getAliases()) {
                    if (n.equalsIgnoreCase(alias)) {
                        C.setName(alias);
                    }
                }
            }
        }
        return countryCodeMap;
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
     * @param place_string the place_string
     * @return places List of place entries
     * @throws SolrServerException the solr server exception
     */
    public List<Place> search(String place_string) throws SolrServerException {
        return search(place_string, false);
    }

    /**
     * Instance method that reuses a set of SolrParams for optimized search.
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
     * @param place the place
     * @param as_solr the as_solr
     * @return places List of place entries
     * @throws SolrServerException the solr server exception
     */
    public List<Place> search(String place, boolean as_solr) throws SolrServerException {

        if (as_solr) {
            params.set("q", place);
        } else {
            params.set("q", "\"" + place + "\""); // Bare keyword query needs to be quoted as "word word word"
        }

        return SolrProxy.searchGazetteer(solr.getInternalSolrServer(), params);
    }

    /**
     * Find places located at a particular location.  UNSORTED!
     *
     * @param yx
     * @param d  positive distance radius is required.
     * @return
     * @throws SolrServerException
     */
    public List<Place> placesAt(LatLon yx, int withinKM) throws SolrServerException {

        /*          */
        geoLookup.set("pt", GeodeticUtility.formatLatLon(yx)); // The point in question.
        geoLookup.set("d", withinKM);
        return SolrProxy.searchGazetteer(solr.getInternalSolrServer(), geoLookup);
    }

    /**
     * Variation on placesAt().  UNSORTED!
     *
     * @param yx        location
     * @param withinKM  distance - required.
     * @param feature   feature class
     * @return
     * @throws SolrServerException
     */
    public List<Place> placesAt(LatLon yx, int withinKM, String feature) throws SolrServerException {

        /*
         */
        ModifiableSolrParams spatialQuery = createGeodeticLookupParams();
        spatialQuery.set(CommonParams.FQ, String.format("feat_class:%s", feature));

        spatialQuery.set("pt", GeodeticUtility.formatLatLon(yx)); // The point in question.
        spatialQuery.set("d", withinKM); // Example - Find places within 50 KM, but only first N rows are returned.
        return SolrProxy.searchGazetteer(solr.getInternalSolrServer(), spatialQuery);
    }

    /**
     * Iterate through a list and choose a place closest to the given point
     * @param yx point of interest
     * @param places list of places
     * @return closest place
     */
    public static final Place closest(LatLon yx, List<Place> places) {

        long dist = 10000000L;
        Place chosen = null;
        for (Place p : places) {
            long currentDist = GeodeticUtility.distanceMeters(yx, p);
            if (currentDist < dist) {
                dist = currentDist;
                chosen = p;
            }
        }
        return chosen;  // Is not null.
    }

    /**
     * This is a reasonable guess.
     * CAVEAT:  This does not use Solr Spatial location sorting.
     * 
     * @param yx
     * @param withinKM
     * @param feature
     * @return
     * @throws SolrServerException
     */
    public Place placeAt(LatLon yx, int withinKM, String feature) throws SolrServerException {
        List<Place> candidates = placesAt(yx, withinKM, feature);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return closest(yx, candidates);
    }

}
