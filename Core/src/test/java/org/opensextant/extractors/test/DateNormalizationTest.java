package org.opensextant.extractors.test;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.xtemporal.DateMatch;
import org.opensextant.extractors.xtemporal.XTemporal;

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

    /**
     * Note that this may report false negatives if the JVM's default time
     * zone is UTC.
     */
    @Test
    public void ensureTimeZone() {
        // Not parseable by default.  pattern is too noisy.
        final TextMatchResult result1 = timeFinder.extract_dates("Oct 07", "dummy");
        System.err.println("1 " + result1.matches.toString());
        assertEquals(0, result1.matches.size());
        final TextMatchResult result2 = timeFinder.extract_dates("Oct 2007", "dummy");
        System.err.println("2 " + result1.matches.toString());
        assertEquals(1, result2.matches.size());

        DateMatch dt = (DateMatch) result2.matches.get(0);
        long noon = (12 * 3600 * 1000);
        assertEquals(1191196800000L + noon, dt.datenorm.getTime());
    }
}
