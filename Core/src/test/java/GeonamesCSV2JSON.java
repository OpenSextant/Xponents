import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jodd.json.JsonObject;
import jodd.json.JsonSerializer;
import org.opensextant.util.FileUtility;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class GeonamesCSV2JSON extends GeonamesUtility {

    /**
     * Brute force copy of Geonames loadCountries method; Objective: read
     * geonames.org CSV file and convert to JSON. This sort of data is helpful for
     * integrating with apps and JavaScript tools.
     * 
     * @param outfile
     * @throws IOException
     */
    public GeonamesCSV2JSON(String outfile) throws IOException {

        JsonObject obj = new JsonObject();

        final String uri = "/geonames.org/countryInfo.txt";
        // 0-9
        // #ISO ISO3 ISO-Numeric fips Country Capital Area(in sq km) Population
        // Continent tld
        // 10-18
        // CurrencyCode CurrencyName Phone Postal Code Format Postal Code Regex
        // Languages geonameid neighbours EquivalentFipsCode
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(GeonamesUtility.class.getResourceAsStream(uri), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#")) {
                    continue;
                }
                String[] cells = line.split("\t");
                if (cells.length < 16) {
                    continue;
                }

                String langs = cells[15];
                if (isBlank(langs) || isBlank(cells[0])) {
                    continue;
                }
                String cc2 = cells[0].toUpperCase().trim();
                String cc3 = cells[1].toUpperCase().trim();
                String fips = cells[3].toUpperCase().trim();
                String name = cells[4].toUpperCase().trim();
                Integer pop = Integer.parseInt(cells[7]);
                List<String> langIDs = TextUtils.string2list(langs.toLowerCase(), ",");

                /*
                 * We want to produce JSON:
                 * 
                 * ISO3 key:{ name:NAME cc_iso3: CC cc_iso2: CC cc_fips: CC languages: [ ]
                 * population: NUM }
                 **/
                JsonObject jsonCountry = new JsonObject();
                jsonCountry.put("name", name);
                jsonCountry.put("cc_iso3", cc3);
                jsonCountry.put("cc_iso2", cc2);
                jsonCountry.put("cc_fips", fips);
                jsonCountry.put("population", pop);
                jsonCountry.put("languages", langIDs);

                obj.put(cc3, jsonCountry);
            }

            JsonSerializer writer = JsonSerializer.create();
            FileUtility.writeFile(writer.serialize(obj), outfile);

        } catch (Exception err) {
            throw new IOException("Did not find Country Metadata at " + uri, err);
        }
    }

    /**
     * 
     * @param args
     */
    public static void main(String[] args) {
        try {
            new GeonamesCSV2JSON(args[0]);
        } catch (Exception err) {
            System.err.println("GeonamesCSV2JSON  outputfile");
            err.printStackTrace();
        }
    }

}
