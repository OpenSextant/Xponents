/*
 *  KmlBaseReader.java
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
 */
package org.opensextant.giscore.input.kml;


import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.events.NetworkLink;
import org.opensextant.giscore.events.TaggedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * URL rewriting logic extracted from KmlReader handles low-level rewriting
 * URL href if relative link along with some other helper methods.
 * <p>
 * Makes best effort to resolve relative URLs but has some limitations such as if
 * KML has nested chain of network links with mix of KML and KMZ resources.
 * KMZ files nested inside KMZ files are not supported.
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: Mar 30, 2009 12:04:01 PM
 */
public abstract class KmlBaseReader implements IKml {

	private static final Logger log = LoggerFactory.getLogger(KmlBaseReader.class);

	/**
	 * if true indicates that the stream is for a KMZ compressed file
	 * and network links with relative URLs need to be handled special
	 */
	protected boolean compressed;

	protected URL baseUrl;

	private Geodetic2DBounds viewBounds;
	/**
	 * holder for names of supported httpQuery fields as of 2/19/09 in Google Earth 5.0.11337.1968 with KML 2.2
	 * httpQuery names unchanged as of April 2011 with Google Earth 6.0.2.2074.
	 */
	private static final Map<String,String> httpQueryLabels = new HashMap<>();

    /**
     * names of supported viewFormat fields as of 2/19/09 in Google Earth 5.0.11337.1968 with KML 2.2
     * viewFormat names unchanged as of April 2011 with Google Earth 6.0.2.2074.
     * see <a href="http://code.google.com/apis/kml/documentation/kmlreference.html#link">...</a>
     */
	private static final Map<String,String> VIEW_FORMAT_LABELS = new HashMap<>();

	private final Map<String,String> viewFormatLabels = new HashMap<>();

	static {
		final String[] labels = {
				"clientVersion", "5.2.1.1588",
				"kmlVersion",   "2.2",
				"clientName",   "Google+Earth",
				"language",     "en"};

		for (int i = 0; i < labels.length; i += 2)
			httpQueryLabels.put(labels[i], labels[i+1]);

        final String[] viewLabels = {
			"bboxEast",         "180",
			"bboxNorth",        "90",
			"bboxSouth",        "-45",
			"bboxWest",         "-180",
			"cameraLon",        "0",
			"cameraLat",        "0",
			"cameraAlt",        "0",
			"horizFov",         "60",
			"horizPixels",      "917",
			"lookatHeading",    "0",
			"lookatLat",        "0",
			"lookatLon",        "0",
			"lookatRange",      "7190000",
			"lookatTerrainAlt", "0",
			"lookatTerrainLat", "0",
			"lookatTerrainLon", "0",
			"lookatTilt",       "0",
			"terrainEnabled",   "1",
			"vertFov",          "56.477",
			"vertPixels",       "853" };

        for (int i = 0; i < viewLabels.length; i += 2)
			VIEW_FORMAT_LABELS.put(viewLabels[i], viewLabels[i+1]);
	}

	KmlBaseReader() {
		viewFormatLabels.putAll(VIEW_FORMAT_LABELS);
	}
	public boolean isCompressed() {
		return compressed;
	}

	/**
	 * Return true only if Region is out of view otherwise false
	 * @param region
	 * @return
	 */
	public boolean checkRegion(TaggedMap region) {
		/*
		<Region id="ID">
		<LatLonAltBox>
		 <north></north>                            <!-- required; kml:angle90 -->
		 <south></south>                            <!-- required; kml:angle90 -->
		 <east></east>                              <!-- required; kml:angle180 -->
		 <west></west>                              <!-- required; kml:angle180 -->
		 <minAltitude>0</minAltitude>               <!-- float -->
		 <maxAltitude>0</maxAltitude>               <!-- float -->
		 <altitudeMode>clampToGround</altitudeMode>
		</LatLonAltBox>
		*/
		if (region == null || region.isEmpty())
			return false;
		Double north = region.getDoubleValue(NORTH);
		if (north == null) return false;
		Double south = region.getDoubleValue(SOUTH);
		if (south == null) return false;
		Double east = region.getDoubleValue(EAST);
		if (east == null) return false;
		Double west = region.getDoubleValue(WEST);
		if (west == null) return false;
		// invalidate bogus regions
		// valid constraints:
		// 1. kml:north > kml:south; lat range: +/- 90
		// 2. kml:east > kml:west;   lon range: +/- 180
		if (north <= south || east <= west) return false;
		try {
		if (viewBounds == null) {
			double viewNorth = getViewFormatValue(IKml.BBOX_NORTH, 90);
			double viewSouth = getViewFormatValue(IKml.BBOX_SOUTH, -90);
			double viewEast = getViewFormatValue(IKml.BBOX_EAST, 180);
			double viewWest = getViewFormatValue(IKml.BBOX_WEST, -180);
			viewBounds = new Geodetic2DBounds(
				new Geodetic2DPoint(new Longitude(viewEast, Angle.DEGREES),
						new Latitude(viewNorth, Angle.DEGREES)),	// north-east
				new Geodetic2DPoint(new Longitude(viewWest, Angle.DEGREES),
						new Latitude(viewSouth, Angle.DEGREES)));	// south-west
		}
		Geodetic2DBounds bbox = new Geodetic2DBounds(
				new Geodetic2DPoint(new Longitude(east, Angle.DEGREES),
						new Latitude(north, Angle.DEGREES)),	// north-east
				new Geodetic2DPoint(new Longitude(west, Angle.DEGREES),
						new Latitude(south, Angle.DEGREES)));	// south-west

		return !viewBounds.intersects(bbox);
		} catch (IllegalArgumentException e ) {
			log.debug("", e);
			return false;
		}
	}

	private double getViewFormatValue(String label, double defaultValue) {
		String value = viewFormatLabels.get(label);
		if (value != null) {
			try {
				return Double.parseDouble(value);
			} catch(NumberFormatException nfe) {
				log.debug(label, nfe);
			}
		}
		return defaultValue;
	}
	/***
	 * Adjust Link href URL if httpQuery and/or viewFormat parameters are defined.
	 * Rewrites URL href if needed and returns URL as URI. Stores back href value in links
	 * TaggedMap if modifications were made to href otherwise left unchanged. 
	 *
	 * @param parent Parent UrlRef from which to resolve relative URLs in links
	 * @param links TaggedMap object containing href link
     * @return adjusted href URL as URI, null if href is missing or empty string
	 */
    // @CheckForNull

	protected URI getLinkHref(UrlRef parent, TaggedMap links) {
		String href = links != null ? trimToNull(links, HREF) : null;
		if (href == null) return null;
		URI uri = getLink(parent, href);
		if (uri == null) return null;

		String httpQuery = trimToNull(links, HTTP_QUERY);
		String viewFormat = trimToEmpty(links, VIEW_FORMAT); // allowed to be empty string
		href = uri.toString();

        /*
        If you specify a <viewRefreshMode> of onStop and do not include the <viewFormat> tag in the file,
        the following information is automatically appended to the query string:

        BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]

        This information matches the Web Map Service (WMS) bounding box specification.
        If you specify an empty <viewFormat> tag, no information is appended to the query string.
        */
        String viewRefreshMode = trimToNull(links, VIEW_REFRESH_MODE); // default=never
        // System.out.printf("%nXXX: viewRefreshMode=%s viewFormat=%s%n%n", viewRefreshMode, viewFormat);
        if (VIEW_REFRESH_MODE_NEVER.equals(viewRefreshMode)) {
            // viewRefreshMode = never (default) - Ignore changes in the view. Also ignore <viewFormat> parameters, if any sent as all 0's
            viewRefreshMode = null; // never is the default value so simply test as null below
        } else if (VIEW_REFRESH_MODE_ON_STOP.equals(viewRefreshMode) && viewFormat == null) {
            viewFormat = VIEW_FORMAT_DEFAULT; // BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]
            // System.out.printf("XXX: new viewRefreshMode=%s viewFormat=%s%n%n", viewRefreshMode, viewFormat);
        }

		// if have NetworkLink href with no httpQuery/viewFormat then
		// return href as-is otherwise modify href accordingly.
		// Likewise if URI is local file then httpQuery and viewFormat are ignored
		if (StringUtils.isBlank(viewFormat) && StringUtils.isBlank(httpQuery) || "file".equals(uri.getScheme())) {
			// if URL was relative then getLink() rewrites URL to be absolute wrt to the baseURL
			// store modified HREF back in map
			links.put(HREF, href);
			return uri;
		}

		StringBuilder buf = new StringBuilder(href);
		// check if '?' is part of base HREF
		// sometimes last character of URL is ? in which case don't need to add anything
		if (href.charAt(href.length() - 1) != '?') {
			buf.append(href.indexOf('?') == -1 ? '?' : '&');
		}

		/*
			Construct HttpQuery and viewFormat values
			http://code.google.com/apis/kml/documentation/kmlreference.html

			KML NetworkLink Example:

			<NetworkLink>
			   <Link>
				<href>baseUrl</href>
				<viewFormat>BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth];\
					CAMERA=[lookatLon],[lookatLat],[lookatRange],[lookatTilt],[lookatHeading];\
					VIEW=[horizFov],[vertFov],[horizPixels],[vertPixels],[terrainEnabled];\
					LOOKAT=[lookatTerrainLon],[lookatTerrainLat],[lookatTerrainAlt]
				</viewFormat>
				<httpQuery>client=[clientVersion],[kmlVersion],[clientName],[language]</httpQuery>
			   </Link>
			 </NetworkLink>

			Issues following URL fetch via HTTP GET
			baseUrl?client=5.0.11337.1968,2.2,Google+Earth,en&BBOX=0,0,0,0;CAMERA=0,0,0,0,0;VIEW=0,0,0,0,0;LOOKAT=0,0,0
			if '?' is in the href URL then '&amp;' is appended before httpQuery and/or viewFormat arguments
			Any spaces in httpQuery or viewFormat are encoded as %20. Other []'s are encoded as %5B%5D

			seamap.kml with LookAt

			<LookAt>
				<longitude>-1.8111</longitude>
				<latitude>54.3053</latitude>
				<altitude>0</altitude>
				<range>9500000</range>
				<tilt>0</tilt>
				<heading>-2.0</heading>
			</LookAt>

			baseUrl?mode=NetworkLink&taxa_column=all_taxa&BBOX=-180,-12.00837846543677,180,90

			<LookAt>
				<longitude>-95.2654831941224</longitude>
				<latitude>38.95938957105111</latitude>
				<altitude>0</altitude>
				<range>11001000</range>
				<tilt>0</tilt>
				<heading>2.942013080353753e-014</heading>
				<altitudeMode>relativeToGround</altitudeMode>
			</LookAt>

			GET /placemark.kml?client=Google+Earth,5.0.11337.1968,2.2,Google+Earth,en,%5Bfoobar%5D&
			BBOX=-180,-56.92725201297682,180,90;
			CAMERA=-40.00123907841759,25.00029463919559,-21474836.48,0,0;
			VIEW=60,54.921,751,676,1;
			LOOKAT=-40.00123610631735,25.00029821129455,-4824.05

			*/

		if (httpQuery != null) {
			/*
			 * <httpQuery>
			 *  [clientVersion]  5.0.11337.1968      4.3.7284.3916
			 *  [kmlVersion]     2.2
			 *  [clientName]     Google+Earth
			 *  [language]       en
			 */
			for (int i=0; i < httpQuery.length(); i++) {
				char ch = httpQuery.charAt(i);
				if (ch == '[') {
					int ind = httpQuery.indexOf(']', i + 1);
					String val = null;
					if (ind != -1) {
						String key = httpQuery.substring(i + 1, ind);
						val = httpQueryLabels.get(key);
					}
					if (val != null) {
						// insert replacement value for key (e.g. clientVersion, kmlVersion. etc.)
						buf.append(val);
						i = ind;
					} else
						buf.append("%5B");
				}
				else if (ch == ']')
					buf.append("%5D");
				else if (ch == ' ')
					buf.append("%20");
				else
					buf.append(ch);
			}

			// client=Google+Earth,4.3.7284.3916,2.2,%20Google+Earth,en&BBOX=0,0,0,0;CAMERA=0,0,0,0,0;VIEW=0,0,0,0,0;lookAt=0,0,0

			// add httpQuery parameters to URL
			// unscape HTML encoding &amp; -> &
			//href +=  + httpQuery.replace("&amp;", "&");
		}

		/*
	<viewFormat>

		Specifies the format of the query string that is appended to the Link's <href> before the file is fetched.
		(If the <href> specifies a local file, this element is ignored.)
		If you specify a <viewRefreshMode> of onStop and do not include the <viewFormat> tag in the file,
		the following information is automatically appended to the query string:

		BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]

	This information matches the Web Map Service (WMS) bounding box specification.
	If you specify an empty <viewFormat> tag, no information is appended to the query string.

	You can also specify a custom set of viewing parameters to add to the query string. If you supply a format string,
	it is used instead of the BBOX information. If you also want the BBOX information, you need to add those parameters
	along with the custom parameters.

	You can use any of the following parameters in your format string (and Google Earth will substitute the appropriate
	current value at the time it creates the query string):

		* [lookatLon], [lookatLat] - longitude and latitude of the point that <LookAt> is viewing
		* [lookatRange], [lookatTilt], [lookatHeading] - values used by the <LookAt> element (see descriptions of <range>, <tilt>, and <heading> in <LookAt>)
		* [lookatTerrainLon], [lookatTerrainLat], [lookatTerrainAlt] - point on the terrain in degrees/meters that <LookAt> is viewing
		* [cameraLon], [cameraLat], [cameraAlt] - degrees/meters of the eyepoint for the camera
		* [horizFov], [vertFov] - horizontal, vertical field of view for the camera
		* [horizPixels], [vertPixels] - size in pixels of the 3D viewer
		* [terrainEnabled] - indicates whether the 3D viewer is showing terrain
        */

		if (viewFormat != null) {
			if (httpQuery != null)
				buf.append('&');

			for (int i=0; i < viewFormat.length(); i++) {
				char ch = viewFormat.charAt(i);
				if (ch == '[') {
					int ind = viewFormat.indexOf(']', i + 1);
					if (ind != -1) {
						String key = viewFormat.substring(i + 1, ind);
                        String value = viewFormatLabels.get(key);
                        if (value != null) {
                            // insert default values for viewFormat parameters
							// see http://code.google.com/apis/kml/documentation/kmlreference.html#viewformat
                            // viewRefreshMode = never (default) - Ignore changes in the view. Also ignore <viewFormat> parameters, if any sent as all 0's
                            if (viewRefreshMode == null) buf.append('0');
                            else buf.append(value);
							i = ind;
							continue;
						}
					}
					buf.append("%5B"); // hex-encode '['
				}
				else if (ch == ']')
					buf.append("%5D");
				else if (ch == ' ')
					buf.append("%20");
				else
					buf.append(ch);
			}
		}

		href = buf.toString();
		// store modified HREF back in map
		links.put(HREF, href);
		try {
			return new URI(href);
		} catch (URISyntaxException e) {
			log.error("Failed to create URI from URL=" + href, e);
			return null;
		}
	}

    // @CheckForNull

	protected URI getLink(UrlRef parent, String href) {
        // assumes href is not null nor zero length
        URI uri = null;
        try {
            // must escape special characters (e.g. [], whitespace, etc.) otherwise new URI() throws URISyntaxException
			// e.g. http://mw1.google.com/mw-earth-vectordb/kml-samples/gp/seattle/gigapxl/$[level]/r$[y]_c$[x].jpg
            href = UrlRef.escapeUri(href);            

            // check if URL is absolute otherwise it is relative to base URL if defined
            if (UrlRef.isAbsoluteUrl(href)) {
                // absolute URL (e.g. http://host/path/x.kml)
                // uri = new URL(href).toURI();
                uri = new URI(href);
                //href = url.toExternalForm();
            } else if (baseUrl == null) {
                log.warn("no base URL to resolve relative URL: " + href);
            } else {
                // relative URL
                // if compressed amd relative link then need special encoded kmz URI
				// if parent other than baseUrl then use explicit parent
				URL baseUrl = parent == null ? this.baseUrl : parent.getURL();
                //if (parent != null) System.out.format("XXX: parent=%s uisKmz=%b%n", parent, parent.isKmz());//debug
                /*
                    make best effort to resolve relative URLs but note limitations:
                    if for example parent KML includes networkLink to KMZ
                    which in turn links a KML which in turn has relative link to image overlay
                    then parent of overlay URI will not be compressed/kmz
                    and will fail to get inputStream to the image within KMZ file...
                */
                if (compressed || (parent != null && parent.getURL().getFile().endsWith(".kmz"))) {
                    //System.out.println("XXX: compressed: base="+ baseUrl);//debug
                    // if relative link and parent is KMZ file (compressed=true)
                    // then need to keep track of parent URL in addition
                    // to the relative link to match against the KMZ file entries.
                    uri = new UrlRef(baseUrl, href).getURI();
                    // System.err.println("XXX:" + uri);
                } else {
                    //System.out.println("XXX: uncompressed: base="+ baseUrl);//debug
                    // what if from networklink that was compressed??
                    uri = new URL(baseUrl, href).toURI();
                }
            }
        } catch (URISyntaxException | MalformedURLException e) {
            log.warn("Invalid link: " + href, e);
        }
		return uri;
    }

    /**
     * Gets non-empty for named value in TaggedMap or null if not found (or empty/blank string).
     * @param map TaggedMap (never null)
     * @param name
     * @return non-empty value if found and non-blank string otherwise <code>null</code>
     */
    @Nullable
    protected static String trimToNull(TaggedMap map, String name) {
        String val = map.get(name);
        if (val != null) {
            val = val.trim();
            if (val.length() == 0) return null;
        }
        return val;
    }

    /**
     * Gets trimmed named value in TaggedMap or null if not found. Use this
     * if the empty string is viable value.
     * @param map TaggedMap (never null)
     * @param name
     * @return trimmed value if found otherwise <code>null</code>
     */
    @Nullable
    protected static String trimToEmpty(TaggedMap map, String name) {
        String val = map.get(name);
        return val != null ? val.trim() : null;
    }

    /**
     * Gets href value as URI if present otherwise <code>null</code> 
     * @param link NetworkLink (never null)
     * @return NetworkLink link as URI or <code>null</code> if not present 
     */
    // @CheckForNull

    public static URI getLinkUri(NetworkLink link) {
        TaggedMap links = link.getLink();
        if (links != null) {
            String href = trimToNull(links, HREF);
            if (href != null)
                try {
                    return new URI(href);
                } catch (URISyntaxException e) {
                    log.warn("Invalid link URI: " + href, e);
                }
        }

        return null;
    }

    /**
     * Override the default values for the HttpQuery parameters (e.g. clientVersion).
     * These are appended to URLs when importing NetworkLinks.
     * <P>
     * Valid values to set are the following: <ul>
     * <li> clientVersion
     * <li> kmlVersion
     * <li> clientName
     * <li> language
     * </ul>
     * <P>
     * Use cautiously and set only legal values that a Google Earth client could actually send.<BR>
     * For example, do not set KML version to value out of range or non-decimal values, etc.
     * because it could have unanticipated consequences.
     * <P>
     * See <a href="http://code.google.com/apis/kml/documentation/kmlreference.html#link">
     * <i>http://code.google.com/apis/kml/documentation/kmlreference.html#link</i></a>
     * @param property Property name, not null
     * @param value, never null
     * @throws IllegalArgumentException if property is not valid or value is empty or null.
     */
    public static void setHttpQuery(String property, String value) {
        if (!httpQueryLabels.containsKey(property))
            throw new IllegalArgumentException("invalid property: " + property);
        if (StringUtils.isBlank(value))
            throw new IllegalArgumentException("invalid property value: " + value);
        httpQueryLabels.put(property, value);
    }

   /**
     * Override the default values for the ViewFormat parameters (e.g. bboxEast).
     * These are appended to URLs when importing NetworkLinks.
     * <P>
     * Valid property names are the following: <ul>
     * <li>bboxEast
     * <li>bboxNorth
     * <li>bboxSouth
     * <li>bboxWest
     * <li>horizFov
     * <li>horizPixels
     * <li>lookatHeading
     * <li>lookatLat
     * <li>lookatLon
     * <li>lookatRange
     * <li>lookatTerrainAlt
     * <li>lookatTerrainLat
     * <li>lookatTerrainLon
     * <li>lookatTilt
     * <li>terrainEnabled
     * <li>vertFov
     * <li>vertPixels
     * </ul>
     * <P>
     * Use cautiously and set only legal values that a Google Earth client could actually send.<BR>
     * For example, do not set bounding box to values out of range or non-decimal values, etc.
     * because it could have unanticipated consequences.
     * <P>
     * See <a href="http://code.google.com/apis/kml/documentation/kmlreference.html#link">
     * <i>http://code.google.com/apis/kml/documentation/kmlreference.html#link</i></a>
     * @param property Property name, not null
     * @param value, never null
     * @throws IllegalArgumentException if property is not valid or value is empty or null.
     */
    public static void setDefaultViewFormat(String property, String value) {
        if (!VIEW_FORMAT_LABELS.containsKey(property))
            throw new IllegalArgumentException("invalid property: " + property);
        if (StringUtils.isBlank(value))
            throw new IllegalArgumentException("invalid property value: " + value);
        VIEW_FORMAT_LABELS.put(property, value);
    }

	/**
	 * Override the default ViewFormat parameters for this KmlReader instance.
	 * Valid property names are the following: <ul>
	 * <li>bboxEast
	 * <li>bboxNorth
	 * <li>bboxSouth
	 * <li>bboxWest
	 * <li>horizFov
	 * <li>horizPixels
	 * <li>lookatHeading
	 * <li>lookatLat
	 * <li>lookatLon
	 * <li>lookatRange
	 * <li>lookatTerrainAlt
	 * <li>lookatTerrainLat
	 * <li>lookatTerrainLon
	 * <li>lookatTilt
	 * <li>terrainEnabled
	 * <li>vertFov
	 * <li>vertPixels
	 * </ul>
	 * @param property Property name, not null
	 * @param value if null removes the override and used global default value instead
	 * @throws IllegalArgumentException if property is not valid or value is empty string
	 */
	public void setViewFormat(String property, String value) {
		if (!VIEW_FORMAT_LABELS.containsKey(property))
			throw new IllegalArgumentException("invalid property: " + property);
		if (value == null) value = VIEW_FORMAT_LABELS.get(property);
		if (StringUtils.isBlank(value))
			throw new IllegalArgumentException("invalid property value: " + value);
		viewFormatLabels.put(property, value);
		if (property.startsWith("bbox")) {
			// if setting bbox property then must recalculate the viewBounds
			viewBounds = null;
		}
	}

    /**
     * Get base URL of KML resource. May be null if URL is not applicable to
     * the KML resource (e.g. internal byte stream).
     */
    // @CheckForNull

    public URL getBaseUrl() {
        return baseUrl;
    }

    public String toString() {
	    return baseUrl != null ? baseUrl.toString() : super.toString();
    }
}
