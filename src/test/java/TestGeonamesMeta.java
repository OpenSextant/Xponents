import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;
import org.opensextant.data.Place;
import org.opensextant.util.GeonamesUtility;

public class TestGeonamesMeta {

    private static void print(String m) {
        System.out.println(m);
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
            
            assertTrue(util.getAdmin1Place("US", "06").getPlaceName().equals("California"));
            
            Place p = util.getAdmin1Place("CI", "78");
            print("Is Montagnes? " + p);
            assertTrue(p.getPlaceID().equals("NGA11153058"));

        } catch (Exception err) {
            fail("Oops: " + err.getMessage());
        }
    }
}
