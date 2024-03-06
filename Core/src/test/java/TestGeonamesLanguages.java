import java.io.IOException;
import java.util.Collection;

import org.junit.Test;
import org.opensextant.util.GeonamesUtility;
import static org.junit.Assert.*;

public class TestGeonamesLanguages {

    private static void print(String m) {
        System.out.println(m);
    }

    @Test
    public void test() {
        try {
            GeonamesUtility util = new GeonamesUtility();
            // NOTE: caller has to invoke this language metadata load separately.
            // This metadata should be carried close to gazetteer, e.g.,
            // solr/gazetteer/conf/geonames.org/
            // with "....solr/gazetteer/conf/" path in CLASSPATH.
            //
            util.loadCountryLanguages();

            Collection<String> langs = util.languagesInCountry("RW");
            assertNotNull(langs);
            assertTrue(!langs.isEmpty());

            print(langs.toString());

            print("is French spoken in Rwanda? " + util.countrySpeaks("FR", "RW"));
            assertTrue(util.countrySpeaks("FR", "RW"));

            print("is French spoken in US? " + util.countrySpeaks("fr", "US"));
            assertTrue(util.countrySpeaks("fr", "US"));

            print("is RW French spoken in Rwanda? " + util.countrySpeaks("FR-RW", "RW"));
            print("Primary language of Rwanda? " + util.primaryLanguage("RW"));

            Collection<String> countries = util.countriesSpeaking("fr");
            print("Countries speaking French " + countries.toString());

            // Test island nations -- these entries in geonames.org data have fewer columns:
            // no neighbors column.
            //
            print("Spoken in Philippines - " + util.languagesInCountry("PH"));
            print("Unkonwn Language IDs " + util.unknownLanguages.toString());

            String[] primaryLangCountries = { "US", "AT", "RU", "ZA", "CN", "TW", "KR", "KP", "JP", "TH", "ID", "PH",
                    "MY", "FR", "ES", "DE", "CA", "MX", "SV", "SD", "SS", "EG" };

            for (String cc : primaryLangCountries) {
                print("Primary language of " + cc + "? " + util.primaryLanguage(cc));
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

}
