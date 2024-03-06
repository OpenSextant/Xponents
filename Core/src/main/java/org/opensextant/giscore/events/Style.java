/****************************************************************************************
 *  Style.java
 *
 *  Created: Jan 28, 2009
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.utils.Color;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * Represents style information for points and lines. This information is used
 * by the rendering code to emit the correct information for the output format.
 * <p>
 * Generic information from a KML reference.
 * <p>
 * Color and opacity (alpha) values are expressed in hexadecimal notation. The
 * range of values for any one color is 0 to 255 (00 to ff). For alpha, 00 is
 * fully transparent and ff is fully opaque. The order of expression is
 * aabbggrr, where aa=alpha (00 to ff); bb=blue (00 to ff); gg=green (00 to ff);
 * rr=red (00 to ff). For example, if you want to apply a blue color with 50
 * percent opacity to an overlay, you would specify the following:
 * {@code <color>7fff0000</color>}, where alpha=0x7f, blue=0xff, green=0x00, and
 * red=0x00.
 * <p>
 * Values for {@code <colorMode>} are normal (no effect) and random. A value of random
 * applies a random linear scale to the base {@code <color>}}as follows.
 * <p>
 * <h2>Notes/Limitations:</h2>
 * <p>
 * Some less common tags (e.g. hotSpot in IconStyle and
 * ItemIcon in ListStyle) are not currently supported.
 *
 * @author DRAND
 * @author J.Mathews
 */
public class Style extends StyleSelector {

	private static final long serialVersionUID = 1L;

	public enum ColorMode {NORMAL, RANDOM}

	public enum ListItemType {
		check, checkOffOnly,
		checkHideChildren, radioFolder
	}

	private boolean hasIconStyle; // false
	private Color iconColor;
	private Double iconScale;
	private Double iconHeading;
	private String iconUrl;

	private boolean hasLineStyle; // false
	private Color lineColor;
	private Double lineWidth;
	private ColorMode lineColorMode; // default: normal

	private boolean hasListStyle; // false
	private Color listBgColor;
	private ListItemType listItemType;

	private boolean hasBalloonStyle; // false
	private Color balloonBgColor;
	private Color balloonTextColor;
	private String balloonText;
	private String balloonDisplayMode;

	private boolean hasLabelStyle; // false;
	private Color labelColor;
	private Double labelScale;

	private boolean hasPolyStyle; // false
	private Color polyColor;
	private ColorMode polyColorMode; // default: normal
	private Boolean polyfill;
	private Boolean polyoutline;

	/**
	 * Default Ctor
	 */
	public Style() {
		// default constructor only calls super()
	}

	/**
	 * Constructor Style with id
	 *
	 * @param id
	 */
	public Style(String id) {
		setId(id);
	}

	/**
	 * Copy constructor creates new style as a copy from another style
	 * excluding its id. Caller must explicitly set the unique id on the
	 * target style.
	 *
	 * @param aStyle Source style
	 */
	public Style(Style aStyle) {
		if (aStyle != null) {
			if (aStyle.hasBalloonStyle()) {
				hasBalloonStyle = true;
				balloonBgColor = aStyle.balloonBgColor;
				balloonTextColor = aStyle.balloonTextColor;
				balloonText = aStyle.balloonText;
				balloonDisplayMode = aStyle.balloonDisplayMode;
			}
			if (aStyle.hasIconStyle()) {
				hasIconStyle = true;
				iconColor = aStyle.iconColor;
				iconScale = aStyle.iconScale;
				iconHeading = aStyle.iconHeading;
				iconUrl = aStyle.iconUrl;
			}
			if (aStyle.hasLineStyle()) {
				hasLineStyle = true;
				lineColor = aStyle.lineColor;
				lineWidth = aStyle.lineWidth;
				lineColorMode = aStyle.lineColorMode;
			}
			if (aStyle.hasListStyle()) {
				hasListStyle = true;
				listBgColor = aStyle.listBgColor;
				listItemType = aStyle.listItemType;
			}
			if (aStyle.hasLabelStyle()) {
				hasLabelStyle = true;
				labelColor = aStyle.labelColor;
				labelScale = aStyle.labelScale;
			}
			if (aStyle.hasPolyStyle()) {
				hasPolyStyle = true;
				polyColor = aStyle.polyColor;
				polyfill = aStyle.polyfill;
				polyoutline = aStyle.polyoutline;
			}
		}
	}

	/**
	 * Merge style into this style. This copies all non-null properties
	 * onto this style excluding its id. Null or empty properties in target
	 * style will be ignored. This can for example merge an inline style
	 * over a shared style.
	 *
	 * @param aStyle Source style
	 */
	public void merge(Style aStyle) {
		if (aStyle != null) {
			if (aStyle.hasBalloonStyle()) {
				hasBalloonStyle = true;
				if (aStyle.balloonBgColor != null)
					balloonBgColor = aStyle.balloonBgColor;
				if (aStyle.balloonTextColor != null)
					balloonTextColor = aStyle.balloonTextColor;
				if (aStyle.balloonText != null)
					balloonText = aStyle.balloonText;
				if (aStyle.balloonDisplayMode != null)
					balloonDisplayMode = aStyle.balloonDisplayMode;
			}
			if (aStyle.hasIconStyle()) {
				hasIconStyle = true;
				if (aStyle.iconColor != null)
					iconColor = aStyle.iconColor;
				if (aStyle.iconScale != null)
					iconScale = aStyle.iconScale;
				if (aStyle.iconHeading != null)
					iconHeading = aStyle.iconHeading;
				if (aStyle.iconUrl != null)
					iconUrl = aStyle.iconUrl;
			}
			if (aStyle.hasLabelStyle()) {
				hasLabelStyle = true;
				if (aStyle.labelColor != null)
					labelColor = aStyle.labelColor;
				if (aStyle.labelScale != null)
					labelScale = aStyle.labelScale;
			}
			if (aStyle.hasLineStyle()) {
				hasLineStyle = true;
				if (aStyle.lineColor != null)
					lineColor = aStyle.lineColor;
				if (aStyle.lineWidth != null)
					lineWidth = aStyle.lineWidth;
				if (aStyle.lineColorMode != null)
					lineColorMode = aStyle.lineColorMode;
			}
			if (aStyle.hasListStyle()) {
				hasListStyle = true;
				if (aStyle.listBgColor != null)
					listBgColor = aStyle.listBgColor;
				if (aStyle.listItemType != null)
					listItemType = aStyle.listItemType;
			}
			if (aStyle.hasPolyStyle()) {
				hasPolyStyle = true;
				if (aStyle.polyColor != null)
					polyColor = aStyle.polyColor;
				if (aStyle.polyfill != null)
					polyfill = aStyle.polyfill;
				if (aStyle.polyoutline != null)
					polyoutline = aStyle.polyoutline;
			}
		}
	}

	/**
	 * @return {@code true} if this style contains an icon style,
	 *         {@code false} otherwise.
	 */
	public boolean hasIconStyle() {
		return hasIconStyle;
	}

	/**
	 * @return {@code true} if this style contains a line style,
	 *         {@code false} otherwise.
	 */
	public boolean hasLineStyle() {
		return hasLineStyle;
	}

	/**
	 * @return {@code true} if this style contains a list style,
	 *         {@code false} otherwise.
	 */
	public boolean hasListStyle() {
		return hasListStyle;
	}

	/**
	 * @return {@code true} if this style contains a balloon style,
	 *         {@code false} otherwise.
	 */
	public boolean hasBalloonStyle() {
		return hasBalloonStyle;
	}

	/**
	 * @return {@code true} if this style contains a poly rendering style,
	 *         {@code false} otherwise.
	 */
	public boolean hasPolyStyle() {
		return hasPolyStyle;
	}

	/**
	 * @return {@code true} if this style contains a label style,
	 *         {@code false} otherwise.
	 */
	public boolean hasLabelStyle() {
		return hasLabelStyle;
	}

	/**
	 * Set the icon style information. No Url will use the default icon with provided color and scale.
	 *
	 * @param color the color for the icon, can be null if want to use default color.
	 * @param scale the scale of the icon, nullable (1.0=normal size of icon, 2.0=twice normal size, etc.)
	 */
	public void setIconStyle(Color color, Double scale) {
		setIconStyle(color, scale, null, null);
	}

	/**
	 * Set the icon style information
	 *
	 * @param color the color for the icon, can be null if want to use default color.
	 * @param scale the scale of the icon, nullable (1.0=normal size of icon, 2.0=twice normal size, etc.)
	 * @param url   the url of the icon, nullable. If url is empty string or blank
	 *              then an empty Icon element would appear in KML output.
	 *              If {@code null} then no {@code <Icon>} will appear in IconStyle (using default icon).
	 */
	public void setIconStyle(Color color, Double scale, String url) {
		setIconStyle(color, scale, null, url);
	}

	/**
	 * Set the icon style information
	 *
	 * @param color   the color for the icon, can be null if want to use default color.
	 * @param scale   the scale of the icon, nullable (1.0=normal size of icon, 2.0=twice normal size, etc.)
	 * @param heading heading (i.e. icon rotation) in degrees. Default=0 (North).
	 *                Values range from 0 to 360 degrees, nullable.
	 * @param url     the url of the icon, nullable. If url is blank or empty string
	 *                then an empty Icon element would appear in corresponding KML output.
	 *                If {@code null} then no {@code <Icon>} will appear in IconStyle (using default icon).
	 * @see org.opensextant.giscore.output.kml.KmlOutputStream handleIconStyleElement(Style)
	 */
	public void setIconStyle(Color color, Double scale, Double heading, String url) {
		iconColor = color;
		iconScale = scale == null || scale < 0.0 ? null : scale;
		iconHeading = heading == null || Math.abs(heading - 360) < 0.1 ? null : heading; // default heading = 0.0
		setIconUrl(url);
	}

	/**
	 * Set Icon Style url
	 *
	 * @param url the url of the icon, nullable. If url is blank or empty string
	 *            then an empty Icon element would appear in corresponding KML output.
	 *            If {@code null} then no {@code <Icon>} will appear in IconStyle (using default icon).
	 */
	public void setIconUrl(String url) {
		iconUrl = url == null ? null : url.trim();
		hasIconStyle = iconColor != null || iconScale != null || iconHeading != null || iconUrl != null;
	}

	/**
	 * @return the iconColor, the color to apply to the icon in the display.
	 *         Value may be {@code null} in which the default color should be used.
	 */
	// @CheckForNull

	public Color getIconColor() {
		return iconColor;
	}

	/**
	 * @return the iconScale, the fraction to increase or decrease the size of
	 *         the icon from it's native size.
	 *         Value may be {@code null} in which the default scale (1.0) may be used.
	 */
	// @CheckForNull

	public Double getIconScale() {
		return iconScale;
	}

	/**
	 * @return the iconHeading.
	 *         Value may be {@code null} in which the default heading (0) may be used.
	 */
	// @CheckForNull

	public Double getIconHeading() {
		return iconHeading;
	}

	/**
	 * Get URL associated with IconStyle.
	 *
	 * @return the url of the icon (non-empty or null value)
	 *         If null then IconStyle did not have Icon element. If value is
	 *         non-null then IconStyle should have an associated Icon element
	 *         present.<P>
	 *         If icon URL is empty string then this indicates the href
	 *         element was omitted, an empty element or value was an empty string.<BR>
	 *         All 3 of these cases are handled the same in Google Earth which suppresses
	 *         showing an icon.
	 *         <pre>
	 *                         1. &lt;IconStyle&gt;
	 *                         &lt;Icon/&gt;
	 *                         &lt;/IconStyle&gt;
	 *
	 *                         2. &lt;Icon&gt;
	 *                         &lt;href/&gt;
	 *                         &lt;/Icon&gt;
	 *
	 *                         3. &lt;Icon&gt;
	 *                         &lt;href&gt;&lt;/href&gt;
	 *                         &lt;/Icon&gt;
	 *                              </pre>
	 */
	// @CheckForNull

	public String getIconUrl() {
		return iconUrl;
	}

	/**
	 * Set the line style
	 *
	 * @param color the color of the line(s), can be null if want to use default color.
	 * @param width the width of the line(s).
	 *              Note non-positive width suppresses display of lines in Google Earth
	 */
	public void setLineStyle(Color color, Double width) {
		lineColor = color;
		lineWidth = width == null ? null : (width <= 0.0) ? 0.0 : width;
		hasLineStyle = lineColor != null || lineWidth != null || lineColorMode != null;
	}

	/**
	 * @return the lineColor, the color to use when rendering the line. May
	 *         be {@code null} in which the default color should be used.
	 */
	// @CheckForNull

	public Color getLineColor() {
		return lineColor;
	}

	/**
	 * @return the lineWidth, the width of the line when rendered or <code>null</code> if not defined.
	 *         Valid if {@link #hasLineStyle} is {@code true}.
	 */
	// @CheckForNull

	public Double getLineWidth() {
		return lineWidth;
	}

	public ColorMode getLineColorMode() {
		return lineColorMode;
	}

	public void setLineColorMode(ColorMode lineColorMode) {
		this.lineColorMode = lineColorMode;
		if (lineColorMode != null) {
			hasLineStyle = true;
		} else hasLineStyle = lineColor != null || lineWidth != null;
	}

	public void setListStyle(Color listBgColor, ListItemType listItemType) {
		this.listBgColor = listBgColor;
		this.listItemType = listItemType;
		hasListStyle = listBgColor != null || listItemType != null;
	}

	/**
	 * Valid if {@link #hasListStyle} returns {@code true}.
	 *
	 * @return the list background color, <code>null</code> if not defined.
	 */
	// @CheckForNull

	public Color getListBgColor() {
		return listBgColor;
	}

	// @CheckForNull

	public ListItemType getListItemType() {
		return listItemType;
	}

	/**
	 * Set the balloon style
	 *
	 * @param bgColor     the color for the balloon background, if {@code null}
	 *                    will use default color: opaque white (ffffffff).
	 * @param text        the textual template for the balloon content
	 * @param textColor   the color for the text in the balloon.
	 *                    The default is black (ff000000).
	 * @param displayMode If displayMode is "default", Google Earth uses the information
	 *                    supplied in <b>text</b> to create a balloon . If displayMode is "hide",
	 *                    Google Earth does not display the balloon. "default" is the default
	 *                    value if <code>null</code>, blank or empty string value is supplied.
	 */
	public void setBalloonStyle(Color bgColor, String text, Color textColor,
								String displayMode) {
		this.balloonDisplayMode = StringUtils.trimToNull(displayMode);
		hasBalloonStyle = text != null || bgColor != null || textColor != null || balloonDisplayMode != null;
		this.balloonText = text == null ? null : text.trim(); // allow empty string
		// Note: having display mode=default and all other properties null basically same as having no BalloonStyle at all
		this.balloonBgColor = bgColor;
		this.balloonTextColor = textColor;
	}

	/**
	 * Valid if {@link #hasBalloonStyle} returns {@code true}.
	 *
	 * @return the bgColor, background color of the balloon (optional). Color
	 *         and opacity (alpha) values are expressed in hexadecimal notation.
	 *         The range of values for any one color is 0 to 255 (00 to ff). The
	 *         order of expression is aabbggrr, where aa=alpha (00 to ff);
	 *         bb=blue (00 to ff); gg=green (00 to ff); rr=red (00 to ff). For
	 *         alpha, 00 is fully transparent and ff is fully opaque. For
	 *         example, if you want to apply a blue color with 50 percent
	 *         opacity to an overlay, you would specify the following:
	 *         {@code <bgColor>7fff0000</bgColor>}, where alpha=0x7f, blue=0xff,
	 *         green=0x00, and red=0x00. The default is opaque white (ffffffff).
	 */
	// @CheckForNull

	public Color getBalloonBgColor() {
		return balloonBgColor;
	}

	/**
	 * Valid if {@link #hasBalloonStyle} returns {@code true}.
	 *
	 * @return the text, Text displayed in the balloon. If no text is specified,
	 *         Google Earth draws the default balloon (with the Feature name
	 *         in boldface, the Feature description, links for driving
	 *         directions, a white background, and a tail that is attached to
	 *         the point coordinates of the Feature, if specified).
	 */
	// @CheckForNull

	public String getBalloonText() {
		return balloonText;
	}

	/**
	 * Valid if {@link #hasBalloonStyle} returns {@code true}.
	 *
	 * @return the balloonTextColor, foreground color for text. The default is
	 *         black (ff000000).
	 */
	// @CheckForNull

	public Color getBalloonTextColor() {
		return balloonTextColor;
	}

	/**
	 * Valid if {@link #hasBalloonStyle} returns {@code true}.
	 *
	 * @return the balloonDisplayMode, If displayMode is 'default', Google Earth
	 *         uses the information supplied in <b>text</b> to create a balloon . If
	 *         displayMode is 'hide', Google Earth does not display the balloon.
	 *         In Google Earth, clicking the List View icon for a Placemark
	 *         whose balloon's displayMode is 'hide' causes Google Earth to fly
	 *         to the Placemark.
	 */
	//@CheckForNull
	public String getBalloonDisplayMode() {
		return balloonDisplayMode;
	}

	/**
	 * Set the label style
	 *
	 * @param color the color for the label, can be null if want to use default color.
	 * @param scale the scale of the labels, nullable (1.0=normal size, 2.0=twice normal size, etc.)
	 */
	public void setLabelStyle(Color color, Double scale) {
		labelColor = color;
		labelScale = scale;
		hasLabelStyle = color != null || scale != null;
	}

	/**
	 * Valid if {@link #hasLabelStyle} returns {@code true}.
	 *
	 * @return the labelColor, <code>null</code> if not defined.
	 */
	// @CheckForNull
	public Color getLabelColor() {
		return labelColor;
	}

	/**
	 * Valid if {@link #hasLabelStyle} returns {@code true}.
	 *
	 * @return the labelScale, <code>null</code> if not defined.
	 */
	//@CheckForNull
	public Double getLabelScale() {
		return labelScale;
	}

	/**
	 * Set the poly style
	 *
	 * @param color   Polygon color
	 *                the color for the Polygon, can be null if want to use default color.
	 * @param fill    Specifies whether to fill the polygon
	 * @param outline Specifies whether to outline the polygon. Polygon outlines use the current LineStyle.
	 */
	public void setPolyStyle(Color color, Boolean fill, Boolean outline) {
		polyColor = color;
		polyfill = fill;
		polyoutline = outline;
		hasPolyStyle = color != null || fill != null || outline != null || polyColorMode != null;
	}

	/**
	 * Valid if {@link #hasPolyStyle} returns {@code true}.
	 *
	 * @return the polyColor
	 */
	// @CheckForNull
	public Color getPolyColor() {
		return polyColor;
	}

	/**
	 * Valid if {@link #hasPolyStyle} returns {@code true}.
	 *
	 * @return the polyfill, specifies whether to fill the polygon.
	 */
	// @CheckForNull
	public Boolean getPolyfill() {
		return polyfill;
	}

	/**
	 * Valid if {@link #hasPolyStyle} returns {@code true}.
	 *
	 * @return the polyoutline, specifies whether to outline the polygon.
	 *         Polygon outlines use the current LineStyle.
	 */
	// @CheckForNull
	public Boolean getPolyoutline() {
		return polyoutline;
	}

	public ColorMode getPolyColorMode() {
		return polyColorMode;
	}

	public void setPolyColorMode(ColorMode polyColorMode) {
		this.polyColorMode = polyColorMode;
		if (!hasPolyStyle && polyColorMode != null)
			hasPolyStyle = true;
	}

	public void accept(IStreamVisitor visitor) {
		visitor.visit(this);
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
	 * @see org.opensextant.giscore.utils.IDataSerializable#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		hasIconStyle = in.readBoolean();
		if (hasIconStyle) {
			iconColor = (Color) in.readScalar();
			iconUrl = in.readString();
			iconScale = (Double) in.readScalar();
			iconHeading = (Double) in.readScalar();
		}
		hasLineStyle = in.readBoolean();
		if (hasLineStyle) {
			lineColor = (Color) in.readScalar();
			lineWidth = (Double) in.readScalar();
			// TODO: lineColorMode
		}

		hasListStyle = in.readBoolean();
		if (hasListStyle) {
			listBgColor = (Color) in.readScalar();
			listItemType = (ListItemType) in.readEnum(ListItemType.class);
		}

		hasBalloonStyle = in.readBoolean();
		if (hasBalloonStyle) {
			balloonBgColor = (Color) in.readScalar();
			balloonTextColor = (Color) in.readScalar();
			balloonText = in.readString();
			balloonDisplayMode = in.readString();
		}

		hasLabelStyle = in.readBoolean();
		if (hasLabelStyle) {
			labelColor = (Color) in.readScalar();
			labelScale = (Double) in.readScalar();
		}

		hasPolyStyle = in.readBoolean();
		if (hasPolyStyle) {
			polyColor = (Color) in.readScalar();
			polyfill = (Boolean) in.readScalar();
			polyoutline = (Boolean) in.readScalar();
			polyColorMode = in.readBoolean() ? ColorMode.RANDOM : null;
		}
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeBoolean(hasIconStyle);
		if (hasIconStyle) {
			out.writeScalar(iconColor);
			out.writeString(iconUrl);
			out.writeScalar(iconScale);
			out.writeScalar(iconHeading);
		}

		out.writeBoolean(hasLineStyle);
		if (hasLineStyle) {
			out.writeScalar(lineColor);
			out.writeScalar(lineWidth);
			// TODO: lineColorMode
		}

		out.writeBoolean(hasListStyle);
		if (hasListStyle) {
			out.writeScalar(listBgColor);
			out.writeEnum(listItemType);
		}

		out.writeBoolean(hasBalloonStyle);
		if (hasBalloonStyle) {
			out.writeScalar(balloonBgColor);
			out.writeScalar(balloonTextColor);
			out.writeString(balloonText);
			out.writeString(balloonDisplayMode);
		}

		out.writeBoolean(hasLabelStyle);
		if (hasLabelStyle()) {
			out.writeScalar(labelColor);
			out.writeScalar(labelScale);
		}

		out.writeBoolean(hasPolyStyle);
		if (hasPolyStyle()) {
			out.writeScalar(polyColor);
			out.writeScalar(polyfill);
			out.writeScalar(polyoutline);
			out.writeBoolean(polyColorMode == ColorMode.RANDOM);
		}
	}

}
