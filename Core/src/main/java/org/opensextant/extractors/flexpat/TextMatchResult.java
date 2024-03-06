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
package org.opensextant.extractors.flexpat;

import java.util.List;

import org.opensextant.extraction.TextMatch;

/**
 * This result class holds all the results for a given text block.
 * Important: When iterating over your results, matches, you should @see
 * GeocoordMatch.is_submatch
 * to see if a match is part of another match.
 *
 * @author ubaldino
 */
public class TextMatchResult {

    /**
     *
     */
    public String result_id = null;
    /**
     *
     */
    public boolean pass = false;
    /**
     *
     */
    public boolean evaluated = false;
    /**
     *
     */
    public String message = "";
    /**
     *
     */
    public List<TextMatch> matches = null;

    private final StringBuilder msgTrace = new StringBuilder();

    /**
     * @param msg processing/matching message
     */
    public void add_trace(String msg) {
        msgTrace.append(msg);
        msgTrace.append("; ");
    }

    /**
     * @return message buffer for matching
     */
    public String get_trace() {
        return msgTrace.toString();
    }
}
