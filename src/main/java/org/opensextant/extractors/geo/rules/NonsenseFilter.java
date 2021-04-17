package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.TextUtils;

/**
 * Filter out nonsense tokens that match some city or state name.
 * Indicators are: irregular whitespace, mixed punctuation
 * This does not apply to longer matches. Default nonsense length is 10 chars or
 * shorter.
 *
 * <pre>
 * // Do. do do
 * // ta-da
 * // doo doo
 * </pre>
 *
 * @author ubaldino
 */
public class NonsenseFilter extends GeocodeRule {

    private static int MAX_NONSENSE_PHRASE_LEN = 20;
    private static int MIN_PHONETIC_MATCH_LEN = 4;
    public static final int GENERIC_ONE_WORD = 10; // Chars in average word.

    private static Pattern wsRedux = Pattern.compile("[-\\s+`]");

    protected static final String phoneticRedux(final String n) {
        return wsRedux.matcher(n).replaceAll("");
    }

    private static boolean lowerInitial(String s) {
        return Character.isLowerCase(s.charAt(0));
    }

    /**
     * Test for simple abbreviations.
     * 
     * @param s
     * @return
     */
    private static boolean isValidAbbreviation(String s) {
        String test = dotAbbrev.matcher(s).replaceAll("");
        return TextUtils.isASCII(test);
    }

    /**
     * See if name n2 is a phonetic match for a relative constant ph1.
     * phonetic(n2) = ph1 ?
     *
     * @param ph1 - phonetic redux of a name.
     * @param n2  - a test name.
     * @return
     */
    protected static final boolean isPhoneticMatch(final String ph1, final String n2) {
        final String ph2 = phoneticRedux(n2);
        return ph2.equalsIgnoreCase(ph1);
    }

    /**
     * Trivial Articles in lowercase -- when these appear prefixed in place matches
     * they are usually false positives. e.g., "the hotel"
     */
    private static Set<String> TRIVIAL_ARTICLES = new HashSet<>();
    static {
        TRIVIAL_ARTICLES.add("the");
        TRIVIAL_ARTICLES.add("a");
        TRIVIAL_ARTICLES.add("an");
        TRIVIAL_ARTICLES.add("le");
    }

    /**
     * Evaluate the name in each list of names.
     *
     * <pre>
     * doo doo      - FAIL
     * St. Paul     - PASS
     * south"  bend - FAIL
     * </pre>
     */
    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate p : names) {
            if (p.isValid() || p.getTokens() == null) {
                // isValid: this place was marked by other rules as valid
                // tokens: in general trivial geo name references (continents) are not analyzed
                // and tokens may be null.
                continue;
            }

            /*
             * For performance reasons longer phrases are not assessed for non-sense.
             */
            if (p.getLength() > MAX_NONSENSE_PHRASE_LEN) {
                continue;
            }
            
            /* Look at valid and invalid punctuation patterns */
            if (assessPunctuation(p)) {
                continue;
            }

            /*
             * Ignore phrases starting with trivial articles or mismatching initial case.
             */
            if (p.getWordCount() == 2) {
                String tok1 = p.getTokens()[0];
                String tok2 = p.getTokens()[1];
                if (p.isLower() || (lowerInitial(tok1) && !lowerInitial(tok2))) {
                    if (TRIVIAL_ARTICLES.contains(tok1)) {
                        p.setFilteredOut(true);
                        continue;
                    }
                }
            }

            /*
             * Short words, with numerics. Approximately one word.
             */
            if (p.getLength() < GENERIC_ONE_WORD) {
                if (trivialNumerics.matcher(p.getText()).matches()) {
                    p.setFilteredOut(true);
                    p.addRule("Nonsense Numbers");
                    continue;
                }              
            }

            /*
             * Ignore repeated lowercase terms. "ha ha ha"
             */
            if (p.isLower() || lowerInitial(p.getText())) {
                HashSet<String> set = new HashSet<>();
                for (String w : p.getTokens()) {
                    String wl = w.toLowerCase();
                    if (set.contains(wl)) {
                        p.setFilteredOut(true);
                        p.addRule("Nonsense Repeated-Lower");
                        break;
                    } else {
                        set.add(wl);
                    }
                }
            }

            /*
             * Still here? Check for short obscure matches where diacritics mismatch.
             * Cannot eliminate a candidate based on a single location. But reduce score for
             * those that
             * mismatch severely.
             * NOTE: Score on each geo location is accounted for in default score. I.E.,
             * edit distance between text match and geo name.
             */
            if (!p.isFilteredOut() && p.getLength() <= GENERIC_ONE_WORD) {
                assessPhoneticMatch(p);
            }
        }
    }

    
    /**
     * optimize punctuation detection and filtration.
     * 
     * @param p
     * @return
     */
    public static boolean assessPunctuation(PlaceCandidate p) {
        Matcher m = anyPunctuation.matcher(p.getText());
        int punct = 0;
        while (m.find()) {
            ++punct;
        }
        if (punct == 0) {
            return false;
        }

        // Don't allow things like X. G"willaker
        boolean abnormal = isIrregularPunct(punct, p.getLength());

        /*
         * Place name has punctuation from here on...
         */
        if (isValidAbbreviation(p.getText()) && ! abnormal) {
            p.addRule("Valid Punct");
            return true;
        }

        // Allow things like S. Ana
        boolean normal = regularAbbreviationPatterns(p.getText());

        if (!normal && abnormal) {
            p.setFilteredOut(true);
            p.addRule("Nonsense Punct");
            return true;
        } else if (abnormal) {

            // Does phrase carry low-signal, high punctuation count?
            // Ho-ho-ho-ho!!! => matches village "Ho-ho"
            //
            p.setFilteredOut(true);
            p.addRule("Nonsense Punct");
            return true;
        }
        return false;
    }

    /**
     * Assess the validity of a match candidate with the geographic names associated
     * with it.
     * For example if you have ÄEÃ how well does it match Aeå, Aea or aeA?
     * this is intended for ruling out short crap phonetically.
     *
     * @param p
     */
    public void assessPhoneticMatch(PlaceCandidate p) {
        boolean hasValidGeo = false;
        String ph1 = phoneticRedux(p.getTextnorm());
        String diacriticRule = null;
        log.debug("Testing phrase {} phonetic:{}", p.getTextnorm(), ph1);
        for (Place geo : p.getPlaces()) {
            log.debug("\tPLACE={}, {}", geo, geo.getNamenorm());
            boolean geoDiacritics = TextUtils.hasDiacritics(geo.getPlaceName());
            if (geoDiacritics && p.hasDiacritics) {
                if (geo.getName().equalsIgnoreCase(p.getText())) {
                    hasValidGeo = true;
                    diacriticRule = "Matched-Diacritics";
                    break;
                }
            }
            if (!geoDiacritics && !p.hasDiacritics) {
                hasValidGeo = true;
                // both ASCII? not worth tracking.
                break;
            }

            /*
             * Pattern: Official name has accented/emphasis markings on the name, such as:
             * `NAME or NAME`
             * Where NAME is some Latin transliteration of non-Latin script
             * RULE applies to names 5 characters or longer; Shorter than that
             * we find too much noise.
             */
            if (p.getLength() > MIN_PHONETIC_MATCH_LEN) {
                if (geo.getNamenorm().contains(p.getTextnorm())) {
                    hasValidGeo = true;
                    diacriticRule = "Location-Contains-Name";
                    break;
                }
                if (isPhoneticMatch(ph1, geo.getNamenorm())) {
                    hasValidGeo = true;
                    diacriticRule = "Matched-Phonetic";
                    break;
                }
            }
            log.debug("\t{} !~ {}", p.getText(), geo.getNamenorm());
        }
        if (!hasValidGeo) {
            p.setFilteredOut(true);
            p.addRule("Nonsense,Mismatched,Diacritic");
        } else if (diacriticRule != null) {
            p.addRule(diacriticRule);
        }
    }

    // Pattern to identify cues like "N. " or "Ft. " in lieu of "North " or "Fort "
    private static final Pattern anyValidAbbrev = Pattern.compile("[EFMNSW][A-Z]{0,2}\\.\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern dotAbbrev = Pattern.compile("\\.\\s*");

    // Abbreviated word: WWW. SSSSS word, period, single space, text
    static Pattern validAbbrev = Pattern.compile("\\w+[.] \\S+");
    // Punctuation abounds: WWWWPPPP+ SSSS word, punct, multiple spaces, text
    // Any phrase containing double quotes or long dashes.
    static Pattern trivialNumerics = Pattern.compile("\\w+[\\p{Punct}\\s]+\\d+");
    static Pattern anyInvalidPunct = Pattern.compile("[[\\p{Punct}\u2014\u2015\u201C\u201D\u2033]&&[^-_.'`]]+");
    static Pattern anyPunctuation = Pattern.compile("([\\p{Punct}\u2014\u2015\u201C\u201D\u2033]{1})");

    /**
     * Approximation of Per-character punctuation noise.
     * 1 punctuation diacritic per character is a maximum.
     * 
     * @param t text
     * @return
     */
    public static boolean irregularPunctCount(String t) {
        return irregularPunctCount(t, 5);
    }

    public static boolean isIrregularPunct(int punct, int strLength) {
        return isIrregularPunct(punct, strLength, 5);
    }

    public static boolean isIrregularPunct(int punct, int strLength, int validCharRate) {
        if (punct == 0) {
            return false;
        }
        return (strLength / punct) < validCharRate;
    }

    public static boolean irregularPunctCount(String t, int validCharRate) {
        Matcher m = anyPunctuation.matcher(t);
        int punct = 0;
        while (m.find()) {
            ++punct;
        }
        if (punct == 0) {
            return false;
        }

        String content = TextUtils.delete_whitespace(t);
        // Approximately 1 punctuation char for every 5 words is a sign it is noise.
        // 10 chars with 3 punctuation chars is 3.33 chars per punct char.
        // 10 chars with 2 punctuation chars is 5.00 -- this seems like a reasonable
        // limit.  Compare Non-whitespace characters to punctuation count.
        //
        return (content.length() / punct) < validCharRate;
    }

    public static boolean irregularPunctPatterns(final String t) {
        return anyInvalidPunct.matcher(t).find();
    }

    public static boolean regularAbbreviationPatterns(final String t) {
        return anyValidAbbrev.matcher(t).find();
    }

    /**
     * for each letter that occurs, look at the one before it.
     * Track how many times multiple non-text chars appear in a row
     * after a alphanum char.
     * 'abc- xx123' FAIL: odd hyphenation
     * 'St. Paul' PASS: valid use of abbrev.
     *
     * @param t
     * @return
     */
    public static int[] irregularPunct(final String t) {

        int irregular = 0;
        int ws = 0;
        char prev = 0;
        for (char c : t.toCharArray()) {
            // A %
            // A %^
            if (Character.isWhitespace(c)) {
                ++ws;
            }
            if ((Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) && !Character.isLetterOrDigit(prev)
                    && prev != 0) {
                ++irregular;
            }
            prev = c;
        }
        return new int[] { ws, irregular };
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        // no op
    }
}
