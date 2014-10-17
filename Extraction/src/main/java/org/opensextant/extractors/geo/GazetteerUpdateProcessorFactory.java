package org.opensextant.extractors.geo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
/* JS and Groovy scripts worked out well... to an extent.
 * But adding global parameters to those stateless scripts was not possible.
import org.apache.solr.update.processor.StatelessScriptUpdateProcessorFactory;
 * 
 * And so this URP was created to do finer tuning of the solr data.
 */
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
//import org.opensextant.util.AnyFilenameFilter;
//import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

public class GazetteerUpdateProcessorFactory extends UpdateRequestProcessorFactory {

    public GazetteerUpdateProcessorFactory() {
        super();
    }

    protected Set<String> includeCategorySet = null;
    protected boolean includeAll = false;
    protected String catField = "SplitCategory";
    protected static Set<String> stopTerms = null;
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected long rowCount = 0;
    protected long addCount = 0;

    /**
     * include_category   -- Tells the update processor which flavors of data to keep
     * category_field     -- the field name where a row of data is categorized.
     * stopterms -- a list of terms used to mark rows of data as "search_only"
     * 
     */
    @Override
    public void init(NamedList params) {
        String ic = (String) params.get("include_category"); //array of String
        if (ic != null) {
            includeCategorySet = new HashSet<String>();
            includeCategorySet.addAll(TextUtils.string2list(ic, ","));
            if (includeCategorySet.contains("all")) {
                includeAll = true;
            }
        }

        if (params.get("category_field") != null) {
            catField = (String) params.get("category_field");
        }

        /* Load the exclusion names -- these are terms that are 
         * gazeteer entries, e.g., gazetteer.name = <exclusion term>,
         * that will be marked as search_only = true.
         * 
         */
        try (InputStream io = new FileInputStream(new File(
                "gazetteer/conf/exclusions/non-placenames.csv"))) {
            java.io.Reader termsIO = new InputStreamReader(io);
            CsvMapReader termreader = new CsvMapReader(termsIO, CsvPreference.EXCEL_PREFERENCE);
            String[] columns = termreader.getHeader(true);
            Map<String, String> terms = null;
            stopTerms = new HashSet<String>();
            while ((terms = termreader.read(columns)) != null) {

                String term = terms.get("exclusion");
                if (StringUtils.isBlank(term) || term.startsWith("#")) {
                    continue;
                }
                stopTerms.add(term.toLowerCase());
            }
        } catch (IOException err) {
            logger.error("Unable to load exclusion terms");
            return;
        }
    }

    /**
     * Returns null if the factory is not setup properly, e.g., stopTerms not found.
     */
    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp,
            UpdateRequestProcessor next) {
        if (stopTerms == null) {
            return null;
        }
        return new GazetteerUpdateProcessor(next);
    }

    class GazetteerUpdateProcessor extends UpdateRequestProcessor {

        public GazetteerUpdateProcessor(UpdateRequestProcessor next) {
            super(next);
        }

        /**
         * Adding a gazetteer entry involves looking at a few fields
         *  -- we keep it if its values match the desired "include category"
         *  -- if we keep it, we filter it and mark "search_only" if needed.
         *  -- finally, ensure geo = lat,lon format
         */
        @Override
        public void processAdd(AddUpdateCommand cmd) throws IOException {
            SolrInputDocument doc = cmd.getSolrInputDocument();

            ++rowCount;

            if (rowCount % 100000 == 0) {
                logger.info("GazURP ## Row {}; Added: {}", rowCount, addCount);
            }

            /* See solrconfig for documentation on gazetteer filtering
             * =======================================================
             */
            String nm = (String) doc.getFieldValue("name");
            if (!includeAll && includeCategorySet != null) {
                String cat = (String) doc.getFieldValue(catField);
                if (cat == null) {
                    cat = "general";
                }
                if (!includeCategorySet.contains(cat)) {
                    logger.info("GazURP ##: Exclude {} {}", cat, nm);
                    return;
                }
            }

            boolean search_only = false;

            /* Trivially short ASCII terms are not good for tagging.
             * 
             */
            if (nm.length() < 2 && StringUtils.isAsciiPrintable(nm)) {
                search_only = true;
                logger.info("GazURP ##: Short name set search only {}", nm);
            }

            String nameLower = nm.toLowerCase();
            if (stopTerms.contains(nameLower)) {
                search_only = true;
                logger.info("GazURP ## Stop word set search only {}", nm);
            }

            if (search_only) {
                doc.setField("search_only", "true");
            } else {
                doc.removeField("search_only");
            }

            /* End Filtering
            * =======================================================
            */

            // CREATE searchable lat lon
            String lat = (String) doc.getFieldValue("lat");
            String lon = (String) doc.getFieldValue("lon");

            if (lat != null && lon != null) {
                doc.setField("geo", lat + "," + lon);
            }

            ++addCount;

            // pass it up the chain
            super.processAdd(cmd);
        }
    }
}
