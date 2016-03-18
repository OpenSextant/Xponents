import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.xlayer.XlayerClient;

public class XlayerClientTest {

    public static void main(String[] args) {
        URL url;
        try {
            url = new URL("http://localhost:8890/xlayer/rest/process");
            XlayerClient c = new XlayerClient(url);
            try {
                List<TextMatch> results = c.process("X", "Where is 56:08:45N, 117:33:12W?");
                for (TextMatch m : results) {
                    System.out.println("Found " + m);
                }
            } catch (Exception parseErr) {
                parseErr.printStackTrace();
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ConfigException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
