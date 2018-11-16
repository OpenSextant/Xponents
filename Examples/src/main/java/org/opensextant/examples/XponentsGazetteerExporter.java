package org.opensextant.examples;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.SolrGazetteer;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.cellprocessor.FmtNumber;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

public class XponentsGazetteerExporter extends XponentsGazetteerQuery {

    private HashMap<String, Object> row = new HashMap<>();

    private ModifiableSolrParams queryParams = null;

    public void export(File f) throws IOException, SolrServerException {

        String[] header = { "lat", "lon", "name", "admin1", "admin2", "cc" };
        CellProcessor[] outputSchema = { new FmtNumber("0.00###"), new FmtNumber("0.00###"), new Optional(),
                new Optional(), new Optional(), new Optional() };

        try (FileWriter fio = new FileWriter(f)) {
            CsvMapWriter writer = new CsvMapWriter(fio, CsvPreference.EXCEL_PREFERENCE);
            writer.writeHeader(header);

            HashSet<String> done = new HashSet<>();
            for (String cc : this.allCountries.keySet()) {
                if (cc.length() != 2) {
                    continue;
                }

                if (done.contains(cc)) {
                    continue;
                }
                Map<String, Place> adm1forCountry = hashAdminPlaces("ADM1", cc);
                Map<String, Place> adm2forCountry = hashAdminPlaces("ADM2", cc);
                List<Place> places = listPopulatedPlaces(cc);

                print(String.format("%s FOUND %d, Admin Boundaries: %d,  %d ", cc, places.size(), adm1forCountry.size(),
                        adm2forCountry.size()));
                for (Place geo : places) {
                    writer.write(
                            placeDict(geo, adm1forCountry.get(geo.getAdmin1()), adm2forCountry.get(geo.getAdmin2())),
                            header, outputSchema);
                }
                done.add(cc);
                writer.flush();
            }
            writer.close();
        }

    }

    private Map<String, ?> placeDict(Place geo, Place adm1, Place adm2) {
        row.clear();
        Country C = allCountries.get(geo.getCountryCode());
        row.put("lat", geo.getLatitude());
        row.put("lon", geo.getLongitude());
        row.put("name", geo.getName());
        row.put("cc", (C != null ? C.CC_ISO2 : ""));
        row.put("admin1", (adm1 != null ? adm1.getName() : ""));
        row.put("admin2", (adm2 != null ? adm2.getName() : ""));
        // TODO Auto-generated method stub
        return row;
    }

    private List<Place> listPopulatedPlaces(String cc) throws SolrServerException, IOException {
        String q = String.format("(feat_class:A OR (feat_class:P AND feat_code:PPL)) AND cc:%s", cc);
        queryParams.set("q", q);
        return this.gazetteer.search(queryParams);
    }

    /**
     * Query gazetteer for all admin boundaries of a certain type and return.
     * @param code
     * @param cc
     * @return
     * @throws SolrServerException
     * @throws IOException
     */
    private Map<String, Place> hashAdminPlaces(String code, String cc) throws SolrServerException, IOException {
        String q = String.format("feat_class:A AND feat_code:%s AND cc:%s", code, cc);
        queryParams.set("q", q);
        List<Place> places = this.gazetteer.search(queryParams);
        Map<String, Place> hashed = new HashMap<>();

        for (Place geo : places) {
            String k = null;
            if (code == "ADM1") {
                k = geo.getAdmin1();
            } else if (code == "ADM2") {
                k = geo.getAdmin2();
            } else {
                print("Bad code: " + code);
                continue;
            }
            // ALWAYS an ouput for a single country!!  so we just dump the ADM=Place key/values, not the HASC=Place. 
            hashed.put(k, geo);
        }

        return hashed;
    }

    public static void main(String[] args) {

        XponentsGazetteerExporter exporter = null;

        /*
         * Process some data.
         */
        try {
            CommandLineParser parser = new DefaultParser();
            Options opts = new Options();
            opts.addOption(Option.builder("o").longOpt("output").hasArg().desc("Output file").build());
            opts.addOption(Option.builder("h").longOpt("help").build());

            HelpFormatter cliHelp = new HelpFormatter();

            String appName = XponentsGazetteerExporter.class.getName();
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

            String outputFile = cmd.getOptionValue("o");

            /** 
             * Initialize resources, 
             */
            try {
                exporter = new XponentsGazetteerExporter();
                exporter.configure();
                exporter.queryParams = SolrGazetteer.createDefaultSearchParams(1000000);
                exporter.export(new File(outputFile));
            } catch (Exception err) {
                err.printStackTrace();
                System.exit(1);
            }
        } catch (Exception err) {
            err.printStackTrace();
        }

        /*
         *  Ensure you close resources, regardless of prior error conditions.
         */
        if (exporter.gazetteer != null) {
            exporter.gazetteer.close();
        }

        /*
         * Annoying: Solr client/server thread hangs on if you do not explicitly hit an System Exit condition.
         */
        System.exit(0);
    }

}
