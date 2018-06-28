package org.opensextant.xlayer.server.xgeo;

import java.util.List;

import org.json.JSONException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.output.Transforms;
import org.opensextant.processing.Parameters;
import org.opensextant.xlayer.server.TaggerResource;
import org.restlet.data.CharacterSet;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;

import jodd.json.JsonObject;

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
        PlaceGeocoder xgeo = (PlaceGeocoder) this.getApplication().getContext().getAttributes().get("xgeo");
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
    public Representation process(TextInput input, Parameters jobParams) {

        if (input == null || input.buffer == null) {
            return status("FAIL", "No text");
        }
        debug("Processing plain text doc");

        ++requestCount;
        try {
            if (prodMode) {
                PlaceGeocoder xgeo = (PlaceGeocoder) getExtractor();
                xgeo.setAllowLowerCase(jobParams.tag_lowercase);

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

    private Representation format(List<TextMatch> matches, Parameters jobParams) throws JSONException {

        JsonObject j = Transforms.toJSON(matches, jobParams);
        Representation result = null;
        result = new JsonRepresentation(j.toString());
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
