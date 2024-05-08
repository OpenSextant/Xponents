/*
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

/**
 * Utility class to help access Lucene stop words, mainly as they exist in Solr
 * index
 * stopword files are in either list form or snowball format. getStopwords()
 */
public class LuceneStopwords {

    static final Set<String> SNOWBALL_SETS = new HashSet<>();
    static {
        SNOWBALL_SETS.addAll(TextUtils.string2list("da, de, es, fi, fr, hu, it, nl no, pt, ru, sv", ","));
    }

    private static String defaultPath(String lang) {
        return String.format("/lang/stopwords_%s.txt", lang.toLowerCase());
    }

    /**
     * Simple wrapper around Lucene resource loading to access Solr-provided stop
     * lists.
     *
     * @param loader    classpath loader
     * @param givenLang ISO 2-char language ID used by lucene for lang-specific
     *                  filters (./lang)
     * @return set of stopwords in Lucene API construct
     * @throws IOException if resource files are not found in classpath (JAR or solr
     *                     core ./conf/lang/
     */
    public static Set<Object> getStopwords(ResourceLoader loader, String givenLang) throws IOException {
        String lang = givenLang.toLowerCase();
        HashMap<String, String> configurationArgs = new HashMap<>();
        configurationArgs.put("words", defaultPath(lang));
        configurationArgs.put("format", SNOWBALL_SETS.contains(lang) ? "snowball" : "wordset");
        StopFilterFactory filter = new StopFilterFactory(configurationArgs);
        filter.inform(loader);

        return filter.getStopWords();
    }

}
