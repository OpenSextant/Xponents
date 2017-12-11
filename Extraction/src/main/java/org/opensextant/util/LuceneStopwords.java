/**
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
 *
 * **************************************************************************
 * (c) Copyright 2017 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 *
 */
package org.opensextant.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.util.ResourceLoader;

/** Utility class to help access Lucene stop words, mainly as they exist in Solr index
 * stopword files are in either list form or snowball format.  getStopwords()
 */ 
public class LuceneStopwords {

    static Set<String> SNOWBALL_SETS = new HashSet<>();
    static {
        SNOWBALL_SETS.addAll(TextUtils.string2list("da, de, es, fi, fr, hu, it, nl no, pt, ru, sv", ","));
    }

    private static String defaultPath(String lang) {
        return String.format("/lang/stopwords_%s.txt", lang.toLowerCase());
    }

    /**
     * Simple wrapper around Lucene resource loading to access Solr-provided stop lists.
     * @param loader classpath loader
     * @param givenLang ISO 2-char language ID used by lucene for lang-specific filters (./lang)
     * @return
     * @throws IOException
     */
    public static Set<Object> getStopwords(ResourceLoader loader, String givenLang) throws IOException {
        String lang = givenLang.toLowerCase();
        HashMap<String, String> configurationArgs = new HashMap<>();
        configurationArgs.put("words", defaultPath(lang));
        configurationArgs.put("format", SNOWBALL_SETS.contains(lang) ? "snowball" : "wordset");
        configurationArgs.put("luceneMatchVersion", "6.6");
        StopFilterFactory filter = new StopFilterFactory(configurationArgs);
        filter.inform(loader);

        return filter.getStopWords();
    }

}
