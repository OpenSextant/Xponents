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
package org.opensextant.processing;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opensextant.util.TextUtils;

/**
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class Parameters extends java.util.Properties {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /** Tag/Output countries */
    public boolean tag_countries = true;
    /** Tag/Output placenames of any time other than countries */
    public boolean tag_places = true;
    /** Tag/Output coordinates */
    public boolean tag_coordinates = true;
    /** Tag/Output codes of placenames -- Exclusive of Coutry or other Placenames */
    public boolean tag_codes = false;
    /** Tag/Output postal hierarchy, i.e., "City, Province, Postal code" */
    public boolean tag_postal = false;

    /** "taxons" is a catch all for the non-geo items that come out of geotagger, e.g.,
     * negated tags such as the surname "Johnson" -- typically a surname and not a city.
     * These tags are a by-product of Geotagging and available to caller.
     */
    public boolean tag_taxons = false;

    /**
     * "all taxons" is an explicit request to tag all keyphrases AKA taxons
     */
    public boolean tag_all_taxons = false;

    /**
     * Name taxons are a special grouping of tags, including "person_name" and other entries
     * Entity type is usually `person`.  This group is differentiated from non-name tags such as assets or codes.
     */
    public boolean tag_names = true;

    public boolean tag_patterns = false;
    public boolean tag_lowercase = false;
    public boolean clean_input = false;

    /**
     * Reverse Geo here is accommodated by resolving Provinces and Localities when a
     * geodetic coordinate is encountered in text. The containing province and/or
     * closest
     * feature is reported. Country code and ADM1 code, and Province Name are
     * inferred
     * and set on coordinate object, improving the contextual information for the
     * rest of the processing.
     */
    public boolean resolve_localities = false;

    /**
     * By default Country Names will not be included in GIS products
     * They should appear in CSV, though.
     */
    public boolean output_filtered = false;
    /**
     * Default is to not generate Geohash
     */
    public boolean output_geohash = false;

    /**
     * Is the concept of duplicate filtering more general than for just coords?
     */
    public boolean output_coordinate_duplicates = true;

    public String tempDir = "/tmp";
    public String outputDir = ".";
    // This is basically the file name for the output.
    private String jobName = null;
    public String inputFile = null;
    public String outputFile = null;

    private final Set<String> formats = new HashSet<>();

    /**
     * A way of relaying arbitrary geographic filters to an extraction routine
     * indicating that useful answers for
     * disambiguation for tie-breakers come from these cues.
     * "countries" = [c1, c2, c3, ...]
     * "geohash" = [g1, g2, g3, ...]
     */
    public HashMap<String, List<String>> preferredGeography = new HashMap<>();

    /**
     * You the caller must explicitly set isdefault = false;
     * forcing you to actually look at these parameters.
     */
    public boolean isdefault = true;

    public static final int FLAG_EXTRACT_CONTEXT = 0x10;
    public static final int FLAG_NO_COORDINATES = 0x20;

    /* DEFAULT RUNTIME FLAGS: */
    public static int RUNTIME_FLAGS = FLAG_EXTRACT_CONTEXT;

    /**
     * Processing will support multiple output formats
     *
     * @param fmt requested format
     */
    public void addOutputFormat(String fmt) {
        formats.add(fmt);
    }

    public Set<String> getOutputFormats() {
        return formats;
    }

    public static final String INVALID_FCNAME_CHAR = "\\/+-.;, $&"; // Basically only ASCII A-Z, 0-9 are valid.

    public void setJobName(String nm) {
        jobName = TextUtils.fast_replace(nm, INVALID_FCNAME_CHAR, "_");
    }

    public String getJobName() {
        return jobName;
    }

    private static final DateTimeFormatter procdate_fmt = DateTimeFormat.forPattern("yyyyMMMdd_HHmm");

    /**
     * Generates a simple job date/time key for the job
     *
     * @return 12-digit processing date format date+time
     */
    public static String getJobTimestamp() {
        return procdate_fmt.print(new Date().getTime());
    }
}
