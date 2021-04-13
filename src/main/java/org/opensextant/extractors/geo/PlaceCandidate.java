/*
 *
 * Copyright 2012-2019 The MITRE Corporation.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.opensextant.data.Geocoding;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;

/**
 * A PlaceCandidate represents a portion of a document which has been identified
 * as a possible named geographic location. It is used to collect together the
 * information from the document (the evidence), as well as the possible
 * geographic locations it could represent (the Places ). It also contains the
 * results of the final decision to include:
 * <ul>
 * <li>bestPlace - Of all the places with the same/similar names, which place is
 * it?
 * </ul>
 *
 * @author ubaldino
 * @author dlutz, based on OpenSextant Toolbox
 */
public class PlaceCandidate extends TextMatch {

    // --------------Place/NotPlace stuff ----------------------
    // which rules have expressed a Place/NotPlace opinion on this PC
    private final Set<String> rules = new HashSet<>();
    // --------------Disambiguation stuff ----------------------
    // the places along with their disambiguation scores
    private final Map<String, ScoredPlace> scoredPlaces = new HashMap<>();
    // the list of PlaceEvidences accumulated from the document about this PC
    private final HashMap<String, PlaceEvidence> evidence = new HashMap<>();
    // The chosen, best place:
    private ScoredPlace choice1 = null;
    private ScoredPlace choice2 = null;
    private int confidence = 0;
    private Set<String> hierarchicalPaths = new HashSet<>();
    private Set<String> countries = new HashSet<>();
    private boolean markedValid = false;

    /**
     * Default weighting increments.
     */
    private static final String[] CLASS_SCALE = { "A:3", "P:2", "L:1", "R:0", "H:1", "V:0", "T:1" };

    private static final String[] DESIGNATION_SCALE = {
            /* Places: cities, villages, ruins, etc. */
            "PPLC:12", "PPLA:8", "PPLG:7", "PPL:5", "PPLL:2", "PPLQ:2", "PPLX:2",
            /* Administrative regions */
            "ADM1:9", "ADM2:8", "ADM3:7",
            /* Other geographic features */
            "ISL:4", "ISLS:5" };

    private static final Map<String, Integer> classWeight = new HashMap<>();
    private static final Map<String, Integer> designationWeight = new HashMap<>();
    private static final int DEFAULT_DESIGNATION_WT = 2;

    static {
        for (String entry : DESIGNATION_SCALE) {
            String[] parts = entry.split(":");
            designationWeight.put(parts[0], Integer.parseInt(parts[1]));
        }
        for (String entry : CLASS_SCALE) {
            String[] parts = entry.split(":");
            classWeight.put(parts[0], Integer.parseInt(parts[1]));
        }
    }

    /**
     *
     */
    public PlaceCandidate() {
    }

    /**
     * Using a scale of 0 to 100, indicate how confident we are that the chosen
     * place is best.
     * Note this is different than the individual score assigned to each candidate
     * place.
     * We just need one final confidence measure for this place mention.
     *
     * @param c
     */
    public void setConfidence(int c) {
        confidence = c;
    }

    /**
     * see setConfidence.
     *
     * @return confidence
     */
    public int getConfidence() {
        return confidence;
    }

    /**
     * If caller is willing to claim an explicit choice, so be it. Otherwise
     * unchosen places go to disambiguation.
     *
     * @param geo
     */
    public void choose(Place geo) {
        if (geo instanceof ScoredPlace) {
            choice1 = (ScoredPlace) geo;
        } else {
            String k = makeKey(geo);
            if (scoredPlaces.containsKey(k)) {
                choice1 = scoredPlaces.get(k);
            }
        }
    }

    // ---- the getters and setters ---------
    //

    private String[] preTokens = null;
    private String[] postTokens = null;
    private String[] tokens = null;
    private final int DEFAULT_TOKEN_SIZE = 40;

    /**
     * Get some sense of tokens surrounding match. Possibly optimize this by
     * getting token list from SolrTextTagger (which provides the
     * lang-specifics)
     *
     * @param sourceBuffer
     */
    protected void setSurroundingTokens(String sourceBuffer) {
        int[] window = TextUtils.get_text_window(start, end - start, sourceBuffer.length(), DEFAULT_TOKEN_SIZE);

        /*
         * Get right most or left most whole tokens, for now whitespace
         * delimited. TODO: ensure whole tokens are retrieved.
         */
        setPrematchTokens(TextUtils.tokensRight(sourceBuffer.substring(window[0], window[1])));
        setPostmatchTokens(TextUtils.tokensLeft(sourceBuffer.substring(window[2], window[3])));

        if (window[1] != 0) {
            preChar = sourceBuffer.charAt(window[1]); /* offset greater than 0 */
        }
        if (window[2] != sourceBuffer.length()) {
            preChar = sourceBuffer.charAt(window[2]);/* offset less than doc end */
        }
    }

    /**
     * Common evidence flags -- isCountry, isPerson, isOrganization,
     * abbreviation, and acronym.
     */
    public boolean isCountry = false;

    /**
     *
     */
    public boolean isContinent = false;

    /**
     *
     */
    public boolean isPerson = false;

    /**
     *
     */
    public boolean isOrganization = false;

    /**
     *
     */
    public boolean isAbbreviation = false;

    /**
     *
     */
    public boolean isAcronym = false;

    /**
     *
     */
    public boolean hasDiacritics = false;

    /**
     * After candidate has been scored and all, the final best place is the
     * geocoding result for the given name in context.
     *
     * @return the chosen geocoding
     */
    public Geocoding getGeocoding() {
        choose();
        return getChosen();
    }

    /**
     * @return
     */
    public ScoredPlace getChosen() {
        return choice1;
    }

    /**
     * @return
     */
    public ScoredPlace getFirstChoice() {
        return getChosen();
    }

    /**
     * Get the most highly ranked Place, or Null if empty list.
     * Typical usage:
     * choose() // this does work. performance cost.
     * getChosen() // this is a getter; no performance cost
     */
    public void choose() {
        if (choice1 != null) {
            // return chosen;
            return;
        }
        if (scoredPlaces.isEmpty()) {
            return;
        }

        List<ScoredPlace> tmp = new ArrayList<>();
        tmp.addAll(scoredPlaces.values());
        Collections.sort(tmp);

        int last = tmp.size() - 1;
        choice1 = tmp.get(last);
        if (tmp.size() > 1) {
            choice2 = tmp.get(last - 1);
            secondPlaceScore = choice2.getScore();
        }
    }

    /**
     * This only makes sense if you tried choose() first
     * to sort scored places.
     *
     * @return true if two choices are tied
     */
    public boolean isAmbiguous() {
        if (choice2 != null && choice1 != null) {
            // float == float does this work in Java? 7.125 == 7.125 ?
            //
            // first place Not better than second place?
            return !(choice1.getScore() > choice2.getScore());
        }
        return false;
    }

    private double secondPlaceScore = -1;

    /**
     * Only call after choose() operation.
     *
     * @return score
     */
    public double getSecondChoiceScore() {
        return secondPlaceScore;
    }

    /**
     * @return ScoredPlace, choice2
     */
    public ScoredPlace getSecondChoice() {
        return choice2;
    }

    /**
     * @return all values of scored places. Not a copy
     */
    public Collection<ScoredPlace> getPlaces() {
        return scoredPlaces.values();
    }

    /**
     * @param place
     */
    public void addPlace(ScoredPlace place) {
        this.addPlace(place, defaultScore(place));
        this.rules.add(DEFAULT_SCORE);
    }

    public static final String DEFAULT_SCORE = "DefaultScore";

    /**
     * Each place has an ID, but this candidate scoring mechanism must score
     * distinct ID+NAME tuples. As name variances play into scoring and choosing.
     *
     * @param p
     * @return
     */
    public String makeKey(Place p) {
        return String.format("%s~%s", p.getKey(), p.getNamenorm());
    }

    /**
     * @param place
     * @param score
     */
    public void addPlace(ScoredPlace place, Double score) {
        place.incrementScore(score);
        this.scoredPlaces.put(makeKey(place), place);

        // 'US.CA' or 'US.06', etc.
        // 'US'
        // Not "" or null allowed here:
        if (place.getCountryCode() != null) {
            this.hierarchicalPaths.add(place.getHierarchicalPath());
            this.countries.add(place.getCountryCode());
        }
    }

    /**
     *
     */
    public static final double NAME_WEIGHT = 0.2;

    /**
     *
     */
    public static final double FEAT_WEIGHT = 0.1;

    /**
     *
     */
    public static final double LOCATION_BIAS_WEIGHT = 0.7;

    /**
     * Given this candidate, how do you score the provided place
     * just based on those place properties (and not on context, document
     * properties,
     * or other evidence)?
     * This 'should' produce a base score of something between 0 and 1.0, or 0..10.
     * These scores do not necessarily need to stay in that range, as they are all
     * relative.
     * However, as rules fire and compare location data it is better to stay in a
     * known range
     * for sanity sake.
     *
     * @param g
     * @return objective score for the gazetteer entry
     */
    public double defaultScore(Place g) {
        double sn = scoreName(g);
        double sf = scoreFeature(g);
        double sb = g.getId_bias();

        double baseScore = (NAME_WEIGHT * sn) + (FEAT_WEIGHT * sf) + (LOCATION_BIAS_WEIGHT * sb);
        return 10 * baseScore;
    }

    /**
     * Produce a goodness score in the range 0 to 1.0
     * Trivial examples of name matching:
     *
     * <pre>
     *  given some patterns, 'geo' match Text
     *
     *   case 1. 'Alberta' matches ALBERTA or alberta just fine.
     *   case 2. 'La' matches LA, however, knowing "LA" is a acronym/abbreviation
     *       adds to the score of any geo that actually is "LA"
     *   case 3. 'Afghanestan' matches Afghanistan, but decrement because it is not perfectly spelled.
     * </pre>
     *
     * @param g
     * @return score for a given name based on all of its diacritics
     */
    protected double scoreName(Place g) {
        int startingScore = getTextnorm().length();

        int editDist = LevenshteinDistance.getDefaultInstance().apply(getTextnorm(), g.getNamenorm());
        int score = startingScore - editDist;
        if (isUpper() && (g.isAbbreviation() || TextUtils.isUpper(g.getName()))) {
            ++score;
        }
        // Mismatch in case for abbreviation.
        else if (!isUpper() && g.isAbbreviation()) {
            --score;
        }
        // Mismatch in name diacritics downgrades name score here.
        if ((isASCII() && !g.isASCIIName()) || (!isASCII() && g.isASCIIName())) {
            --score;
        }
        if (isASCII() && g.isASCIIName()) {
            ++score;
        }
        return (float) score / startingScore;
    }

    /**
     * A preference for features that are major places or boundaries.
     * This yields a feature score on a 0 to 1.0 point scale.
     *
     * @param g
     * @return feature score
     */
    protected double scoreFeature(Place g) {

        Integer wt = designationWeight.get(g.getFeatureCode());
        if (wt != null) {
            return (float) wt / 10;
        }
        int score = DEFAULT_DESIGNATION_WT;
        wt = classWeight.get(g.getFeatureClass());
        if (wt != null) {
            score += wt.intValue();
        }

        return (float) score / 10;
    }

    /**
     * @param place
     * @param score
     * @deprecated Avoid incrementing location score without citing the reason. Use
     *             {@link #incrementPlaceScore(Place, Double, String)}
     */
    @Deprecated
    public void incrementPlaceScore(Place place, Double score) {
        ScoredPlace currentScore = this.scoredPlaces.get(makeKey(place));
        if (currentScore != null) {
            currentScore.incrementScore(score);
        }
    }

    /**
     * Consolidate attaching Rules to this name when also scoring candidate
     * locations.
     * This operation says a given Place deserves a certain increment in score for a
     * certain reason.
     * 
     * @param place
     * @param score
     * @param rule
     */
    public void incrementPlaceScore(Place place, Double score, String rule) {
        addRule(rule);
        ScoredPlace geo = this.scoredPlaces.get(makeKey(place));
        if (geo != null) {
            geo.incrementScore(score, rule);
        }
    }

    /**
     * @return all rules
     */
    public Collection<String> getRules() {
        return rules;
    }

    /**
     * @param rule
     * @return true if candidate has seen this rule already
     */
    public boolean hasRule(String rule) {
        return rules.contains(rule);
    }

    /**
     * @param rule
     */
    public void addRule(String rule) {
        rules.add(rule);
    }

    /**
     * @param ev evidence
     * @return internal ID for evidence (rule + location)
     */
    protected static String getEvidenceID(PlaceEvidence ev) {
        String rule = ev.getRule();
        String pid = ev.getPlaceID() != null ? ev.getPlaceID() : "x";
        return String.format("%s/%s", rule, pid);
    }

    /**
     * @param ev evidence object
     */
    public void addEvidence(PlaceEvidence ev) {
        String evid = getEvidenceID(ev);
        if (!evidence.containsKey(evid)) {
            evidence.put(evid, ev);
            if (ev.getRule() != null) {
                this.rules.add(ev.getRule());
            }
        }
    }

    /**
     * @param rule
     * @param weight
     * @param ev
     */
    public void addEvidence(String rule, double weight, Place ev) {
        addEvidence(new PlaceEvidence(ev, rule, weight));
    }

    /**
     * Add country evidence and increment score immediately.
     *
     * @param rule
     * @param weight
     * @param cc
     * @param geo
     */
    public void addCountryEvidence(String rule, double weight, String cc, Place geo) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setCountryCode(cc);
        ev.setPlaceID(cc);
        addEvidence(ev);

        ev.setEvaluated(true);
        this.incrementPlaceScore(geo, weight, ev.getRule());
    }

    /**
     * @param rule
     * @param weight
     * @param adm1
     * @param cc
     */
    public void addAdmin1Evidence(String rule, double weight, String adm1, String cc) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setAdmin1(adm1);
        ev.setCountryCode(cc);
        ev.setPlaceID(adm1);
        this.addEvidence(ev);
    }

    /**
     * @param rule
     * @param weight
     * @param fclass
     */
    public void addFeatureClassEvidence(String rule, double weight, String fclass) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setFeatureClass(fclass);
        ev.setPlaceID(String.format("fc-%s", fclass)); /* Fake internal place ID */
        addEvidence(ev);
    }

    /**
     * @param rule
     * @param weight
     * @param fcode
     */
    public void addFeatureCodeEvidence(String rule, double weight, String fcode) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setFeatureCode(fcode);
        ev.setPlaceID(String.format("fc-%s", fcode)); /* Fake internal place ID */
        addEvidence(ev);
    }

    /**
     * Add evidence and increment score immediately.
     *
     * @param rule
     * @param weight
     * @param coord
     * @param geo
     * @param proximityScore
     */
    public void addGeocoordEvidence(String rule, double weight, LatLon coord, Place geo, double proximityScore) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setLatLon(coord);
        ev.setPlaceID(geo.getPlaceID()); /* coord_text should be set for valid coordinates */
        addEvidence(ev);
        //
        ev.setEvaluated(true);
        this.incrementPlaceScore(geo, weight * proximityScore, "Coordinate.Proximity");
        // The indirect connection between found coord and closest geo candidate
        // is assessed here. The score for geo has already be incremented.
    }

    /**
     * @return the current evidence
     */
    public Collection<PlaceEvidence> getEvidence() {
        return this.evidence.values();
    }

    /**
     * @return true if candidate has any associated potential locations
     */
    public boolean hasPlaces() {
        return !this.scoredPlaces.isEmpty();
    }

    /**
     * @return string representation of candidate
     */
    @Override
    public String toString() {
        return summarize(false);
    }

    /**
     * If you need a full print out of the data, use summarize(true);.
     *
     * @param dumpAll
     * @return summary of evidence, rules and chosen location
     */
    public String summarize(boolean dumpAll) {
        StringBuilder tmp = new StringBuilder(getText());
        tmp.append(String.format("(C=%d, N=%d, filtered=%s)", getConfidence(), scoredPlaces.size(),
                isFilteredOut() ? "Out" : "In"));
        tmp.append("\nRules=");
        tmp.append(rules.toString());
        tmp.append("\nEvidence=");
        tmp.append(evidence.toString());
        if (dumpAll) {
            tmp.append("\nPlaces=\n");
            for (ScoredPlace p : scoredPlaces.values()) {
                tmp.append("\t");
                tmp.append(p.toString());
                tmp.append("\n");
            }
        }
        return tmp.toString();
    }

    /**
     * @return the preceding tokens
     */
    public String[] getPrematchTokens() {
        return preTokens;
    }

    /**
     * @param toks set preceding tokens
     */
    public void setPrematchTokens(String[] toks) {
        this.preTokens = toks;
    }

    /**
     * @return tokens following name span
     */
    public String[] getPostmatchTokens() {
        return postTokens;
    }

    /**
     * @param toks set following tokens
     */
    public void setPostmatchTokens(String[] toks) {
        this.postTokens = toks;
    }

    /**
     * Given a path, 'a.b' ( province b in country a),
     * see if this name is present there.
     *
     * @param path
     * @return true if given path is represented by candidates' potential locations
     */
    public boolean presentInHierarchy(String path) {
        return path != null && this.hierarchicalPaths.contains(path);
    }

    /**
     * @param cc country code
     * @return true if candidate has potential locations for the given country code.
     */
    public boolean presentInCountry(String cc) {
        return this.countries.contains(cc);
    }

    /**
     * How many different countries contain this name?.
     *
     * @return count of distinct country codes inferred
     */
    public int distinctCountryCount() {
        return this.countries.size();
    }

    /**
     * @return distinct locations by ID, not by geodetic location
     */
    public int distinctLocationCount() {
        return this.scoredPlaces.size(); // These are keyed by PLACE ID, essentially location.
    }

    /**
     * Mark candidate as valid to protect it from being filtered out by downstream
     * rules.
     */
    public void markValid() {
        markedValid = true;
    }

    /**
     * if candidate was marked as valid. IF valid, then avoid filters.
     *
     * @return true if rules have marked this candidate valid
     */
    public boolean isValid() {
        return markedValid;
    }

    /**
     * @return true if candidate has any evidence.
     */
    public boolean hasEvidence() {
        return !this.evidence.isEmpty();
    }

    public static Pattern tokenizer = Pattern.compile("[\\s+\\p{Punct}]+");

    public static int ABBREVIATION_MAX_LEN = 5;
    private int wordCount = 0;

    /**
     * a basic whitespace, punctuation delimited count of grams
     * Set ONLY after inferTextSense() is invoked
     */
    public int getWordCount() {
        return wordCount;
    }

    /**
     * text hueristics
     * 
     * @param contextisLower True if text around mention is mainly lowercase
     * @param contextisUpper True if text around mention is mainly uppercase
     */
    public void inferTextSense(boolean contextisLower, boolean contextisUpper) {

        if (getText() == null) {
            return;
        }
        this.tokens = tokenizer.split(getText());
        this.wordCount = tokens.length;
        this.hasDiacritics = TextUtils.hasDiacritics(getText());
        /*
         * Old logic had harmful consequences on By-lines etc: Keep scope of this
         * narrow.
         */
        if (!contextisUpper && isUpper() && 0 < getLength() && getLength() < ABBREVIATION_MAX_LEN) {
            this.isAcronym = true;
            this.isAbbreviation = true;
        }
    }

    /**
     * Tokens in word. Only after inferTextSense() is invoked.
     * 
     * @return
     */
    public String[] getTokens() {
        return tokens;
    }
}
