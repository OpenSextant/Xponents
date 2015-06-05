/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *               http://www.apache.org/licenses/LICENSE-2.0
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
 * Continue contributions:
 *    Copyright 2013-2015 The MITRE Corporation.
 */
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
//\ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//\ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
// \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//  \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//          \ \_\
//           \/_/
//
// OpenSextant PoLi - Patterns extractor
//*  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//*/
package org.opensextant.extractors.poli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.util.TextUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class PatternsOfLife extends AbstractFlexPat {

    public static final String DEFAULT_POLI_CFG = "/poli_patterns.cfg";

    public PatternsOfLife(boolean debugmode) {
        patterns_file = DEFAULT_POLI_CFG;
        log = LoggerFactory.getLogger(getClass());

        if (debugmode) {
            debug = true;
        } else {
            debug = log.isDebugEnabled();
        }
    }

    /**
     * Default constructor, debugging off.
     */
    public PatternsOfLife() {
        this(false);
    }

    /**
     * Extractor interface: extractors are responsible for cleaning up after
     * themselves.
     */
    @Override
    public void cleanup() {
    }

    /**
     * Extractor interface: getName
     *
     * @return
     */
    @Override
    public String getName() {
        return "PoLi";
    }

    @Override
    protected RegexPatternManager createPatternManager() throws java.net.MalformedURLException {
        if (this.patterns_url != null) {
            return new PoliPatternManager(patterns_url);
        } else {
            return new PoliPatternManager(new File(patterns_file));
        }
    }

    /**
     * Support the standard Extractor interface. This provides access to the
     * most common extraction; For PoLi extraction, you would process ALL
     * patterns in your configuration file, or if you enable only certain
     * patterns -- those enabled at the time of this call would be executed.
     * extract_patterns( family = null ) implies ALL patterns.
     */
    @Override
    public List<TextMatch> extract(TextInput input) {
        TextMatchResult results = extract_patterns(input.buffer, input.id, null);
        return results.matches;
    }

    @Override
    public List<TextMatch> extract(String input_buf) {
        TextMatchResult results = extract_patterns(input_buf, NO_DOC_ID, null);
        return results.matches;
    }

    /**
     * Extract patterns of a certain family from a block of text.
     *
     * @param text
     *            - data to process
     * @param text_id
     *            - identifier for the data
     * @param family
     *            - optional filter; to reuse the same PatManager but extract
     *            certain patterns only.
     *
     * @return PoliResult
     */
    public TextMatchResult extract_patterns(String text, String text_id, String family) {

        TextMatchResult results = new TextMatchResult();
        results.result_id = text_id;
        results.matches = new ArrayList<TextMatch>();
        int bufsize = text.length();

        PoliMatch poliMatch = null;

        int found=0;
        int patternsComplete = 0;
        for (RegexPattern repat : patterns.get_patterns()) {
            if (!repat.enabled) {
                continue;
            }

            if (family != null && !repat.id.startsWith(family)) {
                continue;
            }

            Matcher match = repat.regex.matcher(text);
            results.evaluated = true;
            while (match.find()) {

                ++found;
                Map<String, String> fields = patterns.group_map(repat, match);

                if (repat.match_class == null) {
                    poliMatch = new PoliMatch(fields, match.group());
                } else {
                    try {
                        poliMatch = (PoliMatch) repat.match_class.newInstance();
                        poliMatch.setText(match.group());
                        poliMatch.setGroups(fields);
                    } catch (InstantiationException classErr1) {
                        poliMatch = null;
                        log.error("Could not create... ", classErr1);
                    } catch (IllegalAccessException classErr2) {
                        poliMatch = null;
                        log.error("Could not create... ", classErr2);
                    }

                }

                if (poliMatch == null) {
                    // This would have been thrown at init.
                    log.error("Could not find pattern family for " + repat.id);
                    continue;
                }

                poliMatch.setType(repat.family);
                poliMatch.pattern_id = repat.id;
                poliMatch.start = match.start();
                poliMatch.end = match.end();

                poliMatch.normalize();

                // Filter -- trivial filter is to filter out any coord that
                // cannot
                // TODO: Assess filters?

                // returns indices for window around text match
                int[] slices = TextUtils.get_text_window(poliMatch.start, bufsize, match_width);

                // left l1 to left l2
                poliMatch.setContext(TextUtils.delete_eol(text.substring(slices[0], slices[1])));
                set_match_id(poliMatch, found);

                results.matches.add(poliMatch);

            }
            patternsComplete++;
            updateProgress(patternsComplete / (double) patterns.get_patterns().size() + 1);
        }

        results.pass = !results.matches.isEmpty();
        PoliPatternManager.reduce_matches(results.matches);

        return results;
    }
}
