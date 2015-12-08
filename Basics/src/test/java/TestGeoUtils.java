import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensextant.data.Country;
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
    public void testResources() throws IOException {
        GeonamesUtility util = new GeonamesUtility();
        // About 280 entries in CSV flat file.
        assert(util.getCountries().size() > 280);
        for (Country c : util.getCountries()) {
            print(String.format("%s, %s", c.getName(), c.getCountryCode()));
        }
        if (util.getCountryByAnyCode("IV") == null) {
            fail("IV - Cote D'Ivoire not found");
        }

        Country C = util.getCountry("USA");
        if (!C.containsUTCOffset(-5.0)) {
            fail("USA contains GMT-0500");
        }
        C = util.getCountry("JP");
        if (!C.containsUTCOffset(9.0)) {
            fail("Japan contains GMT+0900");
        }
    }

    @Test
    public void testCitiesPopulation() throws IOException {
        //GeonamesUtility.loadMajorCities(getZipContent(new File("./src/test/resources/cities15000.zip")));
        List<Place> cities = GeonamesUtility.loadMajorCities(new File("./src/test/resources/cities15000.txt"));

        print("Cities with pop = " + cities.size());
        int x = 0;
        for (Place p : cities) {
            print(String.format("(ID=%s) %s %d, %s", p.getPlaceID(), p, p.getPopulation(), p.getGeohash()));
            ++x;
            if (x > 100) {
                break;
            }
        }

        Map<String, Place> mapped = GeonamesUtility.mapMajorCityIDs(cities);
        print("Cities distinct, size=" + mapped.size());

        Map<String, Integer> popGrid = null;
        // RECOMMENDED: Use geohash resolution 5.
        popGrid = GeonamesUtility.mapPopulationByLocation(cities); 
        print("Population =" + popGrid.size());
        // This makes more sense for a regional accumulation of population geohash prefix = 4 or 3.
        popGrid = GeonamesUtility.mapPopulationByLocation(cities, 4);
        print("Population =" + popGrid.size());
        print("Grid = " +popGrid);
    }

    private void print(String m) {
        System.out.println(m);
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
            print("METERS from point to point " + xy + " to " + p2 + " = "
                    + GeodeticUtility.distanceMeters(xy, p2));
            print("METERS from point to point " + p2 + " to " + p3 + " = "
                    + GeodeticUtility.distanceMeters(p2, p3));
            print("METERS from point to point " + p2 + " to " + p3 + " = "
                    + GeodeticUtility.distanceMeters(p3, p2));
            print("METERS from point to point " + p2 + " to " + p3 + " = "
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
