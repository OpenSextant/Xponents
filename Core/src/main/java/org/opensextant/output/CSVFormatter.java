/*
 * Copyright 2012-2015 The MITRE Corporation.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package org.opensextant.output;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensextant.ConfigException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.processing.ProcessingException;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

public class CSVFormatter extends AbstractFormatter {

    private CsvMapWriter writer = null;

    private final HashSet<String> fieldSet = new HashSet<>();

    public CSVFormatter() {
        this.outputExtension = ".csv";
        this.outputType = "CSV";
        this.includeOffsets = true;
        defaultFields();
    }

    @Override
    public void addField(String f) throws ConfigException {
        fieldOrder.add(f);
        fieldSet.add(f);

    }

    @Override
    public void removeField(String f) throws ConfigException {
        fieldOrder.remove(f);
        fieldSet.remove(f);
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
        try {
            writer.flush();
            close();
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
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }

        if (fio != null) {
            fio.close();
        }
    }

    @Override
    public void writeGeocodingResult(ExtractionResult rowdata) {
        HashMap<String, String> values = new HashMap<>();
        for (TextMatch m : rowdata.matches) {

            values.clear();

            if (fieldSet.contains(OpenSextantSchema.FILEPATH.getName())) {
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
     * Pull in data from match into the output schema (map)
     *
     * @param row input row to write
     * @param m   given match has more metadata
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
     * @throws ConfigException schema configuration error
     */
    protected void buildSchema() throws ConfigException {

        if (!this.includeOffsets) {
            // field_order.add("start");
            // field_order.add("end");
            // } else {
            fieldOrder.remove("start");
            fieldOrder.remove("end");
        }

        outputSchema = new CellProcessor[fieldOrder.size()];
        header = new String[fieldOrder.size()];
        fieldOrder.toArray(header);

        for (int x = 0; x < fieldOrder.size(); ++x) {
            outputSchema[x] = new Optional();
        }

        this.fieldSet.addAll(fieldOrder);
    }

    /**
     */
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

    protected List<String> fieldOrder = new ArrayList<>();

    /**
     * Default fields for generic CSV output. If GIS output is desired, then use
     * GeoCSV formatter.
     */
    protected final void defaultFields() {

        fieldOrder.add("matchtext");
        fieldOrder.add("context");
        fieldOrder.add("filepath");
        fieldOrder.add("method");
        fieldOrder.add("start");
        fieldOrder.add("end");
    }
}
