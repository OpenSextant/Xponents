package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

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
             *   
             * 
             */
            if (p.getLength() > MAX_NONSENSE_PHRASE_LEN) {
                continue;
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
                continue;
            }
        }
    }

    //Abbreviated word:  WWW. SSSSS   word, period, single space, text
    static Pattern validAbbrev = Pattern.compile("\\w+[.][ \n][\\w\\d]+");
    // Punctuation abounds:  WWWWPPPP+  SSSS     word, punct, multiple spaces, text 
    static Pattern invalidPunct = Pattern.compile("[\\p{Punct}&&[^'`]]+\\s+");

    /**
     * Find odd patterns of punctuation in names.
     * // Note we have to do this becuase we have over-matched in our tagger or used aggressive tokenizer.
     * // Which lets in all sorts of odd punctuation false-pos.
     * 
     * @param t
     * @return
     */
    public static boolean irregularPunctPatterns(final String t) {
        Matcher abbr = validAbbrev.matcher(t);
        Matcher punct = invalidPunct.matcher(t);
        int a = 0;
        int p = 0;
        while (abbr.find()) {
            ++a;
        }
        while (punct.find()) {
            ++p;
        }
        if (a >= 0 && p == 0 || (a == p)) {
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
            if ((Character.isWhitespace(c) || !Character.isLetterOrDigit(c))
                    && !Character.isLetterOrDigit(prev) && prev != 0) {
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
