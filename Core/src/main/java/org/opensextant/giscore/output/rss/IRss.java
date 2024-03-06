package org.opensextant.giscore.output.rss;

import org.opensextant.giscore.Namespace;

/**
 * Container for element and attribute names used in GeoRSS.
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: Jun 6, 2009 6:07:28 PM
 */
public interface IRss {

    Namespace GEORSS_NS = Namespace.getNamespace("georss", "http://www.georss.org/georss");
    Namespace GML_NS = Namespace.getNamespace("gml", "http://www.opengis.net/gml");

    // RSS Elements
    // see http://cyber.law.harvard.edu/rss/rss.html
    String AUTHOR = "author";
    String CATEGORY = "category";
    String CHANNEL = "channel";
    String COMMENTS = "comments";
    String DESCRIPTION = "description";
    String GUID = "guid";
    String IMAGE = "image";
    String ITEM = "item";
    String LANGUAGE = "language";
    String LINK = "link";
    String PUB_DATE = "pubDate";
    String RSS = "rss";
    String SOURCE = "source";
    String TITLE = "title";

    // GeoRSS elements namespace="http://www.georss.org/georss"
    // see http://www.georss.org/xml/1.1/georss.xsd

    String CIRCLE = "circle";
    String ELEV = "elev";
    String FEATURE_NAME = "featureName";
    String FEATURE_TYPE_TAG = "featureTypeTag";
    String LINE = "line";
    String POINT = "point";
    String POLYGON = "polygon";
    String RADIUS = "radius";
    String RELATIONSHIP_TAG = "relationshipTag";

}