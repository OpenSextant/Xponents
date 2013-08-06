package org.opensextant.examples.twitter;

import java.io.*;
import java.util.*;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;


import org.opensextant.extractors.geo.SimpleGeocoder;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.extractors.xcoord.XConstants;
import org.opensextant.extractors.xcoord.GeocoordMatch;

import org.opensextant.extraction.*;

import org.opensextant.output.FormatterFactory;
import org.opensextant.output.GISDataFormatter;
import org.opensextant.output.OpenSextantSchema;

import org.opensextant.data.*;

import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;

import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.util.UnicodeTextUtils;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class TweetGeocoder {

    private final Logger log = LoggerFactory.getLogger(TweetGeocoder.class);
    private final boolean debug = log.isDebugEnabled();
    private int recordCount = 0;
    int batch = 10000;
    private SimpleGeocoder geocoder = null;
    XCoord userlocX;
    private static String formatType = null;
    GISDataFormatter tw_output;
    GISDataFormatter user_output;
    private static Set<String> tweet_stop;
    private static Set<String> tweet_pass;

    public TweetGeocoder(String job) throws IOException, ConfigException, ProcessingException {
        // This is to bypass XCoord processing within GATE
        // Although we still want to use XCoord for some adhoc processing.
        // This is not internal to XCoord.  It is internal to the PR that uses XCoord: GeocoordFinderPR
        Parameters.RUNTIME_FLAGS = Parameters.FLAG_NO_COORDINATES;//| Parameters.FLAG_ALLOW_LOWERCASE_ABBREV;
        Parameters.RUNTIME_FLAGS ^= Parameters.FLAG_EXTRACT_CONTEXT;

        userlocX = new XCoord();
        try {
            userlocX.configure(TweetGeocoder.class.getResource("/tweet-xcoord.cfg"));
            userlocX.match_MGRS(false);
            userlocX.match_UTM(false);
            // Explicitly enable DD

            // Note -- for parsing coordinates in Tweet metadata
            // we need to turn off the normal Decimal degree filters.
            //  Decimal degrees are really the only thing we want out of tweets,
            //  so we need to carefully undo DD filters.
            //
            userlocX.match_DD(true);
            XCoord.RUNTIME_FLAGS ^= XConstants.DD_FILTERS_ON;  // Be less strict with Decimal degrees.
            XCoord.RUNTIME_FLAGS ^= XConstants.FLAG_EXTRACT_CONTEXT;  // ignore text context.

        } catch (ConfigException xcerr) {
            throw new ProcessingException(xcerr);

        }

        tweet_stop = FileUtility.loadDictionary("tweet-not-places.txt", true);
        tweet_pass = FileUtility.loadDictionary("tweet-places.txt", true);


        geocoder = new SimpleGeocoder();
        Parameters job1 = new Parameters();
        Parameters job2 = new Parameters();

        // Fill out the basic I/O parameters.
        job1.outputDir = "/tmp";

        if (formatType == null) {
            formatType = "CSV";
        }

        job1.setJobName(job);
        boolean overwrite = true;

        // Caller should be sure "timestamp" field does not overwrite existing field.
        // DEFINE this once.
        OpenSextantSchema.addDateField("timestamp");
        OpenSextantSchema.addTextField("tweet");
        OpenSextantSchema.addTextField("author");

        // Given the job parameters you can then create the default tw_output formatter
        // which takes the Parameters as guidance on file paths, locations, oput filters, etc.
        tw_output = createFormatter(formatType, job1);
        tw_output.overwrite = overwrite;
        tw_output.includeOffsets = false;
        tw_output.includeCoordinate = true;
        tw_output.start(job1.getJobName());

        job2.outputDir = "/tmp";
        job2.setJobName(job + "_Users");
        user_output = createFormatter(formatType, job2);
        user_output.overwrite = overwrite;

        // Tune User profile geo schema -- very few fields that matter.
        user_output.includeOffsets = false;
        user_output.includeCoordinate = true;

        // Swap these fields.
        tw_output.removeField("context");
        tw_output.addField("tweet");

        tw_output.addField("timestamp");
        tw_output.addField("author");

        user_output.removeField("start");
        user_output.removeField("end");

        // user_output.field_order.remove("method");
        user_output.removeField("placename");
        user_output.removeField("confidence");

        user_output.addField("timestamp");
        user_output.addField("author");

        user_output.removeField("context");
        user_output.addField("tweet");

        user_output.start(job2.getJobName());

        geocoder.setParameters(job1);

        geocoder.configure();

    }

    /**
     * The default formatter
     */
    public static GISDataFormatter createFormatter(String outputFormat, Parameters p)
            throws IOException, ProcessingException {

        if (p.isdefault) {
            throw new ProcessingException("Caller is required to use non-default Parameters; "
                    + "\nat least set the output options, folder, jobname, etc.");
        }
        GISDataFormatter formatter = (GISDataFormatter) FormatterFactory.getInstance(outputFormat);
        if (formatter == null) {
            throw new ProcessingException("Wrong formatter?");
        }

        formatter.setParameters(p);
        // formatter.setOutputDir(params.outputDir);
        formatter.setOutputFilename(p.getJobName() + formatter.outputExtension);

        return formatter;
    }

    /**
     * Batching happens inside -- each tweet is pushed onto batch Every N tweets
     * is geocoded.
     *
     * If user loc.xy: write out( xy ) else if user loc geocode (user loc) write
     * out ()
     *
     * geocode(status) write out ()
     */
    public void geocodeTweetUser(Tweet tw) {

        ExtractionResult geo = new ExtractionResult(tw.id);
        geo.addAttribute("timestamp", tw.pub_date);
        geo.addAttribute("author", tw.author);
        geo.addAttribute("tweet", tw.getBody());

        if (tw.author_xy != null) {

            // Geocoding object is the final data that is written to GISCore or other outputs, per the ResultsFormatter IF.
            GeocoordMatch userLoc_geocoded = new GeocoordMatch();

            userLoc_geocoded.setLatitude(tw.author_xy.getLatitude());
            userLoc_geocoded.setLongitude(tw.author_xy.getLongitude());

            geo.matches.add(userLoc_geocoded);
            // Method = USER-GEO;

            user_output.writeGeocodingResult(geo);

        } else if (tw.author_location != null) {

            /**
             * Produces Geocoding TextMatch objects:
             */
            ExtractionResult res = new ExtractionResult(tw.id);

            res.matches = userlocX.extract(new TextInput(tw.author_location, tw.id));
            if (res.matches.isEmpty()) {

                try {
                    res.matches.addAll(geocoder.extract(new TextInput(tw.id, tw.author_location.toUpperCase())));
                } catch (Exception userErr) {
                    log.error("Geocoding error with Users?", userErr);
                }
            }
            if (!res.matches.isEmpty()) {
                res.addAttribute("timestamp", tw.pub_date);
                res.addAttribute("author", tw.author);
                res.addAttribute("author", tw.getBody());
            }
            user_output.writeGeocodingResult(res);

        }
    }

    /**
     * If a tweet has a non-zero status text, let's find all places in the
     * content.
     */
    public void geocodeTweet(Tweet tw) {
        ++recordCount;

        if (tw.getBody() != null && !tw.getBody().isEmpty()) {
            try {
                ExtractionResult res = new ExtractionResult(tw.id);
                // Place name tagger may not work if content has mostly lower case proper names.!!!! TODO: allow mixed case;
                res.matches = geocoder.extract(new TextInput(tw.id, tw.getBody().toUpperCase()));
                res.addAttribute("timestamp", tw.pub_date);
                res.addAttribute("tweet", tw.getBody());
                res.addAttribute("author", tw.author);
                enrichResults(res.matches);

                tw_output.writeGeocodingResult(res);
            } catch (Exception err) {
                log.error("Geocoding error?", err);
            }
        }

        if (recordCount % batch == 0 && recordCount > 0) {
            log.info("ROW #" + recordCount);
            geocoder.reportMemory();
        }
    }
    private Set<String> distinct_names = new HashSet<String>();

    /**
     * Enrich and filter geocoding as needed.
     *
     * FILTER OUT from GIS output: + name or matchtext is a known stop word
     * (non-place), + short terms that are not countries
     */
    private void enrichResults(List<TextMatch> matches) {
        distinct_names.clear();
        for (TextMatch g : matches) {

            String norm = g.getText().toLowerCase();

            // Filter out duplicates
            if (distinct_names.contains(norm)) {
                g.setFilteredOut(true);
            } else {
                // Track distinct names
                distinct_names.add(norm);
            }

            if (tweet_stop.contains(norm)) {
                g.setFilteredOut(true);
                if (debug) {
                    log.debug("Filter out:" + norm);
                }
            } else if (tweet_pass.contains(norm) || !TextUtils.isASCII(g.getText().getBytes())) {
                // DO Nothing.
                //
            } else if (norm.length() < 4) {
                Geocoding geo = (Geocoding) g;

                if (!(geo.isCountry() || geo.isAdministrative())) {
                    g.setFilteredOut(true);
                    if (debug) {
                        log.info("Filter out short term:" + norm);
                    }
                }
            }
        }
    }

    /**
     * Remove line endings; Emoticons; what else?
     */
    private String scrubText(String x) {
        String _new = TextUtils.fast_replace(x, "\n\r", " ");
        _new = UnicodeTextUtils.remove_emoticons(_new);
        _new = UnicodeTextUtils.remove_symbols(_new);

        return _new;
    }

    private String separateHashMark(String t) {
        return t.replace("#", "# ");
    }

    /**
     * Need references to current methodologies for what data is available,
     * reliable, etc and where/when to use it.
     */
    private void readTweet(String json, Tweet tw) throws ParseException {

        try {

            JSONObject twj = JSONObject.fromObject(json.trim());
            tw.fromJSON(twj);

            // RESET using a cleaned up status text
            tw.setBody(scrubText(separateHashMark(tw.getBody())));

        } catch (Exception twerr) {
            throw new ParseException("Failed to parse Tweet " + twerr.getMessage(), 0);
        }
    }

    public static int START_ROW = 0;
    public static int MAX_ROWS = -10000;

    /**
     * One JSON tweet per line
     */
    public void process(String path) throws IOException, ProcessingException {
        File input = new File(path);
        LineNumberReader io = new LineNumberReader(new FileReader(input));
        String line;
        Tweet tw = new Tweet();
        int linecount = 0;
        while ((line = io.readLine()) != null) {
            ++linecount;

            if (linecount < START_ROW) {
                continue;
            }
            try {
                tw.reset();
                readTweet(line, tw);
                geocodeTweet(tw);
                geocodeTweetUser(tw);
            } catch (ParseException err) {
                // throw new ProcessingException(err);
                log.error("At line #" + linecount + " we failed to parse " + line, err);
            }

            if (recordCount >= MAX_ROWS && MAX_ROWS > 0) {
                break;
            }
        }
        shutdown();
    }

    public void shutdown() {
        // Close connections and save your output.
        if (geocoder != null) {
            geocoder.shutdown();
        }
        if (tw_output != null) {
            tw_output.finish();
        }
        if (user_output != null) {
            user_output.finish();
        }
    }

    public static void main(String[] args) {

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("TweetGeocoder", args, "ln:i:f:");

        try {
            String jobname = null;
            String inputfile = null;

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                    case 'n':
                        jobname = opts.getOptarg();
                        break;
                    case 'i':
                        inputfile = opts.getOptarg();
                        break;
                    case 'f':
                        formatType = opts.getOptarg();
                        break;
                }
            }

            final TweetGeocoder job = new TweetGeocoder(jobname);
            job.process(inputfile);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    /**
     * Extend the generic Tweet with some name and value tracking.
     */
    class TweetPlus extends Tweet {

        public Set<String> names = new HashSet<String>();

        public TweetPlus() {
            super();
        }

        @Override
        public void reset() {
            super.reset();
            names.clear();
        }
    }
}
