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

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class GeoTaggerMapper extends Mapper<BytesWritable, Text, BytesWritable, Text> {

    @Override
    public void cleanup(Context c) {
    }

    /**
     * Setup.  XTax or PlaceGecoder takes in SOLR path for xponents solr from JVM environment.
     */
    @Override
    public void setup(Context c) throws IOException {
    }

    /**
     * 
     */
    @Override
    public void map(BytesWritable key, Text textRecord, Context context)
            throws IOException, InterruptedException {
    }

}
