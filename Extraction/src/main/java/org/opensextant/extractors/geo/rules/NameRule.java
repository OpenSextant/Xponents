package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.TextUtils;

public class NameRule extends GeocodeRule {

    public static String CITY = "QualifiedName.City";
    public static String ADM1 = "QualifiedName.Prov";
    public static String ADM2 = "QualifiedName.Dist";
    
    public static Set<String> P_prefixes = new HashSet<>();
    public static Set<String> A1_suffixes = new HashSet<>();
    public static Set<String> A2_suffixes = new HashSet<>();

    static {
        P_prefixes.addAll(TextUtils.string2list(
                "town,city,village,hamlet,municipality", ","));
        A1_suffixes.addAll(TextUtils.string2list(
                "province,state,prefecture", ","));
        A2_suffixes.addAll(TextUtils.string2list(
                "district,county", ","));

    }

    public NameRule() {
        this.weight = 1;
        this.NAME = "QualifiedName";
    }

    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            /*
             * This was filtered out already so ignore.
             */
            if (name.isFilteredOut() || name.getChosen() != null) {
                continue;
            }
            if (name.getTextnorm().length() < 10) {
                continue;
            }

            String[] words = name.getTextnorm().split(" ");
            boolean isPlace = P_prefixes.contains(words[0]);
            boolean isAdmin1 = A1_suffixes.contains(words[words.length - 1]);
            boolean isAdmin2 = A2_suffixes.contains(words[words.length - 1]);
            if (!isPlace && !isAdmin1 && !isAdmin2){
                // rule does not apply
                continue;
            }

            for (Place geo : name.getPlaces()) {
                if (filterOutBySize(name, geo)) {
                    continue;
                }
                if (isPlace && geo.isPopulated()) {
                    name.addRule(CITY);
                    name.incrementPlaceScore(geo, 1.0);
                } else if (isAdmin1 && geo.isAdmin1()) {
                    name.addRule(ADM1);
                    name.incrementPlaceScore(geo, 1.0);
                } else if (isAdmin2 && geo.isAdministrative()) {
                    name.addRule(ADM2);
                    name.incrementPlaceScore(geo, 1.0);
                }
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /*
         * no-op        
         */
    }

}
