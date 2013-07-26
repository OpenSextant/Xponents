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
package org.mitre.xtemporal;

import org.mitre.flexpat.*;

import java.util.*;
import java.io.IOException;

import java.net.URL;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ubaldino
 */
public class PatternManager extends RegexPatternManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    {
      debug = log.isDebugEnabled();//TODO remove debug field altogether in place of log.isDebugEnabled()
    }

    /**
     *
     */
    public Map<Integer, Boolean> pattern_family_state = new HashMap<Integer, Boolean>();

    /**
     *
     * @param _patternfile
     * @throws MalformedURLException
     */
    public PatternManager(String _patternfile) throws MalformedURLException {
        super(_patternfile);
    }
    
    /**
     *
     * @param _patternfile
     */
    public PatternManager(URL _patternfile) {
        super(_patternfile);
    }

    /**
     *
     * @throws IOException
     */
    public void initialization() throws IOException {
        super.initialize();
        enable_pattern_family(XTConstants.DATETIME_FAMILY, true);
        enable_pattern_family(XTConstants.MDY_FAMILY, true);
        if (debug){
            log.debug(this.getConfigurationDebug());
        }

    }

    /**
     *
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
     *
     * @param pat
     * @return
     */
    @Override
    protected boolean validate_pattern(RegexPattern pat) {
        // Nothing to validate yet.
        return true;
    }

    /**
     *
     * @param id
     * @param fam
     * @param text
     * @return
     */
    @Override
    protected PatternTestCase create_testcase(String id, String fam, String text) {
        return new org.mitre.xtemporal.TestCase(id, fam, text);
    }

    /** enable an instance of a pattern based on the global settings. 
     * @param repat 
     */
    @Override
    public void enable_pattern(RegexPattern repat) {
        DateTimePattern p = (DateTimePattern) repat;

        Boolean b = pattern_family_state.get(p.family_id);
        if (b != null) {
            p.enabled = b.booleanValue();
        }
    }

    /**
     *
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
