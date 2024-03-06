/****************************************************************************************
 *  Overlay.java
 *
 *  Created: Feb 4, 2009
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




import java.io.IOException;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.opensextant.giscore.utils.Color;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * Abstract overlay class is a parent to the specific classes for each.
 * Note Overlays extend Feature class but in KML context they do not have a Geometry.  
 * 
 * @author DRAND
 * 
 */
public abstract class Overlay extends Feature {
	private static final long serialVersionUID = 1L;
	
	private TaggedMap icon;
	private Color color;
	private int drawOrder;

	/**
     * Get Icon properties which may include href, refreshMode, refreshInterval,
     * viewRefreshMode, viewFormat, etc. all of which are optional.
     * 
	 * @return the icon property map
	 */
    // @CheckForNull

	public TaggedMap getIcon() {
		return icon;
	}

	/**
     * Set Icon properties which may include href, refreshMode, refreshInterval,
     * viewRefreshMode, viewFormat, etc. all of which are optional.
     * 
	 * @param icon
	 *            the icon property map to set
	 */
	public void setIcon(TaggedMap icon) {
		this.icon = icon;
	}

	/**
	 * @return the color
	 */
    // @CheckForNull

	public Color getColor() {
		return color;
	}

	/**
	 * @param color
	 *            the color to set
	 */
	public void setColor(Color color) {
		this.color = color;
	}

	/**
	 * @return the drawOrder
	 */
	public int getDrawOrder() {
		return drawOrder;
	}

	/**
	 * Defines the draw order. Draw order is the stacking order overlapping
	 * overlays. Overlays with higher @{code drawOrder} values are
	 * drawn on top of overlays with lower @{code drawOrder} values.
	 * @param drawOrder
	 *            the drawOrder to set
	 */
	public void setDrawOrder(int drawOrder) {
		this.drawOrder = drawOrder;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((icon == null) ? 0 : icon.hashCode());
		result = prime * result
				+ ((color == null) ? 0 : color.hashCode());
		result = prime * result + drawOrder;
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
		Overlay other = (Overlay) obj;
		if (drawOrder != other.drawOrder)
			return false;
		if (icon == null) {
			if (other.icon != null)
				return false;
		} else if (other.icon == null ||
				!icon.equals(other.icon))
			return false;
		if (color == null) {
			return other.color == null;
		} else return other.color != null &&
				color.equals(other.color);
	}

	/**
	 * The approximately equals method checks all the fields for equality with
	 * the exception of the geometry.
	 * 
	 * @param tf Target feature to compare against
	 * @return true if this and other are approximately equal
	 */
    @Override
    public boolean approximatelyEquals(Feature tf) {
		if (!(tf instanceof Overlay))
			return false;
		if (!super.approximatelyEquals(tf))
			return false;

		Overlay other = (Overlay) tf;
		EqualsBuilder eb = new EqualsBuilder();

		// Note: icon.href may get normalized/escaped making it fail equality test
		// (e.g. http://chart.google.com/chart?text=Hello%20Word) gets normalized
		// differently in KmlOutputStream.handleLinkElement().

        return eb.append(color, other.color).append(drawOrder, other.drawOrder)
                .append(icon, other.icon).isEquals();
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Feature#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		boolean hasColor = in.readBoolean();
		if (hasColor) {
			int rgb = in.readInt();
			color = new Color(rgb);
		}
		drawOrder = in.readInt();
		icon = (TaggedMap) in.readObject();
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Feature#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {	
		super.writeData(out);
		if (color != null) {
			out.writeBoolean(true);
			out.writeInt(color.getRGB());
		} else {
			out.writeBoolean(false);
		}
		out.writeInt(drawOrder);
		out.writeObject(icon);
	}

    @Override
    public String toString() {
		return ToStringBuilder.reflectionToString(this,
				ToStringStyle.MULTI_LINE_STYLE);
	}

    /**
     * Compare if two Double objects are approximately equal.
     * @param   a        the first {@code Double} to compare
     * @param   b        the second {@code Double} to compare
     * @return true if values are close, otherwise false.
     */
	protected static boolean closeDouble(Double a, Double b) {
		if (a == null && b == null)
			return true;
		else if (a != null && b != null) {
			double delta = Math.abs(a - b);
			return delta < 1e-5; // delta < epsilon
		} else {
			return false;
		}
	}
}
