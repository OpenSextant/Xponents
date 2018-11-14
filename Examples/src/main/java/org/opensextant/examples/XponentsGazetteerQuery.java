package org.opensextant.examples;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
/* OpenSextant APIs */
import org.opensextant.ConfigException;
import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;
/* Logging */
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * <pre>
 * 
 * WARNING -- NOT END USER CODE
 * WARNING -- THIS IS A DEMONSTRATION OF API AND TECHNIQUE.
 * 
 * Demo of PlaceGeocoder and SolrGazetteer: parse and lookup names or snippets of text.
 * 
 * Class hiearchy: 
 *    SolrGazetteer     - core gazetteer functions. lookup by name, parameters, location.
 *    GazetteerMatcher  - tagging gazetteer entries in text blobs; Has an internal instance of SolrGazetteer. 
 *    + PlaceGeocoder   - rules and techniques for tagging, filtering, and geocoding locations in free text.  
 *         SEE: https://opensextant.github.io/Xponents/doc/Geocoder_Handbook.html
 *         
 * Guidance -- don't subclass items above, but use instances of them.
 * Always close() instance when done to release underlying Solr I/O, especially 
 * for the typical EmbeddedSolrClient usage which is the default here.
 * 
 * Consider the following API usages. Given string X, do F(X).
 * 
 *     X                        F(X)
 *     _______                  ________________
 *  *  NAME                ....  findPlaces(NAME), returns all possible LOCs for NAME.
 *  *  NAME, PROV, COUNTRY ....  parse fields, validate Country, findPlaces(PROV) giving ADM1 code, find NAME.
 *  *  TEXT NAME TEXT NAME ....  geotag names in free text.  extract(TEXT) geocodes and filters entities.
 *  * 
 *  *  COORD               ....  parse coordinate, placesAt(COORD) lists places near COORD.
 *  *  NON-ENGLISH         ....  findPlacesRomanizedNameOf(NON-ENGLISH) lists Romanized version of a name.
 *  * 
 *  </pre> 
 * @author ubaldino
 *
 */
public class XponentsGazetteerQuery extends ExampleMain {

    // Full out extraction
    protected PlaceGeocoder extractor = null;
    // Instance of the gazetteer -- world-wide gazetteer of 18 million entries.
    protected SolrGazetteer gazetteer = null;
    // Geonames Utility - tables of Countries (names, languages, TZ), Province official names+codes.
    protected GeonamesUtility util = null;
    protected Map<String, Country> allCountries = null;

    protected Logger logger = LoggerFactory.getLogger(XponentsGazetteerQuery.class);

    public XponentsGazetteerQuery(){
    }
    
    public void configure() throws ConfigException, IOException {
        util = new GeonamesUtility();
        gazetteer = new SolrGazetteer();
        allCountries = gazetteer.getCountries();
    }

    public void demo(String... args) throws ParseException {

        CommandLineParser parser = new DefaultParser();
        Options opts = new Options();
        opts.addOption(Option.builder("l").longOpt("lookup").hasArg().desc("Phrase to lookup (required)").build());
        opts.addOption(Option.builder("p").longOpt("parse").desc("Parse first.").build());
        opts.addOption(Option.builder("g").longOpt("geotag").desc("Geotag as unstructured text").build());
        opts.addOption(Option.builder("h").longOpt("help").build());

        HelpFormatter cliHelp = new HelpFormatter();

        String appName = XponentsGazetteerQuery.class.getName();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(opts, args);
        } catch (ParseException parseErr) {
            cliHelp.printHelp(appName, "_______", opts, "_________", true);
            return;
        }

        if (cmd.hasOption('h')) {
            cliHelp.printHelp(appName, "_______", opts, "_________", true);
            return;
        }

        /**
         * Can honestly say Commons CLI is not much of an improvement over GetOpt.
         */
        if (!cmd.hasOption("l")) {
            print("--lookup NAME is required.\n");
            cliHelp.printHelp(appName, "_______", opts, "_________", true);
            return;
        }

        try {
            configure();
        }  catch (Exception err){
            error(err, "Something went wrong.");
            return;
        }

        String nm = cmd.getOptionValue("l");

        if (cmd.hasOption("parse")) {
            // PARSE 
            print("NAME parsed, then various lookups");
            print("=============================");
            try {
                parseThenLookup(nm);
            } catch (Exception err) {
                error(err, "\tFailure");
            }
        } else if (cmd.hasOption("geotag")) {
            // GEOTAG FREE TEXT
            print("TEXT BLOB geotagged and all findings listed.");
            print("=============================");
            try {
                extraction(nm);
            } catch (Exception err) {
                error(err, "\tFailure");
            }
        } else {
            // LOOKUP AS-IS.
            print("NAME straight lookup: '%s'", nm);
            print("=============================");
            try {
                nameLookup(nm);
            } catch (Exception err) {
                error(err, "\tFailure");
            }
        }
    }

    /**
     * Parse a pattern, then Lookup slots in a parametric fashion.
     * @param nm
     * @throws ExtractionException
     */
    public void parseThenLookup(String nm) throws ExtractionException {
        /*
         * Is this 
         *     NAME, PROV
         *           PROV, COUNTRY
         *     NAME,       COUNTRY
         *     NAME, PROV, COUNTRY
         *     
         *     Is PROV code, abbrev or name?
         *     Ditto, is Country code, abbrev, or name?
         *     
         *     JUST supporting the last one for demonstration purposes.
         *     
         *  Gazetteer Object classes:
         *      NAME  maps to Place object, any entry, but mostly feat_class:P or feat_class:A
         *      PROV  maps to Place object, feat_class:A.  Resulting Place.getAdmin1() code is important
         *            to use in subsequent queries
         *      COUNTRY  maps to Country object;  
         *            2000 listings in the gazetteer come from SolrGazetteer.getCountries(); lookup by native lang name.
         *            250 official listings come from GeonamesUtility methods to lookup country by code.
         */
        List<String> abc = TextUtils.string2list(nm, ",");
        if (abc.size() != 3) {
            print("Lookup for parsing is NAME, PROV, CC");
            return;
        }

        Country C = null;
        Place prov = null;
        List<Place> cities = null;

        /*
         * Techniques for rendering a string to a country object.
         */
        String countryValue = abc.get(2).toUpperCase();
        // By code
        C = util.getCountryByAnyCode(countryValue);
        if (C == null) {
            // By any name in any language
            C = allCountries.get(countryValue);
        }

        if (C == null) {
            print("Country not found");
            return;
        }

        /* Techniques for Administrative names
         * 
         */
        String provValue = abc.get(1);

        // Find the data for the administrative place in that country.
        // 
        String parametricQuery = String.format("feat_class:A AND cc:%s", C.getCountryCode());
        List<Place> provPlaces = gazetteer.findPlaces(provValue, parametricQuery, 1);
        if (provPlaces.isEmpty()) {
            print("\tNo such province '%s'", provValue);
            return;
        }

        // A Guess!!!  Just using the first item in the list. 
        prov = provPlaces.get(0);
        String cityValue = abc.get(0);
        // 
        parametricQuery = String.format("feat_class:P AND cc:%s AND adm1:%s", C.getCountryCode(), prov.getAdmin1());
        cities = gazetteer.findPlaces(cityValue, parametricQuery, 2);

        if (cities == null) {
            print("\tNo such province '%s'", cityValue);
            return;
        }
        for (Place p : cities) {
            print("\t%s @ (%s)", p.toString(), GeodeticUtility.formatLatLon(p));
        }

    }

    public void extraction(String text) throws ExtractionException, ConfigException {

        /*
         * Additional configuration you should do once.
         * If you use this, then use the internal SolrGazetteer, instead of creating a separate one.
         * 
         */
        extractor = new PlaceGeocoder();
        extractor.configure();
        gazetteer = extractor.getGazetteer();

        TextInput item = new TextInput("doc id", text);
        item.langid = "en";

        List<TextMatch> findings = extractor.extract(item);
        for (TextMatch m : findings) {
            if (m instanceof PlaceCandidate) {
                /* Work with geotag */
                PlaceCandidate geotag = (PlaceCandidate) m;
                if (geotag.getChosen() != null) {
                    print("\t%s, Geocoded to %s", m.getText(), geotag.getChosen().toString());
                } else {
                    print("\t%s, Rules %s", m.getText(), geotag.getRules().toString());
                }
            } else {
                print("\t%s, Not a place. Type=%s", m.getText(), m.getType());
            }
        }
    }

    public void nameLookup(String nm) throws ExtractionException {
        /*
         *  two options:  findPlaces()
         *                findPlacesRomanizedNameOf()  return only ASCII versions of place names.
         */
        List<Place> places = gazetteer.findPlaces(nm, "+feat_class:(P A)", 2);
        if (places.isEmpty()) {
            print("\tNothing found");
            return;
        }
        print("\tfirst 10...");
        for (Place p : places.subList(0, places.size() > 10 ? 10 : places.size() - 1)) {
            print("\t%s", p.toString());
        }

    }

    public static void main(String[] args) {
        XponentsGazetteerQuery demo = null;

        /** 
         * Initialize resources, 
         */
        try {
            demo = new XponentsGazetteerQuery();
        } catch (Exception err) {
            err.printStackTrace();
            System.exit(1);
        }

        /*
         * Process some data.
         */
        try {
            demo.demo(args);
        } catch (Exception err) {
            err.printStackTrace();
        }

        /*
         *  Ensure you close resources, regardless of prior error conditions.
         */
        if (demo.gazetteer != null) {
            demo.gazetteer.close();
        }
        if (demo.extractor != null) {
            demo.extractor.close();
        }

        /*
         * Annoying: Solr client/server thread hangs on if you do not explicitly hit an System Exit condition.
         */
        System.exit(0);
    }
}
