package org.opensextant.extractors.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;

public class TestPlaceGeocoderGeoBoundaries extends TestGazMatcher {

    public TestPlaceGeocoderGeoBoundaries() throws ConfigException {
        super();
        geocoder = new PlaceGeocoder();
        geocoder.configure();
    }

    class TestPhrase {
        public String text = null;
        public int expected = 0;

        public TestPhrase(String t, int c) {
            text = t;
            expected = c;
        }
    }

    /**
     * Language-specific parsing will involve more testing... For now, just making
     * it available is enough.
     *
     * @throws IOException
     */
    public void tagEvaluation(String[] testText) throws IOException {

        /* TBD */
        String[] textsMiddleEastScripts = {};
        /* TBD */
        String[] textsCJK = {};
        TestPhrase[] textASCII = { new TestPhrase("Good Docter, MD", 0), new TestPhrase("Hey, Al xxx", 0),
                new TestPhrase("oh, Montgomery, Al?", 1), new TestPhrase("xyz co.", 0), new TestPhrase("xyz Co.", 0),
                new TestPhrase("Boise, ID", 1), new TestPhrase("Boise id", 1), new TestPhrase("CO", 0),
                new TestPhrase("Colo.", 0), new TestPhrase("COLO", 0), new TestPhrase("COLORADO", 1),
                new TestPhrase("Boulder &Co.", 1), new TestPhrase("Boulder,Co.", 1), new TestPhrase("Boulder Co.", 1),
                new TestPhrase("Boulder CO", 1), new TestPhrase("Boulder COLORADO", 1),
                new TestPhrase("Boulder, Colorado", 1), new TestPhrase("Boulder, CO", 1),
                new TestPhrase("Boulder, Colo.", 1), new TestPhrase("boulder, co", 1),
                new TestPhrase("Bohaaaa and Co.", 0),
                new TestPhrase("some text and Denver, CO and some more text.", 1) };

        List<TestPhrase> tests = new ArrayList<TestPhrase>();
        String langId = "en";
        if (testText != null && testText.length >= 2) {
            tests.add(new TestPhrase(testText[1], 0));
        } else {
            tests.addAll(Arrays.asList(textASCII));
        }

        try {
            for (TestPhrase t : tests) {
                print("%%%%%%%%%%%  %%%%%%%%%%%%%   %%%%%%%%%%%  %%%%%%%%%");
                print("TEST:\t" + t.text + "\n=====================");
                TextInput i = new TextInput("test", t.text);
                i.langid = langId;
                List<TextMatch> matches = geocoder.extract(i);
                summarizeFindings(matches, t.expected);
                print("\n");
            }
        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    public static void main(String[] args) {
        TestPlaceGeocoderGeoBoundaries tester = null;
        try {
            tester = new TestPlaceGeocoderGeoBoundaries();
            // args not used for now.
            tester.tagEvaluation(args);
        } catch (Exception err) {
            err.printStackTrace();
        }
        tester.geocoder.cleanup();

        System.exit(0);
    }

}
