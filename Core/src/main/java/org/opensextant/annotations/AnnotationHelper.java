package org.opensextant.annotations;
/*
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * Xponents sub-project "DeepEye", NLP methodology
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013-2021 MITRE Corporation
 */


import static org.opensextant.util.GeodeticUtility.isValidNonZeroCoordinate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opensextant.data.Country;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.Taxon;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jodd.json.JsonObject;

/**
 * <pre>
 * Basis for this optional helper class was three or four different projects using DeepEye as
 * a model for persisting annotations from the typical Named Entity and Geo/Time extraction work.
 *
 * Common annotation practices include:
 * - All annotations should have both their own ID as well as a Record ID;  this involves tracking unique annots for a given doc.
 *   The intent of DeepEye identifiers is that they are unique within a system or a data set, not necessarily UUID:
 *     - Record IDs should be related to their source identifier
 *     - Annotation IDs should be deterministic based on metadata tuple: MD5( name + value + contrib + rec_id ) or a similar predictable, reproducible hash
 * - Caching an consolidating repetitive annotations. Example:  seeing 'U.S.' 100 times in a
 *   single document as a "GPE" or as a "geo" can be overwhelming.  Is it 100 individual annots,
 *   or 1 annotation for GPE and 1 annotation for the geo.  Each annotation tracks as many offsets as it needs.
 *   As individual instances in the document vary in attributes or other metadata, then those
 *   should be considered unique annotations.
 * - Allow common doc-level annotations, such as a topic tag, even when there is no offset or text span in question. "Span-less" annotations.
 *
 * Advice for what to persist to a DeepEye database:
 * - its costly or complex to compute
 * - its helpful to aggregate many raw extracted values to review over a large data set
 *
 * Consider not storing:
 * - annotations that are trivial to compute at runtime, e.g., indexing a derived metadata tag,
 *   such as converting 'US' to 'United States'
 * - NLP artifacts such as tokens like pronouns or other parts of speech.  These might be
 * - Filtered values  -- if you are filtering out certain data in all your analyses or downstream
 *   operations, consider filtering out such things before you store them blindly.
 * </pre>
 *
 * SEE ALSO: Xponents SDK class org.opensextant.output.Transform: this utility
 * class offers more ideas on standard JSON representations for REST.
 * whereas this utility is aimed at a more reliable pure representation of the
 * match data for storing/retrieving from a data store.
 *
 * @author ubaldino
 */
public class AnnotationHelper {

    /** Start of data quality logging */
    private static final Logger logger = LoggerFactory.getLogger(AnnotationHelper.class);

    /**
     * Given encoded annotations from db, decode them and yield a flattened set of
     * annotations, e.g.,
     * for use with MAT
     *
     * @param codedAnnots the coded annots
     * @return the list
     */
    public static List<Annotation> decodeAnnotations(List<Annotation> codedAnnots) {
        List<Annotation> annots = new ArrayList<>();
        for (Annotation a : codedAnnots) {
            if (a.offset >= 0) {
                annots.add(a);
                continue;
            }
            // If no attrs, just add Annotation.
            if (a.attrs == null) {
                annots.add(a);
            } else if (a.attrs.containsKey(Annotation.OFFSETS_FLD)) {
                // ATTRS not null, if 'offsets' is used, decoded it. This annoation appears at
                // those offsets.
                annots.addAll(decodeOffsets(a, a.attrs.getString(Annotation.OFFSETS_FLD)));
            } else {
                // No attributes, no offset, etc.
                // This is just an ordinary annotation for the document. E.g., classifier label.
                annots.add(a);
            }
        }
        return annots;
    }

    /**
     * Gets the first offset.
     *
     * @param a the a
     * @return the first offset
     */
    public static long getFirstOffset(Annotation a) {
        if (a.offset >= 0) {
            return a.offset;
        }

        if (a.attrs == null || a.attrs.containsKey(Annotation.OFFSETS_FLD)) {
            return -1;
        }
        String val = a.attrs.getString(Annotation.OFFSETS_FLD);
        if (StringUtils.isBlank(val)) {
            return -1;
        }

        List<Integer> offsets = decodeOffsets(val);
        if (offsets.isEmpty()) {
            return -1;
        }
        return offsets.get(0);
    }

    /** The Constant NUM_SEP. */
    public static final String NUM_SEP = ";";

    /**
     * Encode offsets.
     *
     * @param offsets the offsets
     * @return the string
     */
    public static String encodeOffsets(Collection<Integer> offsets) {
        return StringUtils.join(offsets, NUM_SEP);
    }

    /**
     * Take a list of numbers and convert to Integer list
     * "1;5;89;777" =&gt; List&lt;&gt; [ 1, 5, 89, 777 ].
     *
     * @param list the list
     * @return the list
     */
    public static List<Integer> decodeOffsets(String list) {
        List<Integer> numarray = new ArrayList<>();
        String[] nums = list.split(NUM_SEP);
        for (String n : nums) {
            numarray.add(Integer.parseInt(n));
        }
        return numarray;
    }

    /**
     * Generate annotations in a linear fashion. Given the optimized Annotation, A,
     * create duplicate
     * annotations, each with an offset from the list of offsets.
     *
     * @param meta       the meta
     * @param offsetList the offset list
     * @return the list
     */
    public static List<Annotation> decodeOffsets(Annotation meta, String offsetList) {
        List<Annotation> annots = new ArrayList<>();
        List<Integer> _offsets = decodeOffsets(offsetList);

        // Shallow hashmap, not complex.
        JsonObject copyAttrs = new JsonObject();
        copyAttrs.mergeIn(meta.attrs);
        copyAttrs.remove(Annotation.OFFSETS_FLD);

        for (Integer x : _offsets) {
            // COPY annotation "meta" to "a"
            //
            Annotation a = new Annotation(meta.id);
            a.contrib = meta.contrib;
            a.value = meta.value;
            a.name = meta.name;
            a.rec_id = meta.rec_id;
            a.source_id = meta.source_id;

            // Set offset here.
            a.offset = x;
            a.attrs = copyAttrs;

            annots.add(a);
        }

        return annots;
    }

    /**
     * New, required format for an annotation ID: Md5 hash made up of:
     *
     * <pre>
     *    rec_id + contributor + type + value
     *
     *    Distinct entities from a single contributor for a single document will be recorded (and overwritten over time).
     *    Reprocessing data will overwrite a new value.
     *
     *     Doc abc has a NAMEX = 'the diplomat', provided by xyz extractor.
     *
     *     key is MD5( 'abc;xyz;NAMEX;the diplomat' )
     *     Multiple occurrences of the same value in the same document must be recorded as "atts.offsets" = [n1,n2,n3...] offsets
     * </pre>
     *
     * @param rec_id  the rec_id
     * @param contrib the contrib
     * @param atype   the atype
     * @param val     the val
     * @return the annotation id
     */
    public static String getAnnotationId(String rec_id, String contrib, String atype, String val) {
        try {
            return TextUtils.text_id(String.format("%s;%s;%s;%s", rec_id, contrib, atype, val));
        } catch (Exception err) {
            // This is an ODD and rare error to have with an MD5 or other digest.
            logger.error("Obscure hashing error R={}", rec_id,  err);
        }
        return null;
    }

    /**
     * Reset() clears the internal cache. Ideally, you hit reset on each new Record
     * in a loop.
     */
    public void reset() {
        deepEyeEntities.clear();
    }

    /**
     * optimization: keep list of annotations stored in database to a minimum.
     * deepeEyeEntities is a
     * hash of Annotation organized by distinct name/value pairs. The offsets of the
     * entity values is
     * the only thing that appears to change.
     */
    private final Map<String, Annotation> deepEyeEntities = new HashMap<>();

    /**
     * Gets the cached annotations, unordered.
     *
     * @return the cached annotations
     */
    public Collection<Annotation> getCachedAnnotations() {
        if (deepEyeEntities.isEmpty()) {
            return null;
        }
        return deepEyeEntities.values();
    }

    /**
     * Cache entity annotations, accumulating unique offsets for a name/value pair.
     *
     * @param contrib the contrib
     * @param etype   the etype
     * @param value   the value
     * @param start   the start
     * @param docid   the docid
     * @return the entity annotation
     */
    public Annotation cacheAnnotation(String contrib, String etype, String value, int start, String docid) {
        String key = cacheKey(etype, value);
        Annotation ea = deepEyeEntities.get(key);
        if (ea == null) {
            ea = createAnnotation(contrib, etype, value, -1, docid);
            deepEyeEntities.put(key, ea);
        }
        ea.addOffset(start);
        return ea;
    }

    /**
     * Cache an annotation.
     *
     * @param ea  the ea
     * @param key the key
     */
    public void cacheAnnotation(Annotation ea, String key) {
        deepEyeEntities.put(key, ea);
    }

    /**
     * Cache annotation.
     *
     * @param ea your annotation
     */
    public void cacheAnnotation(Annotation ea) {
        deepEyeEntities.put(cacheKey(ea.name, ea.value), ea);
    }

    /**
     * Cache entity annotation - in Memory; Note, the actual ID or key in database
     * is usually composed
     * of name+value+contrib. NOTE: these are normalized case-insensitive values.
     * NOTE: If annotation
     * already exists, then all we do is add the start offset to the existing entry.
     * Name and value must not be null.
     *
     * @param ea    your annotation
     * @param start offset into your doc.
     * @throws NullPointerException if name and value are not set on Annotation.
     */
    public void cacheAnnotation(Annotation ea, int start) {
        String key = cacheKey(ea.name, ea.value.toLowerCase());
        Annotation eaCurr = deepEyeEntities.get(key);

        /*
         * Only additional metadata here is offset.
         */
        if (eaCurr != null) {
            eaCurr.addOffset(start);
        } else {
            deepEyeEntities.put(key, ea);
        }
    }

    /**
     * Checks for cached annotation.
     *
     * @param etype the etype
     * @param value the value
     * @return true, if successful
     */
    public boolean hasCachedAnnotation(String etype, String value) {
        String key = cacheKey(etype, value);
        return (deepEyeEntities.get(key) != null);
    }

    /**
     * Cache Key helps distinguish a distinct entity/value pair. Caller could decide
     * if LOC="Boston" and
     * LOC="BOSTON" are different or the same by passing in the lower case value.
     *
     * @param n the n
     * @param v the v
     * @return the string
     */
    private String cacheKey(String n, String v) {
        return String.format("%s;%s", n, v);
    }

    /**
     * Careful -- no guarntee that two entity annotations could share the same
     * type/value
     * unintentionally.
     * e.g., if "tx" type annot implies a taxon from one contrib and "tx" means a
     * transaction from
     * another, then you the developer should choose more distinct entity type
     * codes.
     *
     * @param etype entity type
     * @param value value
     * @return the cached annotation
     */
    public Annotation getCachedAnnotation(String etype, String value) {
        String key = cacheKey(etype, value);
        return deepEyeEntities.get(key);
    }

    /**
     * Cache taxon entity annotation.
     *
     * @param contrib contributor ID
     * @param taxon   taxon obj
     * @param value   string value
     * @param offset  offset
     * @param docid   docid
     * @return the entity annotation
     */
    protected Annotation cacheTaxonAnnotation(String contrib, Taxon taxon, String value, int offset, String docid) {
        String key = String.format("%s;%s", taxon.name, value);
        Annotation ea = deepEyeEntities.get(key);
        if (ea == null) {
            ea = createTaxonAnnotation(contrib, "tx", value, -1, docid, taxon);
            deepEyeEntities.put(key, ea);
        }
        ea.addOffset(offset);
        return ea;
    }

    /**
     * Creates a standard named entity annotation.
     *
     * @param contrib contributor ID
     * @param type    annotation type/ID
     * @param val     string value
     * @param offset  offset
     * @param docid   docid
     * @return the annotation
     */
    public static Annotation createAnnotation(String contrib, String type, String val, int offset, String docid) {
        String _id = getAnnotationId(docid, contrib, type, val);
        Annotation annot = new Annotation(_id, docid, contrib, type, val);
        annot.offset = offset;
        annot.value = val;
        return annot;
    }

    /**
     * @param contrib
     * @param type
     * @param val
     * @param offset
     * @param len
     * @param docid
     * @return
     */
    public static Annotation createAnnotation(String contrib, String type, String val, int offset, int len,
            String docid) {
        Annotation ea = createAnnotation(contrib, type, val, offset, docid);
        ea.setLength(len);
        return ea;
    }

    /**
     * Create an annotation for a Taxon node that has a found value, val, in
     * document, docid at offset.
     * Taxon match has a type and a contributor, usually the tagger or extractor
     * that processed the
     * document.
     *
     * @param contrib the contrib
     * @param type    the type
     * @param val     the val
     * @param offset  the offset
     * @param docid   the docid
     * @param n       the n
     * @return the entity annotation
     */
    public static Annotation createTaxonAnnotation(String contrib, String type, String val, int offset, String docid,
            Taxon n) {
        String _id = getAnnotationId(docid, contrib, type, val);
        Annotation annot = new Annotation(_id, docid, contrib, type, val);
        annot.newAttributes();
        annot.attrs.put("name", n.name);
        annot.attrs.put("cat", n.catalog);

        annot.offset = offset;
        return annot;
    }

    /**
     * Recreates a Taxon from a stored annotation. Required fields:
     * a.attrs[name] -- taxon node name a.attrs[cat] -- catalog a.name -- Not used
     * here. a.value -- the
     * value of the matched text.
     *
     * @param a the a
     * @return the taxon
     */
    public static Taxon createTaxon(Annotation a) {
        if (a.attrs == null) {
            return null;
        }
        Taxon t = new Taxon();
        t.catalog = a.attrs.getString("cat");
        t.name = a.attrs.getString("name");

        return t;
    }

    /**
     * Tracking a country name match of some sort. You know this is a country,
     * eh,... so please enrich
     * with the country code here. We know you can always find out the country code
     * later from a given
     * country name/match, however this may be context specific.
     * Georgia / GE -- putting the country code here gives more confidence that you
     * found Georgia, the
     * country and not the US state You might have other means for deriving the
     * country code for a given
     * value, e.g.,
     * for example you found "GOI" a geopolitical entity you infer to be Govt. of
     * India, so you emit
     * "IN" as the country code.
     * create( xxx, 'GPE', 'GOI', ..., 'IN' )
     *
     * @param contrib      the contrib
     * @param type         the type
     * @param val          the val
     * @param offset       the offset
     * @param docid        the docid
     * @param country_code the country_code
     * @return the entity annotation
     */
    public static Annotation createCountryAnnotation(String contrib, String type, String val, int offset, String docid,
            String country_code) {
        Annotation annot = createAnnotation(contrib, type, val, offset, docid);
        if (country_code != null) {
            annot.newAttributes();
            annot.attrs.put("cc", country_code);
            // Normalize the country mention, if possible.
        }

        return annot;
    }

    /**
     * Returns an instance of a Country object using annotation value as country
     * name, and attr[cc]
     * optionally as code. This does not reproduce a full Country object as if
     * queried from
     *
     * @see org.opensextant.util.GeonamesUtility#getCountry(String)
     * @param a annot
     * @return the country
     */
    public static Country createCountry(Annotation a) {
        if (a.attrs == null) {
            return null;
        }

        return new Country(a.attrs.getString("cc"), a.value);
    }

    /**
     * Encode geocoding annotations to be saved. This schema follows from
     * EH/GLINT/Glare, etc.
     *
     * @param contrib the contrib
     * @param type    the type
     * @param val     the val
     * @param offset  the offset
     * @param docid   the docid
     * @param g       the g
     * @return the entity annotation
     */
    public static Annotation createGeocodingAnnotation(String contrib, String type, String val, int offset,
            String docid, Geocoding g) {

        Annotation annot = createAnnotation(contrib, type, val, offset, docid);
        annot.newAttributes();

        if (g.getCountryCode() != null) {
            annot.attrs.put("cc", g.getCountryCode());
        }
        if (g.getAdmin1() != null) {
            annot.attrs.put("adm1", g.getAdmin1());
        }
        if (g.getFeatureClass() != null) {
            annot.attrs.put("feat_class", g.getFeatureClass());
            annot.attrs.put("feat_code", g.getFeatureCode());
        }
        if (isValidNonZeroCoordinate(g.getLatitude(), g.getLongitude())) {
            annot.attrs.put("prec", g.getPrecision());
            annot.attrs.put("lat", g.getLatitude());
            annot.attrs.put("lon", g.getLongitude());
        }
        if (g.getMethod() != null) {
            annot.attrs.put("method", g.getMethod());
        }
        if (g.getAdminName() != null) {
            annot.attrs.put("adm1_name", g.getAdminName());
        } else if (g.getAdmin1Name() != null) {
            annot.attrs.put("adm1_name", g.getAdmin1Name());
        }
        return annot;
    }

    private static double parseDouble(Object o) {
        if (o instanceof String) {
            return Double.parseDouble(o.toString());
        }
        if (o instanceof Double) {
            return (double) o;
        }
        throw new NumberFormatException("Unknown object" + o);
    }

    /**
     * Decode: Geocoding See OpenSextant Geocoding interface. Here required
     * annotation fields are:
     * lat, lon, prec cc, adm1, place feat_class, feat_code method
     *
     * @param a the a
     * @return the geocoded data
     */
    public static Place createGeocoding(Annotation a) {
        if (a.attrs == null) {
            return null;
        }
        //
        Place geo = new Place(a.id, a.value);

        if (a.attrs.containsKey("lat")) {
            Object olat = a.attrs.getValue("lat");
            Object olon = a.attrs.getValue("lon");

            double lat = parseDouble(olat);
            double lon = parseDouble(olon);
            if (isValidNonZeroCoordinate(lat, lon)) {
                geo.setLatitude(lat);
                geo.setLongitude(lon);
                int prec = a.attrs.getInteger("prec", -1);
                if (prec < 0) {
                    prec = a.attrs.getInteger("precision", -1);
                }
                geo.setPrecision(prec);
                if (geo.getPrecision() <= 0) {
                    logger.info("Location should have precision: {}", a.attrs);
                }
            } else {
                logger.info("GEOLOCATION warning: 0,0: ID={}, {}", a.rec_id, a.attrs);
            }
        }
        geo.setMethod(a.attrs.getString("method", null));

        geo.setAdmin1(a.attrs.getString("adm1", null));
        geo.setAdmin2(a.attrs.getString("adm2", null));
        geo.setCountryCode(a.attrs.getString("cc", null));
        geo.setFeatureClass(a.attrs.getString("feat_class", null));
        geo.setFeatureCode(a.attrs.getString("feat_code", null));

        // Optional items:
        // geo.setName(a.attrs.optString("place"));
        geo.setAdmin1Name(a.attrs.getString("adm1_name", null));
        geo.setAdmin2Name(a.attrs.getString("adm2_name", null));

        /*
         * Choose finest grained admin name.
         * First ADM2 name then ADM1 name...
         */
        if (geo.getAdmin2Name() != null) {
            geo.setAdminName(geo.getAdmin2Name());
        } else if (geo.getAdmin1Name() != null) {
            geo.setAdminName(geo.getAdmin1Name());
        }

        return geo;
    }

    /** The datefmt. */
    private static final DateTimeFormatter DATEFMT = DateTimeFormat.forPattern("yyyy-MM-dd");

    /**
     * Creates the temporal entity annotation.
     *
     * @param contrib    the contrib
     * @param type       the type
     * @param val        the val
     * @param offset     the offset
     * @param docid      the docid
     * @param d          the d
     * @param resolution the resolution
     * @return the entity annotation
     */
    public static Annotation createTemporalEntityAnnotation(String contrib, String type, String val, int offset,
            String docid, Date d, String resolution) {
        Annotation annot = createAnnotation(contrib, type, val, offset, docid);
        annot.newAttributes();

        annot.attrs.put("datenorm", DATEFMT.print(d.getTime())); // formatted date/time.
        annot.attrs.put("epoch", d.getTime()); // Milliseconds
        annot.attrs.put("res", resolution); // finest resolution of date - Y, M,
        // D, H, m, s; Week (W)

        return annot;
    }

    /**
     * Same createTemporalEntityAnnotation, just with len param.
     *
     * @param contrib
     * @param type
     * @param val
     * @param offset
     * @param len
     * @param docid
     * @param d
     * @param resolution
     * @return
     */
    public static Annotation createTemporalAnnotation(String contrib, String type, String val, int offset, int len,
            String docid, Date d, String resolution) {
        Annotation annot = createTemporalEntityAnnotation(contrib, type, val, offset, docid, d, resolution);
        annot.setLength(len);
        return annot;
    }
}
