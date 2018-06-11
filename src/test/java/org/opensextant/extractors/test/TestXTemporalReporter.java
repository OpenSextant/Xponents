package org.opensextant.extractors.test;

import java.io.IOException;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.HashMap;

import org.opensextant.util.FileUtility;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.xtemporal.DateMatch;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.cellprocessor.constraint.*;
import org.supercsv.cellprocessor.*;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author ubaldino
 */
public class TestXTemporalReporter {

    private CsvMapWriter report = null;

    /**
     *
     * @param file
     * @throws IOException
     */
    public TestXTemporalReporter(String file) throws IOException {
        report = open(file);
        report.writeHeader(header);
    }
    protected static final String[] header = {"RESULT_ID", "STATUS",
        "Message", "PATTERN", "MATCHTEXT", "DATETEXT", "DATE", "RESOLUTION", "JAVA_EPOCH", "OFFSET"};

    protected static final CellProcessor[] xtempResultsSpec = new CellProcessor[]{
        // Given test data is required:
        new NotNull(), new NotNull(), new NotNull(),
        // test results fields -- if result exists
        //
        new Optional(), new Optional(), new Optional(), new Optional(), // new
        // FmtDate("yyyy-MM-dd'T'HH:mm"),
        new Optional(),  // res
        new Optional(),   // java epoch
        new Optional()   // offset
    };

    /**
     *
     * @param file
     * @return
     * @throws IOException
     */
    public final CsvMapWriter open(String file) throws IOException {

        FileUtility.makeDirectory(new File(file).getParentFile());
        OutputStreamWriter iowriter = FileUtility
                .getOutputStream(file, "UTF-8");
        CsvMapWriter R = new CsvMapWriter(iowriter,
                CsvPreference.STANDARD_PREFERENCE);
        return R;
    }

    /**
     *
     */
    public void close_report() throws IOException {
        if (report != null) {
            report.flush();
            report.close();
        }
    }

    /**
     * @param results
     * @throws IOException
     */
    public void save_result(TextMatchResult results) throws IOException {

        Map<String, Object> row = null;

        if (! results.matches.isEmpty()) {
            for (TextMatch tm : results.matches) {

                row = new HashMap<String, Object>();
                row.put(header[0], results.result_id);
                row.put(header[1], "PASS");

                DateMatch m = (DateMatch) tm;
                String msg = results.message;
                if (m.is_submatch) {
                    msg += "; Is Submatch";
                }
                row.put(header[2], msg);
                row.put(header[3], m.pattern_id);
                row.put(header[4], m.getText());
                row.put(header[5], m.datenorm.toString());
                row.put(header[6], m.datenorm_text);
                row.put(header[7], m.resolution.toString());
                row.put(header[8], m.datenorm.getTime());
                row.put(header[9], m.start);

                report.write(row, header, xtempResultsSpec);
            }
        } else {
            row = new HashMap<String, Object>();
            row.put(header[0], results.result_id);
            row.put(header[1], "FAIL");
            row.put(header[2], results.get_trace());

            report.write(row, header, xtempResultsSpec);

        }
    }
}
