package org.opensextant.giscore.output;


import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.Geometry;

/**
 * A key used to look up a specific temp file. 
 * 
 * @author DRAND
 */
public class FeatureKey {
	private final Schema schema;
	// @CheckForNull
	private final String path;
    // @CheckForNull
	private String styleRef;
	// @CheckForNull
	private final Class<? extends Geometry> geoclass;
	private final Class<? extends Row> featureClass;
	
	public FeatureKey(Schema schema, String path, Class<? extends Geometry> geoclass, 
			Class<? extends Row> featureClass) {
		if (schema == null) {
			throw new IllegalArgumentException(
					"schema should never be null");
		}
		if (featureClass == null) {
			throw new IllegalArgumentException(
					"featureClass should never be null");
		}
		this.featureClass = featureClass;
		this.schema = schema;
		this.path = path;
		this.geoclass = geoclass;
	}

	/**
	 * @return the schema
	 */
    @NotNull
	public Schema getSchema() {
		return schema;
	}

	/**
	 * @return the geoclass
	 */
    // @CheckForNull

	public Class<? extends Geometry> getGeoclass() {
		return geoclass;
	}
	
	/**
	 * @return the featureClass
	 */
    @NotNull
	public Class<? extends Row> getFeatureClass() {
		return featureClass;
	}

	/**
	 * @return the path
	 */
    // @CheckForNull

	public String getPath() {
		return path;
	}

    // @CheckForNull

	public String getStyleRef() {
		return styleRef;
	}

	public void setStyleRef(String styleRef) {
		this.styleRef = styleRef;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this,
				ToStringStyle.MULTI_LINE_STYLE);
	}
}