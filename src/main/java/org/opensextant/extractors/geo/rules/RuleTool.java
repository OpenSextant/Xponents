package org.opensextant.extractors.geo.rules;

import org.opensextant.extractors.geo.PlaceCandidate;

public class RuleTool {
    /**
     * test if candidate match has trivial evidence.
     * 
     * @param pc
     * @return
     */
    public static boolean hasOnlyDefaultRules(PlaceCandidate pc) {
        int ruleCount = pc.getRules().size();
        return (pc.hasRule(PlaceCandidate.DEFAULT_SCORE) && ruleCount == 1)
                || (pc.hasRule(PlaceCandidate.DEFAULT_SCORE) && pc.hasRule(FeatureRule.FEAT_RULE) && ruleCount == 2);
    }


}
