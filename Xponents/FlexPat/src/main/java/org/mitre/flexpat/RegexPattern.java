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

import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author ubaldino
 */
public class RegexPattern {

    /**
     *
     */
    public String id = null;
    /**
     *
     */
    public String parser_rule = null; // hold
    /**
     *
     */
    public Pattern regex = null;
    /**
     *
     */
    public List<String> regex_groups = new ArrayList<String>();
    /**
     *
     */
    public String version = null;
    /**
     *
     */
    public String description = null;
    /**
     *
     */
    public boolean enabled = true;

    /**
     *
     * @param _id
     * @param _description
     */
    public RegexPattern(String _id, String _description) {
        id = _id;
        description = _description;
    }

    /**
     *
     * @return
     */
    public String toString() {
        return id + ", Pattern:" + regex.pattern().toString();
    }
}
