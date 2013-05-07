/**
 Copyright 2013 The MITRE Corporation.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 ** **************************************************
 * NOTICE
 *
 *  
 * This software was produced for the U. S. Government
 * under Contract No. W15P7T-12-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
**************************************************   */


package org.mitre.opensextant.poli;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
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
    public void configure(String patfile) throws PoliException {
        if (patfile != null) {
            patterns_file = patfile;
        }

        try {
            patterns = new PoliPatternManager(patterns_file.trim());
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

    public PoliResult extract_patterns(String text, String text_id) {

        PoliResult results = new PoliResult();
        results.result_id = text_id;
        results.matches = new ArrayList<TextMatch>();
        int bufsize = text.length();

        PoliMatch poliMatch = null;

        for (RegexPattern repat : patterns.get_patterns()) {
            if (!repat.enabled) {
                continue;
            }

            Matcher match = repat.regex.matcher(text);
            results.evaluated = true;
            while (match.find()) {

                Map<String, String> fields = patterns.group_map(repat, match);

                poliMatch = patterns.create_match(repat.id, match.group(), fields);
                if (poliMatch == null) {
                    // This would have been thrown at init.
                    log.error("Could not find pattern family for " + repat.id);
                    continue;
                }

                poliMatch.pattern_id = repat.id;
                poliMatch.start = match.start();
                poliMatch.end = match.end();

                // Filter -- trivial filter is to filter out any coord that cannot 
                //TODO: Assess filters?

                // returns indices for window around text match
                int[] slices = TextUtils.get_text_window((int) poliMatch.start, bufsize, MATCH_WIDTH);


                //                               left l1 to left l2
                poliMatch.setContext(TextUtils.delete_eol(text.substring(slices[0], slices[1])));

                // coord.createID();
                set_match_id(poliMatch);

                results.matches.add(poliMatch);

            }
        }
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

    /**
     * Run a simple test.
     *
     * @param args only one argument accepted: a text file input.
     */
    public static void main(String[] args) {
        boolean debug = true;

        // Use default config file.
        PatternsOfLife poli = new PatternsOfLife(debug);

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("Poli", args, "aft:u:");

        try {
            //xc.configure( "file:./etc/test_regex.cfg"); // default
            poli.configure(); // default
            TestScript test = new TestScript(poli);

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                    case 'f':
                        System.out.println("\tSYSTEM TESTS=======\n" + opts.getOptarg());
                        test.test();
                        break;

                    case 't':
                        System.out.println("\tUSER TEST\n=======\n" + opts.getOptarg());
                        test.testUserFiles(opts.getOptarg());
                        break;

                    case 'u':
                        System.out.println("\tUSER FILE\n=======\n" + opts.getOptarg());
                        test.testUserFile(opts.getOptarg());
                        break;

                    case 'a':
                        System.out.println("\tAdhoc Tests\n=======\n" + opts.getOptarg());
                        test.adhoc();
                        break;


                    default:
                }
            }
        } catch (PoliException xerr) {
            xerr.printStackTrace();
        }
    }
}
