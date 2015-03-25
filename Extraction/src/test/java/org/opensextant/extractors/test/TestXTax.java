package org.opensextant.extractors.test;

import java.util.List;
import java.util.Set;

import org.opensextant.data.Taxon;
import org.opensextant.extractors.xtax.TaxonMatcher;

/**
 * Demonstration of XTax 
 * - search the 'taxcat' catalog
 * - tag data using the 'taxcat' catalog
 * 
 * Prerequisite -- Build the JRC example catalog per Extraction/XTax/  notes
 * 
 * Run with JVM arg:
 *   -Dopensextant.solr=/path/to/your/xponents-solr
 *   
 *   where that solr contains the taxcat core
 *   
 * @author ubaldino
 *
 */
public class TestXTax {

    /**
     * 
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) {

        TaxonMatcher tax = null;
        try {

            tax = new TaxonMatcher();

            // Find JRC entities that have this random id pattern ID='1*1' and are in Russian form.
            // 
            List<Taxon> results = tax.search("tag:jrc_id+1*1 AND tag:lang_id+ru");
            for (Taxon tx : results) {

                System.out.println("Found: " + getJRCTag(tx.tagset) + " = " + tx);
            }

        } catch (Exception err) {
            err.printStackTrace();
        } finally {
            tax.shutdown();
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
