import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.FileUtility;
import org.opensextant.xlayer.XlayerClient;

public class XlayerClientTester {

    public static void main(String[] args) {
        URL url;
        try {
            url = new URL(args[0]);

            /*
             * Create client.
             */
            XlayerClient c = new XlayerClient(url);
            try {
                /*
                 * Prepare request. Text must be UTF-8 encoded. Note -- readFile() here assumes
                 * the file is unicode content
                 */
                String text = FileUtility.readFile(args[1]);
                String docid = args[1];

                /*
                 * Process the text and print results to console. Result is an array of
                 * TextMatch objects. For each particular TextMatch (Xponents Basic API), you
                 * have some common fields related to the text found, and then class-specific
                 * fields and objects you need to evaluate yourself.
                 * The XlayerClient process() method makes use of Transforms helper class to
                 * digest JSON annotations into Java API TextMatch objects of various flavors.
                 */
                System.out.println("Processing document " + docid);
                List<TextMatch> results = c.process(docid, text, false);
                for (TextMatch m : results) {
                    String filt = m.isFilteredOut() ? "\tFILTERED NOISE" : "";
                    System.out.println(
                            String.format("Found %s %s @ (%d:%d) " + filt, m.getType(), m.getText(), m.start, m.end));
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
