/*
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

    private static final String CNAME = "Country.name";
    private static final String CCODE = "Country.code";

    public CountryRule() {
        weight = 1;
        NAME = "Country";
    }

    /**
     * Assess which candidate tags are references to countries.
     *
     * @param name
     *             list of candidates to evaluate for country evidence.
     * @param geo
     *             country/geo to evaluate
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {

        if (!geo.isCountry()) {
            // This gate should prevent evaluation on every location.
            return;
        }

        // Cases on Name sense:
        // - AL -- must match Geo "AL" that is acronynm
        // - Al   -> Stop word in Spanish or Arabic transliteration
        // - al   -> "" "" 
        // - Al.  -> inconclusive
        // - Ala. -> Alabama, not a country
        // - Alb. -> Albania
        // etc.

        // Otherwise this is some country name or reference.
        // Name case must match for any code to align.
        //
        if (name.isAcronym && name.isUpper() && geo.isUppercaseName()) {
            // "AL" = "AL"
            name.addCountryEvidence(CCODE, weight, geo.getCountryCode(), geo);
        } else if (name.getLength() > 3) {
            if (name.isAbbreviation && geo.isAbbreviation()) {
                // "Alb"   = "Alb." (For Albania, for example)
                // "U.S.A" = "u.s.a."
                name.addCountryEvidence(CCODE, weight, geo.getCountryCode(), geo);
            } else {
                // "Albania" = "ALBANIA" 
                name.addCountryEvidence(CNAME, weight + 2, geo.getCountryCode(), geo);
            }
        } else {
            name.isCountry = false;
            return;
        }

        name.choose(geo);
        log("Chose Country", name.getText());

        if (countryObserver != null) {
            countryObserver.countryInScope(geo.getCountryCode());
        }

    }
}
