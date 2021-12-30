package org.opensextant.annotations;
/*
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * Xponents sub-project "DeepEye", NLP methodology
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013-2021 MITRE Corporation
 */


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jodd.json.JsonArray;
import jodd.json.JsonObject;

/**
 * A base class for Record, Annotation and other structures. Common fields
 * include: id, value, and
 * attributes, which may be empty.
 *
 * @author ubaldino
 */
public abstract class DeepEyeData {

    /** A base class to keep all data organized */
    public String id = null;
    public JsonObject attrs = null;
    public String value = null;

    public void addAttribute(String k, Object v) {
        if (attrs == null) {
            this.newAttributes();
        }
        attrs.put(k, v);
    }

    public void newAttributes() {
        if (this.attrs == null) {
            this.attrs = new JsonObject();
        }
    }

    /**
     * Converts internal JSON store to a key/value map.
     */
    public Map<String, Object> getAttributes() {

        if (attrs == null) {
            return null;
        }
        if (attrs.isEmpty()) {
            return null;
        }

        return map(attrs);
    }

    public boolean isValue(Map<?, ?> v) {
        if (v == null) {
            return false;
        }
        return !v.isEmpty();
    }

    public boolean isValue(Collection<?> v) {
        if (v == null) {
            return false;
        }
        return !v.isEmpty();
    }

    /**
     * utility -- get list from jsonarray.
     *
     * @param arr JSON array
     * @return
     */
    public static List<Object> list(JsonArray arr) {
        if (arr != null) {
            return arr.list();
        }
        return null;
    }

    public static List<String> list(JsonObject arr) {
        if (arr != null) {
            ArrayList<String> keys = new ArrayList<>();
            keys.addAll(arr.fieldNames());
            return keys;
        }
        return null;
    }

    /**
     * Convert an array to a trivial map, [i1, i2, i3,...] ==&gt; { i1:"1", i2:"1",
     * ...} UNUSED.
     *
     * @param obj JSON array
     * @return map representation of array
     */
    public static Map<String, Object> asMap(JsonArray obj) {
        if (obj != null) {
            Map<String, Object> map = new HashMap<>();
            for (Object o : obj) {
                map.put(o.toString(), "1");
            }
            return map;
        }
        return null;
    }

    public static Map<String, Object> map(JsonObject obj) {
        if (obj != null) {
            return obj.map();
        }
        return null;
    }

    public Collection<String> getAttributeNames() {
        if (attrs == null) {
            return null;
        }
        return attrs.fieldNames();
    }

    public abstract Map<String, Object> getMap();
}
