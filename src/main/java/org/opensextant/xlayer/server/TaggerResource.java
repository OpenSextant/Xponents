package org.opensextant.xlayer.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
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
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public abstract class TaggerResource extends ServerResource {

    public static final String GEO_TAGGER = "xgeo";
    public static final String POSTAL_TAGGER = "xpostal";
    public static final String DATE_TAGGER = "xtemp";
    public static final String TAXON_TAGGER = "xtax"; // Unused.

    protected static final HashSet<String> extractorSet = new HashSet<>();

    /**
     * The log.
     */
    protected Logger log = null;

    public TaggerResource() {
        super();
    }

    protected String operation = null;

    public static final String FLD_FEATURES = "features";
    public static final String FLD_STATUS = "status";

    /**
     * operational parameter.
     */
    @Override
    public void doInit() {
        operation = this.getAttribute("operation");
    }

    public String getProperty(String k) {
        return (String) this.getApplication().getContext().getAttributes().get(k);
    }

    /**
     * Ping. trivial thing for now.
     *
     * @return status
     */
    public Representation ping() {
        JSONObject ping = new JSONObject();
        ping.put(FLD_STATUS, "OK");
        ping.put("version", getProperty("version"));
        return new JsonRepresentation(ping);
    }

    public abstract Extractor getExtractor(String xid);

    /**
     * Implement the processing of a single Input given some request parameters
     * Based on the processing
     * and the request, format response accordingly.
     *
     * @param input     signal
     * @param jobParams controls
     * @return JSON or other formatted response.
     */
    public abstract Representation process(TextInput input, Parameters jobParams);

    /**
     * Get parameters for processing? None currently, but may be:
     * - lower case tagging or filtering
     * - coordinate parsing on|off
     * Get parameters for formatting. JSON, HTML, mainly.
     * Output represents filters + format.
     *
     * @param inputs arguments to RESTful request
     * @return Xponents Parameters
     */
    protected Parameters fromRequest(Form inputs) {
        Parameters job = new Parameters();
        String list = inputs.getValues(FLD_FEATURES);
        job.tag_coordinates = true;
        job.tag_countries = true;
        job.tag_places = true;

        if (isNotBlank(list)) {
            HashSet<String> features = new HashSet<>(TextUtils.string2list(list.toLowerCase(), ","));
            parseParameters(job, features);
        }

        String fmt = inputs.getFirstValue("format");
        if (fmt != null) {
            job.addOutputFormat(fmt);
        }
        String len = inputs.getFirstValue("minlen");
        if (len != null){
            job.minimum_tag_len = Integer.parseInt(len);
        }

        return job;
    }

    protected void parseParameters(Parameters p, Set<String> kv) {
        p.tag_coordinates = kv.contains("coordinates");
        p.tag_countries = kv.contains("countries");
        p.tag_places = kv.contains("places");
        p.tag_postal = kv.contains("postal"); /* Postal tuples which include codes */
        p.tag_codes = kv.contains("codes"); /* literal codes, 'IX', 'VQS', 'MA' */

        if (kv.contains("geo")) {
            p.tag_coordinates = true;
            p.tag_countries = true;
            p.tag_places = true;
        }

        // Request tagging on demand.
        p.tag_all_taxons = kv.contains("all_taxons");
        p.tag_taxons = (kv.contains("taxons") || kv.contains("orgs") || kv.contains("persons"));
        p.tag_patterns = kv.contains("patterns") || kv.contains("dates");

        p.output_filtered = kv.contains("filtered_out");
    }

    /**
     * Convenience helper to reset data.
     *
     * @param job job parameters
     */
    protected void resetParameters(Parameters job) {
        job.tag_lowercase = false;
        job.tag_coordinates = false;
        job.tag_countries = false;
        job.tag_places = false;
        job.tag_postal = false;

        job.tag_taxons = false;
        job.tag_all_taxons = false;
        job.tag_patterns = false;
        job.output_geohash = false;
        job.output_filtered = false;

        job.addOutputFormat("json");
    }

    /**
     * @param a JSONArray
     * @return
     */
    protected List<String> fromArray(JSONArray a) {
        ArrayList<String> strings = new ArrayList<>();
        for (Object o : a) {
            strings.add((String) o);
        }
        return strings;
    }

    /**
     * @param inputs the inputs
     * @return job parameters
     * @throws JSONException on error.
     */
    protected Parameters fromRequest(JSONObject inputs) throws JSONException {
        Parameters job = new Parameters();
        job.tag_coordinates = false;
        job.tag_countries = true;
        job.tag_places = true;
        /* Coordinates are not reverse geocoded by default. */
        job.resolve_localities = false;

        job.tag_taxons = true;
        job.tag_patterns = true;
        job.output_filtered = false;
        job.addOutputFormat("json");

        String list;
        try {
            list = inputs.getString(FLD_FEATURES);
            if (list != null) {
                resetParameters(job);
                Set<String> features = new HashSet<>(TextUtils.string2list(list.toLowerCase(), ","));
                this.parseParameters(job, features);
            }
        } catch (Exception err) {
            /* JSONObject getString() fails when retrieving a null value. "options" = null. */
        }

        try {
            list = inputs.getString("options");
            if (list != null) {
                Set<String> opts = new HashSet<>(TextUtils.string2list(list.toLowerCase(), ","));
                job.clean_input = opts.contains("clean_input");
                job.tag_lowercase = opts.contains("lowercase");
                job.resolve_localities = opts.contains("revgeo") || opts.contains("resolve_localities");
            }
        } catch (Exception err) {
            /* JSONObject getString() fails when retrieving a null value. "options" = null. */
        }
        //
        // Geographic filters
        if (inputs.has("preferred_countries")) {
            job.preferredGeography.put("countries", fromArray(inputs.getJSONArray("preferred_countries")));
        }
        if (inputs.has("preferred_locations")) {
            job.preferredGeography.put("geohashes", fromArray(inputs.getJSONArray("preferred_locations")));
        }
        if (job.clean_input || job.tag_lowercase) {
            job.isdefault = false;
        }
        if (inputs.has("minlen")){
            job.minimum_tag_len = inputs.getInt("minlen");
        }

        return job;
    }

    /**
     * @param status status
     * @param error  error msg
     * @return json formatted response
     */
    protected JsonRepresentation status(String status, String error) {
        JSONObject s = new JSONObject();
        try {
            if (error != null) {
                s.put(FLD_STATUS, status);
                s.put("error", error);
            } else {
                s.put(FLD_STATUS, status);
            }
        } catch (JSONException jsonErr) {
            error("Trivial JSON issue!!!!", jsonErr);
        }
        return new JsonRepresentation(s.toString());
    }

    public void error(String msg, Exception err) {
        if (err == null) {
            log.severe(msg);
        } else {
            log.severe(msg + " ERR:" + err.getMessage());
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
