/*
 * Copyright 2013 ubaldino at mitre dot org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensextant.data;

import org.apache.commons.lang3.StringUtils;

/**
 * Simple mapping of ISO 639 id to display name for languages
 *
 * @author ubaldino
 */
public class Language {

    private String iso2 = null;
    private String iso3 = null;
    private String name = null;
    private String namecode = null;

    public Language(String id, String n) {
        this(null, id, n);
    }

    /**
     * A normalize view of a Language - ISO 639-2 and -1 codes and display name
     * codes are lower cased.
     *
     * @param id3 ISO 639-2 3-alpha code
     * @param id2 ISO 639-2 2-alpha code
     * @param n   name of language
     */
    public Language(String id3, String id2, String n) {
        if (StringUtils.isNotBlank(id3)) {
            iso3 = id3.toLowerCase();
        }
        if (StringUtils.isNotBlank(id2)) {
            iso2 = id2.toLowerCase();
        }
        this.name = n;
        if (name != null) {
            namecode = name.toLowerCase();
        }
    }

    /** @return display name of language */
    public String getName() {
        return this.name;
    }

    public String getNameCode() {
        return this.namecode;
    }

    /**
     * @return ISO 639-2 3-char code
     */
    public String getCode() {
        return this.iso3;
    }

    public String getISO639_1_Code() {
        return this.iso2;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", getName(), getCode());
    }
}
