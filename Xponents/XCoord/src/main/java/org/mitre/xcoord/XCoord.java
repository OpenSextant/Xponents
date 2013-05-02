/**
 *
 *  Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
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
package org.mitre.xcoord;

import java.util.*;
import java.util.regex.Matcher;
import org.mitre.flexpat.*;
import org.mitre.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use this XCoord class for both test and development of patterns, as well as
 * to extract coordinates at runtime.
 *
 * @author ubaldino
 */
public class XCoord {

    String patterns_file = "/geocoord_regex.cfg";
    PatternManager patterns = null;
    static Logger log = LoggerFactory.getLogger(XCoord.class);
    private boolean debug = false;
    private final TextUtils utility = new TextUtils();
    /** Reserved.
     * This is a bit mask for caller to use. 
     * DEFAULTS:  (a) enable all false-positive filters for coordinate types;
     *   (b) extract context around coordinate.
     */
    public static long RUNTIME_FLAGS = XConstants.FLAG_ALL_FILTERS | XConstants.FLAG_EXTRACT_CONTEXT;

    /**
     * Debugging constructor -- if debugmode = True, enable debugging else if
     * log4j debug mode is enabled, respect that.
     *
     * @param debugmode
     */
    public XCoord(boolean debugmode) {
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
     * Configure with the default coordinate patterns file, geocoord_regex.cfg
     * in CLASSPATH
     *
     * @throws XCoordException
     */
    public void configure() throws XCoordException {
        configure(getClass().getResource(patterns_file)); // default
    }

    /**
     * Configure using a particular pattern file.
     *
     * @param patfile
     * @throws XCoordException
     */
    public void configure(String patfile) throws XCoordException {
        if (patfile != null) {
            patterns_file = patfile;
        }

        try {
            patterns = new PatternManager(patterns_file.trim());
            patterns.testing = debug;
            patterns.initialize();
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file FILE=" + patterns_file;
            log.error(msg, loaderr);
            throw new XCoordException(msg, loaderr);
        }
    }

    /**
     * Configure using a URL pointer to the pattern file.
     *
     * @param patfile
     * @throws XCoordException
     */
    public void configure(java.net.URL patfile) throws XCoordException {

        try {
            patterns = new PatternManager(patfile);
            patterns.testing = debug;
            patterns.initialize();
            patterns_file = patfile.getFile();
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file URL=" + patfile;
            log.error(msg, loaderr);
            throw new XCoordException(msg, loaderr);
        }
    }

    /**
     *
     */
    public void matchAll() {
        match_DD(true);
        match_DMS(true);
        match_DM(true);
        match_UTM(true);
        match_MGRS(true);
    }

    /**
     * Enable matching of DMS patterns
     *
     * @param flag
     */
    public void match_DMS(boolean flag) {
        patterns.enable_CCE_family(XConstants.DMS_PATTERN, flag);
    }

    /**
     * Enable matching of DM patterns
     *
     * @param flag
     */
    public void match_DM(boolean flag) {
        patterns.enable_CCE_family(XConstants.DM_PATTERN, flag);
    }

    /**
     * Enable matching of DD patterns
     *
     * @param flag
     */
    public void match_DD(boolean flag) {
        patterns.enable_CCE_family(XConstants.DD_PATTERN, flag);
    }

    /**
     * Enable matching of MGRS patterns
     *
     * @param flag
     */
    public void match_MGRS(boolean flag) {
        patterns.enable_CCE_family(XConstants.MGRS_PATTERN, flag);
    }

    /**
     * Enable matching of UTM patterns
     *
     * @param flag
     */
    public void match_UTM(boolean flag) {
        patterns.enable_CCE_family(XConstants.UTM_PATTERN, flag);
    }
    /**
     * CHARS. SHP DBF limit is 255 bytes, so SHP file outputters should assess
     * at that time how/when to curtail match width. The max pre/post text seen
     * useful has typically been about 200-250 characters.
     */
    private int MATCH_WIDTH = 250;

    /**
     * Match Width is the text buffer before and after a TextMatch. Match
     * buffers are used to create a match ID
     *
     * @see TextMatch.createID
     * @param w
     */
    public void setMatchWidth(int w) {
        MATCH_WIDTH = w;
    }

    /**
     * Assess all enabled patterns against the given text. Resulting TextMatch
     * objects carry both the original text ID and their own match ID
     *
     * @param text
     * @param text_id
     * @return
     */
    public TextMatchResultSet extract_coordinates(String text, String text_id) {
        return extract_coordinates(text, text_id, XConstants.ALL_PATTERNS);
    }

    /**
     * Limit the extraction to a particular family of coordinates.
     *
     * @param text
     * @param text_id
     * @param family
     * @return TextMatchResultSet result set.  If input is null, result set is null
     */
    public TextMatchResultSet extract_coordinates(String text, String text_id, int family) {

        if (text == null) {
            return null;
        }

        int bufsize = text.length();

        TextMatchResultSet results = new TextMatchResultSet();
        results.result_id = text_id;
        results.matches = new ArrayList<>();

        for (RegexPattern repat : patterns.get_patterns()) {

            if (debug) {
                log.debug("pattern=" + repat.id);
            }

            if (!repat.enabled) {
                //results.message = "pattern=" + pat.id + " not enabled. ";
                String message = "pattern=" + repat.id + " not enabled. ";
                if (debug) {
                    log.debug("CFG " + message);
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
                //coord.matcher = match.

                // Normalize
                try {
                    patterns.normalize_coordinate(coord, patterns.group_map(pat, match));
                } catch (XCoordException pex) {
                    // Quietly ignore
                    results.message = "Parse error with '" + coord.getText() + "'";
                    if (debug) {
                        log.error("EX  Parsing error TEXT=" + coord.getText(), pex);
                    }
                    continue;
                }

                // Filter -- trivial filter is to filter out any coord that cannot 
                // yield GeocoordMatch.coord_text, the normalized version of the coordinate text match
                //
                if (patterns.filter_out(coord)) {
                    results.message = "Filtered out coordinate pattern=" + pat.id + "'"
                            + coord.getText() + "'";
                    if (debug) {
                        log.debug("EX " + results.message);
                    }
                    continue;
                }

                // Establish precision
                patterns.set_precision(coord);

                /** Caller may want to disable getContext operation here for short texts....
                 * or for any use case.   This is more helpful for longer texts with many annotations.
                 */
                if ((XCoord.RUNTIME_FLAGS & XConstants.FLAG_EXTRACT_CONTEXT) > 0) {
                    // returns indices for two  windows before and after match
                    int[] slices = TextUtils.get_text_window(
                            (int) coord.start,
                            coord.match_length(),
                            bufsize, MATCH_WIDTH);

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

        results.pass = !results.matches.isEmpty();

        PatternManager.reduce_matches(results.matches);

        return results;
    }

    /**
     * Assign an identifier to each Text Match found. This is an MD5 of the
     * coord in-situ.  If context is provided, it is used to generate the identity
     * 
     * otherwise make use of just pattern ID + text value.
     *
     * @param coord
     */
    protected void set_match_id(TextMatch coord) {
        if (coord.getContextBefore() == null) {
            coord.match_id = utility.genTextID(coord.pattern_id + coord.getText());
        } else {
            coord.match_id = utility.genTextID(
                    coord.getContextBefore()
                    + coord.getText()
                    + coord.getContextAfter());
        }
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
                        test.fileTruth("src/test/resources/Coord_Patterns_Truth.csv");
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
        } catch (XCoordException xerr) {
            xerr.printStackTrace();
        }
    }
}
