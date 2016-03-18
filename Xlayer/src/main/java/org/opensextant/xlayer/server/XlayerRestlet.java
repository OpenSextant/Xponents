package org.opensextant.xlayer.server;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensextant.ConfigException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.util.FileUtility;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * @author ubaldino
 *
 */
public class XlayerRestlet extends Application {

    /** The log. */
    protected Logger log = null;

    public XlayerRestlet(Context c) {
        super(c);
        log = getContext().getCurrentLogger();
    }

    private static final String version = "v2.8";

    private void banner() throws IOException {
        info("\n" + FileUtility.readFile("etc/banner.txt"));
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

    @Override
    public synchronized Restlet createInboundRoot() {
        Router router = new Router(getContext());

        info("Starting Xponents Xlayer Service");

        try {
            banner();
            configure();
            Context ctx = getContext();
            ctx.getAttributes().put("xgeo", tagger);
            info("%%%%   Xponents Geo Phase Configured");
        } catch (Exception err) {
            error("Unable to start", err);
            System.exit(-1);
        }

        router.attach("/process", XponentsGeotagger.class);

        return router;
    }

    private PlaceGeocoder tagger = null;

    /**
     * 
     * @throws ConfigException
     */
    public void configure() throws ConfigException {
        // Default - process place/country mentions in document texts.
        //
        tagger = new PlaceGeocoder();
        // tagger.setParameters(this.params); See Xponents concept of Parameters
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
    }
}
