import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.FileUtility;
import org.opensextant.xlayer.XlayerClient;

public class XlayerClientTest {

	public static void main(String[] args) {
		URL url;
		try {
			url = new URL(args[0]);
			XlayerClient c = new XlayerClient(url);
			try {
				String text = FileUtility.readFile(args[1]);
				String docid = args[1];
				List<TextMatch> results = c.process(docid, text);
				for (TextMatch m : results) {
					System.out.println(String.format("Found %s %s @ (%d:%d)", m.getType(), m.getText(), m.start, m.end));
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
