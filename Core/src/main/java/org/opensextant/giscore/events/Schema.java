/****************************************************************************************
 *  Schema.java
 *
 *  Created: Jan 29, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.events;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.events.SimpleField.Type;

/**
 * Defines a data schema. Data schemata are important because they allow us to
 * transmit information about the structure of the data from the input to the
 * output side, and even to the processing.
 * <p>
 * While this concept comes from KML, it definitely is present in Shapefiles
 * in the form of the header and type information from the dbf file.
 * <p>
 * A Schema element contains one or more SimpleField elements. In the SimpleField,
 * the Schema declares the type and name of the custom field.
 *
 * @author DRAND
 */
public class Schema implements IGISObject {
    private static final long serialVersionUID = 1L;

    // Numeric id, used for GDB XML and to create an initial name
    private final static AtomicInteger ms_idgen = new AtomicInteger();

    @NotNull
    private String name;
    @NotNull
    private URI id;
    private transient int nid;
    private final Map<String, SimpleField> fields = new LinkedHashMap<>();
    private String parent;

    /**
     * Default Ctor
     */
    public Schema() {
        try {
            id = new URI("s_" + ms_idgen.incrementAndGet());
            name = "schema_" + ms_idgen.get();
            nid = ms_idgen.get();
        } catch (URISyntaxException e) {
            // Impossible
        }
    }

    /**
     * @param urn
     * @throws IllegalArgumentException if urn is null
     */
    public Schema(@NotNull URI urn) {
        // note schema ID not really a URI but represents the ID attribute type from [XML 1.0 (Second Edition)]
        // matching the NCName production rules [see Namespaces in XML].
        this();
        id = urn;
    }

    /**
     * @return the name, never null
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Set name for schema. Normalizes name trimming any leading and trailing whitespace.
     *
     * @param name the name to set, non-blank string (never null)
     * @throws IllegalArgumentException if name is null or blank string
     */
    public void setName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException(
                    "name should never be null or empty");
        }
        this.name = name.trim();
    }

    /**
     * @return the id, never null
     */
    @NotNull
    public URI getId() {
        return id;
    }

    /**
     * @param id the id to set
     * @throws IllegalArgumentException if id is null
     */
    public void setId(URI id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "id should never be null");
        }
        this.id = id;
    }

    /**
     * @return the nid, a numeric id used for gdb interactions, ignored in KML
     */
    public int getNid() {
        return nid;
    }

    /**
     * Return parent. Used only in old-style KML 2.0 documents.
     * This should normally be null.
     *
     * @return parent
     */
    // @CheckForNull

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        if (parent != null) {
            parent = parent.trim();
            if (parent.length() == 0) parent = null;
        }
        this.parent = parent;
    }

    /**
     * Add a new field to the schema
     *
     * @param name  the name of the field, never {@code null} or empty
     * @param field the field, never {@code null}
     */
    public void put(String name, SimpleField field) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException(
                    "name should never be null or empty");
        }
        if (field == null) {
            throw new IllegalArgumentException(
                    "field should never be null");
        }
        fields.put(name, field);
    }

    /**
     * Removes named field from the schema
     *
     * @param name the name of the field, never {@code null} or empty
     * @return the previous value associated with <code>name</code>, or
     *         <code>null</code> if there was no mapping for <code>name</code>.
     */
    // @CheckForNull

    public SimpleField remove(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException(
                    "name should never be null or empty");
        }
        return fields.remove(name);
    }

    /**
     * Shortcut that adds a field and uses the name in the field
     *
     * @param field the field, never {@code null}
     */
    public void put(SimpleField field) {
        if (field == null) {
            throw new IllegalArgumentException(
                    "field should never be null");
        }
        put(field.getName(), field);
    }

    /**
     * Get the field description for a given field
     *
     * @param name the name of the field, never {@code null} or empty
     * @return the field data, may be {@code null} if the field is
     *         not found.
     */
    // @CheckForNull

    public SimpleField get(String name) {
        if (name == null || name.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "name should never be null or empty");
        }
        return fields.get(name);
    }

    public void accept(IStreamVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * @return the read-only entry set of fields, never {@code null}.
     */
    @NotNull
    public Collection<Map.Entry<String, SimpleField>> entrySet() {
        return Collections.unmodifiableSet(fields.entrySet());
    }

    /**
     * @return the read-only collection of field names, never {@code null}.
     */
    @NotNull
    public Collection<String> getKeys() {
        return Collections.unmodifiableSet(fields.keySet());
    }

    /**
     * @return the read-only collections of SimpleFields in this Schema,
     *         never {@code null}.
     */
    @NotNull
    public Collection<SimpleField> getFields() {
        return Collections.unmodifiableCollection(fields.values());
    }

    /**
     * @return the name of the geometry field or {@code null} if one
     *         doesn't exist
     */
    // @CheckForNull

    public String getGeometryField() {
        for (SimpleField field : fields.values()) {
            if (field.getType().isGeometry()) return field.getName();
        }
        return null;
    }

    /**
     * @return the contained field that has a geometry, or {@code null}
     *         if there is no such field. Note that if there is, for some reason,
     *         multiples, then the first will be returned.
     */
    // @CheckForNull

    public SimpleField getShapeField() {
        return getFieldOfType(SimpleField.Type.GEOMETRY);
    }

    /**
     * @return the contained field that is the OID, or {@code null}
     *         if there is no such field. Note that if there is, for some reason,
     *         multiples, then the first will be returned.
     */
    // @CheckForNull

    public SimpleField getOidField() {
        return getFieldOfType(SimpleField.Type.OID);
    }

    /**
     * @param type
     * @return
     */
    // @CheckForNull

    private SimpleField getFieldOfType(Type type) {
        for (SimpleField field : fields.values()) {
            if (type.equals(field.getType())) {
                return field;
            }
        }
        return null;
    }

    /* (non-Javadoc)
      * @see java.lang.Object#equals(java.lang.Object)
      */
    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    /* (non-Javadoc)
      * @see java.lang.Object#hashCode()
      */
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /* (non-Javadoc)
      * @see java.lang.Object#toString()
      */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(fields.size() * 80);
        b.append("<Schema name='");
        b.append(getName());
        b.append("' id='");
        b.append(getId());
        b.append("'>\n");
        for (SimpleField field : fields.values()) {
            b.append("  ");
            b.append(field);
            b.append("\n");
        }
        b.append("</Schema>\n");

        return b.toString();
    }

}
