package org.opensextant.extractors.test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.opensextant.data.Place;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.GazetteerMatcher;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.processing.RuntimeTools;
import org.opensextant.util.FileUtility;
import org.opensextant.util.GeodeticUtility;

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

    protected final static ArrayList<Integer> memStats = new ArrayList<>();

    protected static final void printMemory() {
        int kbMemory = RuntimeTools.reportMemory();
        memStats.add(kbMemory);
        print("MEMORY USAGE (KB) " + kbMemory);
    }

    protected static final void dumpStats() {
        print("MEMORY SUMMARY");
        for (int s : memStats) {
            print("" + s);
        }
    }

    public static void print(String m) {
        System.out.println(m);
    }

    public static void printGeoTags(TextMatch match) {
        System.out.println("Name:" + match.getText() + ", Type:" + match.getType());
    }

    public static void summarizeFindings(List<TextMatch> matches, int expected) {
        if (matches == null) {
            print(" *** NULL MATCHES ***");
            return;
        }
        int count = 0;
        for (TextMatch m : matches) {
            count += (m.isFilteredOut() ? 0 : 1);
        }
        if (count != expected) {
            print(" *** MISMATCH COUNT Expected=" + expected + " ***");
        }
        summarizeFindings(matches);
    }

    public static void summarizeFindings(List<TextMatch> matches) {
        if (matches == null) {
            print(" *** NULL MATCHES ***");
            return;
        }
        Set<String> placeNames = new TreeSet<>();
        Set<String> countryNames = new TreeSet<>();
        Set<String> coordinates = new TreeSet<>();
        System.out.println("MENTIONS ALL == " + matches.size());
        for (TextMatch tm : matches) {
            printGeoTags(tm);
            if (tm instanceof PlaceCandidate) {
                PlaceCandidate p = (PlaceCandidate) tm;
                if (tm.isFilteredOut()) {
                    print("Filtered Out.  Rules = " + p.getRules());
                    continue;
                }
                if (!p.getRules().isEmpty()) {
                    print("Rules = " + p.getRules());
                }
                if (p.isCountry) {
                    countryNames.add(p.getText());
                } else if (p.getChosen() != null) {
                    print(String.format("\tgeocoded @ %s with conf=%d, at [%s]", p.getChosen(), p.getConfidence(),
                            GeodeticUtility.formatLatLon(p.getChosen())));
                    ScoredPlace alt = p.getSecondChoice();
                    if (alt != null) {
                        print(String.format("\tgeocoded @ %s second place", alt));
                    }
                    placeNames.add(p.getText());
                } else {
                    placeNames.add(p.getText());
                }
            } else if (tm.isFilteredOut()) {
                System.out.println("\t(filtered out: " + tm.getText() + ")");
                continue;
            }

            if (tm instanceof GeocoordMatch) {
                GeocoordMatch geo = (GeocoordMatch) tm;
                coordinates.add(geo.getText());
                if (geo.getRelatedPlace() != null) {
                    System.out.println("Coordinate at place named " + geo.getRelatedPlace());
                }
                if (geo.getNearByPlaces() != null) {
                    for (Place p : geo.getNearByPlaces()) {
                        long dist = GeodeticUtility.distanceMeters(p, geo);
                        System.out.println(String.format("Nearby place %s is %d meters away", p.getName(), dist));
                    }
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
     * Do a basic test. Requirements include setting opensextant.solr to solr core
     * home. (Xponents/solr, by default) USAGE:
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
            sm.close();
        }
    }
}
