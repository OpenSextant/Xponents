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

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

public abstract class GeocodeRule {

    public int WEIGHT = 0; /* of 100 */
    public static String NAME = null;

    /**
     * 
     * @param nameList
     */
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            // Each rule must decide if iterating over name/geo combinations contributes 
            // evidence.   But can just as easily see if name.chosen is already set and exit early.
            // 
            /*
             * This was filtered out already so ignore.
             */
            if (name.isFilteredOut()) {
                return;
            }
            for (Place geo : name.getPlaces()) {
                evaluate(name, geo);
            }
        }

    }

    public abstract void evaluate(PlaceCandidate name, Place geo);

    /**
     * no-op, unless overriden.
     */
    public void reset() {

    }
}
