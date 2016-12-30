/*
 * This software was produced for the U. S. Government
 * under Basic Contract No. W15P7T-13-C-A802, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 *
 * Copyright (C) 2016 The MITRE Corporation.
 * Copyright (C) 2016 OpenSextant.org
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
package org.opensextant.mapreduce;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.opensextant.ConfigException;
import org.opensextant.data.Geocoding;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;

public class GeoTaggerMapper extends AbstractMapper {
    private PlaceGeocoder geocoder = null;
    private Logger log = LoggerFactory.getLogger(GeoTaggerMapper.class);

    public static int MIN_CONFIDENCE = 15; /* 0..100 scale for confidence of IS_A_PLACE AND IS_CORRECT_LOCATION */
    public static int MAX_PRECISION_ERROR = 10000; /* METERS of error in precision */

    @Override
    public void cleanup(Context c) {
        if (geocoder != null) {
            geocoder.shutdown();
        }
    }

    /**
     * Setup.  XTax or PlaceGecoder takes in SOLR path for xponents solr from JVM environment.
     */
    @Override
    public void setup(Context c) throws IOException {
        super.setup(c);

        try {
            geocoder = new PlaceGeocoder();
            geocoder.configure();
        } catch (ConfigException e) {
            // TODO Auto-generated catch block
            throw new IOException("setup.PlaceGeocoder", e);
        }

        log.info("DONE");
    }

    /**
     * 
     */
    @Override
    public void map(BytesWritable key, Text textRecord, Context context)
            throws IOException, InterruptedException {

        ++counter;
        TextInput textObj = prepareInput(null, textRecord);
        if (textObj == null) {
            return;
        }

        /* LANG ID = 'ENGLISH',
         * If this is not true, then you need to add LangID to your metadata or detect it live
         */
        textObj.langid = "en";
        HashSet<String> dedup = new HashSet<>();
        try {

            List<TextMatch> matches = geocoder.extract(textObj);

            if (matches.isEmpty()) {
                return;
            }

            Text oid = new Text(textObj.id);

            /* NORMALIZE findings.
             * Reduce all matches, minimizing duplicates, removing whitespace, etc.
             *
             */
            int filtered = 0, duplicates = 0;
            for (TextMatch tm : matches) {
                /* DEDUPLICATE */
                if (dedup.contains(tm.getText())) {
                    duplicates += 1;
                    continue;
                }

                /* FILTER OUT NOISE */
                if (filterOutMatch(tm)) {
                    continue;
                }
                /* FORMAT */
                JSONObject o = match2JSON(tm);
                dedup.add(tm.getText());
                Text matchOutput = new Text(o.toString());
                /* SERIALIZE GEOCODING */
                context.write(oid, matchOutput);
            }
            if (log.isTraceEnabled()) {
                log.trace("For key {}, found={}, junk filtered={}, duplicates={}",
                        key.toString(), matches.size(), filtered, duplicates);
            }
        } catch (Exception err) {
            log.error("Error running geotagger", err);
        }
    }

    /**
     * Determine if you want to keep this or not.
     * @param tm
     * @return
     */
    public boolean filterOutMatch(final TextMatch tm) {
        if (tm instanceof PlaceCandidate) {
            if (((PlaceCandidate) tm).getConfidence() < MIN_CONFIDENCE) {
                return true;
            }
        } else if (tm instanceof GeocoordMatch) {

            /* Geocoding coordinates can also be noisy.  Accept only high precision matches.
             * E.g., +/- 10KM
             */
            GeocoordMatch geo = (GeocoordMatch) tm;
            return (geo.getPrecision() > MAX_PRECISION_ERROR);
        }
        return false;
    }

    /**
     * Convert a TextMatch (Place, Taxon, Pattern, etc.) and convert to JSON.
     * This outputs only geocoding objects -- PlaceCandidate or GeocoordMatch. 
     * Attributes reported are gazetteer metadata, precision, country code, confidence of match, etc.
     * 
     * @param tm
     * @return
     */
    public static final JSONObject match2JSON(TextMatch tm) {
        JSONObject j = prepareOutput(tm);

        if (tm instanceof PlaceCandidate) {
            PlaceCandidate candidate = (PlaceCandidate) tm;
            if (candidate.isCountry) {
                j.put("type", "country");
            } else {
                j.put("type", "place");
            }

            /* Geotagging can be noisy -- accept only highest confidence matches.
             * 
             */
            if (candidate.getFirstChoice() != null) {
                addPlaceData(candidate.getFirstChoice(), j);
                j.put("confidence", candidate.getConfidence());

                if (candidate.getSecondChoice() != null) {
                    JSONObject alt = new JSONObject();
                    addPlaceData(candidate.getSecondChoice(), alt);
                    alt.put("score", candidate.getSecondChoice().getScore());
                    j.put("alt-place", alt);
                }
            }
        } else if (tm instanceof GeocoordMatch) {

            /* Geocoding coordinates can also be noisy.  Accept only high precision matches.
             * E.g., +/- 10KM
             */
            GeocoordMatch geo = (GeocoordMatch) tm;
            addPlaceData(geo, j);
            j.put("type", "coordinate");
            j.put("method", geo.getMethod());
            j.put("confidence", geo.getConfidence());
            if (geo.getRelatedPlace() != null) {
                JSONObject alt = new JSONObject();
                addPlaceData(geo.getRelatedPlace(), alt);
                j.put("related-place", alt);
            }
        }
        return j;
    }

    /**
     * A very indiscriminate mapping of Geocoding to JSON.  This output schema does not 
     * distinguish much between coordinates, country names/codes, cities, etc.  EVERYTHING
     * has a lat/lon, gazetteer metadata, confidence, name, etc.
     * 
     * @param place
     * @param j
     */
    protected static final void addPlaceData(Geocoding place, JSONObject j) {
        j.put("name", place.getPlaceName());
        j.put("cc", place.getCountryCode());
        j.put("feat_code", place.getFeatureCode());
        j.put("lat", place.getLatitude());
        j.put("lon", place.getLongitude());
        j.put("precision", place.getPrecision());
        if (place.getAdmin1() != null) {
            j.put("adm1", place.getAdmin1());
        }
        if (place.getAdmin2() != null) {
            j.put("adm2", place.getAdmin2());
        }
    }
}
