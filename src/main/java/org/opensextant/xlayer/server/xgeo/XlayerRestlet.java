package org.opensextant.xlayer.server.xgeo;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import org.opensextant.ConfigException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.opensextant.processing.Parameters;
import org.opensextant.xlayer.server.XlayerApp;
import org.opensextant.xlayer.server.XlayerControl;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * @author ubaldino
 *
 */
public class XlayerRestlet extends XlayerApp {

    /** The log. */
    protected Logger log = null;

    public XlayerRestlet(Context c) {
        super(c);
        version = "v3.0";
        log = getContext().getCurrentLogger();
    }

    @Override
    public synchronized Restlet createInboundRoot() {
        Router router = new Router(getContext());

        info("Starting Xponents Xlayer Service");

        try {
            banner();
            configure();
            Context ctx = getContext();
            ctx.getAttributes().put("xgeo", tagger);
            ctx.getAttributes().put("xtemp", dateTagger);
            info("%%%%   Xponents Geo Phase Configured");
        } catch (Exception err) {
            error("Unable to start", err);
            System.exit(-1);
        }

        router.attach("/control/{operation}", XlayerControl.class);
        router.attach("/process", XponentsGeotagger.class);

        return router;
    }

    private PlaceGeocoder tagger = null;
    private XTemporal dateTagger = null;

    /**
     * 
     * @throws ConfigException
     */
    public void configure() throws ConfigException {
        // Default - process place/country mentions in document texts.
        //
        tagger = new PlaceGeocoder();
        Parameters taggerParams = new Parameters();
        taggerParams.resolve_localities = true;
        tagger.setParameters(taggerParams); 
        //See Xponents concept of Parameters
        tagger.enablePersonNameMatching(true);
        tagger.configure();

        // TODO: refine this filter list.  Use "/filters/non-placenames,user.csv" going forward. 
        // 
        String userFilterPath = "/filters/non-placenames,user.csv";
        URL filterFile = getClass().getResource(userFilterPath);
        if (filterFile != null) {
            // Add User filter here. This prevents irrelevant stuff from
            // getting out of naiive tagging phase.
            //
            try {
                MatchFilter filt = new MatchFilter(filterFile);
                tagger.setMatchFilter(filt);
            } catch (IOException err) {
                throw new ConfigException("Setup error with geonames utility or other configuration", err);
            }
        } else {
            info("Optional user filter not found.  User exclusion list is file=" + userFilterPath);
        }
        
        // Support Dates
        // 
        dateTagger = new XTemporal();
        dateTagger.configure();
    }
}
