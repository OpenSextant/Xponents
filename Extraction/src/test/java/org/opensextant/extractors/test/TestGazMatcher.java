package org.opensextant.extractors.test;

import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.opensextant.extraction.MatchFilter;
import org.opensextant.extractors.geo.GazetteerMatcher;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.FileUtility;

public class TestGazMatcher {

    public static void printGeoTags(List<PlaceCandidate> list) {
        for (PlaceCandidate pc : list) {
            System.out.println("Name:" + pc.getText());
        }
    }

    /**
     * Do a basic test.  Requirements include setting opensextant.solr to solr core home. (Xponents/solr, by default)
     * USAGE:
     * 
     *      TestGazMatcher  file
     *      
     * Prints:
     *      all matched, filtered place mentions
     *      distinct places
     *      distinct countries
     */
    public static void main(String[] args) throws Exception {

        GazetteerMatcher sm = new GazetteerMatcher();
        URL filterFile = TestGazMatcher.class.getResource("/test-filter.txt");
        if (filterFile == null) {
            System.err
                    .println("This test requires a 'test-filter.txt' file with non-place names in it."
                            + "\nThese filters should match up with your test documents");
        }
        MatchFilter filt = new MatchFilter(filterFile);
        sm.setMatchFilter(filt);

        try {
            String docContent = "We drove to Sin City. The we drove to -$IN ĆITŸ .";

            System.out.println(docContent);

            List<PlaceCandidate> matches = sm.tagText(docContent, "main-test");
            printGeoTags(matches);

            String buf = FileUtility.readFile(args[0]);
            matches = sm.tagText(buf, "main-test", true);
            Set<String> placeNames = new TreeSet<>();
            Set<String> countryNames = new TreeSet<>();
            for (PlaceCandidate p : matches) {
                if (p.isCountry) {
                    countryNames.add(p.getText());
                } else {
                    placeNames.add(p.getText());
                }
            }

            System.out.println("MENTIONS ALL == " + matches.size());
            printGeoTags(matches);
            System.out.println("MENTIONS DISTINCT == " + placeNames.size());
            System.out.println(placeNames);
            System.out.println("MENTIONS COUNTRY == " + countryNames.size());
            System.out.println(countryNames);
        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            sm.shutdown();
        }
    }
}
