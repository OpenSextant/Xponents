/****************************************************************************************
 *  KmlInputStream.java
 *
 *  Created: Jan 26, 2009
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
package org.opensextant.giscore.input.kml;


import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.input.XmlInputStream;
import org.opensextant.giscore.utils.Color;
import org.opensextant.giscore.utils.DateTime;
import org.opensextant.giscore.utils.NumberStreamTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read a Google Earth Keyhole Markup Language (KML) file in as an input stream
 * one event at a time.
 * <P>Supports KML 2.0 through KML 2.2 data formats with allowance
 * for sloppy or lax KML files as would be allowed in Google Earth. Limited
 * support is provided for Google's KML Extensions with gx prefix and
 * <code><a href="http://www.google.com/kml/ext/2.2">...</a></code> namespace .
 * Strict conformance to the KML 2.2 specification
 * is maintained in output in the associated {@link org.opensextant.giscore.output.kml.KmlOutputStream}
 * class. In doing so some "legacy" KML 2.0 or KML 2.1 conventions may be
 * normalized into the equivalent 2.2 form or dropped if not supported.
 * <P>Each time the read method is called,
 * the code tries to read a single event's worth of data. Generally that is one
 * of the following events:
 * <ul>
 * <li>A new container returning a <code>ContainerStart</code> object
 * <li>Exiting a container returning a <code>ContainerEnd</code> object
 * <li>A new feature returning a <code>Feature</code> object
 * </ul>
 * <p>
 * Supports KML Placemark, GroundOverlay, ScreenOverlay, NetworkLink, Document, and Folder elements with
 * limited/partial support for the lesser used NetworkLinkControl(<A href="#NetworkLinkControl">*</A>),
 * and PhotoOverlay(<A href="#PhotoOverlay">*</A>) elements.
 * <p>
 * Geometry support includes: Point, LineString, LinearRing, Polygon, MultiGeometry, and Model(<A href="#Model">*</A>).
 * <p>
 * Supported KML properties include: name, address(<A href="#address">*</A>), description, open, visibility,
 * Camera/LookAt, atom:author, atom:link, xal:AddressDetails, phoneNumber(<A href="#address">*</A>), styleUrl,
 * inline/shared Styles, Region, Snippet, snippet, ExtendedData(<A href="#ExtendedData">*</A>),
 * Schema, TimeStamp/TimeSpan elements in addition to the geometry are parsed
 * and set on the Feature object.
 * <p>
 * Style and StyleMap supported on Features (Placemarks, Documents, Folders, etc.)
 * with IconStyle, PolyStyle, ListStyle, etc.
 * {@code StyleMaps} with inline Styles are supported.
 * StyleMaps must specify {@code styleUrl} and/or inline Style. Nested StyleMaps are not supported.
 * <p>
 * If elements (e.g. {@code Style} or {@code StyleMap}) are out of order on input
 * that same order is preserved in the order of elements returned in {@link #read()}.
 * For example, if Style/StyleMap elements appear incorrectly out of order such as
 * after the firstFeature element then it will likewise be out of order. This will
 * not be corrected by KmlWriter or KmlOutputStream, which assumes the caller writes
 * those elements in a valid order. It should still work with Google Earth but it
 * will not conform to the KML 2.2 spec.
 * <p>
 * <p>
 * If debug logging mode is enabled for this class, the following additional validations will be checked
 * and logged as warnings:
 * <ul>
 * <li> ATC 3: Geometry coordinates:
 * Whitespace found within coordinate tuple
 * </li>
 * <li> ATC 14: Point [OGC-07-147r2: cl. 10.3.2]:
 * Check that the kml:coordinates element in a kml:Point geometry contains exactly one coordinate tuple.
 * </li>
 * <li>ATC 15: LineString [OGC-07-147r2: cl. 10.7.3.4.1]:
 * Verify that the kml:coordinates element in a kml:LineString geometry contains at least two coordinate tuples.
 * </li>
 * <li>ATC 16: LinearRing - control points:
 * LinearRing geometry must contain at least 4 coordinate tuples, where the first and last are identical.
 * </li>
 * <li>ATC 26: Schema - SimpleField:
 * Verify the SimpleField value of the 'type' attribute is one of the allowable data types
 * </li>
 * </ul>
 * <p>
 * <b>Notes/Limitations:</b>
 * <ul>
 * <li> The actual handling of containers and other features has some uniform
 * methods. Every feature in KML can have a set of common attributes and
 * additional elements.
 * </li>
 * <li> Geometry is handled by common code as well. All coordinates in KML are
 * transmitted as tuples of two or three elements. The formatting of these is
 * consistent and is handled internally. {@code Tessellate},
 * {@code extrude}, {@code altitudeMode} and {@code gx:drawOrder} properties are maintained
 * on the associated Geometry.
 * </li>
 * <li>phoneNumber and address fields do not explicitly exist on the Feature
 * or Common object but are added as an {@link Element} with the
 * {@code http://www.opengis.net/kml/2.2} namespace. Access by calling {@link
 *  Feature#getElements() getElements()} on the feature.
 * </li>
 * <li> <b>ExtendedData</b>
 * Handles ExtendedData with Data/Value or SchemaData/SimpleData elements including the non-KML namespace
 * form of extended data for arbitrary XML data (see http://code.google.com/apis/kml/documentation/extendeddata.html#opaquedata).
 * Only a single {@code Data/SchemaData/Schema ExtendedData} mapping is assumed
 * per Feature but note that although uncommon, KML allows features to define multiple
 * Schemas. Features with mixed {@code Data} and/or multiple {@code SchemaData} elements
 * will be associated only with the last {@code Schema} referenced.
 * </li>
 * <li> Unsupported deprecated tags include: {@code Metadata}, which is consumed but ignored.
 * </li>
 * <li> Some support for gx KML extensions (e.g. Track, MultiTrack, Tour, etc.). Also {@code gx:altitudeMode}
 * is handle specially and stored as a value of the {@code altitudeMode} in LookAt, Camera, Geometry,
 * and GroundOverlay.
 * </li>
 * <li> <b>PhotoOverlay</b>
 * Limited support for {@code PhotoOverlay} which creates an basic overlay object
 * with Point and rotation without retaining other PhotoOverlay-specific properties
 * (ViewVolume, ImagePyramid, or shape).
 * </li>
 * <li> <b>Model</b>
 * Limited support for {@code Model} geometry type. Keeps only location and altitude
 * properties.
 * </li>
 * <li> <b>"NetworkLinkControl"</b>
 * Limited support for {@code NetworkLinkControl} which creates an object wrapper for the link
 * with the top-level info but the update details (i.e. Create, Delete, and Change) are discarded.
 * </li>
 * <li> IconStyle and LineStyle are fully supported except for colorMode = random which is ignored.
 *   Only PolyStyle has support for the random colorMode.
 * </li>
 * <li> Allows timestamps to omit seconds field as does Google Earth. Strict XML schema validation requires
 * seconds field in the dateTime ({@code YYYY-MM-DDThh:mm:ssZ}) format but Google Earth is lax in its
 * parsing rules. Likewise allows the 'Z' suffix to be omitted in which case it defaults to UTC.
 * </li>
 * </ul>
 *
 * @author J.Mathews
 */
public class KmlInputStream extends XmlInputStream implements IKml {

	public static final Logger log = LoggerFactory.getLogger(KmlInputStream.class);

	private static final Pattern WHITESPACE_PAT = Pattern.compile(",\\s+\\.?\\d");

	private static final Set<String> ms_kml_ns = new HashSet<>(8);

	static {
		ms_kml_ns.add("http://earth.google.com/kml/2.0");
		ms_kml_ns.add("http://earth.google.com/kml/2.1");
		ms_kml_ns.add("http://earth.google.com/kml/2.2");
		ms_kml_ns.add("http://earth.google.com/kml/2.3");
		ms_kml_ns.add("http://earth.google.com/kml/3.0");

		ms_kml_ns.add("http://www.opengis.net/kml/2.2"); // this is the default
		ms_kml_ns.add("http://www.opengis.net/kml/2.3");
		ms_kml_ns.add("http://www.opengis.net/kml/3.0");
	}

	private static final Set<String> ms_features = new HashSet<>(5);   // Placement, etc.
	private static final Set<String> ms_containers = new HashSet<>(2); // Document, Folder
	private static final Set<String> ms_attributes = new HashSet<>(2); // open, metadata
	private static final Set<String> ms_geometries = new HashSet<>(6); // Point, LineString, etc.

	private static final Longitude COORD_ERROR = new Longitude();
	private static final QName ID_ATTR = new QName(ID);

	private Map<String, String> schemaAliases;
	private final Map<String, Schema> schemata = new HashMap<>();
	private boolean dupAltitudeModeWarn;

	static {
		// all non-container elements that extend kml:AbstractFeatureType base type in KML Schema
		ms_features.add(PLACEMARK);
		ms_features.add(NETWORK_LINK);
		ms_features.add(GROUND_OVERLAY);
		ms_features.add(PHOTO_OVERLAY);
		ms_features.add(SCREEN_OVERLAY);

		// all elements that extend kml:AbstractContainerType in KML Schema
		ms_containers.add(FOLDER);
		ms_containers.add(DOCUMENT);

		// basic tags in Feature that are skipped but consumed
		ms_attributes.add(OPEN); // note special handling for Folders, Documents or NetworkLinks
		ms_attributes.add(METADATA); // deprecated

		// all possible elements that extend kml:AbstractGeometryType base type in KML Schema
		ms_geometries.add(POINT);
		ms_geometries.add(LINE_STRING);
		ms_geometries.add(LINEAR_RING);
		ms_geometries.add(POLYGON);
		ms_geometries.add(MULTI_GEOMETRY);
		ms_geometries.add(MODEL);

	}

	public KmlInputStream(InputStream input) throws IOException {
		this(input, new Object[0]);
	}
	
	/**
	 * Creates a {@code KmlInputStream}
	 * and saves its argument, the input stream
	 * {@code input}, for later use.
	 *
	 * @param input input stream for the kml file, never {@code null}
	 * @throws IOException              if an I/O or parsing error occurs
	 * @throws IllegalArgumentException if input is null
	 */
	public KmlInputStream(InputStream input, Object[] args) throws IOException {
		super(input);
		DocumentStart ds = new DocumentStart(DocumentType.KML);
		addLast(ds);
		try {
			XMLEvent ev = stream.peek();
			// Find first StartElement in stream
			while (ev != null && !ev.isStartElement()) {
				ev = stream.nextEvent(); // Actually advance
				if (ev != null) {
					if (ev.isStartDocument()) {
						// first element will be the XML header as StartDocument whether explicit or not
						StartDocument doc = (StartDocument) ev;
						if (doc.encodingSet())
							setEncoding(doc.getCharacterEncodingScheme()); // default UTF-8
					}
					ev = stream.peek();
				}
			}
			if (ev == null) return;
			// The first start element may be a KML element, which isn't
			// handled by the rest of the code. We'll handle it here to obtain the
			// namespaces
			StartElement first = ev.asStartElement();
			QName qname = first.getName();
			String nstr = qname.getNamespaceURI();
			final String localPart = qname.getLocalPart();
			if ("kml".equals(localPart)) {
				if (StringUtils.isNotBlank(nstr) && !ms_kml_ns.contains(nstr)) {
					// KML namespace not registered
					log.info("Registering unrecognized KML namespace: {}", nstr);
					ms_kml_ns.add(nstr);
				}
				stream.nextEvent(); // Consume event
			} else if (StringUtils.isNotBlank(nstr) && !ms_kml_ns.contains(nstr)
					&& (ms_features.contains(localPart) || ms_containers.contains(localPart))) {
				// root element non-kml (e.g. GroundOverlay) and namespace is not registered.
				// Add it otherwise will be parsed as foreign elements
				log.info("Registering unrecognized KML namespace: {}", nstr);
				ms_kml_ns.add(nstr);
			}
			@SuppressWarnings("unchecked")
			Iterator<Namespace> niter = first.getNamespaces();
			while (niter.hasNext()) {
				Namespace ns = niter.next();
				String prefix = ns.getPrefix();
				if (StringUtils.isBlank(prefix)) continue;
				// assuming that namespace prefixes are unique in the source KML document since it would violate
				// the XML unique attribute constraint and not even load in Google Earth.
				try {
					org.opensextant.giscore.Namespace gnamespace =
							org.opensextant.giscore.Namespace.getNamespace(prefix, ns.getNamespaceURI());
					ds.getNamespaces().add(gnamespace);
				} catch (IllegalArgumentException e) {
					// ignore invalid namespaces since often namespaces may not even be used in the document itself
					log.warn("ignore invalid namespace " + prefix + "=" + ns.getNamespaceURI());
				}
			}
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Reads the next {@code IGISObject} from the InputStream.
	 *
	 * @return next {@code IGISObject},
	 *         or {@code null} if the end of the stream is reached.
	 * @throws IOException if an I/O error occurs or if there
	 *                     is a fatal error with the underlying XML
	 */
	// @CheckForNull

	public IGISObject read() throws IOException {
		if (hasSaved()) {
			return readSaved();
		} else {
			try {
				while (true) {
					XMLEvent e = stream.nextEvent();
					if (e == null) {
						return null;
					}
					int type = e.getEventType();
					if (XMLStreamReader.START_ELEMENT == type) {
						IGISObject se = handleStartElement(e);
						if (se == NullObject.getInstance())
							continue;
						return se; // start element is GISObject or null (indicating EOF)
					} else if (XMLStreamReader.END_ELEMENT == type) {
						IGISObject rval = handleEndElement(e);
						if (rval != null)
							return rval;
					}
					/*
						 // saving comments messes up the junit tests so comment out for now
                         } else if (XMLStreamReader.COMMENT == type) {
                             IGISObject comment = handleComment(e);
                             if (comment != null)
                                 return comment;
                         */
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				// if have wrong encoding can end up here
				//log.warn("Unexpected parse error", e);
				throw new IOException(e);
			} catch (NoSuchElementException e) {
				return null;
			} catch (NullPointerException e) {
				throw new IOException(e);
			} catch (XMLStreamException e) {
				throw new IOException(e);
			}
		}
	}

    /*
		 private IGISObject handleComment(XMLEvent e) throws XMLStreamException {
             if (e instanceof javax.xml.stream.events.Comment) {
                 String text = ((javax.xml.stream.events.Comment)e).getText();
                 if (StringUtils.isNotBlank(text))
                     return new Comment(text);
             }
             return null;
         }
         */

	/**
	 * Read elements until we find a feature or a schema element. Use the name
	 * and description data to set the equivalent data on the container start.
	 *
	 * @param e
	 * @return
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@NotNull
	private IGISObject handleContainer(XMLEvent e) throws XMLStreamException {
		StartElement se = e.asStartElement();
		QName name = se.getName();
		String containerTag = name.getLocalPart();
		ContainerStart cs = new ContainerStart(containerTag); // Folder or Document
		addLast(cs);
		Attribute id = se.getAttributeByName(ID_ATTR);
		if (id != null) cs.setId(id.getValue());

		while (true) {
			XMLEvent ne = stream.peek();

			// Found end tag, sometimes a container has no other content
			if (foundEndTag(ne, name)) {
				break;
			}
			if (ne.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement nextel = ne.asStartElement();
				String tag = nextel.getName().getLocalPart();
				// check if element has been aliased in Schema
				// only used for old-style KML 2.0 Schema defs with "parent" attribute/element.
				if (schemaAliases != null) {
					String newName = schemaAliases.get(tag);
					if (newName != null) {
						// log.info("Alias " + tag +" -> " + newName);
						tag = newName;
					}
				}
				if (ms_containers.contains(tag) || ms_features.contains(tag)
						|| SCHEMA.equals(tag)) {
					break;
				}
			}

			XMLEvent ee = stream.nextEvent();
			if (ee == null) {
				break;
			}
			if (ee.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement sl = ee.asStartElement();
				QName qname = sl.getName();
				if (OPEN.equals(qname.getLocalPart())) {
					if (isTrue(getElementText(qname)))
						cs.setOpen(true); // default = 0
				} else if (!handleProperties(cs, ee, qname)) {
					// Ignore other container elements
					log.debug("ignore {}", qname);
				}
			}
		}

		return readSaved();
	}

	/**
	 * Handle the elements found in all features
	 *
	 * @param feature
	 * @param ee
	 * @param name    the qualified name of this event
	 * @return {@code true} if the event has been handled
	 */
	private boolean handleProperties(Common feature, XMLEvent ee,
									 QName name) {
		String localname = name.getLocalPart(); // never null
		try {
			if (localname.equals(NAME)) {
				// sometimes markup found in names (e.g. <name><B>place name</B></name>)
				// where is should be in the description and/or BalloonStyle
				feature.setName(getElementText(name)); // non-empty or null value
				return true;
			} else if (localname.equals(DESCRIPTION)) {
				// description content with markup not enclosed in CDATA is invalid and cannot be parsed
				feature.setDescription(getElementText(name));
				return true;
			} else if (localname.equals(VISIBILITY)) {
				String val = stream.getElementText();
				if (val != null) {
					val = val.trim();
					if ("1".equals(val) || val.equalsIgnoreCase("true"))
						feature.setVisibility(Boolean.TRUE); // default: 1 (true)
					else if ("0".equals(val) || val.equalsIgnoreCase("false"))
						feature.setVisibility(Boolean.FALSE);
				}
				return true;
			} else if (localname.equals(STYLE)) {
				handleStyle(feature, ee, name);
				return true;
			} else if (ms_attributes.contains(localname)) {
				// basic tags in Feature that are skipped but consumed
				// e.g. open, address, phoneNumber, Metadata
				// Skip, but consumed
				skipNextElement(stream, name);
				return true;
			} else if (localname.equals(STYLE_URL)) {
				feature.setStyleUrl(stream.getElementText()); // value trimmed to null
				return true;
			} else if (localname.equals(TIME_SPAN) || localname.equals(TIME_STAMP)) {
				handleTimePrimitive(feature, ee);
				return true;
			} else if (localname.equals(REGION)) {
				handleRegion(feature, name);
				return true;
			} else if (localname.equals(STYLE_MAP)) {
				handleStyleMap(feature, ee, name);
				return true;
			} else if (localname.equals(LOOK_AT) || localname.equals(CAMERA)) {
				handleAbstractView(feature, name);
				return true;
			} else if (localname.equals(EXTENDED_DATA)) {
				handleExtendedData(feature, name);
				return true;
			} else if (localname.equals("Snippet")) { // kml:Snippet (deprecated)
				// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#Snippet
				feature.setSnippet(getElementEmptyText(name)); // allow empty string to be preserved
				return true;
			} else if (localname.equals("snippet")) { // kml:snippet
				// http://code.google.com/apis/kml/documentation/kmlreference.html#snippet
				feature.setSnippet(getElementEmptyText(name)); // allow empty string to be preserved
				return true;
			} else if (localname.equals(ADDRESS) || localname.equals(PHONE_NUMBER)) { // kml:address | kml:phoneNumber
				String value = getElementText(name); // non-empty or null value
				if (value != null) {
					// add value as KML element to be handled later
					Element e = new Element(org.opensextant.giscore.Namespace.getNamespace(KML_NS), localname);
					e.setText(value);
					feature.getElements().add(e);
				}
			} else {
				//StartElement sl = ee.asStartElement();
				//QName name = sl.getName();
				// handle atom:link and atom:author elements (e.g. http://www.w3.org/2005/Atom)
				// and google earth extensions as ForeignElements
				// skip other non-KML namespace elements.
				String ns = name.getNamespaceURI();
				if (StringUtils.isNotEmpty(ns) && !ms_kml_ns.contains(ns)) {
					if (localname.equals(ADDRESS_DETAILS) || ns.startsWith("http://www.w3.org/")
							|| ns.startsWith(NS_GOOGLE_KML_EXT_PREFIX)) {
						try {
							Element el = (Element) getForeignElement(ee.asStartElement());
							feature.getElements().add(el);
						} catch (XMLStreamException e) {
							log.error("Problem getting element", e);
						}
					} else {
						// TODO: should we add all-non KML elements as-is or only expected ones ??
						log.debug("Skip unknown namespace {}", name);
						skipNextElement(stream, name);
					}
					return true;
				}
			}
		} catch (XMLStreamException e) {
			log.error("Failed to handle: " + localname, e);
			// TODO: do we have any situation where need to skip over failed localname element??
			// skipNextElement(stream, name);
		}
		return false;
	}

	/**
	 * @param cs
	 * @param name the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleExtendedData(Common cs, QName name)
			throws XMLStreamException {
		XMLEvent next;
		// namespace of the ExtendedData element
		// should never be null => empty string if no namespace
		String rootNS = name.getNamespaceURI();
		while (true) {
			next = stream.nextEvent();
			if (foundEndTag(next, name)) {
				return;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				QName qname = se.getName();
				String tag = qname.getLocalPart();
				boolean handleAsForeignElement = false;
				/*
					 * xmlns:prefix handling. skips namespaces other than parent namespace (e.g. http://www.opengis.net/kml/2.2)
                     */
				if (!StringUtils.equals(rootNS, qname.getNamespaceURI())) {
					handleAsForeignElement = true;
					// handle extended data elements other namespace other than the root (KML) namespace
				} else if (tag.equals(DATA)) {
					Attribute nameAttr = se.getAttributeByName(new QName(NAME));
					if (nameAttr != null) {
						String value = parseValue(qname);
						if (value != null)
							cs.putData(new SimpleField(nameAttr.getValue()), value);
						// NOTE: if feature has mixed Data and SchemaData then Data fields will be associated with last SchemaData schema processed
					} else {
						// no name skip any value element
						// TODO: if Data has id attr but no name can we use still the value ??
						log.debug("No name attribute for Data. Skip element");
						skipNextElement(stream, qname);
					}
				} else if (tag.equals(SCHEMA_DATA)) {
					Attribute url = se.getAttributeByName(new QName(SCHEMA_URL));
					if (url != null) {
						// NOTE: reference and schema id must be handled exactly the same. See handleSchema()
						String uri = UrlRef.escapeUri(url.getValue());
						handleSchemaData(uri, cs, qname);
						try {
							cs.setSchema(new URI(uri));
						} catch (URISyntaxException e) {
							// is URI properly encoded??
							log.error("Failed to handle SchemaData schemaUrl=" + uri, e);
						}
					} else {
						// no schemaUrl skip SchemaData element
						// TODO: if SchemaData has SimpleData but no schemaUrl attr can we use still the value ??
						log.debug("No schemaUrl attribute for Data. Skip element");
						skipNextElement(stream, qname);
					}
				} else {
					handleAsForeignElement = true;
				}

				if (handleAsForeignElement) {
					// handle extended data elements (i.e., arbitrary XML data) with
					// namespace other than the root (KML) namespace.
					// http://code.google.com/apis/kml/documentation/extendeddata.html#opaquedata
					/*
							 <ExtendedData xmlns:camp="http://campsites.com">
                               <camp:number>14</camp:number>
                               <camp:parkingSpaces>2</camp:parkingSpaces>
                               <camp:tentSites>4</camp:tentSites>
                             </ExtendedData>
                          */
					try {
						log.debug("ExtendedData other {}", qname);
						Element el = (Element) getForeignElement(se.asStartElement());
						cs.getExtendedElements().add(el);
					} catch (XMLStreamException e) {
						log.error("Problem getting other namespace element", e);
						skipNextElement(stream, qname); // is this XML exception recoverable ??
					}
				}
			}
		}
	}

	/**
	 * @param uri   a reference to a schema, if local then use that schema's
	 *              simple field objects instead of creating ones on the fly
	 * @param cs    Feature/Container for ExtendedData tag
	 * @param qname the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleSchemaData(String uri, Common cs, QName qname)
			throws XMLStreamException {
		XMLEvent next;
		if (uri.startsWith("#")) uri = uri.substring(1);
		Schema schema = schemata.get(uri);

		while (true) {
			next = stream.nextEvent();
			if (foundEndTag(next, qname)) {
				return;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				if (foundStartTag(se, SIMPLE_DATA)) {
					Attribute name = se.getAttributeByName(new QName(NAME));
					if (name != null) {
						String value = stream.getElementText();
						SimpleField field = null;
						if (schema != null) {
							field = schema.get(name.getValue());
						}
						if (field == null) {
							// Either we don't know the schema or it isn't local
							field = new SimpleField(name.getValue());
						}
						// NOTE: if feature has multiple SchemaData elements (multi-schemas) then fields will be associated with last SchemaData schema processed
						cs.putData(field, value);
					}
				}
			}
		}
	}

	/**
	 * @param name the qualified name of this event
	 * @return the value associated with the element
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@Nullable
	private String parseValue(QName name) throws XMLStreamException {
		XMLEvent next;
		String rval = null;
		while (true) {
			next = stream.nextEvent();
			if (foundEndTag(next, name)) { // also checks if next == null
				return rval;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				if (foundStartTag(se, VALUE)) {
					rval = stream.getElementText();
				}
			}
			// otherwise next=END_ELEMENT(2) | CHARACTERS(4)
		}
	}

	/**
	 * Handle AbstractView (Camera or LookAt) element
	 *
	 * @param feature
	 * @param name    the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleAbstractView(Common feature, QName name)
			throws XMLStreamException {
		TaggedMap viewGroup = handleTaggedData(name); // Camera or LookAt
		if (viewGroup != null) feature.setViewGroup(viewGroup);
	}

	/**
	 * Handle KML Region
	 *
	 * @param feature
	 * @param name    the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleRegion(Common feature, QName name)
			throws XMLStreamException {
		TaggedMap region = new TaggedMap(REGION);
		while (true) {
			XMLEvent next = stream.nextEvent();
			if (foundEndTag(next, name)) {
				// must have at least one value from either Lod or LatLonAltBox
				if (!region.isEmpty()) {
					feature.setRegion(region);
				}
				return;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				if (foundStartTag(se, LAT_LON_ALT_BOX)) {
					handleTaggedData(se.getName(), region); // LatLonAltBox
				} else if (foundStartTag(se, LOD)) {
					handleTaggedData(se.getName(), region); // Lod
				}
			}
		}
	}

	/**
	 * @param cs
	 * @param ee
	 * @param name the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@NotNull
	private StyleMap handleStyleMap(Common cs, XMLEvent ee, QName name)
			throws XMLStreamException {
		XMLEvent next;
		StyleMap sm = new StyleMap();
		if (cs != null) {
			if (cs instanceof Feature) {
				// inline StyleMap for Placemark, NetworkLink, GroundOverlay, etc.
				((Feature) cs).setStyle(sm);
			} else if (cs instanceof ContainerStart) {
				// add style to container
				((ContainerStart) cs).addStyle(sm);
			} else addLast(sm);
		}
		// otherwise out of order StyleMap

		StartElement sl = ee.asStartElement();
		Attribute id = sl.getAttributeByName(ID_ATTR);
		if (id != null) {
			sm.setId(id.getValue());
		}

		while (true) {
			next = stream.nextEvent();
			if (foundEndTag(next, name)) {
				return sm;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement ie = next.asStartElement();
				if (foundStartTag(ie, PAIR)) {
					handleStyleMapPair(sm, ie.getName());
				}
			}
		}
	}

	/**
	 * @param sm
	 * @param name the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleStyleMapPair(StyleMap sm, QName name) throws XMLStreamException {
		String key = null, value = null;
		Style style = null;
		while (true) {
			XMLEvent ce = stream.nextEvent();
			if (ce == null) {
				return;
			}
			if (ce.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = ce.asStartElement();
				if (foundStartTag(se, KEY)) {
					// key: type="kml:styleStateEnumType" default="normal"/>
					// styleStateEnumType: [normal] or highlight
					key = getNonEmptyElementText();
				} else if (foundStartTag(se, STYLE_URL)) {
					value = getNonEmptyElementText(); // type=anyURI
				} else if (foundStartTag(se, STYLE)) {
					style = handleStyle(null, se, se.getName());
					// inline Styles within StyleMap
				} else if (foundStartTag(se, STYLE_MAP)) {
					// nested StyleMaps are not supported nor does it even make sense
					log.debug("skip nested StyleMap");
					skipNextElement(stream, se.getName());
				}
			}
			XMLEvent ne = stream.peek();
			if (foundEndTag(ne, name)) {
				if (key != null || value != null || style != null) {
					if (key == null) {
						key = StyleMap.NORMAL; // default
					} else if (key.equalsIgnoreCase(StyleMap.NORMAL))
						key = StyleMap.NORMAL;
					else if (key.equalsIgnoreCase(StyleMap.HIGHLIGHT))
						key = StyleMap.HIGHLIGHT;
					else
						log.warn("Unknown StyleMap key: " + key);

					if (sm.containsKey(key)) {
						if (value != null) {
							log.warn("StyleMap already has " + key + " definition. Ignore styleUrl=" + value);
						} else {
							log.warn("StyleMap already has " + key + " definition. Ignore inline Style");
						}
						// Google Earth keeps the first pair for a given key
					} else {
						// note if styleUrl is "local reference" and does not have '#' prefix
						// then it will be pre-pended to the URL.
						sm.add(new Pair(key, value, style));
					}
				}
				return;
			}
		}
	}

	/**
	 * Handle timePrimitives (TimeStamp or timeSpan elements)
	 *
	 * @param cs feature to set with time
	 * @param ee
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleTimePrimitive(Common cs, XMLEvent ee)
			throws XMLStreamException {
		XMLEvent next;
		StartElement sl = ee.asStartElement();
		QName tag = sl.getName();
		while (true) {
			next = stream.nextEvent();
			if (next == null)
				return;
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				String time = null;
				try {
					if (foundStartTag(se, WHEN)) {
						time = getNonEmptyElementText();
						if (time != null) {
							DateTime date = new DateTime(time);
							cs.setStartTime(date);
							cs.setEndTime(date);
						}
					} else if (foundStartTag(se, BEGIN)) {
						time = getNonEmptyElementText();
						if (time != null)
							cs.setStartTime(new DateTime(time));
					} else if (foundStartTag(se, END)) {
						time = getNonEmptyElementText();
						if (time != null)
							cs.setEndTime(new DateTime(time));
					}
				} catch (IllegalArgumentException e) {
					log.warn("Ignoring bad time: " + time + ": " + e);
				} catch (ParseException e) {
					log.warn("Ignoring bad time: " + time + ": " + e);
				}
			}
			if (foundEndTag(next, tag)) {
				return;
			}
		}
	}

	/**
	 * Parse kml:dateTimeType XML date/time field and convert to Date object.
	 *
	 * @param datestr Lexical representation for one of XML Schema date/time datatypes.
	 *                Must be non-null and non-blank string.
	 * @return {@code Date} created from the <code>lexicalRepresentation</code>, never null.
	 * @throws ParseException If the {@code lexicalRepresentation} is not a valid <code>Date</code>.
	 */
	@NotNull
	@Deprecated
	public static Date parseDate(String datestr) throws ParseException {
		return new DateTime(datestr).toDate();
	}

	/**
	 * Get the style data and push the style onto the buffer so it is returned
	 * first, before its container or placemark
	 *
	 * @param cs
	 * @param ee
	 * @param name the qualified name of this event
	 * @return
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@NotNull
	private Style handleStyle(Common cs, XMLEvent ee, QName name)
			throws XMLStreamException {
		XMLEvent next;

		Style style = new Style();
		StartElement sse = ee.asStartElement();
		Attribute id = sse.getAttributeByName(ID_ATTR);
		if (id != null) {
			// escape invalid characters in id field?
			// if so must be consistent in feature.setStyleUrl() and handleStyleMapPair(), etc.
			style.setId(id.getValue());
		}
		if (cs != null) {
			if (cs instanceof Feature) {
				// inline Style for Placemark, NetworkLink, GroundOverlay, etc.
				((Feature) cs).setStyle(style);
			} else if (cs instanceof ContainerStart) {
				// add style to container
				((ContainerStart) cs).addStyle(style);
			} else addLast(style);
		}
		// otherwise out of order style or inline style in StyleMap

		while (true) {
			next = stream.nextEvent();
			if (foundEndTag(next, name)) {
				return style;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				QName qname = se.getName();
				String localPart = qname.getLocalPart();
				if (localPart.equals(ICON_STYLE)) {
					handleIconStyle(style, qname);
				} else if (localPart.equals(LINE_STYLE)) {
					handleLineStyle(style, qname);
				} else if (localPart.equals(BALLOON_STYLE)) {
					handleBalloonStyle(style, qname);
				} else if (localPart.equals(LABEL_STYLE)) {
					handleLabelStyle(style, qname);
				} else if (localPart.equals(POLY_STYLE)) {
					handlePolyStyle(style, qname);
				} else if (localPart.equals(LIST_STYLE)) {
					handleListStyle(style, qname);
				}
			}
		}
	}

	private void handleListStyle(Style style, QName qname) throws XMLStreamException {
		Color bgColor = null; // default color="ffffffff" (white)
		Style.ListItemType listItemType = null;
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, qname)) {
				style.setListStyle(bgColor, listItemType);
				return;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				String localPart = se.getName().getLocalPart();
				if (localPart.equals(LIST_ITEM_TYPE)) {
					String text = getNonEmptyElementText();
					if (text != null)
						try {
							listItemType = Style.ListItemType.valueOf(text);
						} catch (IllegalArgumentException e2) {
							log.warn("Invalid ListItemType value: " + text);
						}
				} else if (localPart.equals(BG_COLOR)) {
					bgColor = parseColor(stream.getElementText());
				}
			}
		}
	}

	/**
	 * @param style
	 * @param qname the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handlePolyStyle(Style style, QName qname) throws XMLStreamException {
		Color color = null; // default color="ffffffff" (white)
		Boolean fill = null; // default = true
		Boolean outline = null;    // default = true
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, qname)) {
				style.setPolyStyle(color, fill, outline);
				return;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				String localPart = se.getName().getLocalPart();
				if (localPart.equals(FILL)) {
					fill = isTrue(stream.getElementText()); // default=true
				} else if (localPart.equals(OUTLINE)) {
					outline = isTrue(stream.getElementText()); // default=true
				} else if (localPart.equals(COLOR)) {
					color = parseColor(stream.getElementText()); // default=WHITE
				} else if (localPart.equals(COLOR_MODE) &&
						"random".equals(stream.getElementText())) {
					style.setPolyColorMode(Style.ColorMode.RANDOM); // default=normal
				}
			}
		}
	}

	/**
	 * Determine if an element value is true or false
	 *
	 * @param val the value, may be {@code null}
	 * @return {@code true} if the value is the single character "1" or "true".
	 */
	private boolean isTrue(String val) {
		// xsd:boolean can have the following legal literals {true, false, 1, 0}.
		if (val != null) {
			val = val.trim();
			//if ("1".equals(val)) return true;
			//else if ("0".equals(val)) return false;
			return "1".equals(val) || val.equalsIgnoreCase("true");
		}
		return false;
	}

	/**
	 * @param style
	 * @param qname the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleLabelStyle(Style style, QName qname) throws XMLStreamException {
		double scale = 1;
		Color color = null; // Color.black;
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, qname)) {
				style.setLabelStyle(color, scale);
				return;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				String name = se.getName().getLocalPart();
				if (name.equals(SCALE)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							scale = Double.parseDouble(value);
						} catch (NumberFormatException nfe) {
							log.warn("Invalid scale value: " + value);
						}
				} else if (name.equals(COLOR)) {
					color = parseColor(stream.getElementText());
				}
			}
		}
	}

	/**
	 * @param style
	 * @param qname the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleLineStyle(Style style, QName qname) throws XMLStreamException {
		double width = 1;
		Color color = Color.white;
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, qname)) {
				style.setLineStyle(color, width);
				return;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				String name = se.getName().getLocalPart();
				if (name.equals(WIDTH)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							width = Double.parseDouble(value);
						} catch (NumberFormatException nfe) {
							log.warn("Invalid width value: " + value);
						}
				} else if (name.equals(COLOR)) {
					String value = stream.getElementText();
					color = parseColor(value);
					if (color == null) {
						//log.warn("Invalid LineStyle color: " + value);
						color = Color.white; // use default
					}
				} else if (name.equals(COLOR_MODE) &&
						"random".equals(stream.getElementText())) {
					style.setLineColorMode(Style.ColorMode.RANDOM); // default=normal
				}
			}
		}
	}

	/**
	 * @param style
	 * @param qname the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleBalloonStyle(Style style, QName qname) throws XMLStreamException {
		String text = null;
		Color color = null;        // default 0xffffffff
		Color textColor = null;    // default 0xff000000
		String displayMode = null; // [default] | hide
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, qname)) {
				style.setBalloonStyle(color, text, textColor, displayMode);
				return;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				String name = se.getName().getLocalPart();
				if (name.equals(TEXT)) {
					// Note: Google Earth 6.0.3 treats blank/empty text same as if missing.
					// It's suggested that an earlier version handled blank string differently
					// according to comments in some KML samples so we're preserving empty strings
					// to force a BalloonStyle to be retained.
					text = StringUtils.trim(stream.getElementText()); // allow empty string
				} else if (name.equals(BG_COLOR)) {
					color = parseColor(stream.getElementText());
				} else if (name.equals(DISPLAY_MODE)) {
					displayMode = getNonEmptyElementText();
				} else if (name.equals(TEXT_COLOR)) {
					textColor = parseColor(stream.getElementText());
				} else if (name.equals(COLOR)) {
					// color element is deprecated in KML 2.1
					// this is alias for bgColor
					color = parseColor(stream.getElementText());
				}
			}
		}
	}

	/**
	 * Get the href property from the Icon element.
	 *
	 * @param qname the qualified name of this event
	 * @return the href, {@code null} if not found.
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@Nullable
	private String parseIconHref(QName qname) throws XMLStreamException {
		String href = null;
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, qname)) { // also checks e == null
				return href;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				String name = se.getName().getLocalPart();
				if (name.equals(HREF)) {
					href = getNonEmptyElementText();
				}
			}
		}
	}

	/**
	 * Parse the color from a kml file, in {@code AABBGGRR} order.
	 *
	 * @param cstr a hex encoded string, must be exactly 8 characters long.
	 * @return the color value, null if value is null, empty or invalid
	 */
	@Nullable
	private Color parseColor(String cstr) {
		if (cstr == null) return null;
		cstr = cstr.trim();
		if (cstr.startsWith("#")) {
			// skip over '#' prefix used for HTML color codes allowed by Google Earth
			// but invalid wrt KML XML Schema.
			log.debug("Skip '#' in color code: {}", cstr);
			cstr = cstr.substring(1);
		}
		if (cstr.length() == 8)
			try {
				int alpha = Integer.parseInt(cstr.substring(0, 2), 16);
				int blue = Integer.parseInt(cstr.substring(2, 4), 16);
				int green = Integer.parseInt(cstr.substring(4, 6), 16);
				int red = Integer.parseInt(cstr.substring(6, 8), 16);
				return new Color(red, green, blue, alpha);
			} catch (IllegalArgumentException ex) {
				// fall through and log bad value
			}

		log.warn("Invalid color value: " + cstr);
		return null;
	}

	/**
	 * @param style
	 * @param name  the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	private void handleIconStyle(Style style, QName name) throws XMLStreamException {
		String url = null;
		Double scale = null; //1.0;		// default value
		Double heading = null; // 0.0;
		Color color = null; // Color.white;	// default="ffffffff"
		while (true) {
			XMLEvent e = stream.nextEvent();
			if (foundEndTag(e, name)) {
				try {
					style.setIconStyle(color, scale, heading, url);
				} catch (IllegalArgumentException iae) {
					log.warn("Invalid style: " + iae);
				}
				return;
			}
			if (e.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = e.asStartElement();
				QName qname = se.getName();
				String localPart = qname.getLocalPart();
				if (localPart.equals(SCALE)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							scale = Double.parseDouble(value);
						} catch (NumberFormatException nfe) {
							log.warn("Invalid scale value: " + value);
						}
				} else if (localPart.equals(HEADING)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							heading = Double.parseDouble(value);
						} catch (NumberFormatException nfe) {
							log.warn("Invalid heading value: " + value);
						}
				} else if (localPart.equals(COLOR)) {
					String value = stream.getElementText();
					color = parseColor(value);
					//if (color == null) {
					//log.warn("Invalid IconStyle color: " + value);
					//color = Color.white; // use default="ffffffff"
					//}
				} else if (localPart.equals(ICON)) {
					// IconStyle/Icon is kml:BasicLinkType with only href property
					url = parseIconHref(qname);
					// if have Icon element but no href then use empty string to indicate that Icon
					// was present but don't have an associated href as handled in KmlOutputStream.
					// Having empty Icon element is handled the same as having an empty href
					// element in Google Earth.
					if (url == null) url = "";
				}
			}
		}
	}

	/**
	 * @param e current XML element
	 * @return IGISObject representing current element,
	 *         NullObject if failed to parse and unable to skip to end tag for that element
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 * @throws IOException        if encountered NetworkLinkControl or out of order Style element
	 *                            and failed to skip to end tag for that element.
	 */
	@NotNull
	private IGISObject handleStartElement(XMLEvent e) throws XMLStreamException {
		StartElement se = e.asStartElement();
		QName name = se.getName();
		String ns = name.getNamespaceURI();

		// handle non-kml namespace elements as foreign elements
		// review: should we check instead if namespace doesn't equal our root document namespace...
		// if namespace empty string then probably old-style "kml" root element without explicit namespace
		if (StringUtils.isNotEmpty(ns) && !ms_kml_ns.contains(ns)) {
			// //ns.startsWith("http://www.google.com/kml/ext/")) { ...
			// handle extension namespace
			// http://code.google.com/apis/kml/documentation/kmlreference.html#kmlextensions
			log.debug("XXX: handle as foreign element: {}", name);
			return getForeignElement(se);
		}

		String localname = name.getLocalPart();
		String elementName = localname; // differs from localname if aliased by Schema mapping
		//System.out.println(localname); //debug
		// check if element has been aliased in Schema
		// only used for old-style KML 2.0/2.1 Schema defs with "parent" attribute.
		// generally only Placemarks are aliased. Not much use to alias Document or Folder elements, etc.
		if (schemaAliases != null) {
			String newName = schemaAliases.get(elementName);
			if (newName != null) {
				// log.info("Alias " + elementName + " -> " + newName);
				// Note: does not support multiple levels of aliases (e.g. Person <- Placemark; VipPerson <- Person, etc.)
				// To-date have only seen aliases for Placemarks so don't bother checking.
				elementName = newName;
			}
		}
		try {
			if (ms_features.contains(elementName)) {
				// all non-container elements that extend kml:AbstractFeatureType base type in KML Schema
				// Placemark, NetworkLink, GroundOverlay, ScreenOverlay, PhotoOverlay
				return handleFeature(e, elementName);
			} else if (ms_containers.contains(elementName)) {
				// all container elements that extend kml:AbstractContainerType base type in KML Schema
				//System.out.println("** handle container: " + elementName);
				return handleContainer(se);
			} else if (SCHEMA.equals(localname)) {
				return handleSchema(se, name);
			} else if (NETWORK_LINK_CONTROL.equals(localname)) {
				return handleNetworkLinkControl(stream, name);
			} else if (STYLE.equals(localname)) {
				log.debug("Out of order element: {}", localname);
				// note this breaks the strict ordering required by KML 2.2
				return handleStyle(null, se, name);
			} else if (STYLE_MAP.equals(localname)) {
				log.debug("Out of order element: {}", localname);
				// note this breaks the strict ordering required by KML 2.2
				return handleStyleMap(null, se, name);
			} else {
				String namespace = name.getNamespaceURI();
				if (ms_kml_ns.contains(namespace)) {
					// Look for next start element and recurse
					XMLEvent next = stream.peek();
					if (next != null) {
						if (next.getEventType() == XMLEvent.START_ELEMENT) {
							next = stream.nextTag();
							return handleStartElement(next);
						} else if (next.getEventType() == XMLEvent.END_ELEMENT) {
							log.debug("Skip element: {}", localname);
							stream.nextTag();
						} else throw new XMLStreamException("unexpected element");
					}
				} else {
					log.debug("XXX: handle startElement with foreign namespace: {}", name);
					return getForeignElement(se);
				}
			}
		} catch (XMLStreamException e1) {
			log.warn("Skip element: {}", localname);
			log.debug("", e1);
			skipNextElement(stream, name);
		}
		/*
		} catch(NullPointerException npe) {
			// bad encoding in characters may throw NullPointerException
			log.warn("XXX: Skip element: " + localname);
			log.debug("", npe);
			skipNextElement(stream, name);
		}
		*/

		// return non-null NullObject to skip but not end parsing...
		return NullObject.getInstance();
	}

	@NotNull
	private IGISObject handleNetworkLinkControl(XMLEventReader stream, QName name) throws XMLStreamException {
		NetworkLinkControl c = new NetworkLinkControl();
		// if true indicates we're parsing the Update element
		boolean updateFlag = false;
		//String updateType = null;
		while (true) {
			XMLEvent next = stream.nextEvent();
			if (foundEndTag(next, name)) {
				break;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				QName qname = se.getName();
				String tag = qname.getLocalPart(); // never-null
				if (updateFlag) {
					if (tag.equals("targetHref")) {
						String val = getNonEmptyElementText();
						if (val != null) c.setTargetHref(val);
						// TODO: NetworkLinkControl can have 1 or more Update controls
						// TODO: -- handle Update details
					} else if (tag.equals("Create")) {
						c.setUpdateType("Create");
					} else if (tag.equals("Delete")) {
						c.setUpdateType("Delete");
					} else if (tag.equals("Change")) {
						c.setUpdateType("Change");
					}
				} else {
					if (tag.equals("minRefreshPeriod")) {
						Double val = getDoubleElementValue("minRefreshPeriod");
						if (val != null) c.setMinRefreshPeriod(val);
					} else if (tag.equals("maxSessionLength")) {
						Double val = getDoubleElementValue("maxSessionLength");
						if (val != null) c.setMaxSessionLength(val);
					} else if (tag.equals("cookie")) {
						String val = getNonEmptyElementText();
						if (val != null) c.setCookie(val);
					} else if (tag.equals("message")) {
						String val = getNonEmptyElementText();
						if (val != null) c.setMessage(val);
					} else if (tag.equals("linkName")) {
						String val = getNonEmptyElementText();
						if (val != null) c.setLinkName(val);
					} else if (tag.equals("linkDescription")) {
						String val = getNonEmptyElementText();
						if (val != null) c.setLinkDescription(val);
					} else if (tag.equals("linkSnippet")) {
						String val = getNonEmptyElementText();
						if (val != null) c.setLinkSnippet(val);
					} else if (tag.equals("expires")) {
						String expires = getNonEmptyElementText();
						if (expires != null)
							try {
								c.setExpires(new DateTime(expires).toDate());
							} catch (IllegalArgumentException e) {
								log.warn("Ignoring bad expires value: " + expires + ": " + e);
							} catch (ParseException e) {
								log.warn("Ignoring bad expires value: " + expires + ": " + e);
							}
					} else if (tag.equals(LOOK_AT) || tag.equals(CAMERA)) {
						TaggedMap viewGroup = handleTaggedData(qname); // LookAt | Camera
						c.setViewGroup(viewGroup);
					} else if (tag.equals("Update")) {
						updateFlag = true; // set flag to parse inside Update element
					}
				}
			} else {
				if (foundEndTag(next, "Update"))
					updateFlag = false;
			}
		}
		return c;
	}

	/**
	 * Return Schema object populated with SimpleFields as defined
	 *
	 * @param element
	 * @param qname   the qualified name of this event
	 * @return
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@NotNull
	private IGISObject handleSchema(StartElement element, QName qname)
			throws XMLStreamException {
		Schema s = new Schema();
		Attribute attr = element.getAttributeByName(new QName(NAME));
		String name = getNonEmptyAttrValue(attr);

		// get parent attribute for old-style KML 2.0/2.1 which aliases KML elements
		// (e.g. Placemarks) with user-defined ones.
/*
        <Schema name="S_FOBS_USA_ISAF_NATO_DSSSSSSDDDD" parent="Placemark">
            <SimpleField name="NAME" type="wstring"/>
            <SimpleField name="DATE" type="wstring"/>
            <SimpleField name="MGRS" type="wstring"/>
        </Schema>
*/
		attr = element.getAttributeByName(new QName(PARENT));
		String parent = getNonEmptyAttrValue(attr);
		Attribute id = element.getAttributeByName(ID_ATTR);
        /*
                   * The 'value space' of ID is the set of all strings that match the NCName production in [Namespaces in XML]:
                   *  NCName ::=  (Letter | '_') (NCNameChar)*  -- An XML Name, minus the ":"
                   *  NCNameChar ::=  Letter | Digit | '.' | '-' | '_' | CombiningChar | Extender
                   */
		if (id != null) {
			// NOTE: reference and schema id must be handled exactly the same. See handleExtendedData().
			// Schema id is not really a URI but will be treated as such for validation for now.
			// NCName is subset of possible URI values.
			// Following characters cause fail URI creation: 0x20 ":<>[\]^`{|} so escape them
			String uri = UrlRef.escapeUri(id.getValue());
			// remember the schema for later references
			schemata.put(uri, s);
			try {
				s.setId(new URI(uri));
			} catch (URISyntaxException e) {
				// is URI properly encoded??
				log.warn("Invalid schema id " + uri, e);
			}
		}

		int gen = 0;
		while (true) {
			XMLEvent next = stream.nextEvent();
			if (foundEndTag(next, qname)) {
				break;
			}
			if (next.getEventType() == XMLEvent.START_ELEMENT) {
				StartElement se = next.asStartElement();
				if (foundStartTag(se, SIMPLE_FIELD)) {
					Attribute fname = se.getAttributeByName(new QName(NAME));
					String fieldname = fname != null ? fname.getValue() : "gen" + gen++;
					/*
					  https://developers.google.com/kml/documentation/kmlreference#simplefield
					  If either the type or the name is omitted, the field is ignored.

					<element name="SimpleField" type="kml:SimpleFieldType"/>
					<complexType name="SimpleFieldType" final="#all">
						<sequence>
							<element ref="kml:displayName" minOccurs="0"/>
							<element ref="kml:SimpleFieldExtension" minOccurs="0" maxOccurs="unbounded"/>
						</sequence>
						<attribute name="type" type="string"/>
						<attribute name="name" type="string"/>
					</complexType>

					NOTE: allowed type values (per-spec): string, int, uint, short, ushort, float, double, bool
					*/
						SimpleField field = new SimpleField(fieldname);
						Attribute type = se.getAttributeByName(new QName(TYPE));
						SimpleField.Type ttype = SimpleField.Type.STRING; // default
						if (type != null) {
							String typeValue = type.getValue();
							// old-style "wstring" is just a string type
							if (StringUtils.isNotBlank(typeValue) && !"wstring".equalsIgnoreCase(typeValue)) {
								try {
									ttype = SimpleField.Type.valueOf(typeValue.toUpperCase());
								} catch (IllegalArgumentException e) {
									// ATC 26: Schema - SimpleField
									// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#Schema-SimpleField
									// type value must map to enumerations defined in SimpleField.Type which is a super-set
									// of those defined in KML 2.2 spec for kml:SimpleField/type
									log.warn("Invalid SimpleField type in field [name=" + fieldname + " type=" + typeValue+"]");
									// if invalid type then default to string
								}
							}
						}
						field.setType(ttype);
						String displayName = parseDisplayName(SIMPLE_FIELD);
						field.setDisplayName(displayName);
						s.put(fieldname, field);
				} else if (foundStartTag(se, PARENT)) {
					// parent should only appear as Schema child element in KML 2.0 or 2.1
					/*
					        <Schema>
					            <name>S_FOBS_USA_ISAF_NATO_DSSSSSSDDDD</name>
					            <parent>Placemark</parent>
					            <SimpleField name="NAME" type="string"/>
					            <SimpleField name="DATE" type="string"/>
					            <SimpleField name="MGRS" type="string"/>
					        </Schema>
					*/
					String parentVal = getNonEmptyElementText();
					if (parentVal != null) parent = parentVal;
				} else if (foundStartTag(se, NAME)) {
					// name should only appear as Schema child element in KML 2.0 or 2.1
					String nameVal = getNonEmptyElementText();
					if (nameVal != null) name = nameVal;
				}
			}
		}

		if (name != null) s.setName(name);

		// define old-style Schema parent association
		if (parent != null) {
			s.setParent(parent);
			if (name != null) {
				// add alias to schema alias list
				if (schemaAliases == null)
					schemaAliases = new HashMap<>();
				schemaAliases.put(name, parent);
			}
		}

		return s;
	}

	/**
	 * Returns non-empty text value from attribute. Functionally
	 * same as calling <code>StringUtils.trimToNull(attr.getValue())</code>.
	 *
	 * @param attr Attribute
	 * @return non-empty text value trimmed from attribute,
	 *         null if empty
	 */
	@Nullable
	private static String getNonEmptyAttrValue(Attribute attr) {
		if (attr != null) {
			String value = attr.getValue();
			if (value != null) {
				value = value.trim();
				if (value.length() != 0) return value;
			}
		}
		return null;
	}

	/**
	 * @param tag
	 * @return
	 * @throws XMLStreamException if there is an error with the underlying XML
	 */
	@Nullable
	private String parseDisplayName(String tag) throws XMLStreamException {
		String rval = null;
		while (true) {
			XMLEvent ee = stream.nextEvent();
			if (ee == null) {
				break;
			}
			if (ee.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement sl = ee.asStartElement();
				QName name = sl.getName();
				String localname = name.getLocalPart();
				if (localname.equals(DISPLAY_NAME)) {
					rval = getNonEmptyElementText();
				}
			} else if (foundEndTag(ee, tag)) {
				break;
			}
		}
		return rval;
	}

	/**
	 * @param e
	 * @param type
	 * @return
	 * @throws XMLStreamException if there is an error with the underlying XML
	 */
	@NotNull
	private IGISObject handleFeature(XMLEvent e, String type) throws XMLStreamException {
		StartElement se = e.asStartElement();
		boolean placemark = PLACEMARK.equals(type);
		boolean screen = SCREEN_OVERLAY.equals(type);
		boolean photo = PHOTO_OVERLAY.equals(type);
		boolean ground = GROUND_OVERLAY.equals(type);
		boolean network = NETWORK_LINK.equals(type);
		boolean isOverlay = screen || photo || ground;
		Feature fs;
		if (placemark) {
			fs = new Feature();
		} else if (screen) {
			fs = new ScreenOverlay();
		} else if (photo) {
			fs = new PhotoOverlay();
		} else if (ground) {
			fs = new GroundOverlay();
		} else if (network) {
			fs = new NetworkLink();
		} else {
			// should never get here
			String localname = se.getName().getLocalPart();
			if (!localname.equals(type))
				log.error("Found new unhandled feature type: {} [{}]", type, localname);
			else
				log.error("Found new unhandled feature type: " + type);
			return NullObject.getInstance();
		}

		QName name = se.getName();
		addLast(fs);
		Attribute id = se.getAttributeByName(ID_ATTR);
		if (id != null) fs.setId(id.getValue());

		while (true) {
			XMLEvent ee = stream.nextEvent();
			// Note: if element has undeclared namespace then throws XMLStreamException
			// Message: http://www.w3.org/TR/1999/REC-xml-names-19990114#ElementPrefixUnbound
			if (foundEndTag(ee, name)) {
				break; // End of feature
			}
			if (ee.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement sl = ee.asStartElement();
				QName qName = sl.getName();
				String localname = qName.getLocalPart();
				// Note: if element is aliased Placemark then metadata fields won't be saved
				// could treat as ExtendedData if want to preserve this data.
				if (network && OPEN.equals(localname)) {
					if (isTrue(getElementText(qName)))
						((NetworkLink) fs).setOpen(true); // default = 0
				} else if (!handleProperties(fs, ee, qName)) {
					// Deal with specific feature elements
					if (ms_geometries.contains(localname)) {
						// geometry: Point, LineString, LinearRing, Polygon, MultiGeometry, Model
						// does not include gx:Track or gx:MultiTrack
						try {
							Geometry geo = handleGeometry(sl);
							if (geo != null) {
								fs.setGeometry(geo);
							}
						} catch (XMLStreamException xe) {
							log.warn("Failed XML parsing: skip geometry " + localname);
							log.debug("", xe);
							skipNextElement(stream, qName);
						} catch (RuntimeException rte) {
							// IllegalStateException or IllegalArgumentException
							log.warn("Failed geometry: " + fs, rte);
						}
					} else if (isOverlay) {
						if (COLOR.equals(localname)) {
							((Overlay) fs).setColor(parseColor(stream
									.getElementText()));
						} else if (DRAW_ORDER.equals(localname)) {
							Integer val = getIntegerElementValue(DRAW_ORDER);
							if (val != null)
								((Overlay) fs).setDrawOrder(val);
						} else if (ICON.equals(localname)) {
							((Overlay) fs).setIcon(handleTaggedData(qName)); // Icon
						}
						if (ground) {
							if (LAT_LON_BOX.equals(localname)) {
								handleLatLonBox((GroundOverlay) fs, qName);
							} else if (ALTITUDE.equals(localname)) {
								String text = getNonEmptyElementText();
								if (text != null) {
									((GroundOverlay) fs).setAltitude(Double.valueOf(text));
								}
							} else if (ALTITUDE_MODE.equals(localname)) {
								// TODO: doesn't differentiate btwn kml:altitudeMode and gx:altitudeMode
								((GroundOverlay) fs).setAltitudeMode(
										getNonEmptyElementText());
							}
						} else if (screen) {
							if (OVERLAY_XY.equals(localname)) {
								ScreenLocation val = handleScreenLocation(sl);
								((ScreenOverlay) fs).setOverlay(val);
							} else if (SCREEN_XY.equals(localname)) {
								ScreenLocation val = handleScreenLocation(sl);
								((ScreenOverlay) fs).setScreen(val);
							} else if (ROTATION_XY.equals(localname)) {
								ScreenLocation val = handleScreenLocation(sl);
								((ScreenOverlay) fs).setRotation(val);
							} else if (SIZE.equals(localname)) {
								ScreenLocation val = handleScreenLocation(sl);
								((ScreenOverlay) fs).setSize(val);
							} else if (ROTATION.equals(localname)) {
								String val = getNonEmptyElementText();
								if (val != null)
									try {
										double rot = Double.parseDouble(val);
										if (Math.abs(rot) <= 180)
											((ScreenOverlay) fs).setRotationAngle(rot);
										else {
											// 190 => -170, -190 => 170
											if (rot > 180) rot -= 360;
											else rot += 360; // otherwise rot < -180
											log.warn("Normalize ScreenOverlay rotation value: {} => {}", val, rot);
											((ScreenOverlay) fs).setRotationAngle(rot);
										}
									} catch (IllegalArgumentException nfe) {
										log.warn("Invalid ScreenOverlay rotation " + val + ": " + nfe);
									}
							}
						} else if (photo) {
							if (ROTATION.equals(localname)) {
								String val = getNonEmptyElementText();
								if (val != null)
									try {
										double rot = Double.parseDouble(val);
										if (Math.abs(rot) <= 180)
											((PhotoOverlay) fs).setRotation(rot);
										else
											log.warn("Invalid PhotoOverlay rotation value " + val);
									} catch (IllegalArgumentException nfe) {
										log.warn("Invalid PhotoOverlay rotation " + val + ": " + nfe);
									}
							}
							// TODO: fill in other properties (ViewVolume, ImagePyramid, shape)
							// Note Point is populated above using setGeometry()
						}
					} else if (network) {
						if (REFRESH_VISIBILITY.equals(localname)) {
							((NetworkLink) fs).setRefreshVisibility(isTrue(stream
									.getElementText())); // default=false
						} else if (FLY_TO_VIEW.equals(localname)) {
							((NetworkLink) fs).setFlyToView(isTrue(stream
									.getElementText())); // default=false
						} else if (LINK.equals(localname)) {
							((NetworkLink) fs).setLink(handleTaggedData(qName)); // Link
						} else if (URL.equals(localname)) {
							// uses deprecated kml:Url element
							// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#NetworkLink-Url
							((NetworkLink) fs).setLink(handleTaggedData(qName)); // Url
						}
					}
				}
			}
		}
		return readSaved();
	}

	/**
	 * Process the attributes from the start element to create a screen location
	 *
	 * @param sl the start element
	 * @return the location, never {@code null}.
	 */
	@Nullable
	private ScreenLocation handleScreenLocation(StartElement sl) {
		try {
			ScreenLocation loc = new ScreenLocation();
			Attribute x = sl.getAttributeByName(new QName("x"));
			Attribute y = sl.getAttributeByName(new QName("y"));
			Attribute xunits = sl.getAttributeByName(new QName("xunits"));
			Attribute yunits = sl.getAttributeByName(new QName("yunits"));
			if (x != null) {
				loc.x = Double.parseDouble(x.getValue());
			}
			if (y != null) {
				loc.y = Double.parseDouble(y.getValue());
			}
			if (xunits != null) {
				String val = xunits.getValue();
				loc.xunit = ScreenLocation.UNIT.valueOf(val.toUpperCase());
			}
			if (yunits != null) {
				String val = yunits.getValue();
				loc.yunit = ScreenLocation.UNIT.valueOf(val.toUpperCase());
			}
			return loc;
		} catch (IllegalArgumentException iae) {
			log.error("Invalid screenLocation", iae);
			return null;
		}
	}

	/**
	 * Handle a set of elements with character values. The block has been found
	 * that starts with a &lt;localname&gt; tag, and it will end with a matching
	 * tag. All other elements found will be added to a created map object.
	 *
	 * @param name the QName, assumed not {@code null}.
	 * @param map  TaggedMap to provide, never null
	 * @return the map, null if no non-empty values are found
	 * @throws XMLStreamException if there is an error with the underlying XML
	 */
	@Nullable
	private TaggedMap handleTaggedData(QName name, TaggedMap map)
			throws XMLStreamException {
		String rootNs = name.getNamespaceURI();
		while (true) {
			XMLEvent event = stream.nextEvent();
			if (foundEndTag(event, name)) {
				break;
			}
			if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement se = event.asStartElement();
				QName qname = se.getName();
				String sename = qname.getLocalPart();
				if (rootNs != null) {
					// rootNs should never be null. should be empty string "" if default xmlns
					// ignore extension elements. don't know how to parse inside them yet
					// e.g. http://www.google.com/kml/ext/2.2
					// only add those element that are part of the KML namespace which we expect
					String ns = qname.getNamespaceURI();
					if (ns != null && !rootNs.equals(ns)) {
						if (!handleExtension(map, se, qname)) {
							log.debug("Skip " + qname.getPrefix() + ":" + sename);
						}
						continue;
					}
				}
				String value;
				// ignore empty elements; e.g. {@code <Icon>}<href /></Icon>
				// except viewFormat tag in Link which is allowed to have empty string value
				if (VIEW_FORMAT.equals(sename)) {
					value = StringUtils.trim(stream.getElementText()); // allow empty string
				} else {
					value = getNonEmptyElementText();
				}
				if (value != null)
					map.put(sename, value);
			}
		}
		return map.isEmpty() ? null : map;
	}

	@Nullable
	private TaggedMap handleTaggedData(QName name) throws XMLStreamException {
		// handle Camera, LookAt, Icon, Link, and Url elements
		return handleTaggedData(name, new TaggedMap(name.getLocalPart()));
	}

	private boolean handleExtension(TaggedMap map, StartElement se, QName qname) throws XMLStreamException {
		String ns = qname.getNamespaceURI();
		// tagged data used to store child text-content elements for following elements:
		// Camera, LookAt, LatLonAltBox, Lod, Icon, Link, and Url
		// TODO: allow other extensions for TaggedMaps besides gx namespace ?
		if (ns.startsWith(NS_GOOGLE_KML_EXT_PREFIX)) {
			return handleElementExtension(map, (Element) getForeignElement(se), null);
		} else {
			skipNextElement(stream, qname);
			return false;
		}
	}

	private boolean handleElementExtension(TaggedMap map, Element el, String namePrefix) {
        /*
		   LookAt/Camera elements can include gx:TimeSpan or gx:TimeStamp child elements:

		   <gx:TimeSpan>
			 <begin>2010-05-28T02:02:09Z</begin>
			 <end>2010-05-28T02:02:56Z</end>
		   </gx:TimeSpan>
		  */
		String prefix = el.getPrefix();
		String ns = el.getNamespaceURI();
		// use "gx" handle regardless of what KML uses for gx namespace
		if (ns.startsWith(NS_GOOGLE_KML_EXT_PREFIX)) prefix = "gx";

		if (!el.getChildren().isEmpty()) {
			boolean found = false;
			String eltname = el.getName();
			if (StringUtils.isNotBlank(prefix)) {
				eltname = prefix + ":" + eltname;
			}
			if (namePrefix == null) namePrefix = eltname;
			else namePrefix += "/" + eltname;
			for (Element child : el.getChildren()) {
				if (handleElementExtension(map, child, namePrefix))
					found = true; // got match
			}
			return found;
		}

		// if not Node then look for simple TextElement
		String text = el.getText();
		if (StringUtils.isBlank(text)) {
			return false;
		}

		String eltname = el.getName();
		if (StringUtils.isNotBlank(prefix)) {
			if (ALTITUDE_MODE.equals(eltname)) {
				// handle altitudeMode as special case. store w/o prefix since handled as a single attribute
				// if have gx:altitudeMode and altitudeMode then altitudeMode overrides gx:altitudeMode
				// Note Google Earth deliberately generates Placemarks with both gx:altitudeMode and altitudeMode
				// for backward compatibility breaking strict conformance to the official OGC KML 2.2 Schema !!!
				// see http://code.google.com/p/earth-issues/issues/detail?id=1182
				if (map.containsKey(ALTITUDE_MODE)) {
					if (!dupAltitudeModeWarn) {
						// Google Earth-generated output may have this on every placemark
						log.debug("Element has duplicate altitudeMode defined"); // ignore but return as element processed
						dupAltitudeModeWarn = true;
					}
					return true;
				}
			} else {
				eltname = prefix + ":" + eltname; // prefix name with namespace prefix
			}
			log.debug("Handle tag data " + prefix + ":" + el.getName());
		} // else log.debug("non-prefix ns=" + el.getNamespace());
		if (namePrefix == null) namePrefix = eltname;
		else namePrefix += "/" + eltname;
		map.put(namePrefix, text);
		return true;
	}

	/**
	 * Handle a LatLonBox element with north, south, east and west elements.
	 *
	 * @param overlay
	 * @param name    the qualified name of this event
	 * @throws XMLStreamException if there is an error with the underlying XML
	 */
	private void handleLatLonBox(GroundOverlay overlay, QName name)
			throws XMLStreamException {
		while (true) {
			XMLEvent event = stream.nextEvent();
			if (foundEndTag(event, name)) {
				break;
			}
			if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement se = event.asStartElement();
				String sename = se.getName().getLocalPart();
				String value = getNonEmptyElementText();
				if (value != null) {
					try {
						Double angle = Double.valueOf(value);
						if (NORTH.equals(sename)) {
							overlay.setNorth(angle);
						} else if (SOUTH.equals(sename)) {
							overlay.setSouth(angle);
						} else if (EAST.equals(sename)) {
							overlay.setEast(angle);
						} else if (WEST.equals(sename)) {
							// normalize west: value < -180 and add 360.
							// reverse hack/fix in KmlOutputStream for bug in Google Earth crossing IDL
							// must be consistent with handling in KmlOutputStream.handleOverlay()
							if (angle < -180) {
								log.debug("Normalized GroundOverlay west value");
								angle += 360;
							}
							overlay.setWest(angle);
						} else if (ROTATION.equals(sename)) {
							overlay.setRotation(angle);
						}
					} catch (NumberFormatException nfe) {
						log.error("Invalid GroundOverlay angle " + value + " in " + sename);
					} catch (IllegalArgumentException nfe) {
						log.error("Invalid GroundOverlay value in " + sename + ": " + nfe);
					}
				}
			}
		}
	}

	/**
	 * Parse and process the geometry for the feature and store in the feature
	 *
	 * @param sl StartElement
	 * @return Geometry associated with this element
	 *         otherwise null if no valid Geometry can be constructed
	 * @throws XMLStreamException       if there is an error with the underlying XML
	 * @throws IllegalStateException    if geometry is invalid
	 * @throws IllegalArgumentException if geometry is invalid (e.g. invalid Lon/Lat, Line has < 2 points, etc.)
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	private Geometry handleGeometry(StartElement sl) throws XMLStreamException {
		QName name = sl.getName();
		String localname = name.getLocalPart();
		// localname must match: { Point, MultiGeometry, Model }, or { LineString, LinearRing, Polygon }
		// note: gx:altitudeMode may be found within geometry elements but doesn't appear to affect parsing
		if (localname.equals(POINT)) {
			return parseCoordinate(name);
		} else if (localname.equals(MULTI_GEOMETRY)) {
			List<Geometry> geometries = new ArrayList<>();
			while (true) {
				XMLEvent event = stream.nextEvent();
				if (foundEndTag(event, name)) {
					break;
				}
				if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
					StartElement el = (StartElement) event;
					String tag = el.getName().getLocalPart();
					// tag must match: Point, LineString, LinearRing, Polygon, MultiGeometry, or Model
					// does not include gx:Track or gx:MultiTrack
					if (ms_geometries.contains(tag)) {
						try {
							Geometry geom = handleGeometry(el);
							if (geom != null) geometries.add(geom);
						} catch (RuntimeException rte) {
							// IllegalStateException or IllegalArgumentException
							log.warn("Failed geometry: " + tag, rte);
						}
					}
				}
			}
			// if no valid geometries then return null
			if (geometries.isEmpty()) {
				log.debug("No valid geometries in MultiGeometry");
				return null;
			}
			// if only one valid geometry then drop collection and use single geometry
			if (geometries.size() == 1) {
				log.debug("Convert MultiGeometry to single geometry");
				// tesselate/extrude properties are preserved on target geometry
				return geometries.get(0);
			}
			boolean allpoints = true;
			for (Geometry geo : geometries) {
				if (geo != null && geo.getClass() != Point.class) {
					allpoints = false;
					break;
				}
			}
			if (allpoints) {
				return new MultiPoint((List) geometries);
			} else {
				return new GeometryBag(geometries);
			}
		} else if (localname.equals(MODEL)) {
			// we don't really have a way to represent this yet
			Model model = new Model();
			while (true) {
				XMLEvent event = stream.nextEvent();
				if (foundEndTag(event, name)) {
					break;
				}
				if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
					StartElement se = event.asStartElement();
					QName qname = se.getName();
					String localPart = qname.getLocalPart();
					if (localPart.equals(LOCATION)) {
						// Location specifies the exact coordinates of the Model's origin in latitude, longitude, and altitude.
						// Latitude and longitude measurements are standard lat-lon projection with WGS84 datum.
						// Altitude is distance above the earth's surface, in meters, and is interpreted according to <altitudeMode>.
						Geodetic2DPoint point = parseLocation(qname);
						if (point != null)
							model.setLocation(point);
					} else if (localPart.equals(ALTITUDE_MODE)) {
						// TODO: doesn't differentiate between kml:altitudeMode and gx:altitudeMode and situation of having duplicates
						model.setAltitudeMode(getNonEmptyElementText());
					}
					// todo: Orientation, Scale, Link, ResourceMap
				}
			}
			return model;
		} else {
			// otherwise try LineString, LinearRing, Polygon
			return getGeometryBase(name, localname);
		}
	}

	/**
	 * Construct Geometry from the KML
	 *
	 * @param name      the qualified name of this event
	 * @param localname local part of this {@code QName}
	 * @return geometry
	 * @throws XMLStreamException       if there is an error with the underlying XML.
	 * @throws IllegalArgumentException if geometry is invalid (e.g. no valid coordinates)
	 * @throws IllegalStateException    if Bad poly found (e.g. no outer ring)
	 */
	@Nullable
	private GeometryBase getGeometryBase(QName name, String localname) throws XMLStreamException {
		if (localname.equals(LINE_STRING)) {
			GeometryGroup geom = parseCoordinates(name);
			if (log.isDebugEnabled() && geom.size() < 2) {
				// ATC 15: LineString [OGC-07-147r2: cl. 10.7.3.4.1]
				// Verify that the kml:coordinates element in a kml:LineString geometry contains at least two coordinate tuples.
				// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#LineString
				// NOTE: log level checked at debug level but logged at warn level to be picked up with KmlMetaDataDump
				log.warn("LineString geometry fails constraint to contain at least two coordinate tuples [ATC 15]");
			}
			if (geom.size() == 1) {
				Point pt = geom.points.get(0);
				log.info("line with single coordinate converted to point: {}", pt);
				return getGeometry(geom, pt);
			} else {
				// if geom.size() == 0 throws IllegalArgumentException
				return getGeometry(geom, new Line(geom.points));
			}
		} else if (localname.equals(LINEAR_RING)) {
			GeometryGroup geom = parseCoordinates(name);
			if (log.isDebugEnabled() && geom.size() < 4) {
				// ATC 16: LinearRing - control points
				// LinearRing geometry must contain at least 4 coordinate tuples, where the first and last are identical (i.e. they constitute a closed figure).
				// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#LinearRing-ControlPoints
				// NOTE: log level checked at debug level but logged at warn level to be picked up with KmlMetaDataDump
				log.warn("LinearRing geometry fails constraint to contain at least 4 coordinate tuples [ATC 16]");
			}
			if (geom.size() == 1) {
				Point pt = geom.points.get(0);
				log.info("ring with single coordinate converted to point: {}", pt);
				return getGeometry(geom, pt);
			} else if (geom.size() != 0 && geom.size() < 4) {
				log.info("ring with {} coordinates converted to line: {}", geom.size(), geom);
				return getGeometry(geom, new Line(geom.points));
			} else {
				// if geom.size() == 0 throws IllegalArgumentException
				return getGeometry(geom, new LinearRing(geom.points));
			}
		} else if (localname.equals(POLYGON)) {
			// contains one outer ring and 0 or more inner rings
			LinearRing outer = null;
			GeometryGroup geom = new GeometryGroup();
			List<LinearRing> inners = new ArrayList<>();
			while (true) {
				XMLEvent event = stream.nextEvent();
				if (foundEndTag(event, name)) {
					break;
				}
				if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
					StartElement se = event.asStartElement();
					QName qname = se.getName();
					String localPart = qname.getLocalPart();
					if (OUTER_BOUNDARY_IS.equals(localPart)) {
						parseCoordinates(qname, geom);
						int nPoints = geom.size();
						if (log.isDebugEnabled() && nPoints < 4) {
							// ATC 16: LinearRing - control points
							// LinearRing geometry must contain at least 4 coordinate tuples, where the first and last are identical (i.e. they constitute a closed figure).
							// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#LinearRing-ControlPoints
							// NOTE: log level checked at debug level but logged at warn level to be picked up with KmlMetaDataDump
							log.warn("Polygon/LinearRing geometry fails constraint to contain at least 4 coordinate tuples [ATC 16]");
						}
						if (nPoints == 1) {
							Point pt = geom.points.get(0);
							log.info("polygon with single coordinate converted to point: {}", pt);
							return getGeometry(geom, pt);
						} else if (nPoints != 0 && nPoints < 4) {
							// less than 4 points - use line for the shape
							log.info("polygon with {} coordinates converted to line: {}", nPoints, geom);
							Line line = new Line(geom.points);
							return getGeometry(geom, line);
						}
						// if geom.size() == 0 throws IllegalArgumentException
						outer = new LinearRing(geom.points);
					} else if (INNER_BOUNDARY_IS.equals(localPart)) {
						GeometryGroup innerRing = parseCoordinates(qname);
						if (innerRing.size() != 0)
							inners.add(new LinearRing(innerRing.points));
					} else {
						parseGeomAttr(geom, qname, localPart);
					}
				}
			} // while
			if (outer == null) {
				throw new IllegalStateException("Bad poly found, no outer ring");
			}
			return getGeometry(geom, new Polygon(outer, inners));
		}

		return null;
	}

	/**
	 * Map properties of GeometryGroup onto the created {@code GeometryBase}
	 *
	 * @param group
	 * @param geom  GeometryBase, never null
	 * @return filled in GeometryBase object
	 */
	@NotNull
	private static GeometryBase getGeometry(GeometryGroup group, GeometryBase geom) {
		if (group != null) {
			if (group.tessellate != null)
				geom.setTessellate(group.tessellate);
			if (group.extrude != null)
				geom.setExtrude(group.extrude);
			if (group.altitudeMode != null)
				geom.setAltitudeMode(group.altitudeMode);
			if (group.drawOrder != null)
				geom.setDrawOrder(group.drawOrder);
		}
		return geom;
	}

	@Nullable
	private Geodetic2DPoint parseLocation(QName qname) throws XMLStreamException {
		Latitude latitude = null;
		Longitude longitude = null;
		Double altitude = null;
		while (true) {
			XMLEvent event = stream.nextEvent();
			if (foundEndTag(event, qname)) {
				break;
			}
			if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
				StartElement se = event.asStartElement();
				String name = se.getName().getLocalPart();
				if (name.equals(LATITUDE)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							latitude = new Latitude(Double.parseDouble(value), Angle.DEGREES);
						} catch (IllegalArgumentException nfe) {
							log.warn("Invalid latitude value: " + value);
						}
				} else if (name.equals(LONGITUDE)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							longitude = new Longitude(Double.parseDouble(value), Angle.DEGREES);
						} catch (IllegalArgumentException nfe) {
							log.warn("Invalid longitude value: " + value);
						}
				} else if (name.equals(ALTITUDE)) {
					String value = getNonEmptyElementText();
					if (value != null)
						try {
							altitude = Double.valueOf(value);
						} catch (NumberFormatException nfe) {
							log.warn("Invalid altitude value: " + value);
						}
				}
			}
		}

		if (longitude == null && latitude == null) return null;

		if (longitude == null) longitude = new Longitude();
		else if (latitude == null) latitude = new Latitude();

		return altitude == null ? new Geodetic2DPoint(longitude, latitude)
				: new Geodetic3DPoint(longitude, latitude, altitude);
	}

	/**
	 * Find the coordinates element of non-point geometry (line, ring. polygon),
	 * extract the lat/lon/alt properties, and store in a <code>GeometryGroup</code>
	 * object. The element name is used to spot if we leave the "end" of the block.
	 * The stream will be positioned after the element when this returns.
	 *
	 * @param qname the qualified name of this event
	 * @param geom  GeomBase, never null
	 * @throws XMLStreamException       if there is an error with the underlying XML.
	 * @throws IllegalArgumentException error if lat/lon coordinate values are out of range
	 */
	private void parseCoordinates(QName qname, GeometryGroup geom) throws XMLStreamException {
		while (true) {
			XMLEvent event = stream.nextEvent();
			if (foundEndTag(event, qname)) {
				break;
			}
			if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
				final QName name = event.asStartElement().getName();
				final String localPart = name.getLocalPart();
				if (COORDINATES.equals(localPart)) {
					String text = getNonEmptyElementText();
					if (text != null) {
						geom.points = parseCoord(text);
					}
				} else {
					parseGeomAttr(geom, name, localPart);
				}
			}
		}
		if (geom.points == null) geom.points = Collections.emptyList();
	}

	/**
	 * Parse elements of non-point geometry (line, ring. polygon) such as
	 * altitudeMode, extrude, tesselate, and gx:drawOrder.
	 *
	 * @param geom
	 * @param name
	 * @param localPart
	 * @throws XMLStreamException
	 */
	private void parseGeomAttr(GeometryGroup geom, QName name, String localPart) throws XMLStreamException {
		if (ALTITUDE_MODE.equals(localPart)) {
			// Note: handle kml:altitudeMode and gx:altitudeMode
			// if have both forms then use one from KML namespace as done in handleElementExtension()
			if (geom.altitudeMode == null || ms_kml_ns.contains(name.getNamespaceURI()))
				geom.altitudeMode = getNonEmptyElementText();
			else {
				// e.g. qName = {http://www.google.com/kml/ext/2.2}altitudeMode
				log.debug("Skip duplicate value for {}", name);
			}
		} else if (EXTRUDE.equals(localPart)) {
			if (isTrue(stream.getElementText()))
				geom.extrude = Boolean.TRUE; // default=false
		} else if (TESSELLATE.equals(localPart)) {
			if (isTrue(stream.getElementText()))
				geom.tessellate = Boolean.TRUE; // default=false
		} else if (DRAW_ORDER.equals(localPart)) {
			// handle gx:drawOrder (default=0)
			if (NS_GOOGLE_KML_EXT.equals(name.getNamespaceURI())) {
				String value = getNonEmptyElementText();
				if (value != null)
					try {
						geom.drawOrder = Integer.valueOf(value);
					} catch (NumberFormatException nfe) {
						log.warn("Invalid drawOrder value: " + value);
					}
			} else {
				log.warn("invalid namespace for drawOrder: {}", name);
				skipNextElement(stream, name);
			}
		}
	}

	/**
	 * Find/parse the coordinates element of non-point geometry (line, ring. polygon),
	 * extract the lat/lon/alt properties, and return in a <code>GeometryGroup</code>
	 * object. The element name is used to spot if we leave the "end" of the block.
	 * The stream will be positioned after the element when this returns.
	 *
	 * @param qname the qualified name of this event
	 * @return the list coordinates, empty list if no valid coordinates are found
	 * @throws XMLStreamException if there is an error with the underlying XML.
	 */
	@NotNull
	private GeometryGroup parseCoordinates(QName qname) throws XMLStreamException {
		GeometryGroup geom = new GeometryGroup();
		parseCoordinates(qname, geom);
		return geom;
	}

	/**
	 * Find the coordinates element for Point and extract the lat/lon/alt
	 * properties optionally with extrude and altitudeMode. The element name
	 * is used to spot if we leave the "end" of the block. The stream will
	 * be positioned after the element when this returns.
	 *
	 * @param name the qualified name of this event
	 * @return the coordinate (first valid coordinate if found), null if not
	 * @throws XMLStreamException       if there is an error with the underlying XML.
	 * @throws IllegalArgumentException error if coordinates values are out of range
	 */

	@Nullable
	private Point parseCoordinate(QName name) throws XMLStreamException {
		Point rval = null;
		String altitudeMode = null;
		Boolean extrude = null;
		while (true) {
			XMLEvent event = stream.nextEvent();
			if (foundEndTag(event, name)) {
				break;
			}
			if (event.getEventType() == XMLStreamReader.START_ELEMENT) {
				final QName qName = event.asStartElement().getName();
				String localPart = qName.getLocalPart();
				if (COORDINATES.equals(localPart)) {
					String text = getNonEmptyElementText();
					// allow sloppy KML with whitespace appearing before/after
					// lat and lon values; e.g. <coordinates>-121.9921875, 37.265625</coordinates>
					// http://kml-samples.googlecode.com/svn/trunk/kml/ListStyle/radio-folder-vis.kml
					if (text != null) rval = parsePointCoord(text);
				} else if (ALTITUDE_MODE.equals(localPart)) {
					// Note: handle kml:altitudeMode and gx:altitudeMode
					// if have both forms then use one from KML namespace as done in handleElementExtension()
					if (altitudeMode == null || ms_kml_ns.contains(qName.getNamespaceURI()))
						altitudeMode = getNonEmptyElementText();
					else if (!dupAltitudeModeWarn) {
						// e.g. qName = {http://www.google.com/kml/ext/2.2}altitudeMode
						log.debug("Skip duplicate value for {}", qName);
						dupAltitudeModeWarn = true;
					}
				} else if (EXTRUDE.equals(localPart)) {
					if (isTrue(stream.getElementText()))
						extrude = Boolean.TRUE; // default=false
				}
				// Note tessellate tag is not applicable to Point
			}
		}
		if (rval != null) {
			if (altitudeMode != null) rval.setAltitudeMode(altitudeMode);
			if (extrude != null) rval.setExtrude(extrude);
		}
		return rval;
	}

	/**
	 * @throws IllegalArgumentException error if coordinates values are out of range
	 */
	@Nullable
	private static Point parsePointCoord(String coord) {
		List<Point> list = parseCoord(coord);
		if (log.isDebugEnabled() && list.size() != 1) {
			// ATC 14: Point [OGC-07-147r2: cl. 10.3.2]
			// Check that the kml:coordinates element in a kml:Point geometry contains exactly one coordinate tuple
			// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#Point
			// NOTE: log level checked at debug level but logged at warn level to be picked up with KmlMetaDataDump
			log.warn("Point geometry fails constraint to contain exactly one coordinate tuple [ATC 14]");
		}
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * Coordinate parser that matches the loose parsing of coordinates in Google Earth.
	 * KML reference states "Do not include spaces within a [coordinate] tuple" yet
	 * Google Earth allows whitespace to appear anywhere in the input or commas
	 * to appear between tuples (e.g., <code>1,2,3,4,5,6 -> 1,2,3  4,5,6</code>).
	 * <p>
	 * <ul>
	 * <li> Simple state machine parsing keeps track of what part of the coordinate
	 * had been found so far.
	 * <li> Extra whitespace is allowed anywhere in the string.
	 * <li> Invalid text in input is ignored.
	 * </ul>
	 *
	 * @param coord Coordinate string
	 * @return list of coordinates. Returns empty list if no coordinates are valid, never null
	 * @throws IllegalArgumentException error if lat/lon coordinate values are out of range
	 */
	@NotNull
	public static List<Point> parseCoord(String coord) {
		List<Point> list = new ArrayList<>();
		NumberStreamTokenizer st = new NumberStreamTokenizer(coord);
		st.ordinaryChar(',');
		boolean seenComma = false;
		int numparts = 0;
		double elev = 0;
		Longitude lon = null;
		Latitude lat = null;

		if (log.isDebugEnabled() && WHITESPACE_PAT.matcher(coord).find()) {
			// ATC 3: Geometry coordinates
			// http://service.kmlvalidator.com/ets/ogc-kml/2.2/#Geometry-Coordinates
			log.warn("Whitespace found within coordinate tuple [ATC 3]");
			// NOTE: log level checked at debug level but logged at warn level to be picked up with KmlMetaDataDump
		}
		// note the NumberStreamTokenizer may introduce some floating-error (e.g., 5.5 -> 5.499999999999999)
		try {
			while (st.nextToken() != NumberStreamTokenizer.TT_EOF) {
				switch (st.ttype) {
					case NumberStreamTokenizer.TT_WORD:
						//s = "STRING:" + st.sval; // Already a String
						log.warn("ignore invalid string in coordinate: \"" + st.sval + "\"");
						//if (seenComma) System.out.println("\tXXX: WORD: seenComma");
						//if (numparts != 0) System.out.println("\tXXX: WORD: numparts=" + numparts);
						break;

					case NumberStreamTokenizer.TT_NUMBER:
						try {
							if (numparts == 3) {
								if (seenComma) {
									log.warn("comma found instead of whitespace between tuples before " + st.nval);
									// handle commas appearing between tuples
									// Google Earth interprets input with: "1,2,3,4,5,6" as two tuples: {1,2,3}  {4,5,6}.
									seenComma = false;
								}
								// add last coord to list and reset counter
								if (lon != COORD_ERROR)
									list.add(new Point(new Geodetic3DPoint(lon, lat, elev)));
								numparts = 0; // reset state for start of new tuple
							}

							switch (++numparts) {
								case 1:
									if (seenComma) {
										// note: this branch might not be possible: if numparts==0 then seenComma should be false
										lat = new Latitude(st.nval, Angle.DEGREES);
										lon = new Longitude(); // skipped longitude (use 0 degrees)
										numparts = 2;
									} else {
										// starting new coordinate (numparts => 1)
										lon = new Longitude(st.nval, Angle.DEGREES);
										if (log.isDebugEnabled() && Math.abs(st.nval) > 180)
											log.debug("longitude out of range: " + st.nval);
									}
									break;

								case 2:
									if (seenComma) {
										//System.out.println("lat=" + st.nval);
										lat = new Latitude(st.nval, Angle.DEGREES);
									} else {
										if (lon != COORD_ERROR)
											list.add(new Point(new Geodetic2DPoint(
													lon, new Latitude())));
										//else System.out.println("\tERROR: drop bad coord");
										// start new tuple
										lon = new Longitude(st.nval, Angle.DEGREES);
										if (log.isDebugEnabled() && Math.abs(st.nval) > 180)
											log.debug("longitude out of range: " + st.nval);
										numparts = 1;
									}
									break;

								case 3:
									if (seenComma) {
										elev = st.nval;
									} else {
										if (lon != COORD_ERROR)
											list.add(new Point(new Geodetic2DPoint(lon, lat)));
										//else System.out.println("\tERROR: drop bad coord");
										// start new tuple
										lon = new Longitude(st.nval, Angle.DEGREES);
										if (log.isDebugEnabled() && Math.abs(st.nval) > 180)
											log.debug("longitude out of range: " + st.nval);
										numparts = 1;
									}
									break;
							}

							//s = "NUM:" + Double.toString(st.nval);
                            /*
                                    double nval = st.nval;
                                    if (st.nextToken() == StreamTokenizer.TT_WORD && expPattern.matcher(st.sval).matches()) {
                                        s = "ENUM:" + Double.valueOf(Double.toString(nval) + st.sval).toString();
                                    } else {
                                        s = "NUM:" + Double.toString(nval);
                                        st.pushBack();
                                    }
                                    */
						} catch (IllegalArgumentException e) {
							// bad lat/longitude; e.g. out of valid range
							log.error("Invalid coordinate: " + st.nval, e);
							if (numparts != 0) lon = COORD_ERROR;
						}
						seenComma = false; // reset flag
						break;

					default: // single character in ttype
						if (st.ttype == ',') {
							if (!seenComma) {
								// start of next coordinate component
								seenComma = true;
								if (numparts == 0) {
									//System.out.println("\tXXX: WARN: COMMA0: seenComma w/numparts=" + numparts);
									lon = new Longitude(); // skipped longitude (use 0 degrees)
									numparts = 1;
								}
							} else
								// seenComma -> true
								if (numparts == 1) {
									//System.out.println("\tXXX: WARN: COMMA2: seenComma w/numparts=" + numparts);
									lat = new Latitude();  // skipped Latitude (use 0 degrees)
									numparts = 2;
								} else if (numparts == 0) {
									// note this branch may never occur since seenComa=true implies numparts > 0
									//System.out.println("\tXXX: WARN: COMMA1: seenComma w/numparts=" + numparts);
									lon = new Longitude(); // skipped longitude (use 0 degrees)
									numparts = 1;
								}
							//else System.out.println("\tXXX: ** ERROR: COMMA3: seenComma w/numparts=" + numparts);
						} else
							log.warn("ignore invalid character in coordinate string: (" + (char) st.ttype + ")");
						//s = "CHAR:" + String.valueOf((char) st.ttype);
				}
				//System.out.println("\t" + s);
			} // while
		} catch (IOException e) {
			// we're using StringReader. this should never happen
			log.error("Failed to parse coord string: " + coord == null || coord.length() <= 20
					? coord : coord.substring(0, 20) + "...", e);
		}

		// add last coord if valid
		if (numparts != 0 && lon != COORD_ERROR)
			switch (numparts) {
				case 1:
					list.add(new Point(new Geodetic2DPoint(lon, new Latitude())));
					break;
				case 2:
					list.add(new Point(new Geodetic2DPoint(lon, lat)));
					break;
				case 3:
					list.add(new Point(new Geodetic3DPoint(lon, lat, elev)));
			}

		return list;
	}

	/**
	 * Reads the content of a text-only element allowing empty string to be preserved.
	 * Attempts to skip element if throws XMLStreamException.
	 * @param name
	 *            the qualified name of this event
	 * @return String
	 * @throws XMLStreamException
	 *             if there is an error with the underlying XML.
	 */
	@Nullable
	private String getElementEmptyText(QName name) throws XMLStreamException {
		try {
			String elementText = stream.getElementText();
			return elementText == null || elementText.isEmpty() ? elementText : elementText.trim();
		} catch (XMLStreamException e) {
			log.warn("Unable to parse " + name.getLocalPart()
					+ " as text element: " + e);
			skipNextElement(stream, name);
			return null;
		}
	}

	/**
	 * @param e
	 * @return
	 */
	@Nullable
	private IGISObject handleEndElement(XMLEvent e) {
		EndElement ee = e.asEndElement();
		String localname = ee.getName().getLocalPart();

		if (ms_containers.contains(localname)) {
			return new ContainerEnd();
		}

		return null;
	}

	private static class GeometryGroup {
		List<Point> points;
		String altitudeMode;
		Integer drawOrder;
		Boolean extrude;
		Boolean tessellate;

		int size() {
			return points == null ? 0 : points.size();
		}

		public String toString() {
			return String.valueOf(points);
		}
	}

}
