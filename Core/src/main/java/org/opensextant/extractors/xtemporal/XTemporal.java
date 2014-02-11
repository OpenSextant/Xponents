/**
 *
 * Copyright 2009-2013 The MITRE Corporation.
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
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
 * // // _____ ____ __ __ ///\ __`\ /\ _`\ /\ \__ /\ \__ //\ \ \/\ \ _____ __
 * ___ \ \,\L\_\ __ __ _\ \ ,_\ __ ___ \ \ ,_\ // \ \ \ \ \ /\ '__`\ /'__`\ /' _
 * `\ \/_\__ \ /'__`\/\ \/'\\ \ \/ /'__`\ /' _ `\\ \ \/ // \ \ \_\ \\ \ \L\ \/\
 * __/ /\ \/\ \ /\ \L\ \ /\ __/\/> </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_ // \
 * \_____\\ \ ,__/\ \____\\ \_\ \_\ \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\
 * \_\ \_\\ \__\ // \/_____/ \ \ \/ \/____/ \/_/\/_/ \/_____/ \/____/\//\/_/
 * \/__/ \/__/\/_/ \/_/\/_/ \/__/ // \ \_\ // \/_/ // // OpenSextant XTemporal
 * // *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
 * //
 */
package org.opensextant.extractors.xtemporal;

import java.util.*;
import java.util.regex.Matcher;
import org.opensextant.extraction.ConfigException;
import org.opensextant.extraction.TextInput;
import org.opensextant.extractors.flexpat.AbstractFlexPat;
import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extractors.flexpat.RegexPattern;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.RegexPatternManager;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.processing.progress.ProgressMonitor;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author ubaldino
 */
public class XTemporal extends AbstractFlexPat {

	public final static String DEFAULT_XTEMP_CFG = "/datetime_patterns.cfg";

	/**
	 * Extractor interface: getName
	 * 
	 * @return
	 */
	public String getName() {
		return "XTemporal";
	}

	/**
	 * Extractor interface: extractors are responsible for cleaning up after themselves.
	 */
	public void cleanup(){}
	
	/**
	 * 
	 * @param debugmode
	 */
	public XTemporal(boolean debugmode) {
		this.patterns_file = DEFAULT_XTEMP_CFG;
		log = LoggerFactory.getLogger(getClass());

		if (debugmode) {
			debug = true;
		} else {
			debug = log.isDebugEnabled();
		}
	}

	/**
	 * non-debugging ctor;
	 */
	public XTemporal() {
		this(false);
	}

	@Override
	protected RegexPatternManager createPatternManager()
			throws java.net.MalformedURLException {
		if (this.patterns_url != null) {
			return new PatternManager(patterns_url);
		} else {
			return new PatternManager(patterns_file);
		}
	}

	/**
	 * Support the standard Extractor interface. This provides access to the
	 * most common extraction;
	 */
	@Override
	public List<TextMatch> extract(TextInput input) {
		TextMatchResult results = extract_dates(input.buffer, input.id);
		return results.matches;
	}

	   /**
     * Support the standard Extractor interface. This provides access to the
     * most common extraction;
     */
    @Override
    public List<TextMatch> extract(String input_buf) {
        TextMatchResult results = extract_dates(input_buf, NO_DOC_ID);
        return results.matches;
    }

	/**
	 * A direct call to extract dates; which is useful for diagnostics and
	 * development/testing.
	 * 
	 * @param text
	 * @param text_id
	 * @return
	 */
	public TextMatchResult extract_dates(String text, String text_id) {

		TextMatchResult results = new TextMatchResult();
		results.matches = new ArrayList<TextMatch>();
		results.result_id = text_id;

		int patternsComplete = 0;
		for (RegexPattern pat : patterns.get_patterns()) {

			log.debug("pattern={}", pat.id);

			if (!pat.enabled) {
				// results.message = "pattern=" + pat.id + " not enabled. ";
				log.debug("CFG pattern={} not enabled.", pat.id);
				continue;
			}

			Matcher match = pat.regex.matcher(text);
			results.evaluated = true;

			while (match.find()) {

				DateMatch dt = new DateMatch();

				dt.pattern_id = pat.id;
				dt.start = match.start();
				dt.end = match.end();
				dt.setText(match.group());

				try {

					DateNormalization.normalize_date(
							patterns.group_map(pat, match), dt);
					if (dt.datenorm == null) {
						continue;
					}

					dt.datenorm_text = DateNormalization
							.format_date(dt.datenorm);
					results.pass = true;

				} catch (Exception err) {
					// Not a date.
					results.pass = false;
					continue;
				}

				results.matches.add(dt);
			}

			patternsComplete++;
			updateProgress(patternsComplete
					/ (double) patterns.get_patterns().size() + 1);
		}

		results.pass = !results.matches.isEmpty();

		PatternManager.reduce_matches(results.matches);

		return results;
	}

	/**
	 * 
	 * @param file
	 */
	public void test(String file) {
		systemTests();
	}

	/**
	 * 
	 * @param flag
	 */
	public void match_DateTime(boolean flag) {
		((PatternManager) patterns).enable_pattern_family(
				XTConstants.DATETIME_FAMILY, flag);
	}

	/**
	 * 
	 * @param flag
	 */
	public void match_MonDayYear(boolean flag) {
		((PatternManager) patterns).enable_pattern_family(
				XTConstants.MDY_FAMILY, flag);
	}

	/**
     */
	public void adhocTests() {
		log.info("=== SYSTEM TESTS START ===");

		match_MonDayYear(true);
		match_DateTime(false);

		String[] tests = { "12/30/90", "JUN 00", "JUN '13", "JUN '12",
				"JUN '17", "JUN '33", "JUN 2017", "JUN 1917" };

		try {
			XTTestUtility tester = new XTTestUtility(
					"./results/xtemp_Adhoc.csv");

			int count = 0;
			for (String tst_text : tests) {
				++count;
				TextMatchResult results = extract_dates(tst_text, "" + count);
				results.add_trace("Test Payload: " + tst_text);

				if (!results.evaluated) {
					continue;
				}

				log.info("=========SYSTEM TEST "
						+ count
						+ " FOUND:"
						+ (results.matches == null ? "NOTHING"
								: results.matches.size()));
				tester.save_result(results);

			}
			tester.close_report();

		} catch (Exception err) {
			log.error("Not finishing tests", err);
			return;
		}
		log.info("=== SYSTEM TESTS DONE ===");

	}

	/**
     *
     */
	public void systemTests() {
		log.info("=== SYSTEM TESTS START ===");

		match_MonDayYear(true);
		match_DateTime(true);

		try {
			XTTestUtility tester = new XTTestUtility(
					"./results/xtemp_System.csv");

			for (PatternTestCase tst : patterns.testcases) {
				TextMatchResult results = extract_dates(tst.text, tst.id);
				results.add_trace("Test Payload: " + tst.text);

				if (!results.evaluated) {
					continue;
				}

				log.info("=========SYSTEM TEST "
						+ tst.id
						+ " FOUND:"
						+ (results.matches == null ? "NOTHING"
								: results.matches.size()));
				tester.save_result(results);

			}
			tester.close_report();

		} catch (Exception err) {
			log.error("Not finishing tests", err);
			return;
		}
		log.info("=== SYSTEM TESTS DONE ===");
	}

	/**
     *
     */
	public static void usage() {
		System.out.println("\tXTemporal -f     -- run system tests."
				+ "\n\tMore operations coming...");
	}

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		boolean debug = true;
		// default test patterns, run test/debug mode.
		XTemporal xdt = new XTemporal(debug);
		boolean systemTest = false;
		boolean adhocTest = false;

		try {
			gnu.getopt.Getopt opts = new gnu.getopt.Getopt("XTemporal", args,
					"fa");
			int c;
			while ((c = opts.getopt()) != -1) {
				switch (c) {
				case 'f':
					systemTest = true;
					break;
				case 'a':
					adhocTest = true;
					break;
				default:
					XTemporal.usage();
					System.exit(1);
				}
			}
		} catch (Exception err) {
			// xdterr.printStackTrace();
			XTemporal.usage();
			System.exit(1);
		}

		try {
			xdt.configure();

			if (systemTest) {
				System.out.println("\tSYSTEM TESTS=======\n");
				xdt.systemTests();
			}
			if (adhocTest) {
				System.out.println("\tADHOC TESTS=======\n");
				xdt.adhocTests();
			}
		} catch (ConfigException exErr) {
			exErr.printStackTrace();
		}

	}

}
