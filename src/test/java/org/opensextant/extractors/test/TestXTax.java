package org.opensextant.extractors.test;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.opensextant.data.Taxon;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.opensextant.util.FileUtility;

/**
 * Demonstration of XTax - search the 'taxcat' catalog - tag data using the
 * 'taxcat' catalog
 * Prerequisite -- Build the JRC example catalog per Extraction/XTax/ notes
 * Run with JVM arg: -Dopensextant.solr=/path/to/your/xponents-solr
 * where that solr contains the taxcat core
 *
 * @author ubaldino
 */
public class TestXTax {

    /**
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) {

        TaxonMatcher tax = null;
        try {

            tax = new TaxonMatcher();

            // Find JRC entities that have this random id pattern ID='1*1' and are in
            // Russian form.
            //
            List<Taxon> results = tax.search("tag:jrc_id+1*1 AND tag:lang_id+ru");
            for (Taxon tx : results) {

                System.out.println("Found: " + getJRCTag(tx.tagset) + " = " + tx);
            }

            if (args.length > 0) {
                File f = new File(args[0]);
                String content = FileUtility.readFile(f, "UTF-8");
                List<TextMatch> findings = tax.extract(content);
                for (TextMatch tm : findings) {
                    String type = "" + ((TaxonMatch) tm).getTaxons();
                    System.out.println("Found: " + tm + "\n\t\t" + type);
                }
            }

        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            tax.close();
            System.exit(0);
        }
    }

    private static String getJRCTag(Set<String> set) {
        for (String t : set) {
            if (t.startsWith("jrc_id")) {
                return t;
            }
        }
        return null;
    }
}
