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

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

public class CountryRule extends GeocodeRule {

    public CountryRule() {
        WEIGHT = 2;
        NAME = "CountryName";
    }

    /**
     * Assess which candidate tags are references to countries.
     * @param name list of candidates to evaluate for country evidence.
     * @param geo  country/geo to evaluate
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        if (geo.isCountry()) {
            if (name.isAcronym || name.isAbbreviation) {
                name.addCountryEvidence("CountryCode", 1, geo.getCountryCode());
            } else {
                name.addCountryEvidence(NAME, WEIGHT, geo.getCountryCode());
            }
        }
    }
}
