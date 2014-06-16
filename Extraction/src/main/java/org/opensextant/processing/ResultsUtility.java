/**
 * Copyright 2009-2013 The MITRE Corporation.
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
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 *
 */
package org.opensextant.processing;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.opensextant.util.TextUtils;
import org.opensextant.extraction.TextEntity;
import org.opensextant.extraction.TextMatch;

/**
 * Abstract class encapsulating basic results formatter functionality.
 *
 */
public class ResultsUtility {

    // -------------
    public static final String PLACE_ANNOTATION = "PLACE";
    public static final String PLACE_CANDIDATE_ANNOTATION = "placeCandidate";
    public static final String GEOCOORD_ANNOTATION = "geocoord";
    public static final Set<String> GATE_GEOCODE_ANNOTATIONS = new HashSet<String>();

    static {
        // This annot set matches "isLocation(annotType)"
        GATE_GEOCODE_ANNOTATIONS.add(GEOCOORD_ANNOTATION);
        GATE_GEOCODE_ANNOTATIONS.add(PLACE_ANNOTATION);
    }
    /**
     * The default TEXT WIDTH. ~75 chars per line yields 2 lines of text.
     */
    public static int TEXT_WIDTH = 150;

    /**
     * Given the GATE annotation set the context on the TextEntity object.
     */
    public static void setPrePostContextFor(String content, TextEntity t, int offset, int match_size, int doc_size) {
        if (t.getContextAfter() != null) {
            return;
        }

        int[] bounds = TextUtils.get_text_window(offset, match_size, doc_size, TEXT_WIDTH);

        t.setContext(content.substring(bounds[0], bounds[1]), // text before match
                content.substring(bounds[2], bounds[3])); // text after match
    }

    /**
     * Given the GATE annotation set the context on the TextEntity object.
     */
    public static void setContextFor(String content, TextEntity t, int offset, int match_size, int doc_size) {

        if (t.getContext() != null) {
            return;
        }

        int[] bounds = TextUtils.get_text_window(offset, doc_size, TEXT_WIDTH);

        t.setContext(TextUtils.squeeze_whitespace(content.substring(bounds[0], bounds[1]))); // text after match
    }

    /**
     * Testers for TextMatch: isLocation macro
     * @param m
     * @return
     */
    public static boolean isLocation(TextMatch m) {
        if (StringUtils.isBlank(m.getType())) {
            return false;
        }
        String test = m.getType().toLowerCase();
        return "coord".equals(test) || "place".equals(test);
    }

    /**
     * Testers for TextMatch: isDatetime macro
     * @param m
     * @return
     */
    public static boolean isDatetime(TextMatch m) {
        if (StringUtils.isBlank(m.getType())) {
            return false;
        }
        String test = m.getType().toLowerCase();
        return "datetime".equals(test);
    }

    /**
     * Is this a Location annotation type?
     */
    public static boolean isLocation(String a) {
        return GEOCOORD_ANNOTATION.equals(a) || PLACE_ANNOTATION.equals(a);
    }

    /**
     * Is this a Location geocoordinate annotation type?
     */
    public static boolean isCoordinate(String a) {
        return GEOCOORD_ANNOTATION.equals(a);
    }

    /**
     * Is this a Location placename annotation type?
     */
    public static boolean isPlaceName(String a) {
        return PLACE_ANNOTATION.equals(a);
    }

    /**
     * Control floating point accuracy on any results.
     *
     * @return A string representation of a double with a fixed number of digits
     * to the right of the decimal point.
     */
    static final DecimalFormat confFmt = new DecimalFormat("0.000");

    public static String formatConfidence(double d) {
        return confFmt.format(d);
    }
}
