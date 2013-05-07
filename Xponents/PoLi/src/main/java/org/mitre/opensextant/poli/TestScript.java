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

import org.mitre.flexpat.PatternTestCase;
import org.mitre.flexpat.TextMatch;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.*;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class TestScript {

    private Logger log = LoggerFactory.getLogger(TestScript.class);
    private PatternsOfLife poli;

    public TestScript(PatternsOfLife _poli) {
        this.poli = _poli;
    }

    /**
     * System tests
     */
    public void test() {

        List<TextMatch> allResults = new ArrayList<>();
        log.info("TESTING ALL SYSTEM PATTERNS");
        for (PatternTestCase test : this.poli.patterns.testcases) {
            log.info("TEST " + test.id);
            PoliResult results = this.poli.extract_patterns(test.text, test.id);
            if (results.evaluated && !results.matches.isEmpty()) {
                for (TextMatch m : results.matches) {
                    log.info("TEST " + test.id + " FOUND: " + m.toString());
                    allResults.add(m);
                }
            } else {
                log.info("TEST " + test.id + " STATUS: FAILED");
            }
        }
    }

    /**
     * Run patterns over a list of files
     */
    public void testUserFiles(String listfile) {
    }

    /**
     * Run patterns over a single file
     */
    public void testUserFile(String f) {
    }

    /** Random testing 
     */
    public void adhoc() {
        this.poli.patterns.disableAll();
        this.poli.patterns.enable_patterns("PHONE");
        this.test();
    }
}
