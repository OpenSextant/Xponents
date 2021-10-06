package org.opensextant.xlayer.server.xgeo;

import jodd.json.JsonObject;
import org.json.JSONException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.PostalGeocoder;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.opensextant.output.Transforms;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.RuntimeTools;
import org.opensextant.xlayer.server.TaggerResource;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * A RESTFul application of PlaceGeocoder
 */
public class XponentsGeotagger extends TaggerResource {

    /**
     * Restlet resource that pulls its configuration from Context.
     */
    public XponentsGeotagger() {
        super();
        getContext();
        log = Context.getCurrentLogger();
    }

    private static final String[] extractors = {"xgeo", "xtemp", "xpostal"};
    private static final HashSet extractorSet = new HashSet<>();

    static {
        extractorSet.addAll(Arrays.asList(extractors));
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

    /**
     * Process the text for the given document.
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
            if (prodMode) {
                PlaceGeocoder xgeo = (PlaceGeocoder) getExtractor("xgeo");
                List<TextMatch> matches = xgeo.extract(input, jobParams);

                if (jobParams.tag_patterns) {
                    XTemporal xt = (XTemporal) getExtractor("xtemp");
                    matches.addAll(xt.extract(input));
                }
                if (jobParams.tag_postal) {
                    // TODO: PostalGeocoder code may operate with older index
                    //       but extractor will be null here if that *postal* index is not present.
                    PostalGeocoder pg = (PostalGeocoder) getExtractor("xpostal");
                    if (pg != null) {
                        List<TextMatch> postalMatches = pg.extract(input);
                        // Associate raw geotags with Postal matches, so any Postal codes are fully geolocated.
                        // Filter out any postal code that does not line up with other geography in input text.
                        if (!postalMatches.isEmpty()) {
                            // 1. link postal geotags with other non-postal geotags.
                            PostalGeocoder.associateMatches(matches, postalMatches);
                            // 2. regenerate new spans and geocodes;  This may filter in/out geotags.
                            List<TextMatch> derivedMatches = PostalGeocoder.deriveMatches(postalMatches, input);
                            // 3. add the superset of postal+derived tags.
                            matches.addAll(derivedMatches);
                        }
                    }
                }
                if (isDebug()) {
                    debug(String.format("CURRENT MEM USAGE(K)=%d", RuntimeTools.reportMemory()));
                }
                /*
                 * formulate matches as JSON output.
                 */
                filter(matches, jobParams);
                return format(matches, jobParams);
            }

        } catch (Exception processingErr) {
            error("Failure on doc " + input.id, processingErr);
            return status("FAIL", processingErr.getMessage());
        }

        return status("TEST", "nothing done in test with doc=" + input.id);
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

    /**
     * @param params         parameters
     * @param variousMatches matches to filter
     */
    public void filter(List<TextMatch> variousMatches, Parameters params) {
        /* TODO: filter other matches more seriously.
         *  Marking Taxons as filteredOut is a hack.
         */
        if (!params.tag_taxons) {
            for (TextMatch m : variousMatches) {
                if (m instanceof TaxonMatch) {
                    m.setFilteredOut(true);
                }
            }
        }
    }
}
