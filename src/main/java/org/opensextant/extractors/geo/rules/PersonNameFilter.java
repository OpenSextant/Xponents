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

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.solr.common.util.Hash;
import org.opensextant.ConfigException;
import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.util.FileUtility;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class PersonNameFilter extends GeocodeRule {

    private final Set<String> nameFilter;
    private final Set<String> titles;
    private final Set<String> suffixes;

    private final static HashSet<String> FEAT_CODE_ORG_LOCATIONS = new HashSet<>();

    static {
        // ORGANIZATIONS that also have location or "SITEs", e.g., feat_class=S, feat_code is below.
        // We want to acknowledge location over the fact its an organization.
        // We can do both, but filter approach here is to filter out location that is an org.
        // This feature coding prevents such organizations from not being geolocated.

        // Hospital, Clinic
        FEAT_CODE_ORG_LOCATIONS.add("HSP");
        FEAT_CODE_ORG_LOCATIONS.add("HSPC");
        FEAT_CODE_ORG_LOCATIONS.add("HSP");

        // Buildings, Municipal buildings, etc.
        FEAT_CODE_ORG_LOCATIONS.add("BLDG");
        FEAT_CODE_ORG_LOCATIONS.add("BLDA");
        FEAT_CODE_ORG_LOCATIONS.add("BLDO");

        // Schools, Colleges, Universities
        FEAT_CODE_ORG_LOCATIONS.add("SCH");
        FEAT_CODE_ORG_LOCATIONS.add("SCHC");
        FEAT_CODE_ORG_LOCATIONS.add("SCHT");
        FEAT_CODE_ORG_LOCATIONS.add("UNIV");

        // Places of worship and religious activity
        FEAT_CODE_ORG_LOCATIONS.add("MSQE");
        FEAT_CODE_ORG_LOCATIONS.add("SHRN");
        FEAT_CODE_ORG_LOCATIONS.add("TMPL");
        FEAT_CODE_ORG_LOCATIONS.add("CH");
        FEAT_CODE_ORG_LOCATIONS.add("MSSN");
    }

    /**
     * Locations that are some number of words long AND have lat/lon
     * should be allowed to pass as geocodings even when they overlap with
     * organizational names.
     */
    private static final int LONG_NAME_LEN = 3 * AVG_WORD_LEN;

    /**
     * Constructor for general usage if you know your files might come from file
     * system or JAR.
     *
     * @param names
     * @param persTitles
     * @param persSuffixes
     * @throws ConfigException when filter files are missing.
     */
    public PersonNameFilter(URL names, URL persTitles, URL persSuffixes) throws ConfigException {
        try {
            nameFilter = FileUtility.loadDictionary(names, false);
            titles = FileUtility.loadDictionary(persTitles, false);
            suffixes = FileUtility.loadDictionary(persSuffixes, false);
            debug();
        } catch (IOException filterErr) {
            throw new ConfigException("Default filter not found", filterErr);
        }
    }

    private void debug() {
        if (log.isDebugEnabled()) {
            log.debug("NAME FILTER\n\t{}", nameFilter);
            log.debug("TITLE FILTER\n\t{}", titles);
            log.debug("SUFFIX FILTER\n\t{}", suffixes);
        }
    }

    /**
     * Default constructor here used resource paths (which are retrieved as
     * getResourceAsStream() Instead of retrieving resource URLs or files. This
     * works best if you know your resource files will come from JAR only.
     *
     * @param namesPath
     * @param persTitlesPath
     * @param persSuffixesPath
     * @throws ConfigException when filter files are missing
     */
    public PersonNameFilter(String namesPath, String persTitlesPath, String persSuffixesPath) throws ConfigException {
        try {
            nameFilter = FileUtility.loadDictionary(namesPath, false);
            titles = FileUtility.loadDictionary(persTitlesPath, false);
            suffixes = FileUtility.loadDictionary(persSuffixesPath, false);
            debug();
        } catch (IOException filterErr) {
            throw new ConfigException("Default filter not found", filterErr);
        }
    }

    private final Map<String, String> resolvedPersons = new HashMap<>();
    private final Map<String, String> resolvedOrgs = new HashMap<>();
    private final Map<String, String> protectedLocations = new HashMap<>();

    private static final Pattern delPeriod = Pattern.compile("\\.+$");

    /** Delete ending "."or "..." */
    private static String withoutPeriod(String s) {
        if (s.endsWith(".")) {
            return delPeriod.matcher(s).replaceAll("");
        }
        return s;
    }

    @Override
    public void reset() {
        resolvedPersons.clear();
        resolvedOrgs.clear();
        protectedLocations.clear();
    }

    public Map<String, String> getPersonNames() {
        return resolvedPersons;
    }

    public Map<String, String> getOrgNames() {
        return resolvedOrgs;
    }

    /**
     * Simple check for a span of text to see if it is purely whitespace or not at
     * the given offsets, [x1..x2}
     * Include left side, not right side-character.
     *
     * @param buf buf to splice
     * @param x1  offset to start at
     * @param x2  offset to stop at.
     * @return
     */
    private static boolean hasNonWhitespace(final String buf, int x1, int x2) {
        for (int x = x1; x < x2; ++x) {
            if (!Character.isWhitespace(buf.charAt(x))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Use known person names to distinguish well-known persons that may or may
     * not overlap in in the text and the namespace.
     *
     * <pre>
     *  Hillary Clinton visited New York state today.
     * </pre>
     * <p>
     * So, Clinton is part of a well known celebrity, and is not referring to
     * Clinton, NY a town in upstate. We identify all such person names and mark
     * any overlaps and co-references that coincide with tagged place names.
     *
     * @param placeNames places to negate
     * @param persons    named persons in doc
     * @param orgs       named orgs in doc
     */
    public void evaluateNamedEntities(final TextInput input, final List<PlaceCandidate> placeNames,
                                      final List<TaxonMatch> persons, final List<TaxonMatch> orgs, final List<TaxonMatch> others) {

        for (PlaceCandidate pc : placeNames) {
            if (pc.isFilteredOut() || pc.isValid() || (pc.isCountry && !pc.isAbbreviation)) {
                // TODO: document examples for this clause
                continue;
            }

            // We already resolved this first or last name to a known
            // person/celebrity
            if (resolvedPersons.containsKey(pc.getTextnorm())) {
                pc.setFilteredOut(true);
                pc.addRule("ResolvedPerson");
                continue;
            }
            if (resolvedOrgs.containsKey(pc.getTextnorm())) {
                pc.setFilteredOut(true);
                pc.addRule("ResolvedOrg");
                continue;
            }

            if (evaluatePersonName(pc, persons, input.buffer)) {
                continue;
            }

            /*
             * Ignore terms like Boston City Hall if that is marked as both Org and Location
             * Let location pass as-is.
             */
            if (pc.getLength() > LONG_NAME_LEN) {
                continue;
            }

            if (evaluateOrgName(pc, orgs)) {
                continue;
            }

            if (!pc.isCountry) {
                evaluateOtherName(pc, others);
            }
        }
    }

    private boolean evaluateOtherName(PlaceCandidate pc, List<TaxonMatch> matches) {
        for (TaxonMatch name : matches) {
            if (pc.isSameMatch(name)) {
                pc.setFilteredOut(true);
                pc.isCountry = false;
                pc.addRule("Matches.Taxon");
                return true;
            } else if (pc.isWithin(name)) {
                pc.addRule(NAME_IN_ORG_RULE);
            } else if (name.isWithin(pc)) {
                name.setFilteredOut(true);
                pc.addRule("Contains.Taxon");
                return true;
            }
        }
        return false;
    }

    /**
     * Detect if a name is a site (that might also be an Org).  Detroit City Hall ( A building and an org)
     * @param pc
     * @return true if named location represents particular site features
     */
    private boolean isSiteLocation(PlaceCandidate pc) {
        /*
         * Shunt:  Look up feature by name.  But as names may be spelled differently
         *  we also key known feature types by their place ID or key
         */
        if (protectedLocations.containsKey(pc.getTextnorm())) {
            return true;
        }
        for (ScoredPlace pl : pc.getPlaces()) {
            Place feature = pl.getPlace();
            if (protectedLocations.containsKey(feature.getKey())) {
                return true;
            }

            // If feature type is known to represent both ORG AND SITE location, return true.
            if (FEAT_CODE_ORG_LOCATIONS.contains(feature.getFeatureCode())) {
                protectedLocations.put(feature.getKey(), feature.getFeatureCode());
                protectedLocations.put(pc.getTextnorm(), feature.getFeatureCode());
                return true;
            }
        }
        return false;
    }

    private boolean evaluateOrgName(PlaceCandidate pc, List<TaxonMatch> orgs) {

        if (isSiteLocation(pc)) {
            // Because this location also represents a taxon that is an ORG,
            // mark the mention valid and it will avoid being filtered out
            pc.addRule("LocationForOrg");
            pc.markValid();
            return true;
        }

        /* is LOC candidate in ORG name
         * or ORG name in LOC candidate?
         */
        for (TaxonMatch name : orgs) {
            if (pc.isSameMatch(name)) {
                pc.setFilteredOut(true);
                pc.isCountry = false;
                resolvedOrgs.put(pc.getTextnorm(), name.getText());
                pc.addRule("ResolvedOrg");
                return true;
            } else if (pc.isWithin(name) && !pc.isCountry) {
                // LOC: "Memorial Square" in ORG: "Friends of Memorial Square"

                // Special conditions:
                // City name in the name of a Building or Landmark is worth saving as a location.
                // But short one-word names appearing in organization names, may be false positives
                // After more evaluation, it seems like presence of a city name in an organization name
                // is good evidence to leverage, so do not claim the location name is a resolved org name.
                //
                pc.addRule(NAME_IN_ORG_RULE);
            } else if (name.isWithin(pc)) {
                name.setFilteredOut(true);
                pc.addRule("Contains.OrgName");
                return true;
            }
        }
        return false;
    }

    private boolean evaluatePersonName(PlaceCandidate pc, List<TaxonMatch> persons, String text) {
        for (TaxonMatch name : persons) {
            String rule = null;
            // Case: LOC in NAME
            // LOC: "Murtagh" in PERSON: "General Murtagh"
            if (pc.isSameNorm(name) || pc.isWithin(name)) {
                rule = "ResolvedPerson";
            } else if (pc.isBefore(name) && pc.getWordCount() == 1 && !pc.isCountry) {
                if (hasNonWhitespace(text, pc.end, name.start)) {
                    continue;
                }
                rule = "ResolvedPerson.PreceedingName";
            } else if (pc.isAfter(name)) {
                if (hasNonWhitespace(text, name.end, pc.start)) {
                    continue;
                }
                rule = "ResolvedPerson.SucceedingName";
            } else if (name.isWithin(pc)) {
                // Filter out PERSON name, let PLACE pass.
                // Ignore person names that are sub-matches
                // NAME: Murtagh in LOC: "General Murtagh Memorial Square"
                pc.addRule("Contains.PersonName");
                name.setFilteredOut(true);
            }

            if (rule != null) {
                pc.setFilteredOut(true);
                resolvedPersons.put(pc.getTextnorm(), name.getText());
                pc.addRule(rule);
                return true;
            }
        }
        return false;
    }

    /**
     * Rule fired if a location is found in an organization name; Only
     * organization should be filtered out.
     */
    public static final String NAME_IN_ORG_RULE = "NameInOrg";


    private boolean evaluateValidNames(PlaceCandidate name) {

        if (name.isCountry) {
            return true;
        }

        /*
         * If you have already associated an Admin code with this name, then do
         * not filter out Eugene, OR Jackson, MI
         */
        else if (NameCodeRule.isRuleFor(name)) {
            name.setFilteredOut(false);
            return true;
        } else if (MajorPlaceRule.isRuleFor(name)) {
            name.setFilteredOut(false);
            return true;
        }
        return false;

    }

    private boolean isResolvedName(PlaceCandidate name) {
        /*
         * Name matches not yet filtered out, but may be co-referrenced to prior
         * mention
         */
        if (resolvedPersons.containsKey(name.getTextnorm())) {
            name.setFilteredOut(true);
            name.addRule("ResolvedPerson.CoRef");
            return true;
        } else if (resolvedOrgs.containsKey(name.getTextnorm())) {
            name.setFilteredOut(true);
            name.addRule("ResolvedOrg.CoRef");
            return true;
        }
        return false;
    }

    private boolean isPersonName(PlaceCandidate name) {
        if (nameFilter.contains(name.getTextnorm())) {
            name.setFilteredOut(true);
            resolvedPersons.put(name.getTextnorm(), name.getText());
            name.addRule("PersonName");
            return true;
        }
        return false;
    }

    /**
     * Evaluate the place name purely based on previous rules or the lexical nature
     * of the name, and not any geography, so this parent method is overriden and returns
     * True always.  That shunts the geo evaluation -- So, yes it always returns true.
     */
    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            // Conditional here is to find just one hueristic that works successfully.
            boolean test = evaluateValidNames(name)
                    || isResolvedName(name)
                    || hasPreHonorific(name)
                    || isPersonName(name)
                    || hasPostHonorific(name);
        }
    }

    /**
     * Test if a name has a trailing honorific or title.
     *
     * @param nm
     * @return
     */
    private boolean hasPostHonorific(PlaceCandidate nm) {
        String[] toks = nm.getPostmatchTokens();
        if (toks == null || toks.length == 0) {
            return false;
        }
        String post = toks[0].toLowerCase();
        if (suffixes.contains(withoutPeriod(post))) {
            nm.setFilteredOut(true);
            resolvedPersons.put(val(nm.getTextnorm(), post), nm.getText());
            nm.addRule("PersonSuffix");
            return true;
        }
        return false;
    }

    /**
     * Test if name has preceeding honorific
     *
     * @param nm
     * @return
     */
    private boolean hasPreHonorific(PlaceCandidate nm) {
        String[] toks = nm.getPrematchTokens();
        if (toks == null || toks.length == 0) {
            return false;
        }
        String pre = toks[toks.length - 1].toLowerCase();
        if (isNotBlank(pre)) {
            if (titles.contains(withoutPeriod(pre))) {
                nm.setFilteredOut(true);
                resolvedPersons.put(val(pre, nm.getTextnorm()), nm.getText());
                nm.addRule("PersonTitle");
                nm.addRule("Prefix=" + pre);
                return true;
            } else if (nameFilter.contains(pre)) {
                nm.setFilteredOut(true);
                resolvedPersons.put(nm.getTextnorm(), String.format("%s %s", pre, nm.getTextnorm()));
                nm.addRule("PersonName");
                nm.addRule("Prefix=" + pre);
                return true;
            }
        }
        return false;
    }

    @Override
    public void evaluate(final PlaceCandidate name, final Place geo) {
        /* No Op */
    }

    /**
     * Debug support -- formatted value here helps convey the name + title or
     * suffix
     *
     * @param nm1
     * @param nm2
     * @return
     */
    private String val(String nm1, String nm2) {
        return String.format("%s/%s", nm1, nm2);
    }
}
