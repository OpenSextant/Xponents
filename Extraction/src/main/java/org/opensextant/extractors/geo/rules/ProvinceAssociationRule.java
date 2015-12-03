/**
 * Copyright 2014 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opensextant.extractors.geo.rules;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceCount;

/**
 * Give a list of province metadata that scopes the document, mark the list of
 * evaluated PlaceCandidates' locations as relevant or not. Lack of association
 * of a candidate does not down-grade any location candidate. This association
 * only promotes matches.
 *
 * @author ubaldino
 *
 */
public class ProvinceAssociationRule extends GeocodeRule {

    private Map<String, Place> relevantProvinceID = new HashMap<>();

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
            Place adm1 = count.place;
            relevantProvinceID.put(adm1.getHierarchicalPath(), adm1);
        }

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
     * 
     * </pre>
     */
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            /*
             * This was filtered out already so ignore.
             */
            if (name.isFilteredOut()) {
                continue;
            }
            if (name.getChosen() != null) {
                // DONE
                continue;
            }

            // All or any of these Geos for a name could be in scope.
            // Assess all of them.
            for (Place adm1 : relevantProvinceID.values()) {
                if (name.presentInHierarchy(adm1.getHierarchicalPath())) {
                    name.addAdmin1Evidence("InferredAdmin1", weight, adm1.getAdmin1(), adm1.getCountryCode());
                }
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        // Don't evaluate individual Geos -- too many.
    }

}
