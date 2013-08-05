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
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
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
// *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */
package org.opensextant.extractors.xcoord;

import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extraction.TextInput;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.TextMatchResult;
import java.util.*;
import java.util.regex.Matcher;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.util.TextUtils;

import org.slf4j.LoggerFactory;

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
    protected static String DEFAULT_XCOORD_CFG = "/geocoord_regex.cfg";

    /**
     * Debugging constructor -- if debugmode = True, enable debugging else if
     * log4j debug mode is enabled, respect that.
     *
     * @param debugmode
     */
    public XCoord(boolean debugmode) {
        this.patterns_file = DEFAULT_XCOORD_CFG;
        log = LoggerFactory.getLogger(XCoord.class);
        if (debugmode) {
            debug = true;
        } else {
            debug = log.isDebugEnabled();
        }
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
     * @return
     */
    public String getName() {
        return "XCoord";
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
        TextMatchResult results = extract_coordinates(input.buffer, input.id);

        return results.matches;
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
        super.disableAll();  // Disable unsupported patterns.
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
     */
    public void match_DMS(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.DMS_PATTERN, flag);
    }

    /**
     * Enable matching of DM patterns
     *
     * @param flag
     */
    public void match_DM(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.DM_PATTERN, flag);
    }

    /**
     * Enable matching of DD patterns
     *
     * @param flag
     */
    public void match_DD(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.DD_PATTERN, flag);
    }

    /**
     * Enable matching of MGRS patterns
     *
     * @param flag
     */
    public void match_MGRS(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.MGRS_PATTERN, flag);
    }

    /**
     * Enable matching of UTM patterns
     *
     * @param flag
     */
    public void match_UTM(boolean flag) {
        ((PatternManager) patterns).enable_CCE_family(XConstants.UTM_PATTERN, flag);
    }

    /**
     * Assess all enabled patterns against the given text. Resulting TextMatch
     * objects carry both the original text ID and their own match ID
     *
     * @param text
     * @param text_id
     * @return
     */
    public TextMatchResult extract_coordinates(String text, String text_id) {
        return extract_coordinates(text, text_id, XConstants.ALL_PATTERNS);
    }

    /**
     * Limit the extraction to a particular family of coordinates. Diagnostic
     * messages appear in TextMatchResultSet only when debug = ON.
     *
     * @param text
     * @param text_id
     * @param family
     * @return TextMatchResultSet result set. If input is null, result set is
     * null
     */
    public TextMatchResult extract_coordinates(String text, String text_id, int family) {

        if (text == null) {
            return null;
        }

        int bufsize = text.length();

        TextMatchResult results = new TextMatchResult();
        results.result_id = text_id;
        results.matches = new ArrayList<TextMatch>();

        for (RegexPattern repat : patterns.get_patterns()) {

            if (debug) {
                log.debug("pattern=" + repat.id);
            }

            if (!repat.enabled) {
                if (debug) {
                    log.debug("CFG pattern=" + repat.id + " not enabled");
                }
                continue;
            }

            GeocoordPattern pat = (GeocoordPattern) repat;

            // If family specified, the limit to that family.  Only one for now.
            // To limit multiple use enable_XXXX()
            if (family != XConstants.ALL_PATTERNS
                    && pat.cce_family_id != family) {

                if (debug) {
                    log.debug("CFG pattern=" + pat.id + " not requested.");
                }
                continue;
            }


            Matcher match = pat.regex.matcher(text);
            results.evaluated = true;

            while (match.find()) {

                GeocoordMatch coord = new GeocoordMatch();

                // MATCH METHOD aka Pattern ID aka CCE instance
                coord.pattern_id = pat.id;
                coord.cce_family_id = pat.cce_family_id;
                coord.cce_variant = pat.cce_variant;

                coord.start = match.start();
                coord.end = match.end();
                coord.setText(match.group());

                // Normalize
                try {
                    GeocoordNormalization.normalize_coordinate(coord, patterns.group_map(pat, match));
                } catch (NormalizationException normErr) {
                    if (debug) {
                        // Quietly ignore
                        results.message = "Parse error with '" + coord.getText() + "'";
                        log.error(results.message, normErr);
                    }
                    continue;
                }

                // Filter -- trivial filter is to filter out any coord that cannot
                // yield GeocoordMatch.coord_text, the normalized version of the coordinate text match
                //
                if (GeocoordNormalization.filter_out(coord)) {
                    if (debug) {
                        results.message = "Filtered out coordinate pattern=" + pat.id + " value='"
                                + coord.getText() + "'";
                        log.debug("EX " + results.message);
                    }
                    continue;
                }

                // Establish precision
                GeocoordNormalization.set_precision(coord);

                /**
                 * Caller may want to disable getContext operation here for
                 * short texts.... or for any use case. This is more helpful for
                 * longer texts with many annotations.
                 */
                if ((XCoord.RUNTIME_FLAGS & XConstants.FLAG_EXTRACT_CONTEXT) > 0) {
                    // returns indices for two  windows before and after match
                    int[] slices = TextUtils.get_text_window(
                            coord.start,
                            coord.match_length(),
                            bufsize, match_width);

                    // This sets the context window before/after.
                    //
                    coord.setContext(
                            //                               left l1 to left l2
                            TextUtils.delete_eol(text.substring(slices[0], slices[1])),
                            //                                right r1 to r2
                            TextUtils.delete_eol(text.substring(slices[2], slices[3])));
                }
                // coord.createID();
                set_match_id(coord);

                results.matches.add(coord);

                // Other Interpretations -- due to possible ambiguities with typos
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
                        //String _c = m2.coord_text;
                        m2.copyMetadata(coord);
                        // Preserve coordinate text of interpretation.
                        //m2.coord_text = _c;

                        results.matches.add(m2);
                    }
                }
            }
        }

        // "pass" is the wrong idea.  If no data was found
        // because there was no data, then it still passes.
        //
        results.pass = !results.matches.isEmpty();

        PatternManager.reduce_matches(results.matches);

        return results;
    }

    /**
     * Run a simple test.
     *
     * @param args only one argument accepted: a text file input.
     */
    public static void main(String[] args) {
        boolean debug = true;

        // Use default config file.
        XCoord xc = new XCoord(debug);
        XCoord.RUNTIME_FLAGS = XConstants.FLAG_EXTRACT_CONTEXT;

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("XCoord", args, "af:t:u:");

        try {
            //xc.configure( "file:./etc/test_regex.cfg"); // default
            xc.configure(); // default
            TestScript test = new TestScript(xc);

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                    case 'f':
                        System.out.println("\tSYSTEM TESTS=======\n" + opts.getOptarg());
                        test.test(opts.getOptarg());
                        test.fileTruth("test/Coord_Patterns_Truth.csv");
                        break;

                    case 't':
                        System.out.println("\tUSER TEST\n=======\n" + opts.getOptarg());
                        test.fileTestByLines(opts.getOptarg());
                        break;

                    case 'u':
                        System.out.println("\tUSER FILE\n=======\n" + opts.getOptarg());
                        test.fileTests(opts.getOptarg());
                        break;

                    case 'a':
                        System.out.println("\tAdhoc Tests\n=======\n" + opts.getOptarg());
                        test.focusedTests();
                        break;


                    default:
                }
            }
        } catch (Exception xerr) {
            xerr.printStackTrace();
        }
    }
}
