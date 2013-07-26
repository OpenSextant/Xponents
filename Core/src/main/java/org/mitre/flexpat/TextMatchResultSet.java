/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.flexpat;

import java.util.List;

/**
 * This result class holds all the results for a given text block.
 * Important:  When iterating over your results, matches, you should @see GeocoordMatch.is_submatch
 * to see if a match is part of another match.
 * 
 * @author ubaldino
 */
public class TextMatchResultSet {

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

    /**
     *
     */
    public TextMatchResultSet() {
    }
    
    private StringBuilder _msg_trace = new StringBuilder();

    /**
     *
     * @param msg
     */
    public void add_trace(String msg) {
        _msg_trace.append(msg);
        _msg_trace.append("; ");
    }

    /**
     *
     * @return
     */
    public String get_trace() {
        return _msg_trace.toString();
    }
}
