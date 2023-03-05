package org.opensextant.output;

import java.util.Date;
import java.util.List;
import java.util.Map;

import jodd.json.JsonArray;
import jodd.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.data.MatchSchema;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtemporal.DateMatch;
import org.opensextant.processing.Parameters;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.GeonamesUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transforms  implements MatchSchema {

    public static final String FLD_FILTERED = "filtered-out";
    public static final String FLD_TYPE = "type";
    public static final String FLD_MATCH_ID = "match-id";

    /**
     * Convert JSON object for an annotation into a Xponents TextMatch instance.
     * Parsing data from
     * JSON/REST representations has very limited capability compared to using Java
     * API for processing
     * routines directly.
     *
     * @param data
     * @return TextMatch object represented by json annotation
     * @see org.opensextant.annotations.AnnotationHelper utility and package. This
     * Annotation approach is more general
     * with respect to the data model overall. This Transforms utility is about
     * transforming matches directly to JSON output
     * ready for RESTful response.
     */
    public static TextMatch parseAnnotation(Object data) {
        if (!(data instanceof JsonObject)) {
            return null;
        }
        TextMatch m = null;
        JsonObject a = (JsonObject) data;

        String typ = a.getString(FLD_TYPE);
        String text = a.getString("matchtext");
        int start = a.getInteger("offset");
        int len = a.getInteger("length");
        int end = start + len;

        switch (typ) {

            case VAL_PLACE:
                PlaceCandidate placeMatch = new PlaceCandidate(start, end);
                Place geo = new Place();
                placeMatch.setText(text);
                Transforms.parseGeocoding(geo, a);
                placeMatch.setConfidence(a.getInteger("confidence", -1));
                placeMatch.setChosenPlace(geo);

                m = placeMatch;
                break;

            case VAL_COORD:
            case "coordinate":
                GeocoordMatch coord = new GeocoordMatch(start, end);
                Place coordLoc = new Place();
                coord.setText(text);
                // How awful:.... need to parse Coord directly
                Transforms.parseGeocoding(coordLoc, a);
                coord.setLatLon(coordLoc);
                coord.setMethod(coordLoc.getMethod());

                /*
                 * TODO: GeocoordMatch needs to support setters for Geocoding here. missing
                 * reverse geo info
                 * cc, adm1
                 */
                m = coord;
                break;

            case VAL_COUNTRY:
                PlaceCandidate countryMatch = new PlaceCandidate(start, end);
                Place cc = new Place();
                countryMatch.setText(text);
                cc.setName(text);
                countryMatch.setConfidence(a.getInteger("confidence", -1));
                cc.setCountryCode(a.getString("cc"));
                countryMatch.isCountry = true;
                countryMatch.setChosenPlace(cc);
                m = countryMatch;

                break;

            case "person":
                m = new TaxonMatch(start, end);
                Transforms.parseTaxon((TaxonMatch) m, "person", a);
                break;

            case "org":
                m = new TaxonMatch(start, end);
                Transforms.parseTaxon((TaxonMatch) m, "org", a);
                break;

            case VAL_TAXON:
                m = new TaxonMatch(start, end);
                Transforms.parseTaxon((TaxonMatch) m, VAL_TAXON, a);
                break;

            case "date":
                DateMatch dt = new DateMatch(start, end);
                Transforms.parseDate(dt, a);
                m = dt;
                break;

            default:
                throw new jodd.json.JsonException("Unknown Annotation " + typ);
        }

        m.setType(typ);
        m.start = a.getInteger("offset");
        m.end = m.start + a.getInteger("length");
        m.setFilteredOut(a.getBoolean(FLD_FILTERED));

        return m;
    }

    /**
     * First stab at deserializing JSON date annotation.
     *
     * @param d datematch
     * @param a json annot
     */
    public static void parseDate(DateMatch d, JsonObject a) {
        d.setText(a.getString("matchtext"));
        d.setType(a.getString(FLD_TYPE));
        d.datenorm_text = a.getString("date-normalized");
        d.pattern_id = a.getString("pattern-id");
    }

    /**
     * Parse out a taxon from JSON/REST
     *
     * @param x a taxon object
     * @param t type of taxon
     * @param a JSON annotation
     */
    public static void parseTaxon(TaxonMatch x, String t, JsonObject a) {
        x.setText(a.getString("matchtext"));
        if (a.containsKey(VAL_TAXON)) {
            Taxon tx = new Taxon();
            tx.setName(a.getString("taxon"));
            tx.catalog = a.getString("catalog");
            x.addTaxon(tx);
        }
        x.setType(t);
    }

    /**
     * Given an existing JSON object, add geocoding metadata to it.
     *
     * @param geo  geocoding object
     * @param node JsonObject representing the serialized JSON for an Xlayer or
     *             other annotation.
     */
    public static final void createGeocoding(Geocoding geo, JsonObject node) {
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
        if (GeodeticUtility.isValidNonZeroCoordinate(geo.getLatitude(), geo.getLongitude())) {
            node.put("prec", geo.getPrecision());
            node.put("lat", geo.getLatitude());
            node.put("lon", geo.getLongitude());
            node.put("geohash", GeodeticUtility.geohash(geo));
        }
        if (geo.getMethod() != null) {
            node.put("method", geo.getMethod());
        }
        if (geo.getAdminName() != null) {
            node.put("adm1_name", geo.getAdminName());
        }
        if (geo instanceof GeocoordMatch) {
            GeocoordMatch coord = (GeocoordMatch) geo;
            if (coord.getRelatedPlace() != null) {
                node.put("related_place_name", coord.getRelatedPlace().getName());
            }
            /*
             * "name": "Provincial Park of Alberta", "cc": "CA", "lat": 56.16, "lon":
             * -117.54, "prec": 5000, "feat_class": "S", "feat_code": "PARK", "adm1": "01",
             * "distance": 3400
             */
            if (coord.getNearByPlaces() != null) {
                JsonArray arr = new JsonArray();
                for (Place pl : coord.getNearByPlaces()) {
                    JsonObject o = new JsonObject();
                    o.put("name", pl.getName());
                    o.put("cc", pl.getCountryCode());
                    o.put("adm1", pl.getAdmin1());
                    o.put("lat", pl.getLatitude());
                    o.put("lon", pl.getLongitude());
                    o.put("feat_class", pl.getFeatureClass());
                    o.put("feat_code", pl.getFeatureCode());
                    o.put("prec", pl.getPrecision());
                    o.put("distance", GeodeticUtility.distanceMeters(coord, pl));

                    arr.add(o);
                }
                node.put("nearest_places", arr);
            }
        }
    }

    /**
     * Given a JSON object, parse fields relevant to the geocoding and populate that
     * JSON data TODO:
     * implement Parsing reverse geocoding structures related_place_name and
     * nearest_places
     *
     * @param geo  geocoding object
     * @param node JsonObject representing the serialized JSON for an Xlayer or
     *             other annotation.
     */
    public static final void parseGeocoding(Place geo, JsonObject node) {
        if (node.containsKey("cc")) {
            geo.setCountryCode(node.getString("cc"));
        }
        if (node.containsKey("adm1")) {
            geo.setAdmin1(node.getString("adm1"));
        }
        if (node.containsKey("feat_class")) {
            geo.setFeatureClass(node.getString("feat_class"));
        }
        if (node.containsKey("feat_code")) {
            geo.setFeatureCode(node.getString("feat_code"));
        }
        if (node.containsKey("lat")) {
            geo.setLatitude(node.getDouble("lat"));
            geo.setLongitude(node.getDouble("lon"));
            if (node.containsKey("prec")) {
                geo.setPrecision(node.getInteger("prec"));
            }
        }
        if (node.containsKey("method")) {
            geo.setMethod(node.getString("method"));
        }
        if (node.containsKey("adm1_name")) {
            geo.setAdminName(node.getString("adm1_name"));
        }

        /*
         * Name overrides matchtext or text.
         */
        if (node.containsKey("text")) {
            geo.setPlaceName(node.getString("text"));
        }
        if (node.containsKey("matchtext")) {
            geo.setPlaceName(node.getString("matchtext"));
        }
        if (node.containsKey("name")) {
            geo.setPlaceName(node.getString("name"));
        }
    }

    /**
     * Copy the basic match information
     *
     * @param m
     * @return
     */
    private static JsonObject populateMatch(final TextMatch m) {

        JsonObject o = new JsonObject();
        int len = m.end - m.start;
        o.put("offset", m.start);
        o.put("length", len);
        // String matchText = TextUtils.squeeze_whitespace(name.getText());
        o.put("matchtext", m.getText());
        o.put(FLD_MATCH_ID, m.getMatchId());
        o.put(FLD_TYPE, m.getType());
        o.put(FLD_FILTERED, m.isFilteredOut());
        return o;
    }

    /**
     * Return seconds of epoch.
     *
     * @param d
     * @return
     */
    private static int asSeconds(final Date d) {
        return (int) (d.getTime() / 1000);
    }

    /**
     * Cutoff confidence for geocoding results:
     */
    public static int DEFAULT_LOWER_CONFIDENCE = 10;

    public static JsonObject toJSON(final List<TextMatch> matches, final Parameters jobParams) {
        Logger log = LoggerFactory.getLogger(Transforms.class);

        int tagCount = 0;

        JsonObject resultContent = new JsonObject();
        JsonObject resultMeta = new JsonObject();
        resultMeta.put("status", "ok");
        resultMeta.put("numfound", 0);
        JsonArray resultArray = new JsonArray();

        /*
         * Super loop: Iterate through all found entities. record Taxons as person or
         * orgs record Geo tags as country, place, or geo. geo = geocoded place or
         * parsed coordinate (MGRS, DMS, etc)
         */
        for (TextMatch name : matches) {

            if (!jobParams.output_filtered && name.isFilteredOut()) {
                log.debug("Filtered out {}", name.getText());
                continue;
            }

            /*
             * ==========================
             * ANNOTATIONS: non-geographic entities that are
             * filtered out, but worth tracking
             * ==========================
             */
            if (name instanceof TaxonMatch &&
                    (jobParams.tag_taxons || jobParams.tag_all_taxons)) {
                TaxonMatch match = (TaxonMatch) name;
                match.defaultMatchId();
                ++tagCount;
                if (!match.getTaxons().isEmpty()) {
                    // Only get one taxon from this match. That is sufficient, but not perfect.
                    Taxon n = match.getTaxons().get(0);
                    JsonObject node = populateMatch(name);
                    node.put("taxon", n.name); // Name of taxon
                    node.put("catalog", n.catalog); // Name of catalog or source
                    node.put("method", "TaxonMatcher");
                    resultArray.add(node);
                }
                continue;
            }

            /*
             * ========================== FILTERING ==========================
             */
            JsonObject node = populateMatch(name);

            if (name instanceof DateMatch && jobParams.tag_patterns) {
                ++tagCount;
                DateMatch dt = (DateMatch) name;
                node.put(FLD_TYPE, "date");
                node.put("date-normalized", dt.datenorm_text);
                if (dt.datenorm != null) {
                    // Java/Unix Date Epoch in Seconds.
                    node.put("timestamp", asSeconds(dt.datenorm));
                }
                node.put("pattern-id", dt.pattern_id);
                resultArray.add(node);
                continue;
            }

            /*
             * ==========================
             * ANNOTATIONS: coordinates
             * ==========================
             */
            if (name instanceof GeocoordMatch && jobParams.tag_coordinates) {
                ++tagCount;
                GeocoordMatch geo = (GeocoordMatch) name;
                Transforms.createGeocoding(geo, node);
                resultArray.add(node);
                continue;
            }

            PlaceCandidate place = (PlaceCandidate) name;
            Place resolvedPlace = place.getChosenPlace();
            if (resolvedPlace == null){
                // TODO: need more examples of how this might happen.
                continue;
            }

            /*
             * ==========================
             * ANNOTATIONS: countries, places, etc.
             * ==========================
             */
            /*
             * Accept all country names as potential geotags Else if name can be filtered
             * out, do it now. Otherwise it is a valid place name to consider
             */
            ++tagCount;
            if (place.isCountry && jobParams.tag_countries) {
                node.put("name", resolvedPlace.getPlaceName());
                node.put(FLD_TYPE, VAL_COUNTRY);
                node.put("cc", resolvedPlace.getCountryCode());
                node.put("confidence", place.getConfidence());
                node.put("method", resolvedPlace.getMethod());
            } else if (resolvedPlace != null &&
                    (jobParams.tag_places|| jobParams.tag_postal)) {

                // IF Caller is not asking for "codes" output....
                if (!jobParams.tag_codes) {
                    boolean qualified = place.isDerived() || place.isValid();
                    // Filter out non-Postal codes if user is not requesting "codes" to be listed.
                    if (resolvedPlace.isCode() && !qualified && !GeonamesUtility.isPostal(resolvedPlace)) {
                        /* Given a bare token ' MA ' not attached to another term,
                         * this would be considered just a code.  Caller must add "codes" to request to get these.
                         */
                        continue;
                    }
                }
                /*
                 * Conf = 20 or greater to be geocoded.
                 */
                Transforms.createGeocoding(resolvedPlace, node);
                node.put("name", resolvedPlace.getPlaceName());
                addProvinceName(node, resolvedPlace);
                // "Related" or linked geography is for Postal or other use cases.
                addRelatedGeography(node, place);
                node.put("confidence", place.getConfidence());
                node.put("rules", StringUtils.join(place.getRules(), ";"));
                if (place.getConfidence() <= DEFAULT_LOWER_CONFIDENCE) {
                    place.setFilteredOut(true);
                    node.put(FLD_FILTERED, place.isFilteredOut());
                }
            } else {
                node.put("name", name.getText());
                node.put("confidence", 15); /* A low confidence */
                node.put("rules", StringUtils.join(place.getRules(), ";"));
            }
            resultArray.add(node);
        }
        resultMeta.put("numfound", tagCount);
        resultContent.put("response", resultMeta);
        resultContent.put("annotations", resultArray);

        return resultContent;
    }

    /**
     * @param map
     * @param resolvedPlace
     */
    private static void addProvinceName(JsonObject map, Place resolvedPlace) {
        String adm1Name = resolvedPlace.getAdmin1Name();
        if (adm1Name == null) {
            return;
        }
        map.put("province-name", adm1Name);
    }

    /**
     * @param map
     * @param mention
     */
    private static void addRelatedGeography(JsonObject map, PlaceCandidate mention) {
        if (!mention.isDerived() || mention.getLinkedGeography() == null) {
            return;
        }
        JsonObject relatedInfo = new JsonObject();
        Map<String, Place> slots = mention.getLinkedGeography();
        for (String slot : slots.keySet()) {
            Place linkedPlace = slots.get(slot);
            JsonObject slotValue = new JsonObject();
            slotValue.put("matchtext", linkedPlace.getPlaceName());
            slotValue.put("match-id", linkedPlace.getInstanceId());
            relatedInfo.put(slot, slotValue);
        }
        map.put("related", relatedInfo);
    }
}
