package org.opensextant.extractors.geo.rules;

import java.util.HashMap;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

/**
 * FeatureRule is a rule that makes use of feature distribution across the
 * gazetteer as well as known properties of specific feature types that make
 * them more or less likely to
 * be the best location type for a particular mention.
 * This may or may not be tie breakers -- within a feature class for the same
 * name, this provides
 * no tie breaker.
 * "Boston", OH (P/PPL) ~ "Boston", MA (P/PPL) -- feature stats
 * are the same.
 * "Boston", ?? (T/ISL) ... is a different feature type and in this case is less
 * likely to be the best location.
 * Note -- other evidence and context has to overwhelm certain feature stats
 * here.
 * TODO: this overtakes the "DefaultScore" component from "scoreFeature()"
 *
 * @author ubaldino
 */
public class FeatureRule extends GeocodeRule {

    private static FeatureClassMeta[] fcmeta = {
            /*
             * Example: 3.5% of placenames are Administrative boundaries.
             * But they are significant and more likely to be mentioned because of that.
             * So they are weighted heavier per entry. A resulting factor of wt x proportion
             * = factor
             * For "A" class names, 10.0 x 0.035 = 0.35 factor
             * Keeping Cities/Towns as a 1.0 weight; Adjust others relative to that.
             **/
            new FeatureClassMeta("A", 700000, 11.0),
            new FeatureClassMeta("P", 10000000, 1.0),
            new FeatureClassMeta("P/PPLX", 10000000, 0.7), // Ruins and unused historical names.
            new FeatureClassMeta("S", 2700000, 0.7),
            new FeatureClassMeta("T", 2300000, 0.6),
            new FeatureClassMeta("L", 700000, 0.7),
            new FeatureClassMeta("V", 85000, 0.6),
            new FeatureClassMeta("R", 65000, 0.5),
            new FeatureClassMeta("U", 12000, 0.1),
            new FeatureClassMeta("H", 3200000, 0.5),
            new FeatureClassMeta("H/RSV", 100000, 0.3),
            new FeatureClassMeta("H/STM", 1600000, 0.02),
            new FeatureClassMeta("H/SPNG", 100000, 0.02),
            new FeatureClassMeta("H/WLL", 100000, 0.01) };

    public static HashMap<String, FeatureClassMeta> featWeights = new HashMap<>();

    static {
        for (FeatureClassMeta fc : fcmeta) {
            featWeights.put(fc.label, fc);
        }
    }

    static final int[] FEAT_RESOLUTION = { 6, 5, 1 };

    /**
     * Find feature metadata if we have it.
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
        return null;
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
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {

        FeatureClassMeta fc = lookupFeature(geo);
        if (fc == null) {
            return;
        }

        name.incrementPlaceScore(geo, 10 * fc.factor, NAME);
    }
}
