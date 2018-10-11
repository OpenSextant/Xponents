package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

import gnu.getopt.LongOpt;

public class TestPlaceGeocoder extends TestGazMatcher {

    PlaceGeocoder geocoder = null;

    public TestPlaceGeocoder() {
    }

    public void configure() throws ConfigException {
        // INIT once.
        geocoder = new PlaceGeocoder(true);
        geocoder.enablePersonNameMatching(true);
        geocoder.setAllowLowerCaseAbbreviations(false);
        geocoder.configure();

    }

    public void tagFile(File f) {
        // Call as many times as you have documents...
        //
        try {
            List<TextMatch> matches = geocoder.extract(FileUtility.readFile(f, "UTF-8"));
            summarizeFindings(matches);
        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    public void tagFile(File f, String langid) throws IOException {
        // Call as many times as you have documents...
        //
        TextInput in = new TextInput("test", FileUtility.readFile(f, "UTF-8"));
        in.langid = langid;

        try {
            List<TextMatch> matches = geocoder.extract(in);
            summarizeFindings(matches);
        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    /**
     * try a series of known tests.
     * 
     * @throws IOException
     */
    public void tagEvaluation() throws IOException {

        Set<String> texts = FileUtility.loadDictionary("/data/placename-tests.txt", true);
        // Call as many times as you have documents...
        //
        try {
            for (String t : texts) {
                print("TEST:\t" + t + "\n=====================");
                List<TextMatch> matches = geocoder.extract(t);
                summarizeFindings(matches);
                print("\n");
            }
        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    public void cleanup() {
        // CALL only when you are done for good.
        geocoder.cleanup();
    }

    protected static void printHelp() {

        print("Options:");
        print("\t-s                 System unit-test evaluation tests");
        print("\t-f, --file FILE    path to file to process");
        print("\t-t, --text TEXT    text to process");
        print("\t-l, --lang LANG    lang id of text");
        print("\t-h, --help");
        System.exit(0);

    }

    /**
     * see TestGazMatcher documentation
     * 
     * @param args
     */
    public static void main(String[] args) {
        try {

            /** Sub-class can override options */
            LongOpt[] options = { new LongOpt("file", LongOpt.REQUIRED_ARGUMENT, null, 'f'),
                    new LongOpt("text", LongOpt.REQUIRED_ARGUMENT, null, 't'),
                    new LongOpt("lang", LongOpt.REQUIRED_ARGUMENT, null, 'l'),
                    new LongOpt("system-tests", LongOpt.NO_ARGUMENT, null, 's'),
                    new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h') };

            gnu.getopt.Getopt opts = new gnu.getopt.Getopt("PlaceGeocoder demo", args, "hsf:t:l:", options);

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
                case 'i':
                    file = opts.getOptarg();
                    break;
                case 't':
                    text = opts.getOptarg();
                    break;
                case 'l':
                    lang = opts.getOptarg();
                    break;
                case 'h':
                default:
                    printHelp();
                }
            }

            if (text == null && file == null && !doEval) {
                printHelp();
            }

            TestPlaceGeocoder tester = new TestPlaceGeocoder();
            try {
                tester.configure();
                if (doEval) {
                    tester.tagEvaluation();
                } else if (file != null) {
                    tester.tagFile(new File(file), lang);
                } else if (text != null) {
                    TextInput t = new TextInput("test", text);
                    t.langid = lang;
                    tester.tagText(t);
                }
            } catch (Exception err) {
                err.printStackTrace();
            }
            tester.cleanup();
            System.exit(0);

        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void tagText(TextInput t) throws ExtractionException {
        print("TEST:\t" + t + "\n=====================");
        List<TextMatch> matches = geocoder.extract(t);
        summarizeFindings(matches);
        print("\n");
    }
}
