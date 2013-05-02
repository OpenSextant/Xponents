/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
/** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
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
//   OpenSextant XTemporal
// *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
 */
package org.mitre.xtemporal;

import java.util.*;
import java.util.regex.Matcher;
import org.mitre.flexpat.PatternTestCase;
import org.mitre.flexpat.RegexPattern;
import org.mitre.flexpat.TextMatch;
import org.mitre.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ubaldino
 */
public class XTemporal {

    String patterns_file = "/datetime_patterns.cfg";
    org.mitre.xtemporal.PatternManager patterns = null;
    static Logger log = LoggerFactory.getLogger(XTemporal.class);
    private boolean debug = false;
    private TextUtils utility = new TextUtils();

    /**
     *
     * @param debugmode
     */
    public XTemporal(boolean debugmode) {
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

    /**
     *
     * @throws XTempException
     */
    public void configure() throws XTempException {
        configure(getClass().getResource(patterns_file)); // default
    }

    /**
     *
     * @param patfile
     * @throws XTempException
     */
    public void configure(String patfile) throws XTempException {
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
            throw new XTempException(msg, loaderr);
        }
    }

    /**
     *
     * @param patfile
     * @throws XTempException
     */
    public void configure(java.net.URL patfile) throws XTempException {

        try {
            patterns = new PatternManager(patfile);
            patterns.testing = debug;
            patterns.initialize();
            patterns_file = patfile.getFile();
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file URL=" + patfile;
            log.error(msg, loaderr);
            throw new XTempException(msg, loaderr);
        }
    }
    /**
     * CHARS. SHP DBF limit is 255 bytes, so SHP file outputters should assess
     * at that time how/when to curtail match width. The max pre/post text seen
     * useful has typically been about 200-250 characters.
     */
    private int MATCH_WIDTH = 250;

    /**
     *
     * @param w
     */
    public void setMatchWidth(int w) {
        MATCH_WIDTH = w;
    }

    /**
     *
     * @param text
     * @param text_id
     * @return
     */
    public XTempResult extract_dates(String text, String text_id) {

        XTempResult results = new XTempResult();
        results.matches = new ArrayList<TextMatch>();
        results.result_id = text_id;

        for (RegexPattern pat : patterns.get_patterns()) {

            if (debug) {
                log.debug("pattern=" + pat.id);
            }

            if (!pat.enabled) {
                //results.message = "pattern=" + pat.id + " not enabled. ";
                String message = "pattern=" + pat.id + " not enabled. ";
                if (debug) {
                    log.debug("CFG " + message);
                }
                continue;
            }


            Matcher match = pat.regex.matcher(text);
            results.evaluated = true;

            while (match.find()) {

                DateMatch dt = new DateMatch();

                dt.pattern_id = pat.id;
                dt.start = match.start();
                dt.end = match.end();
                dt.setText(match.group());

                try {

                    dt.datenorm = DateNormalization.normalize_date(patterns.group_map(pat, match));
                    if (dt.datenorm == null) {
                        continue;
                    }

                    dt.datenorm_text = DateNormalization.format_date(dt.datenorm);
                    results.pass = true;

                } catch (Exception err) {
                    // Not a date.
                    results.pass = false;
                    continue;
                }


                results.matches.add(dt);
            }

        }

        results.pass = !results.matches.isEmpty();

        PatternManager.reduce_matches(results.matches);

        return results;
    }

    /**
     * Assign an identifier to each Text Match found. This is an MD5 of the
     * coord in-situ.
     *
     * @param dt
     */
    protected void set_match_id(TextMatch dt) {
        dt.match_id = utility.genTextID(dt.getContextBefore() + dt.getText() + dt.getContextAfter());
    }

    /**
     *
     * @param file
     */
    public void test(String file) {
        systemTests();
    }

    /**
     *
     * @param flag
     */
    public void match_DateTime(boolean flag) {
        patterns.enable_pattern_family(XTConstants.DATETIME_FAMILY, flag);
    }

    /**
     *
     * @param flag
     */
    public void match_MonDayYear(boolean flag) {
        patterns.enable_pattern_family(XTConstants.MDY_FAMILY, flag);
    }

    /**
     *
     */
    public void systemTests() {
        log.info("=== SYSTEM TESTS START ===");

        match_MonDayYear(true);
        match_DateTime(true);


        try {
            XTTestUtility tester = new XTTestUtility("./results/xtemp_System.csv");

            for (PatternTestCase tst : patterns.testcases) {
                XTempResult results = extract_dates(tst.text, tst.id);
                results.add_trace("Test Payload: " + tst.text);

                if (!results.evaluated) {
                    continue;
                }

                log.info("=========SYSTEM TEST " + tst.id + " FOUND:" + (results.matches == null ? "NOTHING" : results.matches.size()));
                tester.save_result(results);

            }
            tester.close_report();

        } catch (Exception err) {
            log.error("Not finishing tests", err);
            return;
        }
        log.info("=== SYSTEM TESTS DONE ===");


    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {

        // default test patterns, run test/debug mode.
        XTemporal xdt = new XTemporal(true);
        try {
            xdt.configure();
            xdt.test(args[0]);
        } catch (XTempException xdterr) {
            xdterr.printStackTrace();
        }
    }
}
