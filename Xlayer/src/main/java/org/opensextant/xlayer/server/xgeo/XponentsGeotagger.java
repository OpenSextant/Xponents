package org.opensextant.xlayer.server.xgeo;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.xlayer.Transforms;
import org.opensextant.xlayer.server.RequestParameters;
import org.opensextant.xlayer.server.TaggerResource;
import org.restlet.data.CharacterSet;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;

/**
 * 
 */
public class XponentsGeotagger extends TaggerResource {

	/**
	 * Restlet resource that pulls its configuration from Context.
	 */
	public XponentsGeotagger() {
		super();
		log = getContext().getCurrentLogger();
	}

	public Extractor getExtractor() {
		PlaceGeocoder xgeo = (PlaceGeocoder) this.getApplication().getContext()
				.getAttributes().get("xgeo");
		if (xgeo == null) {
			info("Misconfigured, no context-level pipeline initialized");
			return null;
		}
		return xgeo;
	}

	/**
	 * Process the text for the given document.
	 *
	 * @param input     the input
	 * @param jobParams the job params
	 * @return the representation
	 */
	public Representation process(TextInput input, RequestParameters jobParams) {

		if (input == null || input.buffer == null) {
			return status("FAIL", "No text");
		}
		debug("Processing plain text doc");

		++requestCount;
		try {
			if (prodMode) {
				PlaceGeocoder xgeo = (PlaceGeocoder) getExtractor();

				List<TextMatch> matches = xgeo.extract(input);
				/*
				 * formulate matches as JSON output.
				 */
				return format(matches, jobParams);
			}

		} catch (Exception processingErr) {
			error("Failure on doc " + input.id, processingErr);
			return status("FAIL", processingErr.getMessage() + "; requests=" + requestCount);
		}

		return status("TEST", "nothing done in test with doc=" + input.id);
	}

	/**
	 * Copy the basic match information
	 * 
	 * @param m
	 * @return
	 * @throws JSONException
	 */
	private JSONObject populateMatch(TextMatch m) throws JSONException {

		JSONObject o = new JSONObject();
		int len = m.end - m.start;
		o.put("offset", m.start);
		o.put("length", len);
		// String matchText = TextUtils.squeeze_whitespace(name.getText());
		o.put("matchtext", m.getText());
		return o;
	}

	private Representation format(List<TextMatch> matches, RequestParameters jobParams) throws JSONException {

		Representation result = null;
		int tagCount = 0;

		JSONObject resultContent = new JSONObject();
		JSONObject resultMeta = new JSONObject();
		resultMeta.put("status", "ok");
		resultMeta.put("numfound", 0);
		JSONArray resultArray = new JSONArray();

		/*
		 * Super loop: Iterate through all found entities. record Taxons as
		 * person or orgs record Geo tags as country, place, or geo. geo =
		 * geocoded place or parsed coordinate (MGRS, DMS, etc)
		 * 
		 */
		for (TextMatch name : matches) {

			/*            
			 * ==========================
			 * ANNOTATIONS: non-geographic entities that are filtered out, but worth tracking
			 * ==========================             
			 */
			if (name instanceof TaxonMatch) {
				if (jobParams.output_taxons) {

					TaxonMatch match = (TaxonMatch) name;
					++tagCount;
					for (Taxon n : match.getTaxons()) {
						JSONObject node = populateMatch(name);
						String t = "taxon";
						if (n.name.startsWith("org")) {
							t = "org";
						} else if (n.name.startsWith("person")) {
							t = "person";
						}
						node.put("type", t);
						node.put("taxon", n.name); // Name of taxon
						node.put("catalog", n.catalog); // Name of catalog or
														// source
						// node.put("filtered-out", true);

						resultArray.put(node);
					}
				}
				continue;
			}

			/*
			 * ==========================
			 * FILTERING
			 * ==========================
			 */
			// Ignore non-place tags
			if (name.isFilteredOut() || !(name instanceof PlaceCandidate || name instanceof GeocoordMatch)) {
				continue;
			}

			JSONObject node = populateMatch(name);

			/*
			 * ==========================
			 * ANNOTATIONS: coordinates
			 * ==========================
			 */
			if (name instanceof GeocoordMatch) {
				++tagCount;
				GeocoordMatch geo = (GeocoordMatch) name;
				node.put("type", "coordinate");
				Transforms.createGeocoding(geo, node);
				resultArray.put(node);
				continue;
			}

			if (name.isFilteredOut()) {
				debug("Filtered out " + name.getText());
				continue;
			}

			PlaceCandidate place = (PlaceCandidate) name;
			Place resolvedPlace = place.getChosen();

			/*
			 * ==========================
			 * ANNOTATIONS: countries, places, etc.
			 * ==========================
			 */
			/*
			 * Accept all country names as potential geotags Else if name can be
			 * filtered out, do it now. Otherwise it is a valid place name to
			 * consider
			 */
			++tagCount;
			if (place.isCountry) {
				node.put("type", "country");
				node.put("cc", resolvedPlace.getCountryCode());
			} else {

				/*
				 * Conf = 20 or greater to be geocoded.
				 */
				Transforms.createGeocoding(resolvedPlace, node);
				node.put("type", "place");
				node.put("confidence", place.getConfidence());
				if (place.getConfidence() <= 10) {
					node.put("filtered-out", true);
				}
			}
			resultArray.put(node);
		}
		resultMeta.put("numfound", tagCount);
		resultContent.put("response", resultMeta);
		resultContent.put("annotations", resultArray);

		result = new JsonRepresentation(resultContent.toString(2));
		result.setCharacterSet(CharacterSet.UTF_8);

		return result;
	}

	/**
	 * Must explicitly stop Solr multi-threading. 
	 */
	@Override
	public void stop() {
		Extractor x = getExtractor();
		if (x != null) {
			x.cleanup();
		}
		System.exit(0);

	}
}
