import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.PostalGeocoder;
import org.opensextant.extractors.test.TestGazMatcher;

import java.util.List;

public class PostalGeocoderTester extends TestGazMatcher {

    PlaceGeocoder preTagger = null;
    public PostalGeocoderTester() throws ConfigException {
        geocoder = new PostalGeocoder();
        geocoder.configure();

        preTagger = new PlaceGeocoder();
        preTagger.configure();
    }

    protected void tagText(TextInput t) throws ExtractionException {
        print("TEST:\t" + t.buffer + "\n=====================");
        // Step 0.  You can stop here and return postal matches if you want.
        List<TextMatch> postalMatches = geocoder.extract(t);

        // Step 1. Fully tag text to find related geography and associate City + Postal.
        //         NOTE: this assumes that the postal tagger is incomplete and any pre-selected geocoding is tossed away.
        List<TextMatch> matches = preTagger.extract(t);
        PostalGeocoder.associateMatches(matches, postalMatches);

        //  Step 2. (Only if Step 1 occurred) Generate new spans with the finest location chosen.
        //       "derivedMatches" here is a super set of the originals and any derivations.
        List<TextMatch> derivedMatches = PostalGeocoder.deriveMatches(postalMatches, t);

        // Emit just postal matches here -- to not overwhelm output.
        // But in general the super set of matches is output.
        summarizeFindings(derivedMatches);

        print(" ** Only Postal Geotags were emitted. **\n");
    }


    public static void main(String[] args) {
        PostalGeocoderTester tester = null;
        try {
            tester = new PostalGeocoderTester();
            tester.parseOptions(args);
            if (tester.params.inputFile != null && tester.params.inputFile.endsWith(".json")) {
                tester.tagBatch(tester.params.inputFile);
            } else {
                tester.tagText(tester.inputText);
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
        if (tester!=null) {
            tester.geocoder.cleanup();
            tester.preTagger.cleanup();
        }
        System.exit(0);
    }
}
