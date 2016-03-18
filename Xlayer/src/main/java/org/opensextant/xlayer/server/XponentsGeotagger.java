package org.opensextant.xlayer.server;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.processing.Parameters;
import org.opensextant.util.TextUtils;
import org.opensextant.xlayer.Transforms;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 */
public class XponentsGeotagger extends ServerResource {

    /** The log. */
    //protected Logger log = LoggerFactory.getLogger(getClass());
    private Logger log = null;

    /** The request count. */
    public static long requestCount = 0;

    /** The test mode. */
    protected final static boolean testMode = false;

    /** The prod mode. */
    protected final static boolean prodMode = !testMode;

    /**
     * Restlet resource that pulls its configuration from Context.
     */
    public XponentsGeotagger() {
        super();
        log = getContext().getCurrentLogger();
    }

    /**
     * Ping.
     *
     * @return status with current request count
     */
    public void stop() {
        PlaceGeocoder xgeo = (PlaceGeocoder) this.getApplication().getContext()
                .getAttributes().get("xgeo");
        if (xgeo == null) {
            log.info("Misconfigured, no context-level pipeline initialized");
        }
        xgeo.shutdown();

        System.exit(0);
    }

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
    private PlaceGeocoder getExtractor() {
        PlaceGeocoder xgeo = (PlaceGeocoder) this.getApplication().getContext()
                .getAttributes().get("xgeo");
        if (xgeo == null) {
            info("Misconfigured, no context-level pipeline initialized");
            return null;
        }
        return xgeo;
    }

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

            JobParameters job = fromRequest(json);
            return process(item, job);
        }

        //org.json.JSONArray array = json.optJSONArray("doc-list");
        //if (array != null) {
        //    return processList(array);
        //}
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

        JobParameters job = fromRequest(inputs);
        return process(item, job);
    }

    class JobParameters extends Parameters {
        String format = null;
        boolean outputTaxons = true;

        public JobParameters() {
            super();
        }
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
    private JobParameters fromRequest(Form inputs) {
        JobParameters job = new JobParameters();
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

        job.format = inputs.getFirstValue("format");
        if (job.format == null) {
            job.format = "json";
        }

        return job;
    }

    /**
     * 
     * @param inputs
     * @return
     * @throws JSONException
     */
    private JobParameters fromRequest(JSONObject inputs) throws JSONException {
        JobParameters job = new JobParameters();
        job.output_coordinates = false;
        job.output_countries = false;
        job.output_places = false;
        job.tag_coordinates = true;
        job.tag_countries = true;
        job.tag_places = true;
        job.format = "json";

        if (inputs.has("features")) {
            String list = inputs.getString("features");
            Set<String> features = new HashSet<>();

            //JSONArray list = inputs.getJSONArray("features");
            features.addAll(TextUtils.string2list(list.toLowerCase(), ","));

            job.output_coordinates = features.contains("coordinates");
            job.output_countries = features.contains("countries");
            job.output_places = features.contains("places");
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
    private JsonRepresentation status(String status, String error) {
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

    private void error(String msg, Exception err) {
        log.severe(msg + " ERR: " + err.getMessage());
        if (isDebug()) {
            log.fine("" + err.getStackTrace());
        }
    }

    private void info(String msg) {
        log.info(msg);
    }

    private void debug(String msg) {
        if (isDebug()) {
            log.fine(msg);
        }
    }

    private boolean isDebug() {
        return (log.getLevel() == Level.FINE || log.getLevel() == Level.FINEST || log.getLevel() == Level.FINER);
    }

    /**
     * Process the text for the given document.
     *
     * @param docid
     *            the docid
     * @param input
     *            the input
     * @return the representation
     */
    private Representation process(TextInput input, JobParameters jobParams) {

        if (input == null || input.buffer == null) {
            return status("FAIL", "No text");
        }
        debug("Processing plain text doc");

        ++requestCount;
        try {
            if (prodMode) {
                PlaceGeocoder xgeo = getExtractor();

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
        //String matchText = TextUtils.squeeze_whitespace(name.getText());        
        o.put("text", m.getText());
        return o;
    }

    private Representation format(List<TextMatch> matches, JobParameters jobParams) throws JSONException {

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
                if (jobParams.outputTaxons) {

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
                        node.put("catalog", n.catalog); // Name of catalog or source 
                        //node.put("filtered-out", true);

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
}
