package org.opensextant.examples;

import java.util.List;

import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.util.FileUtility;

public class TaxonomicTagger {

	static TaxonMatcher taxtag = null;

	public static void testDoc(String buf) throws ExtractionException {
		List<TextMatch> matches = taxtag.extract(new TextInput("test", buf));

		for (TextMatch tx : matches) {
			System.out.println(tx.toString());
		}
	}

	/**
	 * Do a basic test of TaxTagger. Read a file, tag it.
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		gnu.getopt.Getopt opts = new gnu.getopt.Getopt("TaxonomicTagger", args,
				"i:c:");

		int c = -1;
		String file = null;
		String catalog = null;
		while ((c = opts.getopt()) != -1) {

			switch (c) {
			case 'i':
				file = opts.getOptarg();
				break;
			case 'c':
				catalog = opts.getOptarg();
				break;

			default:
				System.out.println("Usage  -i filename [-c cat]");
				System.exit(-1);
			}

		}
		taxtag = new TaxonMatcher();
		taxtag.configure();

		try {
			// String doc =
			// "Fruits of paradise are like pineapple, guava, passion fruit. "+
			// " You may abandon the calories by eating fewer than one a day";

			String doc = FileUtility.readFile(file);

			// No filters.
			testDoc(doc);

			// Invalid filter
			System.out.println("Testing invalid catalog");
			taxtag.addCatalogFilter("Boo");
			// taxtag.reset();
			testDoc(doc);

			if (catalog != null) {
				// Invalid filter + valid filter.
				System.out
						.println("Testing a valid catalog - tagger will return only matches from subset catalog="
								+ catalog);
				taxtag.addCatalogFilter(catalog);
				// taxtag.reset();
				testDoc(doc);
			}

			//TaxonMatcher.shutdown();
		} catch (Exception err) {
			err.printStackTrace();
		}
		
		taxtag.cleanup();
	}

}
