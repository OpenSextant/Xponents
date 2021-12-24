package org.opensextant.output;

import java.io.FileWriter;
import java.util.Map;

import org.opensextant.ConfigException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * Alternative to CSVFormatter which is schema-specific and a bit
 * rigid. ResultsFormatter interface assumes an "extraction result"
 *
 * @author ubaldino
 */
public class CSVGenericFormatter extends AbstractGenericFormatter {

    private CsvMapWriter writer = null;

    private String delimiter = ",";

    CellProcessor[] outputSchema = null;

    String[] header = null;

    public CSVGenericFormatter(Parameters p) {
        this.outputExtension = ".csv";
        this.outputType = "CSV";

        this.setParameters(p);
        if (p.outputFile != null) {
            this.setOutputFilename(p.outputFile);
        }

        defaultFields();
    }

    public void setDelimiter(String ch) {
        delimiter = ch;
    }

    @Override
    public void start(String nm) throws ProcessingException {
        try {
            buildSchema();
            createOutputStreams();
            switch (delimiter) {
            case "\t":
                writer = new CsvMapWriter(fio, CsvPreference.TAB_PREFERENCE);
                break;
            case ",":
            default:
                writer = new CsvMapWriter(fio, CsvPreference.STANDARD_PREFERENCE);
            }
            writer.writeHeader(header);
        } catch (Exception err) {
            throw new ProcessingException("Failed to launch", err);
        }
    }

    @Override
    public void finish() {
        try {
            writer.flush();
            closeOutputStreams();
        } catch (Exception err) {
            log.error("IO Failure on finish", err);
        }
    }

    private FileWriter fio = null;

    @Override
    protected void createOutputStreams() throws Exception {
        fio = new FileWriter(getOutputFilepath());
    }

    @Override
    protected void closeOutputStreams() throws Exception {
        if (writer != null) {
            writer.close();
        }

        if (fio != null) {
            fio.close();
        }
    }

    /**
     * Write the data to the output stream.
     * @param values Map of data
     */
    public void writeRow(Map<String, Object> values) {
        try {
            writer.write(values, header, outputSchema);
        } catch (Exception err) {
            log.error("Delayed error ERR: {}", err.getLocalizedMessage());
        }
    }

    /**
     * Create a schema instance with the fields properly typed and ordered
     *
     * @throws ConfigException
     *                         schema configuration error
     */
    protected void buildSchema() throws ConfigException {

        outputSchema = new CellProcessor[fieldOrder.size()];
        header = new String[fieldOrder.size()];
        fieldOrder.toArray(header);

        for (int x = 0; x < fieldOrder.size(); ++x) {
            outputSchema[x] = new Optional();
        }

        this.fieldSet.addAll(fieldOrder);
    }

    protected boolean canAdd(String f) {
        if (f == null) {
            return false;
        }
        return fieldSet.contains(f);
    }

    /**
     * Add a column of data to output; Field is validated ; value is not added
     * if null
     */
    protected void addColumn(Map<String, String> row, String f, String d) {
        if (d == null) {
            return;
        }
        if (canAdd(f)) {
            row.put(f, d);
        }
    }

    protected void addColumn(Map<String, String> row, String f, int d) {
        if (canAdd(f)) {
            row.put(f, Integer.toString(d));
        }
    }

    /**
     * Not implemented: this formatter is more flexible than requiring you to use it
     * for Extraction output
     */
    @Override
    public String formatResults(ExtractionResult result) throws ProcessingException {
        throw new ProcessingException("Currently not implemented as an Extraction schema formatter");
    }

}
