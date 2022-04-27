package org.opensextant.extractors.test;

import java.io.IOException;

import org.opensextant.ConfigException;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.extractors.poli.PatternsOfLife;

public class TestPoLi {

    public static void usage() {
        System.out.println(""
                + "\n\tPatternsOfLife  -f          -- run all system tests\n"
                + "\n\tPatternsOfLife  -u  <file>  -- run user tests on given text file."
                + "\n\tPatternsOfLife  -c  <file>  -- run tests in patterns config file."
                );
    }

    /**
     * Run a simple test.
     * 
     * @param args only one argument accepted: a text file input.
     */
    public static void main(String[] args) {
        boolean debug = true;

        boolean systemTest = false;
        String testFile = null;
        String config = null;
        try {
            gnu.getopt.Getopt opts = new gnu.getopt.Getopt("Poli", args, "c:u:f");

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'f':
                    System.out.println("\tSystem TESTS======= ");
                    systemTest = true;
                    break;

                case 'u':
                    testFile = opts.getOptarg();
                    System.out.println("\tUser TESTS======= FILE=" + testFile);
                    break;

                case 'c':
                    config = opts.getOptarg();
                    System.out.println("\tUser Patterns Configuration ======= FILE=" + config);
                    break;

                default:
                    TestPoLi.usage();
                    System.exit(1);
                }
            }
        } catch (Exception runErr) {
            runErr.printStackTrace();
            TestPoLi.usage();
            System.exit(1);
        }
        PatternsOfLife poli = null;

        try {
            // Use default config file.
            poli = new PatternsOfLife(debug);
            if (config == null) {
                poli.configure(); // default
            } else {
                poli.configure(config);
            }
        } catch (ConfigException xerr) {
            xerr.printStackTrace();
            System.exit(-1);
        }

        try {
            TestPoLiReporter test = new TestPoLiReporter(poli);
            if (systemTest) {
                test.test();
            } else if (testFile != null) {
                test.testUserFile(testFile);
            }
        } catch (NormalizationException | IOException xerr) {
            xerr.printStackTrace();
        }
    }
}
