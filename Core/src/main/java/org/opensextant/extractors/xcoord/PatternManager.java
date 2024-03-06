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
package org.opensextant.extractors.xcoord;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extractors.flexpat.RegexPatternManager;

/**
 * <p>
 * This is the culmination of various coordinate extraction efforts in python
 * and Java. This API poses no assumptions on input data or on execution.
 * <p >
 * Common Coordinate Enumeration (CCE) is a concept for enumerating the
 * coordinate representations. See XConstants for details.
 * The basics of CCE include a family (DD, DMS, MGRS, etc.) and style (
 * enumerated in patterns config file).
 * <p >
 * Features of REGEX patterns file:
 * <ul>
 * <li>DEFINE - a component of a coord pattern to match</li>
 * <li>RULE - a complete pattern to match</li>
 * <li>TEST - an example of the text the pattern should match in part or
 * whole.</li>
 * </ul>
 * <p >
 * The Rules file: The Rules is an external text file containing rules
 * consisting of regular expressions used to identify geocoords. Below is an
 * example of what a simple rule might look like:
 *
 * <pre>
 * // Parts of a decimal degree Latitude/Longitude
 * #DEFINE  decDegLat   \d?\d\.\d{1,20}
 * #DEFINE  decDegLon   [0-1]?\d?\d\.\d{1,20}
 *
 * // TARGET: DD-xx, Decimal Deg, Preceding Hemisphere (a) H DD.DDDDDD° HDDD.DDDDDD°, optional deg symbol
 * #RULE   DD      01      &lt;hemiLatPre&gt;\s?&lt;decDegLat&gt;&lt;degSym&gt;?\s*&lt;latlonSep&gt;?\s*&lt;hemiLonPre&gt;\s?&lt;decDegLon&gt;lt;degSym&gt;?
 * #TEST   DD      01      N42.3, W102.4
 * </pre>
 *
 * Where the DEFINE statements relay fields that the PatternManager will recall
 * at runtime. The RULE is a composition of DEFINEs, other literals and regex
 * patterns. A rule must have a family and a rule ID within that family. And the
 * TEST statement (which is enumerated the same as the RULE family and ID). At
 * runtime all tests are further labeled with an incrementor, e.g. for TEST
 * "DD-01" might be the eighth test in the pattern file, so the test will be
 * labeled internally as DD-01#8.
 *
 * @author dlutz, MITRE creator (lutzdavp)
 * @author ubaldino, MITRE adaptor
 * @author swainza
 */
public final class PatternManager extends RegexPatternManager {

    public PatternManager(InputStream s, String n) throws IOException {
        super(s, n);
    }

    /**
     *
     */
    public Map<Integer, Boolean> CCE_family_state = new HashMap<>();

    /**
     * @throws IOException
     */
    @Override
    public void initialize(InputStream io) throws IOException {
        CCE_family_state = new HashMap<>();
        super.initialize(io);
        log.debug(this.getConfigurationDebug());
    }

    /**
     * @param cce_fam
     * @param enabled
     */
    public void enable_CCE_family(int cce_fam, boolean enabled) {
        CCE_family_state.put(cce_fam, enabled);

        // And re-set all such patterns.
        if (patterns_list.size() > 0) {
            for (RegexPattern repat : patterns_list) {

                GeocoordPattern pat = (GeocoordPattern) repat;
                // This seems like overkill, but just changing the states of
                // patterns for the specified group of patterns.
                if (pat.cce_family_id == cce_fam) {
                    enable_pattern(pat);
                }
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
        GeocoordPattern p = (GeocoordPattern) repat;

        Boolean b = CCE_family_state.get(p.cce_family_id);
        if (b != null) {
            p.enabled = b;
        }
    }

    /**
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
        return new GeocoordPattern(fam, rule, desc);
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
        GeocoordPattern p = (GeocoordPattern) repat;
        if (p.cce_family_id == XConstants.UNK_PATTERN) {
            log.error("Invalid Pattern @ " + p);
        }
        return (p.cce_family_id != XConstants.UNK_PATTERN);
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
        return new GeocoordTestCase(id, fam, text);
    }
}
