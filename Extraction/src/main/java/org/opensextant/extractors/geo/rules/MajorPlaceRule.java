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
import org.opensextant.extractors.geo.PlaceEvidence;

/**
 * Major Place rule -- fire this rule after Country rule.
 * Try to find all countries in scope first, then major places.
 * If you try to infer country from major places first you get a lot of false positives.
 * Country name space is smaller and more reliable.
 * 
 * LOTS of caveats: these rules enforce the notion that country names are drivers here, and major places amplify.
 * IF we see a National Capital we can infer a country, provided no countries have been seen in document
 * IF we see a major place, add that evidence weighting it higher if the country of that major place is also
 * mentioned in document.
 * 
 * @author ubaldino
 *
 */
public class MajorPlaceRule extends GeocodeRule {

    private final static String MAJ_PLACE_RULE = "MajorPlace";
    public final static String CAPITAL = "MajorPlace.Captial";
    public final static String ADMIN = "MajorPlace.Admin";

    public MajorPlaceRule() {
        NAME = MAJ_PLACE_RULE;
        weight = 1;
    }

    /**
     * attach either a Capital or Admin region ID, giving it some weight based on various properties or context.
     */
    @Override
    public void evaluate(final PlaceCandidate name, final Place geo) {
        PlaceEvidence ev = null;
        if (geo.isNationalCapital()) {
            // IFF no countries are mentioned, Capitals are good proxies for country.
            inferCountry(geo);
            ev = new PlaceEvidence(geo, CAPITAL, weight(weight + 1, geo));
        } else if (geo.isAdmin1()) {
            ev = new PlaceEvidence(geo, ADMIN, weight(weight, geo));
        }
        if (ev != null) {
            ev.setEvaluated(true);
            name.addEvidence(ev);
            name.incrementPlaceScore(geo, ev.getWeight() * 0.1);
        }
        //else if (geo.isAdministrative()) {
        //    name.addEvidence(ADMIN, weight(weight - 1, geo), geo);
        //}
    }

    /**
     * Infer the country if a major place is a capital and no other countries have been found yet.
     * 
     * @param capital
     */
    public void inferCountry(final Place capital) {
        if (this.countryObserver == null) {
            return;
        }
        if (countryObserver.countryCount() == 0) {
            this.countryObserver.countryInScope(capital.getCountryCode());
        }
    }

    /**
     * 
     * @param g
     * @return
     */
    private int weight(final int wt, final Place g) {
        int adjusted = wt;
        if (this.countryObserver != null) {
            if (this.countryObserver.countryObserved(g.getCountryCode())) {
                ++adjusted;
            }
        }
        return adjusted;
    }
}
