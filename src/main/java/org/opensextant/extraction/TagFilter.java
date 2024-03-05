package org.opensextant.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;
import org.opensextant.extractors.geo.GazetteerMatcher;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.LuceneStopwords;
import org.opensextant.util.TextUtils;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;
/*
 * We can filter out trivial place name matches that we know to be close to
 * false positives 100% of the time. E.g,. "way", "back", "north" You might
 * consider two different stop filters, Is "North" different than "north"?
 * This first pass filter should really filter out only text we know to be
 * false positives regardless of case.
 *
 * Filter out unwanted tags via GazetteerETL data model or in Solr index. If
 * you believe certain items will always be filtered OUT then set name_bias < 0
 */

public class TagFilter extends MatchFilter {
    /**
     * This may need to be turned off for processing lower-case or dirty data.
     */
    boolean filter_stopwords = true;
    boolean filter_on_case = true;
    Set<String> nonPlaceStopTerms = null;
    public final static int GENERIC_LOWERCASE_MIN = 12;

    /*
     * Select languages for experimentation.
     */
    private final Map<String, Set<Object>> langStopFilters = new HashMap<>();

    /**
     * TagFilter is provides access to a superset of all stop filters for placenames, etc.
     * for general purpose tag filtering.   This is tailored to placenames but also has general stop filters by lang
     *
     * @throws ConfigException if any file has a problem.
     */
    public TagFilter() throws IOException {
        super();
        // Load specific Xponents filters to negate things that are
        // generally not place names.
        // ------------------------------------------------------
        nonPlaceStopTerms = new HashSet<>();
        String[] defaultNonPlaceFilters = {"/filters/non-placenames.csv", // GENERAL
                "/filters/non-placenames,spa.csv", // SPANISH
                "/filters/non-placenames,deu.csv", // GERMAN
                "/filters/non-placenames,rus,ukr.csv", // RUSSIAN, UKRANIAN
                "/filters/non-placenames,acronym.csv" // ACRONYMS
        };
        for (String f : defaultNonPlaceFilters) {
            nonPlaceStopTerms.addAll(loadExclusions(GazetteerMatcher.class.getResourceAsStream(f)));
        }

        // NOTE: these stop word sets are of format='wordset' -- as they are Lucene produced data sets
        // Whereas other languages (es, it, etc.) are provided in format='snowball'
        // StopFilterFactory is needed to load snowball filters.
        // ------------------------------------------------------
        String[] langSet = {
                "ja", "ko", "zh",
                "ar", "fa", "ur",
                "th", "tr", "id", "tl", "vi",
                "ru", "it", "pt", "de", "nl", "es", "en"};
        loadLanguageStopwords(langSet);

        // SPECIFIC NON-PLACES BY LANGUAGE; and GENERIC STOPWORDS
        // ------------------------------------------------------
        String lang = "ar";
        String[] langSpecificStops = {
                "/filters/non-placenames,ara.csv",
                "/lang/stopwords_ar_mohataher.txt"};
        for (String stops : langSpecificStops) {
            langStopFilters.get("ar").addAll(loadExclusions(GazetteerMatcher.class.getResourceAsStream(stops)));
        }
    }

    /**
     * Load default Lucene stop words to aid in language specific filtration.
     *
     * @param langids
     * @throws IOException
     * @throws ConfigException
     */
    private void loadLanguageStopwords(String[] langids) throws IOException, ConfigException {
        for (String lg : langids) {
            langStopFilters.put(lg, LuceneStopwords.getStopwords(new ClasspathResourceLoader(TagFilter.class), lg));
        }
    }

    public void enableStopwordFilter(boolean b) {
        filter_stopwords = b;
    }

    public void enableCaseSensitive(boolean b) {
        filter_on_case = b;
    }

    /**
     * Default filtering rules: (a) If filter is in case-sensitive mode
     * (DEFAULT), all lower case matches are ignored; only mixed case or upper
     * case passes (b) If match term, t, is in stop word list it is filtered
     * out. Case is ignored.
     * TODO: filter rules -- if text match is all lower case and filter is
     * case-sensitive, then this filters out any lower case matches. Not
     * optimal. This should take into account alpha-case of document.
     * TODO: trivial for the general case, but important: stopTerms is hashed
     * only by lower case value, so native-case lookup is not possible.
     */
    @Override
    public boolean filterOut(String t) {
        if (filter_on_case && StringUtils.isAllLowerCase(t)) {
            return true;
        }

        if (filter_stopwords) {
            return nonPlaceStopTerms.contains(t.toLowerCase().replace('-', ' '));
        }

        return false;
    }

    /**
     * Experimental.
     * Using proper Language ID (ISO 2-char for now), determine if the given
     * term, t, is a stop term in that language.
     *
     * @param t
     * @param tin TextInput object, that has been characterized()
     * @return
     */
    public boolean filterOut(TextMatch t, TextInput tin) {

        /*
         * Consider no given language ID -- only short, non-ASCII terms should be filtered out
         * against all stop filters; Otherwise there is some performance issues.
         */
        if (tin.langid == null) {
            if (t.isASCII()) {
                return false; /* Not filtering out short crap, right now. */
            } else if (t.getLength() < 4) {
                return assessAllFilters(t.getTextnorm());
            }
        }

        /* CASE A -- input has language ID, so we filter MATCH against stop filters for that language.
         * Consider language specific stop filters.
         * NOTE: LangID should not be 'CJK' or group. langStopFilters keys stop terms by LangID
         */
        if (langStopFilters.containsKey(tin.langid)) {
            Set<Object> terms = langStopFilters.get(tin.langid);
            if (terms.contains(t.getTextnorm())) {
                return true;
            }
        }

        if (tin.getCharacterization().hasCJK) {
            // CASE B. - generalization for CJK text -- filter out trivial trigram and bigrams for CJK
            return filterOutCJK(t);
        } else if (tin.isMixedCase()) {
            // CASE C. Allow Proper names
            char c = t.getText().charAt(0);
            if (Character.isUpperCase(c) && !t.isUpper()) {
                // Proper Name, possibly. Not stopping.
                return false;
            }
            // CASE D. Filter out lower case text
            return t.isLower() && t.getLength() < GENERIC_LOWERCASE_MIN;
        }
        return false;
    }

    /**
     * @param langId    lang ID to check.
     * @param termLower lower case term.
     * @return
     */
    public boolean filterOut(String langId, String termLower) {

        String lg = langId != null ? langId : "en"; // default? eek.

        if (langStopFilters.containsKey(lg)) {
            Set<Object> terms = langStopFilters.get(lg);
            return terms.contains(termLower);
        }
        return false;
    }

    /**
     * Experimental. As the name implies use this if know the content has CJK characters
     *
     * Due to bi-gram shingling with CJK languages - Chinese, Japanese, Korean -
     * the matcher really over-matches, e.g. For really short matches, let's
     * rule out obvious bad matches.
     *
     * <pre>
     *  ... に た ... input text matched にた
     * gazetteer place name;  But given the space in the match we think this is invalid.
     * </pre>
     * <p>
     *
     * @param t
     * @return
     */
    private boolean filterOutCJK(TextMatch t) {

        if (t instanceof PlaceCandidate) {
            PlaceCandidate pc = (PlaceCandidate) t;
            if (pc.getNDTextnorm() != null) {
                // This non-diacritic version of text should always exist...
                int normlen = pc.getNDTextnorm().length();
                return normlen < 3 && normlen <= t.getLength();
            }
        } else {
            // Any text from a TextMatch
            String tnorm = TextUtils.phoneticReduction(t.getTextnorm(), false);
            // This non-diacritic version of text should always exist...
            int normlen = tnorm.length();
            return normlen < 3 && normlen <= t.getLength();
        }
        return false;
    }

    /**
     * Run a term (already lowercased) against all stop filters.
     *
     * @param textnorm
     * @return
     */
    public boolean assessAllFilters(String textnorm) {
        for (Set<Object> terms : langStopFilters.values()) {
            if (terms.contains(textnorm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exclusions have two columns in a CSV file. 'exclusion', 'category'
     * "#" in exclusion column implies a comment. Call is responsible for
     * getting I/O stream.
     *
     * @param filestream URL/file with exclusion terms
     * @return set of filter terms
     * @throws ConfigException if filter is not found
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
            HashSet<String> stopTerms = new HashSet<>();
            while ((terms = termreader.read(columns)) != null) {
                String term = terms.get("exclusion");
                if (StringUtils.isBlank(term) || term.startsWith("#")) {
                    continue;
                }
                String trimmed = term.trim();
                /*
                 * Allow for case-sensitive filtration, if stop terms are listed in native case
                 * in resource files
                 */
                stopTerms.add(trimmed);
                stopTerms.add(trimmed.toLowerCase());
            }
            termreader.close();
            return stopTerms;
        } catch (Exception err) {
            throw new ConfigException("Could not load exclusions.", err);
        }
    }
}
