package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import java.net.URL;

import org.junit.Test;
import org.opensextant.ConfigException;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.rules.NonsenseFilter;
import org.opensextant.extractors.geo.rules.PersonNameFilter;
import org.opensextant.util.TextUtils;

public class TestPersonFilter {

    private final static void print(String msg) {
        System.out.println(msg);
    }

    @Test
    public void testNonsenseShortPhrases() {
        assertTrue(isMismatchedShortName("Eï", "ei"));
        assertTrue(isMismatchedShortName("Eï", "Ei"));
        assertTrue(isMismatchedShortName("S A", "S. a'"));
        assertTrue(!isMismatchedShortName("In", "In."));
    }
    
    /**
     * Call if you have a short name. This method does not have a length filter assumed.
     * 
     * @param matched  entry or official name matched
     * @param signal   raw input 
     * @return
     */
    public static boolean isMismatchedShortName(String matched, String signal) {
        boolean isShort = signal.length()<8;
        boolean badMatch = NonsenseFilter.irregularPunctPatterns(signal);
        boolean hasDiacritics = TextUtils.hasDiacritics(matched);
        boolean signalDiacrtics = TextUtils.hasDiacritics(signal);
        boolean misMatchDiacritics = signalDiacrtics != hasDiacritics;
        
        return isShort && (badMatch || misMatchDiacritics);
    }


    @Test
    public void testNonsensePhrases() {
        print("Test punctuation in names");

        /* Invalid names due to punctuation oddities. */
        assertTrue(NonsenseFilter.irregularPunctPatterns("”")); /* Double quotes - fail automatically */
        assertTrue(NonsenseFilter.irregularPunctPatterns("bust”—a-move")); /* Double quotes - fail automatically */
        assertTrue(NonsenseFilter.irregularPunctPatterns("bust— a-move")); /* Unicode Dash with spaces */
        assertTrue(NonsenseFilter.irregularPunctPatterns("south\", bend"));
        assertTrue(NonsenseFilter.irregularPunctPatterns("south\",bend")); /* No space? but has double quotes */
        assertTrue(NonsenseFilter.irregularPunctPatterns("To-  To- To-"));

        /* Valid names. */
        assertTrue(!NonsenseFilter.irregularPunctPatterns("St. Paul"));
        assertTrue(!NonsenseFilter.irregularPunctPatterns("to-to"));
        assertTrue(!NonsenseFilter.irregularPunctPatterns("U. S. A."));
        assertTrue(!NonsenseFilter.irregularPunctPatterns("U.S.A."));
        assertTrue(!NonsenseFilter.irregularPunctPatterns("L` Oreal"));
        assertTrue(!NonsenseFilter.irregularPunctPatterns("L`Oreal"));
    }

    @Test
    public void test() {
        // Set classpath to point to ./gazetteer/conf
        URL p1 = PlaceGeocoder.class.getResource("/filters/person-name-filter.txt");
        URL p2 = PlaceGeocoder.class.getResource("/filters/person-title-filter.txt");
        URL p3 = PlaceGeocoder.class.getResource("/filters/person-suffix-filter.txt");
        try {
            PersonNameFilter filt = new PersonNameFilter(p1, p2, p3);

            PlaceCandidate p = new PlaceCandidate();
            p.setText("John Doe");
            p.setPrematchTokens(null);
            p.setPostmatchTokens(null);
            filt.evaluate(p, null);
            print(p.getText() + " pass? " + p.isFilteredOut());

            p.setPrematchTokens("             ".split(" "));
            p.setPostmatchTokens("             ".split(" "));
            filt.evaluate(p, null);
            print(p.getText() + " pass? " + p.isFilteredOut());

            p.setPrematchTokens("this is Mr. ".split(" "));
            p.setPostmatchTokens(null);
            filt.evaluate(p, null);
            print(p.getText() + " pass? " + p.isFilteredOut());

            p.setPrematchTokens("this is Mr. ".split(" "));
            p.setPostmatchTokens(" and his wife lives in the city...".split(" "));
            filt.evaluate(p, null);
            print(p.getText() + " pass? " + p.isFilteredOut());

        } catch (ConfigException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            fail("Configuration problem -- set CLASSPATH to include ./conf");
        }
    }

}
