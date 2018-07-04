package org.opensextant.output;

import java.util.List;

import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.processing.Parameters;
import org.opensextant.util.GeodeticUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jodd.json.JsonArray;
import jodd.json.JsonObject;

public class Transforms {

    /**
     * Convert JSON object for an annotation into a Xponents TextMatch instance.
     * Parsing data from JSON/REST representations has very limited capability compared to
     * using Java API for processing routines directly.
     * 
     * @param data
     * @return TextMatch object represented by json annotation
     */
    public static TextMatch parseAnnotation(Object data) {
        if (!(data instanceof JsonObject)) {
            return null;
        }
        TextMatch m = null;
        JsonObject a = (JsonObject) data;

        TaxonMatch x = null;
        String typ = a.getString("type");
        String text = a.getString("matchtext");
        switch (typ) {

        case "place":
            PlaceCandidate placeMatch = new PlaceCandidate();
            Place geo = new Place();
            placeMatch.setText(text);
            Transforms.parseGeocoding(geo, a);
            placeMatch.setConfidence(a.getInteger("confidence", -1));
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
            countryMatch.setConfidence(a.getInteger("confidence", -1));
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
            throw new jodd.json.JsonException("Unknown Annotation " + typ);
        }

        m.setType(typ);
        m.start = a.getInteger("offset");
        m.end = m.start + a.getInteger("length");
        m.setFilteredOut( a.getBoolean("filtered-out"));

        return m;
    }

    /**
     * Parse out a taxon from JSON/REST
     * @param x a taxon object
     * @param t type of taxon
     * @param a JSON annotation
     */
    public static void parseTaxon(TaxonMatch x, String t, JsonObject a) {
        x.setText(a.getString("matchtext"));
        if (a.containsKey("taxon")) {
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
     * @param geo
     * @param node
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
            geo.setAdminName("adm1_name");
        }

        /* Name overrides matchtext or text.
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
        return o;
    }

    public static JsonObject toJSON(final List<TextMatch> matches, final Parameters jobParams) {
        Logger log = LoggerFactory.getLogger(Transforms.class);

        int tagCount = 0;

        JsonObject resultContent = new JsonObject();
        JsonObject resultMeta = new JsonObject();
        resultMeta.put("status", "ok");
        resultMeta.put("numfound", 0);
        JsonArray resultArray = new JsonArray();

        /*
         * Super loop: Iterate through all found entities. record Taxons as
         * person or orgs record Geo tags as country, place, or geo. geo =
         * geocoded place or parsed coordinate (MGRS, DMS, etc)
         * 
         */
        for (TextMatch name : matches) {

            if (!jobParams.output_filtered && name.isFilteredOut()) {
                log.debug("Filtered out {}", name.getText());
                continue;
            }

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
                        JsonObject node = populateMatch(name);
                        String t = "taxon";
                        String taxon_name = n.name.toLowerCase();
                        if (taxon_name.startsWith("org")) {
                            t = "org";
                        } else if (taxon_name.startsWith("person")) {
                            t = "person";
                        }
                        node.put("type", t);
                        node.put("taxon", n.name); // Name of taxon
                        node.put("catalog", n.catalog); // Name of catalog or source
                        node.put("filtered-out", name.isFilteredOut());

                        resultArray.add(node);
                        break;
                    }
                }
                continue;
            }

            /*
             * ==========================
             * FILTERING
             * ==========================
             */

            JsonObject node = populateMatch(name);

            /*
             * ==========================
             * ANNOTATIONS: coordinates
             * ==========================
             */
            if (name instanceof GeocoordMatch) {
                ++tagCount;
                GeocoordMatch geo = (GeocoordMatch) name;
                node.put("type", "coordinate");
                node.put("filtered-out", name.isFilteredOut());
                Transforms.createGeocoding(geo, new JsonObject());
                resultArray.add(node);
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
                node.put("name", resolvedPlace.getPlaceName());
                node.put("type", "country");
                node.put("cc", resolvedPlace.getCountryCode());
                node.put("confidence", place.getConfidence());

            } else {

                /*
                 * Conf = 20 or greater to be geocoded.
                 */
                Transforms.createGeocoding(resolvedPlace, node);
                node.put("name", resolvedPlace.getPlaceName());
                addProvinceName(node, resolvedPlace);
                node.put("type", "place");
                node.put("confidence", place.getConfidence());
                if (place.getConfidence() <= 10) {
                    place.setFilteredOut(true);
                }
            }
            node.put("filtered-out", place.isFilteredOut());
            resultArray.add(node);
        }
        resultMeta.put("numfound", tagCount);
        resultContent.put("response", resultMeta);
        resultContent.put("annotations", resultArray);

        return resultContent;
    }

    /**
     * 
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
}
