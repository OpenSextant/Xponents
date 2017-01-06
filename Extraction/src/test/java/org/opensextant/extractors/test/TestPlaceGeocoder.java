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

public class TestPlaceGeocoder extends TestGazMatcher {

    PlaceGeocoder geocoder = null;

    public TestPlaceGeocoder() throws ConfigException {
        // INIT once.
        geocoder = new PlaceGeocoder(true);
        geocoder.enablePersonNameMatching(true);
        geocoder.configure();
    }

    public void tagFile(File f) {
        // Call as many times as you have documents...
        //
        try {
            List<TextMatch> matches = geocoder.extract(FileUtility.readFile(f,
                    "UTF-8"));
            summarizeFindings(matches);
        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    public void tagFile(File f, String langid) throws IOException {
        // Call as many times as you have documents...
        //
        TextInput in = new TextInput("test", FileUtility.readFile(f,
                "UTF-8"));
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

        Set<String> texts = FileUtility.loadDictionary("/placename-tests.txt", true);
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

    /**
     * see TestGazMatcher documentation
     * 
     * @param args
     */
    public static void main(String[] args) {
        try {
            TestPlaceGeocoder tester = new TestPlaceGeocoder();

            try {
                if (args.length == 1) {
                    tester.tagFile(new File(args[0]));
                } else if (args.length == 2) {
                    TextInput t = new TextInput("test", args[1]);
                    t.langid = args[0];
                    tester.tagText(t);
                } else if (args.length == 3) {
                    tester.tagFile(new File(args[2]), args[0]);
                } else {
                    tester.tagEvaluation();
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
