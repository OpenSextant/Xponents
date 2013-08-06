/**
 Copyright 2009-2013 The MITRE Corporation.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.


 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
**/

package org.opensextant.extractors.geo.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.PlaceEvidence.Scope;

/**
 * The Cantilever calculates an aggregate is-place confidence score for PlaceCandidates
 * that share a common base name. It also propagates each PlaceCandidate's
 * place evidence across all PlaceCandidates with the same base name.
 */
public class Cantilever {

	// the factor to weigh evidence which has been propagated (< 1.0)
	private static double WeightPropFactor = 0.75;
	// the factor to weigh place confidence which has been propagated (< 1.0)
	private static double ConfidencePropFactor = 0.75;
	// the factor to weigh document level country information(< 1.0)
	//private static Double CountryPropFactor = 0.1;
	// the factor to weigh document level admin1 information(< 1.0)
	//private static Double AdminPropFactor = 0.1;

	// the inexact matcher used to find the "same" place
	//private static Phoneticizer phoner = new Phoneticizer();

	// which phonetic/inexact algorithm to use
	// private static String phoneticAlgName = "SimplePhonetic0";


	/**
	 * Iterate through a List of PlaceCandidates to determine is-place confidence scores and
	 * propagate place evidence.
	 *
	 * @param pcList The PlaceCandidates found in a document
	 */
	public static void propagateEvidence(List<PlaceCandidate> pcList) {

		// a Map to hold the PCs, organized by their name
		Map<String, List<PlaceCandidate>> pcsByName = new HashMap<String, List<PlaceCandidate>>();

		// first organize the PCs by name.
		for (PlaceCandidate pc : pcList) {

			// get the name to be used as the key
			String fullName = pc.getText();
			String baseName = pc.getTextnorm();

			// put the PC into the Map
			if (!pcsByName.containsKey(baseName)) {
				List<PlaceCandidate> tmp = new ArrayList<PlaceCandidate>();
				pcsByName.put(baseName, tmp);
			}
			// add the PC to the name's list
			pcsByName.get(baseName).add(pc);
		}

		// Now,propagate evidence among all PCs with the same name

		// for each name
		for (String name : pcsByName.keySet()) {
			// get all the PCs for this name
			List<PlaceCandidate> pcs = pcsByName.get(name);

			// nothing to propagate if only one PC
			if (pcs.size() > 1) {
				// create a List to hold the joint evidence
				List<PlaceEvidence> jointEvidence = new ArrayList<PlaceEvidence>();
				// the sum of place confidences
				Double totalPlaceConfidence = 0.0;

				// accumulate all the evidence into one joint evidence list
				// for each PC with this name
				for (PlaceCandidate pc : pcs) {
					// accumulate the place confidence scores
					totalPlaceConfidence = totalPlaceConfidence + pc.getPlaceConfidenceScore();
					// for each bit of evidence on this PC
					for (PlaceEvidence e : pc.getEvidence()) {
						// only propagate LOCAL scope evidence
						if (e.getScope().equals(PlaceEvidence.Scope.LOCAL)) {
							// create a new Evidence based on the existing
							PlaceEvidence tmpEvid = new PlaceEvidence(e);
							// adjust the weight and scope to show that it is
							// coreferenced evidence
							tmpEvid.setWeight(WeightPropFactor
									* tmpEvid.getWeight());
							tmpEvid.setScope(Scope.COREF);
							// add to joint list
							jointEvidence.add(tmpEvid);
						}// end if local block
							// TODO if we were collecting DOCUMENT level
							// evidence, we could do it here
					}// end evidence loop for a PC
				}// end evidence loop for all PCs same name

				// convert total place confidence to (an approx) average by
				// dividing by number of PCs which contributed their (LOCAL) evidence
				totalPlaceConfidence = totalPlaceConfidence / pcs.size();
				// adjust confidence for being co-referenced
				totalPlaceConfidence = ConfidencePropFactor
						* totalPlaceConfidence;

				// Now do the actual propagation by adding the jointEvidence (if
				// any) and the averaged place confidence score to each PC
				for (PlaceCandidate p : pcs) {
					// don't add zero confidences and only propagate to no opinion pcs
					if (totalPlaceConfidence != 0.0 && p.getPlaceConfidenceScore() == 0.0) {
						p.addRuleAndConfidence("CoRefConfidence",totalPlaceConfidence);
					}
					// if we have any evidence to propagate
					if (jointEvidence.size() > 0) {
						p.getEvidence().addAll(jointEvidence);
					}

				}
			}
		}// end propagate same name loop


		// TODO it would be better to aggregate the evidence into
		// n (ideally one) consistent PlaceEvidence(s).

	}
}
