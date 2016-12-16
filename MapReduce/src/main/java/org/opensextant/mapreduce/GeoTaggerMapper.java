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
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.opensextant.ConfigException;
import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeoTaggerMapper extends AbstractMapper {
    private PlaceGeocoder geocoder = null;
    private Logger log = LoggerFactory.getLogger(GeoTaggerMapper.class);


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
        String text = null;
        HashSet<String> dedup = new HashSet<>();

        try {
            JSONObject obj = JSONObject.fromObject(textRecord.toString());
            if (!obj.containsKey("text")) {
                return;
            }
            String text_id = key.toString();
            text = obj.getString("text");
            TextInput textObj = new TextInput(text_id, text);
            textObj.langid = "en";
            /* LANG ID = 'ENGLISH',
             * If this is not true, then you need to add LangID to your metadata or detect it live
             */

            List<TextMatch> matches = geocoder.extract(textObj);

            if (matches.isEmpty()) {
                return;
            }

            /* NORMALIZE findings.
             * Reduce all matches, minimizing duplicates, removing whitespace, etc.
             *
             */
            int filtered = 0, duplicates = 0;
            for (TextMatch tm : matches) {

//                if (filterCrap(tm.getText())) {
//                    filtered += 1;
//                    continue;
//                }
                if (dedup.contains(tm.getText())) {
                    duplicates += 1;
                    continue;
                }
                dedup.add(tm.getText());
                JSONObject o = match2JSON(tm);
                Text matchOutput = new Text(o.toString());
                context.write(NullWritable.get(), matchOutput);
            }
            if (log.isTraceEnabled()) {
                log.trace("For key " + new String(key.getBytes(), StandardCharsets.UTF_8) +
                        " found " + matches.size() + ", filtered: " + filtered + " as junk, " + duplicates +" duplicates.");
            }
        } catch (Exception err) {
            log.error("Error running geotagger", err);
        }
    }

    /**
     * Convert a TextMatch (Place, Taxon, Pattern, etc.) and convert to JSON.
     * @param tm
     * @return
     */
    public static final JSONObject match2JSON(TextMatch tm) {
        JSONObject j = new JSONObject();
        j.put("type", tm.getType());
        j.put("value", TextUtils.squeeze_whitespace(tm.getText()));
        j.put("offset", tm.start);
        if (tm instanceof PlaceCandidate) {
            PlaceCandidate candidate = (PlaceCandidate) tm;
            if (!candidate.getPlaces().isEmpty()) {
                candidate.choose();
                JSONObject obj = parsePlace(candidate.getFirstChoice());
                if (obj != null) {
                    JSONArray places = new JSONArray();
                    places.add(obj);
                    obj = parsePlace(candidate.getSecondChoice());
                    if (obj != null) {
                        places.add(obj);
                    }
                    j.put("places", places);
                }
            }
        } else if (tm instanceof GeocoordMatch) {
            GeocoordMatch geo = (GeocoordMatch) tm;
            j.put("latitude", geo.getLatitude());
            j.put("longitude", geo.getLongitude());
            if (geo.getRelatedPlace() != null) {
                addPlaceData(geo.getRelatedPlace(), j);
            }
        }
        return j;
    }

    private static final JSONObject parsePlace(ScoredPlace place) {
        if (place == null) {
            return null;
        }

        JSONObject j = new JSONObject();
        j.put("score", place.getScore());
        addPlaceData(place, j);
        return j;
    }

    private static final void addPlaceData(Place place, JSONObject j) {
        j.put("name", place.getName());
        j.put("adm1", place.getAdmin1());
        j.put("adm2", place.getAdmin2());
        j.put("cc", place.getCountryCode());
        j.put("featureCode", place.getFeatureCode());
    }

    /**
     * Filtration rules -- what is worth reporting in your output?  You decide.
     *
     * @param t
     * @return
     */
    private static final boolean filterCrap(final String t) {
        if (t.length() <= 3) {
            return true;
        }
        final char c = t.charAt(0);
        if (c == '@') {
            return true;
        }
        if (t.startsWith("http")) {
            return true;
        }
        return false;
    }
}
