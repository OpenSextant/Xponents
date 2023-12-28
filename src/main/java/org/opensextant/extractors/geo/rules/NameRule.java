package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.util.TextUtils;

public class NameRule extends GeocodeRule {

    public static final String CITY = "QualifiedName.City";
    public static final String ADM1 = "QualifiedName.Prov";
    public static final String ADM2 = "QualifiedName.Dist";
    public static final String DIACRITIC = "DiacriticName";
    public static final String UPPERCASE_NOISE = "Noise.Uppercase";

    public static final Set<String> P_prefixes = new HashSet<>();
    public static final Set<String> A1_suffixes = new HashSet<>();
    public static final Set<String> A2_suffixes = new HashSet<>();

    static {
        P_prefixes.addAll(TextUtils.string2list("town,city,village,hamlet,municipality", ","));
        A1_suffixes.addAll(TextUtils.string2list("province,state,prefecture", ","));
        A2_suffixes.addAll(TextUtils.string2list("district,county", ","));
    }

    public NameRule() {
        this.weight = 1;
        this.NAME = "QualifiedName";
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            /*
             * This was filtered out already so ignore.
             */
            if (name.isFilteredOut() || name.getChosen() != null) {
                continue;
            }

            if (significantAdminCodeCount(name)) {
                name.setFilteredOut(true);
                continue;
            }

            // Check short ASCII names vs. non-ASCII names.
            if (name.isASCII()) {
                if (isUppercaseNoise(name)) {
                    continue;
                }
            } else {
                name.addRule(DIACRITIC);
            }

            boolean isPlace = false;
            boolean isAdmin1 = false;
            boolean isAdmin2 = false;

            if (name.getTextnorm().length() > 12 || name.getWordCount() >= 2) {
                String tok1 = name.getTokens()[0].toLowerCase();
                String tok2 = name.getTokens()[name.getWordCount() - 1].toLowerCase();
                isPlace = P_prefixes.contains(tok1);
                isAdmin1 = A1_suffixes.contains(tok2);
                isAdmin2 = A2_suffixes.contains(tok2);
            }

            /*
             * Increment the score for locations whose feature lines up with the nomenclature in the text match.
             */
            for (ScoredPlace geoScore : name.getPlaces()) {
                Place geo = geoScore.getPlace();
                if (filterOutByFrequency(name, geo)) {
                    continue;
                }
                sameLexicalName(name, geo);

                if (isPlace && geo.isPopulated()) {
                    name.incrementPlaceScore(geo, 1.0, CITY);
                } else if (isAdmin1 && geo.isAdmin1()) {
                    name.incrementPlaceScore(geo, 1.0, ADM1);
                } else if (isAdmin2 && geo.isAdministrative()) {
                    name.incrementPlaceScore(geo, 1.0, ADM2);
                }
            }
        }
    }

    private boolean isUppercaseNoise(PlaceCandidate name) {
        // UPPPER CASE name surrounded by mixed case,... and the mention is not linked
        // to any other geography.  That means it is a random acronym of no import...
        if (!name.isValid() || name.getRelated()==null) {
            if (isShort(name.getLength()) && name.isUpper() && !TextUtils.isUpper(name.getSurroundingText())) {
                name.addRule(UPPERCASE_NOISE);
                name.setFilteredOut(true);
                return true;
            }
        }
        return false;
    }

    /**
     * This filter counts "admin code" Places for a given match.  If the match is indeed
     * a short code-like name , e.g., "BS", "MA"... and it is unassociated with a place name, then it is
     * likely noise. Mark it filtered out.   The threshold is 20% ~ i.e., if 1 of 5 gazetteer instances of this
     * name is a code, then we'll treat it as such.
     *
     * @param name
     * @return true if a good number of the gazetteer places are Admin codes.
     */
    private boolean significantAdminCodeCount(PlaceCandidate name) {
        if (name.isValid() || !(name.isShortName() && name.isUpper())) {
            return false;
        }

        int adminCount = 0;
        for (ScoredPlace geo : name.getPlaces()) {
            if (geo.getPlace().isAdministrative() && geo.getPlace().isCode()) {
                ++adminCount;
            }
        }
        // Instead of dividing, use multiplication ot check.
        return (5 * adminCount) >= name.getPlaces().size();
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /*
         * no-op
         */
    }

}
