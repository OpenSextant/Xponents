/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
 */
package org.opensextant.extractors.geo;

import java.util.HashSet;
import java.util.Set;

import org.opensextant.data.Place;

/**
 * A class to hold a Place and a score together. Used by PlaceCandidate to rank
 * places.
 *
 * @author dlutz
 * @author ubaldino
 */
public class ScoredPlace implements Comparable<ScoredPlace> {

    private double score = 0.0;
    private Set<String> rules = null;
    private Place place = null;

    public ScoredPlace() {
    }

    public ScoredPlace(String plid, String nm) {
        place = new Place(plid, nm);
    }

    public Place getPlace() {
        return place;
    }

    public void setPlace(Place p) {
        place = p;
    }

    public double getScore() {
        return score;
    }

    public void incrementScore(double d) {
        score += d;
    }

    /**
     * Increment the score for a given rule exactly once.
     * TODO: expand this to allow incremental scores for Document level vs. local
     * span-level
     * scoring of specific location candidates.
     *
     * @param d    score
     * @param rule rule name
     */
    public void incrementScore(double d, String rule) {
        if (!hasRule(rule)) {
            score += d;
            addRule(rule);
        }
    }

    public boolean hasRule(String r) {
        if (rules == null) {
            return false;
        }
        return rules.contains(r);
    }

    public void addRule(String r) {
        if (rules == null) {
            rules = new HashSet<>();
        }
        rules.add(r);
    }

    /**
     * Ordering: higher score comes first in our sortable lists. So A.score &gt;
     * B.score yields -1, so A is ordered first.
     */
    @Override
    public int compareTo(ScoredPlace o) {
        return Double.compare(getScore(), o.getScore());
    }

    @Override
    public String toString() {
        if (place == null) {
            return "";
        }
        if (place.getName() != null) {
            return String.format("%s (%s, %s, %s), score=%03.2f", place.getName(), place.getAdmin1(), place.getCountryCode(),
                    place.getFeatureCode(), getScore());
        }
        return "No Name";
    }

}
