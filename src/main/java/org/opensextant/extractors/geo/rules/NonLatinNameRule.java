package org.opensextant.extractors.geo.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.util.TextUtils;

/**
 * GeocodeRule called only if document is non-Latin such as C/J/K or MiddleEastern scripts.
 */
public class NonLatinNameRule extends GeocodeRule {

    @Override
    public boolean filterByNameOnly(PlaceCandidate name) {

        // Assess lesser known places if only two chars long or so:
        if (name.getLength() < 3 && !name.isCountry) {
            name.setFilteredOut(true);
            name.addRule("Lang.LengthHeuristic");
            return true;
        } else
        // Assess general alpha to non-alpha content in the name
        {
            int charRatio = name.hasCJKText() ? 3 : name.hasMiddleEasternText() ? 5 : -1;
            if ( charRatio > 0 && !NonsenseFilter.assessPhraseDensity(name.getText(), charRatio)) {
                name.addRule("Lang.DensityHeuristic");
                name.setFilteredOut(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /* no-op */
    }
}
