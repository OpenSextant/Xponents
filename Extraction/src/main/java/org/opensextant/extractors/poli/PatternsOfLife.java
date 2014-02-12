/**
 * Copyright 2013 The MITRE Corporation.
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
 ** **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
 * *************************************************
 */
package org.opensextant.extractors.poli;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.io.IOException;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extraction.ConfigException;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.processing.progress.ProgressMonitor;
import org.opensextant.util.TextUtils;

/**
 * 
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class PatternsOfLife extends AbstractFlexPat {

    public final static String DEFAULT_POLI_CFG = "/poli_patterns.cfg";

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
    public void cleanup() {
    }

    /**
     * Extractor interface: getName
     * 
     * @return
     */
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

                // coord.createID();
                set_match_id(poliMatch);

                results.matches.add(poliMatch);

            }
            patternsComplete++;
            updateProgress(patternsComplete / (double) patterns.get_patterns().size() + 1);
        }

        results.pass = !results.matches.isEmpty();
        PoliPatternManager.reduce_matches(results.matches);

        return results;
    }

    public static void usage() {
        System.out.println("\tPatternsOfLife  -f          -- run all system tests\n"
                + "\tPatternsOfLife  -u  <file>  -- run user tests on given text file.");
    }

    /**
     * Run a simple test.
     * 
     * @param args
     *            only one argument accepted: a text file input.
     */
    public static void main(String[] args) {
        boolean debug = true;

        boolean systemTest = false;
        String testFile = null;
        String config = null;
        try {
            gnu.getopt.Getopt opts = new gnu.getopt.Getopt("Poli", args, "c:u:f");

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'f':
                    System.out.println("\tSystem TESTS======= ");
                    systemTest = true;
                    break;

                case 'u':
                    testFile = opts.getOptarg();
                    System.out.println("\tUser TESTS======= FILE=" + testFile);
                    break;

                case 'c':
                    config = opts.getOptarg();
                    System.out.println("\tUser Patterns Configuration ======= FILE=" + config);
                    break;

                default:
                    PatternsOfLife.usage();
                    System.exit(1);
                }
            }
        } catch (Exception runErr) {
            runErr.printStackTrace();
            PatternsOfLife.usage();
            System.exit(1);
        }
        PatternsOfLife poli = null;

        try {
            // Use default config file.
            poli = new PatternsOfLife(debug);
            if (config == null) {
                poli.configure(); // default
            } else {
                poli.configure(config);
            }
        } catch (ConfigException xerr) {
            xerr.printStackTrace();
            System.exit(-1);
        }

        try {
            TestScript test = new TestScript(poli);
            if (systemTest) {
                test.test();
            } else if (testFile != null) {
                test.testUserFile(testFile);
            }
        } catch (NormalizationException xerr) {
            xerr.printStackTrace();
        } catch (IOException ioerr) {
            ioerr.printStackTrace();
        }
    }

}
