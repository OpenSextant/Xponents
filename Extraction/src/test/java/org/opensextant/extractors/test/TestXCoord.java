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
package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FilenameUtils;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.xcoord.GeocoordTestCase;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xcoord.XConstants;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

/**
 *
 * @author ubaldino
 */
public class TestXCoord {

    static Logger log = LoggerFactory.getLogger(TestXCoordReporter.class);
    private XCoord xcoord = null;

    /**
     *
     * @param xc
     */
    public TestXCoord(XCoord xc) {
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
        TestXCoordReporter tester = null;
        String buffer = null;
        try {
            String _file = file.trim();
            buffer = FileUtility.readFile(_file);
            String fname = FilenameUtils.getBaseName(_file);
            tester = new TestXCoordReporter("./results/xcoord_" + fname + ".csv");
        } catch (IOException err) {
            log.error("Failed to open test file", err);
            return;
        }

        xcoord.enableAll();

        String jobid = TextUtils.text_id(buffer);

        log.info("Extract coordinates; All patterns enabled");
        try {
            TextMatchResult results = xcoord.extract_coordinates(buffer, jobid);
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

        RegexPatternManager mgr = xcoord.getPatternManager();

        log.info("\n\n=== SYSTEM TESTS ===\n\n");
        if (!mgr.testing) {
            log.info("TESTING OFF -- TURN ON DEBUG in LOG4J");
            return;
        }

        xcoord.match_UTM(true);
        xcoord.match_MGRS(true);
        xcoord.match_DD(true);
        xcoord.match_DMS(true);
        xcoord.match_DM(true);

        try {
            TestXCoordReporter tester = new TestXCoordReporter("./results/xcoord_System.csv");

            for (PatternTestCase tst : mgr.testcases) {
                TextMatchResult results = xcoord.extract_coordinates(tst.text, tst.id,
                        tst.family_id);
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========SYSTEM TEST " + tst.id + " FOUND:"
                        + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
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
            TestXCoordReporter tester = new TestXCoordReporter("./results/xcoord_" + fname
                    + "-lines.csv");

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

                GeocoordTestCase tst = new GeocoordTestCase("#" + in.getLineNumber(), fam, text);
                TextMatchResult results = xcoord.extract_coordinates(tst.text, tst.id);
                /**
                 * tst.family_id
                 */
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========FILE TEST " + tst.id + " FOUND:"
                        + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
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
    public CsvMapReader open(File file) throws IOException {

        InputStreamReader rdr = FileUtility.getInputStream(file.getAbsolutePath(), "UTF-8");
        CsvMapReader R = new CsvMapReader(rdr, CsvPreference.STANDARD_PREFERENCE);
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
    public void fileTruth(File coordfile) {

        xcoord.match_UTM(true);
        xcoord.match_MGRS(true);
        xcoord.match_DD(true);
        xcoord.match_DMS(true);
        xcoord.match_DM(true);

        try {

            //String _file = coordfile.trim();
            String fname = FilenameUtils.getBaseName(coordfile.getName());
            TestXCoordReporter tester = new TestXCoordReporter("./results/xcoord_" + fname
                    + "-rows.csv");
            //
            tester.full_report = false;

            CsvMapReader in = open(coordfile);
            String text = null;
            int linenum = 0;

            String[] columns = in.getHeader(true);
            Map<String, String> testRow = null;
            // id, enumeration, test, true_lat, true_lon, remark
            while ((testRow = in.read(columns)) != null) {

                String patid = testRow.get("id");
                if (patid == null) {
                    continue;
                }

                patid = patid.trim();
                if (patid.startsWith("#")) {
                    continue;
                }
                if (patid.isEmpty()) {
                    continue;
                }

                String fam = find_family(patid);
                int famx = XConstants.get_CCE_family(fam);

                if (famx == XConstants.UNK_PATTERN) {
                    log.error("Unknown test pattern TEXT=" + text);
                    continue;
                }

                text = testRow.get("enumeration");
                linenum = Integer.parseInt(text);

                text = testRow.get("test");
                text = text.replace("$NL", "\n");

                String rmks = testRow.get("remark");

                // "Patid # rowid" == test instance id
                // DMS07#12  -- 12th example of DMS07 test.
                //
                GeocoordTestCase tst = new GeocoordTestCase(patid + "#" + linenum, fam, text);
                tst.match.setLatitude(testRow.get("true_lat"));
                tst.match.setLongitude(testRow.get("true_lon"));
                tst.setRemarks(rmks);

                TextMatchResult results = xcoord.extract_coordinates(tst.text, tst.id);
                /**
                 * tst.family_id
                 */
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========FILE TEST " + tst.id + " FOUND:"
                        + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
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
        TextMatchResult results = null;
        // = xcoord.extract_coordinates("text before " + "17S 699999 3335554" + " and after", "UTM");

        boolean dd = false;
        boolean dms = false;
        boolean dm = false;
        boolean mgrs = true;
        boolean utm = false;
        //
        xcoord.match_MGRS(mgrs);

        String[] mgrstest = {
                "7MAR13 1600", 
                "17MAR13 1600", 
                "17MAR13 2014", 
                "17MAY13 2014", 
                "17JUN13 2014", 
                "17JUL13 2014", 
                "17SEP13 2014", 
                "17OCT13 2014", 
                "17NOV13 2014", 
                "17DEC13 2014", 
                "17APR13 2014", 
                "17AUG13 2014", 
                "17JAN13 2014", 
                "7JAN13 2001", 
                "17 JAN 13 2014", 
                "7 JAN 13 2001", 
                "04RAA80099\n\t1", // Fail -- too much whitespace.
                "12FTF82711", "15 EST 2008", "14 MRE\n\n 1445", "4 jul 2008", "10 Jan 1994", // edge case, bare minimum.
                "10 Jan 13", // edge case, bare minimum.
                "10 Jan 94", // no, this is the real bare minimum.
                "38SMB 461136560", "38SMB 461103656", "38SMB 46110 3656", "38SMB 4611 03656", // 0-padded Northing/Easting?  7 4 or 0007 0004
                "38SMB 46110365 60", "38SMB 46110365\n60", // even, but whitespace
                "38SMB 4611035\n60", // odd, and whitespace
                "38 SMB 4611 3656", "42 RPR 4611 3656", "10 Jan 2005 02", // MGRS 01, 10JAN 200502
                "10 Jan 1995 02" };

        xcoord.match_DD(dd);
        String[] ddtest = { "1.718114°  44.699603°", "N34.445566° W078.112233°","00 N 130 WA",
                "xxxxxxxxxxxxx-385331-17004121.1466dc9989b3545553c65ef91c14c0f3yyyyyyyyyyyyyyyyyyy",
                "-385331-17004121", "CAN-385331-17004121", "15S5E",
                "TARGET [1]  LATITUDE: +32.3345  LONGITUDE: -179.3412", //DD04
                "TARGET [1]  LATITUDE= +32.3345  LONGITUDE= -179.3412", //DD04
                "42.3N; 102.4W", "42.3 N; 102.4 W", "23.34N 88.22E",
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
                "xxxxxxxxxxxxx-385331-17004121.1466dc9989b3545553c65ef91c14c0f3yyyyyyyyyyyyyyyyyyy",
                "-385331-17004121",
                "41º58'46\"N, 87º54'20\"W ",
                "Latitude: 41º58'46\"N, Longitude: 87º54'20\"W ",
                "15S5E",
                //"01-02-03-04 005-06-07-08",           
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
                "08°29.067'N 13°14.067'W", "08°29.067'N 113°14.067'W", "40°55'23.2\"N 9°43'51\"E",
                "42° 18' 00\" 102° 24' 00", "(42° 18' 00\" 102° 24' 00", "01° 44' 55.5\" 101° 22' 33.0\"",
                "77°55'33.22\"N 127°33'22.11\"W", "40:26:46.123N,79:56:55.000W", "43-04-30.2720N 073-34-58.4170W",
                "31 53 45.55N 54 16 38.99E", "42.18.009N x 102.24.003W", "42.18.009N 102.24.003W",
                "42.18.009 N x 102.24.003 W", "014455666N1012233444E", "N7922333W10022333",
                "01°44'55.5\"N 101°22'33.0\"E;", "N01°44'55.5\" E101°22'33.0\"", "4025131234N 12015191234W",
                "5113N 00425E", "27° 37' 45’’N, 82° 42' 10’’W", // original
                "27° 37' 45’N, 82° 42' 10’W", // single second hash sym
                "27° 37' 45’’N 82° 42' 10’’W", // no lat/lon sep
                "27° 37 45N, 82° 42 10W" // no min hash.
        };

        String[] utm_tests = { "12\n\t\nX\t\n245070175", "12\n\nX\n266070175", "12 X 266070175", "12X 266070 175" };

        xcoord.match_UTM(utm);
        int count = 0;

        List<String> tests = new ArrayList<String>();
        if (utm) {
            tests.addAll(Arrays.asList(utm_tests));
        }
        if (dd) {
            tests.addAll(Arrays.asList(ddtest));
        }
        if (dms || dm) {
            tests.addAll(Arrays.asList(dmtest));
        }
        if (mgrs) {
            tests.addAll(Arrays.asList(mgrstest));
        }

        for (String testcase : tests) {
            ++count;
            String test_id = "" + count;
            results = xcoord.extract_coordinates("text before " + testcase + " and after", test_id);
            log.info("TEST (" + count + ") " + testcase + " FOUND:"
                    + (results.matches.isEmpty() ? "NOTHING" : results.matches.size()));
            if (results.matches != null) {
                for (TextMatch m : results.matches) {
                    log.info("\t" + m.toString());
                    GeocoordMatch g = (GeocoordMatch) m;
                    log.info("\t" + g.formatLatitude() + ", " + g.formatLongitude());
                }
            }
        }

        log.info("=== ADHOC TESTS DONE ===");

    }

    /**
     * Run a simple test.
     * TODO: Move Main program to Examples or other test area.
     * 
     * @param args
     *            only one argument accepted: a text file input.
     */
    public static void main(String[] args) {
        boolean debug = true;

        // Use default config file.
        XCoord xc = new XCoord(debug);
        XCoord.RUNTIME_FLAGS = XConstants.FLAG_EXTRACT_CONTEXT | XConstants.MGRS_FILTERS_ON
                | XConstants.CONTEXT_FILTERS_ON;

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("TestXCoord", args, "aft:u:");

        try {
            // xc.configure( "file:./etc/test_regex.cfg"); // default
            xc.configure(); // default
            TestXCoord test = new TestXCoord(xc);

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'f':
                    System.out.println("\tSYSTEM TESTS=======\n" + opts.getOptarg());
                    test.systemTests();

                    // Truth source is at src/test/resources  -- Or anywhere in your runtime classpath at TOP LEVEL!
                    //
                    URL truthData = XCoord.class.getResource("/Coord_Patterns_Truth.csv");
                    test.fileTruth(new File(truthData.getPath()));
                    break;

                case 't':
                    System.out.println("\tUSER TEST\n=======\n" + opts.getOptarg());
                    test.fileTestByLines(opts.getOptarg());
                    break;

                case 'u':
                    System.out.println("\tUSER FILE\n=======\n" + opts.getOptarg());
                    test.fileTests(opts.getOptarg());
                    break;

                case 'a':
                    System.out.println("\tAdhoc Tests\n=======\n" + opts.getOptarg());
                    test.focusedTests();
                    break;

                default:
                }
            }
        } catch (Exception xerr) {
            xerr.printStackTrace();
        }
    }

}
