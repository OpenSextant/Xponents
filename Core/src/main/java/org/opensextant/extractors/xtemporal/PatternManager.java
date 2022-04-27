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
package org.opensextant.extractors.xtemporal;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;

/**
 * @author ubaldino
 */
public class PatternManager extends RegexPatternManager {

    /**
     *
     */
    public Map<Integer, Boolean> pattern_family_state = new HashMap<>();

    /**
     * Pass in InputStream to provide yourself the most flexibility.
     *
     * @param s io stream
     * @param n name of patterns file
     * @throws IOException
     */
    public PatternManager(InputStream s, String n) throws IOException {
        super(s, n);
    }

    /**
     * @throws IOException
     */
    @Override
    public void initialize(InputStream io) throws IOException {
        pattern_family_state = new HashMap<>();
        super.initialize(io);
        enable_pattern_family(XTConstants.DATETIME_FAMILY, true);
        enable_pattern_family(XTConstants.MDY_FAMILY, true);
        log.debug(this.getConfigurationDebug());
    }

    /**
     * @param fam
     * @param rule
     * @param desc
     * @return
     */
    @Override
    protected RegexPattern create_pattern(String fam, String rule, String desc) {
        return new DateTimePattern(fam, rule, desc);
    }

    /**
     * @param pat
     * @return
     */
    @Override
    protected boolean validate_pattern(RegexPattern pat) {
        // Nothing to validate yet.
        return true;
    }

    /**
     * @param id
     * @param fam
     * @param text
     * @return
     */
    @Override
    protected PatternTestCase create_testcase(String id, String fam, String text) {
        return new org.opensextant.extractors.xtemporal.TestCase(id, fam, text);
    }

    /**
     * enable an instance of a pattern based on the global settings.
     *
     * @param repat
     */
    @Override
    public void enable_pattern(RegexPattern repat) {
        DateTimePattern p = (DateTimePattern) repat;

        Boolean b = pattern_family_state.get(p.family_id);
        if (b != null) {
            p.enabled = b;
        }
    }

    /**
     * @param fam
     * @param enabled
     */
    public void enable_pattern_family(int fam, boolean enabled) {
        pattern_family_state.put(fam, enabled);

        // And re-set all such patterns.
        if (patterns.isEmpty()) {
            return;
        }

        for (RegexPattern repat : patterns.values()) {

            DateTimePattern pat = (DateTimePattern) repat;
            // This seems like overkill, but just changing the states of
            // patterns for the specified group of patterns.
            if (pat.family_id == fam) {
                enable_pattern(pat);
            }
        }
    }
}