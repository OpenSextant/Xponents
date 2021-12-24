package org.opensextant.extractors.test;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.extractors.geo.rules.NonsenseFilter;
import org.opensextant.util.TextUtils;

public class TestPlacePhonetics {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testWithDiacritics() {
        PlaceCandidate pc = new PlaceCandidate(-1, -1);
        pc.setText("ÄEÃ");
        pc.hasDiacritics = TextUtils.hasDiacritics(pc.getText());

        /*
         * Create a simple set of names that would likely match via tagger but should
         * not pass due to length and odd diacritics.
         */
        String[] names = { /* "ÄEÃ" */ "aea", "Aeå" };
        for (String n : names) {
            ScoredPlace geoScore = new ScoredPlace("nothing" + n, n);
            Place geo = geoScore.getPlace();
            geo.setId_bias(50);
            geo.setFeatureClass("P");
            geo.setFeatureCode("PPLX");
            pc.addPlace(geoScore);
        }
        NonsenseFilter filter = new NonsenseFilter();
        filter.assessPhoneticMatch(pc);
        /*
         * Is Filtered out because no good match appears in candidate's geo list
         */
        assertTrue(pc.isFilteredOut());

        // Reset
        pc.getRules().clear();
        pc.getEvidence().clear();
        pc.setFilteredOut(false);

        /*
         * Uh...Example below has a location with the same exact name as the matched
         * candidate. No filtering should happen here.
         */
        String n = "ÄEÃ";
        ScoredPlace geoScore = new ScoredPlace("nothing" + n, n);
        Place geo = geoScore.getPlace();
        geo.setId_bias(50);
        geo.setFeatureClass("P");
        geo.setFeatureCode("PPLX");
        pc.addPlace(geoScore);
        filter.assessPhoneticMatch(pc);
        assertTrue(!pc.isFilteredOut());
        print("Place Match 'ÄEÃ' -- valid geo for candidate.");

    }

    @Test
    public void testASCII() {
        PlaceCandidate pc = new PlaceCandidate(-1, -1);
        pc.setText("OK");
        pc.hasDiacritics = TextUtils.hasDiacritics(pc.getText());

        /*
         * Create a simple set of names that would likely match via tagger but should
         * not pass due to length and odd diacritics.
         */
        String[] names = { "'OK", "øk", "ØK" };
        for (String n : names) {
            ScoredPlace geoScore = new ScoredPlace("nothing" + n, n);
            Place geo = geoScore.getPlace();
            geo.setId_bias(50);
            geo.setFeatureClass("P");
            geo.setFeatureCode("PPLX");
            pc.addPlace(geoScore);
        }
        NonsenseFilter filter = new NonsenseFilter();
        filter.assessPhoneticMatch(pc);
        /*
         * Is Filtered out because no good match appears in candidate's geo list
         */
        assertTrue(pc.isFilteredOut());

        // Reset
        pc.getRules().clear();
        pc.getEvidence().clear();
        pc.setFilteredOut(false);

        String n = "OK";
        ScoredPlace geoScore = new ScoredPlace("nothing" + n, n);
        Place geo = geoScore.getPlace();
        geo.setId_bias(50);
        geo.setFeatureClass("P");
        geo.setFeatureCode("PPLX");
        pc.addPlace(geoScore);
        filter.assessPhoneticMatch(pc);
        assertTrue(!pc.isFilteredOut());
        print("Place Match 'OK' -- valid geo for candidate.");
    }

    private final static void print(String msg) {
        System.out.println(msg);
    }
}
