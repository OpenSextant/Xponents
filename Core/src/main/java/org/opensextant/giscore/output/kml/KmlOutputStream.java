/****************************************************************************************
 *  KmlOutputStream.java
 *
 *  Created: Jan 30, 2009
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
package org.opensextant.giscore.output.kml;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.geodesy.Geodetic2DCircle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.SafeDateFormat;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.KmlInputStream;
import org.opensextant.giscore.input.kml.UrlRef;
import org.opensextant.giscore.output.XmlOutputStreamBase;
import org.opensextant.giscore.utils.Args;
import org.opensextant.giscore.utils.Color;
import org.opensextant.giscore.utils.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The KML output stream creates a result KML file using the given output
 * stream. It uses STaX methods for writing the XML elements to avoid building
 * an in-memory DOM, which reduces the memory overhead of creating the document.
 * <p>
 * KmlOutputStream produces a valid KML Document with respect to the KML 2.2 specification.
 * <p>
 * For KML, each incoming element generally adds another full element to the
 * output document. There are a couple of distinct exceptions. These are the
 * Style selectors. The style selectors instead appear before the matched
 * feature, and the KML output stream buffers these until the next feature is
 * seen. At that point the styles are output after the element's attributes and
 * before any content.
 * <p>
 * The geometry visitors are invoked by the feature visitor via the Geometry
 * accept method.
 * <p>
 * Following KML tags are supported: address(<A href="#address">*</A>), description,
 * Document, ExtendedData, Folder, Line, NetworkLink, phoneNumber(<A href="#address">*</A>),
 * Placemark, Point, Polygon, Schema, snippet, etc.
 * <p>
 * Elements such as atom:author, atom:link, xal:AddressDetails, and gx: extensions
 * must be added to the Feature object as {@link Element} objects.
 * Supported gx extensions include gx:altitudeMode, gx:altitudeOffset, and gx:drawOrder,
 * gx:TimeStamp, gx:TimeSpan, gx:Tour, gx:Track, and gx:MultiTrack. If gx:Tour element is
 * defined then that element can have child elements such as gx:FlyTo, gx:SoundCue,
 * gx:AnimatedUpdate, gx:Wait, etc.
 * Other gx extensions are not yet supported and will be added when needed.
 * <p>
 * If description contains HTML markup or special characters (e.g. '&amp;', '&lt;', '>', etc.)
 * then a CDATA block will surround the unescaped text in the generated KML output.

 * <b>Notes/Limitations:</b>
 * <ul>
 * <li><b>"address"</b> - Note phoneNumber and address fields do not explicitly exist on the Feature
 * or Common object but are supported if added as an {@link Element} with the
 * {@code http://www.opengis.net/kml/2.2} namespace. This is how the
 * {@link KmlInputStream} stores these fields on a Feature object.
 * </li>
 * <li> A few tags are not yet supported on features so are omitted from output:
 * {@code Metadata}.
 * </li>
 * <li> Limited support for NetworkLinkControl.
 * </li>
 * <li> Partial support for PhotoOverlay. Omits ViewVolume, ImagePyramid, and shape properties.
 * </li>
 * <li> IconStyle and LineStyle are fully supported except for colorMode = random which is not represented
 * in the Style class. Only PolyStyle has support for the random colorMode.
 * </li>
 * <li> Warns if shared styles appear in Folders. According to OGC KML specification
 * shared styles shall only appear within a Document [OGC 07-147r2 section 6.4].
 * </li>
 * </ul>
 *
 * @author J.Mathews
 * @author DRAND
 */
public class KmlOutputStream extends XmlOutputStreamBase implements IKml {

    private static final Logger log = LoggerFactory.getLogger(KmlOutputStream.class);
    private static final boolean debug = log.isDebugEnabled();

    private static final Namespace KML_NAMESPACE = Namespace.getNamespace(KML_NS, "kml");
    private static final String ISO_DATE_FMT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private transient SafeDateFormat dateFormatter;

    private static final int NUM_CIRCLE_POINTS;

    static {
        int numPoints = 32;
        // store preference for # points in generated circles [default = 32]
        // note: generated number points is n+1 if n > 2 since first and last points must be the same
        String value = System.getProperty("giscore.circle.numPoints");
        if (StringUtils.isNotBlank(value)) {
            try {
                numPoints = Integer.parseInt(value);
                if (numPoints < 1) numPoints = 1; // cannot allow less than 1 points. More than 2 is preferred.
            } catch (NumberFormatException nfe) {
                log.warn("Invalid value for giscore.circle.numPoints " + value);
            }
        }
        NUM_CIRCLE_POINTS = numPoints;
    }

    private int numberCirclePoints = NUM_CIRCLE_POINTS;

    /**
     * prefix associated with gx extension namespace if such namespace is provided
     * in root Document declarations
     */
    private Namespace gxNamespace;

    /**
     * current feature being output
     */
    private Feature targetFeature;

    /**
     * Ctor
     *
     * @param stream   OutputStream to decorate as a KmlOutputStream
     * @param encoding the encoding to use, if null default encoding (UTF-8) is assumed
     * @throws XMLStreamException if error occurs creating output stream
     */
    public KmlOutputStream(OutputStream stream, String encoding) throws XMLStreamException {
       this(stream, new Object[]{encoding});        
    }

    /**
     * Standard ctor
     * @param stream
     * @param args
     * @throws XMLStreamException 
     */
    public KmlOutputStream(OutputStream stream, Object[] args) throws XMLStreamException {
    	Args argv = new Args(args);
    	String encoding = (String) argv.get(String.class, 0);
    	init(stream, encoding);
    	if (StringUtils.isBlank(encoding))
            writer.writeStartDocument();
        else
            writer.writeStartDocument(encoding, "1.0");
        // All line breaks must have been normalized on input to #xA (\n)
        writer.writeCharacters("\n");
        writer.writeStartElement(KML);
        writer.writeDefaultNamespace(KML_NS);
    }
    
    /**
     * Ctor
     *
     * @param stream OutputStream to decorate as a KmlOutputStream
     * @throws XMLStreamException if error occurs creating output stream
     */
    public KmlOutputStream(OutputStream stream) throws XMLStreamException {
        this(stream, new Object[0]);
    }

    /**
     * Close this writer and free any resources associated with the
     * writer.  This also closes the underlying output stream.
     *
     * @throws IOException if an error occurs
     */
    @Override
    public void close() throws IOException {
        try {
            if (writerOpen) {
                writer.writeEndElement();
                writer.writeCharacters("\n");
                writer.writeEndDocument();
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        } finally {
            super.close();
        }
    }

    /**
     * Flush and close XMLStreamWriter but not the outputStream
     *
     * @throws IOException if an error occurs
     */
    public void closeWriter() throws IOException {
        if (writerOpen)
            try {
                try {
                    writer.writeEndElement();
                    writer.writeCharacters("\n");
                    writer.writeEndDocument();
                } finally {
                    writer.flush();
                    writer.close();
                    // don't call super.close() which closes the outputStream
                }
            } catch (XMLStreamException e) {
                throw new IOException(e);
            } finally {
                writerOpen = false;
            }
    }

    /**
     * Closes the underlying stream typically done after calling closeWriter
     */
    public void closeStream() {
        IOUtils.closeQuietly(stream);
    }

    /**
	 * Visit a DocumentStart object.
	 * This can only be done once immediately after creating the KmlOutputStream
	 * and before writing any Features, Containers, etc.
     *
     * @param documentStart
	 * @throws IllegalStateException if there is an error with the underlying XML (e.g. current XML
	 *                               state does not allow Namespace writing)
	 *                               or attempt to call visit(DocumentStart) more than once
     */
    @Override
    public void visit(DocumentStart documentStart) {
        try {
            boolean needNewline = false;
            // Add any additional namespaces to the most proximate containing element
            for (Namespace ns : documentStart.getNamespaces()) {
                String prefix = ns.getPrefix();
                if (StringUtils.isNotBlank(prefix)) {
                    final String nsURI = ns.getURI();
                    writer.writeNamespace(prefix, nsURI);
                    needNewline = true;
                    namespaces.put(prefix, nsURI);
                    if (gxNamespace == null && nsURI.startsWith(NS_GOOGLE_KML_EXT_PREFIX)) {
                        gxNamespace = ns;
                    }
                }
            }
            if (needNewline) writer.writeCharacters("\n");
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Visit a ContainerEnd object
     *
     * @param containerEnd
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(ContainerEnd containerEnd) {
        try {
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Visit a ContainerStart object
     *
     * @param containerStart
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(ContainerStart containerStart) {
        try {
            String tag = containerStart.getType();
            if (!IKml.DOCUMENT.equals(tag) && !IKml.FOLDER.equals(tag)) {
                // Folder has more restrictions than Document in KML (e.g. shared styles cannot appear in Folders)
                // so if container is unknown then use Document type by default.
                tag = IKml.FOLDER.equalsIgnoreCase(tag) ? IKml.FOLDER : IKml.DOCUMENT;
            }
            /*
               if (IKml.FOLDER.equals(tag) && !containerStart.getStyles().isEmpty()) {
                   // TODO: if Folder has shared styles then must write out Document element instead
                   log.debug("Shared styles cannot appear in Folders. Convert Folder to Document element"); // ATC 7: OGC-07-147r2: cl. 6.4
                   tag = IKml.DOCUMENT;
               }
               */
            writer.writeStartElement(tag);
            List<Element> elements = handleAttributes(containerStart, tag);

            for (Element el : elements) {
				if (el.getNamespaceURI().equals(NS_GOOGLE_KML_EXT)) {
                    // only gx:Tour supported in this context (substitutionGroup = kml:AbstractFeatureGroup)
                    // which itself can have child elements; e.g. gx:FlyTo, gx:SoundCue, gx:AnimatedUpdate, gx:Wait, etc.
                    if ("Tour".equals(el.getName())) {
                    handleXmlElement(el);
                    }
                    // otherwise extension does not apply to this context
                } else {
                    // what non-kml namespaces can we support without creating invalid KML other than gx: and atom: ??
                    // suppress atom:attributes in post-xml element dump
                    // atoms handled in handleAttributes
                    log.debug("Handle XML element " + el.getName() + " as comment");
                    writeAsComment(el);
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleRegion(TaggedMap region) {
        if (region != null && !region.isEmpty()) {
            try {
                // postpone writing out LAT_LON_BOX element until there is a child element
                // likewise don't write Region element unless we have LAT_LON_BOX or Lod
                LinkedList<String> waitingList = new LinkedList<>();
                waitingList.add(REGION);
                waitingList.add(LAT_LON_ALT_BOX);
                handleTaggedElement(NORTH, region, waitingList);
                handleTaggedElement(SOUTH, region, waitingList);
				// handle east/west as special case
				// +180 might be normalized to -180.
				// If east = -180 and west >=0 then Google Earth invalidate the Region and never be active.
				Double east = region.getDoubleValue(EAST);
				Double west = region.getDoubleValue(WEST);
				if (east != null && west != null && west >= 0 && Double.compare(east, -180) == 0) {
					while (!waitingList.isEmpty()) {
						writer.writeStartElement(waitingList.removeFirst());
					}
					handleSimpleElement(EAST, "180");
					handleSimpleElement(WEST, formatDouble(west));
				} else {
					handleTaggedElement(EAST, region, waitingList);
					handleTaggedElement(WEST, region, waitingList);
				}
				handleTaggedElement(MIN_ALTITUDE, region, waitingList);
                handleTaggedElement(MAX_ALTITUDE, region, waitingList);
                // if altitudeMode is invalid then it will be omitted
                AltitudeModeEnumType altMode = AltitudeModeEnumType.getNormalizedMode(region.get(ALTITUDE_MODE));
                if (altMode != null) {
                    /*
                          if (!waitingList.isEmpty()) {
                              writer.writeStartElement(REGION);
                             writer.writeStartElement(LAT_LON_ALT_BOX);
                             waitingList.clear();
                          }
                          handleAltitudeMode(altMode);
                          */
                    if (waitingList.isEmpty()) {
                        handleAltitudeMode(altMode);
                    }
                    // otherwise don't have LatLonAltBox so AltitudeMode has no meaning
                }
                if (waitingList.isEmpty()) {
                    writer.writeEndElement(); // end LatLonAltBox
                } else {
                    waitingList.remove(1); // remove LatLonAltBox from consideration
                    // we still have Region in waiting list
                }
                // next check Lod
                waitingList.add(LOD);
                handleTaggedElement(MIN_LOD_PIXELS, region, waitingList);
                handleTaggedElement(MAX_LOD_PIXELS, region, waitingList);
                handleTaggedElement(MIN_FADE_EXTENT, region, waitingList);
                handleTaggedElement(MAX_FADE_EXTENT, region, waitingList);
                if (waitingList.isEmpty())
                    writer.writeEndElement(); // end Lod
                //if (!waitingList.isEmpty()) System.out.println("XXX: *NO* LOD in region..."); // debug
                //else System.out.println("XXX: got LOD in region..."); // debug
                // if have 2 elements in map then have neither Lod nor Region to end
                // if have 0 or 1 {Lod} elements in list then we need to end of Region
                if (waitingList.size() < 2)
                    writer.writeEndElement(); // end Region
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void handleAbstractView(TaggedMap viewGroup) {
        if (viewGroup != null && !viewGroup.isEmpty()) {
            String tag = viewGroup.getTag();
            if (!CAMERA.equals(tag) && !LOOK_AT.equals(tag)) {
                log.error("Invalid AbstractView type: " + viewGroup);
                return;
            }
            try {
                writer.writeStartElement(tag); // LookAt or Camera

                // Declare gx:TimeStamp and gx:TimeSpan elements are extensions to AbstractViewObjectExtensionGroup
                String value = viewGroup.get("gx:TimeStamp");
                if (value != null) {
                    writer.writeCharacters("\n");
                    handleGxElement("TimeStamp", value);
                } else {
                    /*
                         TimeSpan is a complex element not a simple element
                          <gx:TimeSpan>
                             <begin>2010-05-28T02:02:09Z</begin>
                             <end>2010-05-28T02:02:56Z</end>
                          </gx:TimeSpan>

                         can represent such complex elements as tags as such:
                         gx:TimeSpan/begin=2010-05-28T02:02:09Z, gx:TimeSpan/end=2010-05-28T02:02:56Z}
                         */
                    String beginValue = StringUtils.trimToNull(viewGroup.get("gx:TimeSpan/begin"));
                    String endValue = StringUtils.trimToNull(viewGroup.get("gx:TimeSpan/end"));
                    if (beginValue != null || endValue != null) {
                        writer.writeCharacters("\n");
                        if (gxNamespace != null)
                            writer.writeStartElement(gxNamespace.getPrefix(),
                                    "TimeSpan", gxNamespace.getURI());
                        else {
                            writer.writeStartElement("TimeSpan");
                            writer.writeDefaultNamespace(NS_GOOGLE_KML_EXT);
                        }
                        handleNonEmptySimpleElement("begin", beginValue);
                        handleNonEmptySimpleElement("end", endValue);
                        writer.writeEndElement();
                        writer.writeCharacters("\n");
                    }
                }

                /*
                    Camera | LookAt
                      <element ref="kml:longitude" minOccurs="0"/>
                      <element ref="kml:latitude" minOccurs="0"/>
                      <element ref="kml:altitude" minOccurs="0"/>
                      <element ref="kml:heading" minOccurs="0"/>
                      <element ref="kml:tilt" minOccurs="0"/>
                      <element ref="kml:roll" minOccurs="0"/> (*) // Camera only
                      <element ref="kml:range" minOccurs="0"/> (*) // Lookat only
                      <element ref="kml:altitudeModeGroup" minOccurs="0"/>
                 */

                handleTaggedElement(LONGITUDE, viewGroup);
                handleTaggedElement(LATITUDE, viewGroup);
                handleTaggedElement(ALTITUDE, viewGroup);
                handleTaggedElement(HEADING, viewGroup);
                handleTaggedElement(TILT, viewGroup);
                if (CAMERA.equals(tag)) {
                    handleTaggedElement(ROLL, viewGroup); // Camera Only
                }
                if (LOOK_AT.equals(tag)) {
                    handleTaggedElement(RANGE, viewGroup); // LookAt Only
                }

                // if altitudeMode is invalid then it will be omitted
                AltitudeModeEnumType altMode = AltitudeModeEnumType.getNormalizedMode(viewGroup.get(ALTITUDE_MODE));
                handleAltitudeMode(altMode);

                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void handleGxElement(String name, String value) throws XMLStreamException {
        if (gxNamespace != null)
            writer.writeStartElement(gxNamespace.getPrefix(), name, gxNamespace.getURI());
        else {
            //writer.writeStartElement(name);
            //writer.writeDefaultNamespace(NS_GOOGLE_KML_EXT); // write explicit namespace
            writer.writeStartElement("gx", name, NS_GOOGLE_KML_EXT);
            writer.writeNamespace("gx", NS_GOOGLE_KML_EXT);
        }
        handleCharacters(value);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    // Thread-safe date formatter helper method
	@NotNull
    private SafeDateFormat getDateFormatter() {
        if (dateFormatter == null) {
            dateFormatter = new SafeDateFormat(ISO_DATE_FMT);
        }
        return dateFormatter;
    }

    /**
     * Common code for outputting feature data that is held for both containers
     * and other features like Placemarks and Overlays.
     *
     * @param feature       Common feature object for whom attributes will be written
     * @param containerType type of Container were visiting if Feature is a Document or Folder otherwise null
     * @return list of elements initialized with getElement() and removed those elements that were processed, empty list
     *         if no non-kml elements left.
	 * @throws IllegalStateException if there is an error with the underlying XML
     */
    private List<Element> handleAttributes(Common feature, String containerType) {
        try {
	        List<Element> elements = feature.hasElements() ?
                    new LinkedList<>(feature.getElements()) : Collections.emptyList();

            String id = feature.getId();
            if (id != null) writer.writeAttribute(ID, id);
            handleNonNullSimpleElement(NAME, feature.getName());
            Boolean visibility = feature.getVisibility();
            if (visibility != null && !visibility)
                handleSimpleElement(VISIBILITY, "0"); // default=1

            if (feature instanceof IContainerType && ((IContainerType) feature).isOpen()) {
                // only applicable to Document, Folder, or NetworkLink
                handleSimpleElement(OPEN, "1"); // default=0
            }

            // handle atom attributes and gx:balloonVisibility if defined and remove from list
            Element author = null;
            Element link = null;
            Element addressDetails = null;
            Element balloonVisibility = null;
            String address = null;
            String phoneNumber = null;
            for (Iterator<Element> it = elements.iterator(); it.hasNext(); ) {
                Element el = it.next();
                if (KML_NS.equals(el.getNamespaceURI())) {
                    if (ADDRESS.equals(el.getName())) {
                        address = el.getText();
                        it.remove(); // remove from list - marked as processed
                    } else if (PHONE_NUMBER.equals(el.getName())) {
                        phoneNumber = el.getText();
                        it.remove(); // remove from list - marked as processed
                    }
                } else if (NS_OASIS_XAL.equals(el.getNamespaceURI()) &&
                        ADDRESS_DETAILS.equals(el.getName())) {
                    addressDetails = el;
                    it.remove(); // remove from list - marked as processed
                } else if (NS_GOOGLE_KML_EXT.equals(el.getNamespaceURI()) &&
                        "balloonVisibility".equals(el.getName())) {
                    balloonVisibility = el;
                    it.remove(); // remove from list - marked as processed
                }
            }
            if (author != null) handleXmlElement(author);
            if (link != null) handleXmlElement(link);
            if (address != null) handleNonEmptySimpleElement(ADDRESS, address);
            if (phoneNumber != null) handleNonEmptySimpleElement(PHONE_NUMBER, phoneNumber);
            if (addressDetails != null) handleXmlElement(addressDetails);

            // Use snippet (lower-case 's'). Snippet deprecated in 2.2 (see OGC kml22.xsd)
			// allow empty string for empty element
			String snippet = StringUtils.trim(feature.getSnippet());
			if (snippet != null) {
				if (snippet.isEmpty())
					writer.writeEmptyElement(SNIPPET);
				else
					handleSimpleElement(SNIPPET, snippet);
			}

            handleNonNullSimpleElement(DESCRIPTION, feature.getDescription());
            handleAbstractView(feature.getViewGroup()); // LookAt or Camera AbstractViewGroup
			DateTime startTime = feature.getStartDate();
			DateTime endTime = feature.getEndDate();
            if (startTime != null) {
                if (endTime == null) {
                    // start time with no end time
                    writer.writeStartElement(TIME_SPAN);
					handleSimpleElement(BEGIN, startTime.toString());
                } else if (endTime.equals(startTime)) {
                    // start == end represents a Timestamp
                    // note that having feature with a timeSpan with same begin and end time
                    // is identical to one with a timestamp of same time in Google Earth client.
                    writer.writeStartElement(TIME_STAMP);
					handleSimpleElement(WHEN, startTime.toString());
                } else {
                    // start != end represents a TimeSpan
                    writer.writeStartElement(TIME_SPAN);
					handleSimpleElement(BEGIN, startTime.toString());
					handleSimpleElement(END, endTime.toString());
                }
                writer.writeEndElement();
            } else if (endTime != null) {
                // end time with no start time
                writer.writeStartElement(TIME_SPAN);
				handleSimpleElement(END, endTime.toString());
                writer.writeEndElement();
            }

            handleNonNullSimpleElement(STYLE_URL, feature.getStyleUrl());

            // if feature has inline style needs to write here
            if (feature instanceof Feature) {
                Feature f = (Feature) feature;
                handleStyle(f.getStyle());
            } else if (feature instanceof ContainerStart) {
                ContainerStart cs = (ContainerStart) feature;
                // if Container then shared styles should be output here...
                for (StyleSelector style : cs.getStyles()) {
                    handleStyle(style);
                }
            }

            handleRegion(feature.getRegion());
            handleExtendedData(feature);

            if (balloonVisibility != null) {
                // gx:balloonVisibility extends kml:AbstractFeatureSimpleExtensionGroup
                // and follows kml:ExtendedData
                handleXmlElement(balloonVisibility);
            }

            // todo: other gx extensions that extend features should probably be output here and removed from the list
            // check out what other extended tags are possible..
            // otherwise invalid elements will create non-compliant KML output

            return elements;
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleStyle(StyleSelector style) throws XMLStreamException {
        if (style != null) {
            if (style instanceof Style) {
                handle((Style) style);
            } else if (style instanceof StyleMap) {
                handleStyleMap((StyleMap) style);
            }
        }
    }

    /**
     * Write an element
     *
     * @param el the element, never null
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleXmlElement(Element el) throws XMLStreamException {
        handleXmlElement(el, KML_NAMESPACE);
    }

    private void handleExtendedData(Row feature) throws XMLStreamException {
        /*
            <element name="ExtendedData" type="kml:ExtendedDataType"/>
                <complexType name="ExtendedDataType" final="#all">
                  <sequence>
                    <element ref="kml:Data" minOccurs="0" maxOccurs="unbounded"/>
                    <element ref="kml:SchemaData" minOccurs="0" maxOccurs="unbounded"/>
                    <any namespace="##other" processContents="lax" minOccurs="0"
                      maxOccurs="unbounded"/>
                  </sequence>
                </complexType>
           */
        if (feature.hasExtendedData()) {
            URI schema = feature.getSchema();
            writer.writeStartElement(EXTENDED_DATA);
            // TODO: ExtendedData can contain unbounded # of Data, SchemaData, and non-KML namespace children
            // For now only supporting either a single set of Data or SchemaData elements followed
            // by any number of/ extended arbitrary extended elements.
            if (schema == null) {
                for (SimpleField field : feature.getFields()) {
                    Object value = feature.getData(field);
                    if (value != null) {
                        writer.writeStartElement(DATA);
                        writer.writeAttribute(NAME, field.getName());
                        handleSimpleElement(VALUE, formatValue(field.getType(),
                                value));
                        writer.writeEndElement();
                    }
                }
            } else {
                writer.writeStartElement(SCHEMA_DATA);
                writer.writeAttribute(SCHEMA_URL, schema.toString());
                for (SimpleField field : feature.getFields()) {
                    Object value = feature.getData(field);
                    if (value != null) {
                        writer.writeStartElement(SIMPLE_DATA);
                        writer.writeAttribute(NAME, field.getName());
                        handleCharacters(formatValue(field.getType(),
                                value));
                        writer.writeEndElement();
                    }
                }
                writer.writeEndElement();
            }
            // handle arbitrary XML non-KML namespace elements
            for (Element e : feature.getExtendedElements()) {
                final Namespace namespace = e.getNamespace();
                final String namespaceURI = namespace.getURI();
                if (namespaceURI.isEmpty() || KML_NS.equals(namespaceURI)) {
                    log.warn("ExtendedData must have explicit non-kml namespace: " + namespace);
                    writeAsComment(e);
                } else {
                    handleXmlElement(e, Namespace.getNamespace(KML_NS));
                }
            }
            writer.writeEndElement();
        }
    }


    /**
     * Format date in ISO format and trim milliseconds field if 0
     *
     * @param date
     * @return formatted date (e.g. 2003-09-30T00:00:06.930Z)
     */
    private String formatDate(Date date) {
        // NOTE: this eliminates alternative date forms (date gYearMonth gYear)
        // all of which are converted to ISO xsd:dateTime format
        String d = getDateFormatter().format(date);
        if (d.endsWith(".000Z")) {
            // trim milliseconds field
            d = d.substring(0, d.length() - 5) + "Z";
        }
        return d;
    }

    /**
     * Format a value according to the type, defaults to using toString.
     *
     * @param type the type, assumed not {@code null}
     * @param data the data, may be a number of types, but must be coercible to
     *             the given type
     * @return a formatted value
     * @throws IllegalArgumentException if values cannot be formatted
     *                                  using specified data type.
     */
    private String formatValue(Type type, Object data) {
        if (data == null) {
            return "";
        } else if (Type.DATE.equals(type)) {
            Object val = data;
            if (val instanceof String) {
                try {
                    // Try converting to ISO?
		// alternate date forms (date gYearMonth gYear) will be preserved
		// but non-standard dateTime variations will be normalized to YYYY-MM-DD'T'HH:MM:SS.SSS'Z'
		val = new DateTime((String)data).toString();
                } catch (ParseException e) {
                    // Fall through
                } catch (RuntimeException e) {
                    // Fall through
                }
            }
            if (val instanceof Date) {
                return formatDate((Date) val);
            } else {
                return val.toString();
            }
        } else if (Type.DOUBLE.equals(type) || Type.FLOAT.equals(type)) {
            if (data instanceof String) {
                return (String) data;
            }

            if (data instanceof Number) {
                return data.toString();
            } else {
                throw new IllegalArgumentException("Data that cannot be coerced to float: " + data);
            }
        } else if (Type.INT.equals(type) || Type.SHORT.equals(type)
                || Type.UINT.equals(type) || Type.USHORT.equals(type) || Type.LONG.equals(type)) {
            if (data instanceof String) {
                return (String) data;
            }

            if (data instanceof Number) {
                return data.toString();
            } else {
                throw new IllegalArgumentException("Data that cannot be coerced to int: " + data);
            }
        } else {
            return data.toString();
        }
    }

    /**
     * Visit a Feature including its geometry and any child XML Elements.
     *
     * @param feature
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(Feature feature) {
        // need feature available for handling gx extensions
        targetFeature = feature;
        try {
            String tag = feature.getType();
            writer.writeStartElement(tag);

            List<Element> elements = handleAttributes(feature, tag);
            if (feature instanceof Overlay) {
                handleOverlay((Overlay) feature);
            } else if (feature.getGeometry() != null) {
                feature.getGeometry().accept(this);
            } else if (feature instanceof NetworkLink) {
                handleNetworkLink((NetworkLink) feature);
            }
            Element geometryExt = null;
            for (Element el : elements) {
                if (el.getNamespaceURI().equals(NS_GOOGLE_KML_EXT)) {
                    String name = el.getName();
                    // only gx:Track and gx:MultiTrack supported in this context
                    // substitutionGroup = kml:AbstractGeometryGroup
                    if ("Track".equals(name) || "MultiTrack".equals(name)) {
                        if (geometryExt == null) {
                            geometryExt = el; // keep track if already have a geometry
                            if (feature.getGeometry() != null) {
                                // cannot have both a geometry and AbstractGeometryGroup extension (e.g. gx:Track) in a Feature
                                log.error("cannot have both a geometry and {} in a Feature", name);
                            } else {
                                // geometry extensions: gx:Track or gx:MultiTrack
                    handleXmlElement(el);
                            }
                        } else log.error("cannot have multiple AbstractGeometryGroup extensions in a Feature");
                    } // otherwise extension does not apply to this context
                } else {
                    // what non-kml namespaces can we support without creating invalid KML other than gx: and atom: ??
                    // suppress atom:attributes in post-xml element dump
                    // atoms handled in handleAttributes
					log.debug("Handle XML element {} as comment", el.getName());
                    writeAsComment(el);
                }
            }
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
		} finally {
            targetFeature = null;
        }
    }

    /**
     * Visit a row. Output as a Placemark with ExtendedData without geometry
     *
     * @param row Row to visit
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(Row row) {
        if (row != null && row.hasExtendedData()) {
            try {
                writer.writeStartElement(PLACEMARK);
                handleExtendedData(row);
                writer.writeEndElement();
                writer.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Visit an XML Element.
     *
     * @param element Element to visit, never null
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(Element element) {
        try {
            if (gxNamespace != null && gxNamespace.getPrefix().equals(element.getPrefix())
                    || element.getNamespaceURI().startsWith(NS_GOOGLE_KML_EXT_PREFIX)) {
                handleXmlElement(element);
            } else {
                // REVIEW: handle non-kml element as comment for now .. any other namespaces to support??
                if (debug) {
                    String prefix = element.getPrefix();
                    String name = element.getName();
                    if (StringUtils.isNotEmpty(prefix)) name += " " + element.getNamespace();
                    log.debug("Handle XML element " + name + " as comment");
                }
                writeAsComment(element);
            }
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Handle elements specific to a network link feature.
     *
     * @param link NetworkLink to be handled
     * @throws XMLStreamException if there is an error with the underlying XML.
     */
    private void handleNetworkLink(NetworkLink link) throws XMLStreamException {
		if(link.isRefreshVisibility()) {
			handleSimpleElement(REFRESH_VISIBILITY, "1"); // boolean [default=0]
		}
		if (link.isFlyToView()) {
			handleSimpleElement(FLY_TO_VIEW, "1"); // boolean [default=0]
		}
        handleLinkElement(LINK, link.getLink());
    }

    /**
     * Handle elements specific to an overlay feature
     *
     * @param overlay Overlay to be handled
     * @throws XMLStreamException if there is an error with the underlying XML.
     */
    private void handleOverlay(Overlay overlay) throws XMLStreamException {
        handleColor(COLOR, overlay.getColor());
        int order = overlay.getDrawOrder();
        // don't bother to output drawOrder element if is the default value (0)
        if (order != 0) handleSimpleElement(DRAW_ORDER, Integer.toString(order));
        handleLinkElement(ICON, overlay.getIcon());

        if (overlay instanceof GroundOverlay) {
            GroundOverlay go = (GroundOverlay) overlay;
            handleNonNullSimpleElement(ALTITUDE, go.getAltitude());
            // if null or default clampToGround then ignore
            handleAltitudeMode(go.getAltitudeMode());
            // postpone writing out LAT_LON_BOX element until there is a child element
            Queue<String> waitingList = new LinkedList<>();
            waitingList.add(LAT_LON_BOX);
            // verify constraints: 1) kml:north > kml:south, and 2) kml:east > kml:west
            // Reference: OGC-07-147r2: cl. 11.3.2 ATC 11: LatLonBox
            Double north = go.getNorth();
            Double south = go.getSouth();
            if (north != null && south != null && north < south) {
                Double t = north;
                north = south;
                south = t;
                log.debug("fails LatLonBox constraint: north > south");
            }
            Double east = go.getEast();
            Double west = go.getWest();
            // if bounds cross IDL (anti-meridian) then GE wraps wrong direction
            if (east != null && west != null && east < west) {
                if (go.crossDateLine()) {
                    log.debug("GroundOverlay crosses IDL");
                    if (west > 0) west -= 360;
                    // Address bug in GE https://code.google.com/p/earth-issues/issues/detail?id=1145
                    // TODO: this workaround works in GE 6.0 through 7.1 but intentionally violates
                    // the schema validation constraint kml:west value >= -180.
                    // This hack must be consistent with handling in KmlInputStream.handleLatLonBox()
                    // e.g. west = 125 (Korea), east = -117 (San Francisco) then hack in Google subtracts 360 from west (e.g. -235)
                } else {
                    Double t = east;
                    east = west;
                    west = t;
                    log.debug("fails LatLonBox constraint: east > west");
                }
            }
            handleNonNullSimpleElement(NORTH, north, waitingList);
            handleNonNullSimpleElement(SOUTH, south, waitingList);
            handleNonNullSimpleElement(EAST, east, waitingList);
            handleNonNullSimpleElement(WEST, west, waitingList);
            handleNonNullSimpleElement(ROTATION, go.getRotation(), waitingList);
            if (waitingList.isEmpty()) writer.writeEndElement();
        } else if (overlay instanceof PhotoOverlay) {
            PhotoOverlay po = (PhotoOverlay) overlay;
            // TODO: fill in other properties (ViewVolume, ImagePyramid, Point, shape)
            handleNonNullSimpleElement(ROTATION, po.getRotation());
            Geometry geom = po.getGeometry();
            if (geom != null && geom.getClass() == Point.class)
                visit((Point) geom); // handle Point
        } else if (overlay instanceof ScreenOverlay) {
            ScreenOverlay so = (ScreenOverlay) overlay;
            handleXY(OVERLAY_XY, so.getOverlay());
            handleXY(SCREEN_XY, so.getScreen());
            handleXY(ROTATION_XY, so.getRotation());
            handleXY(SIZE, so.getSize());
            handleNonNullSimpleElement(ROTATION, so.getRotationAngle());
        }
    }

    private void handleNonNullSimpleElement(String tag, Object content, Queue<String> waitingList) throws XMLStreamException {
        if (content != null) {
            if (waitingList != null && !waitingList.isEmpty())
                writer.writeStartElement(waitingList.remove());
            handleSimpleElement(tag, content);
        }
    }

    // elements associated with Kml22 LinkType in sequence order for Icon, Link, and Url elements
    private static final String[] LINK_TYPE_TAGS = {
            HREF,
            REFRESH_MODE,
            REFRESH_INTERVAL,
            VIEW_REFRESH_MODE,
            VIEW_REFRESH_TIME,
            VIEW_BOUND_SCALE,
            VIEW_FORMAT,
            HTTP_QUERY
    };

    private void handleLinkElement(String elementName, TaggedMap map) throws XMLStreamException {
        if (map == null || map.isEmpty())
            return;
        writer.writeStartElement(elementName);
        for (String tag : LINK_TYPE_TAGS) {
            String val = map.get(tag);
            if (val != null) {
				if(val.isEmpty()) {
					// need to support empty tag for viewFormat
					if (VIEW_FORMAT.equals(tag)) writer.writeEmptyElement(VIEW_FORMAT);
					// otherwise ignore empty values
					continue;
				}
                if (tag.equals(HREF)) {
                    if (val.startsWith("kmz") && val.indexOf("file=") > 0) {
                        // replace internal URI (which is used to associate link with parent KMZ file)
                        // with the relative target URL from original KMZ file.
                        try {
                            UrlRef urlRef = new UrlRef(new URI(val));
                            val = urlRef.getKmzRelPath();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    if (ICON.equals(elementName)) {
                        // photo overlay URLs may have entity substitution in URLs. Restore any escaping done in UrlRef.escapeUri()
                        // example: http://www.gigapan.org/get_ge_tile/46074/$[level]/$[y]/$[x]
                        // gets escaped as such: http://www.gigapan.org/get_ge_tile/46074/$%5Blevel%5D/$%5By%5D/$%5Bx%5D
                        try {
                            val = URLDecoder.decode(val, StandardCharsets.UTF_8).replace(" ", "%20");
                        } catch (IllegalArgumentException e) {
                            log.warn("Failed to decode Icon URL", e);
                        }
                    }
                }
                handleSimpleElement(tag, val);
            }
        }
        writer.writeEndElement();
    }

    /**
     * Handle the screen location information
     *
     * @param tag String tag
     * @param loc ScreenLocation of tag
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleXY(String tag, ScreenLocation loc)
            throws XMLStreamException {
        if (loc != null) {
            writer.writeStartElement(tag);
            writer.writeAttribute("x", Double.toString(loc.x));
            writer.writeAttribute("y", Double.toString(loc.y));
            writer.writeAttribute("xunits", loc.xunit.kmlValue);
            writer.writeAttribute("yunits", loc.yunit.kmlValue);
            writer.writeEndElement();
        }
    }

    /*
     * Output a tagged element.
     *
     * @param data
     */
    /*
    private void handleTagElement(TaggedMap data) throws XMLStreamException {
		if (data == null)
			return;
		writer.writeStartElement(data.getTag());
        // Note: this writes elements in hash order which DOES NOT match order in KML XML schema
        // KML is well-formed and should correctly display in Google Earth but is not valid KML wrt spec.
        for (Map.Entry<String,String> entry : data.entrySet()) {
			handleSimpleElement(entry.getKey(), entry.getValue());
		}
		writer.writeEndElement();
	}
    */

    /**
     * Handle the output of a polygon
     *
     * @param poly the polygon, never {@code null}
     */
    @Override
    public void visit(Polygon poly) {
        if (poly != null)
            try {
                writer.writeStartElement(POLYGON);
                handleGeometryAttributes(poly);
                writer.writeStartElement(OUTER_BOUNDARY_IS);
                writer.writeStartElement(LINEAR_RING);
                // For XSD reasons, altitudeOffset is part of kml:AbstractGeometrySimpleExtensionGroup
                // but it is only supported in kml:LinearRing and kml:LineString.
                handleGxAltitudeOffset();
				handleSimpleElement(COORDINATES, handlePolygonCoordinates(poly
						.getOuterRing().getPoints()));
                writer.writeEndElement();
                writer.writeEndElement();
				for (LinearRing lr : poly.getLinearRings()) {
					writer.writeStartElement(INNER_BOUNDARY_IS);
					writer.writeStartElement(LINEAR_RING);
					handleSimpleElement(COORDINATES, handlePolygonCoordinates(lr
							.getPoints()));
					writer.writeEndElement();
					writer.writeEndElement();
				}
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    private void handleGxAltitudeOffset() throws XMLStreamException {
        if (targetFeature != null) {
            Element elt = targetFeature.findElement("altitudeOffset", NS_GOOGLE_KML_EXT);
            // For XSD reasons, altitudeOffset is part of kml:AbstractGeometrySimpleExtensionGroup but it is only supported in
            // kml:LinearRing and kml:LineString
            if (elt != null) {
                final String text = elt.getText();
                if (StringUtils.isNumeric(text)) {
                    if (gxNamespace != null)
                        writer.writeStartElement(gxNamespace.getPrefix(),
                                "altitudeOffset", gxNamespace.getURI());
                    else {
                        writer.writeStartElement("altitudeOffset");
                        writer.writeDefaultNamespace(NS_GOOGLE_KML_EXT);
                    }
                    writer.writeCharacters(text);
                    writer.writeEndElement();
                }
            }
        }
    }

    /**
     * Handle the output of a ring
     *
     * @param r the ring, never {@code null}
     */
    @Override
    public void visit(LinearRing r) {
        if (r != null)
            try {
                writer.writeStartElement(LINEAR_RING);
                // For XSD reasons, altitudeOffset is part of kml:AbstractGeometrySimpleExtensionGroup
                // but it is only supported in kml:LinearRing and kml:LineString.
                handleGxAltitudeOffset();
                handleGeometryAttributes(r);
                handleSimpleElement(COORDINATES, handleCoordinates(r.getPoints()));
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a line
     *
     * @param l the line, ignored if {@code null}
     */
    @Override
    public void visit(Line l) {
        if (l != null)
            try {
                writer.writeStartElement(LINE_STRING);
                // For XSD reasons, altitudeOffset is part of kml:AbstractGeometrySimpleExtensionGroup
                // but it is only supported in kml:LinearRing and kml:LineString.
                handleGxAltitudeOffset();
                handleGeometryAttributes(l);
                handleSimpleElement(COORDINATES, handleCoordinates(l.getPoints()));
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a point
     *
     * @param p the point, ignored if {@code null}
     * @throws IllegalStateException thrown if XMLStreamException is caught
     */
    @Override
    public void visit(Point p) {
        if (p != null)
            try {
                writer.writeStartElement(POINT);
                handleGeometryAttributes(p);
                //<extrude>0</extrude> <!-- boolean -->
                //<altitudeMode>clampToGround</altitudeMode>
                handleSimpleElement(COORDINATES, handleSingleCoordinate(p));
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a circle
     *
     * @param circle the circle, ignored if {@code null}
     */
    @Override
    public void visit(Circle circle) {
        if (circle != null) {
            try {
                if (numberCirclePoints == 1) {
                    writer.writeStartElement(POINT);
                    handleGeometryAttributes(circle);
                    //<extrude>0</extrude> <!-- boolean -->
                    //<altitudeMode>clampToGround</altitudeMode>
                    handleSimpleElement(COORDINATES, handleSingleCoordinate(circle));
                    writer.writeEndElement();
                    return;
                }
                // use circle hints to output as LinearRing, Polygon, etc.
                Circle.HintType hint = circle.getHint();
                Geodetic2DCircle c = new Geodetic2DCircle(circle.getCenter(), circle.getRadius());
                final boolean is3D = circle.is3D();
                double elev = is3D ? ((Geodetic3DPoint)circle.getCenter()).getElevation() : 0;
                // store preference for # points in generated circles in System.property (default=32)
                // note: number points is one more than count since first and last points must be the same
                StringBuilder b = new StringBuilder();
                Geodetic2DPoint firstPt = null;
                // NOTE: if circle crosses IDL then shape is incorrectly drawn in Google Earth
                for (Geodetic2DPoint point : c.boundary(numberCirclePoints)) {
                    if (firstPt == null) firstPt = point;
                    handleSingleCoordinate(b, point);
                    if (is3D) b.append(',').append(formatDouble(elev));
                }
                if (firstPt != null && numberCirclePoints > 2) {
                    handleSingleCoordinate(b, firstPt);
                    if (is3D) b.append(',').append(formatDouble(elev));
                }
                String coordinates = b.toString();
                if (hint == Circle.HintType.LINE || numberCirclePoints == 2) {
                    writer.writeStartElement(LINE_STRING);
                    handleGeometryAttributes(circle);
                    handleSimpleElement(COORDINATES, coordinates);
                    writer.writeEndElement();
                } else if (hint == Circle.HintType.RING) {
                    writer.writeStartElement(LINEAR_RING);
                    handleGeometryAttributes(circle);
                    handleSimpleElement(COORDINATES, coordinates);
                    writer.writeEndElement();
                } else {
                    writer.writeStartElement(POLYGON); // default
                    handleGeometryAttributes(circle);
                    writer.writeStartElement(OUTER_BOUNDARY_IS);
                    writer.writeStartElement(LINEAR_RING);
                    handleSimpleElement(COORDINATES, coordinates);
                    writer.writeEndElement();
                    writer.writeEndElement();
                    writer.writeEndElement();
                }
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Output a multigeometry, represented by a geometry bag
     *
     * @param bag the geometry bag, ignored if {@code null} or empty
     */
    @Override
    public void visit(GeometryBag bag) {
        if (bag != null && bag.getNumParts() != 0)
            try {
                writer.writeStartElement(MULTI_GEOMETRY);
                super.visit(bag);
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a MultiPoint geometry.
     *
     * @param multiPoint the MultiPoint, never {@code null} or empty
     */
    @Override
    public void visit(MultiPoint multiPoint) {
        if (multiPoint != null && !multiPoint.getPoints().isEmpty())
            try {
                writer.writeStartElement(MULTI_GEOMETRY);
                // this in turn calls visit(Point) handling extrude + altitudeMode elements for each point if needed
                super.visit(multiPoint);
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a MultiLine geometry.
     *
     * @param multiLine the MultiLine, ignored if {@code null} or empty
     */
    @Override
    public void visit(MultiLine multiLine) {
        if (multiLine != null && !multiLine.getLines().isEmpty())
            try {
                writer.writeStartElement(MULTI_GEOMETRY);
                // this in turn calls visit(Line) handling extrude + altitudeMode elements for each line if needed
                super.visit(multiLine);
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a MultiLinearRings geometry.
     *
     * @param rings the MultiLinearRings, ignored if {@code null} or empty
     */
    @Override
    public void visit(MultiLinearRings rings) {
        if (rings != null && !rings.getLinearRings().isEmpty())
            try {
                writer.writeStartElement(MULTI_GEOMETRY);
                super.visit(rings);
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a MultiPolygons geometry.
     *
     * @param polygons the MultiPolygons, ignored if {@code null} or empty
     */
    @Override
    public void visit(MultiPolygons polygons) {
        if (polygons != null && !polygons.getPolygons().isEmpty())
            try {
                writer.writeStartElement(MULTI_GEOMETRY);
                super.visit(polygons);
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle Geometry attributes common to Point, Line, LinearRing, and Polygon namely
     * extrude, tessellate, and altitudeMode.  Note tessellate tag is not applicable to Point
     * geometry and will be ignored if set on Points.
     *
     * @param geom, never null
     * @throws XMLStreamException if there is an error with the underlying XML.
     */
    private void handleGeometryAttributes(GeometryBase geom) throws XMLStreamException {
        /*
        <extension base="kml:AbstractGeometryType">
            <sequence>
                <sequence>
                  <element ref="kml:AbstractGeometrySimpleExtensionGroup" minOccurs="0"
                    maxOccurs="unbounded"/>
                  <element ref="kml:AbstractGeometryObjectExtensionGroup" minOccurs="0"
                    maxOccurs="unbounded"/>
                </sequence>
                <element ref="kml:extrude" minOccurs="0"/>
                <element ref="kml:tessellate" minOccurs="0"/>
                <element ref="kml:altitudeModeGroup" minOccurs="0"/>
            </sequence>
         */
        // <gx:altitudeOffset>...
        //<extrude>0</extrude>                   <!-- boolean -->
        // <tessellate>0</tessellate>             <!-- boolean -->
        // To enable tessellation, the value for <altitudeMode> must be clampToGround or clampToSeaFloor.
        //<altitudeMode>clampToGround</altitudeMode>
        // gx:drawOrder and tessellate not applicable to Point
        // handle gx:drawOrder for Circle, Line, Ring, Polygon geometries
        final Class<? extends GeometryBase> aClass = geom.getClass();
        if (aClass != Point.class) {
            Integer drawOrder = geom.getDrawOrder();
            if (drawOrder != null)
                handleGxElement(DRAW_ORDER, drawOrder.toString());
        }
        Boolean extrude = geom.getExtrude();
        if (extrude != null)
            handleSimpleElement(EXTRUDE, extrude ? "1" : "0");
        Boolean tessellate = geom.getTessellate();
        if (tessellate != null && aClass != Point.class) {
            // note: Circle extends Point but circles treated as Line, Ring or Polygon
            handleSimpleElement(TESSELLATE, tessellate ? "1" : "0");
        }
        handleAltitudeMode(geom.getAltitudeMode());
    }

    private void handleAltitudeMode(AltitudeModeEnumType altitudeMode) throws XMLStreamException {
        // if null or default (clampToGround) then ignore
        // if gx:AltitudeMode extension (clampToSeaFloor, relativeToSeaFloor) then output a with gx namespace
        // NOTE: only these two enumeration values for gx:AltitudeMode as of 1-Jun-2011
        if (altitudeMode != null) {
            if (altitudeMode == AltitudeModeEnumType.relativeToGround || altitudeMode == AltitudeModeEnumType.absolute) {
                handleSimpleElement(ALTITUDE_MODE, altitudeMode);
            } else if (altitudeMode == AltitudeModeEnumType.clampToSeaFloor || altitudeMode == AltitudeModeEnumType.relativeToSeaFloor) {
                handleGxElement(ALTITUDE_MODE, altitudeMode.toString());
                //log.warn("gx:altitudeMode values not supported in KML output: " + altitudeMode);
                //writer.writeComment("gx:altitudeMode>" + altitudeMode + "</gx:altitudeMode");
            }
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
            handleSingleCoordinate(b, point);
        }
        return b.toString();
    }

    private String handleSingleCoordinate(Point point) {
        StringBuilder b = new StringBuilder();
        handleSingleCoordinate(b, point);
        return b.toString();
    }

    /**
     * Output a single coordinate
     *
     * @param b     StringBuilder to write coordinate to
     * @param point Point to be formatted for output
     */
    private void handleSingleCoordinate(StringBuilder b, Point point) {
        handleSingleCoordinate(b, point.getCenter());
    }

    private void handleSingleCoordinate(StringBuilder b, Geodetic2DPoint p2d) {
        if (b.length() > 0) {
            b.append(' ');
        }
        b.append(formatDouble(p2d.getLongitudeAsDegrees()));
        b.append(',');
        b.append(formatDouble(p2d.getLatitudeAsDegrees()));
        if (p2d instanceof Geodetic3DPoint) {
            Geodetic3DPoint p3d = (Geodetic3DPoint) p2d;
            b.append(',');
            b.append(formatDouble(p3d.getElevation()));
        }
    }

    private String handlePolygonCoordinates(Collection<Point> coordinateList) {
		StringBuilder b = new StringBuilder();
		Double lastLonValue = null;
		for (Point point : coordinateList) {
			if (b.length() > 0) {
				b.append(' ');
			}
			final Geodetic2DPoint p2d = point.getCenter();
			double lonDegrees = p2d.getLongitudeAsDegrees();
			// NOTE: geodesy normalizes longitude +180 to -180 so polygons from west with longitude >= 0
			// and east longitude at 180 must be clamped to +180 otherwise Google Earth wraps polygons
			// other way around the world. Lines and LinearRings are drawn correctly.
			// TODO: if first point at -180 longitude and next point >= 0 then will not appear correct in Google Earth
			if (lastLonValue != null && lastLonValue >= 0 && Math.abs(lonDegrees + 180) < 1e-8) {
				log.debug("swap the longitude sign -180 > +180");
				lonDegrees = -lonDegrees; // switch the sign
			}
			b.append(formatDouble(lonDegrees));
			b.append(',');
			b.append(formatDouble(p2d.getLatitudeAsDegrees()));
			if (p2d instanceof Geodetic3DPoint) {
				Geodetic3DPoint p3d = (Geodetic3DPoint) p2d;
				b.append(',');
				b.append(formatDouble(p3d.getElevation()));
			}
			lastLonValue = lonDegrees;
		}
		return b.toString();
    }

    /**
     * Visit a Schema object
     *
     * @param schema Schema to visit, never null
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(Schema schema) {
        try {
            writer.writeStartElement(SCHEMA);
            // review: normalize/escape? GE allows whitespace, []'s. etc. in names
            // as well as BalloonStyle substitution entities...
            // String schemaName = UrlRef.escapeUri(schema.getName());
            writer.writeAttribute(NAME, schema.getName());
            String schemaid = UrlRef.escapeUri(schema.getId().toString());
            if (schemaid.startsWith("#")) {
                schemaid = schemaid.substring(1);
            }
            // must follow xsd:ID type (http://www.w3.org/TR/xmlschema-2/#ID)
            // which follows NCName production in [Namespaces in XML].
            writer.writeAttribute(ID, schemaid);
            for (String name : schema.getKeys()) {
                SimpleField field = schema.get(name);
                if (field.getType().isGeometry()) {
                    continue; // Skip geometry elements, no equivalent in Kml
                }
                writer.writeStartElement(SIMPLE_FIELD);
                if (field.getType() == Type.LONG)
                    writer.writeAttribute(TYPE, "double");
                else if (field.getType().isKmlCompatible())
                    writer.writeAttribute(TYPE, field.getType().toString()
                            .toLowerCase());
                else
                    writer.writeAttribute(TYPE, "string");
                writer.writeAttribute(NAME, field.getName());
                final String displayName = field.getDisplayName();
                if (StringUtils.isNotBlank(displayName)) {
                    handleSimpleElement(DISPLAY_NAME, displayName);
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Visit a Style object
     *
     * @param style Style to visit
     */
    @Override
    public void visit(Style style) {
        if (style != null)
            try {
                handle(style);
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Handle the output of a NetworkLinkControl feature
     *
     * @param networkLinkControl
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
	public void visit(NetworkLinkControl networkLinkControl) {
        /*
        <element name="NetworkLinkControl" type="kml:NetworkLinkControlType"/>
        <complexType name="NetworkLinkControlType" final="#all">
          <sequence>
            <element ref="kml:minRefreshPeriod" minOccurs="0"/>
            <element ref="kml:maxSessionLength" minOccurs="0"/>
            <element ref="kml:cookie" minOccurs="0"/>
            <element ref="kml:message" minOccurs="0"/>
            <element ref="kml:linkName" minOccurs="0"/>
            <element ref="kml:linkDescription" minOccurs="0"/>
            <element ref="kml:linkSnippet" minOccurs="0"/>
            <element ref="kml:expires" minOccurs="0"/>
            <element ref="kml:Update" minOccurs="0"/>
            <element ref="kml:AbstractViewGroup" minOccurs="0"/>
            <element ref="kml:NetworkLinkControlSimpleExtensionGroup" minOccurs="0"
              maxOccurs="unbounded"/>
            <element ref="kml:NetworkLinkControlObjectExtensionGroup" minOccurs="0"
              maxOccurs="unbounded"/>
          </sequence>
        </complexType>
       */
        try {
            writer.writeStartElement(NETWORK_LINK_CONTROL);
            handleNonNullSimpleElement("minRefreshPeriod", networkLinkControl.getMinRefreshPeriod());
            handleNonNullSimpleElement("maxSessionLength", networkLinkControl.getMaxSessionLength());
            handleNonEmptySimpleElement("cookie", networkLinkControl.getCookie());
            handleNonEmptySimpleElement("message", networkLinkControl.getMessage());
            handleNonEmptySimpleElement("linkName", networkLinkControl.getLinkName());
            handleNonEmptySimpleElement("linkDescription", networkLinkControl.getLinkDescription());
            handleNonEmptySimpleElement("linkSnippet", networkLinkControl.getLinkSnippet());
            Date expires = networkLinkControl.getExpires();
            if (expires != null) handleSimpleElement("expires", formatDate(expires));
            String targetHref = networkLinkControl.getTargetHref();
            String type = networkLinkControl.getUpdateType();
            if (targetHref != null && type != null) {
                writer.writeStartElement("Update");
                handleSimpleElement("targetHref", targetHref);
                // create elements -> Create | Delete | Change
                writer.writeEmptyElement(type);//TODO: handle multiple update objects
                writer.writeEndElement(); // end Update
            }
            handleAbstractView(networkLinkControl.getViewGroup()); // LookAt or Camera AbstractViewGroup
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleNonEmptySimpleElement(String tag, String content) throws XMLStreamException {
        if (content != null) {
            content = content.trim();
            if (content.length() != 0) handleSimpleElement(tag, content);
        }
    }

    private void handleTaggedElement(String tag, TaggedMap map) throws XMLStreamException {
        handleNonNullSimpleElement(tag, map.get(tag));
    }

    private void handleTaggedElement(String tag, TaggedMap map, LinkedList<String> waitingList) throws XMLStreamException {
        String content = map.get(tag);
        if (content != null) {
            if (waitingList != null) {
                while (!waitingList.isEmpty()) {
                    writer.writeStartElement(waitingList.removeFirst());
                }
            }
            handleSimpleElement(tag, content);
        }
    }

    /*
     private void writeNonEmptyAttribute(String localName, String value) throws XMLStreamException {
         if (value != null) {
             value = value.trim();
             if (value.length() != 0) writer.writeAttribute(localName, value);
         }
     }

     private void writeNonNullAttribute(String localName, Object value) throws XMLStreamException {
         if (value != null)
             writer.writeAttribute(localName, value.toString());
     }
     */

    /**
     * Actually output the style
     *
     * @param style Style object to be written, never null
     * @throws XMLStreamException if there is an error with the underlying XML.
     */
    private void handle(Style style) throws XMLStreamException {
        writer.writeStartElement(STYLE);
        if (style.getId() != null) {
            writer.writeAttribute(ID, style.getId());
        }
        if (style.hasIconStyle()) {
            handleIconStyleElement(style);
        }
        if (style.hasLabelStyle()) {
            handleLabelStyleElement(style);
        }
        if (style.hasLineStyle()) {
            handleLineStyleElement(style);
        }
        if (style.hasPolyStyle()) {
            handlePolyStyleElement(style);
        }
        if (style.hasBalloonStyle()) {
            handleBalloonStyleElement(style);
        }
        if (style.hasListStyle()) {
            handleListStyleElement(style);
        }
        writer.writeEndElement();
    }

    /**
     * @param style polygon Style element to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handlePolyStyleElement(Style style) throws XMLStreamException {
        writer.writeStartElement(POLY_STYLE);
        handleColor(COLOR, style.getPolyColor());
		if(style.getPolyColorMode() == Style.ColorMode.RANDOM)
			handleSimpleElement(COLOR_MODE, "random");
        if (style.getPolyfill() != null)
            handleSimpleElement(FILL, style.getPolyfill() ? "1" : "0"); // default 1
        if (style.getPolyoutline() != null)
            handleSimpleElement(OUTLINE, style.getPolyoutline() ? "1" : "0"); // default 1
        writer.writeEndElement();
    }

    /**
     * @param style Style element with label Style to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleLabelStyleElement(Style style) throws XMLStreamException {
        writer.writeStartElement(LABEL_STYLE);
        handleColor(COLOR, style.getLabelColor());
        handleDouble(SCALE, style.getLabelScale());
        writer.writeEndElement();
    }

    /**
     * @param style Style element with List style to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleListStyleElement(Style style) throws XMLStreamException {
        writer.writeStartElement(LIST_STYLE);
        Style.ListItemType listItemType = style.getListItemType();
        if (listItemType != null)
            handleSimpleElement(LIST_ITEM_TYPE, listItemType.toString());
        handleColor(BG_COLOR, style.getListBgColor());
        writer.writeEndElement();
    }

    /**
     * @param style balloon Style element to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleBalloonStyleElement(Style style)
            throws XMLStreamException {
        writer.writeStartElement(BALLOON_STYLE);
        handleColor(BG_COLOR, style.getBalloonBgColor());
        handleColor(TEXT_COLOR, style.getBalloonTextColor());
        final String text = style.getBalloonText();
        if (text != null) handleSimpleElement(TEXT, text.isEmpty() ? null : text);
        String displayMode = style.getBalloonDisplayMode();
        // ignore default displayMode value (default)
        if (("hide".equals(displayMode) || "default".equals(displayMode)))
            handleSimpleElement(DISPLAY_MODE, displayMode);
        writer.writeEndElement();
    }

    /**
     * @param style line Style element to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleLineStyleElement(Style style)
            throws XMLStreamException {
        writer.writeStartElement(LINE_STYLE);
        if(style.getLineColorMode() == Style.ColorMode.RANDOM)
            handleSimpleElement(COLOR_MODE, "random");
        handleColor(COLOR, style.getLineColor());
        handleDouble(WIDTH, style.getLineWidth());
        writer.writeEndElement();
    }

    /**
     * Writes out IconStyle portion of the Style.
     * If the Style iconUrl is null then no Icon element is generated which
     * uses the default icon (e.g. yellow pin). If iconUrl is blank or empty
     * string then an empty Icon element is generated instead otherwise
     * the specified URL is added to {@code <Icon>} element.
     *
     * @param style icon Style element to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     * @see Style#setIconStyle(Color, Double, Double, String)
     */
    private void handleIconStyleElement(Style style)
            throws XMLStreamException {
        writer.writeStartElement(ICON_STYLE);
        handleColor(COLOR, style.getIconColor());
        handleDouble(SCALE, style.getIconScale());
        Double heading = style.getIconHeading();
        if (heading != null && Math.abs(heading) > 0.1 && heading < 360)
            handleSimpleElement(HEADING, formatDouble(heading));
        String iconUrl = style.getIconUrl();
        if (iconUrl != null) {
            // if want empty Icon tag then include a blank href ("")
            if (iconUrl.isEmpty())
                writer.writeEmptyElement(ICON);
            else {
                writer.writeStartElement(ICON);
                handleSimpleElement(HREF, iconUrl);
                writer.writeEndElement();
            }
        }
        /*
        // hotSpot optional. skip it
        writer.writeStartElement(HOT_SPOT);
		writer.writeAttribute("x", "0");
		writer.writeAttribute("y", "0");
		//writer.writeAttribute("xunits", "fraction"); // default
		//writer.writeAttribute("yunits", "fraction"); // default
		writer.writeEndElement();
		*/

        writer.writeEndElement();
    }

    private void handleDouble(String tag, Double value) throws XMLStreamException {
        if (value != null) {
            handleSimpleElement(tag, formatDouble(value));
        }
    }

    /**
     * Get the KML compliant color translation
     *
     * @param tag   String tag color element
     * @param color the Color of the tag to be written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleColor(String tag, Color color)
            throws XMLStreamException {
        if (color != null) {
            handleSimpleElement(tag, String.format("%02x%02x%02x%02x",
                    color.getAlpha(), color.getBlue(),
                    color.getGreen(), color.getRed()));
        }
    }

    /**
     * Visit a StyleMap object
     *
     * @param styleMap StyleMap to visit
     */
    @Override
    public void visit(StyleMap styleMap) {
        if (styleMap != null)
            try {
                handleStyleMap(styleMap);
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Actually handle style map
     *
     * @param styleMap StyleMap to be written, never null
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleStyleMap(StyleMap styleMap) throws XMLStreamException {
        writer.writeStartElement(STYLE_MAP);
        if (styleMap.getId() != null) {
            writer.writeAttribute(ID, styleMap.getId());
        }
        handlePair(styleMap, StyleMap.NORMAL);
        handlePair(styleMap, StyleMap.HIGHLIGHT);
        writer.writeEndElement();
    }

    private void handlePair(StyleMap styleMap, String key) throws XMLStreamException {
        Pair pair = styleMap.getPair(key);
        if (pair != null) {
            writer.writeStartElement(PAIR);
            String id = pair.getId();
            if (id != null) writer.writeAttribute(ID, id);
            // <element ref="kml:key" minOccurs="0"/>
            // key must be kml:styleStateEnumType { normal or highlight }
            handleSimpleElement(KEY, key);
            // <element ref="kml:styleUrl" minOccurs="0"/>
            handleNonNullSimpleElement(STYLE_URL, pair.getStyleUrl());
            // <element ref="kml:AbstractStyleSelectorGroup" minOccurs="0"/>
            StyleSelector style = pair.getStyleSelector();
            if (style instanceof Style)
                handle((Style) style);
            // nested StyleMaps not supported
            writer.writeEndElement();
        }
    }

    /**
     * Visit a Model object
     *
     * @param model Model to visit, ignored if null
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(Model model) {
        if (model != null)
            try {
                Geodetic2DPoint point = model.getLocation();
                if (point == null && model.getAltitudeMode() == null)
                    writer.writeEmptyElement(MODEL);
                else {
                    writer.writeStartElement(MODEL);
                    // if altitudeMode is invalid/null then it will be omitted (handles gx:AltitudeMode extensions)
                    handleAltitudeMode(model.getAltitudeMode());
                    if (point != null) {
                        writer.writeStartElement(LOCATION);
                        handleSimpleElement(LONGITUDE, formatDouble(point.getLongitudeAsDegrees()));
                        handleSimpleElement(LATITUDE, formatDouble(point.getLatitudeAsDegrees()));
                        if (model.is3D())
                            handleSimpleElement(ALTITUDE, formatDouble(((Geodetic3DPoint) point).getElevation()));
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Returns number of points used to represent a {@link Circle} geometry,
     * which can be output either as a LineString, LinearRing, or Polygon
     * depending on the hint. If number is greater than 2 then the actual
     * number coordinates produced in output will be n+1 since list will
     * include first point twice since circle (other than as a point or
     * line) must start and end at same point. The default value (32) is
     * overridden by a <code>giscore.circle.numPoints</code> System property
     * if that is defined.
     *
     * @return number of points used to represent a Circle geometry.
     */
    public int getNumberCirclePoints() {
        return numberCirclePoints;
    }

    /**
     * Set number of points used to generate a circle. Defines how output
     * will iterate over boundary points of Circle geometries at
     * <code>circlePoints</code> resolution.
     *
     * @param circlePoints int number of points on boundary to use (1st is due South),
     *                     should be greater than 0
     */
    public void setNumberCirclePoints(int circlePoints) {
        numberCirclePoints = circlePoints >= 1 ? circlePoints : 1;
    }

}
