/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.examples;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.opensextant.ConfigException;
import org.opensextant.extraction.ExtractionMetrics;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.opensextant.output.AbstractFormatter;
import org.opensextant.output.FormatterFactory;
import org.opensextant.output.ResultsFormatter;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;
import org.opensextant.processing.XtractorGroup;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.ConversionListener;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.XText;
import org.slf4j.LoggerFactory;

/**
 * <pre>
 * A default illustration of using Xponent xtractors for geo and temporal
 * extraction.  This demo shows how to:
 *
 *  setup some extractors
 *  crawl data
 *  process data
 *  output in particular formats.
 *
 * All showing the most basic aspects of the OpenSextant and Xponents APIs
 *
 * NOTE: this is a variation on OpenSextant v1.4 "Runner" app.
 *
 *</pre>
 *
 * @author ubaldino
 */
public class BasicGeoTemporalProcessing extends XtractorGroup implements ConversionListener {

    private Parameters params = new Parameters();
    protected XText converter;
    /* # of documents */
    private int total_docs = 0;
    private long total_rawbytes = 0;
    private long total_size = 0;
    /* Process 4 MB of text content  800 x 5KB average documents */
    private ExtractionMetrics conversionMetric = new ExtractionMetrics("doc-conversion");
    private ExtractionMetrics processingMetric = new ExtractionMetrics("doc-processing");
    private boolean overwriteOutput = true;

    /**
     *
     */
    public BasicGeoTemporalProcessing() {
        log = LoggerFactory.getLogger(BasicGeoTemporalProcessing.class);
    }

    /**
     * Shutdown: release global resources, if any; Close all formatters
     *
     */
    public void shutdown() {
        //PlacenameMatcher.shutdown();
        cleanupAll();

        for (ResultsFormatter outputter : formatters) {
            outputter.finish();
        }
    }

    /**   Ideally you should separate your one-time initialization steps, configuring your extractors
     * apart from the repetitive steps of setting up Jobs and Inputs.   Outputs you might setup once
     * for the entire JVM session, or it may be something you do periodically.  In summary:
     *
     * configure separately:
     *   a) extractors, converters
     *   b) job inputs and parameters
     *   c) output formatters
     *   d) other resources, e.g., filters
     */
    public boolean setup(String inFile, List<String> outFormats, String outFile, String tempDir)
            throws  ProcessingException, IOException {

        params.isdefault = false;

        if (!validateParameters(inFile, outFormats, outFile, tempDir, params)) {
            print("VALIDATION ERRORS: " + runnerMessage.toString());
            return false;
        }

        // If you are dead-sure you want only coordinates from text, then just use XCoord.
        // Otherwise SimpleGeocoder does both coords + names.
        // 
        //XCoord xcoord = new XCoord();
        //xcoord.configure();
        //this.addExtractor(xcoord);

        // Testing only
        params.tag_places = true;
        params.tag_coordinates = true;
        params.output_countries = false;

        PlaceGeocoder geocoder = new PlaceGeocoder();
        geocoder.enablePersonNameMatching(true);
        geocoder.setParameters(params);
        geocoder.configure();
        this.addExtractor(geocoder);

        XTemporal xtemp = new XTemporal();
        xtemp.configure();
        this.addExtractor(xtemp);

        converter = new XText();

        converter.enableHTMLScrubber(false);
        converter.enableSaving(true);
        converter.enableOverwrite(false);
        converter.setConversionListener(this);

        // Complications:  Where do we save converted items?
        // Developer should change this based on actual environment, paths, perms, etc.
        // Using a "temp" folder as XText cache or no cache at all... 
        // This is for illustration purposes only.
        // 
        if (tempDir != null) {
            converter.getPathManager().setConversionCache(tempDir);
        } else {
            converter.enableSaving(false);
        }

        try {
            converter.setup();
        } catch (IOException ioerr) {
            throw new ConfigException("Document converter could not start", ioerr);
        }

        this.params.inputFile = inFile.trim();
        this.params.outputFile = outFile.trim();

        if (outFormats != null) {
            for (String fmt : outFormats) {
                params.addOutputFormat(fmt);
                AbstractFormatter formatter = createFormatter(fmt, params);
                formatter.overwrite = overwriteOutput;
                this.addFormatter(formatter);

                //if (formatter instanceof CSVFormatter) {
                //    formatter.addField(OpenSextantSchema.FILEPATH.getName());
                //    formatter.addField(OpenSextantSchema.MATCH_TEXT.getName());
                // }
                formatter.start(params.getJobName());
            }
        }
        return true;
    }

    /**
     * The default formatter
     */
    public static AbstractFormatter createFormatter(String outputFormat, Parameters plist)
            throws IOException, ProcessingException {

        if (plist.isdefault) {
            throw new ProcessingException("Caller is required to use non-default Parameters; "
                    + "\nat least set the output options, folder, jobname, etc.");
        }
        AbstractFormatter formatter = (AbstractFormatter) FormatterFactory.getInstance(outputFormat);
        if (formatter == null) {
            throw new ProcessingException("Wrong formatter?");
        }

        formatter.setParameters(plist);
        formatter.setOutputFilename(plist.getJobName() + formatter.outputExtension);

        return formatter;
    }

    /**
     * =============================================== Pipeline mechanics: track
     * # of docs, raw bytes, plain/text chars.
     * ===============================================
     */
    /**
     * Statusing metrics: # of documents processed so far.
     */
    public int getCurrentDocCount() {
        return total_docs;
    }

    /**
     * Statusing metrics: # of raw bytes processed so far.
     */
    public long getCurrentByteCount() {
        return total_rawbytes;
    }

    /**
     * Statusing metrics: # of plain text characters processed so far.
     */
    public long getCurrentTextCharCount() {
        return total_size;
    }

    /**
     * Runs OpenSextant. See the
     * <code>main</code> method for a description of the input parameters. TODO:
     * outFile is not used. It is only used as a part of global settings
     * somewhere....
     * @throws ConfigException 
     *
     */
    public void run() throws ProcessingException, IOException, ConfigException {

        printRequest();

        log.info("Starting document ingest");
        startTime = System.currentTimeMillis();
        prevTime = startTime;

        // All input and processing happens within:
        converter.extractText(this.params.inputFile);

        reportMemory();
        log.info("Finished all processing");
    }

    long startTime = 0;
    long prevTime = 0;

    /**
     * Note -- a corpus will explode in memory if the job is too large.
     * Processor design should account for how to partition the problem -
     * ingest, conversion, geocoding, persistence, output format generation.
     *
     * This implements the XText conversion listener -- when a document is found
     * it is reported here. We add it to the corpus prior to executing the
     * application on the corpus.
     *
     * The preferred mode is to take the list of document URLs and process them
     * as a batch.
     *
     */
    public void handleConversion(ConvertedDocument txtdoc, String fpath) {
        if (txtdoc == null) {
            log.error("NOTE: Document could not be converted FILE={}", fpath);
            return;
        }
        total_rawbytes += txtdoc.filesize;
        ++total_docs;
        total_size += txtdoc.buffer.length();
        long now = System.currentTimeMillis();

        conversionMetric.addTime(now - prevTime);
        prevTime = now;

        this.processAndFormat(txtdoc);
        now = System.currentTimeMillis();
        processingMetric.addTime(now - prevTime);
        prevTime = now;

        if (total_docs % 100 == 0) {
            reportMemory();
        }
    }

    public void reportMemory() {
        Runtime R = Runtime.getRuntime();
        long usedMemory = R.totalMemory() - R.freeMemory();
        print("CURRENT MEM USAGE(K)=" + (int) (usedMemory / 1024));
    }

    public void reportMetrics() {
        print("===============\nDOCUMENT CONVERSION");
        print("\t" + conversionMetric);

        print("===============\nDOCUMENT PROCESSING");
        print("\t" + processingMetric);
    }

    private static String _inFile = null;
    private static String _outFile = null;
    private static String _outFormat = null;
    private static List<String> _outFormats = null;
    private static String _tempDir = null;

    /**
     * Parse command line options.
     */
    private static void parseCommandLine(String[] args) {
        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("BasicGeoTemp", args, "hi:f:o:t:");

        int c;
        while ((c = opts.getopt()) != -1) {
            switch (c) {

            // -i inputFile = path to file or directory of files to be processed
            case 'i':
                _inFile = opts.getOptarg();
                break;

            // -f outputFormat = the desired output format
            case 'f':
                _outFormat = opts.getOptarg();
                _outFormats = TextUtils.string2list(_outFormat.trim(), ",");
                break;

            // -o outputDir = the path to output file
            case 'o':
                _outFile = opts.getOptarg();
                break;

            // -t tempDir = the path to temp directory
            case 't':
                _tempDir = opts.getOptarg();
                break;
            case 'h':
            default:
                printHelp();
                System.exit(-1);
            }
        }
    }

    public static void print(String msg) {
        System.out.println(msg);
    }

    public static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    public static void error(Exception err, String... args) {
        System.out.println("ERROR " + err.getMessage());
        System.out.println(args);

        System.err.println("ERROR " + err.getMessage());
        err.printStackTrace(System.err);
    }

    protected void printRequest() {
        print("----------------- REQUEST -----------------");
        print("Input file: " + params.inputFile);
        print("Output format: " + params.getOutputFormats());
        print("Output location: " + params.outputDir);
    }

    /**
     * Print a usage message
     */
    protected static void printHelp() {

        print("Options:");
        print("\t-i inputFile = path to file or directory of files to be processed");
        print("\t-f outputFormat = the desired output format");
        print("\t-o outputFile = the path to output file");
        print("\t-t tempDir = the path to the temporary storage directory");
    }

    private StringBuilder runnerMessage = new StringBuilder();

    /**
     * Check that the input parameters are valid and complete.
     *
     * @return true if parameters and defaults suffice; false otherwise.
     */
    public boolean validateParameters(String inPath, List<String> outFormats, String outPath, String tempDir,
            Parameters plist) {

        runnerMessage = new StringBuilder();

        if (outPath == null) {
            runnerMessage.append("Please specify an Output file or folder");
            return false;
        }

        inPath = inPath.trim();
        outPath = outPath.trim();

        // Make sure input file exists
        File inFile = new File(inPath);
        if (!inFile.exists()) {
            runnerMessage.append("Input file " + inPath + " does not exist");
            return false;
        }

        // Check output format
        if (outFormats != null) {
            for (String outFormat : outFormats) {
                if (!FormatterFactory.isSupported(outFormat)) {
                    runnerMessage.append("Unrecognized output format: " + outFormat);
                    return false;
                }
            }
        }

        if (inPath.startsWith("$") || outPath.startsWith("$")) {
            runnerMessage.append("Invalid input/output -- Ant style arguments are null");
            return false;
        }
        // Verify user has specified a directory for unpacking an archive

        // Get file extension
        //String ext = FilenameUtils.getExtension(inPath);

        if (FileUtility.isArchiveFile(inPath) && tempDir == null) {
            runnerMessage.append(
                    "A directory for temporary storage must be provided for unpacking Zip and other archive files");
            return false;
        }

        // Split the path name into directory and file names
        File container = new File(outPath);
        File destDir = null;
        String destFile = null;
        log.info("Working off INPUT=" + container.getAbsolutePath());

        if (container.isDirectory()) {
            destDir = container;
            try {
                // DEFAULT file name.
                plist.setJobName("OpenSextant_Output_" + Parameters.getJobTimestamp());
            } catch (Exception fmterr) {
                runnerMessage.append("Failed to invoke the requested format to create a default output file");
                return false;
            }
        } else {
            destDir = container.getParentFile();
            if (destDir == null) {
                destDir = new File(".");
                log.info("Saving output to current working directory");
            }
            destFile = container.getName();
            plist.setJobName(FilenameUtils.getBaseName(destFile));
        }

        if (!destDir.exists()) {
            // throw new IOException("Sorry - your destination folder " + destDir + " must exist");
            runnerMessage.append("Destination folder must exist, DIR=" + destDir.getAbsolutePath());
            return false;
        }

        plist.outputDir = destDir.getAbsolutePath();

        return true;
    }

    /**
     * Runs Xponent Example from the command line. Command line options are:
     * <ul>
     * <li>
     * <code>-i </code><i>inputFile</i> Path to file or directory of files to be
     * processed
     * </li><li>
     * <code>-f </code><i>outputFormat</i> The desired output format
     * </li><li>
     * <code>-o </code><i>outputDir</i> Path to output file
     * </li><li>
     * <code>-t </code><i>tempDir</i> Path to the temporary storage directory,
     * if one is required
     * </li><li>
     * <code>-d </code><i>descriptionType</i> Choice of text string used to fill
     * description fields, if the output format has a description field.
     * </li>
     * </ul><p>
     */
    public static void main(String[] args) {

        print("Parsing Commandline");
        parseCommandLine(args);
        try {
            BasicGeoTemporalProcessing runner = new BasicGeoTemporalProcessing();

            if (runner.setup(_inFile, _outFormats, _outFile, _tempDir)) {
                runner.run();
                runner.shutdown();
            }
            // Success.
        } catch (Exception err) {
            err.printStackTrace();
        }
        System.exit(0);
    }
}
