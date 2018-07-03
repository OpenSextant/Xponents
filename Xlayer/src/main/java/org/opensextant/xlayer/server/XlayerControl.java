package org.opensextant.xlayer.server;

import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.processing.Parameters;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class XlayerControl extends TaggerResource {

    public XlayerControl() {
        super();
        log = getContext().getCurrentLogger();
    }

    //@Override
    public void stop() {
        // TODO Auto-generated method stub
        Extractor x = getExtractor();
        if (x != null) {
            x.cleanup();
        }
        System.exit(0);
    }

    /**
     * get Xponents Exxtractor object from global attributes. 
     */
    public Extractor getExtractor() {
        PlaceGeocoder xgeo = (PlaceGeocoder) this.getApplication().getContext().getAttributes().get("xgeo");
        if (xgeo == null) {
            info("Misconfigured, no context-level pipeline initialized");
            return null;
        }
        return xgeo;
    }

    @Override
    public Representation process(TextInput input, Parameters jobParams) {
        // TODO Auto-generated method stub
        return new JsonRepresentation("{\"message\":\"not implemented\"}");
    }

    /**
     * /control/OPERATION
     * 
     *   * /control/ping
     *   * /control/stop
     * 
     * @param params
     * @return
     */
    @Get
    public Representation control(Representation params) {
        if ("ping".equalsIgnoreCase(operation)) {
            return ping();
        } else if ("stop".equalsIgnoreCase(operation)) {
            info("Stopping Xponents Xlayer Service Requested by CLIENT=" + getRequest().getClientInfo().getAddress());
            stop();
        }
        return status("failed", "unknown command");
    }

}
