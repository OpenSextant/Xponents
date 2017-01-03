package org.opensextant.extractors.geo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.ConfigException;
import org.opensextant.extraction.MatchFilter;
/*
 * We can filter out trivial place name matches that we know to be close to
 * false positives 100% of the time. E.g,. "way", "back", "north" You might
 * consider two different stop filters, Is "North" different than "north"?
 * This first pass filter should really filter out only text we know to be
 * false positives regardless of case.
 * 
 * Filter out unwanted tags via GazetteerETL data model or in Solr index. If
 * you believe certain items will always be filtered then set name_bias >
 * 0.0
 */
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

public class TagFilter extends MatchFilter {
    /**
     * This may need to be turned off for processing lower-case or dirty
     * data.
     */
    boolean filter_stopwords = true;
    boolean filter_on_case = true;
    Set<String> stopTerms = null;

    /**
     * NOTE:  This expects the files are all available. This fails if resource files are missing.
     * 
     * @throws ConfigException if any file has a problem. 
     */
    public TagFilter() throws ConfigException {
        super();
        stopTerms = new HashSet<>();
        String[] defaultNonPlaceFilters = {
                "/filters/non-placenames.csv", // GENERAL
                "/filters/non-placenames,spa.csv", // SPANISH 
                "/filters/non-placenames,acronym.csv" // ACRONYMS
        };
        for (String f : defaultNonPlaceFilters) {
            stopTerms.addAll(loadExclusions(GazetteerMatcher.class.getResourceAsStream(f)));
        }
    }

    public void enableStopwordFilter(boolean b) {
        filter_stopwords = b;
    }

    public void enableCaseSensitive(boolean b) {
        filter_on_case = b;
    }

    @Override
    public boolean filterOut(String t) {
        if (filter_on_case && StringUtils.isAllLowerCase(t)) {
            return true;
        }

        if (filter_stopwords) {
            if (stopTerms.contains(t.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Exclusions have two columns in a CSV file. 'exclusion', 'category'
     *
     * "#" in exclusion column implies a comment.
     * Call is responsible for getting I/O stream.
     *  
     * @param filestream
     *            URL/file with exclusion terms
     * @return set of filter terms
     * @throws ConfigException
     *             if filter is not found
     */
    public static Set<String> loadExclusions(InputStream filestream) throws ConfigException {
        /*
         * Load the exclusion names -- these are terms that are gazeteer
         * entries, e.g., gazetteer.name = <exclusion term>, that will be marked
         * as search_only = true.
         */
        try (Reader termsIO = new InputStreamReader(filestream)) {
            CsvMapReader termreader = new CsvMapReader(termsIO, CsvPreference.EXCEL_PREFERENCE);
            String[] columns = termreader.getHeader(true);
            Map<String, String> terms = null;
            HashSet<String> stopTerms = new HashSet<String>();
            while ((terms = termreader.read(columns)) != null) {
                String term = terms.get("exclusion");
                if (StringUtils.isBlank(term) || term.startsWith("#")) {
                    continue;
                }
                stopTerms.add(term.toLowerCase().trim());
            }
            termreader.close();
            return stopTerms;
        } catch (Exception err) {
            throw new ConfigException("Could not load exclusions.", err);
        }
    }

}
