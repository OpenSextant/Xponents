package org.opensextant.extractors.test;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.TextInput;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.processing.Parameters;

public class TestReverseGeocoding  {

    public static void main(String[] args) {
        try {

            TestGazMatcher tester = new TestGazMatcher();
            tester.parseOptions(args);

            Parameters testParams = new Parameters();
            testParams.resolve_localities = true;
            testParams.tag_coordinates = true;
            PlaceGeocoder geocoderImpl = new PlaceGeocoder(true);
            geocoderImpl.setParameters(testParams);
            geocoderImpl.enablePersonNameMatching(true);
            geocoderImpl.setAllowLowerCaseAbbreviations(false);
            geocoderImpl.configure();

            tester.geocoder = geocoderImpl;

            try {
                tester.printMemory();
                int iterations = 1;
                int pause = 100; /* ms */

                if (tester.runSystemTests) {
                    iterations = 25;
                    tester.inputText.buffer =  StringUtils.join(new String[] { "20.000N, 10.000E", /* Sahara */
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
                    tester.tagText(tester.inputText);
                    tester.printMemory();
                }
                tester.dumpStats();

            } catch (Exception err) {
                err.printStackTrace();
            }
            tester.geocoder.cleanup();
            System.exit(0);

        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
