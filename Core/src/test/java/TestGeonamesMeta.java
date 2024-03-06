import java.io.IOException;

import org.junit.Test;
import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.util.GeonamesUtility;
import static org.junit.Assert.*;

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
        assertNotNull(pl);
        pl = util.getCountry("PJ");
        assertNull(pl);
        pl = util.getCountryByFIPS("RU");
        assertNull(pl);
        pl = util.getCountryByFIPS("RS");
        assertNotNull(pl);
        pl = util.getCountryByFIPS("PJ");
        assertNotNull(pl);

        pl = util.getCountry("PSE");
        assertNotNull(pl);
        pl = util.getCountryByFIPS("WE");
        assertNotNull(pl);
        pl = util.getCountryByFIPS("PS");
        assertNotNull(pl);
        pl = util.getCountryByFIPS("GZ");
        assertNotNull(pl);

        // US, Outlying Minor islands, Howland Island
        pl = util.getCountry("US");
        assertNotNull(pl);
        pl = util.getCountry("UM");
        assertNotNull(pl);
        pl = util.getCountryByFIPS("HQ");
        assertNotNull(pl);
    }
    @Test
    public void testMetadata() throws IOException {
        GeonamesUtility util = new GeonamesUtility();
        assertFalse(util.getUSStateMetadata().isEmpty());

        print("+++++++ List US States");
        for (Place adm1 : util.getUSStateMetadata()) {
            print(String.format("%s (%s)", adm1.getPlaceName(), adm1.getAdmin1PostalCode()));
        }
    }

    @Test
    public void testWorldMetadata() throws IOException {
        GeonamesUtility util = new GeonamesUtility();
        util.loadWorldAdmin1Metadata();
        assertFalse(util.getWorldAdmin1Metadata().isEmpty());

        /* Print first 100; */
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
