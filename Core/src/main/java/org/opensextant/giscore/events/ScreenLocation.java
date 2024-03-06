/****************************************************************************************
 *  ScreenLocation.java
 *
 *  Created: Feb 4, 2009
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
import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a specific point on the screen, either measure in a percentage
 * or a count of pixels. Used by the screen overlays to dictate where they
 * are on the screen.
 * 
 * @author DRAND
 *
 */
public class ScreenLocation implements IDataSerializable, Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(ScreenLocation.class);

	public enum UNIT {
        PIXELS("pixels"), 
		FRACTION("fraction"), 
		INSETPIXELS("insetPixels");
		UNIT(String v) {
			kmlValue = v;
		}
		public final String kmlValue;

		public static UNIT normalize(String s) {
			if (StringUtils.isNotBlank(s))
				try {
					return valueOf(s);
				} catch (IllegalArgumentException e) {
					log.warn("Ignoring invalid unit value: " + s); // use default value
				}
			return FRACTION; // default value
		}
	}
    
    public double x;
	public UNIT xunit = UNIT.FRACTION;
	public double y;
	public UNIT yunit = UNIT.FRACTION;
	
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
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		x = in.readDouble();
		xunit = UNIT.normalize(in.readString());
		y = in.readDouble();
		yunit = UNIT.normalize(in.readString());
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		out.writeDouble(x);
		out.writeString(xunit == null ? null : xunit.name());
		out.writeDouble(y);
		out.writeString(yunit == null ? null : yunit.name());
	}	
}
