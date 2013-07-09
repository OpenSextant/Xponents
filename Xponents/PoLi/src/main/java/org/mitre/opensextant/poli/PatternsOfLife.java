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
package org.mitre.opensextant.poli;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mitre.flexpat.*;
import org.mitre.opensextant.util.TextUtils;
import org.mitre.flexpat.RegexPatternManager;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class PatternsOfLife {
    
    String patterns_file = "/poli_patterns.cfg";
    PoliPatternManager patterns = null;
    static Logger log = LoggerFactory.getLogger(PatternsOfLife.class);
    private boolean debug = false;
    private TextUtils utility = new TextUtils();
    
    public PatternsOfLife(boolean debugmode) {
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
    
    public RegexPatternManager getPatternManager() {
        return patterns;
    }

    /**
     * @throws PoliException
     */
    public void configure() throws PoliException {
        configure(getClass().getResource(patterns_file)); // default
    }

    /**
     * Configure using a particular pattern file.
     *
     * @param patfile
     * @throws XCoordException
     */
    public void configure(File patfile) throws PoliException {
        if (patfile != null) {
            patterns_file = patfile.getPath();
        }
        
        try {
            patterns = new PoliPatternManager(patfile);
            patterns.testing = debug;
            patterns.initialize();
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file FILE=" + patterns_file;
            log.error(msg, loaderr);
            throw new PoliException(msg, loaderr);
        }
    }

    /**
     * Configure using a URL pointer to the pattern file.
     *
     * @param patfile
     * @throws XCoordException
     */
    public void configure(java.net.URL patfile) throws PoliException {
        
        try {
            patterns = new PoliPatternManager(patfile);
            patterns.testing = debug;
            patterns.initialize();
            patterns_file = patfile.getFile();
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file URL=" + patfile;
            log.error(msg, loaderr);
            throw new PoliException(msg, loaderr);
        }
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
     * Extract patterns of a certain family from a block of text.
     *
     * @param text - data to process
     * @param text_id - identifier for the data
     * @param family - optional filter; to reuse the same PatManager but extract
     * certain patterns only.
     *
     * @return PoliResult
     */
    public PoliResult extract_patterns(String text, String text_id, String family) {
        
        PoliResult results = new PoliResult();
        results.result_id = text_id;
        results.matches = new ArrayList<TextMatch>();
        int bufsize = text.length();
        
        PoliMatch poliMatch = null;
        
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
                int[] slices = TextUtils.get_text_window((int) poliMatch.start,
                        bufsize, MATCH_WIDTH);

                // left l1 to left l2
                poliMatch.setContext(TextUtils.delete_eol(text.substring(
                        slices[0], slices[1])));

                // coord.createID();
                set_match_id(poliMatch);
                
                results.matches.add(poliMatch);
                
            }
        }
        
        results.pass = !results.matches.isEmpty();
        PoliPatternManager.reduce_matches(results.matches);
        
        return results;
    }

    /**
     * Assign an identifier to each Text Match found. This is an MD5 of the
     * coord in-situ.
     *
     * @param coord
     */
    protected void set_match_id(TextMatch match) {
        match.match_id = utility.genTextID(match.getContext());
    }
    
    public static void usage() {
        System.out
                .println("\tPatternsOfLife  -f          -- run all system tests\n"
                + "\tPatternsOfLife  -u  <file>  -- run user tests on given text file.");
    }

    /**
     * Run a simple test.
     *
     * @param args only one argument accepted: a text file input.
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
        
        try {
            // Use default config file.
            PatternsOfLife poli = new PatternsOfLife(debug);
            if (config == null) {
                poli.configure(); // default
            } else {
                poli.configure(new File(config).getAbsoluteFile());
            }
            TestScript test = new TestScript(poli);
            if (systemTest) {
                test.test();
            } else if (testFile != null) {
                test.testUserFile(testFile);
            }
            
        } catch (PoliException xerr) {
            xerr.printStackTrace();
        } catch (IOException ioerr) {
            ioerr.printStackTrace();
        }
        
    }
}
