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

public class MajorPlaceRule extends GeocodeRule {


    private final static String MAJ_PLACE_RULE = "MajorPlace";
    public final static String CAPITAL= MAJ_PLACE_RULE+".Captial";
    public final static String ADMIN = MAJ_PLACE_RULE+".Admin";

    public MajorPlaceRule(){
        NAME = MAJ_PLACE_RULE;
    }



    /**
     * attach either a Capital or Admin region ID
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        if (geo.isNationalCapital()){
            name.addEvidence(CAPITAL, 1, geo);

        } else if (geo.isAdministrative()){
            name.addEvidence(ADMIN, 1, geo);
        }
    }
}
