package org.opensextant.xlayer.server.xgeo;

import java.util.ArrayList;
import java.util.List;

import jodd.json.JsonObject;
import org.json.JSONException;
import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.PostalGeocoder;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.opensextant.output.Transforms;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.RuntimeTools;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.xlayer.server.TaggerResource;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

/**
 * A RESTFul application of PlaceGeocoder
 */
public class XponentsGeotagger extends TaggerResource {

    static {
        extractorSet.add(GEO_TAGGER);
        extractorSet.add(POSTAL_TAGGER);
        extractorSet.add(DATE_TAGGER);
        extractorSet.add(TAXON_TAGGER);
    }

    /**
     * Restlet resource that pulls its configuration from Context.
     */
    public XponentsGeotagger() {
        super();
        getContext();
        log = Context.getCurrentLogger();
    }

    /**
     * get Xponents Exxtractor object from global attributes.
     */
    @Override
    public Extractor getExtractor(String xid) {
        Object X = this.getApplication().getContext().getAttributes().get(xid);
        if (X == null) {
            /* key not present */
            error(String.format("No such extractor %s; It is either mis-configured or not available.", xid), null);
            return null;
        }
        if (extractorSet.contains(xid)) {
            return (Extractor) X;
        }
        /* key present, but invalid */
        error("No such extractor " + xid, null);

        return null;
    }

    /**
     * Contract: docid optional; 'text' | 'doc-list' required.
     * command: cmd=ping sends back a simple response
     * text = UTF-8 encoded text docid = user's provided document ID
     * doc-list = An array of text
     * cmd=ping = report status.
     * Where json-array contains { docs=[ {docid='A', text='...'}, {docid='B', text='...',...] }
     * The entire array must be parsable in memory as a single, traversible JSON object.
     * We make no assumption about one-JSON object per line or anything about line-endings as separators.
     *
     * @param params JSON parameters per REST API: docid, text, lang, features,
     *               options, and preferred_*
     * @return the representation
     * @throws JSONException the JSON exception
     */
    @Post("application/json;charset=utf-8")
    public Representation processForm(JsonRepresentation params) throws JSONException {
        org.json.JSONObject json = params.getJsonObject();
        String input = json.optString("text", null);
        String docid = json.optString("docid", null);

        if (input != null) {
            String lang = json.optString("lang", null);
            TextInput item = new TextInput(docid, input);
            item.langid = lang;

            Parameters job = fromRequest(json);
            return process(item, job);
        }

        return status("FAIL", "Invalid API use text+docid pair or doc-list was not found");
    }

    /**
     * HTTP GET -- vanilla. Do not use in production, unless you have really small
     * data packages. This
     * is useful for testing. Partial contract:
     * miscellany: 'cmd' = 'ping' |... other commands. processing: 'docid' = ?,
     * 'text' = ?
     *
     * @param params JSON parameters. see process()
     * @return the representation
     */
    @Get
    public Representation processGet(Representation params) {
        Form inputs = getRequest().getResourceRef().getQueryAsForm();
        String input = inputs.getFirstValue("text");
        String docid = inputs.getFirstValue("docid");
        String lang = inputs.getFirstValue("lang");
        TextInput item = new TextInput(docid, input);
        item.langid = lang;

        Parameters job = fromRequest(inputs);
        return process(item, job);
    }

    private boolean tag_geo(Parameters p) {
        return p.tag_coordinates || p.tag_countries || p.tag_places;
    }

    /** Extracting Taxons ~ keyphrases, etc. ~ is a secondary function.
     * So either caller explicity asks for all taxons OR
     * they ask for "taxons" and no geo features.
     */
    private boolean tag_taxons(Parameters p) {
        return p.tag_all_taxons || p.tag_taxons;
    }

    /**
     * Process the text for the given document. NOTE: Please note this is NOT MT-safe. Internally there are single stateful instances of
     *  Extractor taggers, which may have significant memory and initialization phases.  As a prototype
     *  that is one limitation.  If you need multiple clients to hit this service, you ideally load-balance
     *  a bank of Xponents REST server.
     *
     * @param input     the input
     * @param jobParams the job params
     * @return the representation
     */
    @Override
    public Representation process(TextInput input, Parameters jobParams) {

        if (input == null || input.buffer == null) {
            return status("FAIL", "No text");
        }
        debug("Processing plain text doc");

        try {
            List<TextMatch> matches = new ArrayList<>();

            // BOTH geo and taxons could be requested:  features = "geo", "all-taxons"
            if (!tag_geo(jobParams) && tag_taxons(jobParams)) {
                // Taxonomic tags only
                TaxonMatcher phraseTagger = (TaxonMatcher) getExtractor(TAXON_TAGGER);
                matches.addAll(phraseTagger.extract(input, jobParams));
            } else if (tag_geo(jobParams) || tag_taxons(jobParams)) {
                // Geotagging
                PlaceGeocoder xgeo = (PlaceGeocoder) getExtractor(GEO_TAGGER);
                matches.addAll(xgeo.extract(input, jobParams));
            }

            if (jobParams.tag_patterns) {
                XTemporal xt = (XTemporal) getExtractor(DATE_TAGGER);
                matches.addAll(xt.extract(input));
            }
            if (jobParams.tag_postal) {
                PostalGeocoder pg = (PostalGeocoder) getExtractor(POSTAL_TAGGER);
                if (tag_geo(jobParams)) {
                    // OPTIMIZATION: reuse matches accumulated so far to prevent
                    // PostalGeocoder from repeating extract()
                    pg.setGeneralMatches(matches);
                }
                matches.addAll(pg.extract(input));
            }
            if (isDebug()) {
                debug(String.format("CURRENT MEM USAGE(K)=%d", RuntimeTools.reportMemory()));
            }

            filter(matches, jobParams, input);
            /*
             * transform matches as JSON output.
             */
            return format(matches, jobParams);

        } catch (Exception processingErr) {
            error("Failure on doc " + input.id, processingErr);
            return status("FAIL", processingErr.getMessage());
        }
    }

    /**
     * Complex filters -- primarily due to the addition of POSTAL feature tagging; That introduce significant
     * noise which needs lots of special post-processing logic.
     *
     * @param matches
     * @param jobParams
     */
    private void filter(List<TextMatch> matches, Parameters jobParams, TextInput signal) {

        // Language filter for text spans.
        boolean isGeneralLang = true;
        if (signal.getCharacterization() != null) {
            isGeneralLang = !(signal.getCharacterization().hasCJK || signal.getCharacterization().hasMiddleEastern);
        }

        for (TextMatch m : matches) {
            // Big loop for conditionals...
            //
            if (m instanceof PlaceCandidate) {
                PlaceCandidate place = (PlaceCandidate) m;
                Place resolvedPlace = place.getChosenPlace();
                boolean validCountry = place.isCountry && place.getLength() > 2;
                boolean no_qualifying_geolocation = !place.hasResolvedRelated();
                if (!jobParams.tag_codes) {
                    // IF Caller is not asking for "codes" output.... then omit any postal codes or state/ADM1 codes
                    // that are not fully resolved.
                    if (resolvedPlace != null && resolvedPlace.isCode()) {
                        // This condition differentiates matches -- looking to evaluate only inferred places that are codes.
                        //    Cases:  CODE          -- Bare CODE. although resolved, its likely noise.
                        //    Cases:  CODE1 CODE2   -- CODE1 is resolved, but related CODE2 is not. Its noise. "AB CD", "MA VA"
                        boolean qualified = place.isDerived() || place.isValid();
                        // Filter out non-Postal codes if user is not requesting "codes" to be listed.
                        if (!qualified && !GeonamesUtility.isPostal(resolvedPlace)) {
                            place.setFilteredOut(true);
                        } else if (place.isShortName() && no_qualifying_geolocation) {
                            place.setFilteredOut(true);
                        }
                    }
                }
                if (no_qualifying_geolocation) {
                    // When place has no related geography and is a form of code, abbrev or other noise omit. Cases:
                    // 1. if Place represents POSTAL area
                    // 2. is a trivial length match (but not a country). This applies to non-CjK, non-Arabic scripts
                    if (place.hasPostal()) {
                        place.setFilteredOut(true);
                    } else if (m.getLength() < jobParams.minimum_tag_len && !validCountry && isGeneralLang) {
                        place.setFilteredOut(true);
                    }
                }
            }
        }
    }

    /**
     * Format matches as JSON
     *
     * @param matches   items to format
     * @param jobParams parameters
     * @return formatted json
     * @throws JSONException on format error
     */
    private Representation format(List<TextMatch> matches, Parameters jobParams) throws JSONException {

        JsonObject j = Transforms.toJSON(matches, jobParams);
        Representation result = new JsonRepresentation(j.toString());
        result.setCharacterSet(CharacterSet.UTF_8);

        return result;
    }
}
