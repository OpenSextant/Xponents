package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

import java.util.HashMap;
import java.util.Map;

/**
 * FeatureRule is a rule that makes use of feature distribution across the
 * gazetteer as well as known properties of specific feature types that make
 * them more or less likely to be the best location type for a particular mention.
 * This may or may not be tie breakers -- within a feature class for the same
 * name, this provides  no tie breaker.
 *
 * "Boston", OH (P/PPL) ~ "Boston", MA (P/PPL) -- feature stats are the same.
 * "Boston", ?? (T/ISL) ... is a different feature type and in this case is less
 * likely to be the best location.
 * Note -- other evidence and context has to overwhelm certain feature stats here.
 * TODO: this overtakes the "DefaultScore" component from "scoreFeature()"
 * @author ubaldino
 */
public class FeatureRule extends GeocodeRule {


    private static final FeatureClassMeta[] featureMeta = {
            /* Administrative regions */
            new FeatureClassMeta("A", 11), // administrative areas are slightly above P/PPL default 10.
            new FeatureClassMeta("A/ADM1", 16), // major provinces
            new FeatureClassMeta("A/ADM2", 13), // county-level
            new FeatureClassMeta("A/PCL", 16), // countries and territories

            /* Places: cities, villages, ruins, etc. */
            new FeatureClassMeta("P", 10),
            new FeatureClassMeta("P/PPL", 10), // most common lookup. Hold as a control point at "10"
            new FeatureClassMeta("P/PPLC", 15), // capital cities
            new FeatureClassMeta("P/PPLA", 10), // on par with A class default
            new FeatureClassMeta("P/PPLG", 9),
            new FeatureClassMeta("P/PPLH", 8),
            new FeatureClassMeta("P/PPLQ", 7),
            new FeatureClassMeta("P/PPLX", 7),
            new FeatureClassMeta("P/PPLL", 8),
            /* Other */
            new FeatureClassMeta("L", 6),
            new FeatureClassMeta("R", 6),
            new FeatureClassMeta("H", 7),
            new FeatureClassMeta("H/RSV", 2),
            new FeatureClassMeta("H/WLL", 2),
            new FeatureClassMeta("H/SPNG", 2),
            new FeatureClassMeta("V", 7),
            new FeatureClassMeta("S", 8),
            new FeatureClassMeta("U", 5),
            new FeatureClassMeta("T", 5),
            new FeatureClassMeta("T/ISL", 6),
            new FeatureClassMeta("T/ISLS", 6)
    };

    private static final FeatureClassMeta DEFAULT_FEATURE_WEIGHT = new FeatureClassMeta("UNK", 5);


    private static final int[] FEAT_RESOLUTION = { 6, 5, 1 };
    public static final HashMap<String, FeatureClassMeta> featWeights = new HashMap<>();

    static {
        for (FeatureClassMeta fc : featureMeta) {
            featWeights.put(fc.label, fc);
        }
    }

    /**
     * Find feature metadata if we have it;  At a minimum
     */
    public static FeatureClassMeta lookupFeature(Place geo) {
        String fullFeature = String.format("%s/%s", geo.getFeatureClass(), geo.getFeatureCode());

        String ft;
        for (int ftlen : FEAT_RESOLUTION) {
            ft = fullFeature;
            if (fullFeature.length() > ftlen) {
                ft = fullFeature.substring(0, ftlen);
            }
            FeatureClassMeta fc = featWeights.get(ft);
            if (fc != null) {
                return fc;
            }
        }
        return DEFAULT_FEATURE_WEIGHT;
    }

    public static final String FEAT_RULE = "Feature";

    public FeatureRule() {
        this.NAME = FEAT_RULE;
    }

    /**
     * Assess the feature of each location found, and provide a score on that geo
     * based on the feature
     * type apriori score (what we prefer in general) and the likelihood of it being
     * mentioned (relative
     * popularity of that feature class)
     */
    @Deprecated
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {

        FeatureClassMeta fc = lookupFeature(geo);
        if (fc == null) {
            return;
        }

        name.incrementPlaceScore(geo, 10 * fc.factor, NAME);
    }
}
