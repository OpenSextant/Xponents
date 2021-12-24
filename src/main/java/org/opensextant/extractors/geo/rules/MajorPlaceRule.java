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
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.ScoredPlace;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public static final String MENTIONED_COUNTRY = "MajorPlace.InCountry";
    private Map<String, Integer> popStats = null;
    private static final int GEOHASH_RESOLUTION = 5;
    private static final int POP_MIN = 50000;

    final Set<String> visitedPlaces = new HashSet<>();

    /**
     * Major Place assigns a score to places that are national capitals, provinces,
     * or cities with sizable population.
     * Log(population) adds up to one point to place weight. Population data is
     * indexed by location/grid using geohash.
     * Source:geonames.org
     * Population stats are deterministic -- they do not change during the
     * processing
     * and they are not context specific. So we only assess population per location
     * ONCE
     * not per mention.
     * 
     * @param populationStats
     *                        optional population stats.
     */
    public MajorPlaceRule(Map<String, Integer> populationStats) {
        NAME = MAJ_PLACE_RULE;
        weight = 2;
        popStats = populationStats;
        locationOnly = true;
    }

    @Override
    public void reset() {
        visitedPlaces.clear();
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            /*
             * Prerequisite rule is NameCode rule -- this should
             * have fired on all names prior to this. Names/Abbreviations "markedValid()"
             * will be assessed.
             */
            if (name.isFilteredOut()) {
                continue;
            }
            if (ignoreAbbreviations(name)) {
                continue;
            }

            boolean isAbbrev = name.isShortName();
            boolean matchedAdmin = false;

            for (ScoredPlace geoScore : name.getPlaces()) {
                Place geo = geoScore.getPlace();
                if (filterOutByFrequency(name, geo)) {
                    continue;
                }

                // Check for mismatched case -- BKA matching Bka (site feature)
                if (isAbbrev) {
                    if (geo.isAdministrative() && geo.isShortName()) {
                        matchedAdmin = true;
                    } else {
                        continue;
                    }
                }

                evaluate(name, geo);
            }

            // IF no geo lines up with the abbrevation eliminate it.
            if (isAbbrev && !matchedAdmin) {
                name.setFilteredOut(true);
            }
        }
    }

    /**
     * If candidate is marked valid - allow
     * If candidate is abbreviation and not valid, explicitly filter out --
     * we don't care about loose references to places.
     * "CITY, MD" is valid if city is in Maryland, but
     * "NAME, MD" may be a name of a doctor for example.
     * 
     * @param pc
     * @return flag
     */
    private static boolean ignoreAbbreviations(PlaceCandidate pc) {
        return pc.isAbbreviation && !pc.isValid();
    }

    /**
     * Determine if this rule was applied to the candidate.
     *
     * @param pc
     * @return
     */
    public static boolean isRuleFor(PlaceCandidate pc) {
        return pc.hasRule(ADMIN) || pc.hasRule(POP) || pc.hasRule(CAPITAL);
    }

    /**
     * attach either a Capital or Admin region ID, giving it some weight based on
     * various properties or context.
     */
    @Override
    public void evaluate(final PlaceCandidate name, final Place geo) {

        setGeohash(geo);

        String pid = String.format("%s/%s", geo.getPlaceID(), geo.getPlaceName());
        if (visitedPlaces.contains(pid)) {
            return;
        }
        visitedPlaces.add(pid);
        PlaceEvidence ev = null;
        if (geo.isNationalCapital()) {
            // IFF no countries are mentioned, Capitals are good proxies for country.
            inferCountry(geo);
            ev = new PlaceEvidence(geo, CAPITAL, weight + 2.0);
        } else if (geo.isAdmin1()) {
            ev = new PlaceEvidence(geo, ADMIN, weight);
            inferBoundary(name.getNDTextnorm(), geo);
        } else if (popStats != null && geo.isPopulated()) {
            String gh = geo.getGeohash();
            String prefix = gh.substring(0, GEOHASH_RESOLUTION);
            if (popStats.containsKey(prefix)) {

                int pop = popStats.get(prefix);
                if (pop > POP_MIN) {
                    geo.setPopulation(pop);
                    // Looking for a scale that is able compare major cities by population.
                    // A city of 50K vs. 75K is not much different. But a city of 500K
                    // is much more likely to be mentioned.
                    // Log scales give a lot of weight to smaller numbers, and pure linear
                    // proportion
                    // is not helpful (hard to say a city of 5 million is 100x more likely to be
                    // mentioned
                    // than one of 50K. This scale uses order of magnitude, but slides it and
                    // squishes it.
                    // To a number that fits meaningfully in the range of 0 to 1.0
                    //
                    // Weight (Population) = 1/10 * (ln(Population) - 10)
                    //
                    // power of E equated to city population:
                    // 10 = 22K
                    // 11 = 58K
                    // 12 = 168K
                    // 13 = 440K
                    // 14 = 1.2m
                    // 15 = 3.2m
                    // 16 = 8.8m
                    // Bounds -- 50K minimum, The power law allows scale to grow quickly
                    // 50K -> weight = 10.82/10 = 1.082
                    // 500K -> weight = 13.11/10 = 1.311
                    // 5000K -> weight = 15.42/10 = 1.542
                    // But lopping off the base order of magnitude (-10) smooths out the scale
                    // and makes it fit in a range of 0 to 1.0 approximately
                    // 50K -> weight = 10.82/10 - 1 = 0.082
                    // 500K -> weight = 13.11/10 - 1 = 0.311
                    // 5000K -> weight = 15.42/10 - 1 = 0.542
                    //
                    double wt = Math.log(geo.getPopulation()) - 10;
                    ev = new PlaceEvidence(geo, POP, wt);
                }
            }
        }

        if (ev != null) {
            name.markValid(); /* Protects this name from stop filters following this rule. */
            ev.setEvaluated(true);
            name.addEvidence(ev);
            name.incrementPlaceScore(geo, ev.getWeight(), ev.getRule());
            log.debug("PlaceEvidence score {}, on Place {}", ev.getWeight(), geo);

            if (this.countryObserver != null) {
                if (this.countryObserver.countryObserved(geo.getCountryCode())) {
                    PlaceEvidence ev2 = new PlaceEvidence(geo, MENTIONED_COUNTRY, 2.0);
                    name.addEvidence(ev2);
                    name.incrementPlaceScore(geo, ev2.getWeight(), ev2.getRule());
                    log.debug("PlaceEvidence - Mentioned Country score {}, on Place {}", ev2.getWeight(), geo);
                }
            }

        }
    }

    /**
     * Infer the country if a major place is a capital and no other countries have
     * been found yet.
     *
     * @param capital
     */
     void inferCountry(final Place capital) {
        if (this.countryObserver == null) {
            return;
        }
        if (countryObserver.countryCount() == 0) {
            this.countryObserver.countryInScope(capital.getCountryCode());
        }
    }

     void inferBoundary(String nameNorm, Place prov) {
        if (this.boundaryObserver != null) {
            this.boundaryObserver.boundaryLevel1InScope(nameNorm, prov);
        }
    }
}
