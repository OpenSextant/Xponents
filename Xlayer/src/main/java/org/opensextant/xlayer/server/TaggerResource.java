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
import org.opensextant.util.TextUtils;
import org.restlet.data.Form;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public abstract class TaggerResource extends ServerResource {

    /** The log. */
    protected Logger log = null;
    /** The request count. */
    public static long requestCount = 0;
    /** The test mode. */
    protected static final boolean testMode = false;
    /** The prod mode. */
    protected static final boolean prodMode = !testMode;

    public TaggerResource() {
        super();
    }

    /**
     * Implement your own STOP cammand
     * 
     */
    public abstract void stop();

    /**
     * Ping.
     *
     * @return status with current request count
     */
    public Representation ping() {
        return new JsonRepresentation("{status='OK', requests='" + requestCount + "'}");
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
    public abstract Representation process(TextInput input, RequestParameters jobParams);

    /**
     * Contract:
     * docid optional; 'text' | 'doc-list' required.
     * command: cmd=ping sends back a simple response
     * 
     * text = UTF-8 encoded text
     * docid = user's provided document ID
     * doc-list = An array of text
     * 
     * cmd=ping = report status.
     * 
     * Where json-array contains { docs=[ {docid='A', text='...'}, {docid='B', text='...',...] }
     * The entire array must be parsable in memory as a single, traversible JSON object.
     * We make no assumption about one-JSON object per line or anything about line-endings as separators.
     * 
     *
     * @param params
     *            the params
     * @return the representation
     * @throws JSONException
     *             the JSON exception
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

            RequestParameters job = fromRequest(json);
            return process(item, job);
        }

        // org.json.JSONArray array = json.optJSONArray("doc-list");
        // if (array != null) {
        // return processList(array);
        // }
        return status("FAIL", "Invalid API use text+docid pair or doc-list was not found");
    }

    /**
     * HTTP GET -- vanilla. Do not use in production, unless you have really small data packages.
     * This is useful for testing. Partial contract:
     * 
     * miscellany: 'cmd' = 'ping' |... other commands.
     * processing: 'docid' = ?, 'text' = ?
     * 
     * @param params
     *            the params
     * @return the representation
     */
    @Get("application/json;charset=utf-8")
    public Representation processGet(Representation params) {
        Form inputs = getRequest().getResourceRef().getQueryAsForm();
        String cmd = inputs.getFirstValue("cmd");
        if ("ping".equalsIgnoreCase(cmd)) {
            return ping();
        } else if ("stop".equalsIgnoreCase(cmd)) {
            info("Stopping Xponents Xlayer Service Requested by CLIENT="
                    + getRequest().getClientInfo().getAddress());
            stop();
        }

        String input = inputs.getFirstValue("text");
        String docid = inputs.getFirstValue("docid");
        String lang = inputs.getFirstValue("lang");
        TextInput item = new TextInput(docid, input);
        item.langid = lang;

        RequestParameters job = fromRequest(inputs);
        return process(item, job);
    }

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
    private RequestParameters fromRequest(Form inputs) {
        RequestParameters job = new RequestParameters();
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
            job.format = fmt;
        }

        return job;
    }

    /**
     * Convenience helper to reset data.
     * @param job
     */
    protected void resetParameters(RequestParameters job) {
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
        job.format = "json";
    }

    /**
     * 
     * @param inputs
     * @return
     * @throws JSONException
     */
    private RequestParameters fromRequest(JSONObject inputs) throws JSONException {
        RequestParameters job = new RequestParameters();
        job.output_coordinates = false;
        job.output_countries = true;
        job.output_places = true;
        job.tag_coordinates = false;
        job.tag_countries = true;
        job.tag_places = true;

        job.tag_taxons = true;
        job.tag_patterns = true;
        job.output_filtered = false;
        job.format = "json";

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