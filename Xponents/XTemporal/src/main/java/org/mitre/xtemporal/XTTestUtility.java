/**
 *
 * Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xtemporal;

import java.io.*;
import java.util.*;
import org.mitre.opensextant.util.FileUtility;
import org.mitre.flexpat.TextMatch;
import org.mitre.flexpat.TextMatchResultSet;

import org.supercsv.io.CsvMapWriter;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.cellprocessor.constraint.*;
import org.supercsv.cellprocessor.*;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author ubaldino
 */
public class XTTestUtility {

    private CsvMapWriter report = null;

    /**
     *
     * @param file
     * @throws IOException
     */
    public XTTestUtility(String file) throws IOException {
        report = open(file);
        report.writeHeader(header);
    }
    protected final static String[] header = {"RESULT_ID", "STATUS",
        "Message", "PATTERN", "MATCHTEXT", "DATETEXT", "DATE", "RESOLUTION", "OFFSET"};
    
    protected static final CellProcessor[] xtempResultsSpec = new CellProcessor[]{
        // Given test data is required:
        new NotNull(), new NotNull(), new NotNull(),
        // test results fields -- if result exists
        //
        new Optional(), new Optional(), new Optional(), new Optional(), // new
        // FmtDate("yyyy-MM-dd'T'HH:mm"),
        new Optional(),  // res
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
    public void save_result(TextMatchResultSet results) throws IOException {

        Map<String, Object> row = null;

        if (! results.matches.isEmpty()) {
            for (TextMatch tm : results.matches) {

                row = new HashMap<>();
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
                row.put(header[8], m.start);

                report.write(row, header, xtempResultsSpec);
            }
        } else {
            row = new HashMap<>();
            row.put(header[0], results.result_id);
            row.put(header[1], "FAIL");
            row.put(header[2], results.get_trace());

            report.write(row, header, xtempResultsSpec);

        }
    }
}
