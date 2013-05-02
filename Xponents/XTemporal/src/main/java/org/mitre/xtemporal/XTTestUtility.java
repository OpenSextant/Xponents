/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
package org.mitre.xtemporal;

import java.io.*;
import org.mitre.opensextant.util.FileUtility;
import org.mitre.flexpat.TextMatch;

import com.csvreader.CsvWriter;
//import java.nio.charset.Charset; CsvWriter's use of Charset.from('UTF-8') appears to be wrong.

/**
 *
 * @author ubaldino
 */
public class XTTestUtility {

    private CsvWriter report = null;

    /**
     *
     * @param file
     * @throws IOException
     */
    public XTTestUtility(String file) throws IOException {
        report = open(file);
        write_header();
    }

    private void write_header() throws IOException {

        String[] header = {"RESULT_ID", "STATUS", "Message",
            "PATTERN", "MATCHTEXT", "DATETEXT", "DATE", "OFFSET"};

        for (String col : header) {
            report.write(col);
        }

        report.endRecord();
    }

    /**
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

    /**   Coordinate Test/Eval format
     * 
     * 
     * Result ID, CCE family, pattern ID, status, message  // Reason for failure
     * Result ID, CCE family, pattern ID, status, Match ID, matchtext, lat, lon etc. // Success implied by match
     * @param results 
     * @throws IOException 
     */
    public void save_result(XTempResult results)
            throws IOException {

        if (results.matches != null) {
            for (TextMatch tm : results.matches) {
                DateMatch m = (DateMatch)tm;
                report.write(results.result_id);
                report.write("PASS");
                String msg = results.message;
                if (m.is_submatch){
                    msg += "; Is Submatch";
                }
                report.write(msg);
                report.write(m.pattern_id);
                report.write(m.getText());
                report.write(m.datenorm.toString());
                report.write(m.datenorm_text);
                report.write("" + m.start);

                //report.write("" + m.is_submatch);
                report.endRecord();
            }
        } else {
            report.write(results.result_id);
            report.write("FAIL");
            report.write(results.get_trace());
            
            report.endRecord();
        }
    }
}
