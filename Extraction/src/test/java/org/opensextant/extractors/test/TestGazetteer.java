package org.opensextant.extractors.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

public class TestGazetteer {

    /**
     * Do a basic test -- This main prog makes use of the default JVM arg for solr:  -Dsolr.solr.home = /path/to/solr
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) {

        SolrGazetteer gaz = null;
        try {

            gaz = new SolrGazetteer();

            GeonamesUtility geodataUtil = new GeonamesUtility();

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
            for (Country C : geodataUtil.getCountries().values()) {
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
                System.out.println(String.format("\"%s\", Has Duplicates:", cname) + done.get(cname));
            }

        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            gaz.shutdown();
            System.exit(0);
        }
    }
}
