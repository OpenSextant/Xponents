/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
package org.opensextant.output;

import org.apache.commons.io.FileUtils;
import org.opensextant.data.Geocoding;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

/**
 * Abstract class encapsulating basic results formatter functionality.
 *
 * @author Rich Markeloff, MITRE Corp. Initial version created on Aug 22, 2011
 * @author Marc Ubaldino, MITRE Corp. Refactored, 2013
 */
public abstract class AbstractFormatter implements ResultsFormatter, MatchInterpreter {

    protected Parameters outputParams = null;
    public boolean overwrite = false;

    /**
     * Note - output parameters use tag_XYZ flags to indicate date to include or exclude
     */
    @Override
    public void setParameters(Parameters params) {
        outputParams = params;
    }

    // Allow customization of "geocoding" object.
    protected MatchInterpreter geoInterpreter = null;

    /**
     * Override means for how geocoding is determined per row.
     */
    public void setMatchInterpeter(MatchInterpreter mi) {
        this.geoInterpreter = mi;
    }

    /**
     * The default geocoding interpretation is here.
     * This works for simple stuff like Coordinate extraction
     */
    @Override
    public Geocoding getGeocoding(TextMatch m) {
        Geocoding geocoding = null;
        if (m instanceof Geocoding) {
            geocoding = (Geocoding) m;
        }
        return geocoding;
    }

    /**
     *
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private String filename = "unset";
    /**
     * File extension for callers to know.
     */
    public String outputExtension = null;
    /**
     *
     */
    protected String outputType = null;
    /**
     *
     */
    protected static int TEXT_WIDTH = 150;
    /**
     *
     */
    public boolean debug = false;
    /**
     * Schema-specific stuff. GIS formats would not make use of offsets.
     * CSV format is only one where offsets make sense.
     */
    public boolean includeOffsets = false;
    /**
     * GIS formats may optionally include coordinates as fields.
     * GDB and SHP have a Point geometry which carries the lat/lon already.
     * KML, CSV, JSON, etc. it makes sense to include these explicitly.
     */
    public boolean includeCoordinate = false;

    /**
     * A basic job name that reflects file name
     *
     */
    @Override
    public String getJobName() {
        return this.outputParams.getJobName();
    }

    /**
     * @param fname file name of output
     */
    @Override
    public void setOutputFilename(String fname) {
        this.filename = fname;
    }

    /**
     * @param path output dir path
     */
    @Override
    public void setOutputDir(String path) {

        // Create the directory if necessary
        File resultsDir = new File(path);
        if (!resultsDir.exists()) {
            resultsDir.mkdir();
        }

        this.outputParams.outputDir = resultsDir.getPath();
    }

    /**
     * Write to a file and return HTML containing a link to the file.
     *
     * @param res result to write
     * @throws ProcessingException processing or formatting error
     */
    @Override
    public String formatResults(ExtractionResult res) throws ProcessingException {
        writeGeocodingResult(res);
        return "";
    }

    @Override
    public String getOutputFilepath() {
        return this.outputParams.outputDir + File.separator + this.filename;
    }

    /**
     * @return file name with extension
     */
    protected String createOutputFileName() {
        return this.filename + this.outputExtension;
    }

    /**
     * @return type of output
     */
    @Override
    public String getOutputType() {
        return this.outputType;
    }

    /**
     * This is checked only by internal classes as they create output streams.
     */
    protected void deleteOutput(File previousRun) {
        if (previousRun.exists()) {
            FileUtils.deleteQuietly(previousRun);
        }
    }

    /**
     * uniform helper for overwrite check.
     */
    protected void checkOverwrite(File item) throws IOException {
        if (this.overwrite) {
            this.deleteOutput(item);
        } else if (item.exists()) {
            throw new FileAlreadyExistsException(
                    String.format("OpenSextant API does not overwrite existing GIS output files -- caller must do that. FILE=%s ", item.getPath()));
        }
    }

    @Override
    public abstract void start(String nm) throws ProcessingException;

    @Override
    public abstract void finish();

    /**
     * Create the output stream appropriate for the output type.
     * IO is created using the filename represented by getOutputFilepath()
     *
     * @throws Exception
     */
    protected abstract void createOutputStreams() throws Exception;

    /**
     * @throws IOException
     */
    public abstract void close() throws IOException;

    /**
     * Write your geocoding result directly to output
     * Result should carry ExtractionResult.recordFile as a URI for original.
     *
     * @param rowdata the data to write out
     */
    public abstract void writeGeocodingResult(ExtractionResult rowdata);

}
