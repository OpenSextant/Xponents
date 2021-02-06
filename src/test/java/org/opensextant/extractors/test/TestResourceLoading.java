package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.junit.Before;
import org.junit.Test;
import org.opensextant.extractors.geo.GazetteerMatcher;
import org.opensextant.util.LuceneStopwords;

public class TestResourceLoading {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void test() throws IOException {
        System.out.println(
                "This requires CLASSPATH set via maven or other means to include ./solr/etc/gazetteer/ or xponents-gaz-meta JAR");
        java.util.Set<Object> words = LuceneStopwords.getStopwords(new ClasspathResourceLoader(GazetteerMatcher.class),
                "ru");
        assertNotNull(words);
        System.out.println("Russian Stopwords count=" + words.size());
    }

}
