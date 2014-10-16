package org.opensextant.output;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensextant.ConfigException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.giscore.DocumentType;
import org.opensextant.processing.ProcessingException;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

public class CSVFormatter extends AbstractFormatter {

    private CsvMapWriter writer = null;

    private HashSet<String> field_set = new HashSet<String>();

    public CSVFormatter() {
        this.outputExtension = ".csv";
        this.outputType = "CSV";
        this.includeOffsets = true;
        defaultFields();
    }

    @Override
    public void addField(String f) throws ConfigException {
        field_order.add(f);
        field_set.add(f);

    }

    @Override
    public void removeField(String f) throws ConfigException {
        field_order.remove(f);
        field_set.remove(f);
    }

    CellProcessor[] outputSchema = null;
    String[] header = null;

    @Override
    public void start(String nm) throws ProcessingException {
        try {
            buildSchema();
            createOutputStreams();
            writer = new CsvMapWriter(fio, CsvPreference.EXCEL_PREFERENCE);
            writer.writeHeader(header);
        } catch (Exception err) {
            throw new ProcessingException("Failed to launch", err);
        }
    }

    @Override
    public void finish() {
        // TODO Auto-generated method stub

    }

    private FileWriter fio = null;

    @Override
    protected void createOutputStreams() throws Exception {
        fio = new FileWriter(new File(getOutputFilepath()));
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

    @Override
    public void writeGeocodingResult(ExtractionResult rowdata) {
        HashMap<String, String> values = new HashMap<String, String>();
        for (TextMatch m : rowdata.matches) {

            values.clear();
            
            if (field_set.contains(OpenSextantSchema.FILEPATH.getName())) {
                values.put(OpenSextantSchema.FILEPATH.getName(), rowdata.recordFile);
            }

            buildRow(values, m);

            try {
                writer.write(values, header, outputSchema);
            } catch (Exception err) {
                log.error("Delayed error ERR:" + err.getLocalizedMessage());
            }
        }
    }

    /**
     * 
     * @param row
     * @param m
     */
    public void buildRow(Map<String, String> row, TextMatch m) {

        addColumn(row, OpenSextantSchema.MATCH_TEXT.getName(), m.getText());
        addColumn(row, OpenSextantSchema.CONTEXT.getName(), m.getContext());
        addColumn(row, OpenSextantSchema.MATCH_METHOD.getName(), m.getType());
        addColumn(row, OpenSextantSchema.START_OFFSET.getName(), m.start);
        addColumn(row, OpenSextantSchema.END_OFFSET.getName(), m.end);
    }

    /**
     * Create a schema instance with the fields properly typed and ordered
     *
     * @return
     * @throws ConfigException schema configuration error
     */
    protected void buildSchema() throws ConfigException {

        if (!this.includeOffsets) {
            //field_order.add("start");
            //field_order.add("end");
        //} else {
            field_order.remove("start");
            field_order.remove("end");
        }

        outputSchema = new CellProcessor[field_order.size()];
        header = new String[field_order.size()];
        field_order.toArray(header);

        for (int x = 0; x < field_order.size(); ++x) {
            outputSchema[x] = new Optional();
        }

        this.field_set.addAll(field_order);
    }

    /**
     */
    protected boolean canAdd(String f) {
        if (f == null) {
            return false;
        }
        return field_set.contains(f);
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
            row.put(f, d.toString());
        }
    }

    protected void addColumn(Map<String, String> row, String f, int d) {
        if (canAdd(f)) {
            row.put(f, Integer.toString(d));
        }
    }

    protected List<String> field_order = new ArrayList<String>();

    /**
     * Default fields for generic CSV output. If GIS output is desired, then use GeoCSV formatter.
     */
    protected final void defaultFields() {

        field_order.add("matchtext");
        field_order.add("context");
        field_order.add("filepath");
        field_order.add("method");
        field_order.add("start");
        field_order.add("end");
    }
}
