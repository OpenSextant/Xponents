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

import static org.opensextant.extractors.geo.rules.RuleTool.hasOnlyDefaultRules;

/**
 * A rule that associates a CODE with a NAME, when the pattern
 * "NAME, CODE" appears within N characters of each other.
 * If CODE.adm1 == NAME.adm1 and CODE is an ADM1 boundary, then flag this is
 * significant.
 *
 * @author ubaldino
 */
public class NameCodeRule extends GeocodeRule {

    /**
     * Character distance between NAME CODE -- this is a simple distance
     * metric to avoid guessing or parsing what is between the NAME CODE or NAME
     * NAME
     * E.g., San Francisco, Uraguay - 2 char distance. Allow up to 3
     * E.g., San Francisco to Uraguay - 4 char distance.
     */
    private static int MAX_CHAR_DIST = 5;

    public static final String NAME_ADMCODE_RULE = "AdminCode";
    public static final String NAME_ADMNAME_RULE = "AdminName";

    public NameCodeRule() {
        NAME = "AdminCodeOrName";
        weight = 3;
    }

    private final static boolean ignoreShortLowercase(final PlaceCandidate pc) {
        return (pc.isLower() && pc.getLength() < 4);
    }

    private final static boolean ignoreMixedCaseAbbreviation(final PlaceCandidate pc) {
        return (pc.isMixedCase() && pc.getLength() < 4);
    }

    public static boolean isRuleFor(PlaceCandidate name) {
        return name.hasRule(NAME_ADMCODE_RULE) || name.hasRule(NameCodeRule.NAME_ADMNAME_RULE);
    }

    /**
     * Requirement: List of place candidate is a linked list.
     */
    @Override
    public void evaluate(final List<PlaceCandidate> names) {
        for (int x = 0; x < names.size(); ++x) {
            PlaceCandidate name = names.get(x);
            if (name.isFilteredOut()) {
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
             * CASE:
             * CODE text.... -(a) CODE is the first match in the doc; No preceeding NAME.
             * ....text NAME -(b) NAME is last match, no qualifying CODE follows
             * ... CODE .... -(c) CODE is the only match in the doc.
             * a and c are variations of each other.
             * b applies to the match, which may actually be a name or code. But we
             * filter out unattached codes. Names are left as is.
             */
            boolean isLast = x == names.size() - 1;

            boolean canIgnoreName = ignoreShortLowercase(name) || ignoreNonAdminCode(name);
            if (x == 0 || isLast) {
                /*
                 * end of line logic. Either you have a dangling name or code, or you have a
                 * single candidate.
                 * but we still want to evaluate it. This is one of the largest sources of
                 * noise.
                 * Evaluate name as if it is the abbreviation, because it is last (or first and
                 * only).
                 */
                if (canIgnoreName) {
                    name.setFilteredOut(true);
                    continue;
                }
                if (isLast) {
                    continue;
                }
            }

            PlaceCandidate code = names.get(x + 1); /* code or name of admin area */
            if (code.isFilteredOut()) {
                continue;
            }

            boolean canIgnoreShortCode = ignoreShortLowercase(code);
            /*
             * Test if SOMENAME, CODE is the case. a1.....a2.b1.., where b1 > a2
             * > a1, but distance is minimal from end of name to start of code.
             * If we see a standalone country code, we'll allow it to pass.
             */
            if ((code.start - name.end) > MAX_CHAR_DIST) {
                boolean canIgnoreCode = canIgnoreShortCode || ignoreNonAdminCode(code);
                // And so we have NAME xxxx CODE, where CODE is clearly not attached to NAME
                // lexically.
                // Filter out such CODE noise.
                if (canIgnoreCode) {
                    code.setFilteredOut(true);
                }
                if (canIgnoreName) {
                    name.setFilteredOut(true);
                }
                continue;
            }

            /*
             * Not supporting lowercase codes/abbreviations. 'la', 'is', 'un', etc.
             */
            if (canIgnoreShortCode) {
                continue;
            }

            boolean comma = false;
            if (name.postChar > 0) {
                comma = name.postChar == ',';
                if (!Character.isLetter(name.postChar)) {
                    // No match.
                    continue;
                }
            }
            /*
             * ignore the trivial case of where a Name may be repeated.
             * Ex.
             * New York, New York -- fine.
             * New York # New York -- ignore association.
             */
            if (!comma && name.isSameNorm(code)) {
                continue;
            }

            /*
             * by this point a place name tag should be marked as a name or
             * code/abbrev. Match the abbreviation with a geographic location
             * that is a state, county, district, etc.
             */
            Place country = code.isCountry ? code.getChosen() : null;
            log.debug("{} name, code: {} in {}?", NAME, name.getText(), code.getText());
            boolean logicalGeoMatchFound = false;
            boolean abbrev = possiblyAbbreviation(code);
            for (Place geo : code.getPlaces()) {
                if (logicalGeoMatchFound) {
                    break;
                }
                if (!geo.isUpperAdmin() || geo.getCountryCode() == null) {
                    continue;
                }

                // Provinces, states, districts, etc. Only.
                //
                // Make sure you can match an province name or code with the gazetteer entries
                // found:
                // Boston, Ma. ==== for 'Ma', resolve to an abbreviation for Massachusetts
                // Ignore places called 'Ma'
                //
                // Place ('Ma') == will have gazetteer metadata indicating if this is a valid
                // abbreviated code for a place.
                // PlaceCandidate('Ma.') will have textual metadata from given text indicating
                // if it is a code, MA, or abbrev. 'Ma.'
                //
                // These two situations must match here. We ignore geo locations that do not fit
                // this profile.
                //
                boolean lexicalMatch = ((abbrev && geo.isAbbreviation()) || (!abbrev && !geo.isAbbreviation()));
                //
                if (!lexicalMatch) {
                    continue;
                }

                String adm1 = geo.getHierarchicalPath();
                if (adm1 == null && !code.isCountry) {
                    log.debug("ADM1 hierarchical path should not be null");
                    continue;
                }

                // Quick determination if these two places have a containment or geopolitical
                // connection
                //
                boolean contains = name.presentInHierarchy(adm1);
                if (!contains && country != null) {
                    contains = name.presentInCountry(country.getCountryCode());
                }

                if (!contains) {
                    continue;
                }

                logicalGeoMatchFound = true;
                updateNameCodePair(name, code, geo, true /* comma */);
            }

            /*
             * Found "Good Docktor, MD" --> likely medical doctor (MD), not Maryland.
             * So if no geographic connection between NAME, CODE and CODE is an
             * abbreviation, then omit CODE.
             * If you actually have NAME, NAME, NAME, ... then you cannot omit subsequent
             * NAMEs.
             * With NAME, CODE -- if CODE is an abbreviation but represents a Country, then
             * let it pass,
             * as it is more common to see country names/GPEs abbreviated as personified
             * actors. Omit all other abbreviations, though.
             * with CODE CODE CODE ... you might have garbage text and would want to filter
             * out chains of abbreviations.
             * E.g., CO MA IN IA
             */
            if (abbrev) {
                if (!logicalGeoMatchFound && !code.isCountry) {
                    code.setFilteredOut(true);
                    /*
                     * NAME is actually an abbreviation and if it has no other evidence, then ignore
                     * this.
                     */
                    if (name.isAbbreviation && hasOnlyDefaultRules(name)) {
                        name.setFilteredOut(true);
                    }
                } else {
                    log.debug("Abbrev/code allowed: {}", code);
                }
            }
        }

        /*
         * Review items once more.
         */
        for (PlaceCandidate name : names) {
            if (name.isFilteredOut() || name.hasEvidence()) {
                continue;
            }
            if (ignoreMixedCaseAbbreviation(name)) {
                /*
                 * This is a short text span, no other evidence
                 * Possibly an abbreviation. Only valid CODEs attached to a NAME were already
                 * filtered out.
                 * If this is an admin code, it is unattached.
                 * OMIT "La", "Bo", "He", "Or" etc. as they matched things like
                 * LA -- Los Angeles or Louisiana
                 * OR -- Oregon,
                 * etc.
                 * If there is no qualifying or contextual information for such matches, they
                 * are usually noise.
                 */
                for (Place geo : name.getPlaces()) {
                    if (geo.isAbbreviation()) {
                        name.setFilteredOut(true);
                        break;
                    }
                }
            } else if ((!name.isCountry && name.isAbbreviation || name.isAcronym) && !name.isValid()) {
                // Filter out all trivial abbreviations not already associated with a city.
                // e.g., I went to see my MD
                //
                name.setFilteredOut(true);
            }

        }
    }

    private static boolean possiblyAbbreviation(final PlaceCandidate pc) {
        if (pc.isAbbreviation) {
            return pc.isAbbreviation;
        }
        // First determine if subsequent tokens indicate this is abbreviation or not.
        // Al. possible.
        // Al<sp> possible.
        // Al- NO.
        // Al= NO.
        if (pc.postChar != 0) {
            if (pc.postChar == '.') {
                return true;
            }
            if (!Character.isLetter(pc.postChar)) {
                return false;
            }
        }
        // Very limited scope assessment of "abbreviation" == 2 chars.
        if (pc.getLength() < 3) {
            return true;
        }

        return false;
    }

    /**
     * Experimental filters.
     * Filter out singular, known Admin boundary or other abbreviations. ... mainly
     * that are not
     * qualifying location names. Test examples include:
     * <ul>
     * <li>Co vs. CO vs. Colo.: Geraldine &amp; Co. or Geraldine, CO?</li>
     * <li>MD vs. Md. vs. ....: Maryland or Medical Doctor?</li>
     * </ul>
     *
     * @param pc
     * @return
     */
    private static boolean ignoreNonAdminCode(final PlaceCandidate pc) {
        // If found alone, unqualified what happens?
        // ------------------
        // ___ CO. ___ filter out
        // ___ Co ___ filter out
        // ___ Colo. ___ filter out
        // ___ COLORADO ___ pass.
        // ___ Al ____ filter out
        // ___ La ____ filter out
        // ___ LA ____ pass
        // ___ L.A. __ pass
        boolean abbrev = possiblyAbbreviation(pc);
        boolean matchFound = false;
        if (abbrev && pc.isCountry) {
            // Ignore standalone references to country codes for this filtration.
            return false;
        }
        for (Place geo : pc.getPlaces()) {
            boolean lexicalMatch1 = (abbrev && geo.isAbbreviation());
            if (geo.isAdministrative()) {
                if (lexicalMatch1) {
                    // Known abbreviation and both match text and geographic name appear to line up.
                    return true;
                }
            }
            if (abbrev && !geo.isAbbreviation()) {
                // Keep looking. given "CO", is "Cø." a match?
                continue;
            }

            // A valid location name
            matchFound = true;
            // Unsure if this is the end of this logic.
            break;
        }

        //
        return !matchFound;
    }

    /**
     * @param n
     * @param code
     * @param codeGeo
     * @param resolvedCountry
     * @param comma
     */
    private void updateNameCodePair(PlaceCandidate n, PlaceCandidate code, Place codeGeo, boolean comma) {
        /*
         * The code is matched with the name.
         */
        code.setFilteredOut(true);
        n.end = code.end;
        n.setTextOnly(String.format("%s%s%s", n.getText(), (comma ? ", " : " "), code.getText()));
        n.markValid();
        // code.markValid(); == tjhe code itself is now, no longer a valid tag.
        // downstream rules would promote this is it is marked valid, even if filtered
        // out.

        /*
         * CITY, STATE
         * CITY, COUNTRY
         */
        // Associate the CODE to the NAME that precedes it.
        //
        PlaceEvidence ev = new PlaceEvidence();
        ev.setCountryCode(codeGeo.getCountryCode());
        ev.setAdmin1(codeGeo.getAdmin1());
        ev.setEvaluated(true); // Shunt. Evaluate this rule here.
        n.markValid(); // and prevent downstream filters from filtering this out

        int wt = weight + (comma ? 2 : 0);
        if (codeGeo.isAbbreviation() && (code.isAbbreviation || code.isAcronym)) {
            ev.setRule(NAME_ADMCODE_RULE);
            ev.setWeight(wt + 1);
        } else {
            ev.setRule(NAME_ADMNAME_RULE);
            ev.setWeight(wt);
        }
        n.addEvidence(ev);

        if (boundaryObserver != null) {
            boundaryObserver.boundaryLevel1InScope(codeGeo);
        }

        /*
         * Example -- "Houston, TX"
         * adm1 = ("TX").adm1 "US.48", for example.
         * Loop through all locations for "Houston" and score the ones that match
         * "US.48" higher.
         */
        String adm1 = codeGeo.getHierarchicalPath();
        Place country = code.isCountry ? code.getChosen() : null;

        // Now choose which location for CITY (name) best suits this.
        // Actually increase score for all geos that match the criteria.
        //
        boolean matchFound = false;
        for (Place nameGeo : n.getPlaces()) {
            if (matchFound) {
                break;
            }
            if (!(nameGeo.isPopulated() || nameGeo.isAdministrative() || nameGeo.isSpot())) {
                continue;
            }
            if (adm1 != null && adm1.equals(nameGeo.getHierarchicalPath())) {
                n.incrementPlaceScore(nameGeo, ev.getWeight(), ev.getRule());
                matchFound = true;
            } else if (country != null && sameCountry(nameGeo, country)) {
                n.incrementPlaceScore(nameGeo, ev.getWeight(), ev.getRule());
                matchFound = true;
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
