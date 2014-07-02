package org.opensextant.extractors.test;

import java.util.List;

import org.opensextant.extractors.geo.GazetteerMatcher;
import org.opensextant.extractors.geo.PlaceCandidate;

public class TestGazMatcher {

    /**
     * Do a basic test.  Requirements include setting solr.solr.home or SOLR_HOME or solr_url
     * 
     */
    public static void main(String[] args) throws Exception {
        // String solrHome = args[0];

        GazetteerMatcher sm = new GazetteerMatcher();
        try {
            String docContent = "We drove to Sin City. The we drove to -$IN ĆITŸ .";

            System.out.println(docContent);

            List<PlaceCandidate> matches = sm.tagText(docContent, "main-test");

            for (PlaceCandidate pc : matches) {
                System.out.println(pc.toString());
            }
        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            sm.shutdown();
        }
    }
}
