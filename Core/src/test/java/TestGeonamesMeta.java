import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;
import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.util.GeonamesUtility;

public class TestGeonamesMeta {

    private static void print(String m) {
        System.out.println(m);
    }

    @Test
    public void testCountryLoader() throws IOException {
        GeonamesUtility util = new GeonamesUtility();

        Country pl;
        // Country/Territory queries:
        pl = util.getCountry("RU");
        assertTrue(pl != null);
        pl = util.getCountry("PJ");
        assertTrue(pl == null);
        pl = util.getCountryByFIPS("RU");
        assertTrue(pl == null);
        pl = util.getCountryByFIPS("RS");
        assertTrue(pl != null);
        pl = util.getCountryByFIPS("PJ");
        assertTrue(pl != null);

        pl = util.getCountry("PSE");
        assertTrue(pl != null);
        pl = util.getCountryByFIPS("WE");
        assertTrue(pl != null);
        pl = util.getCountryByFIPS("PS");
        assertTrue(pl != null);
        pl = util.getCountryByFIPS("GZ");
        assertTrue(pl != null);

        // US, Outlying Minor islands, Howland Island
        pl = util.getCountry("US");
        assertTrue(pl != null);
        pl = util.getCountry("UM");
        assertTrue(pl != null);
        pl = util.getCountryByFIPS("HQ");
        assertTrue(pl != null);
    }
    @Test
    public void testMetadata() throws IOException {
        GeonamesUtility util = new GeonamesUtility();
        assertTrue(!util.getUSStateMetadata().isEmpty());

        print("+++++++ List US States");
        for (Place adm1 : util.getUSStateMetadata()) {
            print(String.format("%s (%s)", adm1.getPlaceName(), adm1.getAdmin1PostalCode()));
        }
    }

    @Test
    public void testWorldMetadata() throws IOException {
        GeonamesUtility util = new GeonamesUtility();
        util.loadWorldAdmin1Metadata();
        assertTrue(!util.getWorldAdmin1Metadata().isEmpty());

        /** Print first 100; */
        print("++++ 100 ADM1 places ++++");
        for (int x = 0; x < 100; ++x) {
            print(util.getWorldAdmin1Metadata().get(x).toString());
        }
        print(".....\n");

        print("+++++ Lookup by HASC ");
        try {
            print("Is California? " + util.getAdmin1Place("US", "CA"));
            print("Is California? " + util.getAdmin1Place("US", "06"));
            print("Is California? " + util.getAdmin1PlaceByHASC("US.06"));

            assertEquals("California", util.getAdmin1Place("US", "06").getPlaceName());

            Place p = util.getAdmin1Place("CI", "78");
            print("Is Montagnes? " + p);
            assertEquals("NGA11153058", p.getPlaceID());

        } catch (Exception err) {
            fail("Oops: " + err.getMessage());
        }
    }
}
