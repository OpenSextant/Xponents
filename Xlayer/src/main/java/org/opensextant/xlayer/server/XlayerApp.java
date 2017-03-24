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
public abstract class XlayerApp extends Application {

	/** The log. */
	protected Logger log = null;

	public XlayerApp(Context c) {
		super(c);
		log = getContext().getCurrentLogger();
	}

	protected static  String version = "v2.9";

	protected void banner() throws IOException {
		info("\n" + FileUtility.readFile("etc/banner.txt"));
	}

	protected void error(String msg, Exception err) {
		log.severe(msg + " ERR: " + err.getMessage());
		if (isDebug()) {
			log.fine("" + err.getStackTrace());
		}
	}

	protected void info(String msg) {
		log.info(msg);
	}

	protected void debug(String msg) {
		if (isDebug()) {
			log.fine(msg);
		}
	}

	protected boolean isDebug() {
		return (log.getLevel() == Level.FINE || log.getLevel() == Level.FINEST || log.getLevel() == Level.FINER);
	}

	@Override
	public abstract Restlet createInboundRoot();

	/**
	 * 
	 * @throws ConfigException
	 */
	public abstract void configure() throws ConfigException;
}
