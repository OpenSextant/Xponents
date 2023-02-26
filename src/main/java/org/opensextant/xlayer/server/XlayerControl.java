package org.opensextant.xlayer.server;

import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.processing.Parameters;
import org.restlet.Context;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class XlayerControl extends TaggerResource {

    public XlayerControl() {
        super();
        getContext();
        log = Context.getCurrentLogger();
    }

    public void stop() {
        Extractor x = getExtractor(GEO_TAGGER);
        if (x != null) {
            x.cleanup();
        }
        x = getExtractor(TAXON_TAGGER);
        if (x != null) {
            x.cleanup();
        }
        System.exit(0);
    }

    /**
     * get Xponents Exxtractor object from global attributes.
     */
    @Override
    public Extractor getExtractor(String xid) {
        /*
         * xid argument is ignored. This default case, only the 'xgeo' object is queried
         * to stop it.
         */
        Extractor extor = (Extractor) this.getApplication().getContext().getAttributes().get(xid);
        if (extor != null) {
            return extor;
        }
        info("Misconfigured, " + xid + " Not found");
        return null;
    }

    @Override
    public Representation process(TextInput input, Parameters jobParams) {
        return new JsonRepresentation("{\"message\":\"not implemented\"}");
    }

    /**
     * /control/OPERATION, for example:
     * /control/ping
     * /control/stop
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
