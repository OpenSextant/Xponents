package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContextualOrganizationRule extends GeocodeRule {

    final Set<String> reEval = new HashSet<>();

    @Override
    public void reset() {
        reEval.clear();
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        if (!isRelevant()) {
            return;
        }

        for (PlaceCandidate name : names) {
            if (!name.hasRule(PersonNameFilter.NAME_IN_ORG_RULE)) {
                continue;
            }
            log.debug(" City Name in Org Name? {}", name);
            if (!name.isFilteredOut()) {
                continue;
            }

            // X-reference this city name that occurs in an organization name,
            // with any state or division "ADM2" or "ADM1" references.
            // E.g., "Xyz City Council" where city "Xyz City" may reside in a state "S"
            // mentioned elsewhere in document.
            for (ScoredPlace geoScore : name.getPlaces()) {
                if (boundaryObserver.placeMentionCount().containsKey(geoScore.getPlace().getHierarchicalPath())) {
                    name.setFilteredOut(false);
                    name.addRule("ContextualOrg");
                    reEval.add(name.getTextnorm());
                    continue;
                }
            }
        }

        /*
         * Re-evaluate items that may have been filtered because the name appeared in an organization
         * name where the org name was not necessarily geographically relevant until now.
         */
        for (PlaceCandidate name : names) {
            if (name.isFilteredOut() && reEval.contains(name.getTextnorm())) {
                name.setFilteredOut(false);
                name.addRule("ContextualOrg.Relation");
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
    }

}
