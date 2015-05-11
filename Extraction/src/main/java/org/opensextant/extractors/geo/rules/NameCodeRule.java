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

/**
 * A rule that associates a CODE with a NAME, when the pattern
 * 
 * "NAME, CODE" appears within N characters.
 * 
 * @author ubaldino
 *
 */
public class NameCodeRule extends GeocodeRule {

    private static int MAX_CHAR_DIST = 5;

    public final static String NAMECODE_RULE = "AdminCode";
    public NameCodeRule() {
        NAME = "AdminCode";
    }

    public void evaluate(List<PlaceCandidate> names) {
        for (int x = 0; x < names.size() - 1; ++x) {
            PlaceCandidate name = names.get(x);
            PlaceCandidate code = names.get(x + 1);

            /*
             * Test if SOMENAME, CODE is the case.
             *         a1.....a2.b1..,  where b1 > a2 > a1, 
             * but distance is minimal from end of name to start of code.
             *         
             */
            if ((code.start - name.end) > MAX_CHAR_DIST) {
                continue;
            }
            /* by this point a place name tag should be marked 
             * as a name or code/abbrev.
             */
            log.info("{} name, code: {} {}?", NAME, name.getText(), code.getText());
            if (code.isAbbreviation) {
                for (Place geo : code.getPlaces()) {
                    if (geo.isAdministrative()) {
                        // Associate the CODE to the NAME that precedes it.
                        name.addAdmin1Evidence(NAME, 1, geo.getAdmin1(), geo.getCountryCode());
                        break;
                    }
                }
            }
        }

    }

    /**
     * No-op. 
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        // no-op

    }

}
