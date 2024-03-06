package org.opensextant.annotations;
/*
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * Xponents sub-project "DeepEye", NLP methodology
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013-2021 MITRE Corporation
 */

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * An annotation is at least a typed name/value pair created by something.
 * <p>
 * Creator is called "contributor" because lots of parties can contribute
 * annotations to this pool.
 * <p>
 * Every annotation belongs to some record (ident by rec_id)<br>
 * Every annotation instance has an ID itself.
 * <p>
 * Offset and attributes are optional, as some annotations apply to entire
 * records, rather than data
 * at a certain offset.
 *
 * @author ubaldino
 */
public class Annotation extends DeepEyeData {

    public String rec_id = null;
    public String name = null;
    /**
     * Source ID here is very much optional Currently it is useful for SQLite usage.
     */
    public String source_id = null;
    /** optional offset */
    public long offset = -1;
    /**
     * optional, but recommended length. If offset is set or attrs.offsets are used,
     * then ideally length
     * should be set
     */
    private int len = 0;
    public String contrib = null;

    public Annotation() {
    }

    public Annotation(String aid) {
        id = aid;
    }

    public Annotation(String aid, String rid) {
        id = aid;
        rec_id = rid;
    }

    public Annotation(String aid, String rid, String ctr, String n, String v) {
        id = aid;
        rec_id = rid;
        contrib = ctr;

        name = n;
        value = v;
        if (value != null) {
            // Default length is based on value.
            this.setLength(value.length());
        }
    }

    public String getAttribute(String k) {
        if (attrs == null) {
            return null;
        }
        return attrs.getString(k);
    }

    @Override
    public String toString() {
        return String.format("%s %s (Record=%s)", this.name, this.value, this.rec_id);
    }

    public void setLength(int l) {
        len = l;
    }

    public boolean hasLength() {
        return len > 0;
    }

    /**
     * Compute length, as logic is related to defaults, if value is set or not. etc.
     *
     * @return
     */
    public int getLength() {
        if (len > 0) {
            return len;
        }
        if (value != null) {
            return value.length();
        }
        return -1;
    }

    /**
     * Compute span end offset.
     *
     * @return end offset (long)
     */
    public long getEndOffset() {
        int l = getLength();
        if (l > 0 && offset >= 0) {
            return offset + l;
        }

        return -1;
    }

    /**
     * Add offset information (offset, len, offsets, etc ) into the representation
     * of this annotation.
     * Some annotations may have a more complex view of offset, e.g.,
     * EntityAnnotation overrides this.
     */
    protected void addOffsetTo(Map<String, Object> repr) {
        this.addOffsetAttribute();
        if (this.offset >= 0) {
            repr.put("offset", this.offset);
            if (this.hasLength()) {
                repr.put("len", this.getLength());
            }
        }
    }
    
    public static String OFFSETS_FLD= "offsets";

    private final TreeSet<Integer> offsets = new TreeSet<>();
    private boolean offsetsEncoded = false;

    public void addOffset(int x) {
        offsets.add(x);
        offsetsEncoded = false;
    }

    public Collection<Integer> getOffsets() {
        return offsets;
    }

    /**
     * add annot.offset = x or annot.attrs.offsets = "x1;x2;x3"... a string list,
     * not a JSON obj.
     */
    public void addOffsetAttribute() {
        // offset attr already done.
        if (offsetsEncoded) {
            return;
        }

        if (offsets.size() > 0) {
            if (this.offset >= 0) {
                addOffset((int) this.offset);
            }
            this.newAttributes();
            // Save List<>( 1, 8, 222, 456 ) as "1;8;222;456", Should the format of this
            // optimization change,
            // CommonAnnotationHelper utility should help manage that.
            //
            attrs.put(OFFSETS_FLD, AnnotationHelper.encodeOffsets(offsets));
            offset = -1;
            offsetsEncoded = true;
            return;
        }

        // One value was given.
        //
        if (offset < 0 && offsets.size() == 1) {
            this.offset = offsets.first();
        }
    }

    /**
     * This is provided mainly for testing. The EntityAnnotation class is not
     * intended for reuse e.g.,
     * populate an instance and then reset offsets alone. Use sparingly.
     */
    public void resetOffsets() {
        offsets.clear();
        offsetsEncoded = false;
        if (attrs != null) {
            this.attrs.remove(OFFSETS_FLD);
        }
    }

    /**
     * Generate a key-value representation of the object with its current values.
     *
     * @return Map
     */
    @Override
    public Map<String, Object> getMap() {

        Map<String, Object> dbo = new HashMap<>();
        /*
         * REQUIRED deepeye fields will be filled, even if value is null.
         */
        dbo.put("_id", this.id);
        dbo.put("rec_id", this.rec_id);
        dbo.put("name", this.name);
        dbo.put("value", this.value);
        dbo.put("contrib", this.contrib);

        /*
         * OPTIONAL fields attrs, source_id, etc. will appear if filled in.
         */
        addOffsetTo(dbo);

        /*
         * Check for NULL all the time.
         */
        Map<?, ?> _attrs = this.getAttributes();
        if (_attrs != null) {
            dbo.put("attrs", _attrs);
        }

        if (isNotBlank(this.source_id)) {
            dbo.put("source_id", this.source_id);
        }

        return dbo;

    }

}
