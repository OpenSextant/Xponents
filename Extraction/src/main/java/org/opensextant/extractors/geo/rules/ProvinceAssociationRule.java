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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.GeonamesUtility;

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

    private Set<String> relevantProvinceID = new HashSet<String>();

    @Override
    public void reset() {
        relevantProvinceID.clear();
    }

    public void setProvinces(List<Place> p) {
        relevantProvinceID.clear();
        for (Place adm1 : p) {
            relevantProvinceID.add(GeonamesUtility.getHASC(adm1.getCountryCode(), adm1.getAdmin1()));
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        String hiearchicalKey = GeonamesUtility.getHASC(geo.getCountryCode(), geo.getAdmin1());
        if (relevantProvinceID.contains(hiearchicalKey)) {
            // Mark as in-scope if geo.ADM1 is in list of relevant provinces.
            //
            // Add to score for geo instance.
            name.addAdmin1Evidence("InferredAdmin1", 1, geo.getAdmin1(), geo.getCountryCode());
        }
    }

}
