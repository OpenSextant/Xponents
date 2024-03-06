/****************************************************************************************
 *  IKml.java
 *
 *  Created: Feb 2, 2009
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
package org.opensextant.giscore.input.kml;

/**
 * Container for element and attribute names used in KML
 *
 * @author DRAND
 */
public interface IKml {
	String KML_NS = "http://www.opengis.net/kml/2.2";

	// default SimpleDateFormat format pattern to parse/format dates
    String ISO_DATE_FMT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    String NS_GOOGLE_KML_EXT_PREFIX = "http://www.google.com/kml/ext/";
    String NS_GOOGLE_KML_EXT = "http://www.google.com/kml/ext/2.2";
    String NS_OASIS_XAL = "urn:oasis:names:tc:ciq:xsdschema:xAL:2.0";

	String ADDRESS_DETAILS = "AddressDetails";
	String ADDRESS = "address";
	String ALTITUDE = "altitude";
	String ALTITUDE_MODE = "altitudeMode";
	String BALLOON_STYLE = "BalloonStyle";
	String BEGIN = "begin";
	String BG_COLOR = "bgColor";
	String CAMERA = "Camera";
	String COLOR = "color";
	String COLOR_MODE =  "colorMode";
	String COORDINATES = "coordinates";
	String DATA = "Data";
	String DESCRIPTION = "description";
	String DISPLAY_MODE = "displayMode";
	String DISPLAY_NAME = "displayName";
	String DOCUMENT = "Document";
	String DRAW_ORDER = "drawOrder";
	String EAST = "east";
	String END = "end";
	String EXTENDED_DATA = "ExtendedData";
	String EXTRUDE = "extrude";
	String FILL = "fill";
	String FLY_TO_VIEW = "flyToView";
	String FOLDER = "Folder";
	String GROUND_OVERLAY = "GroundOverlay";
	String HEADING = "heading";
	String HOT_SPOT = "hotSpot";
	String HREF = "href";
	String HTTP_QUERY = "httpQuery";
	String ICON = "Icon";
	String ICON_STYLE = "IconStyle";
	String ID = "id";
	String INNER_BOUNDARY_IS = "innerBoundaryIs";
	String KEY = "key";
	String KML = "kml";
	String LABEL_STYLE = "LabelStyle";
	String LATITUDE = "latitude";
	String LAT_LON_ALT_BOX = "LatLonAltBox";
	String LAT_LON_BOX = "LatLonBox";
	String LINEAR_RING = "LinearRing";
	String LINE_STRING = "LineString";
	String LINE_STYLE = "LineStyle";
	String LINK = "Link";
	String LIST_ITEM_TYPE = "listItemType";
	String LIST_STYLE = "ListStyle";
	String LOD = "Lod";
	String LOCATION = "Location";
	String LONGITUDE = "longitude";
	String LOOK_AT = "LookAt";
	String MAX_ALTITUDE = "maxAltitude";
	String MAX_FADE_EXTENT = "maxFadeExtent";
	String MAX_LOD_PIXELS = "maxLodPixels";
	String METADATA = "Metadata";
	String MIN_ALTITUDE = "minAltitude";
	String MIN_FADE_EXTENT = "minFadeExtent";
	String MIN_LOD_PIXELS = "minLodPixels";
	String MODEL = "Model";
	String MULTI_GEOMETRY = "MultiGeometry";
	String NAME = "name";
	String NETWORK_LINK = "NetworkLink";
	String NETWORK_LINK_CONTROL = "NetworkLinkControl";
	String NORTH = "north";
	String OPEN = "open";
	String OUTER_BOUNDARY_IS = "outerBoundaryIs";
	String OUTLINE = "outline";
	String OVERLAY_XY = "overlayXY";
	String PAIR = "Pair";
	String PARENT = "parent"; // old-style KML 2.0 Schema attribute removed from KML 2.1
	String PHONE_NUMBER = "phoneNumber";
	String PHOTO_OVERLAY = "PhotoOverlay"; // new in KML 2.2
	String PLACEMARK = "Placemark";
	String POINT = "Point";
	String POLYGON = "Polygon";
	String POLY_STYLE = "PolyStyle";
	String RANGE = "range";
	String REFRESH_INTERVAL = "refreshInterval";
	String REFRESH_MODE = "refreshMode";
	String REFRESH_VISIBILITY = "refreshVisibility";
	String REGION = "Region";
	String ROLL = "roll";
	String ROTATION = "rotation";
	String ROTATION_XY = "rotationXY";
	String SCALE = "scale";
	String SCHEMA = "Schema";
	String SCHEMA_DATA = "SchemaData";
	String SCHEMA_URL = "schemaUrl";
	String SCREEN_OVERLAY = "ScreenOverlay";
	String SCREEN_XY = "screenXY";
	String SIMPLE_DATA = "SimpleData";
	String SIMPLE_FIELD = "SimpleField";
	String SIZE = "size";
	String SNIPPET = "snippet"; // (*) Snippet is deprecated in KML 2.2
	String SOUTH = "south";
	String STYLE = "Style";
	String STYLE_MAP = "StyleMap";
	String STYLE_URL = "styleUrl";
	String TESSELLATE = "tessellate";
	String TEXT = "text";
	String TEXT_COLOR = "textColor";
	String TILT = "tilt";
	String TIME_SPAN = "TimeSpan";
	String TIME_STAMP = "TimeStamp";
	String TYPE = "type";
	String URL = "Url"; // (*) attribute deprecated in KML 2.1
	String VALUE = "value";
	String VIEW_BOUND_SCALE = "viewBoundScale";
	String VIEW_FORMAT = "viewFormat";
	String VIEW_FORMAT_DEFAULT = "BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]";
	String VIEW_REFRESH_MODE = "viewRefreshMode";
	String VIEW_REFRESH_TIME = "viewRefreshTime";
	String VISIBILITY = "visibility";
	String WEST = "west";
	String WHEN = "when";
	String WIDTH = "width";

	// values for kml:viewRefreshMode
    String VIEW_REFRESH_MODE_NEVER = "never"; // default
	String VIEW_REFRESH_MODE_ON_STOP = "onStop";
	String VIEW_REFRESH_MODE_ON_REQUEST = "onRequest";
	String VIEW_REFRESH_MODE_ON_REGION = "onRegion";

	// values for kml:refreshMode
    String REFRESH_MODE_ON_CHANGE = "onChange"; // default
	String REFRESH_MODE_ON_INTERVAL = "onInterval";
	String REFRESH_MODE_ON_EXPIRE = "onExpire";

	// Google KML extensions xmlns:gx="http://www.google.com/kml/ext/2.2"
    String MULTI_TRACK = "MultiTrack";
	String TRACK = "Track";

    // ViewFormat parameters
    // see http://code.google.com/apis/kml/documentation/kmlreference.html#link
    String BBOX_EAST = "bboxEast";
    String BBOX_NORTH = "bboxNorth";
    String BBOX_SOUTH = "bboxSouth";
    String BBOX_WEST = "bboxWest";
    String HORIZ_FOV = "horizFov";
    String HORIZ_PIXELS = "horizPixels";
    String LOOKAT_HEADING = "lookatHeading";
    String LOOKAT_LAT = "lookatLat";
    String LOOKAT_LON = "lookatLon";
    String LOOKAT_RANGE = "lookatRange";
    String LOOKAT_TERRAIN_ALT = "lookatTerrainAlt";
    String LOOKAT_TERRAIN_LAT = "lookatTerrainLat";
    String LOOKAT_TERRAIN_LON = "lookatTerrainLon";
    String LOOKAT_TILT = "lookatTilt";
    String TERRAIN_ENABLED = "terrainEnabled";
    String VERT_FOV = "vertFov";
    String VERT_PIXELS = "vertPixels";

}
