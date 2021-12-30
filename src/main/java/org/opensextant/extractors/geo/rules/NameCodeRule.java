/**
 * Copyright 2014 The MITRE Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.util.GeonamesUtility;

import java.util.HashSet;
import java.util.List;

import static org.opensextant.extractors.geo.rules.RuleTool.hasOnlyDefaultRules;

/**
 * A rule that associates a CODE with a NAME, when the pattern
 * "NAME, CODE" appears within N characters of each other.
 * If CODE.adm1 == NAME.adm1 and CODE is an ADM1 boundary, then flag this is
 * significant.
 *
 * TODO: expand from pairs to 2-4 tuples of related geographic hierachy, e.g., City, State, Country, etc.
 * @author ubaldino
 */
public class NameCodeRule extends GeocodeRule {

    /**
     * Character distance between NAME CODE -- this is a simple distance
     * metric to avoid guessing or parsing what is between the NAME CODE or NAME
     * NAME
     * E.g., San Francisco, Uruguay - 2 char distance. Allow up to 3
     * E.g., San Francisco to Uruguay - 4 char distance.
     */
    private static final int MAX_CHAR_DIST = 5;

    public static final String NAME_ADMCODE_RULE = "AdminCode";
    public static final String NAME_ADMNAME_RULE = "AdminName";

    public NameCodeRule() {
        NAME = "AdminCodeOrName";
        weight = 10;
    }

    private static boolean ignoreShortLowercase(final PlaceCandidate pc) {
        return (pc.isLower() && pc.isAbbrevLength());
    }

    private static boolean ignoreShortMixedCase(final PlaceCandidate pc) {
        return (pc.isMixedCase() && pc.isAbbrevLength());
    }

    public static boolean isRuleFor(PlaceCandidate name) {
        return name.hasRule(NAME_ADMCODE_RULE) || name.hasRule(NameCodeRule.NAME_ADMNAME_RULE);
    }

    private final HashSet<String> ignoreTerms = new HashSet<>();

    @Override
    public void reset() {
        ignoreTerms.clear();
    }

    private void trackIgnoreTerms(PlaceCandidate nm) {
        ignoreTerms.add(nm.getText());
    }

    /**
     *
     */
    @Override
    public void evaluate(final List<PlaceCandidate> names) {
        reset();
        /* 
         * Objective:  associate NAME, CODE pairs
         * 
         * 
         */
        for (int x = 0; x < names.size(); ++x) {
            PlaceCandidate name = names.get(x);
            if (name.isFilteredOut()) {
                continue;
            }
            /* Optimization: */
            if (ignoreTerms.contains(name.getText())) {
                name.setFilteredOut(true);
                name.addRule("IgnoredPrecedent");
                continue;
            }
            // use # of instances of a place name as a rough guess on how common a name is.
            // A popular country name may appear as a city, county or other feature type.
            int placeCount = name.getPlaces().size();

            /*
             * COUNTRY, STATE is not supported under this rule.
             * E.g., Uruguay, Argentina ... This looks like a list of countries
             * However Uruguay is a district in Argentina; Just as Georgia is a state in US
             * and also a country name.  The count of 10 is arbitrary
             */
            if (name.isCountry && placeCount < 10 && !name.isAbbreviation) {
                continue;
            }

            remarkAbbreviation(name);

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

            boolean canIgnoreName = ignoreShortLowercase(name)
                    || (name.isAbbreviation && ignoreNonAdminCode(name));
            // Short names, lower case will not be assessed at all.
            if (canIgnoreName) {
                name.setFilteredOut(true);
                trackIgnoreTerms(name);
                continue;
            }
            // As well, given the pattern is supposed to be <This Name>, <Admin Code> ... if this item is last and
            // nothing follows we are done.
            if (isLast) {
                continue;
            }

            PlaceCandidate code = names.get(x + 1);
            remarkAbbreviation(code);

            /* code or name of admin area */
            if (code.isFilteredOut()) {
                continue;
            }
            /* Ignore series of country names and/or codes. */
            if (name.isCountry && code.isCountry) {
                continue;
            }

            boolean canIgnoreShortCode = ignoreShortLowercase(code);
            boolean abbrev = code.isAbbreviation;

            /*
             * Test if SOMENAME, CODE is the case. a1.....a2.b1.., where b1 > a2
             * > a1, but distance is minimal from end of name to start of code.
             * If we see a standalone country code, we'll allow it to pass.
             */
            if ((code.start - name.end) > MAX_CHAR_DIST) {
                boolean canIgnoreCode = canIgnoreShortCode || (abbrev && ignoreNonAdminCode(code));
                // And so we have NAME xxxx CODE, where CODE is clearly not attached to NAME  lexically.
                // Filter out such CODE noise.
                if (canIgnoreCode) {
                    code.setFilteredOut(true);
                    trackIgnoreTerms(code);
                }
                if (canIgnoreName) {
                    name.setFilteredOut(true);
                    trackIgnoreTerms(name);
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
                // Trying to match valid "NAME, CODE" or "NAME CODE" sequence.
                // Other stuff interferring should be ignored.
                comma = name.postChar == ',' || name.postChar == ' ';
                if (!comma && !Character.isLetter(name.postChar)) {
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
            Place country = code.isCountry ? code.getChosenPlace() : null;
            log.debug("{} name, code: {} in {}?", NAME, name.getText(), code.getText());
            int logicalGeoMatchCount = 0;
            for (ScoredPlace geoScore : code.getPlaces()) {
                if (logicalGeoMatchCount > 4) {
                    /* Optimization: avoid spinning in loop. 
                     * 4 hierachical matches *seems* sufficient */
                    break;
                }
                Place geo = geoScore.getPlace();
                if (!(geo.isUpperAdmin() || GeonamesUtility.isPoliticalEntity(geo))
                        || geo.getCountryCode() == null) {
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
                // NAME CODE
                // A a
                // a A
                // A A
                // a a
                // Aaa Aaa
                // Aaa AA
                // ... etc.
                boolean lexicalMatch = abbrev && geo.isShortName() || (!abbrev && geo.isName());
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
                // -- If country was determined from code earlier, use it.
                // -- Otherwise check if codeGeo country code aligns with name Geo.
                boolean containsRelation = name.presentInHierarchy(adm1);
                boolean inCountryRelation = (code.isCountry
                        && name.presentInCountry(geo.getCountryCode()))
                        || (country != null && name.presentInCountry(country.getCountryCode()));

                if (containsRelation || inCountryRelation) {
                    ++logicalGeoMatchCount;
                    updateNameCodePair(name, code, geo, true /* comma */, containsRelation);
                }
            }

            /*  Post-process abbreviations not associated.
             *
             * Found "Good Docktor, MD" --> likely medical doctor (MD), not Maryland.
             * So if no geographic connection between NAME, CODE and CODE is an
             * abbreviation, then omit CODE.
             * If you actually have NAME, NAME, NAME, ... then you cannot omit subsequent NAMEs.
             * With NAME, CODE -- if CODE is an abbreviation but represents a Country, then
             * let it pass, as it is more common to see country names/GPEs abbreviated as personified
             * actors. Omit all other abbreviations, though.
             * with CODE CODE CODE ... you might have garbage text and would want to filter
             * out chains of abbreviations. E.g., CO MA IN IA
             */
            if (abbrev) {
                if (logicalGeoMatchCount == 0 && !code.isCountry) {
                    code.setFilteredOut(true);
                    trackIgnoreTerms(code);
                    /*
                     * NAME is actually an abbreviation and if it has no other evidence, then ignore this.
                     */
                    if (name.isAbbreviation && hasOnlyDefaultRules(name)) {
                        name.setFilteredOut(true);
                        trackIgnoreTerms(name);
                    }
                } else {
                    log.debug("Abbrev/code allowed: {}", code);
                }
            }
        }

        /*
         * Review items once more.  Note -- no need to track ignored terms from here.
         */
        for (PlaceCandidate name : names) {
            if (name.isFilteredOut() || name.hasEvidence()) {
                continue;
            }
            if (!name.isValid() && name.getLength() <= 2) {
                // junk.  Un-associated tokens that matched something.
                name.setFilteredOut(true);
                continue;
            }

            if (ignoreShortMixedCase(name)) {
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
                for (ScoredPlace geo : name.getPlaces()) {
                    if (geo.getPlace().isShortName()) {
                        name.setFilteredOut(true);
                        break;
                    }
                }
            } else if ((!name.isCountry && !name.isValid() && name.isShortName())
                    && isShort(name.getLength())) {

                /* Last Chance:
                 * - Save a handful of city acronyms, e.g., NYC, BSAS, etc.
                 * - Avoid rechecking items that have already been validated as NAME+CODE pairs (isValid()==true)
                 * - If Not Country, but is ABBREV, then omit all trivial abbreviations not already associated with a city.
                 *   e.g., "I went to see my MD"
                 * 
                 */
                name.setFilteredOut(true);
                for (ScoredPlace geoScore : name.getPlaces()) {
                    Place geo = geoScore.getPlace();
                    if (geo.isPopulated() && geo.isShortName()) {
                        name.setFilteredOut(false);
                        name.incrementPlaceScore(geo, 1.0, "CityNickName");
                        break;
                    }
                }
            }
        }
    }

    private void remarkAbbreviation(PlaceCandidate pc) {
        if (pc.isAbbreviation) {
            /* Find evidence that this match is an abbreviation. */
            boolean matchFound = false;
            for (ScoredPlace geo : pc.getPlaces()) {
                if (geo.getPlace().isShortName()) {
                    matchFound = true;
                    break;
                }
            }
            pc.isAbbreviation = pc.isAcronym = matchFound;
        }
    }

    /**
     * Experimental filters.
     * Filter out singular, known Admin boundary or other abbreviations. ... mainly
     * that are not qualifying location names. Test examples include:
     * <ul>
     * <li>Co vs. CO vs. Colo.: Geraldine &amp; Co. or Geraldine, CO?</li>
     * <li>MD vs. Md. vs. ....: Maryland or Medical Doctor?</li>
     * </ul>
     *
     * @param pc match
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
        if (pc.isCountry) {
            // Ignore standalone references to country codes for this filtration.
            return false;
        }
        for (ScoredPlace geoScore : pc.getPlaces()) {
            Place geo = geoScore.getPlace();
            if (!geo.isShortName()) {
                // Keep looking. given "CO", is "Cø." a match?
                continue;
            }
            // ALLOW administrative codes
            if (geo.isAdministrative()) {
                return false;
            }
            // DISALLOW other random codes and abbrevs, i.e. those for a water tower or bus station.
            // ALLOW such codes and abbrevs for cities, though.
            if (!geo.isPopulated()) {
                return true;
            }
        }

        // No reason to filter out this name.
        return false;
    }

    /**
     * Update the scores for any locations for the relevant Names + Codes. 
     * The given codeGeo is scored against the code placename
     * Reflect this location inference back onto the Name, scoring any possible features higher.
     * 
     * @param n  placename 
     * @param code administrative placename related to `n`
     * @param codeGeo candidate location for `code`
     * @param comma
     * @param closeAssociation - true if inferred Name/Code geography is a close containment relation
     */
    private void updateNameCodePair(PlaceCandidate n, PlaceCandidate code, Place codeGeo,
            boolean comma, boolean closeAssociation) {
        /*
         * The code is matched with the name.  Mark these paired items as valid to avoid further filtering
         */
        n.addRelated(code);
        code.markValid();
        n.markValid();

        /*
         * CITY, STATE        -- weighted higher.   Shared hierarchy CC.xx
         * COUNTY, STATE      -- weighted higher.   Shared hierarchy CC.xx
         * CITY, COUNTRY      -- normal weight.     Shared hierarchy CC only.
         */
        // Associate the CODE to the NAME that precedes it.
        //
        PlaceEvidence ev = new PlaceEvidence();
        ev.setCountryCode(codeGeo.getCountryCode());
        ev.setAdmin1(codeGeo.getAdmin1());
        double wt = weight + (comma ? 2.0 : 0.0) + (closeAssociation ? 4.0 : 0.0);
        String rl = codeGeo.isShortName() && code.isShortName() ? NAME_ADMCODE_RULE
                : NAME_ADMNAME_RULE;
        ev.setRule(rl);
        ev.setWeight(wt);
        ev.setEvaluated(true); // Shunt. Evaluate this rule here; We'll increment the location score discretely.
        n.addEvidence(ev);
        code.addEvidence(ev);
        code.incrementPlaceScore(codeGeo, wt, rl);

        if (boundaryObserver != null) {
            boundaryObserver.boundaryLevel1InScope(code.getNDTextnorm(), codeGeo);
        }

        /*
         * Example -- "Houston, TX" adm1 = ("TX").adm1 "US.48", for example.
         * Loop through all locations for "Houston" and score higher the ones that match "US.48".
         */
        String adm1 = codeGeo.getHierarchicalPath();
        Place country = code.isCountry ? code.getChosenPlace() : null;

        // Now choose which location for CITY (name) best suits this.
        // Actually increase score for all geos that match the criteria.
        //
        boolean matchFound = false;
        for (ScoredPlace nameGeoScore : n.getPlaces()) {
            if (matchFound) {
                break;
            }
            Place nameGeo = nameGeoScore.getPlace();
            if (nameGeo.isSame(codeGeo)) {
                continue; /* Ignore choosing same location for repeated names */
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

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /* no-op*/ }
}
