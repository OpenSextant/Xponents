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

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.extractors.geo.BoundaryObserver;
import org.opensextant.extractors.geo.CountryObserver;
import org.opensextant.extractors.geo.LocationObserver;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.opensextant.util.GeodeticUtility.geohash;

public abstract class GeocodeRule {

    public static final int AVG_WORD_LEN = 8;

    public static final int UPPERCASE = 2;
    public static final int LOWERCASE = 1;

    public static final String LEX1 = "LexicalMatch";
    public static final String LEX2 = "LexicalMatch.NoCase";

    public int weight = 0; /* of 10, approximately */
    public String NAME = null;

    protected String defaultMethod = null;

    protected CountryObserver countryObserver = null;
    protected LocationObserver coordObserver = null;
    protected BoundaryObserver boundaryObserver = null;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected boolean locationOnly = false;

    protected void logMsg(String msg, String val) {
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

    public void setDefaultMethod(String m) {
        defaultMethod = m;
    }

    /** for the purposes of Geocoder Rule reasoning determine the case. */
    public int textCase(TextInput t) {
        if (t.isLower) {
            return LOWERCASE;
        } else if (t.isUpper) {
            return UPPERCASE;
        }
        return 0;
    }

    protected int textCase = 0;

    public void setTextCase(TextInput t) {
        textCase = textCase(t);
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

    public static boolean isShort(int matchLen) {
        return matchLen <= AVG_WORD_LEN;
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
     * To compare places by their country code alone. Useful for CITY.cc =? COUNTRY.cc mentions.
     * @param cc1 code 1
     * @param cc2 code 2
     * @return
     */
    public boolean sameCountry(String cc1, String cc2) {
        if (cc1 == null || cc2 == null) {
            return false;
        }
        return cc1.equalsIgnoreCase(cc2);
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
        return p1.getHierarchicalPath() != null && p1.getHierarchicalPath().equals(p2.getHierarchicalPath());
    }

    public boolean similarFeatures(Place geo1, Place geo2) {
        if (geo1.getFeatureCode() != null) {
            if (geo1.getFeatureCode().equals(geo2.getFeatureCode())) {
                return true;
            }
        }
        return geo1.isSame(geo2);
    }

    public static void setGeohash(Place loc) {
        if (loc.getGeohash() == null) {
            loc.setGeohash(geohash(loc));
        }
    }

    /**
     * Increment score for lexical matches accoringly:
     * - non-ASCII match:       2.5 pts
     * - ASCII match:           1.5 pts
     * - Case insenstive match: 0.5 pts
     * Simple example, with one char in Name match, one char in Geo Name
     *  ø = ø   2.5
     *  o = o   1.5
     *  O = O   1.5
     *  O = o   0.5
     *
     *  Mention ? Gazetteer Entry
     *  Boston != Bøstøn,  no points.
     *  Bøstøn == Bøstøn,  2.5 points
     *
     * @param name
     * @param geo
     */
    public void sameLexicalName(PlaceCandidate name, Place geo) {
        if (geo.getName().equals(name.getText())) {
            name.incrementPlaceScore(geo, name.isASCII() ? 1.5 : 2.5, LEX1);
        } else if (geo.getName().equalsIgnoreCase(name.getText())) {
            name.incrementPlaceScore(geo, 0.5, LEX2);
        }
    }

    /**
     * Override here as needed.
     *
     * @param name
     * @return
     */
    public boolean filterByNameOnly(PlaceCandidate name) {
        if (name.isFilteredOut() && !name.isValid()) {
            return true;
        }
        // Some rules may choose early -- and that would prevent other rules
        // from adding evidence
        return name.getChosen() != null;
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
            if (filterByNameOnly(name)) {
                continue;
            }

            for (ScoredPlace geoScore : name.getPlaces()) {
                if (filterOutByFrequency(name, geoScore.getPlace())) {
                    continue;
                }
                evaluate(name, geoScore.getPlace());
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
     * states) and avoid say streams and roadways by the same name.
     * If a name, N, occurs in more than 100 to 250 places, then consider only
     * feature classes A and P.
     * The exact distinct count is up for debate. Lower count means we filter out
     * random places sooner for common city/village names.
     *
     * @param name
     * @param geo
     * @return
     */
    protected boolean filterOutByFrequency(PlaceCandidate name, Place geo) {
        if (name.distinctLocationCount() > 100) {
            // allow P places and A boundaries to pass through.
            return !geo.isPopulated() && !geo.isAdministrative();// Filter out everything else.
        }

        // Okay, no optimization needed.
        return false;
    }

    /**
     * The one evaluation scheme that all rules must implement.
     * Given a single text match and a location, consider if the geo is a good
     * geocoding for the match.
     *
     * @param name matched name in text
     * @param geo  gazetteer entry or location
     */
    public abstract void evaluate(PlaceCandidate name, Place geo);

    /**
     * no-op, unless overriden.
     */
    public void reset() {

    }
}
