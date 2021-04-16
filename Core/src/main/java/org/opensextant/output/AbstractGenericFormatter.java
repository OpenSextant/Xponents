package org.opensextant.output;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.opensextant.ConfigException;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class encapsulating basic results formatter functionality without
 * prescribing schema
 *
 * @author Marc Ubaldino, MITRE Corp.
 */
abstract public class AbstractGenericFormatter implements ResultsFormatter {

    /** The output params. */
    protected Parameters outputParams = null;

    /** The overwrite. */
    public boolean overwrite = false;

    /**
     * Sets the parameters.
     *
     * @param params job parameters
     */
    @Override
    public void setParameters(Parameters params) {
        outputParams = params;
    }

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** The filename. */
    private String filename = "unset";

    /** File extension for callers to know. */
    public String outputExtension = null;

    /** reflected by extension; an enum in OpenSextant */
    protected String outputType = null;

    /** Size of text window around matches -- to use as excerpts */
    protected static int TEXT_WIDTH = 150;

    public boolean debug = false;

    /** Distinct set of fields in your output schema. */
    protected HashSet<String> fieldSet = new HashSet<>();

    /**
     * Adds the field.
     *
     * @param f field name
     * @throws ConfigException
     */
    @Override
    public void addField(String f) throws ConfigException {
        fieldOrder.add(f);
        fieldSet.add(f);
    }

    /**
     * Removes the field.
     *
     * @param f field name
     * @throws ConfigException
     */
    @Override
    public void removeField(String f) throws ConfigException {
        fieldOrder.remove(f);
        fieldSet.remove(f);
    }

    /** The field_order. */
    protected List<String> fieldOrder = new ArrayList<>();

    /**
     * Default fields for generic CSV output. If GIS output is desired, then use
     * GeoCSV formatter.
     */
    protected final void defaultFields() {

    }

    /**
     * A basic job name that reflects file name.
     *
     * @return the job name
     */
    @Override
    public String getJobName() {
        return this.outputParams.getJobName();
    }

    /**
     * Sets the output filename.
     *
     * @param fname the new output filename
     */
    @Override
    public void setOutputFilename(String fname) {
        this.filename = fname;
    }

    /**
     * Sets the output dir.
     *
     * @param path the new output dir
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
     * Gets the output filepath.
     *
     * @return the output filepath
     */
    @Override
    public String getOutputFilepath() {
        return this.outputParams.outputDir + File.separator + this.filename;
    }

    /**
     * Creates the output file name.
     *
     * @return the string
     */
    protected String createOutputFileName() {
        return this.filename + this.outputExtension;
    }

    /**
     * Gets the output type.
     *
     * @return the output type
     */
    @Override
    public String getOutputType() {
        return this.outputType;
    }

    /**
     * This is checked only by internal classes as they create output streams.
     *
     * @param prevOutput file path of previous output
     */
    protected void deleteOutput(File prevOutput) {
        if (prevOutput.exists()) {
            FileUtils.deleteQuietly(prevOutput);
        }
    }

    /**
     * uniform helper for overwrite check.
     *
     * @param item target output file
     * @throws ProcessingException if unable to overwrite file
     */
    protected void checkOverwrite(File item) throws ProcessingException {
        if (this.overwrite) {
            this.deleteOutput(item);
        } else if (item.exists()) {
            throw new ProcessingException("Overwrite not enabled FILE=" + item.getPath() + " exists");
        }
    }

    /**
     * Start.
     *
     * @param nm output file name/worksheet name
     * @throws ProcessingException
     */
    @Override
    public abstract void start(String nm) throws ProcessingException;

    /**
     * Write the data to the output stream.
     *
     * @param values Map of data
     */
    public abstract void writeRow(Map<String, Object> values);

    /**
     * Finish.
     */
    @Override
    public abstract void finish();

    /**
     * Create the output stream appropriate for the output type. IO is created
     * using the filename represented by getOutputFilepath()
     *
     * @throws Exception when unable to create output stream
     */
    protected abstract void createOutputStreams() throws Exception;

    /**
     * Close output streams.
     *
     * @throws Exception
     */
    protected abstract void closeOutputStreams() throws Exception;

}
