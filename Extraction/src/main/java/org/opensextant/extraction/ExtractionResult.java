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

package org.opensextant.extraction;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.opensextant.processing.Parameters;

/**
 *
 * @author ubaldino
 */
public class ExtractionResult {

    protected static final Parameters DEFAULT_FILTERS = new Parameters();
    public Map<String, Object> attributes = null;
    /**
     * Original file for record
     */
    public String recordFile = null;
    /**
     * short ID or name of file
     */
    public String recordID = null;
    /**
     * Text version of file used for processing
     */
    public String recordTextFile = null;
    /**
     *
     */
    public List<TextMatch> matches = new ArrayList<TextMatch>();

    public ExtractionResult() {
    }

    /**
     * Given a record ID, create a container for holding onto all the geocodes
     * for that particular data object.
     * @param rid result ID, optionally null
     */
    public ExtractionResult(String rid) {
        recordID = rid;
        if (recordID == null) {
            recordID = "";
        }
    }

    /**
     * Add some piece of amplifying metadata about the record which may be
     * carried through to output format in some way
     * @param f field 
     * @param v value
     */
    public void addAttribute(String f, Object v) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }
        attributes.put(f, v);
    }
}
