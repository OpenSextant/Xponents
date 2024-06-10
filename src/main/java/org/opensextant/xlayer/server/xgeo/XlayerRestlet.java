package org.opensextant.xlayer.server.xgeo;

import java.io.IOException;
import java.net.URL;

import org.opensextant.ConfigException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.geo.PostalGeocoder;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.opensextant.processing.Parameters;
import org.opensextant.xlayer.server.XlayerApp;
import org.opensextant.xlayer.server.XlayerControl;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * Xlayer Restlet app constructs the application, initializes resources, etc.
 * Actual logic is REST paths for /process --&gt; XponentsGeotagger
 *
 * @author ubaldino
 */
public class XlayerRestlet extends XlayerApp {

    public XlayerRestlet(Context c) {
        super(c);
        getContext();
        log = Context.getCurrentLogger();
    }

    @Override
    public synchronized Restlet createInboundRoot() {
        Router router = new Router(getContext());

        info("Starting Xponents Xlayer Service");

        try {
            banner();
            configure();
            Context ctx = getContext();
            ctx.getAttributes().put(XponentsGeotagger.GEO_TAGGER, tagger);
            ctx.getAttributes().put(XponentsGeotagger.DATE_TAGGER, dateTagger);
            ctx.getAttributes().put(XponentsGeotagger.POSTAL_TAGGER, postalGeocoder);
            ctx.getAttributes().put(XponentsGeotagger.TAXON_TAGGER, phraseTagger);

            ctx.getAttributes().put("version", this.version);
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
    private PostalGeocoder postalGeocoder = null;
    private TaxonMatcher phraseTagger = null;

    /**
     * @throws ConfigException
     */
    @Override
    public void configure() throws ConfigException {
        // Default - process place/country mentions in document texts.
        //
        tagger = new PlaceGeocoder();
        Parameters taggerParams = new Parameters();
        taggerParams.resolve_localities = true;
        tagger.setParameters(taggerParams);
        tagger.configure();

        phraseTagger = new TaxonMatcher();

        // TODO: refine this filter list. Use "/filters/non-placenames,user.csv" going forward.
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

        // NEW: Tag Postal Codes -- if available.
        try {
            postalGeocoder = new PostalGeocoder();
            postalGeocoder.configure();
        } catch (Exception err) {
            /* */
            error("Postal index/tagger is available in Xponents 3.5+ Solr Index", err);
        }
    }
}
