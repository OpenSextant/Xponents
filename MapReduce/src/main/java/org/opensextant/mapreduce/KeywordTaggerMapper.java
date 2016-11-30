/**
 *
 * Copyright 2016 The MITRE Corporation.
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
 *
 */
package org.opensextant.mapreduce;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.opensextant.ConfigException;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;

/**
 *  A mapper that takes in a KEY, TEXT value.
 *  The map() routine processes some found text -- this demo assumes input is JSON, and JSON has a "text" field.
 *  Tag the text and write out with the KEY any findings.
 *  k1,{ "type":"taxon", "value":"Paul Bunyan", "offset":455, ...}
 *  k1,{ "type":"taxon", "value":"Mary", "offset":1, ...}
 *  k2,{ "type":"taxon", "value":"Mother Goose", "offset":87, ...}
 *  
 */
public class KeywordTaggerMapper extends Mapper<BytesWritable, Text, BytesWritable, Text> {
    private TaxonMatcher xtax = null;
    private Logger log = LoggerFactory.getLogger(KeywordTaggerMapper.class);

    @Override
    public void cleanup(Context c) {
        if (xtax != null) {
            xtax.shutdown();
        }
    }

    /**
     * Setup.  XTax or PlaceGecoder takes in SOLR path for xponents solr from JVM environment.
     */
    @Override
    public void setup(Context c) throws IOException {
        try {
            xtax = new TaxonMatcher();
        } catch (ConfigException e) {
            // TODO Auto-generated catch block
            throw new IOException("setup.XTax", e);
        }

        log.info("DONE");
    }

    static long MAX_SHUTOFF = 10000;
    long counter = 0;

    /**
     * 
     */
    @Override
    public void map(BytesWritable key, Text textRecord, Context context)
            throws IOException, InterruptedException {
        /*
         * 
         */
        if (counter >= MAX_SHUTOFF) {
            //System.exit(-1);
            throw new IOException("Testing only hit max records.");
        }
        ++counter;
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

            /*
             * Testing to see if XTax tagger operates in Hadoop job
             */
            List<TextMatch> matches = xtax.extract(textObj);

            if (matches.isEmpty()) {
                return;
            }

            /* NORMALIZE findings.
             * Reduce all matches, minimizing duplicates, removing whitespace, etc.
             * 
             */
            for (TextMatch tm : matches) {
                if (filterCrap(tm.getText())) {
                    continue;
                }
                if (dedup.contains(tm.getText())) {
                    continue;
                }
                dedup.add(tm.getText());
                JSONObject o = match2JSON(tm);
                Text matchOutput = new Text(o.toString());
                context.write(key, matchOutput);
            }
        } catch (Exception err) {
            log.error("\t\t\t", err.getMessage());
            // System.exit(-1);
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
        if (tm instanceof TaxonMatch) {
            for (Taxon tx : ((TaxonMatch) tm).getTaxons()) {
                j.put("name", tx.name);
                j.put("cat", tx.catalog);
                break; /* Demo: we only capture the first Taxon Match */
            }
        }
        return j;
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
