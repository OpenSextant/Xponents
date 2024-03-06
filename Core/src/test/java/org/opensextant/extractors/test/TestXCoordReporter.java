package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xcoord.GeocoordTestCase;
import org.opensextant.extractors.xcoord.XConstants;
import org.opensextant.util.FileUtility;
import org.supercsv.cellprocessor.FmtBool;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author ubaldino
 */
public class TestXCoordReporter {

    private CsvMapWriter report = null;
    /**
     *
     */
    public boolean full_report = true;
    /**
     * Coordinate Test Report Format
     *
     * important: status = PASS, FAIL, IGNORE
     */
    public static String[] header = {
            // Include truth test fields:
            "test_id", "test_fam", "true_pos", "true_lat", "true_lon", "text",
            // Include processed fields:
            "result_id", "status", "message", "family", "pattern", "matchtext", "lat", "lon", "mgrs", "precision",
            "offset" };

    protected static Map<String, Object> getEmptyRow() {
        Map<String, Object> blank = new HashMap<>();
        blank.put(header[0], "");
        blank.put(header[1], "");
        blank.put(header[2], false);
        blank.put(header[3], "");
        blank.put(header[4], "");
        blank.put(header[5], "");
        return blank;
    }

    protected static final CellProcessor[] xcoordResultsSpec = new CellProcessor[] {
            // Given test data is required:
            new NotNull(), new NotNull(), new FmtBool("true", "false"), new NotNull(), new NotNull(), new NotNull(),

            // test results fields -- if result exists
            //
            new Optional(), new Optional(), new Optional(), new Optional(), new Optional(),

            // Match data; If result is PASS and match exists
            new Optional(), new Optional(), new Optional(), new Optional(), new Optional(), new Optional()

    };

    /**
     *
     * @param file
     * @throws IOException
     */
    public TestXCoordReporter(String file) throws IOException {
        report = open(file);
        report.writeHeader(header);
    }

    /**
     * Create a typical CSV writer -- Excel compliant
     *
     * @param file
     * @return
     * @throws IOException
     */
    public CsvMapWriter open(String file) throws IOException {

        FileUtility.makeDirectory(new File(file).getParentFile());
        OutputStreamWriter iowriter = FileUtility.getOutputStream(file, "UTF-8");
        CsvMapWriter R = new CsvMapWriter(iowriter, CsvPreference.STANDARD_PREFERENCE);

        return R;
    }

    /**
     *
     */
    public void close_report() {
        if (report != null) {
            try {
                report.flush();
                report.close();
            } catch (Exception err) {
                System.out.println("Something is a miss... ");
                err.printStackTrace();
            }
        }
    }

    /**
     * Creates a valid row object for CSV output -- a blank row if TestCase is null,
     * otherwise populate the row with the base test metadata.
     * 
     * @param t
     */
    public Map<String, Object> createTestCase(GeocoordTestCase t) {

        if (t == null) {
            // This is used where there is no given test case... just extracting data from
            // random data.
            return getEmptyRow();
        }
        // This happens when the TestCases are well defined.

        Map<String, Object> row = new HashMap<>();
        row.put(header[0], t.id);
        row.put(header[1], t.family);
        row.put(header[2], t.true_positive);
        row.put(header[3], t.getGeo().getLatText());
        row.put(header[4], t.getGeo().getLonText());
        row.put(header[5], t.text);

        return row;
    }

    /**
     * Coordinate Test/Eval format
     *
     *
     * Result ID, CCE family, pattern ID, status, message // Reason for failure
     * Result ID, CCE family, pattern ID, status, Match ID, matchtext, lat, lon etc.
     * // Success implied by match
     *
     * @TODO: use TestCase here or rely on truth evaluation in Python GeocoderEval?
     * @param t
     * @param results
     * @throws IOException
     */
    public void save_result(GeocoordTestCase t, TextMatchResult results) throws IOException {

        Map<String, Object> row = null;

        if (!results.matches.isEmpty()) {

            // List out true and false positives
            //
            for (TextMatch tm : results.matches) {
                GeocoordMatch m = (GeocoordMatch) tm;
                if (!full_report && (m.is_submatch || m.is_duplicate)) {
                    // Ignore submatches and duplicates
                    continue;
                }
                row = createTestCase(t);

                row.put(header[6], results.result_id);
                row.put(header[7], (full_report & m.is_submatch) ? "IGNORE" : "PASS");

                String msg = results.message;
                if (m.is_submatch) {
                    msg += "; Is Submatch";
                }
                row.put(header[8], msg);

                row.put(header[9], XConstants.get_CCE_family(m.cce_family_id));
                row.put(header[10], m.pattern_id);
                row.put(header[11], m.getText());
                row.put(header[12], "" + m.formatLatitude());
                row.put(header[13], "" + m.formatLongitude());
                String mgrs = "";
                try {
                    mgrs = m.toMGRS();
                } catch (Exception err) {
                }
                row.put(header[14], mgrs);

                row.put(header[15], m.formatPrecision());
                row.put(header[16], (long)m.start);

                report.write(row, header, xcoordResultsSpec);
            }
        } else {
            row = createTestCase(t);

            row.put(header[6], results.result_id);

            boolean expected_failure = false;
            if (t != null) {
                expected_failure = !t.true_positive;
            } else {
                // If the match message contains a test payload from the test cases
                //
                String test_status = results.get_trace().toUpperCase();
                expected_failure = test_status.contains("FAIL");
            }

            row.put(header[7], expected_failure ? "PASS" : "FAIL"); // True Negative -- you ignored one correctly
            row.put(header[8], results.get_trace());

            report.write(row, header, xcoordResultsSpec);
        }
    }
}
