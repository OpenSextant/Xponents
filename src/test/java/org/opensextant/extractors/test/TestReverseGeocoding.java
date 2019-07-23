package org.opensextant.extractors.test;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.TextInput;
import org.opensextant.processing.RuntimeTools;
import org.opensextant.util.TextUtils;

import gnu.getopt.LongOpt;

public class TestReverseGeocoding extends TestPlaceGeocoder {

    public static void main(String[] args) {
        try {

            /** Sub-class can override options */
            LongOpt[] options = { new LongOpt("eval", LongOpt.NO_ARGUMENT, null, 's'),
                    new LongOpt("file", LongOpt.REQUIRED_ARGUMENT, null, 'f'),
                    new LongOpt("text", LongOpt.REQUIRED_ARGUMENT, null, 't'),
                    new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h') };

            gnu.getopt.Getopt opts = new gnu.getopt.Getopt("TestReverseGeo", args, "hsf:t:", options);

            String lang = TextUtils.englishLang;
            String text = null;
            String file = null;
            boolean doEval = false;

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {

                case 0:
                    // 0 = Long opt processed.
                    break;
                case 's':
                    doEval = true;
                    break;
                case 'f':
                    file = opts.getOptarg();
                    break;
                case 't':
                    text = opts.getOptarg();
                    break;
                case 'h':
                default:
                    printHelp();
                }
            }

            if (text == null && file == null && !doEval) {
                printHelp();
            }

            printMemory();
            TestReverseGeocoding tester = new TestReverseGeocoding();
            try {
                tester.configure();
                printMemory();
                int iterations = 1;
                int pause = 100; /* ms */

                if (doEval) {
                    iterations = 25;
                    text = StringUtils.join(new String[] { "20.000N, 10.000E", /* Sahara */
                            "26.14N, 33.52E", /* Egypt */
                            "32.000N, 101.668W", /* Texas */
                            "26.000N, 119.000E", /* China */
                            "42.30N, 71.011W", /* New England */
                            "42.00N, 68.111W", /* New England, water */
                            "42.00N, 75.111W", /* New England */
                            "38.00N, 80.111W", /* New York */
                    }, ";;");

                }

                for (int count = 0; count < iterations; ++count) {
                    if (file != null) {
                        tester.tagFile(new File(file), lang);
                    } else if (text != null) {
                        for (String testString : text.split(";;")) {
                            TextInput t = new TextInput("test", testString);
                            t.langid = lang;
                            tester.tagText(t);
                        }
                    }
                    printMemory();
                }
                dumpStats();

            } catch (Exception err) {
                err.printStackTrace();
            }
            tester.cleanup();
            System.exit(0);

        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    protected static void printHelp() {
        print("Options:");
        print("\t-s, --system       static system eval.");
        print("\t-f, --file FILE    path to file to process");
        print("\t-t, --text TEXT    text to process");
        print("\t-h, --help");
        System.exit(0);

    }

}
