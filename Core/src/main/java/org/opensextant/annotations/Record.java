package org.opensextant.annotations;
/*
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * Xponents sub-project "DeepEye", NLP methodology
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013-2021 MITRE Corporation
 */


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A record is a representation of the raw original. It records a processing
 * date (aka ingest date),
 * metadata, source ID, record ID, and a payload.
 * Conventions:
 * <ul>
 * <li>Record Identity is very important in the context of a full pipeline. If
 * you can leverage the
 * given identity of data, maintain that consistently.
 * MD5 digest or UUID has been used often to create a compact identifier, for
 * examples. If left
 * null, database systems can assign object IDs, but transactional webservices
 * are not typically
 * responsible for generating missing identifiers. The lesson is that we should
 * not ignore the use
 * of identifiers. For Record processing a record ID, if for nothing else, is
 * practical for
 * debugging and logging.</li>
 * <li>The metadata "attributes" are considered optional, but usually helpful.
 * Record the raw
 * metadata as-is when you can.</li>
 * <li>If metadata attributes can be conditioned or normalized easily do that,
 * e.g., tag data with
 * ISO2 country code, rather than with country name or FIPS code.</li>
 * <li>The proc_date is usually determined at ingest time; It makes a good shard
 * key for balancing
 * the load of records across distributed storage/database.</li>
 * <li>Record "value" vs. "content": content was intended to capture the textual
 * content of files,
 * knowing that trying to store raw binary content quickly leads to performance
 * problems. For
 * file-based sources (file system/folder crawls, web crawls, etc) content would
 * store a compressed
 * UTF-8 encoded byte-array; Record value would be the filepath to the original.
 * However for
 * non-file based records, the use of .value may make more sense to record the
 * most obvious innate
 * value.</li>
 * </ul>
 *
 * @author ubaldino
 */
public class Record extends DeepEyeData {

    /**
     * Source ID
     */
    public String source_id = null;
    /**
     * a processing date/time key that has as much resolution as you need This is a
     * string because the
     * lexical sort is likely easier to manage than using actual date/time field
     * with date/time math.
     */
    public String procdate = null;
    public byte[] content = null;

    /**
     * State flags indicate what state of processing the record is in or what
     * processing has been
     * applied to it.
     */
    public Map<String, Object> state = null;
    public Map<String, Object> tags = null;
    public int stateMask = 0;

    /**
     * Notes are any text messages you wish to attach to a record DeepEye is not
     * responsible for how
     * such a buffer is maintained.
     * Not indexed.
     */
    public String notes = null;

    public Record() {
    }

    public Record(String recid, String sid) {
        this.id = recid;
        this.source_id = sid;
    }

    @Override
    public String toString() {
        String val = this.value +
                " (" +
                this.id +
                " Source:" +
                source_id +
                ")";
        return val;
    }

    private static final Integer DEFAULT_STATE_VAL = 1;

    public void addState(String s) {
        if (state == null) {
            state = new HashMap<>();
        }
        state.put(s, DEFAULT_STATE_VAL);
    }

    public void addState(String s, int v) {
        if (state == null) {
            state = new HashMap<>();
        }
        state.put(s, v);
    }

    public void addStates(Collection<String> s) {
        if (s == null) {
            return;
        }

        if (state == null) {
            state = new HashMap<>();
        }
        for (String st : s) {
            state.put(st, DEFAULT_STATE_VAL);
        }
    }

    public void addStates(Map<String, Object> map) {
        if (map == null) {
            return;
        }

        if (state == null) {
            state = new HashMap<>();
        }
        state.putAll(map);
    }

    /**
     * "tags" are meant to be used at a data set or collection level. I.e, a source
     * may have records
     *
     * @param s tag string
     */
    public void addCollectionTag(String s) {
        if (tags == null) {
            tags = new HashMap<>();
        }
        tags.put(s, DEFAULT_STATE_VAL);
    }

    /**
     * Parses the given "a;b;c;..." format of tags into a Set.
     *
     * @param tlist list of tags
     */
    public void addCollectionTags(Collection<String> tlist) {
        if (tlist == null) {
            return;
        }
        if (tags == null) {
            tags = new HashMap<>();
        }

        for (String t : tlist) {
            tags.put(t, DEFAULT_STATE_VAL);
        }
    }

    /**
     * @param map list of tags
     */
    public void addCollectionTags(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        if (tags == null) {
            tags = new HashMap<>();
        }
        tags.putAll(map);
    }

    @Override
    public Map<String, Object> getMap() {
        Map<String, Object> dbo = new HashMap<>();
        dbo.put("_id", this.id);
        dbo.put("value", this.value);
        dbo.put("source_id", this.source_id);
        if (this.procdate != null) {
            dbo.put("proc_date", this.procdate);
        }

        Map<?, ?> _attrs = this.getAttributes();
        if (isValue(_attrs)) {
            dbo.put("attrs", _attrs);
        }
        if (isValue(state)) {
            dbo.put("state", this.state);
        }
        if (this.content != null) {
            dbo.put("content", this.content);
        }

        if (isValue(tags)) {
            dbo.put("tags", this.tags);
        }

        return dbo;
    }
}
