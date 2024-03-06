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
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */

package org.opensextant.extraction;

import org.opensextant.util.TextUtils;

/**
 * A very simple struct to hold data useful for post-processing entities once
 * found.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TextEntity {

    /**
     *
     */
    protected String text = null;

    /**
     * char offset of entity; location in document where entity starts.
     */
    public int start = -1;
    /**
     * char offset of entity; location in document where entity ends.
     */
    public int end = -1;

    /**
     * char immediately after span
     */
    public char postChar = 0;

    /**
     * char immediately before span
     */
    public char preChar = 0;

    // Use this
    private String context = null;
    // OR this
    private String prematch = null;
    private String postmatch = null;
    /**
     *
     */
    public String match_id = null;
    /**
     * If this entity is contained completely within some other
     */
    public boolean is_submatch = false;
    /**
     * If this entity is a overlaps with some other
     */
    public boolean is_overlap = false;
    /**
     * If this entity is a duplicate of some other
     */
    public boolean is_duplicate = false;


    /**
     * Simple Span representation.
     *
     * @param x1 start offset
     * @param x2 end offset
     */
    public TextEntity(int x1, int x2) {
        start = x1;
        end = x2;
    }

    /**
     * sets the value of the TextEntity
     *
     * @param t text
     */
    public void setText(String t) {
        text = t;
        if (text != null) {
            isLower = TextUtils.isLower(text);
            isUpper = TextUtils.isUpper(text);

            // Worth tracking if matched text is ASCII only. If name or entity has
            // diacritics then
            // you may look at it differently.
            //
            try {
                isASCII = TextUtils.isASCII(TextUtils.removePunctuation(text));
            } catch (Exception err) {
                isASCII = false;
            }
        }
    }

    /**
     * Set just the value, without incurring the cost of other metrics or flags
     * about the text that likely are unchanged.
     *
     * @param t the text
     */
    public void setTextOnly(String t) {
        text = t;
    }

    private boolean isLower = false;
    private boolean isUpper = false;
    private boolean isASCII = false;

    /**
     * If non-punctuation content is purely ASCII vs. Latin1 vs. unicode.
     *
     * @return true if text value is purely ASCII
     */
    public boolean isASCII() {
        return isASCII;
    }

    /**
     * test If text (that has a case sense) is ALL lower case
     *
     * @return true if all lower.
     */
    public boolean isLower() {
        return isLower;
    }

    /**
     * test If text (that has a case sense) is ALL upper case
     *
     * @return true if all upper.
     */
    public boolean isUpper() {
        return isUpper;
    }

    /**
     * test if text is mixed case.
     *
     * @return true if neither allower or all upper.
     */
    public boolean isMixedCase() {
        return !(isUpper || isLower);
    }

    /**
     * @return text, value of a TextEntity
     */
    public String getText() {
        return text;
    }

    /**
     * get the length of the matched text
     *
     * @return int, length
     */
    public int getLength() {
        if (start < 0) {
            // Match not initialized
            return 0;
        }
        return (end - start);
    }

    /* ==================================
     * Convenience methods for carrying the context through the output
     * processing
     * ==================================*/
    /**
     * Set the context with before and after windows
     *
     * @param before text before match
     * @param after  text after match
     */
    public void setContext(String before, String after) {
        this.prematch = before;
        this.postmatch = after;
        String buf = this.prematch +
                " " +
                this.text +
                " " +
                this.postmatch;
        this.context = buf;
    }

    /**
     * Set the context buffer from a single window
     *
     * @param window textual window
     */
    public void setContext(String window) {
        this.context = window;
    }

    /**
     * @return context buffer regardless if it is singular context or separate
     * pre/post match
     */
    public String getContext() {
        return this.context;
    }

    /**
     * @return text before match
     */
    public String getContextBefore() {
        return this.prematch;
    }

    /**
     * @return text after match
     */
    public String getContextAfter() {
        return this.postmatch;
    }

    /**
     * @return string representation of entity
     */
    @Override
    public String toString() {
        if (text == null) {
            return String.format("Span @(%d:%d)", start, end);
        } else {
            return String.format("%s @(%d:%d)", text, start, end);
        }
    }

    /**
     * Assess if an offset is within this span
     *
     * @param x offest to test
     * @return if this entity contains the offset
     */
    public boolean contains(int x) {
        if (start < 0 || end < 0) {
            return false;
        }
        return (start <= x && x <= end);
    }

    /**
     * @param m match/entity object to copy
     */
    public void copy(TextEntity m) {

        // TextMatch generic stuff:
        this.text = m.text;
        this.start = m.start;
        this.end = m.end;
        this.is_duplicate = m.is_duplicate;
        this.is_overlap = m.is_overlap;
        this.is_submatch = m.is_submatch;

        // These are private. maybe should use this.setA(m.getA())
        this.postmatch = m.postmatch;
        this.prematch = m.prematch;
        this.context = m.context;
        this.match_id = m.match_id;
    }

    public boolean isWithin(TextEntity t) {
        return (end <= t.end && start >= t.start);
    }

    /**
     * Assuming simple whitespace separation or other simple delimiters, is this
     * term following the argument entity?
     *
     * @param t other entity
     * @return true if t occurs after the current entity
     */
    public boolean isAfter(TextEntity t) {
        return start > t.end;
    }

    /**
     * Assuming simple whitespace separation or other simple delimiters, is this
     * term preceeding the argument entity?
     *
     * @param t other TextEntity
     * @return true if t is before the current entity
     */
    public boolean isBefore(TextEntity t) {
        return t.start > end;
    }

    public boolean isSameMatch(TextEntity t) {
        return (start == t.start && end == t.end);
    }

    public boolean isRightMatch(TextEntity t) {
        return (start == t.start);
    }

    public boolean isLeftMatch(TextEntity t) {
        return (end == t.end);
    }

    public boolean isOverlap(TextEntity t) {
        // t overlaps with self on the left side
        // OR t overlaps with self on right side
        //       Aaaa      start_diff = end - t.end (>0)
        //     Bbbb
        if (isSameMatch(t)) {
            // A perfect overlap.  If you intend to test for sameness, call this separately.
            return true;
        }

        return (end >= t.end && start >= t.start && start <= t.end) ||
                //  Aaaa
                //    Bbbb
                (end <= t.end && start <= t.start && end >= t.start);
    }

    /**
     * Proximity test between this text span and another
     * This is A; B is input. use nchars=2
     * <pre>
     *    AaaaaaaB               // B next to A
     *           BbbbbbA         // B before A
     *           Bbbbb     A     // A far from A
     *    Aaaaaa B               // B within nchars of A
     *        AaaBbbaaa          // B is inside A, so they are "within"
     *
     * </pre>
     *
     * @param t      TextEntity span
     * @param nchars number of characters
     * @return True if given entity span is within nchars, left or right
     */
    public boolean isWithinChars(TextEntity t, int nchars) {
        if (isOverlap(t)) {
            return true;
        }
        return Math.abs(this.start - t.end) <= nchars || Math.abs(t.start - this.end) <= nchars;
    }
}
