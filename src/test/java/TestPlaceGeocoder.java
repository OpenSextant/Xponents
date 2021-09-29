import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.test.TestGazMatcher;
import org.opensextant.processing.Parameters;
import org.opensextant.util.FileUtility;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class TestPlaceGeocoder extends TestGazMatcher {


    public TestPlaceGeocoder() {
    }

    public void configure() throws ConfigException {
        // INIT once.
        Parameters testParams = new Parameters();
        testParams.resolve_localities = true;
        testParams.tag_coordinates = true;
        PlaceGeocoder geocoderImpl = new PlaceGeocoder(true);
        geocoderImpl.setParameters(testParams);
        geocoderImpl.enablePersonNameMatching(true);
        geocoderImpl.setAllowLowerCaseAbbreviations(false);
        geocoderImpl.configure();

        geocoder = geocoderImpl;
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
     * @throws URISyntaxException
     */
    public void tagEvaluation() throws IOException, URISyntaxException {

        File f = new File(TestPlaceGeocoder.class.getResource("/data/placename-tests.txt").toURI());
        String texts = FileUtility.readFile(f, "UTF-8");
        // Call as many times as you have documents...
        //
        try {
            for (String txt : texts.split("\n")) {
                if (txt.trim().isEmpty()) {
                    continue;
                }
                print("TEST:\t" + txt + "\n=====================");
                List<TextMatch> matches = geocoder.extract(txt);
                summarizeFindings(matches);
                print("\n");
            }
            TestGazMatcher.printMemory();

        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    public void cleanup() {
        // CALL only when you are done for good.
        if (geocoder != null) {
            geocoder.cleanup();
        }
    }

    /**
     * see TestGazMatcher documentation
     *
     * @param args
     */
    public static void main(String[] args) {
        try {

            TestPlaceGeocoder tester = new TestPlaceGeocoder();
            tester.parseOptions(args);

            try {
                tester.configure();
                if (tester.runSystemTests) {
                    tester.tagEvaluation();
                } else if (tester.inputText != null) {
                    tester.tagText(tester.inputText);
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
