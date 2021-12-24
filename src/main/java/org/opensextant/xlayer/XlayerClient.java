package org.opensextant.xlayer;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.output.Transforms;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Parameter;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jodd.json.JsonArray;
import jodd.json.JsonObject;
import jodd.json.JsonParser;

public class XlayerClient extends Application {

    protected URL serviceAddress;
    private URI serviceURI;

    protected Logger log = LoggerFactory.getLogger(getClass());
    protected final Context serviceContext = new Context();

    private static final int ONE_SEC = 1000;

    /** 1 minute */
    public static final String SO_TIMEOUT_STRING = Integer.toString(60 * ONE_SEC);
    /** 1 minutes */
    public static final String READ_TIMEOUT_STRING = Integer.toString(60 * ONE_SEC);
    /* 5 seconds */
    public static final String CONN_TIMEOUT_STRING = Integer.toString(5 * ONE_SEC);
    protected JsonParser jsonp = JsonParser.create();

    public XlayerClient(URL serviceAddr) throws ConfigException {

        serviceAddress = serviceAddr;
        try {
            serviceURI = serviceAddr.toURI();
        } catch (Exception err) {
            throw new ConfigException("Bad Service Address", err);
        }

        /*
         * Not-so-super convincing timeout settings documented here:
         * http://restlet.com/technical-resources/restlet-framework/javadocs/2.3
         * /jse/engine/org/restlet/engine/connector/HttpClientHelper.html
         * Hard to find complete info on timeouts for HTTP in restlet.
         */
        serviceContext.getParameters().add(new Parameter("socketTimeout", SO_TIMEOUT_STRING));
        serviceContext.getParameters().add(new Parameter("readTimeout", READ_TIMEOUT_STRING));
        serviceContext.getParameters().add(new Parameter("socketConnectTimeoutMs", CONN_TIMEOUT_STRING));
    }

    /**
     * @param docid           id
     * @param text            input text
     * @param viewFilteredOut true if user wants to print filtered out items for
     *                        diagnostics.
     * @return list of matches
     * @throws IOException on error
     */
    public List<TextMatch> process(String docid, String text, boolean viewFilteredOut) throws IOException {

        ClientResource client = new ClientResource(serviceContext, serviceURI);
        JsonObject content = new JsonObject();
        content.put("text", text);
        content.put("docid", docid);
        String featureSet = "places,coordinates,countries,persons,orgs,reverse-geocode,dates"
                + (viewFilteredOut ? ",filtered_out" : "");
        content.put("features", featureSet);
        /*
         * Coordinates mainly are XY locations; Reverse Geocode them to find what
         * country the location resides
         */
        StringWriter data = new StringWriter();

        try {
            Representation repr = new JsonRepresentation(content.toString());
            repr.setCharacterSet(CharacterSet.UTF_8);
            // Process and read response fully.
            Representation response = client.post(repr, MediaType.APPLICATION_JSON);
            response.write(data);
            response.exhaust();
            response.release();

            JsonObject json = jsonp.parseAsJsonObject(data.toString());
            log.debug("POST: response  {}", json.toString());
            JsonObject meta = json.getJsonObject("response");
            JsonArray annots = json.getJsonArray("annotations");
            List<TextMatch> matches = new ArrayList<>();
            for (int x = 0; x < annots.size(); ++x) {
                JsonObject m = annots.getJsonObject(x);
                matches.add(Transforms.parseAnnotation(m));
            }
            return matches;

        } catch (ResourceException restErr) {
            if (restErr.getCause() instanceof IOException) {
                throw (IOException) restErr.getCause();
            } else {
                throw restErr;
            }
        }  finally {
            client.release();
        }
    }
}
