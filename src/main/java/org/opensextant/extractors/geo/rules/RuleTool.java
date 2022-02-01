package org.opensextant.extractors.geo.rules;

import org.opensextant.extractors.geo.PlaceCandidate;

public class RuleTool {
    /**
     * test if candidate match has trivial evidence.
     * These rules -- default score, feature score, and lexical match (case-insensitive) --
     * are standard trivial rules.
     *
     * @param pc
     * @return
     */
    public static boolean hasOnlyDefaultRules(PlaceCandidate pc) {
        int ruleCount = pc.getRules().size();
        switch (ruleCount) {
            case 1:
                return pc.hasRule(PlaceCandidate.DEFAULT_SCORE);
            case 2:
                return pc.hasRule(PlaceCandidate.DEFAULT_SCORE) && pc.hasRule(FeatureRule.FEAT_RULE);
            case 3:
                return pc.hasRule(PlaceCandidate.DEFAULT_SCORE) && pc.hasRule(FeatureRule.FEAT_RULE)
                        && pc.hasRule(NameRule.LEX2);
            default:
                return false;
        }
    }
}
