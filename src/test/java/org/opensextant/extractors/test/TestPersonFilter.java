package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public void testPunctuation() {
        String testUniDashes = "\u2014\u2015\u201C\u201D\u2033";
        Pattern invalidPunct = Pattern.compile("[\\p{Punct}&&[^'`.]]+\\s+|[\"\u2014\u2015\u201C\u201D\u2033]");

        /*
         * allowable: A. B A-B A.B. A_B A` B A`B `A B 'A B A 'B
         */
        Pattern anyInvalidPunct = Pattern.compile("[\\p{Punct}&&[^-_.'`]]+");
        assertTrue(invalidPunct.matcher("A[ ]B").find());
        assertTrue(invalidPunct.matcher("A} {B").find());
        assertTrue(invalidPunct.matcher(testUniDashes).find());

        Matcher m = invalidPunct.matcher("A}{B");
        while (m.find()) {
            print(m.group());
        }
        String[] validExamples = { "A-B", "Mr. A. B-C", "A{}B", "A} {B", "A. {B", "A\u2014.B" };
        for (String s : validExamples) {
            m = invalidPunct.matcher(s);
            print("Test " + s);
            while (m.find()) {

                print("\tInvalid:" + m.group());
            }
            m = anyInvalidPunct.matcher(s);
            while (m.find()) {
                print("\tInvalid: " + m.group());
            }
        }
    }

    @Test
    public void testNonsenseShortPhrases() {
        assertTrue(isMismatchedShortName("Eï", "ei"));
        assertTrue(isMismatchedShortName("Eï", "Ei"));
        assertTrue(isMismatchedShortName("S A", "S. a'"));
        assertTrue(!isMismatchedShortName("In", "In."));
    }

    /**
     * Call if you have a short name. This method does not have a length filter
     * assumed.
     * 
     * @param matched entry or official name matched
     * @param signal  raw input
     * @return
     */
    public static boolean isMismatchedShortName(String matched, String signal) {
        boolean isShort = signal.length() < 8;
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
