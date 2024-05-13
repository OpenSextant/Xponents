package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.Place;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
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

    private static final int MAX_NONSENSE_PHRASE_LEN = 20;
    private static final int MIN_PHONETIC_MATCH_LEN = 4;
    public static final int AV = 10; // Chars in average word.

    private static boolean lowerInitial(String s) {
        return Character.isLowerCase(s.charAt(0));
    }

    /**
     * Test for simple abbreviations.
     *
     * @param s
     * @return
     */
    public static boolean isValidAbbreviation(String s) {
        return !nonAbbrevPunct.matcher(s).find();
    }

    /**
     * Trivial Articles in lowercase -- when these appear prefixed in place matches
     * they are usually false positives. e.g., "the hotel"
     */
    private static final Set<String> TRIVIAL_ARTICLES = new HashSet<>();

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
            if (p.isValid() || p.getTokens() == null || p.isFilteredOut()) {
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

            if (p.hasMiddleEasternText() || p.hasCJKText()) {
                continue;
            }

            /* Look at valid and invalid punctuation patterns */
            if (assessPunctuation(p)) {
                continue;
            }
            if (!assessPhraseDensity(p)) {
                p.addRule("Noise.LowDensityText");
                p.setFilteredOut(true);
                continue;
            }

            if (irregularCase(p.getText())) {
                p.setFilteredOut(true);
                continue;
            }

            /*
             * Ignore phrases starting with trivial articles or mismatching initial case.
             */
            if (p.getWordCount() == 2 && !p.isCountry) {
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
            if (isShort(p.getLength())) {
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
            if (!p.isFilteredOut() && isShort(p.getLength())) {
                assessPhoneticMatch(p);
            }
        }
    }


    /** Names of places should have about N=5 chars to non-chars.
     *   "A BC"  3:1      filtered out.
     *   "AB CD"  4:1     filterd out.
     *   "AB BCD"  5:1    possibly acceptable.
     */
    public static final int PHRASE_DENSITY_CHAR_RATIO = 5;

    /**
     *
     * @param p
     * @return True if alphanum to non-alphanum content is at or above default threshold
     */
    public static boolean assessPhraseDensity(TextMatch p) {
        return assessPhraseDensity(p.getText(), PHRASE_DENSITY_CHAR_RATIO);
    }

    /**
     *
     * @param name
     * @param charRatio
     * @return True if alphanum to non-alphanum content is at or above charRatio threshold
     */
    public static boolean assessPhraseDensity(String name, int charRatio) {
        int nonAlpha = TextUtils.countNonText(name);
        if (nonAlpha == 0) {
            return true;
        }
        return ((name.length() - nonAlpha) / nonAlpha) >= charRatio;
    }

    /**
     * optimize punctuation detection and filtration.
     * This routine marks the candidate as filtered or not, as well as returning a status indicating
     * something was done.
     * <p>
     * Results:
     * - no punctuation found - continue
     * - valid punctuation found - exit nonsense filter
     * - invalid punctuation found - mark filtered out, exit nonsense filter
     * - inconclusive - continue
     *
     * @param p
     * @return
     */
    public static boolean assessPunctuation(PlaceCandidate p) {
        Matcher m = anyPunct.matcher(p.getText());
        int punct = 0;
        while (m.find()) {
            ++punct;
        }
        if (punct == 0) {
            return false;
        }

        /*
         * Place name has punctuation from here on...
         */
        if (isValidAbbreviation(p.getText())) {
            p.addRule("Valid Punct");
            return true;
        }

        // Ignore short names that start with numbers ... actual gazetteer data, but usually not right.
        if (shortNumericText(p.getText())) {
            p.setFilteredOut(true);
            return true;
        }
        // Ignore names of any length that have odd, common punctuation
        if (irregularCommonPunct(p.getText())) {
            p.setFilteredOut(true);
            return true;
        }

        // Don't allow things like X. G"willaker
        boolean abnormal = isIrregularPunct(punct, p.getLength());
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
        } else if (normal) {
            p.addRule("Normal Abbrev");
            return true;
        }
        return false;
    }

    /**
     * Assess the validity of a match candidate with the geographic names associated
     * with it.
     * For example if you have ÄEÃ how well does it match Aeå, Aea or aeA?
     * this is intended for ruling out short crap phonetically, but NOT for ranking location names for a given candidate
     *
     * @param p
     */
    public void assessPhoneticMatch(PlaceCandidate p) {
        boolean hasValidGeo = false;
        String ph1 = p.getNDTextnorm();
        String diacriticRule = null;
        log.debug("Testing phrase {} phonetic:{}", p.getTextnorm(), ph1);
        for (ScoredPlace geoScore : p.getPlaces()) {
            Place geo = geoScore.getPlace();
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
                if (ph1.equals(geo.getNDNamenorm())) {
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

    /**
     * Filter out cases of acronmyms of the form AAa.... which match codes and abbreviations.
     *
     * @param txt
     * @return
     */
    public boolean irregularCase(String txt) {
        if (txt.length() < 3) {
            return false;
        }
        char last = txt.charAt(txt.length() - 1);
        return Character.isUpperCase(txt.charAt(0)) && Character.isUpperCase(txt.charAt(1)) && Character.isLowerCase(last);
    }

    // Pattern to identify cues like "N. " or "Ft. " in lieu of "North " or "Fort "
    static final Pattern anyValidAbbrev = Pattern.compile("[EFMNSW][A-Z]{0,2}\\.\\s+", Pattern.CASE_INSENSITIVE);
    static final Pattern nonAbbrevPunct = Pattern.compile("[^\\w\\s.-]+");

    static final Pattern trivialNumerics = Pattern.compile("\\w+[\\p{Punct}\\s]+\\d+");
    static final Pattern anyPunct = Pattern.compile("[\\p{Punct}\u2014\u2015\u201C\u201D\u2033]"); /* find onw char */
    static final Pattern commonPunct = Pattern.compile("[()\\[\\]!&$]");

    /**
     * 5th Street  -- fine.
     * 5th A       -- ambiguous
     * 5) Bullet   -- no good.
     *
     * @param t
     * @return
     */
    public static boolean shortNumericText(String t) {
        return (isShort(t.length()) && Character.isDigit(t.charAt(0)));
    }

    /**
     * If common punctuation (), [], !, &amp;, $ are found within the match, then the name is not likely the right thing.
     *
     * @param t
     * @return
     */
    public static boolean irregularCommonPunct(String t) {
        return commonPunct.matcher(t).find();
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

    public static boolean regularAbbreviationPatterns(final String t) {
        return anyValidAbbrev.matcher(t).find();
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        // no op
    }
}
