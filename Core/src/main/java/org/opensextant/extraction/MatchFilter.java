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
package org.opensextant.extraction;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.util.FileUtility;

/**
 * The Class MatchFilter.
 */
public class MatchFilter {

    /** The tag filter. */
    public Set<String> tagFilter = null;

    /** free-form filter */
    public MatchFilter() {

    }

    /**
     * Instantiates a new match filter.
     *
     * @param stopfile path to stopfile resource as it appears in CLASSPATH
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public MatchFilter(String stopfile) throws IOException {
        tagFilter = FileUtility.loadDictionary(stopfile, false);
    }

    public MatchFilter(URL stopfile) throws IOException {
        if (stopfile == null) {
            throw new IOException("Non-existent resource for URL");
        }
        tagFilter = FileUtility.loadDictionary(stopfile, false);
    }

    /**
     * If value is in stop list, then filter it out.
     *
     * @param value the value
     * @return true, if successful. True if the value is null
     */
    public boolean filterOut(String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        return tagFilter.contains(value);
    }
}
