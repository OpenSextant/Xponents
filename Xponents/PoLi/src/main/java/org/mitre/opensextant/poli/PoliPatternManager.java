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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import org.mitre.flexpat.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 */
public final class PoliPatternManager extends RegexPatternManager {

    Logger log = LoggerFactory.getLogger(PoliPatternManager.class);

    /**
     *
     * @param _patternfile
     * @throws MalformedURLException
     */
    public PoliPatternManager(String _patternfile) throws MalformedURLException {
        super(_patternfile);
        log = LoggerFactory.getLogger(PoliPatternManager.class);
        this.debug = log.isDebugEnabled();
    }

    /**
     *
     * @param _patternfile
     */
    public PoliPatternManager(URL _patternfile) {
        super(_patternfile);
        log = LoggerFactory.getLogger(PoliPatternManager.class);
        this.debug = log.isDebugEnabled();
    }


    /** Enable a family of patterns 
     */
    public void disable_patterns(String fam) {
        for (RegexPattern pat : patterns.values()) {
            if (pat.id.startsWith(fam)) {
                pat.enabled = false;
            }
        }
    }

    public void enable_patterns(String fam) {
        for (RegexPattern pat : patterns.values()) {
            if (pat.id.startsWith(fam)) {
                pat.enabled = true;
            }
        }
    }

    /**
     * enable an instance of a pattern based on the global settings.
     *
     * @param repat
     */
    @Override
    public void enable_pattern(RegexPattern repat) {
        repat.enabled = true;
    }

    /**
     *
     * Pattern Factory
     *
     * Implementation must create a RegexPattern given the basic RULE define,
     * #RULE FAMILY RID REGEX PatternManager here adds compiled pattern and
     * DEFINES.
     *
     * @param fam
     * @param rule
     * @param desc
     * @return
     */
    @Override
    protected RegexPattern create_pattern(String fam, String rule, String desc) {
        RegexPattern p = new RegexPattern(fam + "-" + rule, desc);
        return p;
    }

    /**
     * Implementation has the option to check a pattern; For now invalid
     * patterns are only logged.
     *
     * @param repat
     * @return
     */
    @Override
    protected boolean validate_pattern(RegexPattern repat) {
        // No validation at this time.
        return true;
    }

    /**
     * Implementation must create TestCases given the #TEST directive, #TEST RID
     * TID TEXT
     *
     * @param id
     * @param text
     * @param fam
     * @return
     */
    @Override
    protected PatternTestCase create_testcase(String id, String fam, String text) {
        return new TestCase(id, fam, text);
    }

    /**
     * The match object is normalized, setting the coord_text and other data
     * from parsing "text" and knowing which pattern family was matched.
     *
     * @param m
     * @param groups
     * @return void
     */
    public PoliMatch create_match(String pattern_id, String matchtext, Map<String, String> groups) {
        return new PoliMatch(groups, matchtext);
    }

    /**
     * The match object is normalized, setting the coord_text and other data
     * from parsing "text" and knowing which pattern family was matched.
     *
     * @param m
     * @param groups
     * @return void
     * @throws PoliException
     */
    public void normalize(PoliMatch m, Map<String, String> groups)
            throws PoliException {

        throw new PoliException("not yet implemented");
    }
}
