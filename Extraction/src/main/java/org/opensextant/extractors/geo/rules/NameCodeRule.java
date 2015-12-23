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
import org.opensextant.extractors.geo.PlaceEvidence;

/**
 * A rule that associates a CODE with a NAME, when the pattern
 *
 * "NAME, CODE" appears within N characters of each other.
 * 
 * If CODE.adm1 == NAME.adm1 and CODE is an ADM1 boundary, then flag this is significant.
 * 
 * 
 *
 * @author ubaldino
 *
 */
public class NameCodeRule extends GeocodeRule {

    private static int MAX_CHAR_DIST = 4;

    public final static String NAME_ADMCODE_RULE = "AdminCode";
    public final static String NAME_ADMNAME_RULE = "AdminName";

    public NameCodeRule() {
        NAME = "AdminCodeOrName";
        weight = 3;
    }

    /**
     * Requirement: List of place candidate is a linked list.
     */
    @Override
    public void evaluate(final List<PlaceCandidate> names) {
        for (int x = 0; x < names.size() - 1; ++x) {
            PlaceCandidate name = names.get(x);
            PlaceCandidate code = names.get(x + 1); /* code or name of admin area*/

            if (name.isFilteredOut() || code.isFilteredOut()) {
                continue;
            }
            /*
             * COUNTRY, STATE is not supported under this rule.
             * E.g., Uruguay, Argentina ... This looks like a list of countries
             * However Uruguay is a district in Argentina; Just as Georgia is a state in US
             * and also a country name.
             */
            if (name.isCountry) {
                continue;
            }

            /*
             * Test if SOMENAME, CODE is the case. a1.....a2.b1.., where b1 > a2
             * > a1, but distance is minimal from end of name to start of code.
             *
             */
            if ((code.start - name.end) > MAX_CHAR_DIST) {
                continue;
            }

            /*
             * Not supporting lowercase codes/abbreviations.  'la', 'is', 'un', etc.
             */
            if (code.isLower() && code.getText().length()<4) {
                continue;
            }

            boolean comma = false;
            if (name.getPostmatchTokens() != null) {
                // Parse tokens or text following NAME.... CODE
                // Proximity is one factor, but conventional format should weigh more.
                if (",".equals(name.getPostmatchTokens()[0])) {
                    comma = true;
                }
            }

            /*
             * by this point a place name tag should be marked as a name or
             * code/abbrev. Match the abbreviation with a geographic location
             * that is a state, county, district, etc.
             */
            Place country = code.isCountry ? code.getChosen() : null;
            log.debug("{} name, code: {} in {}?", NAME, name.getText(), code.getText());
            for (Place geo : code.getPlaces()) {
                if (!geo.isAdministrative() || geo.getCountryCode() == null) {
                    continue;
                }

                // Provinces, states, districts, etc. Only. 
                //
                // Make sure you can match an province name or code with the gazetteer entries found:
                //   Boston, Ma.  ==== for 'Ma', resolve to an abbreviation for Massachusetts
                //                     Ignore places called 'Ma'
                // 
                // Place ('Ma') == will have gazetteer metadata indicating if this is a valid abbreviated code for a place. 
                // PlaceCandidate('Ma.') will have textual metadata from given text indicating if it is a code, MA, or abbrev. 'Ma.'
                // 
                // These two situations must match here.   We ignore geo locations that do not fit this profile.
                // 
                boolean lexicalMatch = ((code.isAbbreviation && geo.isAbbreviation()) ||
                        (!code.isAbbreviation && !geo.isAbbreviation()));
                // 
                if (!lexicalMatch) {
                    continue;
                }

                String adm1 = geo.getHierarchicalPath();
                if (adm1 == null && !code.isCountry) {
                    log.debug("ADM1 hierarchical path should not be null");
                    continue;
                }

                // Quick determination if these two places have a containment or geopolitical connection
                //                 
                boolean contains = name.presentInHierarchy(adm1)
                        || (country != null ? name.presentInCountry(country.getCountryCode()) : false);

                if (!contains) {
                    continue;
                }

                /*   CITY, STATE
                 *   CITY, COUNTRY
                 */
                // Associate the CODE to the NAME that precedes it.
                // 
                PlaceEvidence ev = new PlaceEvidence();
                ev.setCountryCode(geo.getCountryCode());
                ev.setAdmin1(geo.getAdmin1());
                ev.setEvaluated(true); // Shunt. Evaluate this rule here.

                int wt = weight + (comma ? 2 : 0);
                if (geo.isAbbreviation() && (code.isAbbreviation || code.isAcronym)) {
                    ev.setRule(NAME_ADMCODE_RULE);
                    ev.setWeight(wt + 1);

                } else {
                    ev.setRule(NAME_ADMNAME_RULE);
                    ev.setWeight(wt);
                }
                name.addEvidence(ev);

                if (boundaryObserver != null) {
                    boundaryObserver.boundaryLevel1InScope(geo);
                }

                // Now choose which location for CITY (name) best suits this.
                // Actually increase score for all geos that match the criteria.
                // 
                for (Place nameGeo : name.getPlaces()) {

                    if (!(nameGeo.isPopulated() || nameGeo.isAdministrative() || nameGeo.isSpot())) {
                        continue;
                    }
                    if (adm1 != null && adm1.equals(nameGeo.getHierarchicalPath())) {
                        name.incrementPlaceScore(nameGeo, ev.getWeight());
                    } else if (sameCountry(nameGeo, country)) {
                        name.incrementPlaceScore(nameGeo, ev.getWeight());
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
