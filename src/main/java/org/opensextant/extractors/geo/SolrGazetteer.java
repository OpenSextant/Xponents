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
package org.opensextant.extractors.geo;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.opensextant.ConfigException;
import org.opensextant.data.Country;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code>
 * environment variable must be set to the location of the Solr server.
 *
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public class SolrGazetteer {

    /**
     * In the interest of optimization we made the Solr instance a static class
     * attribute that should be
     * thread safe and shareable across instances of SolrMatcher
     */
    private final ModifiableSolrParams params = new ModifiableSolrParams();
    private SolrProxy solr = null;

    /**
     * fast lookup by ISO2 country code.
     */
    private Map<String, Country> countryCodes = null;

    /**
     * Default country code in solr gazetteer is ISO, so if given a FIPS code, we
     * need a helpful lookup
     * to get ISO code for lookup.
     */
    private final Map<String, String> countryFIPS_ISO = new HashMap<>();

    /**
     * Geodetic search parameters.
     */
    private final ModifiableSolrParams geoLookup = createGeodeticLookupParams(1000);

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
        // initialize();
        solr = currentIndex;

        try {
            this.countryCodes = loadCountries(solr.getInternalSolrClient());
        } catch (SolrServerException loadErr) {
            throw new ConfigException("SolrGazetteer is unable to load countries due to Solr error", loadErr);
        } catch (IOException ioErr) {
            throw new ConfigException("SolrGazetteer is unable to load countries due to IO/file error", ioErr);
        }
    }

    /**
     * Returns the SolrProxy used internally.
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
     * Creates a generic spatial query for up to first 25 rows.
     *
     * @return default params
     */
    protected static ModifiableSolrParams createGeodeticLookupParams() {
        return createGeodeticLookupParams(25);
    }

    public static final String DEFAULT_FIELDS = "id,name,cc,adm1,adm2,feat_class,feat_code,geo,place_id,id_bias,name_type";
    /**
     * For larger areas choose a higher number of Rows to return. If you choose to
     * use Solr spatial
     * score-by-distance for sorting or anything, then Solr appears to want to load
     * entire index into
     * memory. So this sort mechanism is off by default.
     *
     * @param rows rows to include in spatial lookups
     * @return solr params
     */
    protected static ModifiableSolrParams createGeodeticLookupParams(int rows) {
        /*
         * Basic parameters for geospatial lookup. These are reused, and only pt and d
         * are set for each lookup.
         */
        ModifiableSolrParams p = new ModifiableSolrParams();
        p.set(CommonParams.FL, DEFAULT_FIELDS);
        p.set(CommonParams.ROWS, rows);

        /*
         * TOOD: Note that spatial filtering/scoring on large data sets seems to be very
         * memory intensive. Failures below... must calculate and sort distance
         * manually....
         */
        // p.set(CommonParams.Q, "*:*");
        // p.set(CommonParams.Q, "{!geofilt sfield=geo score=distance cache=true}");
        // p.set(CommonParams.SORT, "geodist() asc");
        p.set(CommonParams.Q, "{!geofilt}");
        p.set("sfield", "geo");

        return p;
    }

    public static ModifiableSolrParams createDefaultSearchParams(int rows) {
        ModifiableSolrParams p = new ModifiableSolrParams();

        // What?
        p.set(CommonParams.Q, "*:*");
        // Which fields:
        p.set(CommonParams.FL, DEFAULT_FIELDS);
        // Max Rows:
        p.set(CommonParams.ROWS, rows);

        return p;
    }

    /**
     * Initialize. Cascading env variables: First use value from constructor, then
     * opensextant.solr,
     * then solr.solr.home
     *
     * @throws ConfigException Signals that a configuration exception has occurred.
     */
    private void initialize(String solrHome) throws ConfigException {

        solr = solrHome != null ? new SolrProxy(solrHome, "gazetteer") : new SolrProxy("gazetteer");

        params.set(CommonParams.Q, "*:*");
        params.set(CommonParams.FL, DEFAULT_FIELDS);
        try {
            this.countryCodes = loadCountries(solr.getInternalSolrClient());
        } catch (SolrServerException loadErr) {
            throw new ConfigException("SolrGazetteer is unable to load countries due to Solr error", loadErr);
        } catch (IOException ioErr) {
            throw new ConfigException("SolrGazetteer is unable to load countries due to IO/file error", ioErr);
        }
    }

    /**
     * Close or release all resources.
     */
    public void close() {
        if (solr != null) {
            try {
                solr.close();
            } catch (IOException err) {
                // log: error("Rare failure closing Solr I/O");
            }
        }
    }

    /**
     * List all country names, official and variant names. Distinct territories
     * (whose own ISO codes are unique) are listed as well. Territories owned by other countries -- their ISO
     * code is their owning nation -- are attached as Country.territory (call
     * Country.getTerritories() to list them).
     * Name aliases are listed as Country.getAliases()
     * The hash map returned contains all 260+ country listings keyed by ISO2 and
     * ISO3. Odd commonly used variant codes are added as well.
     *
     * @return the countries
     */
    public Map<String, Country> getCountries() {
        return countryCodes;
    }

    /** The Constant UNK_Country. */
    public static final Country UNK_Country = new Country("UNK", "invalid");

    /**
     * Get Country by the default ISO digraph returns the Unknown country if you are
     * not using an ISO2
     * code.
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
     * TODO: allow caller to get all entries, including abbreviations.
     *
     * @param index solr instance to query
     * @return country data hash
     * @throws SolrServerException the solr server exception
     * @throws IOException         on err, if country metadata file is not found in
     *                             classpath
     */
    public static Map<String, Country> loadCountries(SolrClient index) throws SolrServerException, IOException {

        GeonamesUtility geodataUtil = new GeonamesUtility();
        Map<String, Country> countryCodeMap = geodataUtil.getISOCountries();

        Logger log = LoggerFactory.getLogger(SolrGazetteer.class);
        ModifiableSolrParams ctryparams = new ModifiableSolrParams();
        ctryparams.set(CommonParams.FL, "id,name,cc,FIPS_cc,adm1,adm2,feat_class,feat_code,geo,name_type");

        /* TODO: Consider different behaviors for PCLI vs. PCL[DFS] */
        ctryparams.set("q", "+feat_class:A +feat_code:(PCLI OR PCLIX OR TERR) +name_type:N");
        /*
         * As of 2015 we have 2300+ name variants for countries and territories
         */
        ctryparams.set("rows", 10000);

        QueryResponse response = index.query(ctryparams);

        // Process Solr Response
        //
        SolrDocumentList docList = response.getResults();
        for (SolrDocument gazEntry : docList) {

            Country C = createCountry(gazEntry);

            Country existingCountry = countryCodeMap.get(C.getCountryCode());
            if (existingCountry != null) {
                if (existingCountry.ownsTerritory(C.getName())) {
                    // do nothing.
                } else if (C.isTerritory) {
                    log.debug("{} territory of {}", C, existingCountry);
                    existingCountry.addTerritory(C);
                } else {
                    log.debug("{} alias of {}", C, existingCountry);
                    existingCountry.addAlias(C.getName()); // all other metadata
                                                           // is same.
                }
                continue;
            }

            log.info("Unknown country in gazetteer, that is not in flat files. C={}", C);

            countryCodeMap.put(C.getCountryCode(), C);
            if (C.CC_ISO3!=null) {                
                countryCodeMap.put(C.CC_ISO3, C);
            }
        }

        return countryCodeMap;
    }

    private static final Country createCountry(SolrDocument gazEntry) {
        String code = SolrUtil.getString(gazEntry, "cc");
        String name = SolrUtil.getString(gazEntry, "name");
        String featCode = SolrUtil.getString(gazEntry, "feat_code");

        Country C = new Country(code, name);
        if ("TERR".equals(featCode)) {
            C.isTerritory = true;
            /* "PCL" (political entity) is another likely territory feature code. */
            // Other conditions?
        }
        // Set this once. Yes, indeed we would see this metadata repeated for
        // every country entry.
        // Geo field is specifically Spatial4J lat,lon format.
        double[] xy = SolrUtil.getCoordinate(gazEntry, "geo");
        C.setLatitude(xy[0]);
        C.setLongitude(xy[1]);

        String fips = SolrUtil.getString(gazEntry, "FIPS_cc");
        C.CC_FIPS = fips;

        C.setName_type(SolrUtil.getChar(gazEntry, "name_type"));

        return C;
    }

    /**
     * <pre>
     *  Search the gazetteer using a phrase. The phrase will be quoted
     * internally as it searches Solr
     *
     * e.g., search( "\"Boston City\"" )
     *
     * Solr Gazetteer uses OR as default joiner for clauses. Without quotes the
     * above search would be "Boston" OR "City" effectively.
     * </pre>
     *
     * @param place_string the place_string
     * @return places List of place entries
     * @throws SolrServerException the solr server exception
     */
    public List<Place> search(String place_string) throws SolrServerException, IOException {
        return search(place_string, false);
    }

    /**
     * Instance method that reuses a set of SolrParams for optimized search.
     *
     * <pre>
     *  Search the gazetteer using one of the following:
     *
     * a name or keyword a Solr style fielded query, which by default includes
     * bare keyword searches
     *
     * search( "\"Boston City\"" )
     *
     * Solr Gazetteer uses OR as default joiner for clauses.
     * </pre>
     *
     * @param place   the place
     * @param as_solr the as_solr
     * @return places List of place entries
     * @throws SolrServerException the solr server exception
     * @throws IOException
     */
    public List<Place> search(String place, boolean as_solr) throws SolrServerException, IOException {

        if (as_solr) {
            params.set("q", place);
        } else {
            // Bare keyword query needs to be quoted as "word word word"
            params.set("q", "\"" + place + "\"");
        }

        return SolrProxy.searchGazetteer(solr.getInternalSolrClient(), params);
    }

    /**
     *
     * @param p
     * @return
     * @throws SolrServerException
     * @throws IOException
     */
    public List<Place> search(SolrParams p) throws SolrServerException, IOException {

        return SolrProxy.searchGazetteer(solr.getInternalSolrClient(), p);
    }

    /**
     * Find places located at a particular location.
     *
     * @param yx       location
     * @param withinKM positive distance radius is required.
     * @return unsorted list of places near location
     * @throws SolrServerException on err
     */
    public List<Place> placesAt(LatLon yx, int withinKM) throws SolrServerException, IOException {

        geoLookup.set("pt", GeodeticUtility.formatLatLon(yx));
        geoLookup.set("d", withinKM);

        int MAX_RESULTS = 25;
        List<Place> raw = SolrProxy.searchGazetteer(solr.getInternalSolrClient(), geoLookup);
        List<RelativePlace> sortable = new ArrayList<>();

        /* Prepare results by distance */
        for (Place p : raw) {
            RelativePlace p1 = new RelativePlace(p, GeodeticUtility.distanceMeters(yx, p));
            sortable.add(p1);
        }
        /* Sort */
        Collections.sort(sortable);

        /* Trim */
        List<Place> trimmed = new ArrayList<>();
        int end = sortable.size() > MAX_RESULTS ? MAX_RESULTS : sortable.size();
        for (int x = 0; x < end; ++x) {
            trimmed.add(sortable.get(x).place);
        }
        return trimmed;
    }

    /**
     * Internal class for comparing gazetteer entries returned as a result of a
     * point search, ie.., using
     * 
     * <pre>
     * { !geofilt }
     * </pre>
     *
     * @author ubaldino
     */
    class RelativePlace implements Comparable<RelativePlace> {
        public long radius = -1;
        public Place place = null;

        RelativePlace(Place p, long r) {
            this.place = p;
            this.radius = r;
        }

        @Override
        public int compareTo(RelativePlace o) {
            if (o.radius == this.radius) {
                return 0;
            }
            return o.radius < this.radius ? 1 : -1;
        }
    }

    /**
     * Variation on placesAt().
     *
     * @param yx       location
     * @param withinKM distance - required.
     * @param feature  feature class
     * @return unsorted list of places near location
     * @throws SolrServerException on err
     */
    public List<Place> placesAt(LatLon yx, int withinKM, String feature) throws SolrServerException, IOException {

        /*
         */
        ModifiableSolrParams spatialQuery = createGeodeticLookupParams();
        spatialQuery.add(CommonParams.FQ, String.format("feat_class:%s", feature));

        // The point in question.
        spatialQuery.set("pt", GeodeticUtility.formatLatLon(yx));
        // Example: Find places within 50KM, but only first N rows returned.
        spatialQuery.set("d", withinKM);
        return SolrProxy.searchGazetteer(solr.getInternalSolrClient(), spatialQuery);
    }

    /**
     * Iterate through a list and choose a place closest to the given point
     *
     * @param yx     point of interest
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
        return chosen; // Is not null.
    }

    /**
     * This is a reasonable guess. CAVEAT: This does not use Solr Spatial location
     * sorting.
     *
     * @param yx       location
     * @param withinKM distance in KM
     * @param feature  feature type
     * @return closest place to given location.
     * @throws SolrServerException on err
     */
    public Place placeAt(LatLon yx, int withinKM, String feature) throws SolrServerException, IOException {
        List<Place> candidates = placesAt(yx, withinKM, feature);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return closest(yx, candidates);
    }

    /**
     * Given a name, find all locations matching that. Matches may be +/- 1 or 2
     * characters different.
     *
     * <pre>
     *  search for "Fafu" Is "Fafu'" acceptable? then len tolerance is
     * about +1 IS "Fafu Airport" acceptable, then use feat_class:S and a much
     * longer tolerance.
     * </pre>
     *
     * @param name
     * @param parametricQuery
     * @param lenTolerance    your choice for how much longer a valid matching name can be.
     * @return list of matching places
     * @throws ExtractionException
     */
    public List<Place> findPlaces(String name, String parametricQuery, int lenTolerance) throws ExtractionException {

        /*
         * Create a solr fielded query "field:Value AND|OR field:Value..." Xponents
         * gazetteer fields: name -- stores general purpose full-text value name_ar --
         * stores Arabic-specific full-text value name_cjk -- stores CJK-specific
         * full-text value
         */
        String q = String.format("%s AND +name:\"%s\"", parametricQuery, name);

        /*
         * Execute query, get List of Place instances (one per gazetteer entry)
         */
        try {
            int len = name.length();
            List<Place> locs = new ArrayList<>();
            for (Place loc : this.search(q, true)) {
                if (loc.getName().length() - len <= lenTolerance) {
                    locs.add(loc);
                }
            }
            return locs;
        } catch (SolrServerException | IOException sse) {
            throw new ExtractionException("Query failed", sse);
        }
    }

    /**
     * Find all places for a given gazetteer Place ID. You'll find all the variants
     * vary by name only.
     * same place ID should have same feature coding, lat/lon and other metadata.
     *
     * @param placeID
     * @return
     * @throws ExtractionException
     */
    public List<Place> findPlacesById(String placeID) throws ExtractionException {
        try {
            return this.search("place_id:" + placeID, true);
        } catch (SolrServerException | IOException sse) {
            throw new ExtractionException("Query error using PlaceID. ID is purely alphanumeric", sse);
        }
    }

    /**
     * NOTE: This yields primarily ASCII transliterations/romanized versions of the given place.
     * You may indeed find multiple locations with the same name. Your parametric query
     * should include feature type (feat_code:P, etc.) and country code (cc:AB) to yield the most relevant
     * locations for a given name.
     *
     * @param name
     * @param parametricQuery
     * @param lenTolerance    your choice for how much longer a valid matching name
     *                        can be.
     * @return
     * @throws ExtractionException
     */
    public List<Place> findPlacesRomanizedNameOf(String name, String parametricQuery, int lenTolerance)
            throws ExtractionException {
        List<Place> results = new ArrayList<>();
        for (Place p : this.findPlaces(name, parametricQuery, lenTolerance)) {
            for (Place pid : this.findPlacesById(p.getPlaceID())) {
                if (TextUtils.isASCII(pid.getName().getBytes())) {
                    results.add(pid);
                }
            }
        }
        return results;
    }
}
