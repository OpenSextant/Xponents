package org.opensextant.extractors.test;

import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestXTemporal {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public TestXTemporal() {
    }

    /**
     * @param file
     */
    public void test(String file) {
        systemTests();
    }

    public void userTest(String text) {

        log.info("=== INTERACTIVE TESTS START ===");
        log.info("INPUT:\n" + text);
        xdt.enableAll();

        List<TextMatch> results = xdt.extract(text);
        // results.add_trace("Test Payload: " + text);

        if (results.isEmpty()) {
            log.info("Nada.");
        } else {
            log.info("OUTPUT: " + results.size() + " FOUND.");
            for (TextMatch m : results) {
                log.info(m.toString() + " valid return " + !m.isFilteredOut());
            }
        }
        log.info("=== INTERACTIVE TEST DONE ===");

    }

    /**
     */
    public void adhocTests() {
        log.info("=== ADHOC TESTS START ===");
        xdt.enableAll();

        // xdt.match_MonDayYear(true);
        // xdt.match_DateTime(false);

        String[] tests = { "12 July 2019", "12JULIO2002", "16DEC2020", "2010-04-13", "1111-11-11", "12/13/1900",
                "11/12/1817", "12/30/90", "JUN 00", "JUN '13", "JUN '12", "JUN '17", "JUN '33", "JUN 2017",
                "JUN 1917" };

        try {
            TestXTemporalReporter tester = new TestXTemporalReporter("./results/xtemp_Adhoc.csv");

            int count = 0;
            for (String tst_text : tests) {
                ++count;
                TextMatchResult results = xdt.extract_dates(tst_text, "" + count);
                results.add_trace("Test Payload: " + tst_text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========AHDOC TEST " + count + " FOUND:"
                        + (results.matches == null ? "NOTHING" : results.matches.size()));
                tester.save_result(results);

            }
            tester.close_report();

        } catch (Exception err) {
            log.error("Not finishing tests", err);
            return;
        }
        log.info("=== ADHOC TESTS DONE ===");

    }

    /**
     *
     */
    public void systemTests() {
        log.info("=== SYSTEM TESTS START ===");

        // Enable select patterns: disable all first, then enable pattern families.
        // xdt.disableAll();
        // xdt.match_MonDayYear(true);
        // xdt.match_DateTime(true);
        // xdt.match_MonDayYear(true);
        xdt.enableAll();

        try {
            TestXTemporalReporter tester = new TestXTemporalReporter("./results/xtemp_System.csv");
            for (PatternTestCase tst : xdt.getPatternManager().testcases) {
                TextMatchResult results = xdt.extract_dates(tst.text, tst.id);
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========SYSTEM TEST " + tst.id + " FOUND:"
                        + (results.matches == null ? "NOTHING" : results.matches.size()));
                tester.save_result(results, tst);

            }
            tester.close_report();

        } catch (Exception err) {
            log.error("Not finishing tests", err);
            return;
        }
        log.info("=== SYSTEM TESTS DONE ===");
    }

    /**
     *
     */
    public static void usage() {
        System.out.println(""
                + "\n\tXTemporal -f      -- run system tests."
                + "\n\tXTemporal -t FILE -- user tests using FILE"
                + "\n\tXTemporal -a      -- adhoc tests in debugger");
    }

    private static XTemporal xdt = null;

    /**
     * @param args
     */
    public static void main(String[] args) {

        boolean debug = true;
        // default test patterns, run test/debug mode.
        xdt = new XTemporal(debug);
        boolean systemTest = false;
        boolean adhocTest = false;
        String userText = null;

        try {
            gnu.getopt.Getopt opts = new gnu.getopt.Getopt("XTemporal", args, "fat:");
            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'f':
                    systemTest = true;
                    break;
                case 'a':
                    adhocTest = true;
                    break;
                case 't':
                    userText = opts.getOptarg();
                    break;
                default:
                    usage();
                    System.exit(1);
                }
            }
        } catch (Exception err) {
            usage();
            System.exit(1);
        }

        try {
            TestXTemporal test = new TestXTemporal();
            xdt.configure();

            if (systemTest) {
                System.out.println("\tSYSTEM TESTS=======\n");
                test.systemTests();
            }
            if (adhocTest) {
                System.out.println("\tADHOC TESTS=======\n");
                test.adhocTests();
            }
            if (userText != null) {
                test.userTest(userText);
            }
        } catch (ConfigException exErr) {
            exErr.printStackTrace();
        }
    }

}
