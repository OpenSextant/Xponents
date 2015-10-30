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

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.opensextant.ConfigException;
import org.opensextant.data.Place;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.util.FileUtility;

public class PersonNameFilter extends GeocodeRule {

    private MatchFilter filter = null;
    private Set<String> titles = null;
    private Set<String> suffixes = null;

    public PersonNameFilter(URL names, URL persTitles, URL persSuffixes) throws ConfigException {
        try {
            filter = new MatchFilter(names);
            titles = FileUtility.loadDictionary(persTitles, false);
            suffixes = FileUtility.loadDictionary(persSuffixes, false);
        } catch (IOException filterErr) {
            throw new ConfigException("Default filter not found", filterErr);
        }
    }

    private Map<String, String> resolvedPersons = new HashMap<>();
    private Map<String, String> resolvedOrgs = new HashMap<>();
    private static final Pattern delPeriod = Pattern.compile("\\.+$");

    /** Delete ending "."or "..." */
    private final static String withoutPeriod(String s) {
        if (s.endsWith(".")) {
            return delPeriod.matcher(s).replaceAll("");
        }
        return s;
    }

    @Override
    public void reset() {
        resolvedPersons.clear();
        resolvedOrgs.clear();
    }

    public Map<String, String> getPersonNames() {
        return resolvedPersons;
    }

    public Map<String, String> getOrgNames() {
        return resolvedOrgs;
    }

    /**
     * Use known person names to distinguish well-known persons that may or may
     * not overlap in in the text and the namespace.
     * 
     * <pre>
     * Hillary Clinton visited New York state today.
     * </pre>
     * 
     * So, Clinton is part of a well known celebrity, and is not referring to
     * Clinton, NY a town in upstate. We identify all such person names and mark
     * any overlaps and co-references that coincide with tagged place names.
     * 
     * @param placeNames
     *            places to NEgate
     * @param persons
     *            named persons in doc
     * @param orgs
     *            named orgs in doc
     */
    public void evaluateNamedEntities(List<PlaceCandidate> placeNames, List<TaxonMatch> persons,
            List<TaxonMatch> orgs) {

        for (PlaceCandidate pc : placeNames) {
            if (pc.isFilteredOut()) {
                continue;
            }

            // We already resolved this first or last name to a known
            // person/celebrity
            if (resolvedPersons.containsKey(pc.getTextnorm()) || resolvedOrgs.containsKey(pc.getTextnorm())) {
                pc.setFilteredOut(true);
                filterEvidence(pc, "ResolvedPerson", PlaceEvidence.Scope.COREF);
                continue;
            }

            for (TaxonMatch name : persons) {
                // "General Murtagh" PLACE=murtagh within PERSON (not a valid
                // place name)
                // "General Murtagh Memorial Square" PERSON within PLACE (valid
                // place name)
                if (pc.isWithin(name)) {
                    pc.setFilteredOut(true);
                    resolvedPersons.put(pc.getTextnorm(), name.getText());
                    filterEvidence(pc, "ResolvedPerson", PlaceEvidence.Scope.DOCUMENT);
                }
            }
            for (TaxonMatch name : orgs) {
                if (pc.isSameMatch(name)) {
                    pc.setFilteredOut(true);
                    resolvedOrgs.put(pc.getTextnorm(), name.getText());
                    filterEvidence(pc, "ResolvedOrg", PlaceEvidence.Scope.DOCUMENT);
                }
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /*
         * No other existing evidence that we should keep this entry and if the
         * name is a person name --- AS DEFINED BY THE USER -- then we mark it
         * filtered out.
         *
         */
        if (name.getChosen() != null) {
            return;
        }

        /*
         * This was filtered out already so ignore.
         */
        if (name.isFilteredOut()) {
            return;
        }

        /*
         * If you have already associated an Admin code with this name, then do
         * not filter out
         *
         * Eugene, OR Jackson, MI
         *
         * TODO: Euguene, Oregon etc.
         */
        if (name.hasRule(NameCodeRule.NAMECODE_RULE)) {
            return;
        }

        /**
         * Name matches not yet filtered out, but may be co-referrenced to prior
         * mention
         * 
         */
        if (resolvedPersons.containsKey(name.getTextnorm())) {
            name.setFilteredOut(true);
            filterEvidence(name, "ResolvedPerson", PlaceEvidence.Scope.COREF);
            return;
        }
        if (resolvedOrgs.containsKey(name.getTextnorm())) {
            name.setFilteredOut(true);
            filterEvidence(name, "ResolvedOrg", PlaceEvidence.Scope.COREF);
            return;
        }

        String[] toks = name.getPrematchTokens();
        if (toks != null && toks.length > 0) {
            String pre = toks[toks.length - 1].toLowerCase();
            // pre = delPeriod.matcher(pre).replaceAll("");
            if (titles.contains(withoutPeriod(pre))) {
                name.setFilteredOut(true);
                resolvedPersons.put(val(pre, name.getTextnorm()), name.getText());
                filterEvidence(name, "PersonTitle", PlaceEvidence.Scope.DOCUMENT);
                return;
            }
        }

        if (filter.filterOut(name.getTextnorm())) {
            name.setFilteredOut(true);
            resolvedPersons.put(name.getTextnorm(), name.getText());
            filterEvidence(name, "PersonName", PlaceEvidence.Scope.DOCUMENT);
            return;
        }

        toks = name.getPostmatchTokens();
        if (toks != null && toks.length > 0) {
            String post = toks[0].toLowerCase();
            if (suffixes.contains(withoutPeriod(post))) {
                name.setFilteredOut(true);
                resolvedPersons.put(val(name.getTextnorm(), post), name.getText());
                filterEvidence(name, "PersonSuffix", PlaceEvidence.Scope.DOCUMENT);
                return;
            }
        }
    }

    /**
     * Simple wrapper around filter rules. Later this could be just using rule
     * string IDs, if the full weight/scope/etc. concepts are not used.
     * 
     * @param match
     * @param rule
     * @param scope
     */
    private void filterEvidence(PlaceCandidate match, String rule, PlaceEvidence.Scope scope) {
        PlaceEvidence notPlace = new PlaceEvidence();
        notPlace.setRule(rule);
        notPlace.setScope(scope);
        notPlace.setWeight(-1.0);
        match.addEvidence(notPlace);
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
