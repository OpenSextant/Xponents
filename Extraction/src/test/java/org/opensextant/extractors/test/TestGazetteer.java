package org.opensextant.extractors.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

/**
 * <pre>
 * 1) GeonamesUtility:  list countries official names and data
 * 2) SolrGazetteer:    query countries and print
 * 3) SolrGazetteer:    spatial query to find a place with M meters of a point.
 * </pre>
 */
public class TestGazetteer {

    /**
     * Do a basic test -- This main prog makes use of the default JVM arg for solr:  -Dopensextant.solr=/path/to/solr
     *
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) {

        SolrGazetteer gaz = null;
        try {
            GeonamesUtility geodataUtil = new GeonamesUtility();

            Country aus = geodataUtil.getCountry("AUS");
            System.out.println("Got Australia..."+ aus);

            gaz = new SolrGazetteer();

            // Try to get countries
            Map<String, Country> countries = gaz.getCountries();
            for (Country c : countries.values()) {
                System.out.println(c.getKey() + " = " + c.name + "\t  Aliases: "
                        + c.getAliases().toString());
            }

            /*
             * This test organizes country names to see if there are any country names
             * that are unique. 
             */
            List<String> cnames = new ArrayList<>();
            Map<String, Boolean> done = new TreeMap<>();
            for (Country C : geodataUtil.getCountries()) {

                String q = String.format("name:%s AND -feat_code:PCLI AND -feat_code:TERR",
                        C.getName());
                List<Place> country_name_matches = gaz.search(q, true);
                //System.out.println("Matched names for " + C.getName() + ":\t");
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

            //Collections.sort(cnames);
            for (String cname : done.keySet()) {
                System.out.println(String.format("\"%s\", Has Duplicates:", cname)
                        + done.get(cname));
            }
            
            Place xy = new Place();
            xy.setLatitude(44);
            xy.setLongitude(-118);
            List<Place> states = gaz.placesAt( xy, 200, "A");
            if (states!=null){
                System.out.println(states.toString());
            }

        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            gaz.shutdown();
            System.exit(0);
        }
    }
}
