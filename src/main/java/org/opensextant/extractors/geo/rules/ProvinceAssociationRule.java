/**
 * Copyright 2014 The MITRE Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceCount;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Give a list of province metadata that scopes the document, mark the list of
 * evaluated PlaceCandidates' locations as relevant or not. Lack of association
 * of a candidate does not down-grade any location candidate. This association
 * only promotes matches.
 *
 * @author ubaldino
 */
public class ProvinceAssociationRule extends GeocodeRule {

    private final HashSet<String> relevantProvinceID = new HashSet<>();

    public ProvinceAssociationRule() {
        weight = 5;
    }

    @Override
    public void reset() {
        relevantProvinceID.clear();
    }

    public void setProvinces(Collection<PlaceCount> p) {
        if (p == null) {
            return;
        }
        for (PlaceCount count : p) {
            relevantProvinceID.add(count.label);
        }
    }

    /**
     *
     */
    @Override
    public boolean isRelevant() {
        return !relevantProvinceID.isEmpty();
    }

    /**
     * Evaluate all candidate place mentions by seeing if any resolved Province
     * contains any geo locations with the mentioned name.
     *
     * <pre>
     * given Bala (adm1=XU.45) province is in scope.
     *
     * assess the list of names = [Name1(@geo1, @geo2, @geo3)], Name2(,etc), etc];
     * if any geo locations for Name1 occur also within the province of XU.45, raise the weighting of the location
     * as a better answer to "where is Name1?"
     * </pre>
     */
    @Override
    public void evaluate(List<PlaceCandidate> names) {
        if (!isRelevant()) {
            return;
        }

        for (PlaceCandidate name : names) {
            /*
             * This was filtered out already so ignore.
             */
            if (filterByNameOnly(name)) {
                continue;
            }

            // All or any of these Geos for a name could be in scope.
            // Assess all of them.
            for (String adm1_path : relevantProvinceID) {
                if (name.presentInHierarchy(adm1_path)) {
                    if (adm1_path.contains(".")) {
                        String[] parts = adm1_path.split("\\.");
                        String cc = parts[0];
                        String a = parts[1];
                        name.addAdmin1Evidence("InferredAdmin1", weight, a, cc);
                    } else {
                        log.info("Unknown ADM1 boundary path {}", adm1_path);
                    }
                }
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        // Don't evaluate individual Geos -- too many.
    }

}
