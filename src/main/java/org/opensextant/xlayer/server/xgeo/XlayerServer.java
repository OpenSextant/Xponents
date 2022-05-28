package org.opensextant.xlayer.server.xgeo;

import java.util.HashMap;
import java.util.Map;

import org.restlet.Component;
import org.restlet.Context;
import org.restlet.data.Protocol;

/**
 * @author ubaldino
 */
public class XlayerServer extends Component {

    private static String USAGE = "Usage:\n\t\tXlayerServer <port>";

    public XlayerServer() {
        this(8888);
    }

    public XlayerServer(int port) {
        /* customize app before attaching */
        Context ctx = new Context();

        /*
         * If we had settings,...
         */
        Map<String, Object> settings = new HashMap<>();
        ctx.setAttributes(settings);
        XlayerRestlet service = new XlayerRestlet(ctx);
        /*
         * Configure ports, protocols, security
         */
        getServers().add(Protocol.HTTP, port);

        /*
         * Configure URLs, endpoints.
         */
        getDefaultHost().attach("/xlayer/rest", service);
        this.getContext().setAttributes(settings);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println(USAGE);
            System.exit(-1);
        }
        try {
            new XlayerServer(Integer.parseInt(args[0])).start();
        } catch (Exception err) {
            System.err.println(USAGE);
            System.err.printf("ERROR: %s%n", err.getMessage());
            System.exit(-1);
        }
    }
}
