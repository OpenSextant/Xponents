package org.opensextant.util;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.DateUtil;

/**
 * Utility functions better suited in their own class.
 *
 * @author ubaldino
 *
 */
public class SolrUtil {

    /**
     *
     * @param d
     * @param f
     * @return  a list of strings for this field from that document.;  Or null if none found.
     */
    public static List<String> getStrings(SolrDocument d, String f) {
        List<String> vals = new LinkedList<String>();

        Collection<Object> objlist = d.getFieldValues(f);
        if (objlist == null) {
            return null;
        }

        for (Object o : objlist) {
            if (o instanceof String) {
                vals.add((String) o);
            } else {
                vals.add(o.toString());
            }
        }
        return vals;
    }

    /**
     * Get an integer from a record
     */
    public static int getInteger(SolrDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return 0;
        }

        if (obj instanceof Integer) {
            return ((Integer) obj).intValue();
        } else {
            Integer v = Integer.parseInt(obj.toString());
            return v.intValue();
        }
    }

    /**
     * Get a long from a record
     */
    public static long getLong(SolrDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return 0;
        }

        if (obj instanceof Long) {
            return ((Long) obj).longValue();
        } else {
            return new Long(obj.toString()).longValue();
        }
    }

    /**
     * Get a floating point object from a record
     */
    public static Float getFloat(SolrDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return 0F;
        } else {
            return (Float) obj;
        }
    }

    /**
     * Get a Date object from a record
     *
     * @throws java.text.ParseException
     */
    public static Date getDate(SolrDocument d, String f)
            throws java.text.ParseException {
        if (d == null || f == null) {
            return null;
        }
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return null;
        }
        if (obj instanceof Date) {
            return (Date) obj;
        } else if (obj instanceof String) {
            return DateUtil.parseDate((String) obj);
        }
        return null;
    }

    /**
     *
     */
    public static char getChar(SolrDocument solrDoc, String name) {
        String result = getString(solrDoc, name);
        if (result == null) {
            return 0;
        }
        if (result.isEmpty()) {
            return 0;
        }
        return result.charAt(0);
    }


    /**
     * Get a String object from a record on input.
     * @param solrDoc solr Input document
     */
    public static String getString(SolrInputDocument solrDoc, String name) {
        Object result = solrDoc.getFieldValue(name);

        if (result==null || StringUtils.isBlank((String)result)) {
            return null;
        }
        return result.toString();
    }

    /**
     * Get a String object from a record
     */
    public static String getString(SolrDocument solrDoc, String name) {
        Object result = solrDoc.getFirstValue(name);
        if (result==null || StringUtils.isBlank((String)result)) {
            return null;
        }
        return result.toString();
    }

    /**
     *
     * Get a double from a record
     */
    public static double getDouble(SolrDocument solrDoc, String name) {
        Object result = solrDoc.getFirstValue(name);
        if (result == null) {
            throw new IllegalStateException("Blank: " + name + " in " + solrDoc);
        }
        if (result instanceof Number) {
            Number number = (Number) result;
            return number.doubleValue();
        } else {
            return Double.parseDouble(result.toString());
        }
    }

    /**
     * Parse XY pair stored in Solr Spatial4J record. No validation is done.
     *
     * @return XY double array, [lat, lon]
     */
    public static double[] getCoordinate(SolrDocument solrDoc, String field) {
        String xy = (String) solrDoc.getFirstValue(field);
        if (xy == null) {
            throw new IllegalStateException("Blank: " + field + " in "
                    + solrDoc);
        }

        final double[] xyPair = { 0.0, 0.0 };
        String[] lat_lon = xy.split(",", 2);
        xyPair[0] = Double.parseDouble(lat_lon[0]);
        xyPair[1] = Double.parseDouble(lat_lon[1]);

        return xyPair;
    }

    /**
     * Parse XY pair stored in Solr Spatial4J record. No validation is done.
     *
     * @return XY double array, [lat, lon]
     */
    public static double[] getCoordinate(String xy) {

        final double[] xyPair = { 0.0, 0.0 };
        String[] lat_lon = xy.split(",", 2);
        xyPair[0] = Double.parseDouble(lat_lon[0]);
        xyPair[1] = Double.parseDouble(lat_lon[1]);

        return xyPair;
    }
}
