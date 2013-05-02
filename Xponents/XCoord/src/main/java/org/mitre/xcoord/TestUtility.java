/**
 *
 *  Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xcoord;

import com.csvreader.CsvWriter;
import java.io.*;
import org.mitre.opensextant.util.FileUtility;
import org.mitre.flexpat.TextMatch;
import org.mitre.flexpat.TextMatchResultSet;
//import java.nio.charset.Charset; CsvWriter's use of Charset.from('UTF-8') appears to be wrong.

/**
 *
 * @author ubaldino
 */
public class TestUtility {

    private CsvWriter report = null;
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
        "result_id", "status", "message", "family", "pattern", 
        "matchtext", "lat", "lon", "mgrs", "precision", "offset"};

    /**
     *
     * @param file
     * @throws IOException
     */
    public TestUtility(String file) throws IOException {
        report = open(file);
        write_header();
    }

    private void write_header() throws IOException {

        for (String col : header) {
            report.write(col);
        }

        report.endRecord();
    }

    /**
     * Create a typical CSV writer -- Excel compliant
     *
     * @param file
     * @return
     * @throws IOException
     */
    public CsvWriter open(String file) throws IOException {

        FileUtility.makeDirectory(new File(file).getParentFile());

        OutputStreamWriter iowriter = FileUtility.getOutputStream(file, "UTF-8");
        //CsvWriter report = new CsvWriter(file, ',', Charset.forName("UTF-8"));
        CsvWriter R = new CsvWriter(iowriter, ',');
        R.setRecordDelimiter('\n');
        R.setTextQualifier('"');
        R.setForceQualifier(true);
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
     *
     * @param t
     * @throws IOException
     */
    public void write_testcase(TestCase t) throws IOException {
        if (t == null) {
            report.write("");
            report.write("");
            report.write("");
            report.write("");
            report.write("");
            report.write("");
            return;
        }

        report.write(t.id);
        report.write(t.family);
        report.write(Boolean.toString(t.true_positive));
        report.write( t.match.lat_text );
        report.write( t.match.lon_text );
        report.write(t.text);
    }

    /**
     * Coordinate Test/Eval format
     *
     *
     * Result ID, CCE family, pattern ID, status, message // Reason for failure
     * Result ID, CCE family, pattern ID, status, Match ID, matchtext, lat, lon
     * etc. // Success implied by match
     *
     * @TODO: use TestCase here or rely on truth evaluation in Python
     * GeocoderEval?
     * @param t 
     * @param results
     * @throws IOException
     */
    public void save_result(TestCase t, TextMatchResultSet results)
            throws IOException {


        if (!results.matches.isEmpty()) {

            // List out true and false positives
            // 
            for (TextMatch tm : results.matches) {
                GeocoordMatch m = (GeocoordMatch) tm;
                if (!full_report && (m.is_submatch | m.is_duplicate)){
                    // Ignore submatches and duplicates
                    continue;
                }
                write_testcase(t);

                report.write(results.result_id);
                report.write( (full_report & m.is_submatch) ? "IGNORE" : "PASS" );

                String msg = results.message;
                if (m.is_submatch) {
                    msg += "; Is Submatch";
                }
                report.write(msg);

                report.write(XConstants.get_CCE_family(m.cce_family_id));
                report.write(m.pattern_id);
                report.write(m.getText());
                //report.write("'" + m.coord_text);
                report.write("" + m.formatLatitude());
                report.write("" + m.formatLongitude());
                try {
                    report.write(m.toMGRS());
                } catch (Exception err) {
                    report.write("");
                }

                report.write(m.formatPrecision());
                report.write("" + m.start);

                //report.write("" + m.is_submatch);
                report.endRecord();
            }
        } else {
            write_testcase(t);
            report.write(results.result_id);
            boolean expected_failure = false;
            if (t != null) {
                expected_failure = !t.true_positive;
            } else {
                // If the match message contains a test payload from the test cases
                // 
                String test_status = results.get_trace().toUpperCase();
                expected_failure = test_status.contains("FAIL");
            }

            report.write(expected_failure ? "PASS" : "FAIL");  // True Negative -- you ignored one correctly
            report.write(results.get_trace());
            report.endRecord();
        }
    }
    /* 
     public boolean evaluate(TestCase t, GeocoordMatch m){
        
     
     }
     **/
}
