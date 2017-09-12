package org.opensextant.extractors.test;

import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.Place;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extractors.geo.SolrGazetteer;

public class TestPlaceLookup {

	public static void defaultTest() throws ConfigException, ExtractionException {
		/*
		 * Raw findings for 北京市 P: 北京市 (22, CN, ADM1)
		 * 
		 * // now find all romanized names for that same name (may be different
		 * places).
		 * 
		 * Romanized findings 7 P: Pei-p'ing Shih (22, CN, ADM1) P: Peiping
		 * Municipal Administrative Area (22, CN, ADM1) P: Peiping Municipality
		 * (22, CN, ADM1) P: Peking Municipality (22, CN, ADM1) P: Peking (22,
		 * CN, ADM1) P: Beijing Shi (22, CN, ADM1) P: Beijing (22, CN, ADM1)
		 */

		genericTest("北京市", "-feat_class:P", 2);
	}

	public static void genericTest(String n, String parametricQuery, int tolerance)
			throws ConfigException, ExtractionException {
		SolrGazetteer gaz = new SolrGazetteer();
		/* NAME QUERY */
		List<Place> loc = gaz.findPlaces(n, parametricQuery, tolerance);
		System.out.println("Raw findings");
		for (Place l : loc) {
			System.out.println("P: " + l);
		}
		List<Place> locLatin = gaz.findPlacesRomanizedNameOf(n, parametricQuery, tolerance);
		System.out.println("\n\nRomanized findings " + locLatin.size());
		for (Place l : locLatin) {
			System.out.println("P: " + l);
		}
		gaz.close();

	}

	/**
	 * Lookup a place named N, and has parameters
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			if (args.length == 0) {
				defaultTest();
			} else {
				genericTest(args[0], args[1], 2);
			}
		} catch (ConfigException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExtractionException e2) {
			e2.printStackTrace();
		}

	}

}
