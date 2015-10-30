package org.opensextant.extractors.test;

import java.io.File;
import java.util.List;

import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.util.FileUtility;

public class TestPlaceGeocoder extends TestGazMatcher {

    /**
     * see TestGazMatcher documentation
     *  
     * @param args
     */
    public static void main(String[] args) {
        try {
            // INIT once.
            PlaceGeocoder geocoder = new PlaceGeocoder();
            geocoder.enablePersonNameMatching(true);
            geocoder.configure();

            // Call as many times as you have documents...
            //
            try {
                List<TextMatch> matches = geocoder.extract(FileUtility.readFile(new File(args[0]),
                        "UTF-8"));
                summarizeFindings(matches);
            } catch (Exception procErr) {
                procErr.printStackTrace();
            }

            // CALL only when you are done for good.
            geocoder.cleanup();
            System.exit(0);

        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
