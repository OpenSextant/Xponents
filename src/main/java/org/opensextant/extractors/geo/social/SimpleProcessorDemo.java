/*
 *   __________   ____   ____   ____  ____  
 *  /  ___/  _ \_/ ___\ / ___\_/ __ \/  _ \ 
 *  \___ (  <_> )  \___/ /_/  >  ___(  <_> )
 * /____  >____/ \___  >___  / \___  >____/ 
 *      \/           \/_____/      \/       
 *
 *      Social Media Geo-Inferencing
 *                 OpenSextant
 */

package org.opensextant.extractors.geo.social;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.opensextant.ConfigException;
import org.opensextant.data.Language;
import org.opensextant.data.social.MessageParseException;
import org.opensextant.data.social.Tweet;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extractors.langid.LangDetect;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.getopt.LongOpt;
import jodd.json.JsonObject;
//import jodd.json.JsonParser;

/**
 * SimpleProcessor is a demonstration of the primary geoinferencing techniques:
 * <ul>
 * <li>PropLoc - location propagation</li>
 * <li>Xponents - OpenSextant toolkit for deep geotagging and geocoding natural
 * language text and metadata.</li>
 * <li>AuthorDNA - statistical models for predicting missing data about social
 * media users, i.e., Country.</li>
 * </ul>
 * 
 * <p>
 * Usage:
 * <p>
 *   While the demo() or main() method are the entry points for this executable, 
 *   the <code> readObject( JSONObject ) </code> method is the primary demonstration detail.
 *   It shows:
 *   <ul>
 *   <li>how we have prepared a tweet or other data for inferencing (mainly langID, 
 *   parsing of data into JSON and then into a Tweet API object, etc)
 *   </li>
 *   <li>how the various stack of inferencing phases are called
 *   </li>
 *   <li>finally, once you have annotations (geo or other), you can then save them somewhere.
 *   </li>
 *   </ul>
 *  <p>
 * For PropLoc, it is assumed you will have a large data set of users to work with and so 
 * for this demo the content is parked in a MongoDB, enriched, and then it is ready for use.
 * Alternatively, you could just run the enrichment on the PropLoc data and generate JSON
 * formatted enriched user data. <code> ingestAndEnrichPropLoc </code> works with files;  
 * If only the <code>mongo</code> URL is provide and no input file, then PropLoc is read from Mongo and then updated
 * there as well.
 *    
 * @author ubaldino
 *
 */
public class SimpleProcessorDemo implements JSONListener {

    public static void main(String[] args) {
        SimpleProcessorDemo.demo(args);
    }

    protected static final Logger log = LoggerFactory.getLogger(SimpleProcessorDemo.class);

    /**
     * This captures the staging and tear down mechanics for a job
     * SimpleProcessor is a demonstration of the workflow for geo-inferencing.
     * 
     * @param args
     */
    public static void demo(String... args) {
        final SimpleProcessorDemo looper = new SimpleProcessorDemo();

        /*
         * Run the shutdown implementation for any exit situation. Normal
         * System.exit() or if user calls Ctrl-C or Kill HUP
         * 
         * Objective is to shutdown cleanly.
         */
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                looper.shutdown();
            }
        });

        try {
            if (!looper.startup(args)) {
                usage(); // Help or some other known startup issue.
            }
        } catch (ConfigException cfgErr) {
            log.error("\n\nFailure getting set up", cfgErr);
            usage();
        }

        // If configured,... run some processing.
        //
        if (looper.configured) {

            try {
                TweetLoader.readJSONByLine(new File(looper.inputFile), looper);
                looper.finish();
            } catch (Exception e) {
                log.error("\n\nFailure reading JSON input", e);
            }

        }

        // Normal shutdown
        // Even attempt shutdown if startup partially succeeds.
        //
        looper.shutdown();
        System.exit(0);
    }

    String inputFile = null;
    String outputFile = null;
    BufferedWriter output = null;
    int maxBatch = -1;
    String kmlFile = null;
    KMLDemoWriter kmlWriter = null;

    /*
     * The geoinference handlers here:
     */
    private XponentGeocoder xponentGeocoder = null;
    private XponentTextGeotagger xponentTextGeocoder = null;

    /*
     * And then again here, as generic list of them. This list handles common
     * work like configure(), geoinference() and close() steps.
     */
    private List<GeoInferencer> inferencers = new ArrayList<>();
    private boolean configured = false;
    protected LangDetect langidTool = null;

    /**
     * If you logic is done due to IO or parsing errors, then signal you are
     * done with errors Maybe you tolerate N errors before calling it quits.
     */
    protected boolean doneWithErrors = false;

    /**
     * DataUtility or other users of JSONListener will see isDone = True and
     * exit cleanly.
     */
    @Override
    public boolean isDone() {
        if (maxBatch > 0 && recordCount >= maxBatch) {
            return true;
        }
        return doneWithErrors;
    }

    /**
     * Dump metrics or do whatever you want to finish up the run.
     */
    public void finish() {

        for (GeoInferencer socgeo : inferencers) {
            System.out.println(socgeo.report());
        }
    }

    /**
     * Initialization work for the demo. Did I say this is just a demo?
     * <p>
     * There are number of obscure input and setup concerns in this demo, as it
     * tries to capture a wide range of cmd line use cases
     * <p>
     * Items setup: Parse Arguments, Configure input sources/files, configure
     * requested geoinferencers, configure language ID tool.
     * 
     * @param args
     * @return false if startup failed.
     * @throws ConfigException
     *             if startup is attempted by encounters IO or other
     *             Configuration errors.
     */
    public boolean startup(String[] args) throws ConfigException {
        if (args.length == 0) {
            return false;
        }

        if (parseArgs(args) < 0) {
            return false;
        }

        /*
         * Startup inferencers. LangID tool is a special resource used
         * optionally.
         */
        for (GeoInferencer socgeo : inferencers) {
            if (langidTool == null) {
                // Initialize once!
                langidTool = new LangDetect(GeoInferencer.AVERAGE_TEXT_SIZE);

                /* If you need to point LangDetect to its profiles
                 * some place other than in the CLASSPATH, then use
                 * 
                 *  LangDetect( sz, "/path/to/directory/profiles.sm" );
                 *  
                 *  for example.
                 */
            }

            socgeo.configure();
            // Optional tool -- not used by all inferencers.
            socgeo.setLanguageID(langidTool);
        }

        if (kmlFile != null) {
            try {
                // Generic formatter is ResultsFormatter.
                //
                kmlWriter = new KMLDemoWriter(kmlFile);
            } catch (Exception err) {
                throw new ConfigException("KML Formatter failed", err);
            }
        }

        configured = true;
        return true;

    }

    /**
     * Ensure resources for each routine are released
     */
    public void shutdown() {
        /*
         * Release resources for inferencers.
         */
        for (SocialGeo socgeo : inferencers) {
            socgeo.close();
        }
        inferencers.clear();

        /*
         * Flush output buffers.
         */
        if (output != null) {
            try {
                output.flush();
                output.close();
                output = null;
            } catch (IOException err) {
                log.error("Failue to close output stream @ {}", outputFile, err);
            }
        }

        if (kmlWriter != null) {
            try {
                kmlWriter.close();
                kmlWriter = null;
            } catch (IOException e) {
                log.error("IO Failure closing KML output");
            }
        }
    }

    /**
     * usage.
     */
    public static void usage() {
        System.err.println("\n======USAGE=======\njava ...demo.SimpleProcessor ARGS, where ARGS are:"
                + "\n\t--phase adna | adna-us |\n" + "\t\tproploc-gaz | proploc |\n"
                + "\t\txponents-meta | xponents-text\n\t\t// ID of phase to run."
                + "\n\t\t// Run multiple: --phase A --phase B ... " + "\n\t--in file\t\t// JSON or JSON.gz"
                + "\n\t--max N\t\t\t// N is maximum number of records to process" + "\n\t--help\t\t\t// This help "
                + "\n\t--out FILE\t\t// JSON file for tweets and annotations"
                + "\n\t--kml FILE\t\t// KML file for tweets and annotations");

    }

    /**
     * Command line argument parsing.
     * 
     * @param args
     *            cmd line args
     * @return status of parsing -1=help; -2=bad argument, 0=normal.
     * @throws ConfigException
     */
    public int parseArgs(String[] args) throws ConfigException {

        /** Sub-class can override options */
        LongOpt[] options = { new LongOpt("phase", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
                new LongOpt("in", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
                new LongOpt("max", LongOpt.REQUIRED_ARGUMENT, null, 'm'),
                new LongOpt("out", LongOpt.REQUIRED_ARGUMENT, null, 'o'),
                new LongOpt("kml", LongOpt.REQUIRED_ARGUMENT, null, 'k'),
                new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),

        };

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("SocGeo SimpleProcessor demo", args, "", options);

        try {
            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {

                case 0:
                    // 0 = Long opt processed.
                    break;

                case 'p':
                    String phase = opts.getOptarg().toLowerCase();
                    if ("xponents-meta".equals(phase)) {
                        xponentGeocoder = new XponentGeocoder();
                        inferencers.add(xponentGeocoder);
                    } else if ("xponents-text".equals(phase)) {
                        xponentTextGeocoder = new XponentTextGeotagger();
                        inferencers.add(xponentTextGeocoder);
                    }
                    break;

                case 'i':
                    inputFile = opts.getOptarg();
                    break;

                case 'o':
                    outputFile = opts.getOptarg();
                    output = Files.newBufferedWriter(new File(outputFile).toPath());
                    break;

                case 'm':
                    maxBatch = Integer.parseInt(opts.getOptarg());
                    break;

                case 'k':
                    kmlFile = opts.getOptarg();
                    break;

                case 'h':
                    log.error("\nHelp Requested");
                    return -1;

                default:
                    log.error("\nUnknown Argument; Short arg " + c);
                    return -2;
                }
            }
            return 0;
        } catch (Exception argsErr) {
            throw new ConfigException("Arguments did not parse or other error.", argsErr);
        }
    }

    /**
     * that is, if preferJSON==true then sf.net.json will be used to parse the
     * line and send JSONObject to your reader.
     * 
     * If you do not prefer JSON, then the raw String is passed for you to parse
     * with readObject(String)
     * 
     * Generally ignore this.
     */
    @Override
    public boolean preferJSON() {
        return true;
    }

    boolean ioError = false;

    private long recordCount = 0;

    /**
     * This is a much abbreviated demonstration. Usually: read all data and
     * condition it on ingest - process data as much as possible in parallel
     * 
     * This method shows reading data from a file, processing it using 1 to N
     * number of inferencers and then outputing the the annotations from
     * processing... all in a very serial fashion.
     * 
     * This is only for demonstration.
     */
    @Override
    public void readObject(JsonObject obj) throws MessageParseException {
        ++recordCount;
        Tweet tw = new Tweet();
        tw.fromJSON(obj);
        tw.lang = guessLanguage(tw);

        try {
            saveTweet(tw);
        } catch (IOException err) {
            throw new MessageParseException("Output Failure.", err);
        }

        for (GeoInferencer inferencer : this.inferencers) {
            try {
                // Process one tweet
                //
                // Author's geolocation?
                //
                ++inferencer.totalRecords;
                GeoInference a;
                if (inferencer.infersAuthorGeo()) {
                    a = inferencer.geoinferenceTweetAuthor(tw);
                    if (a != null) {
                        // Output.
                        saveAnnotation(a);
                        // Map Plot
                        mapTweetKML(tw, a);
                    }
                }

                // Message/Status geolocation?
                //
                if (inferencer.infersStatusGeo()) {
                    a = inferencer.geoinferenceTweetStatus(tw);
                    if (a != null) {
                        // Output.
                        saveAnnotation(a);
                        // Map Plot
                        mapTweetKML(tw, a);
                    }
                }

                if (inferencer.infersPlaces()) {
                    Collection<GeoInference> list = inferencer.geoinferencePlaceMentions(tw);
                    if (list == null) {
                        continue;
                    }
                    for (GeoInference a1 : list) {
                        /*
                         * NOT all of these are annotations. Some items
                         * matched are PERSON, ORG -- i.e., not places Some
                         * items are countries, coordinates, or places.
                         * Lastly some items are not geodetic -- that is,
                         * very low confidence items are likely not
                         * geospatial.
                         * 
                         * tags: person, org, country, geo, place
                         * 
                         */
                        saveAnnotation(a1);
                        // Use Contributor to figure out relevance...
                        // Caller will have to figure out how to mark
                        // entities derived from User, Status, Message Text.

                        /*
                         * For now not mapping items to KML map
                         */
                    }
                    // ? KML
                    // mapTweetKML(tw, list);

                }
            } catch (ExtractionException err) {
                log.error("Inferencing {} had trouble with {}", inferencer.inferencerID, tw, err);
            } catch (IOException ioErr) {
                throw new MessageParseException("Output Failure", ioErr);
            }
        }
    }

    /**
     * Guess at the real language of the message.
     * 
     * @param tw
     * @return
     */
    private String guessLanguage(Tweet tw) {
        /*
         * LANG ID ======== Minimal data ingest conditioning. Check language of
         * text, this is evidence of what culture and geography is relevant
         * here. Note, We are sensitive to altering the given raw data -- only
         * objective here is to identify a reasonable language of text.
         */
        if (!tw.isASCII && tw.lang == null) {
            String naturalLanguage = TextUtils.parseNaturalLanguage(tw.getText());
            Language L = langidTool.detectSocialMediaLang(tw.lang, naturalLanguage);
            if (L != null) {
                return L.getCode();
            }
        }
        return tw.lang;
    }

    /**
     * Save a conditioned version of the tweet capturing mainly the data used as
     * input for processing. All extra data in tweet is tossed -- as we assume
     * you have it all stored somewhere.
     * 
     * @param tw
     * @throws IOException
     */
    private void saveTweet(Tweet tw) throws IOException {
        JsonObject serializedTweet = TweetLoader.toJSON(tw);
        if (output != null) {
            output.write(serializedTweet.toString());
            output.write('\n');
        } else {
            log.info(serializedTweet.toString());
        }
    }

    /**
     * Save output for annotations.
     * 
     * @param a
     * @throws IOException
     */
    private void saveAnnotation(GeoInference a) throws IOException {
        throw new IOException("Not re-implemented. Need JSON serialization");
        /*
        String str = null;
        if (output != null) {
            output.write(str);
            output.write('\n');
        } else {
            log.info(str);
        }
        */
    }

    /**
     * Unused API method.
     * @deprecated
     */
    @Override
    public void readObject(String obj) throws MessageParseException {
        // NOT Implemented.
        return;
    }

    /**
     * Convenience handler to open file, compressed or not.
     * 
     * @param f
     * @return
     * @throws IOException
     */
    public final static InputStream getStream(final String f) throws IOException {
        File infile = new File(f);

        if (infile.getName().toLowerCase().endsWith(".gz")) {
            return new GZIPInputStream(new FileInputStream(infile));
        } else {
            return new FileInputStream(infile);
        }
    }

    private void mapTweetKML(Tweet tw, GeoInference a) {
        if (kmlWriter == null) {
            return;
        }
        if (a.inferenceName.equals("country")) {
            return;
        }

        if (a.geocode == null || !GeodeticUtility.isCoord(a.geocode)) {
            log.error("Should have found annotations with lat/lon: {} {}", a.inferenceName, a.attributes);
            return;
        }

        kmlWriter.write(tw, a);
    }

}
