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
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.util.FileUtility;

/**
 * Setup for testing:
 * 
 * - build Gazeteer project - load data into ./solr/gazetteer solr core - run
 * this test main, -Dopensextant.solr=<path to gazetteer solr home> one
 * argument, then input file to test.
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

    public static void print(String m) {
        System.out.println(m);
    }

    public static void printGeoTags(TextMatch match) {
        System.out.println("Name:" + match.getText());
    }

    public static void summarizeFindings(List<TextMatch> matches) {
        Set<String> placeNames = new TreeSet<>();
        Set<String> countryNames = new TreeSet<>();
        Set<String> coordinates = new TreeSet<>();
        System.out.println("MENTIONS ALL == " + matches.size());
        for (TextMatch tm : matches) {
            printGeoTags(tm);
            if (tm.isFilteredOut()) {
                System.out.println("\t(filtered out: " + tm.getText() + ")");
                continue;
            }
            if (tm instanceof PlaceCandidate) {
                PlaceCandidate p = (PlaceCandidate) tm;
                if (p.isCountry) {
                    countryNames.add(p.getText());
                } else if (p.getChosen() != null) {
                    System.out.println(String.format("\tgeocoded @ %s with conf=%d", p.getChosen(), p.getConfidence()));
                    placeNames.add(p.getText());
                } else {
                    placeNames.add(p.getText());
                }
            }
            if (tm instanceof GeocoordMatch) {
                GeocoordMatch geo = (GeocoordMatch) tm;
                coordinates.add(geo.getText());
                if (geo.getRelatedPlace() != null) {
                    System.out.println("Coordinate at place named " + geo.getRelatedPlace());
                }
            }
        }

        System.out.println("MENTIONS DISTINCT PLACES == " + placeNames.size());
        System.out.println(placeNames);
        System.out.println("MENTIONS COUNTRIES == " + countryNames.size());
        System.out.println(countryNames);
        System.out.println("MENTIONS COORDINATES == " + coordinates.size());
        System.out.println(coordinates);

    }

    public static List<TextMatch> copyFrom(List<PlaceCandidate> list) {
        List<TextMatch> list2 = new ArrayList<>();
        list2.addAll(list);
        return list2;
    }

    /**
     * Do a basic test. Requirements include setting opensextant.solr to solr
     * core home. (Xponents/solr, by default) USAGE:
     * 
     * TestGazMatcher file
     * 
     * Prints: all matched, filtered place mentions distinct places distinct
     * countries
     */
    public static void main(String[] args) throws Exception {

        GazetteerMatcher sm = new GazetteerMatcher(true /* allow lowercase */);
        URL filterFile = TestGazMatcher.class.getResource("/test-filter.txt");
        if (filterFile == null) {
            System.err.println("This test requires a 'test-filter.txt' file with non-place names in it."
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
