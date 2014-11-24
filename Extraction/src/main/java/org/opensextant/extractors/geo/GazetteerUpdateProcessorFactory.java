package org.opensextant.extractors.geo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
//import org.apache.lucene.analysis.charfilter.MappingCharFilterFactory;
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
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GazetteerUpdateProcessorFactory extends UpdateRequestProcessorFactory {

    public GazetteerUpdateProcessorFactory() {
        super();
    }

    protected Set<String> includeCategorySet = null;
    protected Set<String> includeCountrySet = null;
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
        String cc = (String) params.get("countries");
        if (cc != null) {
            includeCountrySet = new HashSet<>();
            includeCountrySet.addAll(TextUtils.string2list(cc, ","));
        }

        if (params.get("category_field") != null) {
            catField = (String) params.get("category_field");
        }

        /* Load the exclusion names -- these are terms that are 
         * gazeteer entries, e.g., gazetteer.name = <exclusion term>,
         * that will be marked as search_only = true.
         * 
         */
        try {
            stopTerms = GazetteerMatcher.loadExclusions(GazetteerMatcher.class
                    .getResource("/filters/non-placenames.csv"));
        } catch (Exception err) {
            logger.error("Init failure", err);
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

        protected Set<String> excludedTerms = new HashSet<>();

        @Override
        public void finish() throws IOException {
            logger.info("Terms marked search_only: {}", excludedTerms);
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
                logger.info("GazURP ## Row {}; Excluded:{}", rowCount, excludedTerms.size());
            }

            if (includeCountrySet != null) {
                String cc = (String) doc.getFieldValue("cc");
                if (!includeCountrySet.contains(cc)) {
                    logger.debug("Filtered out CC={}", cc);
                    return;
                }
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
                    logger.debug("GazURP ##: Exclude {} {}", cat, nm);
                    return;
                }
            }
            String nt = (String) doc.getFieldValue("name_type");
            boolean isName = (nt != null ? "N".equals(nt) : false);

            boolean search_only = false;

            /* Trivially short ASCII names are not good for tagging.
             * Do not mark codes as search only.
             */
            String nm2 = nm.replace(".", "").trim();
            if (isName) {
                if ((nm.length() <= 2 || nm2.length() <= 2) && StringUtils.isAsciiPrintable(nm)) {
                    search_only = true;
                    logger.debug("GazURP ##: Short name set search only {}", nm);
                }
            }

            if (!search_only) {
                String nameLower = nm2.toLowerCase();
                if (stopTerms.contains(nameLower)) {
                    search_only = true;
                    logger.debug("GazURP ## Stop word set search only {}", nm);
                }
            }

            /* For relatively short terms that may also be stopterms, 
             * first convert to non-diacritic form, then lower case result.
             * If result is a stop term or exclusion term then it should be tagged search_only
             * 
             */
            if (!search_only && nm.length() < 15) {
                String nameNonDiacrtic = TextUtils.replaceDiacritics(nm).toLowerCase();
                nameNonDiacrtic = TextUtils.replaceAny(nameNonDiacrtic, "‘’-", " ").trim();
                if (stopTerms.contains(nameNonDiacrtic)) {
                    search_only = true;
                    logger.info("GazURP ## Stop word set search only {} ({})", nm, nameNonDiacrtic);
                }
            }

            if (search_only) {
                doc.setField("search_only", "true");
                excludedTerms.add(nm);
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
                // Where  SpatialRecursivePrefixTreeFieldType is used format "LAT LON" is required.
                // Documentation is not clear on this issue.  Order, LAT LON is right, but use of comma vs. space is uncertain.
                //
                doc.setField("geo", lat + "," + lon);
            }

            ++addCount;

            // pass it up the chain
            super.processAdd(cmd);
        }
    }
}
