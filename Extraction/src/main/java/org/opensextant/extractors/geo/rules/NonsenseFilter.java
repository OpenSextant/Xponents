package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.TextUtils;

/**
 * Filter out nonsense tokens that match some city or state name.
 * Indicators are: irregular whitespace, mixed punctuation
 * This does not apply to longer matches. Default nonsense length is 10 chars or shorter.
 * 
 * <pre>
 * // Do. do do
 * // ta-da
 * // doo doo
 * </pre>
 * 
 * @author ubaldino
 *
 */
public class NonsenseFilter extends GeocodeRule {

    public static Pattern tokenizer = Pattern.compile("[\\s+\\p{Punct}]+");

    private static int MAX_NONSENSE_PHRASE_LEN = 15;
    public static final int GENERIC_ONE_WORD = 10; // Chars in average word.

    private static Pattern wsRedux = Pattern.compile("[-\\s+`]");

    protected static final String phoneticRedux(final String n) {
        return wsRedux.matcher(n).replaceAll("");
    }

    /** 
     * See if name n2 is a phonetic match for a relative constant ph1.
     * phonetic(n2) = ph1 ?
     * 
     * @param ph1 - phonetic redux of a name.
     * @param n2 - a test name.
     * @return
     */
    protected static final boolean isPhoneticMatch(final String ph1, final String n2) {
        final String ph2 = phoneticRedux(n2);
        return ph2.equalsIgnoreCase(ph1);
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

            /*
             * is Nonsense?
             * For phrases upto MAX chars long:
             * + does it contain irregular punctuation?
             *   //  "...in the south. Bend it backwards...";  
             *   // South Bend is not intended there.
             *  
             * + does it contain a repeated syllable or word?:
             *   // "doo doo", "bah bah" "to to"
             */
            if (p.getLength() > MAX_NONSENSE_PHRASE_LEN) {
                continue;
            }

            /*
             * Short words, with numerics. Approximately one word.
             */
            if (p.getLength() < GENERIC_ONE_WORD) {
                if (trivialNumerics.matcher(p.getText()).matches()) {
                    p.setFilteredOut(true);
                    p.addRule("Nonsense,Numbers");
                    continue;
                }
            }
            if (irregularPunctPatterns(p.getText())) {
                p.setFilteredOut(true);
                p.addRule("Nonsense,Punct");
                continue;
            }
            if (p.isLower()) {
                String[] wds = tokenizer.split(p.getTextnorm());
                HashSet<String> set = new HashSet<>();
                for (String w : wds) {
                    if (set.contains(w)) {
                        p.setFilteredOut(true);
                        p.addRule("Nonsense,Repeated,Lower");
                        break;
                    }
                    set.add(w);
                }
                //continue;
            }

            /*
             * Still here? Check for short obscure matches where diacritics mismatch.
             * Cannot eliminate a candidate based on a single location. But reduce score for those that 
             * mismatch severely.  
             * NOTE: Score on each geo location is accounted for in default score. I.E., edit distance between text match and geo name. 
             */
            if (p.getLength() <= GENERIC_ONE_WORD) {
                boolean hasValidGeo = false;
                String ph1 = phoneticRedux(p.getTextnorm());
                String diacriticRule = null;
                log.debug("Testing phrase {} phonetic:{}", p.getTextnorm(), ph1);
                for (Place geo : p.getPlaces()) {
                    log.debug("\tPLACE={}, {}", geo, geo.getNamenorm());
                    boolean geoDiacritics = TextUtils.hasDiacritics(geo.getPlaceName());
                    if (geoDiacritics && p.hasDiacritics) {
                        hasValidGeo = true;
                        diacriticRule = "Matched-Diacritics";
                        break;
                    }
                    if (!geoDiacritics && !p.hasDiacritics) {
                        hasValidGeo = true;
                        // both ASCII? not worth tracking.
                        break;
                    }

                    /* Pattern: Official name has accented/emphasis markings on the name, such as:
                     *     `NAME   or NAME`
                     * Where NAME is some Latin transliteration of non-Latin script    
                     */
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
                    log.debug("\t{} != {}", p.getTextnorm(), geo.getNamenorm());
                }
                if (!hasValidGeo) {
                    p.setFilteredOut(true);
                    p.addRule("Nonsense,Mismatched,Diacritic");
                } else if (diacriticRule != null) {
                    p.addRule(diacriticRule);
                }
            }
        }
    }

    //Abbreviated word:  WWW. SSSSS   word, period, single space, text
    static Pattern validAbbrev = Pattern.compile("\\w+[.] \\S+");
    // Punctuation abounds:  WWWWPPPP+  SSSS     word, punct, multiple spaces, text 
    //                       Any phrase containing double quotes or long dashes.
    static Pattern invalidPunct = Pattern.compile("[\\p{Punct}&&[^'`]]+\\s+|[\"\u2014\u2015\u201C\u201D\u2033]");
    static Pattern trivialNumerics = Pattern.compile("\\w+[\\p{Punct}\\s]+\\d+");

    /**
     * Find odd patterns of punctuation in names.
     * We have to do this because we have over-matched in our tagger or 
     * used aggressive tokenizer, which lets in all sorts of odd punctuation false-pos.
     * 
     * @param t
     * @return
     */
    public static boolean irregularPunctPatterns(final String t) {
        // HTML, Internet trash: <,>
        if (t.indexOf('<') >= 0 || t.indexOf('>') >= 0) {
            return true;
        }
        // Edge case "A. B. C." is a valid match, but the last "." is not followed but space. So 
        // Add a single trailing space.
        Matcher abbr = validAbbrev.matcher(t);
        Matcher punct = invalidPunct.matcher(t);
        int a = 0;
        int p = 0;
        while (abbr.find()) {
            ++a;
        }
        if (t.endsWith(".")) {
            ++a;
        }

        while (punct.find()) {
            ++p;
        }
        if (a >= 0 && p == 0 || (a >= p)) {
            return false;
        }
        return (p > 0);
    }

    /**
     * for each letter that occurs, look at the one before it.
     * Track how many times multiple non-text chars appear in a row
     * after a alphanum char.
     * 
     * 'abc- xx123' FAIL: odd hyphenation
     * 'St. Paul' PASS: valid use of abbrev.
     * 
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
