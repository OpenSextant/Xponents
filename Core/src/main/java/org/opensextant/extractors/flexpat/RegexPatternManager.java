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
package org.opensextant.extractors.flexpat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p >
 * This is the culmination of various date/time extraction efforts in python and
 * Java. This API
 * poses no assumptions on input data or on execution. Features of REGEX
 * patterns file:
 * <ul>
 * <li>DEFINE - a component of a pattern to match</li>
 * <li>RULE - a complete pattern to match</li>
 * </ul>
 * This work started in Java 6 and has the limitation of Java 6 Regex, mainly
 * that there are no
 * named groups available in matching.
 * <p >
 * See XCoord PatternManager for a good example implementation.
 *
 * @author dlutz (lutzdavp)
 * @author ubaldino
 */
public abstract class RegexPatternManager {

    protected Logger log = LoggerFactory.getLogger(getClass());

    /**
     *
     */
    protected Map<String, RegexPattern> patterns = null;
    /**
     *
     */
    protected List<RegexPattern> patterns_list = null;
    protected String patternFile = null;

    /**
     *
     */
    public boolean debug = log.isDebugEnabled();
    /**
     *
     */
    public boolean testing = false;
    /**
     *
     */
    public List<PatternTestCase> testcases = new ArrayList<>();

    public RegexPatternManager(InputStream s, String n) throws IOException {
        this.patternFile = n;
        testing = this.debug;
        initialize(s);
    }

    /**
     * @return collection of patterns
     */
    public Collection<RegexPattern> get_patterns() {
        return patterns_list;
    }

    /**
     * Access the paterns by ID
     *
     * @param id pattern id
     * @return found pattern or null
     */
    public RegexPattern get_pattern(String id) {
        return patterns.get(id);
    }

    /**
     * Implementation must create a RegexPattern given the basic RULE define, #RULE
     * FAMILY RID REGEX
     * PatternManager here adds compiled pattern and DEFINES.
     *
     * @param fam  family
     * @param rule rule ID within the family
     * @param desc optional description
     * @return pattern object
     */
    protected abstract RegexPattern create_pattern(String fam, String rule, String desc);

    /**
     * Implementation has the option to check a pattern; For now invalid patterns
     * are only logged.
     *
     * @param pat pattern object
     * @return true if pattern is valid
     */
    protected abstract boolean validate_pattern(RegexPattern pat);

    /**
     * Implementation must create TestCases given the #TEST directive, #TEST RID TID
     * TEXT
     *
     * @param id   pattern id
     * @param fam  pattern family
     * @param text text for test case
     * @return test case object
     */
    protected abstract PatternTestCase create_testcase(String id, String fam, String text);

    /**
     * enable an instance of a pattern based on the global settings.
     *
     * @param p the pattern obj to enable
     */
    public abstract void enable_pattern(RegexPattern p);

    /**
     * default adapter -- you must override. This should be abstract, but not all
     * pattern managers are
     * required to support this.
     *
     * @param name pattern name to enable.
     */
    public void enable_patterns(String name) {
        // throw new Exception("not implemented");
    }

    /**
     * Enable a family of patterns
     */
    public void disableAll() {
        for (RegexPattern pat : patterns.values()) {
            pat.enabled = false;
        }
    }

    public void enableAll() {
        for (RegexPattern pat : patterns.values()) {
            pat.enabled = true;
        }
    }

    private final StringBuilder configMessages = new StringBuilder();

    /**
     * Initializes the pattern manager implementations. Reads the DEFINEs and RULEs
     * from the pattern
     * file and does the requisite substitutions. After initialization patterns
     * HashMap will be
     * populated.
     *
     * @param io stream
     * @throws IOException if patterns file can not be loaded and parsed
     */
    public void initialize(InputStream io) throws IOException {
        String SEPARATOR = "[\t ]+";
        patterns = new HashMap<>();
        patterns_list = new ArrayList<>();

        // the #DEFINE statements as name and regex
        HashMap<String, String> defines = new HashMap<>();

        // the #RULE statements as name and a sequence of DEFINES and regex bits
        HashMap<String, String> rules = new HashMap<>();
        HashMap<String, String> matcherClasses = new HashMap<>();
        List<String> rule_order = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(io, StandardCharsets.UTF_8))) {

            String _line = null;
            String[] fields;
            int testcount = 0;
            while ((_line = reader.readLine()) != null) {

                String line = _line.trim();

                // Is it a define statement?
                if (line.startsWith("#DEFINE")) {
                    // line should be
                    // #DEFINE<tab><defineName><tab><definePattern>
                    fields = line.split(SEPARATOR, 3);
                    if (fields.length!=3) {
                        throw new ConfigException(String.format("DEFINE entry must have 3 fields for LINE: %s", line));
                    }
                    
                    defines.put(fields[1].trim(), fields[2].trim());
                } // Is it a rule statement?
                else if (line.startsWith("#RULE")) {
                    // line should be
                    // #RULE<tab><rule_fam><tab><rule_id><tab><pattern>
                    fields = line.split(SEPARATOR, 4);
                    if (fields.length!=4) {
                        throw new ConfigException(String.format("RULE entry must have 4 fields for LINE: %s", line));
                    }
                    

                    String fam = fields[1].trim();
                    String ruleName = fields[2].trim();
                    String rulePattern = fields[3].trim();

                    // geoform + ruleName should be unique, use as key in rules
                    // table
                    String ruleKey = fam + "-" + ruleName;

                    // if already a rule by that name, error
                    if (rules.containsKey(ruleKey)) {
                        // log.error("Duplicate rule name " + ruleName);
                        throw new IOException("FlexPat Config Error - Duplicate rule name " + ruleName);
                    } else {
                        rules.put(ruleKey, rulePattern);
                        rule_order.add(ruleKey);
                    }
                } else if (testing && line.startsWith("#TEST")) {
                    fields = line.split(SEPARATOR, 4);
                    if (fields.length!=4) {
                        throw new ConfigException(String.format("TEST entry must have 4 fields for LINE: %s", line));
                    }
                    ++testcount;

                    String fam = fields[1].trim();
                    String ruleName = fields[2].trim();
                    String testtext = fields[3].trim().replace("$NL", "\n");

                    String ruleKey = fam + "-" + ruleName;

                    PatternTestCase tc = create_testcase(ruleKey + "#" + testcount, fam, testtext);
                    tc.setRemarks(testtext);
                    // testcount is a count of all tests, not just test within a rule family
                    // testcases.add(new PatternTestCase(ruleKey + "#" + testcount, fam, testtext));
                    testcases.add(tc);
                } else if (line.startsWith("#CLASS")) {
                    fields = line.split(SEPARATOR, 3);
                    if (fields.length!=3) {
                        throw new ConfigException(String.format("CLASS entry must have 3 fields for LINE: %s", line));
                    }

                    String fam = fields[1].trim();
                    matcherClasses.put(fam, fields[2].trim());
                }
            } // end file read loop
        } // try-finally closes reader.

        // defines and rules should be completely populated

        // substitute all uses of DEFINE patterns within a RULE
        // with the DEFINE pattern surrounded by a capture group
        // populate the group names Hashmap with a key made from the rule name
        // and group index

        // the pattern of a DEFINE within a RULE e.g "<somePiece>"
        String elementRegex = "<[a-zA-Z0-9_]+>";
        Pattern elementPattern = Pattern.compile(elementRegex);

        for (String tmpKey : rule_order) {
            String tmpRulePattern = rules.get(tmpKey);

            // the key should be of the form <geoform>_<rulename>
            String[] pieces = tmpKey.split("-", 2);
            String tmpFam = pieces[0];
            String tmpRuleName = pieces[1];

            Matcher elementMatcher = elementPattern.matcher(tmpRulePattern);
            // find all of the element definitions within the pattern
            int groupNum = 1;

            if (debug) {
                configMessages.append("\n\nrulename=" + tmpRuleName);
                configMessages.append(", rulepattern=" + tmpRulePattern);
            }

            RegexPattern pat = create_pattern(tmpFam, tmpRuleName, "No Description yet...");

            if (matcherClasses.containsKey(tmpFam)) {
                pat.match_classname = matcherClasses.get(tmpFam);
                try {
                    pat.match_class = Class.forName(pat.match_classname);
                } catch (ClassNotFoundException err) {
                    throw new IOException("FlexPat initialization failed due to invalid classname", err);
                }
            }

            // find and replace the DEFINEd pattern
            while (elementMatcher.find()) {
                int elementStart = elementMatcher.start();
                int elementEnd = elementMatcher.end();
                String elementName = tmpRulePattern.substring(elementStart + 1, elementEnd - 1);
                pat.regex_groups.add(elementName);

                // groupNames.put(tmpRuleName + "-" + groupNum, elementName);
                if (debug) {
                    String subelementPattern = defines.get(elementName);
                    configMessages.append("\n\t");
                    configMessages.append(groupNum + " " + elementName + " = " + subelementPattern);
                }
                groupNum++;
            }

            for (String tmpDefineName : defines.keySet()) {

                // NOTE: Use of parens, "(expr)", is required to create groups within a pattern.
                String tmpDefinePattern = "(" + defines.get(tmpDefineName) + ")";
                tmpDefineName = "<" + tmpDefineName + ">";
                // use replace(tok, sub) not replaceAll(re, sub)
                tmpRulePattern = tmpRulePattern.replace(tmpDefineName, tmpDefinePattern);
            }

            if (debug) {
                configMessages.append("\nrulepattern=" + tmpRulePattern);
            }

            // MCU: slash simplified.
            // tmpRulePattern = tmpRulePattern.replaceAll("\\", "\\\\");

            // at this point rule pattern should have had defines replaced
            // compile and insert into pattern hashmap
            pat.regex = Pattern.compile(tmpRulePattern, Pattern.CASE_INSENSITIVE);

            enable_pattern(pat);

            patterns_list.add(pat);
            patterns.put(pat.id, pat);

            if (!validate_pattern(pat)) {
                throw new IOException("Invalid Pattern @ " + pat);
            }
        }

        if (debug) {
            configMessages.append("\nFound # of PATTERNS=" + patterns.values().size());

            for (RegexPattern pat : patterns_list) {
                configMessages.append("\n");
                configMessages.append(pat.id + "\t" + pat.regex.pattern());
            }
        }

    }// end initialize

    /**
     * Instead of relying on a logging API, we now throw Exceptionsages for real
     * configuration errors,
     * and capture configuration details in a buffer if debug is on.
     *
     * @return the configuration debug
     */
    public String getConfigurationDebug() {
        if (!debug) {
            return "Debug not enabled; Try again, set .debug  = true";
        }
        return configMessages.toString();
    }

    /**
     * NOTE: We're dealing with Java6's inability to use named groups. So we have to
     * track FlexPat slots
     * in line with Matcher fields matched. Essentially this comes down to a simple
     * Name:Offset pairing;
     * our limitation here is no nesting.
     *
     * @param p       pattern
     * @param matched matcher
     * @return map containing the matched groups, as deciphered by Flexpat and the
     *         definitions in the
     *         patterns file
     */
    public Map<String, String> group_map(RegexPattern p, java.util.regex.Matcher matched) {

        Map<String, String> pairs = new HashMap<>();
        int cnt = matched.groupCount();
        for (int x = 0; x < cnt; ++x) {

            // Put the matcher group in a hash with an appropriate name.
            String nm = p.regex_groups.get(x);
            pairs.put(nm, matched.group(x + 1));
        }

        return pairs;
    }

    /**
     * Matched fields as TextEntities
     *
     * @param p       pattern
     * @param matched java RE Matcher
     * @return keyed TextEntity
     */
    public Map<String, TextEntity> group_matches(RegexPattern p, java.util.regex.Matcher matched) {

        Map<String, TextEntity> pairs = new HashMap<>();
        int cnt = matched.groupCount();
        for (int x = 0; x < cnt; ++x) {

            // Put the matcher group in a hash with an appropriate name.
            String nm = p.regex_groups.get(x);
            String span = matched.group(x + 1);
            int x1 =matched.start(x + 1);
            int x2 = matched.end(x+1);
            TextEntity e = new TextEntity(x1, x2);
            e.setText(span);
            pairs.put(nm, e);
        }

        return pairs;
    }
}
