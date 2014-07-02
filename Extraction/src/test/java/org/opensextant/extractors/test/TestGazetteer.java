package org.opensextant.extractors.test;

import java.util.List;
import java.util.Map;

import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.util.GeonamesUtility;

public class TestGazetteer {


    /**
     * Do a basic test -- This main prog makes use of the default JVM arg for solr:  -Dsolr.solr.home = /path/to/solr
     *
     * @param args the arguments
     * @throws Exception the exception
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
