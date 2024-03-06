/****************************************************************************************
 *  Common.java
 *
 *  Created: Jan 27, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.events;

// import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic3DBounds;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.UrlRef;
import org.opensextant.giscore.utils.DateTime;
import org.opensextant.giscore.utils.DateTime.DateTimeType;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common abstract superclass for features of various kinds.
 *
 * @author DRAND
 */
public abstract class Common extends Row {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(Common.class);

	protected String name;
	protected String description;
	private String snippet;
	private Boolean visibility;
	protected DateTime startTime;
	protected DateTime endTime;
	protected String styleUrl;
	private TaggedMap viewGroup;
	private TaggedMap region;

	@NotNull
	private List<Element> elements = new ArrayList<>();

	/**
	 * @return the name
	 */
	// @CheckForNull

	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the description
	 */
	// @CheckForNull

	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	public String getSnippet() {
		return snippet;
	}

	public void setSnippet(String snippet) {
		this.snippet = snippet;
	}

	/**
	 * @return the styleUrl
	 */
	// @CheckForNull

	public String getStyleUrl() {
		return styleUrl;
	}

	/**
	 * @param styleUrl the styleUrl to set
	 */
	public void setStyleUrl(String styleUrl) {
		styleUrl = StringUtils.trimToNull(styleUrl);
		// test if url dangling anchor reference (neither relative or absolute URL)
		// not containing '#' then prepend '#' to URL (e.g. blueIcon -> #blueIcon)
		// REVIEW: this might not work with all relative URLs...
		if (styleUrl != null && styleUrl.indexOf('#') == -1 && UrlRef.isIdentifier(styleUrl, true)) {
			log.debug("fix StyleUrl identifier as local reference: {}", styleUrl);
			styleUrl = "#" + styleUrl;
		}
		this.styleUrl = styleUrl;
	}

	/**
	 * @return the startTime
	 */
	// @CheckForNull

	public DateTime getStartDate() {
		// note this exposes the internal representation by returning reference to mutable object
		return startTime;
	}

	/**
	 * @return the startTime as Date
	 * @deprecated Use {@link #getStartDate()}
	 */
	// @CheckForNull

	@Deprecated
	public Date getStartTime() {
		return startTime == null ? null : startTime.toDate();
	}

	/**
	 * @param startTime the startTime to set
	 */
	public void setStartTime(DateTime startTime) {
		this.startTime = startTime;
	}

	/**
	 * @param startTime the startTime to set
	 */
	public void setStartTime(Date startTime) {
		this.startTime = startTime == null ? null : new DateTime(startTime.getTime());
	}

	/**
	 * @return the endTime
	 */
	// @CheckForNull

	public DateTime getEndDate() {
		// note this exposes the internal representation by returning reference to mutable object
		return endTime;
	}

	/**
	 * @return the endTime as Date
	 * @deprecated Use {@link #getEndDate()}
	 */
	// @CheckForNull

	@Deprecated
	public Date getEndTime() {
		return endTime == null ? null : endTime.toDate();
	}

	/**
	 * @param endTime the endTime to set
	 */
	public void setEndTime(DateTime endTime) {
		this.endTime = endTime;
	}

	/**
	 * @param endTime the endTime to set
	 */
	public void setEndTime(Date endTime) {
		this.endTime = endTime == null ? null : new DateTime(endTime.getTime());
	}

	/**
	 * Get ViewGroup TaggedMap and {@code null} if not defined.
	 * <p>
	 * This will represent name-value pairs that define the view and orientation
	 * of this object. Compound elements will have its names flattened or
	 * normalized with any namespace prepended to the name and each name appended
	 * with a slash (/) delimiter as in a XPATH like syntax.
	 * <p>
	 * For KML context a ViewGroup will represent a Camera or LookAt with
	 * name-values pairs for properties such as latitude, longitude, tilt, etc.
	 * Also supported are non-simple name-value pairs such as Google Extensions
	 * (E.g., gx:AltitudeMode, and gx:TimeSpan). Only difference with
	 * {@code gx:TimeStamp} is the prefix is prepended to the name.
	 * {@code gx:TimeSpan} as in following KML example is a compound value
	 * with the {@code begin} child element encoded as <code>gx:TimeSpan/begin</code>
	 * and likewise for the {@code end} element.
	 * <pre>
	 *     &lt;LookAt&gt;
	 *  	&lt;gx:TimeSpan&gt;
	 *  	    &lt;begin&gt;2010-05-28T02:02:09Z&lt;/begin&gt;
	 *  	    &lt;end&gt;2010-05-28T02:02:56Z&lt;/end&gt;
	 *  	&lt;/gx:TimeSpan&gt;
	 *  	&lt;longitude&gt;143.1066665234362&lt;/longitude&gt;
	 *  	&lt;latitude&gt;37.1565775502346&lt;/latitude&gt;
	 *     &lt;/LookAt&gt;
	 * </pre>
	 *
	 * @return TaggedMap or {@code null} if none is defined
	 */
	// @CheckForNull

	public TaggedMap getViewGroup() {
		return viewGroup;
	}

	/**
	 * Set ViewGroup on feature (e.g. Camera or LookAt element)
	 *
	 * @param viewGroup
	 * @see #getViewGroup()
	 */
	public void setViewGroup(TaggedMap viewGroup) {
		this.viewGroup = viewGroup;
	}

	// @CheckForNull

	public TaggedMap getRegion() {
		return region;
	}

	/**
	 * Set Region on feature
	 *
	 * @param region
	 */
	public void setRegion(TaggedMap region) {
		this.region = region;
	}

	/**
	 * Set Region on feature.
	 * Caller should set  <em>minLodPixels</em> on the region TaggedMap instance after
	 * calling this method since this is not defined here and it is a required field.
	 *
	 * @param bbox Bounding box from which to set the region. If bbox Geodetic3DBounds
	 *             instance then minAltitude and maxAltitude will be ignored by default.
	 *             To preserve the min/max altitude of the bbox call {@link #setRegion(Geodetic2DBounds, boolean)}
	 *             with {@code true} value instead.
	 */
	public void setRegion(Geodetic2DBounds bbox) {
		setRegion(bbox, false);
	}

	/**
	 * Set Region on feature.
	 * <p>
	 * Caller should set  <em>minLodPixels</em> on the region TaggedMap instance after
	 * calling this method since this is not defined here and it is a required field.
	 * As defined in KML 2.2 OGC 07-147r2: if <em>minAltitude</em> and <em>maxAltitude</em> are both present,
	 * <em>altitudeMode</em> shall <B>not</B> have a value of {@code clampToGround} so in this case <code>absolute</code>
	 * is assumed if the altitudeMode property is not already set. Caller should therefore explicitly set
	 * <em>altitudeMode</em> if altitude will be included in the region.
	 *
	 * @param bbox		  Bounding box from which to set the region.
	 * @param allowAltitude If set to <em>true</em> and bbox is a <em>Geodetic3DBounds</em>
	 *                      instance then <em>minAltitude</em> and <em>maxAltitude</em> properties will be
	 *                      set accordingly otherwise region will not include altitude.
	 */
	public void setRegion(Geodetic2DBounds bbox, boolean allowAltitude) {
		if (bbox == null) {
			// passing null clears the region: same as passing null to setRegion(TaggedMap)
			region = null;
			return;
		}
		if (region == null) {
			region = new TaggedMap(IKml.LAT_LON_ALT_BOX);
		} else if (!allowAltitude) {
			region.remove(IKml.MIN_ALTITUDE);
			region.remove(IKml.MAX_ALTITUDE);
			// force no altitude on the region
		}
		/*
				  <element name="LatLonAltBox">
				  <complexType>
					<complexContent>
						<sequence>
						  <element ref="kml:north" minOccurs="0"/>
						  <element ref="kml:south" minOccurs="0"/>
						  <element ref="kml:east" minOccurs="0"/>
						  <element ref="kml:west" minOccurs="0"/>
						  <element ref="kml:minAltitude" minOccurs="0"/>
						  <element ref="kml:maxAltitude" minOccurs="0"/>
						  <element ref="kml:altitudeModeGroup" minOccurs="0"/>
						</sequence>
					  </extension>
					</complexContent>

				  <element name="Lod">
				  <complexType>
					<complexContent>
					  <extension base="kml:AbstractObjectType">
						<sequence>
						  <element ref="kml:minLodPixels" minOccurs="0"/>
						  <element ref="kml:maxLodPixels" minOccurs="0"/>
						  <element ref="kml:minFadeExtent" minOccurs="0"/>
						  <element ref="kml:maxFadeExtent" minOccurs="0"/>
						</sequence>
					  </extension>
					</complexContent>
				  </complexType>
				 */
		region.put(IKml.NORTH, String.valueOf(bbox.getNorthLat().inDegrees()));
		region.put(IKml.SOUTH, String.valueOf(bbox.getSouthLat().inDegrees()));
		// +180 is normalized to -180 in the Longitude class
		// handle east/west as special case
		// If east = -180 and west >=0 then Google Earth invalidate the Region and never be active
		// in that case normalize east longitude to +180
		double west = bbox.getWestLon().inDegrees();
		double east = bbox.getEastLon().inDegrees();
		if (west >= 0 && Double.compare(east, -180) == 0) {
			region.put(IKml.EAST, "180");
		} else {
			region.put(IKml.EAST, String.valueOf(east));
		}
		region.put(IKml.WEST, String.valueOf(west));
		if (allowAltitude && bbox instanceof Geodetic3DBounds) {
			Geodetic3DBounds bounds = (Geodetic3DBounds) bbox;
			double minElev = bounds.minElev;
			double maxElev = bounds.maxElev;
			// verify constraints minAltitude is less than or equal to maxAltitude
			if (minElev > maxElev) {
				double temp = minElev;
				minElev = maxElev;
				maxElev = temp;
			}
			region.put(IKml.MIN_ALTITUDE, String.valueOf(minElev));
			region.put(IKml.MAX_ALTITUDE, String.valueOf(maxElev));
			String altitudeMode = region.get(IKml.ALTITUDE_MODE);
			if (StringUtils.isBlank(altitudeMode) || "clampToGround".equalsIgnoreCase(altitudeMode)) {
				// KML 2.2 OGC 07-147r2: if kml:minAltitude and kml:maxAltitude are both present, kml:altitudeMode
				// shall not have a value of clampToGround. Must have non-clampToGround value so assume it's absolute.
				region.put(IKml.ALTITUDE_MODE, "absolute");
			}
		}
	}

	// @CheckForNull

	public Boolean getVisibility() {
		return visibility;
	}

	/**
	 * Specifies whether the feature is "visible" when it is initially loaded
	 *
	 * @param visibility Flag whether feature is visible or not.
	 *                   default value is null (or undefined).
	 */
	public void setVisibility(Boolean visibility) {
		this.visibility = visibility;
	}

	/**
     * Returns true if this object has any foreign XML-namespaced elements
     */
    public boolean hasElements() {
        return !elements.isEmpty();
    }

	/**
     * Collection of foreign XML-namespaced elements for this feature
	 * @return the elements, never null
	 */
	@NotNull
	public List<Element> getElements() {
		return elements;
	}

	/**
     * Find element by name and namespace URI.
     *
     * @param name  target element name
     * @param namespaceUri  target element namespace URI, null if want to find any
     *                      element just by its name
     * @return element if found otherwise null
     */
    public Element findElement(String name, String namespaceUri) {
        if (!elements.isEmpty()) {
            for (Element elt : elements) {
                if (elt.getName().equals(name)) {
                    if (namespaceUri == null || namespaceUri.equals(elt.getNamespaceURI())) {
                        return elt;
                    }
                }
            }
        }
        return null;
    }

	/**
	 * Add single element to list
	 *
	 * @param element
	 */
	public void addElement(Element element) {
		if (element != null)
			elements.add(element);
	}

	/**
	 * Replaces current list with new list of elements
	 *
	 * @param elements the elements to set
	 */
	public void setElements(List<Element> elements) {
		this.elements = elements == null ? new ArrayList<>() : elements;
	}

	/**
	 * Read object from the data stream.
	 *
	 * @param in the input stream, never {@code null}
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException, IllegalAccessException {
		super.readData(in);
		name = in.readString();
		description = in.readString();
		styleUrl = in.readString();
		startTime = readDateTime(in);
		endTime = readDateTime(in);
		viewGroup = (TaggedMap) in.readObject();
		region = (TaggedMap) in.readObject();
		visibility = (Boolean) in.readScalar();
		elements = in.readNonNullObjectCollection();
	}

	/**
	 *
	 * @param in the input stream, never {@code null}
	 * @return DateTime
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if DateTime type code is invalid
	 */
	private static DateTime readDateTime(SimpleObjectInputStream in) throws IOException {
		int typeCode = in.readByte();
		if (typeCode == -1) {
			return null;
		} else {
			DateTimeType type = DateTimeType.valueOf(typeCode);
			long val = in.readLong();
			return new DateTime(val, type);
		}
	}

	/**
	 * Write the object to the data stream
	 *
	 * @param out
	 * @throws IOException if an I/O error occurs
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeString(name);
		out.writeString(description);
		out.writeString(styleUrl);
		if (startTime != null) {
			out.writeByte(startTime.getType().ordinal()); // 0=gYear, ..., 3=dateTime
			out.writeLong(startTime.getTime());
		} else {
			out.writeByte(-1);
		}
		if (endTime != null) {
			out.writeByte(endTime.getType().ordinal());
			out.writeLong(endTime.getTime());
		} else {
			out.writeByte(-1);
		}
		out.writeObject(viewGroup);
		out.writeObject(region);
		out.writeScalar(visibility);
		out.writeObjectCollection(elements);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((description == null) ? 0 : description.hashCode());
		result = prime * result + elements.hashCode(); // elements never null
		result = prime * result + ((endTime == null) ? 0 : endTime.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((region == null) ? 0 : region.hashCode());
		result = prime * result
				+ ((startTime == null) ? 0 : startTime.hashCode());
		result = prime * result
				+ ((styleUrl == null) ? 0 : styleUrl.hashCode());
		result = prime * result
				+ ((viewGroup == null) ? 0 : viewGroup.hashCode());
		result = prime * result
				+ ((visibility == null) ? 0 : visibility.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Common other = (Common) obj;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (endTime == null) {
			if (other.endTime != null)
				return false;
		} else if (!endTime.equals(other.endTime))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (region == null) {
			if (other.region != null)
				return false;
		} else if (!region.equals(other.region))
			return false;
		if (startTime == null) {
			if (other.startTime != null)
				return false;
		} else if (!startTime.equals(other.startTime))
			return false;
		if (styleUrl == null) {
			if (other.styleUrl != null)
				return false;
		} else if (!styleUrl.equals(other.styleUrl))
			return false;
		if (viewGroup == null) {
			if (other.viewGroup != null)
				return false;
		} else if (!viewGroup.equals(other.viewGroup))
			return false;
		if (visibility == null) {
			if (other.visibility != null)
				return false;
		} else if (!visibility.equals(other.visibility))
			return false;
		return elements.equals(other.elements);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(super.toString());
		if (name != null) {
			b.append(" name = ");
			b.append(name);
			b.append('\n');
		}
		if (description != null) {
			b.append(" description = ");
			b.append(description);
			b.append('\n');
		}
		if (startTime != null) {
			b.append(" startTime = ");
			b.append(startTime);
			b.append('\n');
		}
		if (endTime != null) {
			b.append(" endTime = ");
			b.append(endTime);
			b.append('\n');
		}
		if (styleUrl != null) {
			b.append(" styleUrl = ");
			b.append(styleUrl);
			b.append('\n');
		}
		if (viewGroup != null && !viewGroup.isEmpty()) {
			b.append(" viewGroup = ");
			b.append(viewGroup);
			b.append('\n');
		}
		if (region != null && !region.isEmpty()) {
			b.append(" region = ");
			b.append(region);
			b.append('\n');
		}
		if (!elements.isEmpty()) {
			b.append(" elements = ");
			b.append(elements);
			b.append('\n');
		}
		return b.toString();
	}

}
