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
package org.opensextant.extraction;

import org.opensextant.data.MatchSchema;
import org.opensextant.util.TextUtils;

/**
 * A variation on TextEntity that also records pattern metadata
 *
 * @author ubaldino
 */
public class TextMatch extends TextEntity implements MatchSchema, Comparable<TextMatch> {

    /**
     * the ID of the pattern that extracted this
     */
    public String pattern_id = null;

    /**
     * A short label or tag representing the matcher, extractor, tagger, etc. that
     * produced this match.
     */
    public String producer = null;

    /**
     * Type, as in Annotation type or code.
     */
    protected String type = "generic";

    public TextMatch(int x1, int x2) {
        super(x1, x2);
    }

    public String getType() {
        return type;
    }

    /**
     * Allow matchers and taggers to set a type label, e.g., pattern family or other
     * string.
     *
     * @param t type
     */
    public void setType(String t) {
        type = t;
    }

    /**
     * @return string representation
     */
    @Override
    public String toString() {
        return text + " @(" + start + ":" + end + ") matched by " + this.pattern_id;
    }

    /**
     * @param m a text match to copy to this instance
     */
    public void copy(TextMatch m) {
        super.copy(m);

        this.pattern_id = m.pattern_id;
    }

    /**
     * Case-insensitive comparison to another string
     * @param m match
     * @return trut if 
     */
    public boolean isSame(String m) {
        if (m == null || text == null) {
            return false;
        }
        return getText().equalsIgnoreCase(m);
    }

    /**
     * Compare the normalized string for this match to that of another.
     * @param m
     * @return true if getTextnorm()s yield same string.
     */
    public boolean isSameNorm(TextMatch m) {
        if (m == null || text == null) {
            return false;
        }
        return getTextnorm().equals(m.getTextnorm());
    }

    private boolean filteredOut = false;

    public boolean isFilteredOut() {
        return filteredOut;
    }

    public void setFilteredOut(boolean b) {
        filteredOut = b;
    }

    private String textnorm = null;

    /**
     * Get a normalized version of the text, lower case, punctuation and diacritics
     * removed. If you want only pieces of this normalization, you may override it.
     *
     * @return normalized version of text.
     */
    public String getTextnorm() {
        if (textnorm == null) {
            textnorm = TextUtils.removePunctuation(TextUtils.removeDiacritics(getText())).toLowerCase();
        }
        return textnorm;
    }

    /** Users of this class should set a non-default type via setType(String), otherwise
     * the match remains default and generic.
     * @return
     */
    public boolean isDefault() {
        return (type == null || "generic".equals(type));
    }

    /**
     * If called, this overwrites existing match_id
     * Match ID is typically entity label @ offset.
     * Alternatively a Match ID could be also label + value + start offset ...
     * to distinguish this text span from others.
     */
    public void defaultMatchId() {

        match_id = String.format("%s@%d", this.getType(), this.start);
    }

    /**
     * create a simple text-based identifier with form of value + start offset ...
     * @return
     */
    public String getContentId() {
        return String.format("%s@%d", this.getText(), this.start);
    }

    /**
     * Future planning -- match_id may become private field in future API.
     * @return
     */
    public String getMatchId() {
        return match_id;
    }

    /**
     * this match, A compared to B
     * Order:  A B  then A &gt; B
     * Order:  B A  then A &lt; B
     * Order:  same spans then A == B
     * @param other
     * @return
     */
    public int compareTo(TextMatch other) {
        if (other == null) {
            return 1;
        }
        if (isSameMatch(other)) {
            return 0;
        }
        if (isOverlap(other)) {
            if (other.end > end) {
                return -1;
            } else {
                return 1;
            }
        }
        return isBefore(other) ? -1 : 1;
    }
}
