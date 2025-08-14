package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.opensextant.extraction.TagFilter;
import org.opensextant.extractors.geo.LanguageFilter;
import org.opensextant.extractors.xtax.TaxonFilter;
import org.opensextant.util.FileUtility;
import static org.junit.Assert.*;

public class TestStopFilters {

    public static void main(String[] args) {
        try {
            testTagFilter(args[0]);
        } catch (Exception err) {
        }

    }

    public static void testTagFilter(String file) throws IOException {
        TagFilter filt = new TagFilter();
        String buf = FileUtility.readFile(new File(file));
        for (String tok : buf.split("\\s+")) {
            if (filt.filterOut(tok.toLowerCase())) {
                System.out.println("STOP " + tok);
            }
        }
    }

    // @Test
    public void test() {
        String[] langSet = {"ja", "cjk", "th", "vi", "id", "ar"};
        Map<String, Set<String>> stopFilters = new HashMap<>();

        for (String lg : langSet) {
            String url = String.format("/org/apache/lucene/analysis/%s/stopwords.txt", lg);
            URL obj = URL.class.getResource(url);
            if (obj == null) {
                continue;
            }
            try (InputStream strm = obj.openStream()) {
                HashSet<String> stopTerms = new HashSet<>();
                for (String line : IOUtils.readLines(strm, StandardCharsets.UTF_8)) {
                    if (line.trim().startsWith("#")) {
                        continue;
                    }
                    stopTerms.add(line.trim());
                }
                if (stopTerms.isEmpty()) {
                    fail("No terms added");
                }
                stopFilters.put(lg, stopTerms);

            } catch (IOException ioe) {
                fail("URL not found " + url);
            }

            if (stopFilters.isEmpty()) {
                fail("No Filters found in classpath");
            }
        }
        for (String lang : stopFilters.keySet()) {
            Set<String> set = stopFilters.get(lang);
            System.out.printf("Lang %s= %d terms%n", lang, set.size());
        }

    }

    @Test
    public void testLangFilter() {

        LanguageFilter filt = new LanguageFilter("es");
        assertFalse(filt.filterOut("hola"));

        filt = new LanguageFilter("zh");
        assertFalse(filt.filterOut("威胁恐吓"));
        assertTrue(filt.filterOut("威胁"));

        filt = new LanguageFilter("ar");
        assertFalse(filt.filterOut("تاريختاريخ"));
        assertTrue(filt.filterOut("تاريخ"));
    }

    @Test
    public void testFilters() throws IOException {
        TaxonFilter filter = new TaxonFilter();
        assertTrue(filter.filterOut("A%B"));
        assertFalse(filter.filterOut("A-B"));
        assertTrue(filter.filterOut("A-B/C-D"));
    }

    @Test
    public void testCustomStopFilters() throws IOException {

        TagFilter filt = new TagFilter();

        // Test stop terms are stopped.
        String[] terms = {"سبعمائة", "سبعمئة", "سبعون"};
        for (String term : terms) {
            assertTrue(filt.filterOut("ar", term));
        }

        // Test non-stop terms are not stopped:
        String term2 = "المالية";
        assertFalse(filt.filterOut("ar", term2));
    }
}
