/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
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
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//   OpenSextant XCoord
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */
package org.opensextant.extractors.xcoord;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.util.TextUtils;
import static org.opensextant.extraction.MatcherUtils.reduceMatches;

/**
 * Use this XCoord class for both test and development of patterns, as well as
 * to extract coordinates at runtime.
 *
 * @author ubaldino
 */
public class XCoord extends AbstractFlexPat {

    /**
     * Reserved. This is a bit mask for caller to use. DEFAULTS: (a) enable all
     * false-positive filters for coordinate types; (b) extract context around
     * coordinate.
     */
    public static long RUNTIME_FLAGS = XConstants.FLAG_ALL_FILTERS | XConstants.FLAG_EXTRACT_CONTEXT;
    protected static final String DEFAULT_XCOORD_CFG = "/geocoord_patterns.cfg";

    /**
     * Debugging constructor -- if debugmode = True, enable debugging else if
     * log4j debug mode is enabled, respect that.
     *
     * @param debugmode
     */
    public XCoord(boolean debugmode) {
        super(debugmode);
        this.patterns_file = DEFAULT_XCOORD_CFG;
    }

    /**
     * Default constructor, debugging off.
     */
    public XCoord() {
        this(false);
    }

    /**
     * Extractor interface: getName
     *
     * @return name of extractor
     */
    @Override
    public String getName() {
        return "XCoord";
    }

    @Override
    protected RegexPatternManager createPatternManager(InputStream s, String n) throws IOException {
        return new PatternManager(s, n);
    }

    /**
     * Support the standard Extractor interface. This provides access to the
     * most common extraction;
     *
     * @param input
     *              text input
     * @return the list of matches
     */
    @Override
    public List<TextMatch> extract(TextInput input) {
        TextMatchResult results = extract_coordinates(input.buffer, input.id);

        return results.matches;
    }

    /**
     * Support the standard Extractor interface. This provides access to the
     * most common extraction;
     *
     * @param input_buf
     *                  text
     * @return the list of matches
     */
    @Override
    public List<TextMatch> extract(String input_buf) {
        TextMatchResult results = extract_coordinates(input_buf, Extractor.NO_DOC_ID);

        return results.matches;
    }

    public static void setStrictMode(boolean b){
        if (b) {
            XCoord.RUNTIME_FLAGS |= XConstants.MGRS_STRICT_ON;
        } else{
            XCoord.RUNTIME_FLAGS &= ~XConstants.MGRS_STRICT_ON;
        }
    }

    public static boolean getStrictMode(){
        return (XCoord.RUNTIME_FLAGS & XConstants.MGRS_STRICT_ON) > 0;
    }

    /**
     *
     */
    @Override
    public void enableAll() {
        match_DD(true);
        match_DMS(true);
        match_DM(true);
        match_UTM(true);
        match_MGRS(true);
    }

    /**
     *
     */
    @Override
    public void disableAll() {
        super.disableAll(); // Disable unsupported patterns.
        match_DD(false);
        match_DMS(false);
        match_DM(false);
        match_UTM(false);
        match_MGRS(false);
    }

    /**
     * Enable matching of DMS patterns
     *
     * @param flag
     *             on/off
     */
    public void match_DMS(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.DMS_PATTERN, flag);
    }

    /**
     * Enable matching of DM patterns
     *
     * @param flag
     *             on/off
     */
    public void match_DM(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.DM_PATTERN, flag);
    }

    /**
     * Enable matching of DD patterns
     *
     * @param flag
     *             on/off
     */
    public void match_DD(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.DD_PATTERN, flag);
    }

    /**
     * Enable matching of MGRS patterns
     *
     * @param flag
     *             on/off
     */
    public void match_MGRS(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.MGRS_PATTERN, flag);
    }

    /**
     * Enable matching of UTM patterns
     *
     * @param flag
     *             on/off
     */
    public void match_UTM(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.UTM_PATTERN, flag);
    }

    /**
     * Assess all enabled patterns against the given text. Resulting TextMatch
     * objects carry both the original text ID and their own match ID
     *
     * @param text
     *                text to match against
     * @param text_id
     *                identifier for text.
     * @return
     */
    public TextMatchResult extract_coordinates(String text, String text_id) {
        return extract_coordinates(text, text_id, XConstants.ALL_PATTERNS);
    }

    /**
     * Strictly internal heuristic.
     * ABC&lt;coord&gt;123 -- coordinate pattern found buried in alpha-numeric text.
     *
     * @param buf
     * @param offset
     * @return
     */
    private boolean filterOutContext(String buf, int offset) {
        // Filter for pre-match.
        if (offset == 0) {
            // Do nothing.
            return false;
        }

        // Character preceeding this offset is Alphanumeric.
        // In that context, this is likely a false positive.
        //
        char c1 = buf.charAt(offset);
        if (Character.isWhitespace(c1)) {
            // word break; -- Pattern must have matched word boundary or optional WS.
            return false;
        }
        char c2 = buf.charAt(offset - 1);
        return Character.isDigit(c2) || Character.isLetter(c2);
    }

    /**
     * Limit the extraction to a particular family of coordinates. Diagnostic
     * messages appear in TextMatchResultSet only when debug = ON.
     *
     * @param text
     *                text to match
     * @param text_id
     *                id for text
     * @param family
     *                pattern family or XConstants.ALL_PATTERNS
     * @return TextMatchResultSet result set. If input is null, result set is
     *         null
     */
    public TextMatchResult extract_coordinates(String text, String text_id, int family) {

        if (text == null) {
            return null;
        }

        int bufsize = text.length();

        TextMatchResult results = new TextMatchResult();
        results.result_id = text_id;
        results.matches = new ArrayList<>();

        int patternsComplete = 0;
        int found = 0;
        for (RegexPattern repat : patterns.get_patterns()) {

            log.debug("pattern={}", repat.id);

            if (!repat.enabled) {
                log.debug("CFG pattern={} not enabled", repat.id);
                continue;
            }

            GeocoordPattern pat = (GeocoordPattern) repat;

            // If family specified, the limit to that family. Only one for now.
            // To limit multiple use enable_XXXX()
            if (family != XConstants.ALL_PATTERNS && pat.cce_family_id != family) {
                // log.debug("CFG pattern={} not requested", pat.id);
                continue;
            }

            Matcher match = pat.regex.matcher(text);
            results.evaluated = true;

            while (match.find()) {

                ++found;
                GeocoordMatch coord = new GeocoordMatch(match.start(), match.end());
                coord.setText(match.group());

                // MATCH METHOD aka Pattern ID aka CCE instance
                coord.pattern_id = pat.id;
                coord.cce_family_id = pat.cce_family_id;
                coord.cce_variant = pat.cce_variant;

                if ((RUNTIME_FLAGS & XConstants.CONTEXT_FILTERS_ON) > 0) {
                    if (this.filterOutContext(text, coord.start)) {
                        log.debug("Filtered out noisy match, {} found by {}", coord.getText(), pat.id);
                        continue;
                    }
                }

                // Normalize
                try {
                    GeocoordNormalization.normalize_coordinate(coord, patterns.group_matches(pat, match));
                } catch (NormalizationException normErr) {
                    if (debug) {
                        // Quietly ignore
                        results.message = "Parse error with '" + coord.getText() + "'";
                        log.error(results.message, normErr);
                    }
                    continue;
                }

                // Filter -- trivial filter is to filter out any coord that
                // cannot
                // yield GeocoordMatch.coord_text, the normalized version of the
                // coordinate text match
                //
                if (GeocoordNormalization.filter_out(coord)) {
                    if (debug) {
                        results.message = "Filtered out coordinate pattern=" + pat.id + " value='" + coord.getText()
                                + "'";
                        log.info("Normalization Filter fired, MSG={}", results.message);
                    }
                    continue;
                }

                /*
                 * Caller may want to disable getContext operation here for
                 * short texts.... or for any use case. This is more helpful for
                 * longer texts with many annotations.
                 */
                if ((XCoord.RUNTIME_FLAGS & XConstants.FLAG_EXTRACT_CONTEXT) > 0) {
                    // returns indices for two windows before and after match
                    int[] slices = TextUtils.get_text_window(coord.start, coord.getLength(), bufsize, match_width);

                    // This sets the context window before/after.
                    //
                    coord.setContext(
                            // left l1 to left l2
                            TextUtils.delete_eol(text.substring(slices[0], slices[1])),
                            // right r1 to r2
                            TextUtils.delete_eol(text.substring(slices[2], slices[3])));
                }

                set_match_id(coord, found);
                results.matches.add(coord);

                // Other Interpretations -- due to possible ambiguities with
                // typos
                // and some formats, other valid interpretations are allowed
                // But they share most of the same metadata about context
                // They will differ as far as normalized coord_text and lat/lon
                //
                if (coord.hasOtherIterpretations()) {
                    // set precision
                    // set pre/post
                    // set id

                    for (GeocoordMatch m2 : coord.getOtherInterpretations()) {
                        // Other interpretations may have different coord text.
                        // String _c = m2.coord_text;
                        m2.copyMetadata(coord);
                        // Preserve coordinate text of interpretation.
                        // m2.coord_text = _c;

                        results.matches.add(m2);
                    }
                }
            }

            patternsComplete++;
        }

        // "pass" is the wrong idea. If no data was found
        // because there was no data, then it still passes.
        //
        results.pass = !results.matches.isEmpty();

        reduceMatches(results.matches);

        return results;
    }
}
