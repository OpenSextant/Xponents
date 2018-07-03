package org.opensextant.xlayer.server;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.processing.Parameters;
import org.opensextant.util.TextUtils;
import org.restlet.data.Form;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ServerResource;

public abstract class TaggerResource extends ServerResource {

    /** The log. */
    protected Logger log = null;
    /** The test mode. */
    protected static final boolean testMode = false;
    /** The prod mode. */
    protected static final boolean prodMode = !testMode;

    public TaggerResource() {
        super();
    }

    protected String operation = null;

    /**
     * operational parameter.
     */
    public void doInit() {
        operation = this.getAttribute("operation");
    }

    /**
     * Ping. trivial thing for now.
     *
     * @return status
     */
    public Representation ping() {
        JSONObject ping = new JSONObject();
        ping.put("status", "OK");
        return new JsonRepresentation(ping);
    }

    /**
     */
    protected abstract Extractor getExtractor();

    /**
     * Implement the processing of a single Input given some request parameters
     * Based on the processing and the request, format response accordingly.
     * 
     * @param input  signal
     * @param jobParams controls
     * @return JSON or other formatted response.  
     */
    public abstract Representation process(TextInput input, Parameters jobParams);

    /**
     * // Get parameters for processing? None currently, but may be:
     * // - lower case tagging or filtering
     * // - coordinate parsing on|off
     * //
     * // Get parameters for formatting. JSON, HTML, mainly.
     * // Output represents filters + format.
     * //
     * 
     * @param inputs
     * @return
     */
    protected Parameters fromRequest(Form inputs) {
        Parameters job = new Parameters();
        String list = inputs.getValues("features");
        Set<String> features = new HashSet<>();
        job.tag_coordinates = true;
        job.tag_countries = true;
        job.tag_places = true;

        if (isNotBlank(list)) {
            features.addAll(TextUtils.string2list(list.toLowerCase(), ","));

            job.output_coordinates = features.contains("coordinates");
            job.output_countries = features.contains("countries");
            job.output_places = features.contains("places");
        }

        String fmt = inputs.getFirstValue("format");
        if (fmt != null) {
            job.addOutputFormat(fmt);
        }

        return job;
    }

    /**
     * Convenience helper to reset data.
     * @param job
     */
    protected void resetParameters(Parameters job) {
        job.output_coordinates = false;
        job.output_countries = false;
        job.output_places = false;
        job.output_geohash = false;
        job.output_filtered = false;

        job.tag_lowercase = false;
        job.tag_coordinates = false;
        job.tag_countries = false;
        job.tag_places = false;
        job.tag_taxons = false;
        job.tag_patterns = false;
        job.addOutputFormat("json");
    }

    /**
     * 
     * @param inputs
     * @return
     * @throws JSONException
     */
    protected Parameters fromRequest(JSONObject inputs) throws JSONException {
        Parameters job = new Parameters();
        job.output_coordinates = false;
        job.output_countries = true;
        job.output_places = true;
        job.tag_coordinates = false;
        job.tag_countries = true;
        job.tag_places = true;

        job.tag_taxons = true;
        job.tag_patterns = true;
        job.output_filtered = false;
        job.addOutputFormat("json");

        if (inputs.has("features")) {
            resetParameters(job);

            String list = inputs.getString("features");
            Set<String> features = new HashSet<>();

            // JSONArray list = inputs.getJSONArray("features");
            features.addAll(TextUtils.string2list(list.toLowerCase(), ","));

            job.tag_coordinates = job.output_coordinates = features.contains("coordinates");
            job.tag_countries = job.output_countries = features.contains("countries");
            job.tag_places = job.output_places = features.contains("places");

            if (features.contains("geo")) {
                job.tag_coordinates = job.output_coordinates = true;
                job.tag_countries = job.output_countries = true;
                job.tag_places = job.output_places = true;
            }

            // Request tagging on demand.
            job.tag_taxons = job.output_taxons = (features.contains("taxons") || features.contains("orgs")
                    || features.contains("persons"));
            job.tag_patterns = job.output_patterns = features.contains("patterns");

            job.output_filtered = features.contains("filtered_out");
        }

        if (inputs.has("options")) {
            String list = inputs.getString("options");
            Set<String> features = new HashSet<>();
            features.addAll(TextUtils.string2list(list.toLowerCase(), ","));
            job.clean_input = features.contains("clean_input");
            job.tag_lowercase = features.contains("lowercase");
        }

        if (job.clean_input || job.tag_lowercase) {
            job.isdefault = false;
        }

        return job;
    }

    /**
     * Status.
     *
     * @param status
     *            the status
     * @param error
     *            the error
     * @return the json representation
     */
    protected JsonRepresentation status(String status, String error) {
        JSONObject s = new JSONObject();
        try {
            if (error != null) {
                s.put("status", status);
                s.put("error", error);
            } else {
                s.put("status", status);
            }
        } catch (JSONException jsonErr) {
            error("Trivial JSON issue!!!!", jsonErr);
        }
        return new JsonRepresentation(s.toString());
    }

    public void error(String msg, Exception err) {
        log.severe(msg + " ERR: " + err.getMessage());
        if (isDebug()) {
            log.fine("" + err.getStackTrace());
        }
    }

    public void info(String msg) {
        log.info(msg);
    }

    public void debug(String msg) {
        if (isDebug()) {
            log.fine(msg);
        }
    }

    public boolean isDebug() {
        return (log.getLevel() == Level.FINE || log.getLevel() == Level.FINEST || log.getLevel() == Level.FINER);
    }
}