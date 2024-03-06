package org.opensextant.extractors.test;

import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.xtemporal.DateMatch;
import org.opensextant.extractors.xtemporal.DateNormalization;
import org.opensextant.extractors.xtemporal.XTemporal;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author jgibson
 */
public class DateNormalizationTest {
    private static XTemporal timeFinder = null;

    @BeforeClass
    public static void setUpClass() {
        try {
            timeFinder = new XTemporal(true);
            timeFinder.configure();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    static int counter = 0;

    /**
     * Facilitate validation.
     * @param result
     * @param validCount
     */
    static void print(TextMatchResult result, int validCount) {
        ++counter;
        System.err.println(counter + " " + result.matches.toString());
        int validMatches = 0;
        for (TextMatch m: result.matches){
            if (!m.isFilteredOut()){
                ++validMatches;
            }
        }
        assertEquals(validCount, validMatches);
    }

    /**
     * Note that this may report false negatives if the JVM's default time zone is UTC.
     */
    @Test
    public void ensureTimeZone() {
        // Not parseable by default. pattern is too noisy.
        TextMatchResult result = timeFinder.extract_dates("Oct 07", "dummy");
        print(result, 0);

        result = timeFinder.extract_dates("Oct 2007", "dummy");
        print(result, 1);

        result = timeFinder.extract_dates("2007-10-12T11:33:00", "dummy");
        print(result, 1);

        result = timeFinder.extract_dates("2007-10-12T25:33:00", "dummy");
        print(result, 1);

        result = timeFinder.extract_dates("2007-10-12T13:33", "dummy");
        print(result, 1);

        // "2007-10-12T13:33:00"  epoch is 1192195980 sec
        DateMatch dt = (DateMatch) result.matches.get(0);

        // Calendar d2007 = Calendar.getInstance();
        // d2007.setTimeZone(TimeZone.getTimeZone("GMT+0000"));
        // d2007.setTimeInMillis(dt.datenorm.getTime());
        //long epoch = d2007.getTime().getTime();
        long epoch = dt.datenorm.getTime();
        assertEquals(1192195980000L , epoch);
    }

    @Test
    public void testNormalization(){
        HashMap<String,String> foundFields = new HashMap<>();
        foundFields.put("hh", "11");
        foundFields.put("mm", "33");
        foundFields.put("ss", "14");
        foundFields.put("xx", "88");

        assertEquals(11, DateNormalization.normalizeTime(foundFields, "hh"));
        assertEquals(33, DateNormalization.normalizeTime(foundFields, "mm"));
        assertEquals(14, DateNormalization.normalizeTime(foundFields, "ss"));
        assertEquals(-1, DateNormalization.normalizeTime(foundFields, "xx"));

        foundFields.put("hh", "28");
        assertEquals(-1, DateNormalization.normalizeTime(foundFields, "hh"));
    }
}
