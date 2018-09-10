package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.opensextant.ConfigException;
import org.opensextant.extractors.geo.TagFilter;
import org.opensextant.util.FileUtility;

public class TestStopFilters {

    public static void main(String[] args) {
        try {
            testTagFilter(args[0]);
        } catch (Exception err) {
        }

    }

    public static void testTagFilter(String file) throws IOException, ConfigException {
        TagFilter filt = new TagFilter();
        String buf = FileUtility.readFile(new File(file));
        for (String tok : buf.split("\\s+")) {
            if (filt.filterOut(tok.toLowerCase())) {
                System.out.println("STOP " + tok);
            }
        }
    }

    //@Test
    public void test() {
        String[] langSet = { "ja", "cjk", "th", "vi", "id", "ar" };
        Map<String, Set<String>> stopFilters = new HashMap<>();

        for (String lg : langSet) {
            String url = String.format("/org/apache/lucene/analysis/%s/stopwords.txt", lg);
            URL obj = URL.class.getResource(url);
            if (obj == null) {
                continue;
            }
            try (InputStream strm = obj.openStream()) {
                HashSet<String> stopTerms = new HashSet<>();
                for (String line : IOUtils.readLines(strm, Charset.forName("UTF-8"))) {
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
            System.out.println(String.format("Lang %s= %d terms", lang, set.size()));
        }
    }

}
