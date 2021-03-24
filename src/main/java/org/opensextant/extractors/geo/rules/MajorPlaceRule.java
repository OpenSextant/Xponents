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

import static org.opensextant.util.GeodeticUtility.geohash;

import java.util.Map;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;

/**
 * Major Place rule -- fire this rule after Country rule.
 * Try to find all countries in scope first, then major places.
 * If you try to infer country from major places first you get a lot of false
 * positives.
 * Country name space is smaller and more reliable.
 * LOTS of caveats: these rules enforce the notion that country names are
 * drivers here, and major places amplify.
 * IF we see a National Capital we can infer a country, provided no countries
 * have been seen in document
 * IF we see a major place, add that evidence weighting it higher if the country
 * of that major place is also
 * mentioned in document.
 *
 * @author ubaldino
 */
public class MajorPlaceRule extends GeocodeRule {

    private static final String MAJ_PLACE_RULE = "MajorPlace";
    public static final String CAPITAL = "MajorPlace.Captial";
    public static final String ADMIN = "MajorPlace.Admin";
    public static final String POP = "MajorPlace.Population";
    private Map<String, Integer> popStats = null;
    private static final int GEOHASH_RESOLUTION = 5;
    private static final int POP_MIN = 60000;

    /**
     * Major Place assigns a score to places that are national capitals, provinces,
     * or cities with sizable population.
     * Log(population) adds up to one point to place weight. Population data is
     * indexed by location/grid using geohash.
     * Source:geonames.org
     *
     * @param populationStats
     *                        optional population stats.
     */
    public MajorPlaceRule(Map<String, Integer> populationStats) {
        NAME = MAJ_PLACE_RULE;
        weight = 2;
        popStats = populationStats;
    }

    /**
     * Determine if this rule was applied to the candidate.
     *
     * @param pc
     * @return
     */
    public static boolean isRuleFor(PlaceCandidate pc) {
        if (pc.hasRule(ADMIN) || pc.hasRule(POP) || pc.hasRule(CAPITAL)) {
            return true;
        }
        return false;
    }

    /**
     * attach either a Capital or Admin region ID, giving it some weight based on
     * various properties or context.
     */
    @Override
    public void evaluate(final PlaceCandidate name, final Place geo) {
        PlaceEvidence ev = null;
        if (geo.isNationalCapital()) {
            // IFF no countries are mentioned, Capitals are good proxies for country.
            inferCountry(geo);
            ev = new PlaceEvidence(geo, CAPITAL, weight(weight + 2, geo));
        } else if (geo.isAdmin1()) {
            ev = new PlaceEvidence(geo, ADMIN, weight(weight, geo));
            inferBoundary(geo);
        } else if (popStats != null && geo.isPopulated()) {
            String gh = geohash(geo);
            geo.setGeohash(gh);
            String prefix = gh.substring(0, GEOHASH_RESOLUTION);
            if (popStats.containsKey(prefix)) {

                int pop = popStats.get(prefix);
                if (pop > POP_MIN) {
                    geo.setPopulation(pop);
                    //
                    // Natural log gives a better, slower curve for population weights.
                    // ln(POP_MIN=25000) = 10.1
                    // ln(5000) = 8.5 wt=-0.5 - Small village, population decrements score.
                    // ln(13000) = 9.5 wt= 0 - Inflection point. Cities larger, have some score
                    // increment.
                    // Smaller population, score decrements.
                    // ln(22,000) = 10.0 wt= 0 - e^10 = 22,000
                    // ln(60,000) = 11.x wt= 1
                    // ln(165,000) = 12.x wt= 2
                    // ln(444,000) = 13.x wt= 3
                    // Etc.
                    // And to make scale even more gradual, wt - 1 or wt/2, wt/3
                    // These population stats cannot overtake all other rules entirely.
                    //
                    int wt = (int) ((Math.log(geo.getPopulation()) - 10)) / 3;
                    ev = new PlaceEvidence(geo, POP, weight(wt, geo));
                }
            }
        }

        if (ev != null) {
            name.markValid(); /* Protects this name from stop filters following this rule. */
            ev.setEvaluated(true);
            name.addEvidence(ev);
            name.incrementPlaceScore(geo, ev.getWeight() * 0.1);
        }
    }

    /**
     * Infer the country if a major place is a capital and no other countries have
     * been found yet.
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

    public void inferBoundary(final Place prov) {
        if (this.boundaryObserver != null) {
            this.boundaryObserver.boundaryLevel1InScope(prov);
        }
    }

    /**
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
