package org.opensextant.xlayer.server;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.commons.io.IOUtils;
import org.opensextant.ConfigException;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;

/**
 * XlayerApp is an abstract "Webapp" running inside the Server.... well, you must implement one
 * first.
 * 
 * @author ubaldino
 *
 */
public abstract class XlayerApp extends Application {

    /** The log. */
    protected Logger log = null;
    protected String version = "3";

    public XlayerApp(Context c) {
        super(c);
        log = getContext().getCurrentLogger();
    }

    public static String getVersion(String buf) {
        /*
         * Capture version from banner
         */
        Pattern pat = Pattern.compile("VERSION:\\s+(.+)\n");
        Matcher m = pat.matcher(buf);
        if (m.find()) {
            String ver = m.group(1);
            return ver;
        }
        return null;
    }

    /**
     * Banner at start improves visibility of your product.
     * 
     * @throws IOException
     */
    protected void banner() throws IOException {
        URL obj = XlayerApp.class.getResource("/banner.txt");
        if (obj != null) {
            String version_banner = IOUtils.toString(obj.openStream(), "UTF-8");
            this.version = getVersion(version_banner);
            info("\n" + version_banner);
        } else {
            info("\nOpenSextant Xponents module 3.x -- banner.txt is missing or CLASSPATH is misconfigured.");
        }
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
