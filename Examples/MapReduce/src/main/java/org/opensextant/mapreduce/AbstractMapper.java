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

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;

import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * Common configuration for the mappers.
 */
public abstract class AbstractMapper extends Mapper<BytesWritable, Text, Text, Text> {

    protected long counter = 0;

    /**
     * Configures logging.
     */
    @Override
    public void setup(Context c) throws IOException {
        LoggingUtilities.configureLogging(c.getConfiguration());
    }

    /**
     * A common method for converting a Text object into an Xponents TextInput tuple.
     * The assumptions for this demonstration method are:
     * <ul>
     * <li>input is JSON data and can be parsed as such</li>
     * <li>JSON data contains a top level "text" field, which will be used for extraction.</li>
     * <li>record ID is the result of key.toString(), or if key is null, then use JSON get('id') </li>
     * </ul>
     * Caller can optionally set Language ID of text.
     * @param key record ID, optionally null.
     * @param textRecord a JSON formatted object.
     * @return TextInput pair.  
     */
    protected static TextInput prepareInput(final Object key, final Text textRecord) {
        JSONObject obj = JSONObject.fromObject(textRecord.toString());
        if (!obj.containsKey("text")) {
            return null;
        }
        String text_id = null;
        if (key != null) {
            text_id = key.toString();
        } else {
            text_id = obj.getString("id");
        }
        String text = obj.getString("text");
        return new TextInput(text_id, text);
    }

    /**
     * Given an Xponents match, produce a common JSON output.
     * @param tm
     * @return
     */
    protected static JSONObject prepareOutput(final TextMatch tm) {
        JSONObject j = new JSONObject();
        j.put("type", tm.getType());
        j.put("value", TextUtils.squeeze_whitespace(tm.getText()));
        j.put("offset", tm.start);
        return j;
    }

}
