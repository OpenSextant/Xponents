package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.util.FileUtility;

public class TestPlaceGeocoder extends TestGazMatcher {

    PlaceGeocoder geocoder = null;

    public TestPlaceGeocoder() throws ConfigException {
        // INIT once.
        geocoder = new PlaceGeocoder();
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
                print("TEST:\t" + t+"\n=====================");
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
                if (args.length > 0) {
                    tester.tagFile(new File(args[0]));
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
}
