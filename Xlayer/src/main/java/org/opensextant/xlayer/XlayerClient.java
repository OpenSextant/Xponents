package org.opensextant.xlayer;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.restlet.Application;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Parameter;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.restlet.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XlayerClient extends Application {

    protected URL serviceAddress = null;
    private URI serviceURI = null;

    protected Logger log = LoggerFactory.getLogger(getClass());
    protected final Context serviceContext = new Context();

    private static final int ONE_SEC = 1000;

    /** 1 minute */
    public static final String SO_TIMEOUT_STRING = new Integer(60 * ONE_SEC).toString();
    /** 1 minutes */
    public static final String READ_TIMEOUT_STRING = new Integer(60 * ONE_SEC).toString();
    /* 5 seconds */
    public static final String CONN_TIMEOUT_STRING = new Integer(5 * ONE_SEC).toString();

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
         * 
         * Hard to find complete info on timeouts for HTTP in restlet.
         */
        serviceContext.getParameters().add(new Parameter("socketTimeout", SO_TIMEOUT_STRING));
        serviceContext.getParameters().add(new Parameter("readTimeout", READ_TIMEOUT_STRING));
        serviceContext.getParameters().add(new Parameter("socketConnectTimeoutMs", CONN_TIMEOUT_STRING));
    }

    /**
     * 
     * @param text
     * @return
     * @throws IOException
     * @throws JSONException
     */
    public List<TextMatch> process(String docid, String text) throws IOException, JSONException {

        ClientResource client = new ClientResource(serviceContext, serviceURI);
        org.json.JSONObject content = new JSONObject();
        content.put("text", text);
        content.put("docid", docid);
        content.put("features", "places,coordinates,countries,persons,orgs,reverse-geocode");
        /* Coordinates mainly are XY locations; Reverse Geocode them to find what country the location resides */
        StringWriter data = new StringWriter();

        try {
            Representation repr = new JsonRepresentation(content.toString());
            repr.setCharacterSet(CharacterSet.UTF_8);
            //log.debug("CLIENT {} {}", serviceAddress, client);
            // Process and read response fully.
            Representation response = client.post(repr, MediaType.APPLICATION_JSON);
            response.write(data);
            response.exhaust();
            response.release();

            JSONObject json = new JSONObject(data.toString());
            log.debug("POST: response  {}", json.toString(2));
            JSONObject meta = json.getJSONObject("response");
            JSONArray annots = json.getJSONArray("annotations");
            List<TextMatch> matches = new ArrayList<TextMatch>();
            for (int x = 0; x < annots.length(); ++x) {
                Object m = annots.get(x);
                matches.add(Transforms.parseAnnotation(m));
            }
            return matches;

        } catch (ResourceException restErr) {
            if (restErr.getCause() instanceof IOException) {
                throw (IOException) restErr.getCause();
            } else {
                throw restErr;
            }
        } catch (java.net.SocketException err) {
            throw err; // This never happens. Restlet wraps everything.
        } finally {
            client.release();
        }
    }
}
