/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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

import org.opensextant.data.Place;

/**
 * A PlaceEvidence represents a fragment of evidence about a Place. Its intended
 * purpose is to represent evidence about a Place's identity which has been
 * extracted from a document. This evidence is used to help disambiguate
 * (distinguish among) places which have the same or similar names. It is
 * intentionally very similar to the Place class to facilitate comparisons with
 * that class.
 *
 * @author dlutz
 */
public final class PlaceEvidence extends Place implements Comparable<Object> {

    /**
     * SCOPE - Where did this evidence come from wrt to the PlaceCandidate it is
     * part of?
     * <ul>
     * <li>APRIORI - derived from the gazetteer only, not from any
     * information in the document</li>
     * <li>LOCAL - directly associated with
     * this instance of PC</li>
     * <li>COREF - associated with another (related)
     * PC in the document</li>
     * <li>MERGED - came from the merger of multiple
     * PlaceEvidences (future use)</li>
     * <li>DOCUMENT - in the same document but
     * has no other direct association</li>
     * </ul>
     */
    public enum Scope {

        APRIORI, LOCAL, COREF, MERGED, DOCUMENT
    }

    // private static final long serialVersionUID = 2389068067890L;
    // The rule which found the evidence
    private String rule = null;
    // the scope from which this evidence came
    private Scope scope = Scope.LOCAL;
    // The strength of the evidence
    private double weight = 0;

    private boolean evaluated = false;

    public PlaceEvidence() {
        super(null, null);
    }

    // copy constructor
    public PlaceEvidence(PlaceEvidence ev) {
        this();
        this.setAdmin1(ev.getAdmin1());
        this.setCountryCode(ev.getCountryCode());
        this.setFeatureClass(ev.getFeatureClass());
        this.setFeatureCode(ev.getFeatureCode());
        // this.setGeocoord(old.getGeocoord());
        this.setLatitude(ev.getLatitude());
        this.setLongitude(ev.getLongitude());
        this.setPlaceName(ev.getPlaceName());
        this.setRule(ev.getRule());
        this.setScope(ev.getScope());
        this.setWeight(ev.getWeight());
    }

    public PlaceEvidence(Place ev, String rule, double wt) {
        this();
        this.setAdmin1(ev.getAdmin1());
        this.setCountryCode(ev.getCountryCode());
        this.setFeatureClass(ev.getFeatureClass());
        this.setFeatureCode(ev.getFeatureCode());
        // this.setGeocoord(old.getGeocoord());
        this.setLatitude(ev.getLatitude());
        this.setLongitude(ev.getLongitude());
        this.setPlaceName(ev.getPlaceName());
        this.setRule(rule);
        // this.setScope(scope);
        this.setWeight(wt);
    }

    public boolean wasEvaluated() {
        return evaluated;
    }

    public void setEvaluated(boolean b) {
        evaluated = b;
    }

    // compare to other evidence by strength
    @Override
    public int compareTo(Object other) {
        if (!(other instanceof PlaceEvidence)) {
            return 0;
        }
        PlaceEvidence tmp = (PlaceEvidence) other;
        // return this.weight.compareTo(tmp.weight);
        if (tmp.weight == weight) {
            return 0;
        } else if (weight > tmp.weight) {
            return 1;
        }
        // must be lower:
        return -1;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    /**
     * if Place given has same feature class and code as the current evidence
     */
    public boolean isSameFeature(Place geo) {
        if (this.getFeatureClass() == null) {
            return false;
        }
        return (this.getFeatureClass().equals(geo.getFeatureClass())
                && this.getFeatureCode().equals(geo.getFeatureCode()));
    }

    // Override toString to get a reasonable string label for this PlaceEvidence
    /**
     * @author ubaldino
     */
    @Override
    public String toString() {
        return String.format("%s - %s/%03.2f (%s, %s, %s)", rule, scope, weight, getPlaceName(), getAdmin1(),
                getCountryCode());
    }
}
