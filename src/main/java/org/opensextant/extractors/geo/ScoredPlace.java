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

import org.opensextant.data.Place;

/**
 * A class to hold a Place and a score together. Used by PlaceCandidate to rank
 * places.
 *
 * @author dlutz
 * @author ubaldino
 */
public class ScoredPlace extends Place implements Comparable<ScoredPlace> {

    private double score = 0.0;

    public ScoredPlace(String plid, String nm) {
        super(plid, nm);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void incrementScore(double d) {
        score += d;
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
        if (getName() != null) {
            return String.format("%s (%s, %s, %s), score=%03.2f", getName(), getAdmin1(), getCountryCode(),
                    getFeatureCode(), getScore());
        }
        return "No Name";
    }

}
