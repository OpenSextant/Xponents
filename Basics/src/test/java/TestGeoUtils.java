import static org.junit.Assert.*;

import org.junit.Test;
import java.io.IOException;
import org.opensextant.data.GeoBase;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;

/**
 * @author ubaldino
 *
 */
public class TestGeoUtils {

   @Test
   public void testResources() throws IOException{
       new GeonamesUtility();
   }

   @Test
    public void testGeodetics() {

        String test = null;
        // Test parsability
        try {
            test = "-34.0 78.9";
            LatLon xy = GeodeticUtility.parseLatLon(test);
            test = "-34.0, 78.9";
            xy = GeodeticUtility.parseLatLon(test);

            LatLon p2 = new Place(-35.2, 79.0);
            LatLon p3 = new Place(-35.2, -101.0);
            System.out.println("METERS from point to point " + xy + " to " + p2 + " = "
                    + GeodeticUtility.distanceMeters(xy, p2));
            System.out.println("METERS from point to point " + p2 + " to " + p3 + " = "
                    + GeodeticUtility.distanceMeters(p2, p3));
            System.out.println("METERS from point to point " + p2 + " to " + p3 + " = "
                    + GeodeticUtility.distanceMeters(p3, p2));
            System.out.println("METERS from point to point " + p2 + " to " + p3 + " = "
                    + GeodeticUtility.distanceMeters(p3, p3));
            
            // Great circle in meters...
            LatLon eq1 = new Place(0, 79.0);
            LatLon eq2 = new Place(0, -101.0);
            System.out.println("METERS from point to point " + eq1 + " to " + eq2 + " = "
                    + GeodeticUtility.distanceMeters(eq1, eq2));
            
            assert(true);

        } catch (Exception err) {
            fail("Could not parse");
            err.printStackTrace();
        }

        // Invalid numbers for LAT/LON
        try {
            test = "-34.0  278.9";
            LatLon xy = GeodeticUtility.parseLatLon(test);
        } catch (Exception err) {
            //err.printStackTrace();
            System.out.println("Pass: invalid coordinate, " + test + " fails to parse; ERR="
                    + err.getMessage());

            assert(true);
        }
    }
}
