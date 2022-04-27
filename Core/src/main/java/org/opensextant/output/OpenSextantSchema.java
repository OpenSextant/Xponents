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

package org.opensextant.output;

import java.util.HashMap;
import java.util.Map;

import org.opensextant.ConfigException;
import org.opensextant.giscore.events.SimpleField;

/**
 * GISCore-based output schema.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public final class OpenSextantSchema {

    public static final SimpleField SCHEMA_OID = new SimpleField("id", SimpleField.Type.OID);
    /** Match Text captures the raw text matched by the tagger. */
    public static final SimpleField MATCH_TEXT = new SimpleField("matchtext", SimpleField.Type.STRING);
    /** ISO Country code */
    public static final SimpleField ISO_COUNTRY = new SimpleField("iso_cc", SimpleField.Type.STRING);
    /** Geonames Feature class */
    public static final SimpleField FEATURE_CLASS = new SimpleField("feat_class", SimpleField.Type.STRING);
    /** Geonames style feature coding */
    public static final SimpleField FEATURE_CODE = new SimpleField("feat_code", SimpleField.Type.STRING);
    /**
     * confidence 0.000 to 1.000 suggests our confidence that we code the MATCH TEXT
     * to the right LAT/LON
     * this is a string for now to keep the actual sig-figs accurate.
     */
    public static final SimpleField CONFIDENCE = new SimpleField("confidence", SimpleField.Type.STRING);
    /**
     * Number of meters of error in coordinate of location. Example, a city location
     * match is likely to be 1-2 KM of error
     * depending on which gazetteer is referenced. A coordinate's precision is
     * implied by number of decimal places, etc.
     */
    public static final SimpleField PRECISION = new SimpleField("precision", SimpleField.Type.INT);
    /**
     * the name in the Gazetteer entry; which aligns with the MATCH TEXT
     */
    public static final SimpleField PLACE_NAME = new SimpleField("placename", SimpleField.Type.STRING);

    /** Field names: filepath */
    public static final String FILEPATH_FLD = "filepath";

    /** Optionally the File path for the text */
    public static final SimpleField FILEPATH = new SimpleField(FILEPATH_FLD, SimpleField.Type.STRING);
    public static final SimpleField FILENAME = new SimpleField("filename", SimpleField.Type.STRING);
    public static final SimpleField TEXTPATH = new SimpleField("textpath", SimpleField.Type.STRING);

    /**
     * A text window around the MATCH TEXT delineated by START/END offsets. Default
     * window size is +/- 150 characters
     */
    public static final SimpleField CONTEXT = new SimpleField("context", SimpleField.Type.STRING);
    public static final SimpleField START_OFFSET = new SimpleField("start", SimpleField.Type.INT);
    public static final SimpleField END_OFFSET = new SimpleField("end", SimpleField.Type.INT);
    /** The method used to match the data in MATCH TEXT */
    public static final SimpleField MATCH_METHOD = new SimpleField("method", SimpleField.Type.STRING);
    public static final SimpleField PROVINCE = new SimpleField("province", SimpleField.Type.STRING);
    public static final SimpleField LAT = new SimpleField("lat", SimpleField.Type.FLOAT);
    public static final SimpleField LON = new SimpleField("lon", SimpleField.Type.FLOAT);
    private static final Map<String, SimpleField> fields = new HashMap<>();

    static {
        fields.put("id", SCHEMA_OID);
        fields.put("matchtext", MATCH_TEXT);
        fields.put("iso_cc", ISO_COUNTRY);
        fields.put("feat_class", FEATURE_CLASS);
        fields.put("feat_code", FEATURE_CODE);
        fields.put("confidence", CONFIDENCE);
        fields.put("precision", PRECISION);
        fields.put("placename", PLACE_NAME);
        fields.put(FILEPATH_FLD, FILEPATH);
        fields.put("filename", FILENAME);
        fields.put("textpath", TEXTPATH);
        fields.put("context", CONTEXT);
        fields.put("start", START_OFFSET);
        fields.put("end", END_OFFSET);
        fields.put("lat", LAT);
        fields.put("lon", LON);
        fields.put("province", PROVINCE);
        fields.put("method", MATCH_METHOD);
    }

    public static SimpleField getField(String f) throws ConfigException {
        SimpleField F = fields.get(f);
        if (F == null) {
            throw new ConfigException("Field " + f + "not found");
        }
        return F;
    }

    /**
     * Cache an arbitrary date field in schema
     */
    public static void addDateField(String f) {
        fields.put(f, new SimpleField(f, SimpleField.Type.DATE));
    }

    /**
     * Cache an arbitrary text field in schema
     */
    public static void addTextField(String f) {
        fields.put(f, new SimpleField(f, SimpleField.Type.STRING));
    }
}
