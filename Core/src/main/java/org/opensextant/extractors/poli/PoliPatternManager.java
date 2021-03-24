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
package org.opensextant.extractors.poli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;

/**
 */
public final class PoliPatternManager extends RegexPatternManager {

    public PoliPatternManager(InputStream s, String n) throws IOException {
        super(s, n);
    }

    /**
     * Enable a family of patterns
     *
     * @param fam pat family to disable
     */
    public void disable_patterns(String fam) {
        for (RegexPattern pat : patterns.values()) {
            if (pat.id.startsWith(fam)) {
                pat.enabled = false;
            }
        }
    }

    /**
     * You don't really want to enable All patterns... unless you are brute force
     * testing all your patterns.
     */
    @Override
    public void enableAll() {
        for (RegexPattern pat : patterns.values()) {
            pat.enabled = true;
        }
    }

    @Override
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
     * @param repat regex pattern
     */
    @Override
    public void enable_pattern(RegexPattern repat) {
        repat.enabled = true;
    }

    /**
     * Pattern Factory
     * Implementation must create a RegexPattern given the basic RULE define,
     * #RULE FAMILY RID REGEX PatternManager here adds compiled pattern and
     * DEFINES.
     *
     * @param fam  pattern family
     * @param rule pattern rule ID
     * @param desc optional description
     * @return flexpat pattern
     */
    @Override
    protected RegexPattern create_pattern(String fam, String rule, String desc) {
        RegexPattern p = new RegexPattern(fam, fam + "-" + rule, desc);
        return p;
    }

    /**
     * Implementation has the option to check a pattern; For now invalid
     * patterns are only logged. All patterns in config file are valid.
     * Override this as needed.
     *
     * @param repat pattern object
     * @return true if pattern is valid
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
     * @param id   test ID
     * @param text text for test
     * @param fam  pattern family to test
     * @return test case
     */
    @Override
    protected PatternTestCase create_testcase(String id, String fam, String text) {
        return new TestCase(id, fam, text);
    }

    /**
     * The match object is normalized, setting the coord_text and other data
     * from parsing "text" and knowing which pattern family was matched.
     *
     * @param pattern_id the pattern_id
     * @param matchtext  the matchtext
     * @param groups     the groups
     * @return the poli match
     * @deprecated logic for creation of a match is back in main PoLi match loop
     */
    @Deprecated
    public PoliMatch create_match(String pattern_id, String matchtext, Map<String, String> groups) {
        return new PoliMatch(groups, matchtext);
    }
}
