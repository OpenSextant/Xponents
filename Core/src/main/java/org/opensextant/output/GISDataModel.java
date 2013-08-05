/**
 Copyright 2009-2013 The MITRE Corporation.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 ** **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government
 * under Contract No. W15P7T-12-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
 **************************************************   */
package org.opensextant.output;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.data.Geocoding;
import org.opensextant.processing.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GISDataModel {

    protected static final Logger log = LoggerFactory.getLogger(GISDataModel.class);
    protected boolean includeOffsets;
    protected boolean includeCoordinate;
    protected Schema schema = null;
    protected List<String> field_order = new ArrayList<String>();
    public Set<String> field_set = new HashSet<String>();

    public GISDataModel(String jobName, boolean includeOffsets, boolean includeCoordinate) {
        this(jobName, includeOffsets, includeCoordinate, true);
    }

    public GISDataModel(String jobName, boolean includeOffsets, boolean includeCoordinate, boolean buildSchema) {
        super();
        this.includeOffsets = includeOffsets;
        this.includeCoordinate = includeCoordinate;
        if (buildSchema) {
            defaultFields();
            try {
                this.schema = buildSchema(jobName);
            } catch (ProcessingException e) {
                // could not successfully construct the schema... fail hard.
                throw new RuntimeException(e);
            }
        }
    }

    protected void addPlaceData(Feature row, Geocoding g) {
        addColumn(row, OpenSextantSchema.ISO_COUNTRY, g.getCountryCode());
        addColumn(row, OpenSextantSchema.PROVINCE, g.getAdmin1());
        addColumn(row, OpenSextantSchema.FEATURE_CLASS, g.getFeatureClass());
        addColumn(row, OpenSextantSchema.FEATURE_CODE, g.getFeatureCode());
        addColumn(row, OpenSextantSchema.PLACE_NAME, g.getPlaceName());
        // Set the geometry to be a point, and add the feature to the list
        row.setGeometry(new Point(g.getLatitude(), g.getLongitude()));
    }

    protected void addPrecision(Feature row, Geocoding g) {
        addColumn(row, OpenSextantSchema.PRECISION, g.getPrecision());
    }

    protected void addConfidence(Feature row, double conf) {
        addColumn(row, OpenSextantSchema.CONFIDENCE, formatConfidence(conf));
    }

    protected void addOffsets(Feature row, TextMatch m) {
        addColumn(row, OpenSextantSchema.START_OFFSET, m.start);
        addColumn(row, OpenSextantSchema.END_OFFSET,  m.end);
    }

    protected void addLatLon(Feature row, Geocoding g) {
        addColumn(row, OpenSextantSchema.LAT, g.getLatitude());
        addColumn(row, OpenSextantSchema.LON, g.getLongitude());
    }

    /**
     * If the caller has additional data to attach to records, allow them to add
     * fields to schema at runtime and map their data to keys on GeocodingResult
     *
     * Similarly, you could have Geocoding row-level attributes unique to the
     * geocoding whereas attrs on GeocodingResult are global for all geocodings
     * in that result set
     *
     * @throws ProcessingException
     */
    protected void addAdditionalAttributes(Feature row, Map<String, Object> rowAttributes) throws ProcessingException {
        if (rowAttributes != null) {

            try {
                for (String field : rowAttributes.keySet()) {
                    if (log.isDebugEnabled()) {
                        log.debug("FIELD=" + field + " = " + rowAttributes.get(field));
                    }
                    addColumn(row, OpenSextantSchema.getField(field), rowAttributes.get(field));
                }
            } catch (ProcessingException fieldErr) {
                throw fieldErr;
            }
        }
    }

    protected void addFilePaths(Feature row, String recordFile, String recordTextFile) {
        // TOOD: HPATH goes here.
        if (recordFile != null) {
            addColumn(row, OpenSextantSchema.FILENAME, FilenameUtils.getBaseName(recordFile));
            addColumn(row, OpenSextantSchema.FILEPATH, recordFile);
            // Only add text path:
            // if original is not plaintext or
            // if original has not been converted
            //
            if (recordTextFile != null && !recordFile.equals(recordTextFile)) {
                addColumn(row, OpenSextantSchema.TEXTPATH, recordTextFile);
            }
        } else {
            log.info("No File path given");
        }
    }

    protected void addContext(Feature row, TextMatch g) {
        addColumn(row, OpenSextantSchema.CONTEXT, g.getContext());
    }

    protected void addMatchText(Feature row, TextMatch g) {
        addColumn(row, OpenSextantSchema.MATCH_TEXT, g.getText());
    }

    /**
     * Allows caller to add a method or pattern id of sorts to denote how match
     * was derived.
     *
     * @param row
     * @param method
     */
    protected void addMatchMethod(Feature row, String method) {
        addColumn(row, OpenSextantSchema.MATCH_METHOD, method);
    }

    protected void addMatchMethod(Feature row, TextMatch match) {
        String method = match.getType();
        addColumn(row, OpenSextantSchema.MATCH_METHOD, method);
    }

    public List<Feature> buildRows(int id, Geocoding g, TextMatch m, Map<String, Object> rowAttributes,
            ExtractionResult res) throws ProcessingException {

        Feature row = new Feature();
        // Administrative settings:
        // row.setName(getJobName());
        row.setSchema(schema.getId());
        row.putData(OpenSextantSchema.SCHEMA_OID, id);

        //
        if (includeOffsets) {
            addOffsets(row, m);
        }

        addPlaceData(row, g);
        addPrecision(row, g);
        //addConfidence(row, g.getConfidence());

        addContext(row, m);

        if (includeCoordinate) {
            addLatLon(row, g);
        }

        addMatchText(row, m);
        addMatchMethod(row, m);

        addAdditionalAttributes(row, rowAttributes);

        if (res.recordFile != null) {
            addFilePaths(row, res.recordFile, res.recordTextFile);
        }

        // this is a list for M x N times
        List<Feature> features = new ArrayList<Feature>();
        features.add(row);

        return features;

    }
    private static final DecimalFormat confFmt = new DecimalFormat("0.000");

    /**
     * Convenience method for managing how confidence number is reported in
     * output.
     */
    protected String formatConfidence(double conf) {
        return confFmt.format(conf);
    }

    public Schema getSchema() {
        return this.schema;
    }

    /**
     * Create a schema instance with the fields properly typed and ordered
     *
     * @return
     * @throws ProcessingException
     */
    protected Schema buildSchema(String jobName) throws ProcessingException {

        if (this.schema != null) {
            return this.schema;
        }

        URI uri = null;
        try {
            uri = new URI("urn:OpenSextant");
        } catch (URISyntaxException e) {
            // e.printStackTrace();
        }

        this.schema = new Schema(uri);
        // Add ID field to the schema
        this.schema.put(OpenSextantSchema.SCHEMA_OID);
        this.schema.setName(jobName);

        for (String field : field_order) {

            if (!this.includeOffsets && (field.equals("start") | field.equals("end"))) {
                continue;
            }

            if (!this.includeCoordinate && (field.equals("lat") | field.equals("lon"))) {
                continue;
            }

            SimpleField F = getField(field);
            this.schema.put(F);
        }

        this.field_set.addAll(field_order);

        return this.schema;
    }

    protected SimpleField getField(String field) throws ProcessingException {
        return OpenSextantSchema.getField(field);
    }

    /**
     */
    protected boolean canAdd(SimpleField f) {
        if (f == null) {
            return false;
        }
        return field_set.contains(f.getName()) && (schema.get(f.getName()) != null);
    }

    /**
     * Add a column of data to output; Field is validated ; value is not added
     * if null
     */
    protected void addColumn(Feature row, SimpleField f, Object d) {
        if (d == null) {
            return;
        }
        if (canAdd(f)) {
            row.putData(f, d);
        }
    }

    /**
     * Add a column of data to output; Field is validated
     */
    protected void addColumn(Feature row, SimpleField f, int d) {
        if (canAdd(f)) {
            row.putData(f, d);
        }
    }

    /**
     * Add a column of data to output; Field is validated
     */
    protected void addColumn(Feature row, SimpleField f, double d) {
        if (canAdd(f)) {
            row.putData(f, d);
        }
    }

    protected void defaultFields() {
        // ID occurs in all output.
        // id.

        // Matching data
        field_order.add("placename");

        // Geographic
        field_order.add("province");
        field_order.add("iso_cc");
        field_order.add("lat");
        field_order.add("lon");

        // Textual context.
        field_order.add("matchtext");
        field_order.add("context");
        field_order.add("filename");
        field_order.add("filepath");
        field_order.add("textpath");

        // File mechanics
        field_order.add("method");
        field_order.add("feat_class");
        field_order.add("feat_code");
        field_order.add("confidence");
        field_order.add("precision");
        field_order.add("start");
        field_order.add("end");

    }
}
