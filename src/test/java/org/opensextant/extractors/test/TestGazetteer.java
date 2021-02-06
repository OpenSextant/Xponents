package org.opensextant.extractors.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.solr.client.solrj.SolrServerException;
import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

/**
 * <ol>
 * <li>GeonamesUtility: list countries official names and data</li>
 * <li>SolrGazetteer: query countries and print</li>
 * <li>SolrGazetteer: spatial query to find a place with M meters of a point.
 * </li>
 * </ol>
 */
public class TestGazetteer {

    public static SolrGazetteer gaz = null;

    /**
     * Do a basic test -- This main prog makes use of the default JVM arg for solr:
     * -Dopensextant.solr=/path/to/solr
     *
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) {

        try {
            GeonamesUtility geodataUtil = new GeonamesUtility();

            Country aus = geodataUtil.getCountry("AUS");
            System.out.println("Got Australia..." + aus);

            gaz = new SolrGazetteer();

            // Try to get countries
            Map<String, Country> countries = gaz.getCountries();
            for (Country c : countries.values()) {
                System.out.println(c.getKey() + " = " + c.name + "\t  Aliases: " + c.getAliases().toString());
            }

            /*
             * This test organizes country names to see if there are any country names that
             * are unique.
             */
            List<String> cnames = new ArrayList<>();
            Map<String, Boolean> done = new TreeMap<>();
            for (Country C : geodataUtil.getCountries()) {

                String q = String.format("name:%s AND -feat_code:PCL* AND -feat_code:TERR", C.getName());
                List<Place> country_name_matches = gaz.search(q, true);
                // System.out.println("Matched names for " + C.getName() +
                // ":\t");
                String cname = TextUtils.removeDiacritics(C.getName()).toLowerCase();

                done.put(cname, false);
                for (Place p : country_name_matches) {
                    String pname = TextUtils.removeDiacritics(p.getName());
                    if (pname.equalsIgnoreCase(cname)) {
                        done.put(p.getName().toLowerCase(), true);
                    }
                }

                cnames.add(cname);
            }

            // Collections.sort(cnames);
            for (String cname : done.keySet()) {
                System.out.println(String.format("\"%s\", Has Duplicates:", cname) + done.get(cname));
            }

            testPlacesAt(44, -118, 25 /* km */, "P"); // US
            testPlacesAt(44, 118, 100 /* km */, "A"); // East Asia
            testPlacesAt(44, 0, 250 /* km */, "A"); // Europe
            testPlacesAt(44, 0, 10 /* km */, "P"); // Europe

        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            gaz.close();
            System.exit(0);
        }
    }

    private static final void print(String m) {
        System.out.println(m);
    }

    /**
     * Test placesAt and closest functions to find and sort within reason places
     * near a given location.
     * 
     * @param lat
     * @param lon
     * @param radius
     * @param feat
     * @throws SolrServerException
     */
    private static final void testPlacesAt(double lat, double lon, int radius, String feat)
            throws SolrServerException, IOException {
        Place xy = new Place();
        xy.setLatitude(lat);
        xy.setLongitude(lon);
        long t0 = new Date().getTime();

        List<Place> places = gaz.placesAt(xy, radius, feat);
        print(String.format("Query time = %d ms", new Date().getTime() - t0));
        print(String.format("Distances from %s", xy));
        if (places != null) {
            print("Found = " + places.size());
            Place closestGeo = SolrGazetteer.closest(xy, places);
            print(String.format("Closest: %s (%d m away)", closestGeo, GeodeticUtility.distanceMeters(xy, closestGeo)));

            // print(places.toString());
            for (Place geo : places) {
                print(String.format("\tDistance  %s = %d m", geo.toString(), GeodeticUtility.distanceMeters(xy, geo)));
            }
        }
    }
}
