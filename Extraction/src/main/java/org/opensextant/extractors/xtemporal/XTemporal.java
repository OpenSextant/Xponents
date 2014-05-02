/**
 *
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
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
 * // // _____ ____ __ __ ///\ __`\ /\ _`\ /\ \__ /\ \__ //\ \ \/\ \ _____ __
 * ___ \ \,\L\_\ __ __ _\ \ ,_\ __ ___ \ \ ,_\ // \ \ \ \ \ /\ '__`\ /'__`\ /' _
 * `\ \/_\__ \ /'__`\/\ \/'\\ \ \/ /'__`\ /' _ `\\ \ \/ // \ \ \_\ \\ \ \L\ \/\
 * __/ /\ \/\ \ /\ \L\ \ /\ __/\/> </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_ // \
 * \_____\\ \ ,__/\ \____\\ \_\ \_\ \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\
 * \_\ \_\\ \__\ // \/_____/ \ \ \/ \/____/ \/_/\/_/ \/_____/ \/____/\//\/_/
 * \/__/ \/__/\/_/ \/_/\/_/ \/__/ // \ \_\ // \/_/ // // OpenSextant XTemporal
 * // *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
 * //
 */
package org.opensextant.extractors.xtemporal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;

import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author ubaldino
 */
public class XTemporal extends AbstractFlexPat {

    public final static String DEFAULT_XTEMP_CFG = "/datetime_patterns.cfg";

    /** Application constants -- note the notion of TODAY is relative to the caller's notion of TODAY.
     * If you are processing data from the past but have a sense of what TODAY is, then when found dates fall on either side of that
     * they will be relative PAST and relative FUTURE. 
     */
    public static Date TODAY = new Date();
    public static long TODAY_EPOCH = TODAY.getTime();

    public final static int JAVA_0_DATE_YEAR = 1970;
    public final static long ONE_YEAR_MS = 365L * 24L * 3600L * 1000L;

    /**
     * Extractor interface: getName
     * 
     * @return
     */
    public String getName() {
        return "XTemporal";
    }

    /**
     * Extractor interface: extractors are responsible for cleaning up after themselves.
     */
    public void cleanup() {
    }

    /**
     * 
     * @param debugmode
     */
    public XTemporal(boolean debugmode) {
        this.patterns_file = DEFAULT_XTEMP_CFG;
        log = LoggerFactory.getLogger(getClass());

        if (debugmode) {
            debug = true;
        } else {
            debug = log.isDebugEnabled();
        }
    }

    /**
     * non-debugging ctor;
     */
    public XTemporal() {
        this(false);
    }

    @Override
    protected RegexPatternManager createPatternManager() throws java.net.MalformedURLException {
        if (this.patterns_url != null) {
            return new PatternManager(patterns_url);
        } else {
            return new PatternManager(patterns_file);
        }
    }

    /**
     * Support the standard Extractor interface. This provides access to the
     * most common extraction;
     */
    @Override
    public List<TextMatch> extract(TextInput input) {
        TextMatchResult results = extract_dates(input.buffer, input.id);
        return results.matches;
    }

    /**
    * Support the standard Extractor interface. This provides access to the
    * most common extraction;
    */
    @Override
    public List<TextMatch> extract(String input_buf) {
        TextMatchResult results = extract_dates(input_buf, NO_DOC_ID);
        return results.matches;
    }

    /**
     * A direct call to extract dates; which is useful for diagnostics and
     * development/testing.
     * 
     * @param text
     * @param text_id
     * @return
     */
    public TextMatchResult extract_dates(String text, String text_id) {

        TextMatchResult results = new TextMatchResult();
        results.matches = new ArrayList<TextMatch>();
        results.result_id = text_id;

        int found = 0;
        int patternsComplete = 0;
        for (RegexPattern pat : patterns.get_patterns()) {

            log.debug("pattern={}", pat.id);

            if (!pat.enabled) {
                // results.message = "pattern=" + pat.id + " not enabled. ";
                log.debug("CFG pattern={} not enabled.", pat.id);
                continue;
            }

            Matcher match = pat.regex.matcher(text);
            results.evaluated = true;

            while (match.find()) {

                ++found;
                DateMatch dt = new DateMatch();

                dt.pattern_id = pat.id;
                dt.start = match.start();
                dt.end = match.end();
                dt.setText(match.group());

                try {

                    DateNormalization.normalize_date(patterns.group_map(pat, match), dt);
                    if (dt.datenorm == null) {
                        continue;
                    }

                    dt.datenorm_text = DateNormalization.format_date(dt.datenorm);

                    // Flags worth setting here.
                    dt.isDistantPast = isDistantPast(dt.datenorm.getTime());
                    dt.isFuture = isFuture(dt.datenorm.getTime());
                    set_match_id(dt, found);

                    results.pass = true;

                } catch (Exception err) {
                    // Not a date.
                    results.pass = false;
                    continue;
                }

                results.matches.add(dt);
            }

            patternsComplete++;
            updateProgress(patternsComplete / (double) patterns.get_patterns().size() + 1);
        }

        results.pass = !results.matches.isEmpty();

        PatternManager.reduce_matches(results.matches);

        return results;
    }

    /**
     * 
     * @param flag
     */
    public void match_DateTime(boolean flag) {
        ((PatternManager) patterns).enable_pattern_family(XTConstants.DATETIME_FAMILY, flag);
    }

    /**
     * 
     * @param flag
     */
    public void match_MonDayYear(boolean flag) {
        ((PatternManager) patterns).enable_pattern_family(XTConstants.MDY_FAMILY, flag);
    }

    /**
     * Optionally reset your context... what is TODAY with respect to your data?
     * @param d
     */
    public void setToday(Date d) {
        if (d != null) {
            TODAY = d;
            TODAY_EPOCH = TODAY.getTime();
        }
    }

    /**
    ** Application thresholds -- chosen by the user 
     * @param y
     */
    public void setDistantPastYear(int y) {
        DISTANT_PAST_YEAR = y;
        DISTANT_PAST_THRESHOLD = (DISTANT_PAST_YEAR - JAVA_0_DATE_YEAR) * ONE_YEAR_MS;
    }

    /** This is a very subjective topic -- if a date is distant past or not is relative to your context
     * If you use the DateMatch flags for distant past and future, then you are likely going to want to set Today and Distant Past Year
     */
    private static int DISTANT_PAST_YEAR = 1950;
    private static long DISTANT_PAST_THRESHOLD = (DISTANT_PAST_YEAR - JAVA_0_DATE_YEAR) * ONE_YEAR_MS;

    /**
     * Given the set MAX_DATE_CUTOFF_YEAR, determine if the date epoch is earlier than this.
     * @param epoch
     * @return
     */
    public boolean isFuture(long epoch) {
        return (epoch > TODAY_EPOCH);
    }

    public boolean isFuture(Date dt) {
        if (dt == null) {
            return true;
        }
        return isFuture(dt.getTime());
    }

    /**
     * 
     * @param epoch
     * @return
     */
    public boolean isDistantPast(long epoch) {
        return (epoch < DISTANT_PAST_THRESHOLD);
    }

    public boolean isDistantPast(Date dt) {
        if (dt == null) {
            return true;
        }
        return isDistantPast(dt.getTime());
    }

}
