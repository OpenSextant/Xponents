/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
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
    public List<String> regex_groups = new ArrayList<>();
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
     * Name of a Java class that extends TextMatch and implements user's
     * normalization routines.
     */
    public String match_classname = null;
    public Class<?> match_class = null;
    public String family = null;

    /**
     * Metadata for a Regular Expression pattern
     *
     * @param _family      name of family
     * @param _id          pattern id within family
     * @param _description optional description of pattern
     */
    public RegexPattern(String _family, String _id, String _description) {
        family = _family;
        id = _id;
        description = _description;
    }

    /**
     * @return string representation of the pattern
     */
    @Override
    public String toString() {
        return id + ", Pattern:" + regex.pattern();
    }
}
