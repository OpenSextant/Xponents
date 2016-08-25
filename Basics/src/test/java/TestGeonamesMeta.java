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
        assertTrue(!util.getAdmin1Metadata().isEmpty());

        for (Place adm1 : util.getAdmin1Metadata()) {
            print(String.format("%s (%s)", adm1.getPlaceName(), adm1.getAdmin1PostalCode()));
        }
    }

}
