/**
 Copyright 2009-2013 The MITRE Corporation.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.


 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
**/
package org.opensextant.extractors.geo;

import org.opensextant.data.Place;

/**
 * A class to hold a Place and a score together. Used by PlaceCandidate to rank places.
 */
public class ScoredPlace implements Comparable<Object> {

    Place place;
    Double score;

    public Place getPlace() {
        return place;
    }

    public void setPlace(Place place) {
        this.place = place;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public ScoredPlace(Place pl, Double scr) {
        this.place = pl;
        this.score = scr;
    }

    @Override
    // compare by score
    public int compareTo(Object o) {
        if (o instanceof ScoredPlace) {
            return -1 * this.getScore().compareTo(((ScoredPlace) o).getScore());
        } else {
            return 0;
        }
    }

}
