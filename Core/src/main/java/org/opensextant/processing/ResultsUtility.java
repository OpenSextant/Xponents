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
package org.opensextant.processing;

import java.text.DecimalFormat;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.Geocoding;
import org.opensextant.extraction.TextEntity;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;

/**
 * Abstract class encapsulating basic results formatter functionality.
 */
public class ResultsUtility {

    /**
     * The default TEXT WIDTH. ~75 chars per line yields 2 lines of text.
     */
    public static int TEXT_WIDTH = 150;

    /**
     * Given the TextEntity, set the context on that object.
     * The text entity must have span (start, end attributes) set.
     *
     * @param content
     *                 entire text from which entity came from
     * @param t
     *                 the entity
     * @param doc_size
     *                 the doc_size
     */
    public static void setPrePostContextFor(String content, TextEntity t, int doc_size) {
        if (t.getContextAfter() != null) {
            return;
        }

        int[] bounds = TextUtils.get_text_window(t.start, t.getLength(), doc_size, TEXT_WIDTH);

        t.setContext(content.substring(bounds[0], bounds[1]), // text before match
                content.substring(bounds[2], bounds[3])); // text after match
    }

    /**
     * Given the annotation or match, set the context on the TextEntity object.
     * The text entity must have span (start, end attributes) set.
     *
     * @param content
     *                 the content
     * @param t
     *                 the t
     * @param doc_size
     *                 the doc_size
     */
    public static void setContextFor(String content, TextEntity t, int doc_size) {

        if (t.getContext() != null) {
            return;
        }

        int[] bounds = TextUtils.get_text_window(t.start, doc_size, TEXT_WIDTH);

        t.setContext(TextUtils.squeeze_whitespace(content.substring(bounds[0], bounds[1]))); // text after match
    }

    /**
     * Testers for TextMatch: isLocation macro.
     *
     * @param m
     *          the m
     * @return true, if is location
     */
    public static boolean isLocation(TextMatch m) {
        if (m instanceof Geocoding) {
            return true;
        }
        if (StringUtils.isBlank(m.getType())) {
            return false;
        }
        String test = m.getType().toLowerCase();
        return "coord".equals(test) || "place".equals(test);
    }

    /**
     * Testers for TextMatch: isDatetime macro.
     *
     * @param m
     *          the m
     * @return true, if is datetime
     */
    public static boolean isDatetime(TextMatch m) {
        if (StringUtils.isBlank(m.getType())) {
            return false;
        }
        String test = m.getType().toLowerCase();
        return "datetime".equals(test);
    }

    /**
     * Control floating point accuracy on any results.
     *
     * @return A string representation of a double with a fixed number of digits
     *         to the right of the decimal point.
     */
    static final DecimalFormat confFmt = new DecimalFormat("0.000");

    /**
     * Format confidence.
     *
     * @param d
     *          the d
     * @return the string
     */
    public static String formatConfidence(double d) {
        return confFmt.format(d);
    }
}
