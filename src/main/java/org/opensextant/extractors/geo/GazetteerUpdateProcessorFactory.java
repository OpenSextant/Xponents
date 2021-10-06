/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *               http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *    Copyright 2013-2015 The MITRE Corporation.
 */
package org.opensextant.extractors.geo;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
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
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.SolrUtil;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GazetteerUpdateProcessorFactory extends UpdateRequestProcessorFactory {

    public GazetteerUpdateProcessorFactory() {
        super();
    }

    protected static Set<String> includeCategorySet = null;
    protected static Set<String> includeCountrySet = null;
    protected static boolean includeAll = false;
    protected static String catField = "SplitCategory";
    // protected static Set<String> stopTerms = null;
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected static long rowCount = 0;
    protected static long addCount = 0;
    protected static GeonamesUtility helper = null;
    protected static final Pattern shortAlphanum = Pattern.compile("\\w+.+\\d+");
    protected TagFilter termFilter = null;

    /**
     * NamedList? in Solr. What a horror. Okay, they work, but...
     * items found in solrconfig under the gaz update processor script:
     * include_category -- Tells the update processor which flavors of data to
     * keep category_field -- the field name where a row of data is categorized.
     * countries -- a list of countries to use. stopterms -- a list of terms
     * used to mark rows of data as "search_only"
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void init(NamedList params) {
        /*
         * Load the exclusion names -- these are terms that are gazeteer
         * entries, e.g., gazetteer.name = <exclusion term>, that will be marked
         * as search_only = true.
         */
        try {
            termFilter = new TagFilter();
            termFilter.enableCaseSensitive(false);
            helper = new GeonamesUtility();
        } catch (Exception err) {
            logger.error("Init failure", err);
        }

        String logTag = "GAZ UPR////////////// ";

        logger.debug("Parameters for Gaz Updater {}, size={}, Country Filter?{}", params, params.size(),
                params.getAll("countries"));
        if (params.size() == 0) {
            logger.debug(logTag + "Zero parameters found.");
            return;
        }
        NamedList p = (NamedList) params.getVal(0);
        logger.debug(logTag + "Found p={}", p);
        logger.debug(logTag + "P={}, V={}", p.getName(0), p.getVal(0));

        List<String> ic = (List<String>) p.get("include_category"); // array of

        /*
         * Optional: filter entries with a category list
         */
        // String
        if (ic != null) {
            logger.debug(logTag + "Found CAT={}", ic);
            includeCategorySet = new HashSet<>();
            List<String> val = TextUtils.string2list(ic.get(0), ",");
            includeCategorySet.addAll(val);
            if (includeCategorySet.contains("all")) {
                includeAll = true;
            }
        } else {
            logger.error(logTag + "No category found.");
        }

        /*
         * Optional: filter entries with a country list
         */
        List<String> cc = (List<String>) p.get("countries");
        if (cc != null) {
            logger.debug("Found CO={}", ic);
            includeCountrySet = new HashSet<>();
            List<String> val = TextUtils.string2list(cc.get(0), ",");
            includeCountrySet.addAll(val);
        } else {
            logger.debug("No country filter found");
        }

        if (params.get("category_field") != null) {
            catField = (String) p.get("category_field");
        }

        logger.debug(logTag + " DONE.   CAT={}, CO={}", includeCategorySet, includeCountrySet);
    }

    /**
     * Returns null if the factory is not setup properly, e.g., stopTerms not
     * found.
     */
    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp,
            UpdateRequestProcessor next) {
        return new GazetteerUpdateProcessor(next);
    }

    /**
     * Test for short WWWW NNNN alphanumeric coded stuff.
     *
     * @param nm
     * @param feat
     * @return
     */
    public static boolean ignoreShortAlphanumeric(String nm, String feat) {
        if (GeonamesUtility.isAdministrative(feat) || GeonamesUtility.isPopulated(feat)) {
            return false;
        }
        return shortAlphanum.matcher(nm).matches();
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
         * Adding a gazetteer entry involves looking at a few fields -- we keep
         * it if its values match the desired "include category" -- if we keep
         * it, we filter it and mark "search_only" if needed. -- finally, ensure
         * geo = lat,lon format
         */
        @Override
        public void processAdd(AddUpdateCommand cmd) throws IOException {
            SolrInputDocument doc = cmd.getSolrInputDocument();

            ++rowCount;

            if (rowCount % 100000 == 0) {
                logger.info("GazURP ## Row {}; Excluded:{}", rowCount, excludedTerms.size());
            }

            String cc = SolrUtil.getString(doc, "cc");
            String fips = SolrUtil.getString(doc, "FIPS_cc");

            if (includeCountrySet != null) {
                if (!includeCountrySet.contains(cc)) {
                    logger.debug("Filtered out CC={}", cc);
                    return;
                }
            }

            /*
             * See solrconfig for documentation on gazetteer filtering
             * =======================================================
             */
            String nm = SolrUtil.getString(doc, "name");
            if (nm == null){
                logger.info("Name field may not be empty.");
                return;
            }
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
            String nt = SolrUtil.getString(doc, "name_type");
            boolean isName = (nt != null && "N".equals(nt));

            /*
             * Cleanup scripts.
             */
            String nameScript = SolrUtil.getString(doc, "script");

            if (StringUtils.isNotBlank(nameScript)) {
                doc.removeField("script");
                List<String> nameScripts = TextUtils.string2list(TextUtils.removeAny(nameScript, "[]"), ",");
                logger.debug("Scripts = {}", nameScripts);
                for (String scr : nameScripts) {
                    doc.addField("script", scr);
                    switch (scr) {
                    case "ARABIC":
                        doc.setField("name_ar", nm);
                        break;
                    case "CJK":
                    case "HANGUL":
                    case "KATAKANA":
                    case "HIRAGANA":
                        doc.setField("name_cjk", nm);
                        break;
                    default:
                        break;
                    }
                }
            }

            boolean search_only = false;

            /*
             * Trivially short ASCII names are not good for tagging.
             * But do not mark codes as search only.
             */
            String nm2 = nm.replace(".", "").trim();
            if (isName) {
                if ((nm.length() <= 2 || nm2.length() <= 2) && StringUtils.isAsciiPrintable(nm)) {
                    search_only = true;
                    logger.debug("GazURP ##: Short name set search only {}", nm);
                }
            }

            String nameLower = nm2.toLowerCase();
            if (!search_only) {
                if (termFilter.filterOut(nameLower)) {
                    search_only = true;
                    logger.debug("GazURP ## Stop word set search only {}", nm);
                }
            }

            /*
             * Pattern: Short word followed by digit. XXXX NNN
             * Approximately one word of text Ignore things that are not major places - adm
             * or ppl
             */
            if (!search_only && nm2.length() <= 8) {
                search_only = ignoreShortAlphanumeric(nm2, SolrUtil.getString(doc, "feat_class"));
            }

            /*
             * For relatively short terms that may also be stopterms, first
             * convert to non-diacritic form, then lower case result. If result
             * is a stop term or exclusion term then it should be tagged
             * search_only
             */
            if (!search_only && nm.length() < 15) {
                String nameNonDiacrtic = TextUtils.replaceDiacritics(nm).toLowerCase();
                nameNonDiacrtic = TextUtils.replaceAny(nameNonDiacrtic, "‘’-", " ").trim();
                if (termFilter.filterOut(nameNonDiacrtic)) {
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

            /*
             * End Filtering
             * =======================================================
             */

            // CREATE searchable lat lon
            String lat = SolrUtil.getString(doc, "lat");
            String lon = SolrUtil.getString(doc, "lon");

            if (lat != null && lon != null) {
                // Where SpatialRecursivePrefixTreeFieldType is used format "LAT
                // LON" is required.
                // Documentation is not clear on this issue. Order, LAT LON is
                // right, but use of comma vs. space is uncertain.
                //
                doc.setField("geo", lat + "," + lon);
            }

            scrubCountryCode(doc, "adm1", cc, fips);
            scrubCountryCode(doc, "adm2", cc, fips);

            ++addCount;

            // pass it up the chain
            logger.debug("Added DOC=" + doc.getFieldValue("name"));
            super.processAdd(cmd);
        }

        /**
         * Parse off country codes that duplicate information in ADM boundary
         * code.
         * MX19 => '19', is sufficient. In cases where FIPS/ISO codes differ
         * (almost all!), then this is significant.
         * Searchability: use has to know that ADM1 code is using a given
         * standard. e.g., adm1 = 'IZ08', instead of the more flexible, cc='IQ',
         * adm1='08'
         * Hiearchical/Lexical organization: CC.ADM1 is useful to organize data,
         * but without this normalization, you might have 'IQ.IZ08' -- which is
         * not wrong, just confusing. IQ.08 is a little easier to parse.
         * So for now, the given Gazetteer entries are remapped to ISO coding.
         * Recommendation: we standardize on ISO country codes where possible.
         *
         * @param d
         *              the gazetteer solr document.
         * @param field
         *              name of an ADMn field, ADM1, ADM2...etc.
         * @param cc
         *              ISO country code
         * @param fips
         *              FIPS country code
         */
        private void scrubCountryCode(SolrInputDocument d, String field, String cc, String fips) {
            String adm = SolrUtil.getString(d, field);
            if (adm == null) {
                /* nothing to do. */
                return;
            }

            // logger.debug("Remap ADM1 code? {} in {}, {}", adm, cc, fips);

            if (adm.startsWith(cc)) {
                d.setField(field, adm.substring(cc.length()));
                return;
            }

            if (fips == null) {
                return;
            }

            /*
             * Strip off FIPS.ADM1
             */
            if (adm.startsWith(fips)) {
                d.setField(field, adm.substring(fips.length()));
                return;
            }

            /*
             * In this situation, the ADM1 code does not contain the given CC or
             * FIPS code; it refers to a different country so find that country
             * code and replace it with ISO if possible.
             */
            if (adm.length() > 2) {
                String cc2 = adm.substring(0, 2);
                String isocode = helper.FIPS2ISO(cc2);
                if (isocode != null) {
                    // this is a country.
                    String newAdm = String.format("%s.%s", isocode, adm.substring(2));
                    d.setField(field, newAdm);
                    logger.info("Metadata reset for {} => {}", adm, newAdm);

                } else {
                    logger.info("Metadata not found for {}", adm);
                }
            }
        }
    }
}
