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

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.BoundaryObserver;
import org.opensextant.extractors.geo.CountryObserver;
import org.opensextant.extractors.geo.LocationObserver;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GeocodeRule {

    public static final int AVG_WORD_LEN = 8;
    public static final int UPPERCASE = 2;
    public static final int LOWERCASE = 1;

    public int weight = 0; /* of 10, approximately */
    public String NAME = null;

    protected CountryObserver countryObserver = null;
    protected LocationObserver coordObserver = null;
    protected BoundaryObserver boundaryObserver = null;
    protected Logger log = LoggerFactory.getLogger(getClass());
    protected boolean locationOnly = false;

    protected void log(String msg) {
        log.debug("{}: {}", NAME, msg);
    }

    protected void log(String msg, String val) {
        log.debug("{}: {} / value={}", NAME, msg, val);
    }

    public void setCountryObserver(CountryObserver o) {
        countryObserver = o;
    }

    public void setLocationObserver(LocationObserver o) {
        coordObserver = o;
    }

    public void setBoundaryObserver(BoundaryObserver o) {
        boundaryObserver = o;
    }

    /**
     * Override if rule instance has another view of relevance, e.g.
     * coordinate rule: no coords found, so rule.isRelevant() is FALSE.
     *
     * @return
     */
    public boolean isRelevant() {
        return true;
    }

    /**
     * Create a location ID useful for tracking distinct named features by location.
     * This is not generalizable. It produces a looser identity such as "the city at
     * location": P/PPL/f57yah5
     * 
     * @param p
     * @return feature+location hash
     */
    protected String internalPlaceID(Place p) {
        return (p.getFeatureClass() == null ? p.getPlaceID()
                : String.format("%s/%s/%s", p.getFeatureClass(), p.getFeatureCode(), p.getGeohash()));
    }

    /*
     * Override as needed. static method, because caller need not have an instance
     * of rule
     * to determine if rule was applied to the candidate.
     * Child rules cannot override static method here, so by convention, rules can
     * implement isRuleFor as needed.
     * @param pc
     * @return
     */
    // public static boolean isRuleFor(PlaceCandidate pc) {
    // return false;
    // }

    public boolean sameCountry(Place p1, Place p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        if (p1.getCountryCode() == null || p2.getCountryCode() == null) {
            return false;
        }
        return p1.getCountryCode().equals(p2.getCountryCode());
    }

    /**
     * Quick test to see if two places are contained within the same boundary.
     *
     * @param p1
     * @param p2
     * @return
     */
    public boolean sameBoundary(Place p1, Place p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        if (p1.getAdmin2() != null) {
            if (p1.getAdmin2().equals(p2.getAdmin2())) {
                return true;
            }
        }
        return p1.getHierarchicalPath() != null ? p1.getHierarchicalPath().equals(p2.getHierarchicalPath()) : false;
    }

    public static void setGeohash(Place loc) {
        if (loc.getGeohash() == null) {
            loc.setGeohash(geohash(loc));
        }
    }

    /**
     * Override here as needed.
     *
     * @param name
     * @return
     */
    public boolean evaluateNameFilterOnly(PlaceCandidate name) {
        if (name.isFilteredOut() && !name.isValid()) {
            return true;
        }
        // Some rules may choose early -- and that would prevent other rules
        // from adding evidence
        // In this scheme.
        if (name.getChosen() != null) {
            // DONE
            return true;
        }
        return false;
    }

    /**
     * @param names list of found place names
     */
    public void evaluate(List<PlaceCandidate> names) {
        if (!isRelevant()) {
            return;
        }

        for (PlaceCandidate name : names) {
            // Each rule must decide if iterating over name/geo combinations
            // contributes evidence. But can just as easily see if name.chosen is already
            // set and exit early.
            //
            /*
             * This was filtered out already so ignore.
             */
            if (evaluateNameFilterOnly(name)) {
                continue;
            }

            for (Place geo : name.getPlaces()) {
                if (filterOutByFrequency(name, geo)) {
                    continue;
                }
                evaluate(name, geo);
                if (name.getChosen() != null) {
                    // DONE
                    break;
                }
            }
        }
    }

    /**
     * Certain names appear often around the world... in such cases
     * we can pare back and evaluate only significant places (e.g., cities and
     * states)
     * and avoid say streams and roadways by the same name.
     * If a name, N, occurs in more than 100 to 250 places, then consider only
     * feature classes A and P.
     * The exact distinct count is up for debate. Lower count means we filter out
     * random places sooner
     * for common city/village names.
     *
     * @param name
     * @param geo
     * @return
     */
    protected boolean filterOutByFrequency(PlaceCandidate name, Place geo) {
        if (name.distinctLocationCount() > 100) {
            if (geo.isPopulated() || geo.isAdministrative()) {
                // allow P places and A boundaries to pass through.
                return false;
            }
            return true; // Filter out everything else.
        }

        // Okay, no optimization needed.
        return false;
    }

    /**
     * The one evaluation scheme that all rules must implement.
     * Given a single text match and a location, consider if the geo is a good
     * geocoding
     * for the match.
     *
     * @param name
     *             matched name in text
     * @param geo
     *             gazetteer entry or location
     */
    public abstract void evaluate(PlaceCandidate name, Place geo);

    /**
     * no-op, unless overriden.
     */
    public void reset() {

    }
}
