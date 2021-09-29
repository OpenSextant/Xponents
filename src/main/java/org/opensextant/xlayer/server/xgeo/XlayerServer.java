package org.opensextant.xlayer.server.xgeo;

import org.restlet.Component;
import org.restlet.Context;
import org.restlet.data.Protocol;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ubaldino
 */
public class XlayerServer extends Component {

    public static String usage() {

        return "Usage:\n\t\tXlayerServer <port>";
    }

    public XlayerServer() throws IOException {
        this(8888);
    }

    public XlayerServer(int port) throws IOException {
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
            System.err.println(usage());
            System.exit(-1);
        }
        try {
            new XlayerServer(Integer.parseInt(args[0])).start();
        } catch (Exception err) {
            System.err.println(usage());
            err.printStackTrace();
            System.exit(-1);
        }
    }
}
