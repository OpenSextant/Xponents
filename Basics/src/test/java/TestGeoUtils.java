import static org.junit.Assert.*;

import org.junit.Test;
import org.opensextant.data.LatLon;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;

/**
 * 
 */

/**
 * @author ubaldino
 *
 */
public class TestGeoUtils {

    //@Test
    public static void test() {

        String test = null;
        // Test parsability
        try {
            test = "-34.0 78.9";
            LatLon xy = GeodeticUtility.parseLatLon(test);
            test = "-34.0, 78.9";
            xy = GeodeticUtility.parseLatLon(test);
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
        }

        try {
            new GeonamesUtility();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public static void main(String[] args) {
        test();
    }

}
