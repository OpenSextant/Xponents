package org.opensextant.extractors.geo;

import java.util.HashMap;
import java.util.HashSet;

import org.opensextant.extraction.MatchFilter;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MatchFilter that provides language-specific heuristics on filters.
 *  - filterOut( string ) filters just based on length and the global language set in filter
 *  - filterOut( Place/PlaceCandidate ) filters out based on geographic features as well: Major places are allowed to pass
 *    even if they are short phrases.
 */
public class LanguageFilter extends MatchFilter {

    private String lang = null;
    private boolean eval = false;
    private boolean eval_arabic = false;
    private boolean eval_cjk = false;
    final static HashMap<String, Integer> TOKEN_LEN = new HashMap<>();
    final static HashMap<String, Integer> NAME_LEN = new HashMap<>();
    final static HashSet<String> MAJOR_FEATURES = new HashSet<>();
    final static Logger log = LoggerFactory.getLogger(LanguageFilter.class);

    static {
        // "token length" is to filter out any trivial noise
        TOKEN_LEN.put("cjk", 2);
        TOKEN_LEN.put("zh", 2);
        TOKEN_LEN.put("zt", 2);
        TOKEN_LEN.put("ja", 2);
        TOKEN_LEN.put("ko", 2);

        TOKEN_LEN.put("ar", 5);
        TOKEN_LEN.put("fa", 5);
        TOKEN_LEN.put("ur", 5);
        TOKEN_LEN.put("dr", 5);

        // "name length" is to filter out place names that are more likely places
        //
        NAME_LEN.put("cjk", 4);
        NAME_LEN.put("zh", 4);
        NAME_LEN.put("zt", 4);
        NAME_LEN.put("ja", 5);  // Performance on japanese is not great. Higher stop filter len.
        NAME_LEN.put("ko", 4);

        NAME_LEN.put("ar", 13);
        NAME_LEN.put("fa", 13);
        NAME_LEN.put("ur", 13);
        NAME_LEN.put("dr", 13);

        MAJOR_FEATURES.add("PCL"); // dependent territory
        MAJOR_FEATURES.add("PCLI"); // Country
        MAJOR_FEATURES.add("PCLD"); // territory/division
        MAJOR_FEATURES.add("ADM1"); // province
        //MAJOR_FEATURES.add("ADM2"); // county or district
        MAJOR_FEATURES.add("PPLC"); // capitol
        // MAJOR_FEATURES.add("PPLA"); // locality administrative
    }

    static int TOKEN_LEN_DEFAULT = 6; // For undetermined language.... that is non-Latin

    public LanguageFilter(String lang_id) {
        this.lang = lang_id;
        this.eval_cjk = TextUtils.isCJK(lang);
        this.eval_arabic = TextUtils.isMiddleEastern(lang);
        this.eval = this.eval_cjk || this.eval_arabic;
    }

    /** Rule out text based on the language and length alone.
     */
    @Override
    public boolean filterOut(String value) {
        if (!eval) {
            return false;
        }
        int eval_len = TOKEN_LEN.getOrDefault(this.lang, TOKEN_LEN_DEFAULT);
        int text_len = value.length();
        return text_len < eval_len;
    }

    /** Name filter for language-based geography.
     * General idea:
     *   - PPLC, PLCI, ADM1, and other significant place names can pass on through.
     *   - PPL (and maybe all other features) -- pass on through ONLY if name length is above a high threshold
     */
    public boolean filterOut(PlaceCandidate pc) {
        for (ScoredPlace geo : pc.getPlaces()) {
            if (MAJOR_FEATURES.contains(geo.getPlace().getFeatureCode())) {
                log.info("Allow place {}, due to location/feat {}", pc.getText(), geo.getPlace());
                return false;
            }
        }
        int eval_len = NAME_LEN.getOrDefault(this.lang, 2 * TOKEN_LEN_DEFAULT);
        int text_len = pc.getLength();
        // TRUE === filter out noise.  if name len is less than threshold, filter it out.
        return text_len < eval_len;
    }
}
