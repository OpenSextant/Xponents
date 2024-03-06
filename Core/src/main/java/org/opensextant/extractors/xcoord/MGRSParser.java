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
package org.opensextant.extractors.xcoord;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.opensextant.geodesy.MGRS;
import org.opensextant.util.TextUtils;

/**
 * @author ubaldino
 */
public class MGRSParser {

    private static final MGRS[] empty = {};

    /**
     * Given the match parse MGRS as best as can be done.
     * TODO: provide level of confidence. Items that match MGRS scheme perfectly are
     * more likely to be MGRS than those that
     * are not perfect matches, e.g. typos, inadvertent text wrapping, whitespace
     * etc.
     *
     * @param rawtext  the rawtext
     * @param txt    text normalized, optionally
     * @param elements matched groups within regex pattern
     * @return array of possible MGRS interpretations.
     */
    public static MGRS[] parseMGRS(String rawtext, String txt, Map<String, String> elements) {
        // pad MGRS
        // remove whitespace
        // set MGRS
        // set lat, lon

        String text = null;
        if (txt == null) {
            text = TextUtils.delete_whitespace(rawtext);
        } else {
            text = txt;
        }

        // Filter out trivial DD DEG MM pattern.
        // This may not be an issue -- how prevalent is the DD DEG MM DMS pattern?
        // Trivial test: 44 DEG 34 is not an MGRS pattern.
        if (text.length() < 6) {
            // less than 6 chars long this is either a zone with no offset
            // or some sort of false positive. Pattern should not match this
            return empty;
        }

        if (text.length() < 8) {
            String _test = text.substring(2, 5);

            if (_test.equalsIgnoreCase("DEG")) {
                return empty;
            }
        }

        // If we matched an obvious and invalid month
        // as an MGRS, then fail early. Otherwise MGRSFilter
        // will parse out more complex patterns that are date + time
        // NOTE: an MGRS pattern may indeed look like a date+time in some cases but it
        // can actually be a valid MGRS. Take care not to filter out too aggressively.
        if (filterOutMonths(text)) {
            return empty;
        }

        String gzd = elements.get("MGRSZone");

        /*
         * Gridzone required.
         */
        if (gzd == null) {
            return empty;
        }

        // GZD Rule: 00 not allowed in 5-digit GZD
        // 0 not allowed in 4-digit
        int num1 = parseInt(gzd.substring(0, 1));
        int num2 = parseInt(gzd.substring(0, 2));

        if (num2 == 0 || (num1 == 0 && gzd.length() == 2)) {
            return empty;
        }

        if (num1 < 0) {
            // Pattern should have never matched.
            return empty;
        }

        // GZD Rule numbered zones not greate than 60
        if (num2 > 60) {
            return empty;
        }

        // ---------------------------------------|
        //
        // MGRS precision is 1m. Quad is 100,000m sq so resolution is 5 digits + 5
        // digits with optional whitespace
        // 99999n 99999e -- in MGRS we never see "m" units or N/E denoted explicitly
        // Occassionally, newlines or whitespace are interspersed in offset
        // minimal:
        // dd
        // ddddd ddddd with an additional one or two white spaces. The offsets start and
        // end with numbers. Only whitespace between is optional.
        // ddddd dddddd additional digit in Easting -- trailing 6th digit is a typo;
        // trim off
        // dddddd ddddd additional digit in Northing -- trailing 6th digit is a typo;
        // trim off
        // ddddddddddd Typo introduces ambiguity -- only correct thing is to split on
        // halfway point +/- 1 digit and emit two answers
        // dd\nddd ddddd Newline early in offset
        // ---------------------------------------|
        String ne = elements.get("Easting_Northing");
        int digits = TextUtils.count_digits(ne);
        boolean odd_len = ((digits & 0x0001) == 1);

        if (!isValidEastingNorthing(ne, odd_len)) {
            return empty;
        }

        if (!odd_len) {
            // ----------------------------
            // Completely normal MGRS with even number of digits.
            //
            // By this point you should have passed in normalized coordinate text - no
            // whitespace
            // ----------------------------
            //
            return new MGRS[]{new MGRS(text)};
        } else {
            // ----------------------------
            // Slightly obscure case that is possibly a typo or Easting/Northing disturbed.
            //
            // The following logic for parsing is predominantly related to managing typos
            // and rare cases.
            // < 5% of the instances seen fall into this category.
            //
            // ----------------------------

            int space_count = TextUtils.count_ws(ne);
            String nenorm;
            String Q = elements.get("MGRSQuad");

            StringBuilder mgrs1 = null;

            if (space_count == 0) {
                nenorm = ne;

                // ddddddddd odd number of digits, no spaces.
                // answer 1: dddd ddddd ==> N=dddd0
                // answer 2: ddddd dddd ==> E=dddd0
                int midpoint = (nenorm.length() / 2);
                mgrs1 = new StringBuilder(ne);
                mgrs1.insert(midpoint, "0"); // N=dddd0, add 0
                mgrs1.insert(0, Q);
                mgrs1.insert(0, gzd);

                StringBuilder mgrs2 = new StringBuilder(ne);
                mgrs2.append("0"); // E=dddd0 add 0
                mgrs2.insert(0, Q);
                mgrs2.insert(0, gzd);

                return new MGRS[]{new MGRS(mgrs1.toString()), new MGRS(mgrs2.toString())};
            }

            nenorm = TextUtils.squeeze_whitespace(ne);
            space_count = TextUtils.count_ws(nenorm);
            int ws_index = nenorm.indexOf(" ");
            int midpoint = (nenorm.length() / 2);

            // Even Split -- meaning easting northing appear to be good. But one needs to be
            // fixed.
            // boolean even_split = Math.abs( midpoint - ws_index ) <= 1;
            // Given one of
            // dddd ddddd
            // ddddd dddd
            // dd ddddddd
            // where whitespace is ' ' or '\n' or '\r', etc.

            // GIVEN: dddd ddddd
            if (space_count == 1 && (ws_index + 1) == midpoint) {
                mgrs1 = new StringBuilder(nenorm);
                // ANSWER: dddd0 ddddd
                mgrs1.insert(ws_index, "0");
                mgrs1.insert(0, Q);
                mgrs1.insert(0, gzd);

                // Just one answer:

                return new MGRS[]{new MGRS(TextUtils.delete_whitespace(mgrs1.toString()))};
            }

            if (space_count == 1 && (ws_index == midpoint)) {

                mgrs1 = new StringBuilder(nenorm);
                // ANSWER: ddddd dddd0
                mgrs1.append("0");
                mgrs1.insert(0, Q);
                mgrs1.insert(0, gzd);

                return new MGRS[]{new MGRS(TextUtils.delete_whitespace(mgrs1.toString()))};
            }

            // Given
            // ddd dd d
            // ddddd ddd dd
            // etc.
            // You have a bunch of MGRS digits broken up by whitespace.
            // This is really obscure case where formatting or content conversion
            // or word processing interferred with the MGRS text.
            //
            // This is < 0.1% of the cases
            //
            nenorm = TextUtils.delete_whitespace(ne);
            // ddddddddd odd number of digits, no spaces.
            // answer 1: dddd ddddd ==> N=dddd0
            // answer 2: ddddd dddd ==> E=dddd0
            midpoint = (nenorm.length() / 2);
            mgrs1 = new StringBuilder(nenorm);
            mgrs1.insert(midpoint, "0"); // N=dddd0, add 0
            mgrs1.insert(0, Q);
            mgrs1.insert(0, gzd);

            StringBuilder mgrs2 = new StringBuilder(nenorm);
            mgrs2.append("0"); // E=dddd0 add 0
            mgrs2.insert(0, Q);
            mgrs2.insert(0, gzd);

            return new MGRS[]{new MGRS(mgrs1.toString()), new MGRS(mgrs2.toString())};
        }
    }

    /**
     * A hueuristic from looking at real data, real text artifacts - typos, line
     * endings, whitespace wrapping, etc.
     * Acceptable Northing/Eastings:
     * dd dd
     * dddd dddd
     * typos: (odd number of digits; whitespace or not.)
     * ddd dd
     * ddddd
     * Not valid:
     * dd dd\nd odd digits and has line endings
     *
     * @param ne        NE string, e.g,. 56789 01234
     * @param oddLength if len is odd
     * @return if easting/northing is valid
     */
    protected static boolean isValidEastingNorthing(String ne, boolean oddLength) {
        // PARSE RULE: ignore abnormal MGRS patterns with line endings in the match
        //
        // The MGRS easting/northing is messy and contains line endings.
        // Abort. This is not likely an MGRS worth anything.
        //
        boolean containsEOL = (ne.contains("\n") || ne.contains("\r"));
        boolean containsTAB = ne.contains("\t");
        if (oddLength) {
            if (XCoord.getStrictMode()) {
                return false;
            }
            return !(containsEOL || containsTAB);
        }

        String[] tuples = ne.split("\\s+");
        if (tuples.length > 2) {
            return false;
        }
        if (tuples.length == 2) {
            // This is not so much an issue of a typo
            // If a northing/easting is a series of digit tuples asymmetrically
            // then we'll ignore it outright.
            if (tuples[0].length() != tuples[1].length()) {
                return false;
            }
        }

        int wsCount = TextUtils.count_ws(ne);

        // NO: dd dd\ndd
        // YES: normal text wrap on offset. dd\ndd
        if (wsCount > 1 && containsEOL) {
            return false;
        }
        return wsCount <= 2;
    }

    /**
     * @param x an integer string
     * @return int for the string
     */
    protected static int parseInt(String x) {
        try {
            return Integer.parseInt(x);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * While date/month patterns match the MGRS format, there are certain months
     * that are just too common
     * to believe they are relevant MGRS patterns.
     */
    private static final Set<String> ignoreTemporalTokens = new HashSet<>();

    static {
        ignoreTemporalTokens.add("jan");  // Lat band that is mostly water; Southern Africa
        ignoreTemporalTokens.add("feb");  // ditto; almost always water.
        // ignoreMonths.add("mar"); // Valid Congo, Brazil.

        ignoreTemporalTokens.add("apr");  // Invalid zone, first letter is C-X; Not likely to ever match
        ignoreTemporalTokens.add("aug");  // ditto

        ignoreTemporalTokens.add("gmt"); // Additional context filtering or part of speech
        ignoreTemporalTokens.add("est"); // would be required to validate ambiguous MGRS grids that look like date/time
        ignoreTemporalTokens.add("pst");

        // Other months, however have to be parsed. If they are dates
        // AND runtime flags have MGRS Filters enabled, then dates will be filtered out
        // usually.
        //
    }

    /**
     * Filter out well-known date patterns that are not valid MGRS;
     * MGRS Filter may additionally parse out more patterns. But we generate an MGRS
     * object here
     * we can filter such things out ahead of time, avoiding the inevitable
     * exception.
     *
     * @param t
     * @return
     */
    private static boolean filterOutMonths(String t) {

        String raw = t.toLowerCase();
        String t1 = raw.substring(2, 5);
        if (ignoreTemporalTokens.contains(t1)) {
            return true;
        }
        t1 = raw.substring(1, 4);
        return ignoreTemporalTokens.contains(t1);
    }

}
