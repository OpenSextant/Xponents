package org.opensextant.giscore.output.rss;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensextant.geodesy.FrameOfReference;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.Comment;
import org.opensextant.giscore.events.Common;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Overlay;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.output.XmlOutputStreamBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GeoRSS output stream creates a RSS 2.0 document with GeoRSS-Simple locations
 * using the given output stream.  It uses STaX methods for writing the XML elements to
 * avoid building an in-memory DOM, which reduces the memory overhead of creating the document.
 * <p>
 * {@code GeoRSSOutputStream} produces a valid GeoRSS XML document wrt the RSS 2.0
 * specification and GeoRSS-Simple encodings.
 * <p>
 * Related Resources:
 * <p>  <a href="http://en.wikipedia.org/wiki/RSS_(file_format)">...</a>
 * <br/> <a href="http://cyber.law.harvard.edu/rss/rss.html">...</a>
 * <br/> <a href="http://www.w3schools.com/rss/default.asp">...</a>
 * <br/> <a href="http://www.georss.org/simple">...</a>
 * <br/> <a href="http://en.wikipedia.org/wiki/GeoRSS">...</a>
 * <p>
 * Related XML Schemas:
 * <p> <a href="http://www.thearchitect.co.uk/schemas/rss-2_0.xsd">...</a>
 * <p> <a href="http://www.georss.org/xml/1.1/georss.xsd">...</a>
 * <p> <a href="http://www.windsorsolutions.biz/xsd/ENGeoTF/gmlgeorss11.xsd">...</a>
 * <p>
 * <b>Notes/Limitations:</b>
 * <p>
 * -Handles all basic GeoRSS-simple shapes (Point, Line, Polygon, Circle).<br/>
 * -Current georss-Simple spec, however, does not have a native collection/multigeometry
 * feature (though one is proposed) so for now the more complex geometries (MultiPoint,
 * MultiLine, MultiLinearRings, MultiPolygons) simply output the first geometry in the
 * group. Proposed fix would be to output gml features for those shapes.<br/>
 * -GeoRSS-Simple doesn't really have a one-to-one mapping for a LinearRing so
 * using a georss:line for now.
 *
 * @author Jason Mathews, MITRE Corp.
 *         Date: Jun 6, 2009 5:50:46 PM
 */
public class GeoRSSOutputStream extends XmlOutputStreamBase implements IRss {

	private static final Logger log = LoggerFactory.getLogger(GeoRSSOutputStream.class);

	// All date-times in RSS conform to the Date and Time Specification of RFC 822
	// e.g. Sat, 07 Sep 2002 21:00:01 GMT
	private final SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");

	private final Map<String, Namespace> namespaceMap;

	/**
     * Creates a {@code GeoRSSOutputStream} that uses the specified underlying OutputStream.
     *
     * @param stream       the underlying output stream.
     * @param encoding     the encoding to use
     * @param namespaceMap Mapping of non-RSS element names (can appear in channel
     *                     properties as passed in the channelMap argument or in items
     *                     as extended data) to explicit namespaces.  Extended data properties in Features
     *                     are checked against this mapping.  If no mapping exists for a given property
     *                     name then it is assumed to be part of the RssChannel or RssItem definition
     *                     otherwise the RSS may not be valid.
     * @param channelMap   simple child elements that are added to the channel element
     *                     (e.g. title, link, category, etc. or user-defined ones if namespace
     *                     mapping is provided).  Note that the title, link, and description
     *                     are considered require elements for the channel.
     *                     See <a href="http://cyber.law.harvard.edu/rss/rss.html#requiredChannelElements">...</a>
     * @throws XMLStreamException if there is an error with the underlying XML
     */
	public GeoRSSOutputStream(OutputStream stream, String encoding, Map<String, Namespace> namespaceMap,
							  Map<String, Object> channelMap) throws XMLStreamException {
		super(stream, encoding);
		// use "ISO-8859-1" encoding if using any non-UTF-8 characters in content
		dateFormatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		this.namespaceMap = namespaceMap;
		if (StringUtils.isBlank(encoding))
			writer.writeStartDocument(); // use default encoding
		else
			writer.writeStartDocument(encoding, "1.0");
		writer.writeCharacters("\n");
		writer.writeStartElement(RSS);
		writer.writeAttribute("version", "2.0");
		writeNamespace(GEORSS_NS);
		//writeNamespace(GML_NS);

		// dump all user-defined namespaces
		if (namespaceMap != null && !namespaceMap.isEmpty()) {
			List<Namespace> visited = new ArrayList<>();
			visited.add(GEORSS_NS);
			//visited.add(GML_NS);
			for (Namespace ns : namespaceMap.values()) {
				if (ns != null && !visited.contains(ns)) {
					writeNamespace(ns);
					visited.add(ns);
				}
			}
		}
		writer.writeCharacters("\n");
		writer.writeStartElement(CHANNEL);
		writer.writeCharacters("\n");

		// enumerate all channelMap elements
		if (channelMap != null)
			for (Map.Entry<String, Object> entry : channelMap.entrySet()) {
				Object value = entry.getValue();
				if (value != null) {
					String textVal = value instanceof Date
							? dateFormatter.format((Date) value) : value.toString();
					String name = entry.getKey();
					Namespace ns = namespaceMap == null ? null : namespaceMap.get(name);
					handleSimpleElement(ns, name, textVal);
				}
			}
	}

	/**
     * Creates a {@code GeoRSSOutputStream} that uses the specified underlying OutputStream.
     *
     * @param stream       the underlying output stream.
     * @param namespaceMap Mapping of non-RSS element names (can appear in channel
     *                     properties as passed in the channelMap argument or in items
     *                     as extended data) to explicit namespaces.  Extended data properties in Features
     *                     are checked against this mapping.  If no mapping exists for a given property
     *                     name then it is assumed to be part of the RssChannel or RssItem definition
     *                     otherwise the RSS may not be valid.
     * @param channelMap   simple child elements that are added to the channel element
     *                     (e.g. title, link, category, etc. or user-defined ones if namespace
     *                     mapping is provided).  Note that the title, link, and description
     *                     are considered require elements for the channel.
     *                     See <a href="http://cyber.law.harvard.edu/rss/rss.html#requiredChannelElements">...</a>
     * @throws XMLStreamException if there is an error with the underlying XML
     */
	public GeoRSSOutputStream(OutputStream stream, Map<String, Namespace> namespaceMap,
							  Map<String, Object> channelMap) throws XMLStreamException {
		this(stream, null, namespaceMap, channelMap);
	}

	/*
	* (non-Javadoc)
	*
	* @see org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events.Feature
	*/
	@Override
	public void visit(Feature feature) {
		try {
			writer.writeStartElement(ITEM);
			writer.writeCharacters("\n");
			handleAttributes(feature);
			if (feature instanceof Overlay) {
				handleOverlay((Overlay) feature);
			}
			if (feature.getGeometry() != null) {
				//log.debug("Visit " + feature.getName());
				feature.getGeometry().accept(this);
			}
			writer.writeEndElement();
			writer.writeCharacters("\n");
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handle the output of a point
	 *
	 * @param point the point, never {@code null}
	 */
	@Override
	public void visit(Point point) {
		try {
			Geodetic2DPoint pt = point.getCenter();
			handleSimpleElement(GEORSS_NS, POINT,
					handleSingleCoordinate(pt).toString());
			if (pt instanceof Geodetic3DPoint) {
				Geodetic3DPoint p3d = (Geodetic3DPoint) pt;
				handleSimpleElement(GEORSS_NS, ELEV, formatDouble(p3d.getElevation()));
			}
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handle the output of a line
	 *
	 * @param l the line, never {@code null}
	 */
	@Override
	public void visit(Line l) {
		try {
			/*
              A line contains a space separated list of latitude-longitude pairs
              in WGS84 coordinate reference system, with each pair separated by
              whitespace. There must be at least two pairs.
             */
			handleSimpleElement(GEORSS_NS, LINE, handleCoordinates(l.getPoints()));
			Geodetic2DPoint center = l.getCenter();
			if (center instanceof Geodetic3DPoint) {
				Geodetic3DPoint p3d = (Geodetic3DPoint) center;
				handleSimpleElement(GEORSS_NS, ELEV, formatDouble(p3d.getElevation()));
			}
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handle the output of a circle
	 *
	 * @param circle the circle, never {@code null}
	 */
	@Override
	public void visit(Circle circle) {
		try {
			Geodetic2DPoint center = circle.getCenter();
			handleSimpleElement(GEORSS_NS, CIRCLE, handleSingleCoordinate(center).toString());
			handleSimpleElement(GEORSS_NS, RADIUS, formatDouble(circle.getRadius()));
			if (center instanceof Geodetic3DPoint) {
				Geodetic3DPoint p3d = (Geodetic3DPoint) center;
				handleSimpleElement(GEORSS_NS, ELEV, formatDouble(p3d.getElevation()));
			}
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handle the output of a polygon
	 *
	 * @param poly the polygon, never {@code null}
	 */
	@Override
	public void visit(Polygon poly) {
		try {
            /*
              use simple georss:polygon where a polygon contains a closed ring
              property element containing a list of pairs of coordinates (first
              pair and last pair identical) representing latitude then longitudex
              pair the WGS84 coordinate reference system.
             */
			List<Point> points = poly.getOuterRing().getPoints();
			/*
			// NOTE: LinearRing now auto-adds first first if not same as last
            if (points.size() > 1 && !points.get(0).equals(points.get(points.size() - 1))) {
                List<Point> newPoints = new ArrayList<Point>(points.size() + 1);
                newPoints.addAll(points);
                newPoints.add(points.get(0));
                points = newPoints;
            }
            */
			handleSimpleElement(GEORSS_NS, POLYGON, handleCoordinates(points));
			Geodetic2DPoint center = poly.getCenter();
			if (center instanceof Geodetic3DPoint) {
				Geodetic3DPoint p3d = (Geodetic3DPoint) center;
				handleSimpleElement(GEORSS_NS, ELEV, formatDouble(p3d.getElevation()));
			}
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handle the output of a ring.
	 * For now just encodes as a GeoRSS-simple line.
	 *
	 * @param ring the ring, never {@code null}
	 */
	@Override
	public void visit(LinearRing ring) {
		// todo: encode geom in georss or gml. possibly gml:MultiLineString ??
		// for now just dump as a georss-simple line
		//visit(new Comment("Ignore LinearRing\n" + ring)); // placeholder
		try {
            /*
              A line contains a space separated list of latitude-longitude pairs
              in WGS84 coordinate reference system, with each pair separated by
              whitespace. There must be at least two pairs.
             */
			handleSimpleElement(GEORSS_NS, LINE, handleCoordinates(ring.getPoints()));
			Geodetic2DPoint center = ring.getCenter();
			if (center instanceof Geodetic3DPoint) {
				Geodetic3DPoint p3d = (Geodetic3DPoint) center;
				handleSimpleElement(GEORSS_NS, ELEV, formatDouble(p3d.getElevation()));
			}
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handle the output of a Model as a georss:point
	 *
	 * @param model the model, never {@code null}
	 */
	@Override
	public void visit(Model model) {
		visit(new Point(model.getCenter()));
		// todo: other metadata to dump; e.g. link href ??
	}

	/**
	 * Handle the output of a MultiPoint.
	 * For now just encodes the center point line as a single GeoRSS-simple point.
	 *
	 * @param multiPoint the MultiPoint, never {@code null}
	 */
	@Override
	public void visit(MultiPoint multiPoint) {
		// todo: no collection grouping in georss-simple so must use gml:MultiPoint as a collection of gml:Point elements
		// for now just visit the center of the group
		//visit(new Comment("Ignore MultiPoint\n" + multiPoint)); // placeholder
		visit(new Point(multiPoint.getCenter()));
	}

	/**
	 * Handle the output of a MultiLine.
	 * For now just encodes the first line as a GeoRSS-simple line.
	 *
	 * @param multiLine the MultiLine, never {@code null}
	 */
	@Override
	public void visit(MultiLine multiLine) {
		// no collection grouping in georss-simple so must use gml:MultiLineString
		// as a collection of gml:LineString elements
		// for now just visit the first line in the group
		//visit(new Comment("Ignore MultiLine\n" + multiLine)); // placeholder
		Iterator<Line> it = multiLine.getLines().iterator();
		if (it.hasNext()) visit(it.next());
    /*
        boolean oldGmlMode = gmlMode;
        try {
            writer.writeStartElement(GML_NS.getPrefix(), "MultiLineString", GML_NS.getURI());
            gmlMode = true;
            // geoRss-simple doesn't have collection we we go into GML mode
            super.visit(multiLine);
            writer.writeEndElement();
            writer.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            } finally {
                gmlMode = oldGmlMode;
            }
    */
	}

	/**
	 * Handle the output of a MultiLinearRings.
	 * For now just encodes the first ring as a GeoRSS-simple line.
	 *
	 * @param rings the MultiLinearRings, never {@code null}
	 */
	@Override
	public void visit(MultiLinearRings rings) {
		// todo: no collection grouping in georss-simple so must use gml:MultiGeometry or MultiPolygon
		// as a collection. for now just visit the first in the group
		//visit(new Comment("Ignore MultiLinearRings\n" + rings)); // placeholder
		Iterator<LinearRing> it = rings.getLinearRings().iterator();
		if (it.hasNext()) visit(it.next());
        /*
        try {
            // MultiGeometry or MultiPolygon ??
            writer.writeStartElement(GML_NS.getPrefix(), "MultiGeometry", GML_NS.getURI());
            super.visit(rings);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        */
	}

	/**
	 * Handle the output of a MultiPolygons.
	 * For now just encodes the first polygon as a GeoRSS-simple polygon.
	 *
	 * @param polygons the MultiPolygons, never {@code null}
	 */
	@Override
	public void visit(MultiPolygons polygons) {
		// no collection grouping in georss-simple so must use gml:MultiPolygon
		// as a collection. for now just visit the first in the group
		//visit(new Comment("Ignore MultiPolygons\n" + polygons)); // placeholder
		Iterator<Polygon> it = polygons.getPolygons().iterator();
		if (it.hasNext()) visit(it.next());
        /*
        boolean oldGmlMode = gmlMode;
        try {
            writer.writeStartElement(GML_NS.getPrefix(), "MultiPolygon", GML_NS.getURI());
            gmlMode = true;
            super.visit(polygons);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } finaly {
		gmlMode = oldGmlMode;
        }
        */
	}

	/**
	 * Output a gml:multigeometry, represented by a geometry bag.
	 * For now just outputs the first geometry with GeoRSS-Simple encoding.
	 *
	 * @param bag the geometry bag
	 */
	@Override
	public void visit(GeometryBag bag) {
		List<Geometry> geoms = new ArrayList<>();
		addGeometry(geoms, bag);
		if (geoms.size() > 1) {
			/*
			 * If first geometry is a Point and is in center of bounding box for other geometries then
			 * remove by convention.
			 */
			Geometry firstGeom = geoms.get(0); // items cannot be null
			Class<? extends Geometry> firstClass = firstGeom.getClass(); // class cannot be null
			if (firstClass == Point.class) {
				Geodetic2DBounds bbox = null;
				int n = geoms.size();
				boolean homogeneous = true;
				for (int i = 1; i < n; i++) {
					Geometry g = geoms.get(i);
					// geoms cannot have null entries since we're adding them in addGeometry() above
					if (g.getClass() != firstClass) {
						homogeneous = false;
						log.debug("multi geometries not homogeneous: drop initial point");
						// if geometries not homogeneous and first geometry is point
						// it is OK to just ignore the initial Point if present, by convention
						geoms.remove(0);
						break;
					}
					Geodetic2DPoint center = g.getCenter();
					if (bbox == null) bbox = new Geodetic2DBounds(center);
					else bbox.include(center);
				}
				if (homogeneous && bbox != null) {
					if (new FrameOfReference().proximallyEquals(firstGeom.getCenter(), bbox.getCenter())) {
						log.debug("multi geometries homogeneous: drop initial point");
						// OK to just ignore the initial Point if present, by convention
						geoms.remove(0);
					} else {
						log.debug("Multi-geometries are homogeneous");
					}
				}
			}
		}

		if (geoms.size() == 1) {
			visit(geoms.get(0));
		} else if (!geoms.isEmpty()) {
			// check if we have two geometries and one is point.
			// drop it it it's the center point of the other.
            /*
            if (geoms.size() == 2) {
                Point point = null;
                Geometry other = null;
                for (Geometry g : geoms) {
                    if (g instanceof Point && point == null) {
                        point = (Point)g;
                    } else
                        other = g;
                }
                if (point != null && other != null && point.asGeodetic2DPoint().equals(other.getCenter())) {
                    //visit(new Comment("XXX: multiGeom Point and other Geom"));//test
                    visit(other);
                    return;
                } //else visit(new Comment("XXX: checked multiGeom for Point and other Geom\n" + point + "\n" + other));//test
            }
            */
			Geometry firstGeom = geoms.remove(0);
			visit(firstGeom); // first first item as georss-simple

			//todo: implement as gml:mutligeom
            /*
            StringBuilder sb = new StringBuilder();
            for (Geometry g : geoms) {
                sb.append('\t').append(g).append('\n');
            }
            visit(new Comment("Ignore MultiGeometry:\n" + sb.toString())); // placeholder
            */

            /*
            try {
            writer.writeStartElement(GML_NS.getPrefix(), "MultiGeometry", GML_NS.getURI());
                gmsmode = true
            writer.writeEndElement();
            writer.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            } finally {
            gmsmode = false
            */
		}
		// if zero do nothing
	}

	/**
	 * Catch-all to figure out which visit() method to invoke.
	 *
	 * @param g geometry, never {@code null}
	 */
	private void visit(Geometry g) {
		if (g == null) return;
		if (g instanceof Circle) {
			// note test for Circle must preceed Point since Circle subclasses Point
			visit((Circle) g);
		} else if (g instanceof Point)
			visit((Point) g);
		else if (g instanceof Line)
			visit((Line) g);
		else if (g instanceof LinearRing)
			visit((LinearRing) g);
		else if (g instanceof Polygon)
			visit((Polygon) g);
		else if (g instanceof Model) {
			Model model = (Model) g;
			if (model.getLocation() != null)
				visit(model);
		} else if (g instanceof MultiPoint)
			visit((MultiPoint) g);
		else if (g instanceof MultiLine)
			visit((MultiLine) g);
		else if (g instanceof MultiLinearRings)
			visit((MultiLinearRings) g);
		else if (g instanceof MultiPolygons)
			visit((MultiPolygons) g);
		else {
			// all geometries should be handled above. Possibly a new Geometry was created after this OutputStream
			// was created.
			log.info("unhandled geometry: " + g);
			// visit(new Comment("Ignore geometry:\n" + g)); // placeholder
		}
	}

	private void addGeometry(List<Geometry> geoms, Geometry geom) {
		if (geom instanceof GeometryBag) {
			for (Geometry geo : (GeometryBag) geom) {
				// recurse all GeometryBag containers for all non-null simple Geometry objects
				addGeometry(geoms, geo);
			}
		} else if (geom != null) {
			if (geom instanceof Model) {
				Model model = (Model) geom;
				if (model.getLocation() != null)
					geoms.add(geom);
			} else {
				// Point, Circle, Polygon, etc.
				geoms.add(geom);
			}
		}
	}

	private void handleOverlay(Overlay overlay) throws XMLStreamException {
        /*
        if (overlay instanceof GroundOverlay) {
            GroundOverlay go = (GroundOverlay)overlay;
            TaggedMap icon = go.getIcon();
            if (icon != null) {
                String href = icon.get("href");
                if (StringUtils.isNotBlank(href)) {
                    //writer.writeStartElement("link");
                    handleSimpleElement("link", href);
                    //handleNonNullSimpleElement("width", icon.get("width"));
                    //handleNonNullSimpleElement("height", icon.get("height"));
                    //writer.writeEndElement();
                    //writer.writeCharacters("\n");
                    return;
                }
            }
        }
       */
		// todo encode geom and/or overlay image in georss or gml
		visit(new Comment("Ignore Overlay\n" + overlay)); // placeholder
	}

	/**
	 * Common code for outputting feature data that is held for both containers
	 * and other features used for RSS Items.
	 *
	 * @param feature Common feature object for whom attributes will be written
	 */
	private void handleAttributes(Common feature) {
		try {
			handleNonNullSimpleElement(TITLE, feature.getName());
			handleNonNullSimpleElement(DESCRIPTION, feature.getDescription());
			// use feature startTime or endTime as pubDate ??
			// for now requires explicit pubDate extended data field
			if (feature.getFieldSize() != 0) {
				for (Map.Entry<SimpleField, Object> entry : feature.getEntrySet()) {
					Object value = entry.getValue();
					if (value != null && !ObjectUtils.NULL.equals(value)) {
						String textVal = value instanceof Date ? dateFormatter.format((Date) value) : value.toString();
						String name = entry.getKey().getName();
						Namespace ns = namespaceMap == null ? null : namespaceMap.get(name);
						handleSimpleElement(ns, name, textVal); // treat as RSS element
						//writer.writeCharacters("\n");
					}
				}
			}
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * output the coordinates. The coordinates are output as lon,lat[,altitude]
	 * and are separated by spaces
	 *
	 * @param coordinateList the list of coordinates, never {@code null}
	 * @return String formatted list of coordinate points
	 */
	private String handleCoordinates(Collection<Point> coordinateList) {
		StringBuilder b = new StringBuilder();
		for (Point point : coordinateList) {
			handleSingleCoordinate(b, point.getCenter());
		}
		return b.toString();
	}

	/**
	 * Output a single coordinate
	 *
	 * @param b  StringBuilder to write coordinate to
	 * @param pt Point to be formatted for output
	 * @return formatted coordinate string
	 */
	private StringBuilder handleSingleCoordinate(StringBuilder b, Geodetic2DPoint pt) {
		if (b.length() > 0) {
			b.append(' ');
		}
		b.append(formatDouble(pt.getLatitudeAsDegrees()));
		b.append(' ');
		b.append(formatDouble(pt.getLongitudeAsDegrees()));
		return b;
	}

	private StringBuilder handleSingleCoordinate(Geodetic2DPoint pt) {
		return handleSingleCoordinate(new StringBuilder(), pt);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.opensextant.giscore.output.XmlOutputStreamBase#close()
	 */
	@Override
	public void close() throws IOException {
		try {
			writer.writeEndElement(); // channel
			writer.writeEndElement(); // rss
			writer.writeCharacters("\n");
			writer.writeEndDocument();
		} catch (XMLStreamException e) {
			final IOException e2 = new IOException(e);
			throw e2;
		} finally {
			super.close();
		}
	}

}