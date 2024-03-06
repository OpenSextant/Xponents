/****************************************************************************************
 *  PointShapeMapper.java
 *
 *  Created: Jul 22, 2009
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
package org.opensextant.giscore.output.shapefile;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps from urls in Style objects to supported shapes from ESRI. The shapes are
 * given by name in the map, i.e. "star", and this code will translate them to
 * the numeric values required by the shm file.
 * <p>
 * If there is no direct mapping then the name in the URL path will be used to
 * map into the ESRI space.
 * 
 * @author DRAND
 * 
 */
public class PointShapeMapper {
	private static final String CIRCLE_STR = "circle";
	private static final String TRIANGLE_STR = "triangle";
	private static final String STAR_STR = "star";
	private static final String SQUARE_STR = "square";
	private static final String CROSS_STR = "cross";
	public static final short CIRCLE = 0;
	public static final short SQUARE = 1;
	public static final short TRIANGLE = 2;
	public static final short CROSS = 3;
	public static final short STAR = 4;

	/**
	 * Pattern that finds the name portion of the path
	 */
	private static final Pattern name = Pattern
			.compile(".*/(\\p{Alpha}*)\\..*");

	/**
	 * Map from url to value.
	 */
	private Map<URL, Short> shapeMap = new HashMap<>();

	/**
	 * Map from value to url
	 */
	private Map<Short, URL> urlMap = new HashMap<>();

	/**
	 * When mapping from a value to a URL use this for cases where the value
	 * isn't mapped
	 */
	private URL baseUrl;

	/**
	 * When mapping from a value to a URL use this for a suffix for cases where
	 * the value isn't mapped
	 */
	private String suffix;

	/**
	 * Map from the url to a symbol value used by ESRI. Defaults to circle if
	 * there's no mapping and/or the name in the path doesn't match one of the
	 * given 5 shapes.
	 * 
	 * @param url
	 * @return
	 */
	public short getMarker(URL url) {
		if (url == null) {
			throw new IllegalArgumentException("url should never be null");
		}
		Short rval = shapeMap.get(url);
		if (rval == null) {
			String path = url.getPath();
			Matcher m = name.matcher(path);
			if (m.matches()) {
				String name = m.group(1).toLowerCase();
				if (CIRCLE_STR.equals(name)) {
					return 0;
				} else if (CROSS_STR.equals(name)) {
					return 3;
				} else if (SQUARE_STR.equals(name)) {
					return 1;
				} else if (STAR_STR.equals(name)) {
					return 4;
				} else if (TRIANGLE_STR.equals(name)) {
					return 2;
				}
			}
			return 0; // Circle
		} else {
			return rval;
		}
	}

	/**
	 * Map from the value to a URL for use in GIScore. If the value doesn't map
	 * then we try and build a sensible url using the base url and the suffix.
	 * 
	 * @param value
	 * @return
	 * @throws MalformedURLException 
	 */
	public URL getURL(short value) throws MalformedURLException {
		URL rval = urlMap.get(value);
		if (rval != null)
			return rval;
		String name;
		switch (value) {
		case CROSS:
			name = CROSS_STR;
			break;
		case SQUARE:
			name = SQUARE_STR;
			break;
		case STAR:
			name = STAR_STR;
			break;
		case TRIANGLE:
			name = TRIANGLE_STR;
			break;
		default:
			name = CIRCLE_STR;
		}
		if (suffix != null)
			name = name + suffix;
		return new URL(baseUrl.toExternalForm() + name);
	}

	public Map<URL, Short> getShapeMap() {
		return shapeMap;
	}

	public void setShapeMap(Map<URL, Short> shapeMap) {
		this.shapeMap = shapeMap;

		urlMap = new HashMap<>();
		for (Map.Entry<URL, Short> ent : shapeMap.entrySet()) {
			urlMap.put(ent.getValue(), ent.getKey());
		}
	}

	public URL getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(URL baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
}
