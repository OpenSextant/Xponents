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

import com.csvreader.CsvReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import org.apache.commons.io.FilenameUtils;
import org.mitre.flexpat.PatternTestCase;
import org.mitre.flexpat.TextMatch;
import org.mitre.flexpat.TextMatchResultSet;
import org.mitre.opensextant.util.FileUtility;
import org.mitre.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ubaldino
 */
public class TestScript {

    static Logger log = LoggerFactory.getLogger(TestUtility.class);
    private XCoord xcoord = null;

    /**
     *
     * @param xc
     */
    public TestScript(XCoord xc) {
        xcoord = xc;
    }

    /**
     * Test one input file, reporting output as a CSV
     *
     * @param file
     */
    public void test(String file) {
        systemTests();
        fileTests(file);
    }

    /**
     *
     * @param file
     */
    public void fileTests(String file) {

        log.info("\n\n=== TEXT FILE TESTS ===\n\n");
        TestUtility tester = null;
        String buffer = null;
        try {
            String _file = file.trim();
            buffer = FileUtility.readFile(_file);
            String fname = FilenameUtils.getBaseName(_file);
            tester = new TestUtility("./results/xcoord_" + fname + ".csv");
        } catch (IOException err) {
            log.error("Failed to open test file", err);
            return;
        }

        xcoord.matchAll();

        String jobid = TextUtils.text_id(buffer);

        log.info("Extract coordinates; All patterns enabled");
        try {
            TextMatchResultSet results = xcoord.extract_coordinates(buffer, jobid);
            tester.save_result(null, results);
        } catch (Exception err) {
            log.error("Failed to write report", err);
        }
        tester.close_report();

        log.info("=== TEXT FILE TESTS DONE ===");

    }

    /**
     * Using the TestUtility, all patterns are tested and reported to the
     * results folder.
     */
    public void systemTests() {

        log.info("\n\n=== SYSTEM TESTS ===\n\n");
        if (!xcoord.patterns.testing) {
            log.info("TESTING OFF -- TURN ON DEBUG in LOG4J");
            return;
        }

        xcoord.match_UTM(true);
        xcoord.match_MGRS(true);
        xcoord.match_DD(true);
        xcoord.match_DMS(true);
        xcoord.match_DM(true);

        try {
            TestUtility tester = new TestUtility("./results/xcoord_System.csv");

            for (PatternTestCase tst : xcoord.patterns.testcases) {
                TextMatchResultSet results = xcoord.extract_coordinates(tst.text, tst.id, tst.family_id);
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========SYSTEM TEST " + tst.id + " FOUND:" + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
                tester.save_result(null, results);

            }
            tester.close_report();

        } catch (Exception err) {
            log.error("Not finishing tests", err);
            return;
        }
        log.info("=== SYSTEM TESTS DONE ===");
    }

    /**
     * This will accomodate any test file that has at least the following style:
     *
     * FAMILY-XXX COORDINATE TEXT "FAIL"
     *
     * Where the first FAMILY token is
     *
     * @param coordfile
     */
    public void fileTestByLines(String coordfile) {

        xcoord.match_UTM(true);
        xcoord.match_MGRS(true);
        xcoord.match_DD(true);
        xcoord.match_DMS(true);
        xcoord.match_DM(true);

        try {

            String _file = coordfile.trim();
            String fname = FilenameUtils.getBaseName(_file);
            TestUtility tester = new TestUtility("./results/xcoord_" + fname + "-lines.csv");

            java.io.LineNumberReader in = FileUtility.getLineReader(coordfile);
            String line = null;
            while ((line = in.readLine()) != null) {

                String text = line.trim();
                if (text.startsWith("#")) {
                    continue;
                }
                if (text.isEmpty()) {
                    continue;
                }

                String fam = find_family(line);
                int famx = XConstants.get_CCE_family(fam);

                if (famx == XConstants.UNK_PATTERN) {
                    log.error("Unknown test pattern TEXT=" + text);
                    continue;
                }

                TestCase tst = new TestCase("#" + in.getLineNumber(), fam, text);
                TextMatchResultSet results = xcoord.extract_coordinates(tst.text, tst.id);
                /**
                 * tst.family_id
                 */
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========FILE TEST " + tst.id + " FOUND:" + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
                tester.save_result(tst, results);
            }


            tester.close_report();

            log.info("=== FILE TESTS DONE ===");

        } catch (Exception err) {
            log.error("TEST BY LINES", err);
        }


    }

    /**
     * Create a typical CSV writer -- Excel compliant
     *
     * @param file
     * @return
     * @throws IOException
     */
    public CsvReader open(String file) throws IOException {

        InputStreamReader rdr = FileUtility.getInputStream(file, "UTF-8");
        //CsvWriter report = new CsvWriter(file, ',', Charset.forName("UTF-8"));
        CsvReader R = new CsvReader(rdr);
        R.setRecordDelimiter('\n');
        R.setDelimiter(',');
        //R.setTextQualifier('"');
        //R.setForceQualifier(true);
        return R;
    }

    /**
     * This will accomodate any test file that has at least the following style:
     *
     * FAMILY-XXX COORDINATE TEXT "FAIL"
     *
     * Where the first FAMILY token is
     *
     * @param coordfile
     */
    public void fileTruth(String coordfile) {

        xcoord.match_UTM(true);
        xcoord.match_MGRS(true);
        xcoord.match_DD(true);
        xcoord.match_DMS(true);
        xcoord.match_DM(true);

        try {

            String _file = coordfile.trim();
            String fname = FilenameUtils.getBaseName(_file);
            TestUtility tester = new TestUtility("./results/xcoord_" + fname + "-rows.csv");
            // 
            tester.full_report = false;

            CsvReader in = open(coordfile);
            String line = null;
            String text = null;
            int linenum = 0;

            // id, enumeration, test, true_lat, true_lon, remark
            while (in.readRecord()) {

                line = in.get(0);
                //log.info(Arrays.toString(in.getValues()));
                //++linenum;
                text = line.trim();
                if (text.startsWith("#")) {
                    continue;
                }
                if (text.isEmpty()) {
                    continue;
                }

                String patid = line;
                String fam = find_family(patid);
                int famx = XConstants.get_CCE_family(fam);

                if (famx == XConstants.UNK_PATTERN) {
                    log.error("Unknown test pattern TEXT=" + text);
                    continue;
                }

                linenum = Integer.parseInt(in.get(1));
                text = in.get(2);
                text = text.replace("$NL", "\n");
                String rmks = in.get(5);

                // "Patid # rowid" == test instance id
                // DMS07#12  -- 12th example of DMS07 test.
                // 
                TestCase tst = new TestCase(patid + "#" + linenum, fam, text);
                tst.match.setLatitude(in.get(3));
                tst.match.setLongitude(in.get(4));
                tst.setRemarks(rmks);

                TextMatchResultSet results = xcoord.extract_coordinates(tst.text, tst.id);
                /**
                 * tst.family_id
                 */
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========FILE TEST " + tst.id + " FOUND:" + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
                tester.save_result(tst, results);
            }


            tester.close_report();

            log.info("=== FILE TESTS DONE ===");

        } catch (Exception err) {
            log.error("TEST BY LINES", err);
        }


    }

    private String find_family(String line) {
        char ch = line.charAt(0);

        if (!(ch == 'M' || ch == 'U' || ch == 'D')) {
            return null;
        }

        for (String fam : XConstants.familyInt.keySet()) {

            if (line.startsWith("DMS")) {
                return "DMS";
            }
            if (line.startsWith(fam)) {
                return fam;
            }

        }
        return null;
    }

    /**
     * Use for limited developmen testing.
     */
    protected void focusedTests() {

        log.info("=== ADHOC TESTS ===");

        log.info("Trying some specific DD tests now:\n=========================");
        xcoord.match_DD(false);
        xcoord.match_DMS(false);
        xcoord.match_DM(false);
        xcoord.match_MGRS(false);
        xcoord.match_UTM(false);
        TextMatchResultSet results = null;
        // = xcoord.extract_coordinates("text before " + "17S 699999 3335554" + " and after", "UTM");

        boolean dd = false;
        boolean dms = false;
        boolean dm = false;
        boolean mgrs = true;
        // 
        xcoord.match_MGRS(mgrs);

        String[] mgrstest = {
            "4 jul 2008",
            "10 Jan 1994", // edge case, bare minimum.                
            "10 Jan 13", // edge case, bare minimum.                
            "10 Jan 94", // no, this is the real bare minimum.
            "38SMB 461136560",
            "38SMB 461103656",
            "38SMB 46110 3656",
            "38SMB 4611 03656", // 0-padded Northing/Easting?  7 4 or 0007 0004
            "38SMB 46110365 60",
            "38SMB 46110365\n60", // even, but whitespace
            "38SMB 4611035\n60", // odd, and whitespace
            "38 SMB 4611 3656",
            "42 RPR 4611 3656",
            "10 Jan 2005 02", // MGRS 01, 10JAN 200502
            "10 Jan 1995 02"
        };

        xcoord.match_DD(dd);
        String[] ddtest = {
            "15S5E",
            "TARGET [1]  LATITUDE: +32.3345  LONGITUDE: -179.3412", //DD04
            "TARGET [1]  LATITUDE= +32.3345  LONGITUDE= -179.3412", //DD04
            "42.3N; 102.4W",
            "42.3 N; 102.4 W",
            "23.34N 88.22E",
            //"34.00N 44E", // Expected to fail as this matches DD06 -- 44E anchors this as a straight degree pattern.
            "N32.3345:W179.3412", // DD01
            "+32.3345:-179.3412", // DD03
            " 32.3345:-179.3412", // DD03
            " 32.3345°;-179.3412°", // DD03
            "032.3345°;-179.3412°",// DD03  leading 0 on lat; 
            "N32.3345:W179.3412", // DD01
            "032.3345°N;-179.3412°W",// DD03  leading 0 on lat; 
            "N32.3345:E179.3412", // DD01
            "32.3345N/179.3412E", // DD02
            "32.33N 179.34E" // DD02
        };

        xcoord.match_DMS(dms);
        xcoord.match_DM(dm);
        String[] dmtest = {
            "15S5E",
            //"01-02-03-04 005-06-07-08",
            "12 kts; B 14:28N - 053:00E",
            " 79.22.333N, 100.22.333W",
            " N 01° 44' E 101° 22'",
            "+42 18.0 x -102 24.0",
            "42 DEG 18.0N 102 DEG 24.0W",
            "#TEST   DM      01b      01DEG 44 N 101DEG 44 E",
            "03bv  4218N 10224W",
            "03bv      42°18'N 102°24'W",
            "03bv      42° 18'N 102° 24'W",
            "N 01° 44' E 101° 22'",
            "1122N-00 11122W-00",
            "01DEG 44N 101DEG 44E",
            "42 9-00 N 102 6-00W",
            "N42 18-00 x W102 24-00",
            "N01° 44' 55.5\" E101° 22' 33.0\"",
            "N 01° 44' 55\" E 101° 22'33.0\"",
            "33-04-05 12:11:10",
            "31°24' 70°21'",
            "40°55'23.2\" 9°43'51.1\"", // No HEMI
            "-40°55'23.2\" +9°43'51.1\"", // with HEMI
            "42 9-00 N 102 6-00W;           ",
            "42 18-009 N 102 24-009W;        ",
            "08°29.067' 13°14.067'", // No HEMI
            "08°29.067'N 13°14.067'W",
            "08°29.067'N 113°14.067'W",
            "40°55'23.2\"N 9°43'51\"E",
            "42° 18' 00\" 102° 24' 00",
            "(42° 18' 00\" 102° 24' 00",
            "01° 44' 55.5\" 101° 22' 33.0\"",
            "77°55'33.22\"N 127°33'22.11\"W",
            "40:26:46.123N,79:56:55.000W",
            "43-04-30.2720N 073-34-58.4170W",
            "31 53 45.55N 54 16 38.99E",
            "42.18.009N x 102.24.003W",
            "42.18.009N 102.24.003W",
            "42.18.009 N x 102.24.003 W",
            "014455666N1012233444E",
            "N7922333W10022333",
            "01°44'55.5\"N 101°22'33.0\"E;",
            "N01°44'55.5\" E101°22'33.0\"",
            "4025131234N 12015191234W",
            "5113N 00425E",
            "27° 37' 45’’N, 82° 42' 10’’W", // original
            "27° 37' 45’N, 82° 42' 10’W", // single second hash sym
            "27° 37' 45’’N 82° 42' 10’’W", // no lat/lon sep
            "27° 37 45N, 82° 42 10W" // no min hash.
        };

        int count = 0;

        List<String> tests = new ArrayList<>();
        if (dd) {
            tests.addAll(Arrays.asList(ddtest));
        }
        if (dms | dm) {
            tests.addAll(Arrays.asList(dmtest));
        }
        if (mgrs) {
            tests.addAll(Arrays.asList(mgrstest));
        }

        for (String testcase : tests) {
            ++count;
            String test_id = "" + count;
            results = xcoord.extract_coordinates("text before " + testcase + " and after", test_id);
            log.info("TEST (" + count + ") " + testcase + " FOUND:" + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
            if (results.matches != null) {
                for (TextMatch m : results.matches) {
                    log.info("\t" + m.toString());
                    GeocoordMatch g = (GeocoordMatch) m;
                    log.info("\t" + g.formatLatitude() + ", " + g.formatLongitude());
                }
            }
        }

        log.info(
                "=== ADHOC TESTS DONE ===");

    }
}
