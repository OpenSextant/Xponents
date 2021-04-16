/**
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
 *    Copyright 2013-2019 The MITRE Corporation.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.opensextant.ConfigException;
import org.opensextant.extractors.geo.TagFilter;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.SolrUtil;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.getopt.Getopt;

/**
 * @deprecated GazetteerIndexer is replaced by Python/SQLite scripts for producing master gazetter
 */
 @Deprecated
public class GazetteerIndexer {

    public static void main(String... args) {
        Getopt opts = new Getopt("Gazetteer Indexer", args, "u:s:h:i:c:t:f:");
        String url = null;
        String schema = null;
        String includeCat = null;
        String ccList = null;
        String catField = null;
        String file = null;
        int o;
        Logger log = LoggerFactory.getLogger(GazetteerIndexer.class);

        while ((o = opts.getopt()) != -1) {
            switch (o) {

            case 'u':
                url = opts.getOptarg();

                break;
            case 's':
                schema = opts.getOptarg();

                break;
            case 'i':
                includeCat = opts.getOptarg();

                break;
            case 'c':
                ccList = opts.getOptarg();

                break;
            case 't':
                catField = opts.getOptarg();
                break;
            case 'f':
                file = opts.getOptarg();
                break;
            case 'h':
            default:
                usage();
                System.exit(-1);
                break;

            }
        }

        GazetteerIndexer indexer = new GazetteerIndexer(url);
        indexer.init(includeCat, ccList, catField);
        try {
            indexer.index(new File(file), schema, "\t");
        } catch (SolrServerException e) {
            log.error("Server Error", e);
        } catch (IOException e) {
            log.error("Input Data Error", e);
        }
    }

    public static void usage() {
        System.out.println("nothuibng herae;kd");
    }

    public GazetteerIndexer(String url) {
        // HttpClientBuilder http = HttpClientBuilder.create();
        client = new HttpSolrClient.Builder().withBaseSolrUrl(url).build();
    }

    private SolrClient client = null;
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
    private Pattern splitter = Pattern.compile("\t");

    private String[] sourceColumn = null;
    private String[] schemaColumn = null;
    private Map<String, Integer> schemaMap = new HashMap<>();

    /**
     * Initialize various filter parameters.
     *
     * @param f
     * @param schema
     * @param delim
     * @throws SolrServerException if solr I/O error occurs.
     * @throws ConfigException     bad data in cells
     */
    public void index(File f, String schema, String delim) throws SolrServerException, IOException {

        LineIterator iter = null;
        schemaColumn = schema.split(",");
        try {
            iter = FileUtils.lineIterator(f, "UTF-8");

            /*
             * Get column headings.
             */
            sourceColumn = splitter.split(iter.nextLine());
            int x = 0;
            for (String c : sourceColumn) {
                schemaMap.put(c, x);
                ++x;
            }

            /*
             * Continue with data parsing.
             */
            List<SolrInputDocument> docs = new ArrayList<>();
            while (iter.hasNext()) {
                String gaz = iter.nextLine();
                SolrInputDocument doc = mapGazetteerEntry(splitter.split(gaz));
                if (doc != null) {
                    // Send to Solr

                    docs.add(doc);
                    if (docs.size() % 1000 == 0) {
                        client.add(docs);
                        client.commit();
                        docs.clear();
                    }
                }
            }
            if (docs.size() > 0) {
                client.add(docs);
                client.commit();
                docs.clear();
            }

            client.close();
            iter.close();
        } catch (Exception someErr) {
            if (iter != null) {
                iter.close();
            }
        }
    }

    protected Set<String> excludedTerms = new HashSet<>();

    public void finish() throws IOException {
        logger.info("Terms marked search_only: {}", excludedTerms);
    }

    /**
     * This mapping rarely ever changes.
     */
    private static final Map<String, String> nameTypes = new HashMap<>();
    static {
        nameTypes.put("name", "N");
        nameTypes.put("abbrev", "A");
        nameTypes.put("code", "A"); // TODO: This mapping should be preserved and distinct.
    }

    /**
     * Adding a gazetteer entry involves looking at a few fields -- we keep it
     * if its values match the desired "include category" -- if we keep it, we
     * filter it and mark "search_only" if needed. -- finally, ensure geo =
     * lat,lon format
     *
     * @throws ConfigException if name type in row is bad: only abbrev and name
     *                         are known.
     */

    private SolrInputDocument mapGazetteerEntry(String[] row) throws ConfigException {
        Map<String, String> raw = parseSchema(row);

        ++rowCount;

        if (rowCount % 100000 == 0) {
            logger.info("GazURP ## Row {}; Excluded:{}", rowCount, excludedTerms.size());
        }

        String cc = raw.get("cc");
        String fips = raw.get("FIPS_cc");
        String nm = raw.get("name");
        String cat = raw.get(catField);

        if (includeCountrySet != null) {
            if (!includeCountrySet.contains(cc)) {
                logger.debug("Filtered out CC={}", cc);
                return null;
            }
        }

        /*
         * See solrconfig for documentation on gazetteer filtering
         * =======================================================
         */
        if (!includeAll && includeCategorySet != null) {
            if (cat == null) {
                cat = "general";
            }
            if (!includeCategorySet.contains(cat)) {
                logger.debug("GazURP ##: Exclude {} {}", cat, nm);
                return null;
            }
        }

        /* Populate document */
        SolrInputDocument doc = new SolrInputDocument();
        for (String k : raw.keySet()) {
            doc.addField(k, raw.get(k));
        }

        String nt = raw.get("name_type");
        boolean isName = true;
        if (nt != null) {
            nt = nt.toLowerCase();
            isName = nt.equals("name");
            if (!nameTypes.containsKey(nt)) {
                throw new ConfigException("Data ingested is has unknown name_type = " + nt);
            }
            doc.setField("name_type", nameTypes.get(nt));
        }

        /**
         * Cleanup scripts.
         */
        String nameScript = raw.get("script");

        if (StringUtils.isNotBlank(nameScript)) {
            doc.removeField("script");
            List<String> nameScripts = TextUtils.string2list(TextUtils.removeAny(nameScript, "[]"), ",");
            logger.debug("Scripts = {}", nameScripts);
            for (String scr : nameScripts) {
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
         * Trivially short ASCII names are not good for tagging. But do not mark
         * codes as search only.
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
         * Pattern: Short word followed by digit. XXXX NNN Approximately one
         * word of text Ignore things that are not major places - adm or ppl
         */
        if (!search_only && nm2.length() <= 8) {
            search_only = ignoreShortAlphanumeric(nm2, SolrUtil.getString(doc, "feat_class"));
        }

        /*
         * For relatively short terms that may also be stopterms, first convert
         * to non-diacritic form, then lower case result. If result is a stop
         * term or exclusion term then it should be tagged search_only
         */
        if (!search_only && nm.length() < 15) {
            String nameNonDiacrtic = TextUtils.replaceDiacritics(nm).toLowerCase();
            nameNonDiacrtic = TextUtils.replaceAny(nameNonDiacrtic, "‘’-", " ").trim();
            if (termFilter.filterOut(nameNonDiacrtic)) {
                search_only = true;
                logger.debug("GazURP ## Stop word set search only {} ({})", nm, nameNonDiacrtic);
            }
        }

        if (search_only) {
            doc.setField("search_only", "true");
            excludedTerms.add(nm);
        } else {
            doc.removeField("search_only");
        }

        /*
         * End Filtering =======================================================
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

        // Reform id from string into integer value.
        int id = SolrUtil.getInteger(doc, "id");
        doc.setField("id", id);

        setFloat(doc, "id_bias");
        setFloat(doc, "name_bias");

        ++addCount;
        return doc;
    }

    private void setFloat(SolrInputDocument doc, String k) {
        Float fl = SolrUtil.getFloat(doc, k);
        if (!Float.isNaN(fl)) {
            doc.setField(k, fl);
        }
    }

    private Map<String, String> parseSchema(String[] row) {

        HashMap<String, String> map = new HashMap<>();
        for (int x = 0; x < row.length; ++x) {
            if (schemaColumn[x].length() == 0) {
                // Ignored field, eg. A,B,C --> spec to pull a A and C would be
                // "A,,C"
                continue;
            }
            map.put(schemaColumn[x], row[x].trim());
        }
        return map;
    }

    /**
     * NamedList? in Solr. What a horror. Okay, they work, but...
     * items found in solrconfig under the gaz update processor script:
     * include_category -- Tells the update processor which flavors of data to
     * keep category_field -- the field name where a row of data is categorized.
     * countries -- a list of countries to use. stopterms -- a list of terms
     * used to mark rows of data as "search_only"
     */
    public void init(String includeCategory, String countryList, String categoryField) {
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

        List<String> ic = TextUtils.string2list(includeCategory, ",");

        /*
         * Optional: filter entries with a category list
         */
        // String
        if (ic != null) {
            logger.debug(logTag + "Found CAT={}", ic);
            includeCategorySet = new HashSet<String>();
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
        List<String> cc = TextUtils.string2list(countryList, ",");
        if (cc != null) {
            logger.debug("Found CO={}", ic);
            includeCountrySet = new HashSet<>();
            List<String> val = TextUtils.string2list(cc.get(0), ",");
            includeCountrySet.addAll(val);
        } else {
            logger.debug("No country filter found");
        }

        catField = categoryField;

        logger.debug(logTag + " DONE.   CAT={}, CO={}", includeCategorySet, includeCountrySet);
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

    /**
     * Parse off country codes that duplicate information in ADM boundary code.
     * MX19 => '19', is sufficient. In cases where FIPS/ISO codes differ (almost
     * all!), then this is significant.
     * Searchability: use has to know that ADM1 code is using a given standard.
     * e.g., adm1 = 'IZ08', instead of the more flexible, cc='IQ', adm1='08'
     * Hiearchical/Lexical organization: CC.ADM1 is useful to organize data, but
     * without this normalization, you might have 'IQ.IZ08' -- which is not
     * wrong, just confusing. IQ.08 is a little easier to parse.
     * So for now, the given Gazetteer entries are remapped to ISO coding.
     * Recommendation: we standardize on ISO country codes where possible.
     *
     * @param d     the gazetteer solr document.
     * @param field name of an ADMn field, ADM1, ADM2...etc.
     * @param cc    ISO country code
     * @param fips  FIPS country code
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
         * FIPS code; it refers to a different country so find that country code
         * and replace it with ISO if possible.
         */
        if (adm.length() > 2) {
            String cc2 = adm.substring(0, 2);
            String isocode = helper.FIPS2ISO(cc2);
            if (isocode != null) {
                // this is a country.
                String newAdm = String.format("%s.%s", isocode, adm.substring(2));
                d.setField(field, newAdm);
                logger.debug("Metadata reset for {} => {}", adm, newAdm);

            } else {
                logger.debug("Metadata not found for {}", adm);
            }
        }
    }

}
