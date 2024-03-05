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

import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.opensextant.data.Geocoding;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.rules.FeatureClassMeta;
import static org.opensextant.extractors.geo.rules.FeatureRule.lookupFeature;
import static org.opensextant.extractors.geo.rules.FeatureRule.FEAT_RULE;
import org.opensextant.util.TextUtils;

/**
 * A PlaceCandidate represents a portion of a document which has been identified
 * as a possible named geographic location. It is used to collect together the
 * information from the document (the evidence), as well as the possible
 * geographic locations it could represent (the Places ). It also contains the
 * results of the final decision to include: bestPlace - Of all the places with
 * the same/similar names, which place is it?
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
    private final Set<String> hierarchicalPaths = new HashSet<>();
    private final Set<String> countries = new HashSet<>();
    private boolean markedValid = false;
    private HashMap<String, PlaceCandidate> related = null;
    private boolean derived = false;
    private boolean anchor = false;
    private String nonDiacriticTextnorm = null;
    private boolean reviewed = false;
    private boolean hasCJKtext = false;
    private boolean hasMEtext = false;

    public final static String VAL_SAME_COUNTRY = "same-country";
    /**
     * Linked geographic slots, in no order. These help develop a fuller depiction of the
     * context of a place mention -- through linked-geography in these categorical slots.
     * These are ordered roughly in resolution order, fine to coarse.
     *
     * POSTAL or other Association:
     * Country vs. "Same Country" -- for small territories, a POSTAL code may be associated with the country at
     * ADM0 level for example, if there are not many admin boundaries.  So "Country" association is tight there.
     * "Same Country" is much looser, indicating only that a mentioned place is in a mentioned country
     *
     * Holding off: VAL_COUNTRY
     */
    public static final String[] KNOWN_GEO_SLOTS = {VAL_PLACE, "city", "admin", VAL_SAME_COUNTRY};

    public PlaceCandidate(int x1, int x2) {
        super(x1, x2);
    }

    public String getNDTextnorm() {
        return nonDiacriticTextnorm;
    }

    @Override
    public void setText(String name) {
        super.setText(name);
        this.nonDiacriticTextnorm = TextUtils.phoneticReduction(getTextnorm(), isASCII());
        this.hasMEtext = TextUtils.hasMiddleEasternText(name);
        this.hasCJKtext = TextUtils.hasCJKText(name);
    }

    public boolean hasCJKText() {
        return this.hasCJKtext;
    }

    public boolean hasMiddleEasternText() {
        return this.hasMEtext;
    }

    public boolean isAbbrevLength() {
        return getLength() <= ABBREVIATION_MAX_LEN;
    }

    /**
     * Mark this candidate as something that was derived by special rules and to treat it
     * differently, e.g., in formatting output or other situations.  A derivation may correct
     * or subsume other non-derived mentions.
     *
     * @param b
     */
    public void setDerived(boolean b) {
        derived = b;
    }

    public boolean isDerived() {
        return derived;
    }

    /**
     * Mark this mention as an anchor to build from, e.g., given a postal code expand the tag to gather
     * the related mentions for city, province, etc. vice versa.  In such situations you want one anchor
     * in such a tuple.
     */
    public void markAnchor() {
        anchor = true;
    }

    public boolean isAnchor() {
        return anchor;
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
    public void choose(ScoredPlace geo) {
        choice1 = geo;
    }

    /**
     * Connect another match to this one, usually something cooccurring or collocated with this match
     *
     * @param pc
     */
    public void addRelated(PlaceCandidate pc) {
        if (related == null) {
            related = new HashMap<>();
        }
        String key = String.format("%s#%d", pc.getText(), pc.start);
        related.put(key, pc);
    }

    public Collection<PlaceCandidate> getRelated() {
        if (related == null) {
            return null;
        }
        return related.values();
    }

    // ---- the getters and setters ---------
    //

    private String[] preTokens = null;
    private String[] postTokens = null;
    private String[] tokens = null;
    private static final int contextWidth = 40;

    /**
     * Get some sense of tokens surrounding match. Possibly optimize this by
     * getting token list from SolrTextTagger (which provides the
     * lang-specifics)
     *
     * @param sourceBuffer
     */
    protected void setSurroundingTokens(String sourceBuffer) {
        int[] window = TextUtils.get_text_window(start, end - start, sourceBuffer.length(),
                contextWidth);

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
            postChar = sourceBuffer.charAt(window[2]);/* offset less than doc end */
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
     * Match types - Abbreviation/Code, Acronym or normal (unknown).
     * From found text we can only tell from case sense and punctuation if the intended
     * part of speech is normal name/text or something coded such as an abbreviation, alphnum, or acronym.
     * For these reason "isAbbreviation" accounts for abbreviations and codes.
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

    public static int SHORT_NAME_LEN = 6;

    /**
     * Alias for "isAbbreviation || isAcronym"
     * and a length criteria of less than #{PlaceCandidate.SHORT_NAME_LEN}
     * @return true if name is short and likely a code or abbreviation.
     */
    public boolean isShortName() {
        return (isAbbreviation || isAcronym) && this.getLength() <= SHORT_NAME_LEN;
    }

    /**
     * After candidate has been scored and all, the final best place is the
     * geocoding result for the given name in context.
     *
     * @return the chosen geocoding
     */
    public Geocoding getGeocoding() {
        choose();
        if (this.choice1 != null) {
            return getChosen().getPlace();
        }
        return null;
    }

    public void setChosenPlace(Place geo) {
        choice1 = new ScoredPlace(null, null);
        choice1.setPlace(geo);
    }

    public Place getChosenPlace() {
        return choice1 != null ? choice1.getPlace() : null;
    }

    /**
     * @return
     */
    public ScoredPlace getChosen() {
        return choice1;
    }

    /**
     * Unlike choose(Place), setChosen(Place) just sets the value.
     * choose() attempts to pull the ScoredPlace from internal cache.
     *
     * @param geo
     */
    public void setChosen(ScoredPlace geo) {
        choice1 = geo;
        if (geo == null) {
            choice2 = null;
        }
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
            // Nothing to choose.
            return;
        }
        if (scoredPlaces.size() == 1) {
            // Just one to choose -- optimization.
            choice1 = scoredPlaces.values().iterator().next();
            choice2 = null;
            secondPlaceScore = 0;
            return;
        }

        // More than one -- sort by score.
        List<ScoredPlace> tmp = new ArrayList<>(scoredPlaces.values());
        Collections.sort(tmp);

        int last = tmp.size() - 1;
        choice1 = tmp.get(last);
        if (tmp.size() > 1) {
            choice2 = tmp.get(last - 1);
            secondPlaceScore = choice2.getScore();
        }
    }


    /**
     * To be used sparingly -- determine if a matched place for this text span
     * is actually a code. Example
     * <pre>
     *     YYZ  -- an airport code
     *     Yyz  -- transliterated name.
     *     If we are not tagging coded information then short abbreviations are ignorable.
     * </pre>
     * @return True if a Geographic place for this match is actually a CODE
     */
    public boolean matchesCode() {
        if (!hasPlaces()) {
            return false;
        }
        for (ScoredPlace geo : getPlaces()) {
            if (geo.getPlace().isCode()) {
                return true;
            }
        }
        return false;
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
    public Place getSecondChoice() {
        return choice2 != null ? choice2.getPlace() : null;
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
        this.addPlace(place, defaultScore(place.getPlace()));
        this.rules.add(DEFAULT_SCORE);
        this.rules.add(FEAT_RULE);
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
        Place geo = place.getPlace();
        this.scoredPlaces.put(makeKey(geo), place);

        // 'US.CA' or 'US.06', etc.
        // 'US'
        // Not "" or null allowed here:
        if (geo.getCountryCode() != null) {
            this.hierarchicalPaths.add(geo.getHierarchicalPath());
            this.countries.add(geo.getCountryCode());
        }
    }

    /**
     *
     */
    public static final double NAME_WEIGHT = 0.5;

    /**
     *
     */
    public static final double FEAT_WEIGHT = 0.3;

    /**
     *
     */
    public static final double LOCATION_BIAS_WEIGHT = 0.10;

    /**
     * Given this candidate, how do you score the provided place
     * just based on those place properties (and not on context, document
     * properties, or other evidence)?
     * This 'should' produce a base score of something between 0 and 1.0, or 0..10.
     * These scores do not necessarily need to stay in that range, as they are all relative.
     * However, as rules fire and compare location data it is better to stay in a
     * known range for sanity sake.
     *
     * @param g
     * @return objective score for the gazetteer entry
     */
    public double defaultScore(Place g) {
        double sn = scoreName(g);
        int sb = g.getId_bias(); /* v3.5: 100 point scale. Multiply by 0.01 */
        return (NAME_WEIGHT * sn) + (LOCATION_BIAS_WEIGHT * sb);
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

        int editDist = LevenshteinDistance.getDefaultInstance().apply(getTextnorm(),
                g.getNamenorm());
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
        FeatureClassMeta meta = lookupFeature(g);
        return meta.factor * 0.10;
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
    public void addGeocoordEvidence(String rule, double weight, LatLon coord, Place geo,
                                    double proximityScore) {
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
        return getText();
    }

    /**
     * If you need a full print out of the data, use summarize(true);.
     *
     * @param dumpAll
     * @return summary of evidence, rules and chosen location
     */
    public String summarize(boolean dumpAll) {
        StringBuilder tmp = new StringBuilder(getText() != null ? getText() : "<null>");
        tmp.append(String.format("(CONF=%d, N=%d, filtered=%s)", getConfidence(),
                scoredPlaces.size(), isFilteredOut() ? "Out" : "In"));
        tmp.append("\nRules=");
        tmp.append(rules);
        tmp.append("\nEvidence=");
        tmp.append(evidence);
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

    public String getSurroundingText() {
        StringJoiner joiner = new StringJoiner(" ");
        // Find if surrounding text is not uppercase.
        if (getPrematchTokens() != null) {
            for (String tok : getPrematchTokens()) {
                joiner.add(tok);
            }
        }
        if (getPostmatchTokens() != null) {
            for (String tok : getPostmatchTokens()) {
                joiner.add(tok);
            }
        }
        return joiner.toString();
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

    public static final Pattern tokenizer = Pattern.compile("[\\s+\\p{Punct}]+");

    public static final int ABBREVIATION_MAX_LEN = 5;
    private int wordCount = 0;

    /**
     * a basic whitespace, punctuation delimited count of grams
     * Set ONLY after inferTextSense() is invoked
     *
     * @return token word count
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
         * Check for abbreviation
         */
        if (TextUtils.isAbbreviation(getText(), !contextisLower)) {
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

    private Map<String, Place> linkedGeography = null;

    /**
     * Get the collection of geographic slots geolocated.  E.g.,
     * for a "Town Hall" building location you might link the Place object representing the "city" slot.
     *
     * @return
     */
    public Map<String, Place> getLinkedGeography() {
        return linkedGeography;
    }

    public void setLinkedGeography(Map<String, Place> geography) {
        linkedGeography = geography;
    }

    /**
     * Foricbly link geography to the given slot.
     *
     * @param otherMention
     * @param slot
     * @param geo
     * @see #linkGeography(PlaceCandidate, String, String)
     */
    public void linkGeography(PlaceCandidate otherMention, String slot, Place geo) {
        addRelated(otherMention);
        linkGeography(slot, geo);
    }

    public void linkGeography(String slot, Place geo) {
        if (linkedGeography == null) {
            linkedGeography = new HashMap<>();
        }
        linkedGeography.put(slot, geo);
    }

    public boolean hasLinkedGeography(String slot) {
        if (linkedGeography == null) {
            return false;
        }
        return linkedGeography.containsKey(slot);
    }

    /**
     * Link geographic mention from other part of the document. E.g.,
     * for a "Town Hall" building location you might link the PlaceCandidate mention object representing the "city" slot.
     * <p>
     * method added to support PostalGeocoder.  TBD.
     *
     * @param otherMention
     * @param slot
     * @param featPrefix
     * @return True if any link was made or already existed.
     */
    public boolean linkGeography(PlaceCandidate otherMention, String slot, String featPrefix) {

        if (hasLinkedGeography(slot)) {
            // This check is not necessary ... how many linkages could be made?
            return true;
        }

        Place geo = getChosenPlace();
        Place otherGeo = otherMention.getChosenPlace();
        if (geo != null && otherGeo != null) {
            if (otherGeo.getFeatureDesignation().startsWith(featPrefix)) {
                if (geo.sameBoundary(otherGeo)) {
                    linkGeography(slot, otherGeo);
                    return true;
                }
            }
        }

        // Dare we cache the sorted scoredPlaces for each mention/otherMention?
        //
        for (ScoredPlace someGeoScore : scoredPlaces.values()) {
            for (ScoredPlace otherGeoScore : otherMention.getPlaces()) {
                Place geo2 = someGeoScore.getPlace();
                Place otherGeo2 = otherGeoScore.getPlace();
                if (otherGeo2.getFeatureDesignation().startsWith(featPrefix)) {
                    if (geo2.sameBoundary(otherGeo2)) {
                        linkGeography(slot, otherGeo2);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * A general purpose flag "reviewed" to indicate something was reviewed and to not repeat that task
     * on this instance.
     *
     * @param b
     */
    public void setReviewed(boolean b) {
        reviewed = b;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    private boolean postalEval = false;
    private boolean hasPostal = false;

    /**
     * Evaluate if postal matches reside in candidate locations. Evaluate only once and save result.
     * We distinguish between "hasPostal" matches vs. marking this place as "is Postal".
     * That's the difference between factual and inferential.
     *
     * @return true if postal features exist here.
     */
    public boolean hasPostal() {
        if (postalEval) {
            return hasPostal;
        }
        postalEval = true;
        for (ScoredPlace geo : getPlaces()) {
            if (!geo.getPlace().isAdministrative()) {
                continue;
            }
            if (geo.getPlace().isPostal()) {
                hasPostal = true;
                return hasPostal;
            }
        }
        return hasPostal;
    }
}
