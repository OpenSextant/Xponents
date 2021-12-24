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

import org.apache.commons.io.FilenameUtils;
import org.opensextant.ConfigException;
import org.opensextant.data.Geocoding;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.processing.ResultsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.*;

public class GISDataModel {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected boolean includeOffsets = false;
    protected boolean includeCoordinate = true;
    protected boolean useFileHyperlink = false;

    protected Schema schema = null;
    protected List<String> field_order = new ArrayList<>();
    public Set<String> field_set = new HashSet<>();

    /**
     * Instantiates a new GIS data model.
     *
     * @param jobName           the job name
     * @param includeOffsets    the include offsets
     * @param includeCoordinate the include coordinate
     */
    public GISDataModel(String jobName, boolean includeOffsets, boolean includeCoordinate) {
        this(jobName, includeOffsets, includeCoordinate, true);
    }

    /**
     * Instantiates a new GIS data model.
     *
     * @param jobName           the job name
     * @param includeOffsets    the include offsets
     * @param includeCoordinate the include coordinate
     * @param buildSchema       the build schema
     */
    public GISDataModel(String jobName, boolean includeOffsets, boolean includeCoordinate, boolean buildSchema) {
        super();
        this.includeOffsets = includeOffsets;
        this.includeCoordinate = includeCoordinate;
        if (buildSchema) {
            defaultFields();
            try {
                this.schema = buildSchema(jobName);
            } catch (ConfigException e) {
                // could not successfully construct the schema... fail hard.
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Adds the place data.
     *
     * @param row row of data
     * @param g   geocoding
     */
    protected void addPlaceData(Feature row, Geocoding g) {
        addColumn(row, OpenSextantSchema.ISO_COUNTRY, g.getCountryCode());
        addColumn(row, OpenSextantSchema.PROVINCE, g.getAdmin1());
        addColumn(row, OpenSextantSchema.FEATURE_CLASS, g.getFeatureClass());
        addColumn(row, OpenSextantSchema.FEATURE_CODE, g.getFeatureCode());
        addColumn(row, OpenSextantSchema.PLACE_NAME, g.getPlaceName());

        if (includeCoordinate) {
            if (g.hasCoordinate()) {
                // Set the geometry to be a point, and add the feature to the list
                row.setGeometry(new Point(g.getLatitude(), g.getLongitude()));
                addLatLon(row, g);
            }
        }
    }

    /**
     * Adds the precision.
     *
     * @param row row of data
     * @param g   geocoding
     */
    protected void addPrecision(Feature row, Geocoding g) {
        addColumn(row, OpenSextantSchema.PRECISION, g.getPrecision());
    }

    /**
     * Adds the confidence.
     *
     * @param row  row of data
     * @param conf confidence
     */
    protected void addConfidence(Feature row, int conf) {
        addColumn(row, OpenSextantSchema.CONFIDENCE, conf);
    }

    /**
     * Adds the offsets.
     *
     * @param row data
     * @param m   match metadata
     */
    protected void addOffsets(Feature row, TextMatch m) {
        addColumn(row, OpenSextantSchema.START_OFFSET, m.start);
        addColumn(row, OpenSextantSchema.END_OFFSET, m.end);
    }

    /**
     * Adds the lat lon. to the given data row.
     *
     * @param row data
     * @param g   geocoding
     */
    protected void addLatLon(Feature row, Geocoding g) {
        addColumn(row, OpenSextantSchema.LAT, g.getLatitude());
        addColumn(row, OpenSextantSchema.LON, g.getLongitude());
    }

    /**
     * If the caller has additional data to attach to records, allow them to add
     * fields to schema at runtime and map their data to keys on GeocodingResult
     * Similarly, you could have Geocoding row-level attributes unique to the
     * geocoding whereas attrs on GeocodingResult are global for all geocodings
     * in that result set.
     *
     * @param row           the row
     * @param rowAttributes the row attributes
     * @throws ConfigException the config exception
     */
    protected void addAdditionalAttributes(Feature row, Map<String, Object> rowAttributes) throws ConfigException {
        if (rowAttributes != null) {
            for (String field : rowAttributes.keySet()) {
                if (log.isDebugEnabled()) {
                    log.debug("FIELD=" + field + " = " + rowAttributes.get(field));
                }
                addColumn(row, OpenSextantSchema.getField(field), rowAttributes.get(field));
            }
        }
    }

    /**
     * Adds the file paths.
     *
     * @param row            data
     * @param recordFile     original file
     * @param recordTextFile text version of original
     */
    protected void addFilePaths(Feature row, String recordFile, String recordTextFile) {
        // TOOD: HPATH goes here.
        if (recordFile != null) {
            String fname = FilenameUtils.getBaseName(recordFile);
            addColumn(row, OpenSextantSchema.FILENAME, fname);
            if (this.useFileHyperlink) {
                // Caller is responsible for making sure recordFile is absolute path.
                addColumn(row, OpenSextantSchema.FILEPATH,
                        String.format("<a href=\"file://%s\">%s</a>", recordFile, fname));
            } else {
                addColumn(row, OpenSextantSchema.FILEPATH, recordFile);
            }
            // Only add text path:
            // if original is not plaintext or
            // if original has not been converted
            //
            if (recordTextFile != null && !recordFile.equals(recordTextFile)) {
                addColumn(row, OpenSextantSchema.TEXTPATH, recordTextFile);
            }
        } else {
            log.error("No File path given");
        }
    }

    /**
     * Adds the context.
     *
     * @param row the row
     * @param g   the g
     */
    protected void addContext(Feature row, TextMatch g) {
        addColumn(row, OpenSextantSchema.CONTEXT, g.getContext());
    }

    /**
     * Adds the match text.
     *
     * @param row the row
     * @param g   the g
     */
    protected void addMatchText(Feature row, TextMatch g) {
        addColumn(row, OpenSextantSchema.MATCH_TEXT, g.getText());
    }

    /**
     * Allows caller to add a method or pattern id of sorts to denote how match
     * was derived.
     *
     * @param row    the row
     * @param method the method
     */
    protected void addMatchMethod(Feature row, String method) {
        addColumn(row, OpenSextantSchema.MATCH_METHOD, method);
    }

    /**
     * Adds the match method.
     *
     * @param row   the row
     * @param match the match
     */
    protected void addMatchMethod(Feature row, TextMatch match) {
        String method = match.getType();
        addColumn(row, OpenSextantSchema.MATCH_METHOD, method);
    }

    /**
     * Builds a GISCore feature array (rows) from a given array of TextMatches;
     * Enrich the features with record-level attributes (columns). If provided
     * result has .input set, then conext and other metadata for this match will
     * be pulled from it. Context is not pulled at match time, as it is not used
     * by most processing -- it tends to be more of an output/formatting issue.
     * And only matches that pass any filters are enriched with context and
     * other metadaa.
     *
     * @param id            the id
     * @param g             the g
     * @param m             the m
     * @param rowAttributes the row attributes
     * @param res           the res
     * @return the list
     * @throws ConfigException schema configuration error
     */
    public List<Feature> buildRows(int id, Geocoding g, TextMatch m, Map<String, Object> rowAttributes,
                                   ExtractionResult res) throws ConfigException {

        Feature row = new Feature();
        // Administrative settings:
        row.setName(g.getPlaceName());
        row.setSchema(schema.getId());
        row.putData(OpenSextantSchema.SCHEMA_OID, id);

        //
        if (includeOffsets) {
            addOffsets(row, m);
        }

        addPlaceData(row, g);
        addPrecision(row, g);
        addConfidence(row, g.getConfidence());

        if (m.getContext() == null && res.input != null) {
            int len = res.input.buffer.length();
            ResultsUtility.setContextFor(res.input.buffer, m, len);
        }
        addContext(row, m);

        addMatchText(row, m);
        addMatchMethod(row, g.getMethod());

        addAdditionalAttributes(row, rowAttributes);

        if (res.recordFile != null) {
            addFilePaths(row, res.recordFile, res.recordTextFile);
        }

        // this is a list for M x N times
        List<Feature> features = new ArrayList<>();
        features.add(row);

        return features;

    }

    private static final DecimalFormat confFmt = new DecimalFormat("0.000");

    /**
     * Convenience method for managing how confidence number is reported in
     * output.
     *
     * @param conf the conf
     * @return the string
     */
    protected String formatConfidence(double conf) {
        return confFmt.format(conf);
    }

    /**
     * Gets the schema.
     *
     * @return the schema
     */
    public Schema getSchema() {
        return this.schema;
    }

    /**
     * Create a schema instance with the fields properly typed and ordered.
     *
     * @param jobName the job name
     * @return the schema
     * @throws ConfigException schema configuration error
     */
    protected Schema buildSchema(String jobName) throws ConfigException {

        if (this.schema != null) {
            return this.schema;
        }

        URI uri;
        try {
            uri = new URI("urn:OpenSextant");
        } catch (URISyntaxException e) {
            throw new ConfigException("URI parsing", e);
        }

        this.schema = new Schema(uri);
        // Add ID field to the schema
        this.schema.put(OpenSextantSchema.SCHEMA_OID);
        this.schema.setName(jobName);

        for (String field : field_order) {

            if (!this.includeOffsets && (field.equals("start") || field.equals("end"))) {
                continue;
            }

            if (!this.includeCoordinate && (field.equals("lat") || field.equals("lon"))) {
                continue;
            }

            SimpleField F = getField(field);
            this.schema.put(F);
        }

        this.field_set.addAll(field_order);

        return this.schema;
    }

    /**
     * Gets the field.
     *
     * @param field the field
     * @return the field
     * @throws ConfigException the config exception
     */
    protected SimpleField getField(String field) throws ConfigException {
        return OpenSextantSchema.getField(field);
    }

    /**
     * Can add.
     *
     * @param f the f
     * @return true, if successful
     */
    protected boolean canAdd(SimpleField f) {
        if (f == null) {
            return false;
        }
        return field_set.contains(f.getName()) && (schema.get(f.getName()) != null);
    }

    /**
     * Add a column of data to output; Field is validated ; value is not added
     * if null.
     *
     * @param row the row
     * @param f   the f
     * @param d   the d
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
     * Add a column of data to output; Field is validated.
     *
     * @param row the row
     * @param f   the f
     * @param d   the d
     */
    protected void addColumn(Feature row, SimpleField f, int d) {
        if (canAdd(f)) {
            row.putData(f, d);
        }
    }

    /**
     * Add a column of data to output; Field is validated.
     *
     * @param row the row
     * @param f   the field name
     * @param d   value
     */
    protected void addColumn(Feature row, SimpleField f, double d) {
        if (canAdd(f)) {
            row.putData(f, d);
        }
    }

    /**
     * Add a field key to the field order; Caller must also be responsible for
     * ensuring field is valid and exists in Schema.
     *
     * @param fld field name
     * @throws ConfigException the config exception
     */
    public void addField(String fld) throws ConfigException {
        if (getField(fld) == null) {
            throw new ConfigException("Field is not defined in Schema");
        }
        field_order.add(fld);
    }

    /**
     * Removes the field.
     *
     * @param fld field name
     * @throws ConfigException the config exception
     */
    public void removeField(String fld) throws ConfigException {
        if (getField(fld) == null) {
            throw new ConfigException("Field is not defined in Schema; Cannot remove non-existing field");
        }
        field_order.remove(fld);
    }

    /**
     * Default fields.
     */
    protected final void defaultFields() {
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
