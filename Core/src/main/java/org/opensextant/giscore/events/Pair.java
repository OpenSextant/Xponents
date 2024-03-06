/****************************************************************************************
 *  Pair.java
 *
 *  (C) Copyright MITRE Corporation 2011
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


import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.input.kml.UrlRef;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a key/value pair that maps a mode (normal or highlight) to the
 * predefined styleUrl or inline Style as defined in KML for StyleMaps.
 * <code>Pair</code> contains two elements (both are required): <code>key</code>, which
 * identifies the key, and <code>styleUrl</code> or <code>Style</code>, which references
 * the style. In <code>styleUrl</code>>, for referenced style elements that are
 * local to the KML document, a simple # referencing is used. For styles that
 * are contained in external files, use a full URL along with # referencing.
 * <P>For example:
 * <pre>
 *     {@code
 *     <Pair>
 *         <key>normal</key>
 *         <styleUrl><a href="http://myserver.com/populationProject.xml#example_style_off">...</a>
 *         </styleUrl>
 *     }
 * </pre>
 *
 * @author Jason Mathews, MITRE Corp.
 * Created: 6/21/11 4:51 PM
 */
public class Pair implements IDataSerializable, Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(Pair.class);

	@NotNull private String key;
	private String id;
	private String styleUrl;
	private StyleSelector styleSelector;

	/** Empty ctor only for object IO.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise object is invalid.
	 * @see SimpleObjectInputStream
	 * @see SimpleObjectOutputStream
	 * */
	public Pair() {
		key = "normal"; // must be non-null
	}

	public Pair(String key, String styleUrl) {
		setKey(key);
		setStyleUrl(styleUrl);
	}

	public Pair(String key, String styleUrl, StyleSelector style) {
		setKey(key);
		setStyleUrl(styleUrl);
		this.styleSelector = style;
	}

	@NotNull
	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		key = StringUtils.trimToNull(key);
		if (key == null) {
			throw new IllegalArgumentException("key should never be null or empty");
		}
		this.key = key;
	}

	// @CheckForNull

	public String getStyleUrl() {
		return styleUrl;
	}

	public void setStyleUrl(String styleUrl) {
		// try to auto-fix bad KML. test if url relative identifier not starting
		// with '#' then prepend '#' to url
        if (styleUrl != null && !styleUrl.startsWith("#") && UrlRef.isIdentifier(styleUrl, true)) {
            styleUrl = "#" + styleUrl;
            log.debug("fix styleUrl identifier as local reference: {}", styleUrl);
        }
		this.styleUrl = styleUrl;
	}

	// @CheckForNull

	public StyleSelector getStyleSelector() {
		return styleSelector;
	}

	public void setStyleSelector(StyleSelector styleSelector) {
		// note nested StyleMaps (StyleMap inside a Pair in a parent StyleMap) can be created
		// but this doesn't make much sense conceptually.
		// styleSelector should only contain Style instances or null if styleUrl is used
		this.styleSelector = styleSelector;
	}

	// @CheckForNull

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
		if (obj == null || getClass() != obj.getClass())
			return false;
		Pair other = (Pair) obj;
		if (!key.equals(other.key))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (styleUrl == null) {
			if (other.styleUrl != null)
				return false;
		} else if (!styleUrl.equals(other.styleUrl))
			return false;
		if (styleSelector == null) {
			return other.styleSelector == null;
		} else return styleSelector.equals(other.styleSelector);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = key.hashCode();
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((styleUrl == null) ? 0 : styleUrl.hashCode());
		result = prime * result + ((styleSelector == null) ? 0 : styleSelector.hashCode());
		return result;
	}

	/**
	 * (non-Javadoc)
	 * @see org.opensextant.giscore.utils.SimpleObjectInputStream#readObject()
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		key = in.readString();
		if (key == null) throw new InstantiationException("Pair key cannot be null");
		id = in.readString();
		styleUrl = in.readString();
		styleSelector = (StyleSelector)in.readObject();
	}

	/*
	 * (non-Javadoc)
	 * @see SimpleObjectOutputStream#writeObject(org.opensextant.giscore.utils.IDataSerializable)
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		out.writeString(key);
		out.writeString(id);
		out.writeString(styleUrl);
		out.writeObject(styleSelector);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}

}
