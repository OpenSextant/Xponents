package org.opensextant.extractors.geo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.opensextant.ConfigException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.util.LuceneStopwords;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * you believe certain items will always be filtered then set name_bias >
 * 0.0
 */

public class TagFilter extends MatchFilter {
    /**
     * This may need to be turned off for processing lower-case or dirty
     * data.
     */
    boolean filter_stopwords = true;
    boolean filter_on_case = true;
    Set<String> stopTerms = null;
    Logger log = LoggerFactory.getLogger(TagFilter.class);

    /*
     * Select languages for experimentation.
     */
    private Map<String, Set<Object>> langStopFilters = new HashMap<>();

    private Set<String> generalLangId = new HashSet<>();

    /**
     * NOTE:  This expects the files are all available. This fails if resource files are missing.
     * 
     * @throws ConfigException if any file has a problem. 
     */
    public TagFilter() throws IOException, ConfigException {
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
        generalLangId.add(TextUtils.englishLang);
        generalLangId.add(TextUtils.spanishLang);

        /* NOTE: these stop word sets are of format='wordset'
         * Whereas other languages (es, it, etc.) are provided in format='snowball'
         * StopFilterFactory is needed to load snowball filters.
         */
        String[] langSet = { "ja", "th", "tr", "id", "ar", 
                "ru", "it", "pt", "de", "nl"
                /*, "es"*/};  // Español (es) is handled by adhoc list of spanish terms above.
        loadLanguageStopwords(langSet);
    }

    /**
     * Load default Lucene stop words to aid in language specific filtration.
     * @param langids
     * @throws IOException
     * @throws ConfigException
     */
    private void loadLanguageStopwords(String[] langids) throws IOException, ConfigException {

        for (String lg : langids) {
            /*String url = String.format("/lang/stopwords_%s.txt", lg);
            URL obj = TagFilter.class.getResource(url);
            if (obj == null) {
                throw new IOException("No such stop filter file " + url);
            }
            loadStopSet(obj, lg);
            */
            langStopFilters.put(lg, LuceneStopwords.getStopwords(new ClasspathResourceLoader(TagFilter.class), lg));
        }

        /*
         * More optional lists.
         */
        // KOREAN
        String url = "/lang/carrot2-stopwords.ko";
        String lg = "ko";
        URL obj = URL.class.getResource(url);
        if (obj != null) {
            loadStopSet(obj, lg);
        }
        // CHINESE
        url = "/lang/carrot2-stopwords.zh";
        lg = "zh";
        obj = URL.class.getResource(url);
        if (obj != null) {
            loadStopSet(obj, lg);
        }

        // VIETNAMESE
        url = "/lang/vietnamese-stopwords.txt";
        lg = "vi";
        obj = URL.class.getResource(url);
        if (obj != null) {
            loadStopSet(obj, lg);
        }

    }

    private void loadStopSet(URL url, String langid) throws IOException, ConfigException {
        try (InputStream strm = url.openStream()) {
            HashSet<Object> stopTerms = new HashSet<>();
            for (String line : IOUtils.readLines(strm, Charset.forName("UTF-8"))) {
                if (line.trim().startsWith("#")) {
                    continue;
                }
                stopTerms.add(line.trim().toLowerCase());
            }
            if (stopTerms.isEmpty()) {
                throw new ConfigException("No terms found in stop filter file " + url.toString());
            }
            langStopFilters.put(langid, stopTerms);
        }
    }

    public void enableStopwordFilter(boolean b) {
        filter_stopwords = b;
    }

    public void enableCaseSensitive(boolean b) {
        filter_on_case = b;
    }

    /**
     * Default filtering rules:
     * (a) If filter is in case-sensitive mode (DEFAULT), all lower case matches are ignored; only mixed case or upper case passes
     * (b) If match term, t, is in stop word list it is filtered out. Case is ignored.
     * 
     * TODO: filter rules -- if text match is all lower case and filter is case-sensitive, then this 
     * filters out any lower case matches. Not optimal.  This should take into account alpha-case of document.
     * 
     * TODO: trivial for the general case, but important: stopTerms is hashed only by lower case value, so native-case 
     * lookup is not possible.
     */
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
     * Experimental.
     * 
     * Using proper Language ID (ISO 2-char for now), determine if the 
     * given term, t, is a stop term in that language.
     * 
     * @param t
     * @param langId
     * @param docIsUpper true if input doc is mostly upper
     * @param docIsLower true if input doc is mostly lower
     * @return
     */
    public boolean filterOut(PlaceCandidate t, String langId, boolean docIsUpper, boolean docIsLower) {
        /*
         * Consider no given language ID -- only short, non-ASCII terms should be filtered out 
         * against all stop filters; Otherwise there is some performance issues.
         */
        if (langId == null) {
            if (t.isASCII()) {
                return false; /* Not filtering out short crap, right now. */
            } else if (t.getLength() < 4) {
                return assessAllFilters(t.getText().toLowerCase());
            }
        }
        /*
         * IGNORE languages already filtered out by the general filter above.
         */
        if (generalLangId.contains(langId)) {
            return false;
        }
        
        /* EXPERIMENTAL.
         * 
         * But if langID is given, we first consider if text in document
         * is possibly a Proper name of sort...
         * UPPERCASENAME -- possibly stop?
         * Upper Case Name -- pass; not stop
         * not upper case name -- possibly stop.
         */
        if (!docIsUpper) {
            char c = t.getText().charAt(0);
            if (Character.isUpperCase(c) && !t.isUpper()) {
                // Proper Name, possibly. Not stopping.
                return false;
            }
        }

        boolean cjk = TextUtils.isCJK(langId);

        /*
         * Bi-gram + whitespace filter for CJK:
         */
        if (cjk && filterOutCJK(t)) {
            return true;
        }
        
        /*
         * FILTER out lower case matches for non-English, non-CJK texts.
         * If document is mixed case.  That is we still expect/assume interesting
         * place names to be proper names.  However, if you find longer name matches ~10 chars or longer
         * as lower case names, then let them pass. 10 chars is arbitrary, but approx. 1 word threshold. 
         */
        if (!cjk){
            if (!docIsLower && !docIsUpper){
                if (t.isLower() && t.getLength()< 10){
                    return true;
                }
            }
        }

        /* 
         * Consider language specific stop filters.
         * NOTE: LangID should not be 'CJK' or group.  langStopFilters keys stop terms by LangID
         */
        if (langStopFilters.containsKey(langId)) {
            Set<Object> terms = langStopFilters.get(langId);
            return terms.contains(t.getText().toLowerCase());
        }
        return false;
    }

    /**
     * Experimental. Hack.
     * 
     * Due to bi-gram shingling with CJK languages - Chinese, Japanese, Korean - 
     * the matcher really over-matches, e.g.  For really short matches, let's rule out obvious bad matches.
     * <pre>
     * ... に た ...  input text matched
     *  にた          gazetteer place name. 
     * </pre>
     * TOOD: make use of better tokenizer/matcher in SolrTextTagger configuration for CJK 
     * @param t
     * @return
     */
    private boolean filterOutCJK(PlaceCandidate t) {
        if (t.getLength() < 5 && TextUtils.count_ws(t.getText()) > 0) {
            return true;
        }
        return false;
    }

    /**
     * Run a term (already lowercased) against all stop filters.
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
                String trimmed = term.trim();
                /* Allow for case-sensitive filtration, if stop terms are listed in native case in resource files */
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
