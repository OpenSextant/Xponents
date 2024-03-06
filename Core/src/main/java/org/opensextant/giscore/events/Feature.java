/****************************************************************************************
 *  Feature.java
 *
 *  Created: Jan 26, 2009
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


import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * We've seen the start of a feature set. The start of the feature set has all
 * the available information about the feature. After the start of a feature,
 * there will be zero or more geometry objects seen.
 * 
 * @author DRAND
 */
public class Feature extends Common {
	private static final long serialVersionUID = 1L;

	private StyleSelector style;
    private Geometry geometry;

	/**
     * Constructs a basic Feature that may contain a geometry and a style.
     * In KML this represents a Placemark.
     */
    public Feature() {
        //
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.opensextant.giscore.events.BaseStart#readData(org.opensextant.giscore.utils.
	 * SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		style = (StyleSelector) in.readObject();
		geometry = (Geometry) in.readObject();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.events.BaseStart#writeData(java.io.DataOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeObject(style);
		out.writeObject(geometry);
	}

	/**
	 * @return the geometry
	 */
    // @CheckForNull

	public Geometry getGeometry() {
		return geometry;
	}

	/**
	 * @param geometry
	 *            the geometry to set
	 */
	public void setGeometry(Geometry geometry) {
		this.geometry = geometry;
	}

	/**
	 * Get inline Style or StyleMap for this Feature
	 * @return the Style or StyleMap
	 */
    // @CheckForNull

	public StyleSelector getStyle() {
		return style;
	}

	/**
	 * Set inline style to use with this Feature.
	 * @param style
	 */
	public void setStyle(StyleSelector style) {
		this.style = style;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return IKml.PLACEMARK;
	}

	public void accept(IStreamVisitor visitor) {
		visitor.visit(this);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((geometry == null) ? 0 : geometry.hashCode());
		result = prime * result + ((endTime == null) ? 0 : endTime.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Feature other = (Feature) obj;
		if (geometry == null) {
			return other.geometry == null;
		} else return other.geometry != null &&
				geometry.equals(other.geometry);
	}

	/**
	 * The approximately equals method checks all the fields for equality with
	 * the exception of the geometry.
	 * 
	 * @param other Other feature to compare against
	 * @return true if this and other are approximately equal
	 */
	public boolean approximatelyEquals(Feature other) {
		EqualsBuilder eb = new EqualsBuilder();
		boolean fields = eb.append(description, other.description)
				.append(name, other.name)
				.append(schema, other.schema)
				.append(styleUrl, other.styleUrl)
				.append(endTime, other.endTime)
				.append(startTime, other.startTime)
				.append(style, other.style).isEquals();

		if (!fields)
			return false;

		// Check extended data
		Set<SimpleField> maximalSet = new HashSet<>();
		maximalSet.addAll(extendedData.keySet());
		maximalSet.addAll(other.extendedData.keySet());
		for (SimpleField fieldname : maximalSet) {
			Object val1 = extendedData.get(fieldname);
			Object val2 = other.extendedData.get(fieldname);
			if (val1 != null && val2 != null) {
				if (val1 instanceof Number && val2 instanceof Number) {
					double dv1 = ((Number) val1).doubleValue();
					double dv2 = ((Number) val2).doubleValue();
					return Math.abs(dv1 - dv2) < 1e-5;
				} else {
					return val1.equals(val2);
				}
			} else if (val1 != null || val2 != null) {
				//System.out.println("unequal name="+fieldname.getName() + " items: this=" + val1 + " " + val2);
				// one value is null and other is non-null
				return false;
			}
		}

		// Check geometry for equivalence
		if (geometry == null && other.geometry == null) {
			return true;
		} else if (geometry != null && other.geometry != null) {
			Geodetic2DBounds bb1 = geometry.getBoundingBox();
			Geodetic2DBounds bb2 = other.geometry.getBoundingBox();
			if (bb1 == null) {
				if (bb2 != null)
					return false;
			} else if (!bb1.equals(bb2, 1e-5))
				return false;
			return geometry.getNumPoints() == other.geometry.getNumPoints();
		} else {
			return false;
		}
	}

    /*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(super.toString());
        if (geometry != null) {
		    b.append(" geometry class=[");
		    b.append(geometry.getClass().getName());
            b.append("]\n\t  center=").append(geometry.getCenter());
            b.append('\n');
        }
		if (style != null) {
		    b.append(" style class=");
			b.append(style.getClass().getName());
			if (style.getId() != null)
				b.append(" id=").append(style.getId());
            b.append('\n');
        }
        return b.toString();
    }

}
