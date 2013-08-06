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
import java.util.List;


//import org.mitre.opensextant.placedata.Geocoord;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.util.GeodeticUtility;


/**
 * The Scorer attempts to quantify (using a range between -1.0 and 1.0) the similarity
 * between a Place Candidate which has been found in a document with matches
 * from the gazetteer. The score consists of a weighted average of four one
 * dimensional similarity measurements:
 * <ul>
 * <li>The similarity between the name in the document and the name in the
 * gazetteer
 * </li><li>The geometric distance between a geocoord associated with the name in the
 * document and the geocoordinate of the name in the gazetteer
 * </li><li>The similarity/compatibility of feature type information associated with
 * the name in the document and the type of the feature in the gazetteer.
 * </li><li>The similarity/compatibility between the admin structure associated with
 * the name in the document and and the structure of the feature in the
 * gazetteer.
 * </li></ul>
 */

public class Scorer {

	// TODO Need deterministic technique to estimate weights
	
	// weight of apriori info
	final double biasWeight = 0.1;
	
	// weights of the four evidence information types
	final double nameWeight = 0.01;
	final double geocoordWeight = 1.90;
	final double featureTypeWeight = 0.5;
	final double adminStructureWeight = 1.5;

	// the sum of the component weights (for normalizing the total score)
	final double totalWeight = nameWeight + geocoordWeight + featureTypeWeight + adminStructureWeight;

	// document level country and admin1  evidence
	List<PlaceEvidence> docEvidList = new ArrayList<PlaceEvidence>();
	
	
	// the minimum value returned by scoreEvidence to avoid multiplying
	// everything by zero
	final Double MinEvidenceValue = .00001;

	// a flag value to indicate no evidence was found
	final String NO_EVIDENCE = "NO_EVIDENCE";

	// name similarity scores
	final double NameSimilarityCaseInsens = 0.90;
	final double NameSimilarityFloor = 0.50;

	// geocoord similarity thresholds and score
	final double geoInnerDist = 0.5; // degrees ~ 55km
	final double geoOuterDist = 2.5; // degrees ~ 280km

	final double geoNearScore = 1.0;
	final double geoMidScore = 0.5;
	final double geoFarScore = -1.0;

	// feature class/code similarity scores
	final double featureTypeClassScore = 0.8;
	final double featureTypeConfusedScore = 0.2;

	// admin structure similarity scores
	final double countryOnlyScore = 0.9;
	final double countryDifferentAdminScore = .75;

	/**
	 * Score each of the place candidates
	 * 
	 * @param pcList
	 */
	public void score(List<PlaceCandidate> pcList) {

		// for each PC in the list
		for (PlaceCandidate pc : pcList) {
			// for each Place on the PC
			for (Place p : pc.getPlaces()) {
				//System.out.println();
				
				double prior = biasWeight * p.getId_bias();
				double evidenceScore = scoreEvidence(p, pc.getEvidence());

				//double totalScore = prior * evidenceScore;
				double totalScore = (prior + evidenceScore)/2.0 ;
				pc.setPlaceScore(p, totalScore);
				// System.out.println("Setting final score for " +p.toString() + "=" + prior +" +" + evidenceScore + "=" +  totalScore );
				//pc.incrementPlaceScore(p, biasWeight * prior);

			}

		}

	}

	/**
	 *  calculate the total score of a place versus all the evidence
	 * @param p
	 * @param evidList
	 * @return
	 */
	private double scoreEvidence(Place p, List<PlaceEvidence> evidList) {

		double prodScore = 1.0;
		double sumScore = 0.0;
		
		// TODO if there is evidence but no country and admin evidence
		// use document level evidence
		// if no evidence from candidate, use document level evidence
		if (evidList.size() == 0) {
			
			if(docEvidList.size() > 0){
				//System.out.println("Using doc level evidence");
				//System.out.println(docEvidList.toString());
				evidList = docEvidList;
			}else{
				// no local or document level evidence
				return 0.0;
			}
			
			
		
		}

		// for each bit of evidence
		for (PlaceEvidence ev : evidList) {
			// compare the evidence to the place
			Double singleScore = score(ev, p);
			// running product of the scores
			prodScore = prodScore * singleScore;

			// running sum of the scores
			sumScore = sumScore + singleScore;
		}

		//return prodScore;
		double avgScore = sumScore / evidList.size();
		return avgScore;
	}

	/**
	 * The scoring function which calculates the similarity between a place and
	 * a single piece of evidence
	 * 
	 * @param ev
	 * @param place
	 * @return
	 */
	private double score(PlaceEvidence ev, Place place) {
		double nameScore = scoreName(ev, place);
		double geocoordScore = scoreGeocoord(ev, place);
		double featureTypeScore = scoreFeatureType(ev, place);
		double adminStructureScore = scoreAdminStructure(ev, place);

		// calculate the total score from the weighted component scores
		double totalScore = (nameWeight * nameScore + geocoordWeight
				* geocoordScore + featureTypeWeight * featureTypeScore + adminStructureWeight
				* adminStructureScore)
				/ totalWeight;
		// System.out.println("\tComparing " + ev.toString() + "\t" +place.toString() + "\t" + totalScore );

		return totalScore;

	}

	/* ---------------------Name Similarity ------------------ */

	/**
	 * Compare the name as found in the document with the name in the candidate
	 * as found in the gazetteer Exact match =1.0 Exact match (ignoring case) =
	 * <NameSimilarityCaseInsens> .... Any condition not caught in the above =
	 * <NameSimilarityFloor>
	 * 
	 * Range = 0.0 -> 1.0 (no negative score)
	 * 
	 */

	private double scoreName(PlaceEvidence evidence, Place place) {

		// the two names to be compared
		String evidenceName = evidence.getPlaceName();
		String gazetteerName = place.getPlaceName();
		double weight = evidence.getWeight();

		if (evidenceName == null) {
			return 0.0;
		}

		// full credit for exact match
		if (evidenceName.equals(gazetteerName)) {
			return 1.0 * weight;
		}

		// partial credit for case differences
		if (evidenceName.equalsIgnoreCase(gazetteerName)) {
			return NameSimilarityCaseInsens * weight;
		}

		// TODO other similarity conditions between case insensitive and the
		// floor? Diacritics? other phonetic?

		// floor value
		if (weight > 0.0) {
			return NameSimilarityFloor * weight;
		} else {
			return MinEvidenceValue; // no opinion on double negative
		}

	}

	/* ---------------------Geocoord Similarity ------------------ */

	/**
	 * Compare the geocoord evidence from the document with the location
	 * (geocoord) of the candidate as found in the gazetteer. Comparison is
	 * based on dist between evidence and gazetteer place. Range = -1.0 -> 1.0
	 * 
	 * NOTE: these thresholds really should be modulated by FeatureType rather
	 * than be absolutes
	 * 
	 * @param evidence
	 * @param place
	 * @return
	 */
	private double scoreGeocoord(PlaceEvidence evidence, Place place) {

		// the two geocoords to compare
		//Geocoord evidenceGeo = evidence.getGeocoord();
		//Geocoord gazetteerGeo = place.getGeocoord();

		// no evidence zero score
		//if (evidence.g == null) {
		//	return MinEvidenceValue;
		//}

                double weight = evidence.getWeight();

		// distance between candidate and evidence (in degrees)
		//double dist = gazetteerGeo.distanceDeg(evidenceGeo);
                double dist = GeodeticUtility.distanceDegrees(
                        evidence.getLatitude(), evidence.getLongitude(), 
                        place.getLatitude(), place.getLongitude());
		
		// non-null coords, check the thresholds

		// if near
		if (dist < geoInnerDist) {
			return geoNearScore * weight;
		}

		// if mid range
		if (dist > geoInnerDist && dist < geoOuterDist) {
			return geoMidScore * weight;
		}

		// must be far away
		if (weight > 0.0) {
			return MinEvidenceValue;
		} else {
			return MinEvidenceValue; // no opinion on double negative
		}
	}

        
	/* ---------------------Feature Type Similarity ------------------ */
	/**
	 * Compare the similarity/compatibility of the feature type evidence as
	 * found in the document with the feature type info from the place in the
	 * gazetteer. Range = -1.0 <-> 1.0
	 * 
	 * @param evidence
	 * @param place
	 * @return
	 */
	private double scoreFeatureType(PlaceEvidence evidence, Place place) {

		// get the two sets of feature type info (code and class)
		String evidenceFClass = evidence.getFeatureClass();
		String evidenceFCode = evidence.getFeatureCode();
		double weight = evidence.getWeight();

		String gazetteerFClass = place.getFeatureClass();
		String gazetteerFCode = place.getFeatureCode();

		// no evidence, zero score
		if (evidenceFClass == null) {
			return MinEvidenceValue;
		}

		if (evidenceFCode == null) {
			evidenceFCode = NO_EVIDENCE;
		}

		// perfect match
		if (evidence.isSameFeature(place)) {
			return 1.0 * weight;
		}

		// class level match, no code level info
		if (evidenceFClass.equals(gazetteerFClass)
				&& evidenceFCode.equals(NO_EVIDENCE)) {
			return featureTypeClassScore * weight;
		}

		// partial credit for the commonly confused classes of A,L and P
		if (isAreaFeature(evidenceFClass) && isAreaFeature(gazetteerFClass)) {
			return featureTypeConfusedScore * weight;
		}

		
		// partial credit for places used to describe spot features e.g. "the Washington office" 
		if (isSiteFeature(evidenceFClass) && isAreaFeature(gazetteerFClass)) {
			return featureTypeConfusedScore * weight;
		}
		
		// floor value for "important" gazetteer entries
		if (place.isAdministrative()) {
			return featureTypeConfusedScore * weight;
		}
		
		
		
		// must be incompatible class/code
		if (weight > 0.0) {
			return MinEvidenceValue;
		} else {
			return MinEvidenceValue; // no opinion on double negative
		}
	}

        /** Abstraction on feature match: ALP = admin area, land area or populated place.
         */
        protected static boolean isAreaFeature(String cls){
            return ("A".equals(cls) || "L".equals(cls) || "P".equals(cls));
        }
        
        protected static boolean isSiteFeature(String cls){
            return "S".equals(cls);
        }
        
	/* ---------------------Admin Structure Similarity ------------------ */
	/**
	 * Compare the similarity/consistency of the administrative structure
	 * evidence found in the document with that of the place from the gazetteer.
	 * Range = -1.0 -> 1.0
	 * 
	 * @param evidence
	 * @param place
	 * @return
	 */
	private double scoreAdminStructure(PlaceEvidence evidence, Place place) {

		// get the two sets of admin info (country and admin1)
		String evidenceCountry = evidence.getCountryCode();
		String evidenceAdm1 = evidence.getAdmin1();

		String gazetteerCountry = place.getCountryCode();
		String gazetteerAdm1 = place.getAdmin1();

		Double weight = evidence.getWeight();

		// no evidence, zero score
		if (evidenceCountry == null) {
			return MinEvidenceValue;
		}

		if (evidenceAdm1 == null) {
			evidenceAdm1 = NO_EVIDENCE;
		}

		// perfect match
		if (evidenceCountry.equals(gazetteerCountry)
				&& evidenceAdm1.equals(gazetteerAdm1)) {
			return 1.0 * weight;
		}

		// country match, no admin1 evidence
		if (evidenceCountry.equals(gazetteerCountry)
				&& evidenceAdm1.equals(NO_EVIDENCE)) {
			return countryOnlyScore * weight;
		}

		// country match, incompatible admin1 evidence
		if (evidenceCountry.equals(gazetteerCountry)
				&& !evidenceAdm1.equals(gazetteerAdm1)) {
			return countryDifferentAdminScore * weight;
		}

		// there should never be a case where the adm1 is correct but the
		// country is not

		// must be incompatible

		if (weight > 0.0) {
			return MinEvidenceValue;
		} else {
			return MinEvidenceValue; // no opinion on double negative
		}

	}

	
	public void setDocumentLevelEvidence(List<PlaceEvidence> docEvidList){
		this.docEvidList = docEvidList;
	}
	
	
	
}
