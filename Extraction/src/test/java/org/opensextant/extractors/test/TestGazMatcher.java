package org.opensextant.extractors.test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.GazetteerMatcher;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.FileUtility;

/**
 * Setup for testing:
 * 
 * - build Gazeteer project
 * - load data into ./solr/gazetteer solr core
 * - run this test main,
 *    -Dopensextant.solr=<path to gazetteer solr home>
 *    one argument, then input file to test.
 * 
 * @author ubaldino
 *
 */
public class TestGazMatcher {

    public static void printTextTags(List<TextMatch> list) {
        for (TextMatch pc : list) {
            System.out.println("Name:" + pc.getText());
        }
    }

    public static void printGeoTags(List<PlaceCandidate> list) {
        for (TextMatch pc : list) {
            System.out.println("Name:" + pc.getText());
        }
    }

    public static void printGeoTags(TextMatch match) {
        System.out.println("Name:" + match.getText());
    }

    public static void summarizeFindings(List<TextMatch> matches) {
        Set<String> placeNames = new TreeSet<>();
        Set<String> countryNames = new TreeSet<>();
        System.out.println("MENTIONS ALL == " + matches.size());
        for (TextMatch tm : matches) {
            printGeoTags(tm);
            if (tm instanceof PlaceCandidate) {
                PlaceCandidate p = (PlaceCandidate) tm;
                if (p.isCountry) {
                    countryNames.add(p.getText());
                } else {
                    placeNames.add(p.getText());
                }
            }
        }

        System.out.println("MENTIONS DISTINCT == " + placeNames.size());
        System.out.println(placeNames);
        System.out.println("MENTIONS COUNTRY == " + countryNames.size());
        System.out.println(countryNames);
    }

    public static List<TextMatch> copyFrom(List<PlaceCandidate> list) {
        List<TextMatch> list2 = new ArrayList<>();
        list2.addAll(list);
        return list2;
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

        GazetteerMatcher sm = new GazetteerMatcher(true /*allow lowercase */);
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
            for (PlaceCandidate pc : matches) {
                printGeoTags(pc);
            }


            docContent = "Is there some city in 刘家埝 written in Chinese?";
            matches = sm.tagCJKText(docContent, "main-test");
            for (PlaceCandidate pc : matches) {
                printGeoTags(pc);
            }

            docContent = "Where is seoul?";
            matches = sm.tagText(docContent, "main-test");
            for (PlaceCandidate pc : matches) {
                printGeoTags(pc);
            }
            String buf = FileUtility.readFile(args[0]);
            matches = sm.tagText(buf, "main-test", true);

            summarizeFindings(copyFrom(matches));
        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            sm.shutdown();
        }
    }
}
