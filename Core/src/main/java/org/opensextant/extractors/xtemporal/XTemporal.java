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
///~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
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
// OpenSextant XTemporal - Date/Time extractor
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */
package org.opensextant.extractors.xtemporal;

import static org.opensextant.extraction.MatcherUtils.reduceMatches;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;

import org.joda.time.DateTime;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;

/**
 * Date/Time pattern extractor -- detects, parses, normalizes dates. Found
 * date/time are DateMatch
 * (TextMatch) objects
 *
 * @author ubaldino
 */
public class XTemporal extends AbstractFlexPat {

    /** The Constant DEFAULT_XTEMP_CFG. */
    public static final String DEFAULT_XTEMP_CFG = "/datetime_patterns.cfg";

    /**
     * Application constants -- note the notion of TODAY is relative to the caller's
     * notion of TODAY. If
     * you are processing data from the past but have a sense of what TODAY is, then
     * when found dates
     * fall on either side of that they will be relative PAST and relative FUTURE.
     */
    public Date TODAY = new Date();

    /** The today epoch. */
    public long TODAY_EPOCH = TODAY.getTime();

    /** The Constant JAVA_0_DATE_YEAR. */
    public static final int JAVA_0_DATE_YEAR = 1970;

    /** The Constant ONE_YEAR_MS. */
    public static final long ONE_YEAR_MS = 365L * 24L * 3600L * 1000L;

    /**
     * Extractor interface: getName.
     *
     * @return extractor name
     */
    @Override
    public String getName() {
        return "XTemporal";
    }


    /**
     * XTemporal ctor
     *
     * @param debugmode true if debugging
     */
    public XTemporal(boolean debugmode) {
        super(debugmode);
        this.patterns_file = DEFAULT_XTEMP_CFG;
    }

    /**
     * non-debugging ctor;.
     */
    public XTemporal() {
        this(false);
    }

    @Override
    protected RegexPatternManager createPatternManager(InputStream strm, String name) throws IOException {
        patterns_file = name;
        PatternManager mgr = new PatternManager(strm, patterns_file);
        mgr.testing = debug;
        return mgr;
    }

    /**
     * Support the standard Extractor interface. This provides access to the most
     * common extraction;
     *
     * @param input text
     * @return list of TextMatch
     */
    @Override
    public List<TextMatch> extract(TextInput input) {
        TextMatchResult results = extract_dates(input.buffer, input.id);
        return results.matches;
    }

    /**
     * Support the standard Extractor interface. This provides access to the most
     * common extraction;
     *
     * @param input_buf text
     * @return list of TextMatch
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
     * @param text    text
     * @param text_id text ID
     * @return TextMatchResult, a wrapper around a list of TextMatch
     */
    public TextMatchResult extract_dates(String text, String text_id) {

        TextMatchResult results = new TextMatchResult();
        results.matches = new ArrayList<>();
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
                DateMatch dt = new DateMatch(match.start(), match.end());
                dt.setText(match.group());
                dt.pattern_id = pat.id;
                dt.patternFields = patterns.group_map(pat, match);

                try {

                    DateNormalization.normalizeDate(dt.patternFields, dt);
                    if (dt.datenorm == null) {
                        continue;
                    }
                    if ("YMD".equalsIgnoreCase(pat.family)) {
                        if (this.isDistantPastYMD(dt.datenorm)) {
                            continue;
                        }
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
        }

        results.pass = !results.matches.isEmpty();

        /*
         * Reduce duplicates, then mark them as filtered out.
         */
        reduceMatches(results.matches);
        for (TextMatch dt : results.matches) {
            if (dt.is_duplicate || dt.is_submatch) {
                dt.setFilteredOut(true);
            }
        }

        return results;
    }

    /**
     * enable date time patterns
     *
     * @param flag true if enabling date/time matching
     */
    public void match_DateTime(boolean flag) {
        ((PatternManager) patterns).enable_pattern_family(XTConstants.DATETIME_FAMILY, flag);
    }

    /**
     * enable mon day year patterns.
     *
     * @param flag true if enabling MonthDayYear family
     */
    public void match_MonDayYear(boolean flag) {
        ((PatternManager) patterns).enable_pattern_family(XTConstants.MDY_FAMILY, flag);
    }

    /**
     * enable day mon year.
     *
     * @param flag the flag
     */
    public void match_DayMonYear(boolean flag) {
        ((PatternManager) patterns).enable_pattern_family(XTConstants.DMY_FAMILY, flag);
    }

    /**
     * Optionally reset your context... what is TODAY with respect to your data?
     *
     * @param d date
     */
    public void setToday(Date d) {
        if (d != null) {
            TODAY = d;
            TODAY_EPOCH = TODAY.getTime();
        }
    }

    /**
     * * Application thresholds -- chosen by the user.
     *
     * @param y 4-digit year
     */
    public static void setDistantPastYear(int y) {
        DISTANT_PAST_YEAR = y;
        DISTANT_PAST_THRESHOLD = (DISTANT_PAST_YEAR - JAVA_0_DATE_YEAR) * ONE_YEAR_MS;
    }

    /**
     * This is a very subjective topic -- if a date is distant past or not is
     * relative to your context
     * If you use the DateMatch flags for distant past and future, then you are
     * likely going to want to
     * set Today and Distant Past Year
     */
    private static int DISTANT_PAST_YEAR = 1950;
    private static long DISTANT_PAST_THRESHOLD = (DISTANT_PAST_YEAR - JAVA_0_DATE_YEAR) * ONE_YEAR_MS;

    private static final int MINIMUM_YEAR_YMD = 1800;
    /* An idea:
     *    DISTANT_PAST_YMD_THRESHOLD = (MINIMUM_YEAR_YMD - JAVA_0_DATE_YEAR) * ONE_YEAR_MS;
     */

    /**
     * Given the set MAX_DATE_CUTOFF_YEAR, determine if the date epoch is earlier
     * than this.
     *
     * @param epoch epoch since 1970-01-01
     * @return true, if is future
     */
    public boolean isFuture(long epoch) {
        return (epoch > TODAY_EPOCH);
    }

    /**
     * Checks if is future.
     *
     * @param dt the dt
     * @return true, if is future
     */
    public boolean isFuture(Date dt) {
        if (dt == null) {
            return true;
        }
        return isFuture(dt.getTime());
    }

    /**
     * Checks if is distant past.
     *
     * @param epoch epoch
     * @return true if past DISTANT_PAST_THRESHOLD
     */
    public boolean isDistantPast(long epoch) {
        return (epoch < DISTANT_PAST_THRESHOLD);
    }

    /**
     * Checks if is distant past.
     *
     * @param dt date
     * @return true, if is distant past
     */
    public boolean isDistantPast(Date dt) {
        if (dt == null) {
            return true;
        }
        return isDistantPast(dt.getTime());
    }

    /**
     * if a date is too far in past to likley be a date of the format YYYY-MM-DD.
     *
     * @param dt date
     * @return true if date is distant
     */
    public boolean isDistantPastYMD(Date dt) {
        // return dt.getTime() < DISTANT_PAST_YMD_THRESHOLD;
        DateTime jodadt = new DateTime(dt);
        return jodadt.getYear() < MINIMUM_YEAR_YMD;
    }
}
