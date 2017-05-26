package org.opensextant.xlayer;

import org.json.JSONException;
import org.json.JSONObject;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.util.GeodeticUtility;

public class Transforms {

	/**
	 * Convert JSON object for an annotation into a Xponents TextMatch instance.
	 * Parsing data from JSON/REST representations has very limited capability compared to
	 * using Java API for processing routines directly.
	 * 
	 * @param data
	 * @return
	 * @throws JSONException
	 */
	public static TextMatch parseAnnotation(Object data) throws JSONException {
		if (!(data instanceof JSONObject)) {
			return null;
		}
		TextMatch m = null;
		JSONObject a = (JSONObject) data;

		TaxonMatch x = null;
		String typ = a.getString("type");
		String text = a.getString("matchtext");
		switch (typ) {

		case "place":
			PlaceCandidate placeMatch = new PlaceCandidate();
			Place geo = new Place();
			placeMatch.setText(text);
			Transforms.parseGeocoding(geo, a);
			placeMatch.setConfidence(a.optInt("confidence", -1));
			placeMatch.choose(geo);

			m = placeMatch;
			break;

		case "coordinate":
			GeocoordMatch coord = new GeocoordMatch();
			Place coordLoc = new Place();
			coord.setText(text);
			// How awful:.... need to parse Coord directly
			Transforms.parseGeocoding(coordLoc, a);
			coord.setLatLon(coordLoc);
			coord.setMethod(coordLoc.getMethod());

			/* TODO: GeocoordMatch needs to support setters for Geocoding here.
			 * missing reverse geo info
			 * 
			 *  cc, adm1
			 *  
			 */
			m = coord;
			break;

		case "country":
			PlaceCandidate countryMatch = new PlaceCandidate();
			Place cc = new Place();
			countryMatch.setText(text);
			cc.setName(text);
			countryMatch.setConfidence(a.optInt("confidence", -1));
			cc.setCountryCode(a.getString("cc"));
			countryMatch.isCountry = true;
			countryMatch.choose(cc);
			m = countryMatch;

			break;

		case "person":
			x = new TaxonMatch();
			Transforms.parseTaxon(x, "person", a);
			m = x;
			break;

		case "org":
			x = new TaxonMatch();
			Transforms.parseTaxon(x, "org", a);
			m = x;
			break;

		case "taxon":
			x = new TaxonMatch();
			Transforms.parseTaxon(x, "taxon", a);
			m = x;
			break;

		default:
			throw new JSONException("Unknown Annotation " + typ);
		}

		m.setType(typ);
		m.start = a.getInt("offset");
		m.end = m.start + a.getInt("length");

		return m;
	}

	/**
	 * Parse out a taxon from JSON/REST
	 * @param x a taxon object
	 * @param t type of taxon
	 * @param a JSON annotation
	 */
	public static void parseTaxon(TaxonMatch x, String t, JSONObject a) {
		if (a.has("taxon")) {
			Taxon tx = new Taxon();
			tx.setName(a.getString("taxon"));
			tx.catalog = a.getString("catalog");
			x.addTaxon(tx);
		}
		x.setType(t);
	}

	/**
	 * Detect trivial 0,0 or 0.000, 0.000, etc. coordinates.
	 *
	 * @param y
	 *            the y
	 * @param x
	 *            the x
	 * @return true, if is valid non zero coordinate
	 */
	public static final boolean isValidNonZeroCoordinate(double y, double x) {
		if (GeodeticUtility.validateCoordinate(y, x)) {
			return (y != 0 && x != 0);
		}
		return false;
	}

	/**
	 * Given an existing JSON object, add geocoding metadata to it.
	 * 
	 * @param geo
	 * @param node
	 * @throws JSONException
	 */
	public static final void createGeocoding(Geocoding geo, JSONObject node) throws JSONException {
		if (geo.getCountryCode() != null) {
			node.put("cc", geo.getCountryCode());
		}
		if (geo.getAdmin1() != null) {
			node.put("adm1", geo.getAdmin1());
		}
		if (geo.getFeatureClass() != null) {
			node.put("feat_class", geo.getFeatureClass());
			node.put("feat_code", geo.getFeatureCode());
		}
		if (isValidNonZeroCoordinate(geo.getLatitude(), geo.getLongitude())) {
			node.put("prec", geo.getPrecision());
			node.put("lat", geo.getLatitude());
			node.put("lon", geo.getLongitude());
		}
		if (geo.getMethod() != null) {
			node.put("method", geo.getMethod());
		}
		if (geo.getAdminName() != null) {
			node.put("adm1_name", geo.getAdminName());
		}
	}

	/**
	 * Given a JSON object, parse fields relevant to the geocoding and populate that JSON data
	 * 
	 * @param geo
	 * @param node
	 * @throws JSONException
	 */
	public static final void parseGeocoding(Place geo, JSONObject node) throws JSONException {
		if (node.has("cc")) {
			geo.setCountryCode(node.getString("cc"));
		}
		if (node.has("adm1")) {
			geo.setAdmin1(node.getString("adm1"));
		}
		if (node.has("feat_class")) {
			geo.setFeatureClass(node.getString("feat_class"));
		}
		if (node.has("feat_code")) {
			geo.setFeatureCode(node.getString("feat_code"));
		}
		if (node.has("lat")) {
			geo.setLatitude(node.getDouble("lat"));
			geo.setLongitude(node.getDouble("lon"));
			if (node.has("prec")) {
				geo.setPrecision(node.getInt("prec"));
			}
		}
		if (node.has("method")) {
			geo.setMethod(node.getString("method"));
		}
		if (node.has("adm1_name")) {
			geo.setAdminName("adm1_name");
		}

		/* Name overrides matchtext or text.
		 */
		if (node.has("text")) {
			geo.setPlaceName(node.getString("text"));
		}
		if (node.has("matchtext")) {
			geo.setPlaceName(node.getString("matchtext"));
		}
		if (node.has("name")) {
			geo.setPlaceName(node.getString("name"));
		}
	}
}
