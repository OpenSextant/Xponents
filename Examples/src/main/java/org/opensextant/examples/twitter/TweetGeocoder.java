package org.opensextant.examples.twitter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.json.JSONObject;

import org.opensextant.data.TextInput;
import org.opensextant.ConfigException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.SimpleGeocoder;
import org.opensextant.extractors.xcoord.XConstants;
import org.opensextant.extractors.xcoord.XCoord;
import org.opensextant.output.FormatterFactory;
import org.opensextant.output.GISDataFormatter;
import org.opensextant.output.OpenSextantSchema;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.util.UnicodeTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    GISDataFormatter tweetOutput;
    GISDataFormatter userOutput;
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
            userlocX.configure(TweetGeocoder.class.getResource("/twitter/tweet-xcoord.cfg"));
            userlocX.disableAll();
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

        tweet_stop = FileUtility.loadDictionary("/twitter/tweet-not-places.txt", true);
        tweet_pass = FileUtility.loadDictionary("/twitter/tweet-places.txt", true);

        geocoder = new SimpleGeocoder();
        Parameters tweetJob = new Parameters();
        Parameters userJob = new Parameters();

        // Fill out the basic I/O parameters.
        tweetJob.outputDir = "./output";
        FileUtility.makeDirectory(tweetJob.outputDir);

        if (formatType == null) {
            formatType = "CSV";
        }

        tweetJob.setJobName(job);
        boolean overwrite = true;
        tweetJob.isdefault = false;

        // Caller should be sure "timestamp" field does not overwrite existing field.
        // DEFINE this once.
        OpenSextantSchema.addDateField("timestamp");
        OpenSextantSchema.addTextField("tweet");
        OpenSextantSchema.addTextField("author");

        // Given the job parameters you can then create the default tweetOutput formatter
        // which takes the Parameters as guidance on file paths, locations, oput filters, etc.
        tweetOutput = createFormatter(formatType, tweetJob);
        tweetOutput.overwrite = overwrite;
        tweetOutput.includeOffsets = false;
        tweetOutput.includeCoordinate = true;
        tweetOutput.setGisDataModel();

        userJob.outputDir = tweetJob.outputDir;
        userJob.setJobName(job + "_Users");
        userJob.isdefault = false;
        userOutput = createFormatter(formatType, userJob);
        userOutput.overwrite = overwrite;

        // Tune User profile geo schema -- very few fields that matter.
        userOutput.includeOffsets = false;
        userOutput.includeCoordinate = true;
        userOutput.setGisDataModel();

        // Swap these fields.
        tweetOutput.removeField("context");
        tweetOutput.addField("tweet");

        tweetOutput.addField("timestamp");
        tweetOutput.addField("author");

        userOutput.removeField("start");
        userOutput.removeField("end");

        // userOutput.field_order.remove("method");
        userOutput.removeField("placename");
        userOutput.removeField("confidence");

        userOutput.addField("timestamp");
        userOutput.addField("author");

        userOutput.removeField("context");
        userOutput.addField("tweet");

        // Create output files, by starting the job formatters.
        // This creates the IO streams and sets the schema for those files.
        //
        tweetOutput.start(tweetJob.getJobName());
        userOutput.start(userJob.getJobName());

        geocoder.setParameters(tweetJob);

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

        if (tw.author_xy_val == null || tw.author_location == null) {
            return;
        }

        ExtractionResult res = new ExtractionResult(tw.id);
        res.addAttribute("timestamp", tw.pub_date);
        res.addAttribute("author", tw.author);
        res.addAttribute("tweet", tw.getText());

        if (tw.author_xy_val != null) {

            res.matches = userlocX.extract(new TextInput(tw.id, tw.author_xy_val));
        } else if (tw.author_location != null) {

            res.matches = userlocX.extract(new TextInput(tw.id, tw.author_location));
            if (res.matches.isEmpty()) {

                try {
                    res.matches = geocoder.extract(new TextInput(tw.id, tw.author_location));
                } catch (Exception userErr) {
                    log.error("Geocoding error with Users?", userErr);
                }
            }
        }

        if (res.matches.isEmpty()) {
            return;
        }

        userOutput.writeGeocodingResult(res);

    }

    /**
     * If a tweet has a non-zero status text, let's find all places in the
     * content.
     */
    public void geocodeTweet(Tweet tw) {
        ++recordCount;

        if (tw.getText() != null && !tw.getText().isEmpty()) {
            try {
                ExtractionResult res = new ExtractionResult(tw.id);
                // Place name tagger may not work if content has mostly lower case proper names.!!!! TODO: allow mixed case;
                res.matches = geocoder.extract(new TextInput(tw.id, tw.getText()));
                res.addAttribute("timestamp", tw.pub_date);
                res.addAttribute("tweet", tw.getText());
                res.addAttribute("author", tw.author);
                enrichResults(res.matches);

                tweetOutput.writeGeocodingResult(res);
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

            if (norm.contains(" tnt ")) {
                g.setFilteredOut(true);
            }

            // Filter out duplicates
            if (distinct_names.contains(norm)) {
                g.setFilteredOut(true);
            } else {
                // Track distinct names
                distinct_names.add(norm);
            }

            if (tweet_pass.contains(norm)) {
                // let it pass.
            } else if (tweet_stop.contains(norm)) {
                g.setFilteredOut(true);
                if (debug) {
                    log.debug("Filter out:" + norm);
                }
                // Hmmm:
                // } else if (tweet_pass.contains(norm) || !TextUtils.isASCII(g.getText().getBytes())) {
                // DO Nothing.
                //
            } else if (norm.length() < 4) {
                /**
                 * TBD. refactoring name tagger.
                 */
                /*Geocoding geo = (Geocoding) g;

                 if (!(geo.isCountry() || geo.isAdministrative())) {
                 g.setFilteredOut(true);
                 if (debug) {
                 log.info("Filter out short term:" + norm);
                 }
                 }
                 *
                 */
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
            tw.setText(scrubText(separateHashMark(tw.getText())));

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
        io.close();
        shutdown();
    }

    public void shutdown() {
        // Close connections and save your output.
        if (geocoder != null) {
            geocoder.cleanup();
        }
        if (tweetOutput != null) {
            tweetOutput.finish();
        }
        if (userOutput != null) {
            userOutput.finish();
        }
    }

    public static void main(String[] args) {

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("TweetGeocoder", args, "n:i:f:");

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
