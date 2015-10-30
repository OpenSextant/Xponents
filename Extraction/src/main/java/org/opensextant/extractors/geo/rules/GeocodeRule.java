/**
 * Copyright 2014 The MITRE Corporation.
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
 */

package org.opensextant.extractors.geo.rules;

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GeocodeRule {

	public int WEIGHT = 0; /* of 100 */
	public String NAME = null;

	protected Logger log = LoggerFactory.getLogger("geocode-rule");

	protected void log(String msg) {
		log.debug("{}: {}", NAME, msg);
	}

	protected void log(String msg, String val) {
		log.debug("{}: {} / value={}", NAME, msg, val);
	}

	/**
	 *
	 * @param names
	 *            list of found place names
	 */
	public void evaluate(List<PlaceCandidate> names) {
		for (PlaceCandidate name : names) {
			// Each rule must decide if iterating over name/geo combinations
			// contributes
			// evidence. But can just as easily see if name.chosen is already
			// set and exit early.
			//
			/*
			 * This was filtered out already so ignore.
			 */
			if (name.isFilteredOut()) {
				continue;
			}
			// Some rules may choose early -- and that would prevent other rules
			// from adding evidence
			// In this scheme.
			if (name.getChosen() != null) {
				// DONE
				continue;
			}

			for (Place geo : name.getPlaces()) {
				evaluate(name, geo);
				if (name.getChosen() != null) {
					// DONE
					break;
				}
			}
		}

	}

	/**
	 * The one evaluation scheme that all rules must implement.
	 * Given a single text match and a location, consider if the geo is a good geocoding
	 * for the match.
	 * @param name matched name in text
	 * @param geo gazetteer entry or location
	 */
	public abstract void evaluate(PlaceCandidate name, Place geo);

	/**
	 * no-op, unless overriden.
	 */
	public void reset() {

	}
}
