/****************************************************************************************
 *  TestKmlOutputStream.java
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
package org.opensextant.giscore.test.output;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.MGRS;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.geometry.Circle;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.GeometryBag;
import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.geometry.Polygon;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.input.XmlInputStream;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.KmlInputStream;
import org.opensextant.giscore.input.kml.KmlReader;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.output.XmlOutputStreamBase;
import org.opensextant.giscore.output.kml.KmlOutputStream;
import org.opensextant.giscore.test.TestGISBase;
import org.opensextant.giscore.utils.DateTime;
import static junit.framework.Assert.*;

import static org.opensextant.giscore.test.TestSupport.OUTPUT;
/**
 * Test the KML output stream.
 *
 * @author DRAND
 * @author Mathews
 */
public class TestKmlOutputStream extends TestGISBase {

    static String FAKE_ATOM_NS = "http://tools.ietf.org/html/rfc4287";
    private final boolean autoDelete = !Boolean.getBoolean("keepTempFiles");

    protected static final File outputDir = new File(OUTPUT );
    protected static final String KML = OUTPUT + "/"+ "out.kml";
    static {
        outputDir.mkdirs();
    }

    @Test
    public void testSimpleCase() throws IOException {
        doTest(getStream("7084.kml"));
    }

    // @Test
    public void testElement() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos, XmlOutputStreamBase.ISO_8859_1);
        try {
            DocumentStart ds = new DocumentStart(DocumentType.KML);
            Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
            Namespace atomNs = Namespace.getNamespace("atom", FAKE_ATOM_NS);
            assertTrue(ds.addNamespace(gxNs));
            assertTrue(ds.addNamespace(gxNs)); // already in list
            ds.addNamespace(atomNs);
            assertEquals(2, ds.getNamespaces().size());
            kos.write(ds);
            Feature f = new Feature();
            f.setName("gx:atom:test");
            f.setDescription("this is a test placemark");
			/*
                <atom:author>
                    <atom:name>the Author</atom:name>
                 </atom:author>
                 <atom:link href="http://tools.ietf.org/html/rfc4287" />
                */
            List<Element> elements = new ArrayList<>(2);
            Element author = new Element(atomNs, "author");
            Element name = new Element(atomNs, "name");
            name.setText("the Author");
            author.getChildren().add(name);
            elements.add(author);
            Element link = new Element(atomNs, "link");
            link.getAttributes().put("href", "http://tools.ietf.org/html/rfc4287");
            elements.add(link);
            f.setElements(elements);
            Point point = new Point(12.233, 146.825);
            point.setAltitudeMode("clampToSeaFloor");
            f.setGeometry(point);
            kos.write(f);
            kos.close();
            kos = null;

            // System.out.println(bos.toString());

            KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
            IGISObject o = kis.read();
            assertTrue(o instanceof DocumentStart);
            List<Namespace> namespaces = ((DocumentStart) o).getNamespaces();
            assertTrue(namespaces.contains(atomNs));
            assertTrue(namespaces.contains(gxNs));
            o = kis.read();
            assertTrue(o instanceof Feature);
            Feature f2 = (Feature) o;
            List<Element> elts = f2.getElements();
            assertTrue(elts.size() == 2);
            checkApproximatelyEquals(f, f2);
            Element e = elts.get(0);
            assertNotNull(e.getNamespaceURI());
            assertEquals(atomNs, e.getNamespace());
            assertNotNull(e.getChildren());
            Element child = e.getChild("name", atomNs);
            assertNotNull(child);
            assertNotNull(e.getChild("name"));
            Point pt = (Point) f2.getGeometry();
            assertEquals(AltitudeModeEnumType.clampToSeaFloor, pt.getAltitudeMode());
            kis.close();
        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        } finally {
            if (kos != null)
                kos.close();
        }
        // System.out.println(kml);
        //Assert.assertTrue(kml.contains("this is a test placemark"));
        //Assert.assertTrue(kml.contains(IKml.NS_GOOGLE_KML_EXT));
    }

    @Test
    public void testNetworkLink() throws XMLStreamException, IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);

        NetworkLink nl = new NetworkLink();
        nl.setId("id");
        nl.setName("Test Link");
        nl.setDescription("POI");
        nl.setOpen(true);
        nl.setStartTime(new DateTime());
        nl.setEndTime(nl.getStartDate());
        nl.setSnippet("snippet");
        Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
        nl.addElement(new Element(gxNs, "balloonVisibility").withText("1"));
        TaggedMap region = new TaggedMap(IKml.REGION);
        region.put("north", "37.834672");
        region.put("south", "37.79627");
        region.put("east", "-122.458072");
        region.put("west", "-122.494786");
        region.put("minLodPixels", "128");
        region.put("maxLodPixels", "-1");
        region.put("minFadeExtent", "0");
        region.put("maxFadeExtent", "0");
        nl.setRegion(region);
        TaggedMap link = new TaggedMap(IKml.LINK);
        link.put("href", "http://localhost:9005/kml");
        link.put("refreshMode", IKml.REFRESH_MODE_ON_INTERVAL);
        link.put("refreshInterval", "4");
        link.put("viewRefreshMode", "onRegion");
        link.put("viewRefreshTime", "1");
        link.put("viewFormat", "");
        nl.setLink(link);
        nl.setRefreshVisibility(true);
        nl.setFlyToView(true);
        kos.write(nl);
        kos.close();

        //System.err.println(new String(bos.toByteArray()));
        //System.err.println(nl);

        compareFeature(nl, new ByteArrayInputStream(bos.toByteArray()));
    }

    // @Test
    public void testScreenOverlay() throws XMLStreamException, IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);

		/*
		<ScreenOverlay>
		  <overlayXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
		  <screenXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
		  <rotationXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
		  <size x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
		</ScreenOverlay>
		 */

        ScreenOverlay so = new ScreenOverlay();
        so.setRotationAngle(45.0);
        ScreenLocation xy = new ScreenLocation();
        xy.x = 0.5;
        xy.xunit = ScreenLocation.UNIT.FRACTION;
        xy.y = 0.5;
        xy.yunit = ScreenLocation.UNIT.FRACTION;
        so.setRotation(xy);
        so.setOverlay(xy);
        so.setScreen(xy);
        so.setSize(xy);
        kos.write(so);
        kos.close();

        compareFeature(so, new ByteArrayInputStream(bos.toByteArray()));
    }

    // @Test
    public void testExtendedElement() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
		/*
			recreate example KML with custom namespace in extended data
			http://code.google.com/apis/kml/documentation/extendeddata.html
		 */
        try {
            DocumentStart ds = new DocumentStart(DocumentType.KML);
            Namespace campNS = Namespace.getNamespace("camp", "http://campsites.com");
            ds.addNamespace(campNS);
            kos.write(ds);
            Feature f = new Feature();
            List<Element> elts = new ArrayList<>();
            elts.add(new Element(campNS, "Data").withText("14"));
            elts.add(new Element(campNS, "parkingSpaces").withText("2"));
            elts.add(new Element(campNS, "tentSites").withText("4"));
            f.setExtendedElements(elts);
            kos.write(f);
            kos.close();
            // System.out.println("KML content:\n" + bos.toString("UTF-8"));

            compareFeature(f, new ByteArrayInputStream(bos.toByteArray()));

            //KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
            //IGISObject o = kis.read();
            //kis.close();
        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    // @Test
    public void testNullData() throws XMLStreamException, IOException {
        // System.out.println("XXX: testNullData");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        try {
            Feature f = new Feature();
            // fields with null values are ignored in KML output so only one field will be written
            f.putData(new SimpleField("name"), null);
            f.putData(new SimpleField("date", SimpleField.Type.DATE), "2009-04-02T02:06:59Z");
            kos.write(f);
            kos.close();

            KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
            IGISObject o = kis.read();
            assertTrue(o instanceof DocumentStart);
            o = kis.read();
            assertTrue(o instanceof Feature);
            Feature f2 = (Feature) o;
            // fields with null values are ignored in KML output so only one field is found
            assertTrue(f2.hasExtendedData());
            assertEquals(f2.getFieldSize(), 1);
            kis.close();
        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    // @Test
    public void testBadExtendedElement() throws IOException, XMLStreamException {
        // System.out.println("XXX: testBadExtendedElement");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        try {
            Feature f = new Feature();
            List<Element> elts = new ArrayList<>();
            Namespace kmlNs = Namespace.getNamespace("kml", IKml.KML_NS);
            // foreign elements in ExtendedData must be non-empty and non-KML namespace
            elts.add(new Element(kmlNs, "description").withText("this is an extended description"));
            elts.add(new Element("noNamespace").withText("2")); // element with no namespace
            f.setExtendedElements(elts);
            kos.write(f);
            kos.close();
            // System.out.println("KML content:\n" + bos.toString("UTF-8"));

            KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
            IGISObject o = kis.read();
            assertTrue(o instanceof DocumentStart);
            o = kis.read();
            assertTrue(o instanceof Feature);
            Feature f2 = (Feature) o;
            // NOTE: invalid extended data elements are output as XML comments
            // and comments are ignored in input in KmlInputStream so expected count is 0.
            assertEquals(0, f2.getExtendedElements().size());
            kis.close();

            // compareFeature(f, new ByteArrayInputStream(bos.toByteArray()));
            // note this does not test the foreign extended elements

        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    private void compareFeature(IGISObject expected, InputStream is) throws IOException {
        KmlInputStream kis = new KmlInputStream(is);
        try {
            assertNotNull(kis.read()); // skip DocumentStart
            IGISObject actual = kis.read();
            //System.err.println(obj);
            assertNotNull(actual);
            //assertTrue(obj instanceof Feature);
            assertEquals(expected, actual);
        } finally {
            kis.close();
        }
    }

    // @Test
    public void testMultiDocumentStarts() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        try {
            DocumentStart ds = new DocumentStart(DocumentType.KML);
            ds.addNamespace(Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT));
            kos.write(ds);
            kos.write(ds); // this should throw IllegalStateException
            kos.write(new Feature());
            fail("Expected to throw IllegalStateException");
        } catch (IllegalStateException e) {
            // expect this as return
        } finally {
            kos.close();
        }
    }

    // @Test
    public void testUndeclaredAttribute() throws Exception {
        Namespace atomNs = Namespace.getNamespace("atom", FAKE_ATOM_NS);

        Element author = new Element(atomNs, "author");
        // add attribute with unknown namespace prefix
        author.getAttributes().put("geo:point", "60,-89.91");
        Element name = new Element(atomNs, "name");
        name.setText("the Author");
        author.getChildren().add(name);

        Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
        Element gxElt = new Element(gxNs, "timestamp");
        gxElt.getAttributes().put("gx:type", "simple");
        // attributes with undeclared namespaces will be discarded on output
        gxElt.getAttributes().put("geo:point", "60,-89.91");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        DocumentStart ds = new DocumentStart(DocumentType.KML);
        ds.addNamespace(atomNs);
        kos.write(ds);
        // this will be written out as a comment but skipped on input
        kos.write(author);
        kos.write(gxElt);
        kos.close();

        /*
          this creates KML like this:

          <?xml version="1.0" encoding="UTF-8"?>
          <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:atom="http://www.w3.org/2005/Atom">
              <!--
                  <atom:author geo:point="60,-89.91">
                    <atom:name>the Author</name>
                  </author>
              -->
              <gx:timestamp gx:type="simple" xmlns:gx="http://www.google.com/kml/ext/2.2"/>
          </kml>
           */

        // KML output should contain a comment for arbitrary non-KML elements
        assertTrue(bos.toString().contains("<!--"));
        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        IGISObject o = kis.read();
        assertTrue(o instanceof DocumentStart);
        o = kis.read();
        assertTrue(o instanceof Element);
        Element elt = (Element) o;
        assertEquals(gxNs, elt.getNamespace());

        assertNull(kis.read());
    }

    @Test
    public void testGxElementNsDeclared() throws IOException, XMLStreamException {
        outputGxElement(true);
    }

    @Test
    public void testGxElementNsUndeclared() throws IOException, XMLStreamException {
        outputGxElement(false);
    }

    private void outputGxElement(boolean declareNamespace) throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        if (declareNamespace) {
            DocumentStart ds = new DocumentStart(DocumentType.KML);
            Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
            ds.getNamespaces().add(gxNs);
            kos.write(ds);
        }
        kos.write(new ContainerStart());
        Feature f = new Feature();
        TaggedMap lookAt = new TaggedMap("LookAt");
        if (declareNamespace) {
            // gx:TimeSpan is a complex element not a simple element
            lookAt.put("gx:TimeSpan/begin", "2011-03-11T01:00:24.012Z");
            lookAt.put("gx:TimeSpan/end", "2011-03-11T05:46:24.012Z");
        } else {
            lookAt.put("gx:TimeStamp", "2011-03-11T05:46:24.012Z");
        }
        lookAt.put("longitude", "143.1066665234362");
        lookAt.put("latitude", "37.1565775502346");
        f.setViewGroup(lookAt);
        Point cp = new Point(random3dGeoPoint());
        cp.setAltitudeMode(AltitudeModeEnumType.clampToSeaFloor);
        f.setGeometry(cp);
        kos.write(f);

        Feature lineFeature = new Feature();
        List<Point> pts = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            pts.add(new Point(i * .01, i * .01));
        }
        Line line = new Line(pts);
        line.setDrawOrder(3);
        line.setTessellate(true);
        line.setExtrude(true);
        lineFeature.setGeometry(line);
        kos.write(lineFeature);

        kos.write(new ContainerEnd());
        kos.close();
        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        try {
            assertNotNull(kis.read()); // skip DocumentStart
            assertNotNull(kis.read()); // skip ContainerStart
            IGISObject obj = kis.read(); // Placemark
            IGISObject obj2 = kis.read(); // Placemark
            kis.close();
            assertTrue(obj instanceof Feature);
            checkApproximatelyEquals(f, obj);
            assertTrue(obj2 instanceof Feature);
            checkApproximatelyEquals(lineFeature, obj2);
            assertEquals(lineFeature.getGeometry(), ((Feature) obj2).getGeometry());
            // System.out.println(bos.toString("UTF-8")); // debug
        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    @Test
    public void createWithUnclosedContainers() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        kos.write(new ContainerStart()); // Document
        kos.write(new ContainerStart("Folder")); // Folder
        kos.write(new Feature());
        // omit writing Container Ends.
        // underlying XML handling should close open elements to generate a well-formed XML document.
        kos.close();

        // make sure KML is parsable
        //System.err.println("XXX: " + bos.toString("UTF-8"));
        //assertTrue(bos.toString().contains("</Folder>"));
        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        //IGISObject obj;
        int count = 0;
        // objects: Document, ContainerStart, ContainerStart, Feature, ContainerEnd, ContainerEnd
        while (kis.read() != null) {
            //System.err.println("XXX: " + obj.getClass().getName());
            count++;
        }
        kis.close();
        assertEquals(6, count);
    }

    @Test
    public void createGxTrack() throws IOException, XMLStreamException {
		/*
		Generate:

		<Placemark>
			<name>track</name>
			<gx:Track>
				<when>2010-05-28T02:02:00Z</when>
				<when>2010-05-28T02:02:30Z</when>
				<when>2010-05-28T02:03:00Z</when>
				<gx:coord>-122.207881 37.371915 156.000000</gx:coord>
				<gx:coord/>
				<gx:coord>-122.203207 37.374857 140.199997</gx:coord>
			</gx:Track>
		</Placemark>

		 */
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        DocumentStart ds = new DocumentStart(DocumentType.KML);
        Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
        ds.getNamespaces().add(gxNs);
        kos.write(ds);
        Feature f = new Feature();
        f.setName("track");
        Element gxElt = new Element(gxNs, "Track");
		/*
		https://developers.google.com/kml/documentation/kmlreference#gxtrack
		"sparse" data support
		When some data values are missing for positions on the track, empty <coord/> (<coord></coord>)
		or <angles/> (<angles></angles>) tags can be provided to balance the arrays.
		An empty <coord/> or <angles/> tag indicates that no such data exists for a given data point,
		and the value should be interpolated between the nearest two well-specified data points.
		This behavior also applies to ExtendedData for a track. Any element except <when> can be empty
		and will be interpolated between the nearest two well-specified elements.
		 */
        List<Element> elts = gxElt.getChildren();
        elts.add(new Element("when").withText("2010-05-28T02:02:00Z"));
        elts.add(new Element("when").withText("2010-05-28T02:02:30Z"));
        elts.add(new Element("when").withText("2010-05-28T02:03:00Z"));
        elts.add(new Element(gxNs, "coord").withText("-122.207881 37.371915 156.000000"));
        elts.add(new Element(gxNs, "coord").withText(""));
        elts.add(new Element(gxNs, "coord").withText("-122.203207 37.374857 140.199997"));
        f.addElement(gxElt);
        kos.write(f);
        kos.close();

        assertTrue(f.hasElements());
        // System.out.println("XXX: KML content:\n" + bos.toString("UTF-8"));

        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        try {
            assertNotNull(kis.read()); // skip DocumentStart
            IGISObject obj = kis.read(); // Placemark
            assertTrue(obj instanceof Feature);
            Feature f2 = (Feature) obj;
            List<Element> list = f2.getElements();
            assertEquals(1, list.size());
            Element track = list.get(0);
            assertEquals(gxNs, track.getNamespace());
            assertEquals(6, track.getChildren().size());
            Element trackElt = f2.findElement("Track", IKml.NS_GOOGLE_KML_EXT);
            assertNotNull(trackElt);
        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    @Test
    public void createGxTour() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        DocumentStart ds = new DocumentStart(DocumentType.KML);
        Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
        ds.getNamespaces().add(gxNs);
        kos.write(ds);
        ContainerStart cs = new ContainerStart();

		/*
		<gx:Tour>
		  <gx:Playlist>
			<gx:FlyTo>
			  <gx:flyToMode>smooth</gx:flyToMode>
			</gx:FlyTo>
		  </gx:Playlist>
		</gx:Tour>
		 */

        Element tour = new Element(gxNs, "Tour");
        Element playlist = new Element(gxNs, "Playlist");
        tour.getChildren().add(playlist);
        Element flyTo = new Element(gxNs, "FlyTo");
        playlist.getChildren().add(flyTo);
        Element flyToMode = new Element(gxNs, "flyToMode");
        flyToMode.setText("smooth");
        flyTo.getChildren().add(flyToMode);
        cs.addElement(tour);
        kos.write(cs);

        Feature f = new Feature();
        f.setName("no tour");
        f.addElement(tour);
        kos.write(f);
        assertTrue(f.hasElements());

        kos.close();

        // System.out.println("XX: KML content:\n" + bos.toString("UTF-8"));

        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        try {
            assertNotNull(kis.read()); // skip DocumentStart
            IGISObject obj = kis.read(); // ContainerStart
            assertTrue(obj instanceof ContainerStart);
            checkElements(cs.getElements(), ((ContainerStart) obj).getElements());
            obj = kis.read(); // Feature
            // gx:Tour write not be written on a Placemark so no elements should be retrieved here
            Feature f2 = (Feature) obj;
            assertFalse(f2.hasElements());
        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    @Test
    public void testRowData() throws XMLStreamException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        DocumentStart ds = new DocumentStart(DocumentType.KML);
        kos.write(ds);
        SimpleField date = new SimpleField("date", SimpleField.Type.DATE);
        SimpleField name = new SimpleField("name");
        kos.write(new ContainerStart(IKml.FOLDER));
        Row row = new Row();
        row.putData(name, "hello");
        row.putData(date, new Date());
        kos.write(row);
        row = new Row();
        row.putData(name, "world");
        row.putData(date, new Date());
        kos.write(row);
        kos.write(new ContainerEnd());
        kos.close();
        String kml = bos.toString("UTF-8");
        // System.out.println(kml);
        Assert.assertTrue(kml.contains(IKml.EXTENDED_DATA));
        Assert.assertTrue(kml.contains("hello"));
    }

    /**
     * Note, this test fails due to some sort of issue with geodesy, but the
     * actual output kml is fine.
     *
     * @throws IOException
     */
    @Test
    public void testCase2() throws IOException {
        doTest(getStream("KML_sample1.kml"));
    }

    @Test
    public void testCase3() throws IOException {
        doTest(getStream("schema_example.kml"));
    }

    @Test
    public void testRingOutput() throws IOException {
        File file = createTemp("testRings", ".kml");
        OutputStream fs = null;
        try {
            fs = new FileOutputStream(file);
            IGISOutputStream os = GISFactory.getOutputStream(DocumentType.KML, fs);
            os.write(new DocumentStart(DocumentType.KML));
            os.write(new ContainerStart(IKml.FOLDER));
            //Feature firstFeature = null;
            for (int i = 0; i < 5; i++) {
                Point cp = getRandomPoint();
                Feature f = new Feature();
                ContainerStart cs = new ContainerStart(IKml.DOCUMENT);
                cs.setName(Integer.toString(i));
                os.write(cs);
                //if (firstFeature == null) firstFeature = f;
                List<Point> pts = new ArrayList<>(6);
                pts.add(getRingPoint(cp, 4, 5, .3, .4));
                pts.add(getRingPoint(cp, 3, 5, .3, .4));
                pts.add(getRingPoint(cp, 2, 5, .3, .4));
                pts.add(getRingPoint(cp, 1, 5, .3, .4));
                pts.add(getRingPoint(cp, 0, 5, .3, .4));
                pts.add(pts.get(0));
                LinearRing ring = new LinearRing(pts, true);
                if (!ring.clockwise()) System.err.println("rings should be in clockwise point order");
                f.setGeometry(ring);
                os.write(f);
                // first and last points same: don't need to output it twice
                final int npoints = pts.size() - 1;
                for (int k = 0; k < npoints; k++) {
                    Point pt = pts.get(k);
                    f = new Feature();
                    f.setName(Integer.toString(k));
                    f.setGeometry(pt);
                    os.write(f);
                }
                os.write(new ContainerEnd());
            }
            os.close();

            /*
            KmlReader reader = new KmlReader(file);
            List<IGISObject> objs = reader.readAll();
            // imported features should be DocumentStart, Container, followed by Features
            assertEquals(8, objs.size());
            checkApproximatelyEquals(firstFeature, objs.get(2));
            */
        } finally {
            IOUtils.closeQuietly(fs);
            if (autoDelete && file.exists()) file.delete();
        }
    }

    @Test
    public void testPolyOutput() throws IOException {
        File file = createTemp("testPolys", ".kml");
        OutputStream fs = null;
        try {
            fs = new FileOutputStream(file);
            IGISOutputStream os = GISFactory.getOutputStream(DocumentType.KML, fs);
            os.write(new DocumentStart(DocumentType.KML));
            os.write(new ContainerStart(IKml.DOCUMENT));
            Schema schema = new Schema();
            SimpleField id = new SimpleField("testid");
            id.setLength(10);
            schema.put(id);
            SimpleField date = new SimpleField("today", SimpleField.Type.STRING);
            schema.put(date);
            os.write(schema);
            Feature firstFeature = null;
            for (int i = 0; i < 5; i++) {
                Point cp = getRandomPoint(25.0); // Center of outer poly
                Feature f = new Feature();
                if (firstFeature == null) firstFeature = f;
                f.putData(id, "id " + i);
                f.putData(date, new Date().toString());
                f.setSchema(schema.getId());
                List<Point> pts = new ArrayList<>(6);
                for (int k = 0; k < 5; k++) {
                    pts.add(getRingPoint(cp, k, 5, 1.0, 2.0));
                }
                pts.add(pts.get(0)); // should start and end with the same point
                LinearRing outerRing = new LinearRing(pts);
                List<LinearRing> innerRings = new ArrayList<>(4);
                for (int j = 0; j < 4; j++) {
                    pts = new ArrayList<>(6);
                    Point ircp = getRingPoint(cp, j, 4, .5, 1.0);
                    for (int k = 0; k < 5; k++) {
                        pts.add(getRingPoint(ircp, k, 5, .24, .2));
                    }
                    pts.add(pts.get(0));
                    innerRings.add(new LinearRing(pts));
                }
                Polygon p = new Polygon(outerRing, innerRings);
                f.setGeometry(p);
                os.write(f);
            }
            os.close();

            KmlReader reader = new KmlReader(file);
            List<IGISObject> objs = reader.readAll(); // implicit close
            // imported features should be DocumentStart, Container, Schema, followed by Features
            assertEquals(9, objs.size());
            checkApproximatelyEquals(firstFeature, objs.get(3));
        } finally {
            IOUtils.closeQuietly(fs);
            if (autoDelete && file.exists()) file.delete();
        }
    }

    @Test
    public void testCircleOutput() throws XMLStreamException, IOException {
        Point pt = getRandomPoint();
        Circle c = new Circle(pt.getCenter(), 1000.0);
        c.setTessellate(true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        kos.setNumberCirclePoints(32);
        final int pointCount = kos.getNumberCirclePoints() + 1;
        kos.write(new ContainerStart(IKml.DOCUMENT));
        try {
            Feature f = new Feature();
            f.setName("P1");
            f.setDescription("this is a test placemark");
            // circle Hint = polygon (default)
            f.setGeometry(c);
            kos.write(f);
            f.setName("P2");
            c.setHint(Circle.HintType.LINE);
            kos.write(f);

            kos.setNumberCirclePoints(2);
            f.setName("P3");
            kos.write(f);

            kos.setNumberCirclePoints(1);
            f.setName("P4");
            kos.write(f);

            kos.write(new ContainerEnd());
        } finally {
            kos.close();
        }

        try {
            KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
            assertNotNull(kis.read()); // skip DocumentStart
            assertNotNull(kis.read()); // skip Document
            IGISObject obj1 = kis.read(); // Placemark w/Circle as Polygon (n=32)
            IGISObject obj2 = kis.read(); // Placemark w/Circle as LineString (n=32)
            IGISObject obj3 = kis.read(); // Placemark w/Circle as LineString (n=2)
            IGISObject obj4 = kis.read(); // Placemark w/Circle as Point (n=1)
            kis.close();
            assert (obj1 instanceof Feature);
            Feature f = (Feature) obj1;
            Geometry geom = f.getGeometry();
            // by default the KmlOutputStream converts Circle into Polygon with 33 points
            assertTrue(geom instanceof Polygon);
            Polygon poly = (Polygon) geom;
            assertTrue(poly.getTessellate());
            assertEquals(pointCount, poly.getNumPoints());

            assert (obj2 instanceof Feature);
            f = (Feature) obj2;
            geom = f.getGeometry();
            assertTrue(geom instanceof Line);
            Line line = (Line) geom;
            assertTrue(line.getTessellate());
            assertEquals(pointCount, line.getNumPoints());

            assert (obj3 instanceof Feature);
            f = (Feature) obj3;
            geom = f.getGeometry();
            assertTrue(geom instanceof Line);
            assertEquals(2, geom.getNumPoints());

            assert (obj4 instanceof Feature);
            f = (Feature) obj4;
            geom = f.getGeometry();
            assertTrue(geom instanceof Point);
            assertEquals(1, geom.getNumPoints());

        } catch (AssertionError ae) {
            System.out.println("Failed with KML content:\n" + bos.toString("UTF-8"));
            throw ae;
        }
    }

    @Test
    public void testKmz() throws IOException, XMLStreamException {
        File file = createTemp("test", ".kmz");
        ZipOutputStream zoS = null;
        try {
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream boS = new BufferedOutputStream(os);
            // Create the doc.kml file inside of a zip entry
            zoS = new ZipOutputStream(boS);
            ZipEntry zEnt = new ZipEntry("doc.kml");
            zoS.putNextEntry(zEnt);
            KmlOutputStream kos = new KmlOutputStream(zoS);
            kos.write(new DocumentStart(DocumentType.KML));
            Feature f = new Feature();
            f.setGeometry(new Point(42.504733587704, -71.238861602674));
            f.setName("test");
            f.setDescription("this is a test placemark");
            kos.write(f);
            try {
                kos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            IOUtils.closeQuietly(zoS);
            zoS = null;
            KmlReader reader = new KmlReader(file);
            List<IGISObject> objs = reader.readAll(); // implicit close
            // imported features should be DocumentStart followed by Feature
            assertEquals(2, objs.size());
            checkApproximatelyEquals(f, objs.get(1));
        } finally {
            IOUtils.closeQuietly(zoS);
            if (autoDelete && file.exists()) file.delete();
        }
    }

    @Test
    public void testRegion() throws XMLStreamException, IOException {
        // test all variations of Lod and LatLonAltBox combinations
        // test Region with LatLonAltBox only
        doTestRegion(new String[]{
                IKml.NORTH, "45",
                IKml.SOUTH, "35",
                IKml.EAST, "1",
                IKml.WEST, "10",
        });
        // test Region with LatLonAltBox + Lod
        doTestRegion(new String[]{
                IKml.NORTH, "45",
                IKml.SOUTH, "35",
                IKml.EAST, "1",
                IKml.WEST, "10",
                IKml.MIN_LOD_PIXELS, "256",
                IKml.MAX_LOD_PIXELS, "-1"
        });
        // test Region with Lod only
        doTestRegion(new String[]{
                IKml.MIN_LOD_PIXELS, "256",
                IKml.MAX_LOD_PIXELS, "-1"
        });
    }

    private void doTestRegion(String[] props) throws XMLStreamException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        kos.write(new DocumentStart(DocumentType.KML));
        Feature f = new Feature();
        f.setGeometry(new Point(42.504733587704, -71.238861602674));
        f.setName("test");
        f.setDescription("this is a test placemark");
        TaggedMap region = new TaggedMap(IKml.REGION);
        for (int i = 0; i < props.length; i += 2) {
            region.put(props[i], props[i + 1]);
        }
        f.setRegion(region);
        kos.write(f);
        kos.close();

        XmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        IGISObject o;
        while ((o = kis.read()) != null) {
            if (o instanceof Feature) {
                Feature f2 = (Feature) o;
                assertEquals(region, f2.getRegion());
                /*
                    if(!(region.equals(f2.getRegion()))) {
                        System.out.println("== region mismatch ==");
                        System.out.println("1:" + region);
                        System.out.println("2:" + f2.getRegion());
                        System.out.println(new String(bos.toByteArray()));
                        System.out.println();
                    }
                    */
            }
        }
        kis.close();
    }

    public void doTest(InputStream fs) throws IOException {
        File temp = null;
        try {
            IGISInputStream is = GISFactory.getInputStream(DocumentType.KML, fs);
            temp = createTemp("test", ".kml");
            OutputStream fos = new FileOutputStream(temp);
            IGISOutputStream os = GISFactory.getOutputStream(DocumentType.KML, fos);
            List<IGISObject> elements = new ArrayList<>();
            IGISObject current;
            while ((current = is.read()) != null) {
                os.write(current);
                elements.add(current);
            }

            is.close();
            fs.close();

            os.close();
            fos.close();

            // Test for equivalence
            fs = new FileInputStream(temp);
            is = GISFactory.getInputStream(DocumentType.KML, fs);
            int index = 0;
            while ((current = is.read()) != null) {
                checkApproximatelyEquals(elements.get(index++), current);
            }
            is.close();
        } finally {
            IOUtils.closeQuietly(fs);
            if (temp != null && autoDelete && temp.exists()) temp.delete();
        }
    }

    /**
     * For most objects they need to be exactly the same, but for some we can
     * approximate equality
     *
     * @param source expected feature object
     * @param test   actual feature object
     */
    public static void checkApproximatelyEquals(IGISObject source, IGISObject test) {
        if (source instanceof Feature && test instanceof Feature) {
            Feature sf = (Feature) source;
            Feature tf = (Feature) test;

            // tests: { description, name, schema, styleUrl, startTime, endTime, style, extendedData, geometry }
            boolean ae = sf.approximatelyEquals(tf);

            if (!ae) {
                System.out.println("Expected: " + source);
                System.out.println("Actual: " + test);
                fail("Found unequal objects");
            } else {
                // need to verify: viewGroup, region, elements, extendedElements
                // not tested: visibility, snippet
                assertEquals(sf.getViewGroup(), tf.getViewGroup());
                assertEquals(sf.getRegion(), tf.getRegion());
                checkElements(sf.getElements(), tf.getElements());
                checkElements(sf.getExtendedElements(), tf.getExtendedElements());
            }
        } else {
            assertEquals(source, test);
        }
    }

    private static void checkElements(List<Element> srcElements, List<Element> tgtElements) {
        if (srcElements.isEmpty()) {
            assertTrue("unexpected elements", tgtElements.isEmpty());
        } else {
            assertEquals(srcElements.size(), tgtElements.size());
            Iterator<Element> tgtIt = tgtElements.iterator();
            for (Element e : srcElements) {
                Element tgt = tgtIt.next();
                assertEquals(e.getNamespace(), tgt.getNamespace());
                //assertEquals(e.getNamespaceURI(), tgt.getNamespaceURI());
                // NOTE: if text is empty string or null it should be handled the same
                assertEquals(StringUtils.trimToNull(e.getText()),
                        StringUtils.trimToNull(tgt.getText()));
                assertEquals(e.getAttributes(), tgt.getAttributes());
                checkElements(e.getChildren(), tgt.getChildren());
				/*
				if (e.equals(tgt)) {
					System.out.println("Element mismatch:");
					System.out.println("Expected: " + e);
					System.out.println("Actual:   " + tgt);
					fail("Found unequal elements");
				}
				*/
            }
        }
    }

    private InputStream getStream(String filename) throws FileNotFoundException {
        System.out.println("Test " + filename);
        File file = new File(getTestDir() + "/input/" + filename);
        if (file.exists()) return new FileInputStream(file);
        System.out.println("File does not exist: " + file);
        return getClass().getResourceAsStream(filename);
    }

    @Test
    public void testMultiGeometries() throws IOException, XMLStreamException {
        File out = new File(OUTPUT + "/testMultiGeometries.kml");
        KmlOutputStream os = new KmlOutputStream(new FileOutputStream(out),
                XmlOutputStreamBase.ISO_8859_1);
        List<Feature> feats;
        try {
            os.write(new DocumentStart(DocumentType.KML));
            os.write(new ContainerStart(IKml.DOCUMENT));
            feats = getMultiGeometries();
            for (Feature f : feats) {
                os.write(f);
            }
        } finally {
            os.close();
        }

        KmlInputStream kis = new KmlInputStream(new FileInputStream(out));
        assertNotNull(kis.read()); // skip DocumentStart
        assertNotNull(kis.read()); // skip Document
        for (Feature expected : feats) {
            IGISObject current = kis.read();
            assertTrue(current instanceof Feature);
            // Note: GeometryBag with Multiple Points converted to MultiPoint geometry on reading
            // but number of points must be the same
            Geometry geom = expected.getGeometry();
            // System.out.format("%n%s %d %d%n", expected.getName(), geom.getNumPoints(), ((Feature)current).getGeometry().getNumPoints());
            // Note: circles are written as Polygons, Line, or LinearRings depending on the hint preference so number of points is *NOT* the same
            // and GeometryBags of only multiple points are converted to single MultiPoint Geometry so geometries are *NOT* the same
            boolean testFeature = true;
            if (geom instanceof GeometryBag) {
                int pointCount = 0;
                for (Geometry g : (GeometryBag) geom) {
                    // System.out.println("XXX: " + g.getClass().getName());
                    if (g instanceof Circle) {
                        // System.out.println("XXX: skip circle");
                        testFeature = false;
                        break;
                    }
                    if (g.getClass() == Point.class) pointCount++;
                }
                if (pointCount == geom.getNumParts()) {
                    // GeometryBags of multiple points are converted to single MultiPoint Geometry so geometries are *NOT* the same
                    // and cannot be compared using checkApproximatelyEquals()
                    // System.out.println("XXX: skip multiPoints");
                    assertEquals(pointCount, ((Feature) current).getGeometry().getNumPoints());
                    testFeature = false;
                }
            } // else System.out.println("other: " + geom.getClass().getName()); // e.g. MultiLine, MultiPoint, etc.
            if (testFeature) checkApproximatelyEquals(expected, current);
        }
        kis.close();
    }

    @Test
    public void testWrapper() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);

        Feature f = new Feature();
        f.setName("test place");
        f.setDescription("POI");
        // f.setGeometry(getRandomPoint());
        WrappedObject obj = new WrappedObject(f);
        kos.write(obj);

        Comment comment = new Comment("This is a comment");
        kos.write(comment);

        kos.close();
        // System.out.println(bos);
        final String content = bos.toString();
        assertTrue(content.contains("<!-- This is a comment -->"));
        assertTrue(content.contains("<!-- [WrappedObject: "));
    }

    /*
     @Test
     public void testClippedAtDateLine() throws Exception {
         // create outline of Fiji islands which wrap international date line
         List<Point> pts = new ArrayList<Point>();
         final Point firstPt = new Point(-16.68226928264316, 179.900033693558);
         pts.add(firstPt);
         pts.add(new Point(-16.68226928264316, -179.65));
         pts.add(new Point(-17.01144405215603, -180));
         pts.add(new Point(-17.01144405215603, 179.900033693558));
         pts.add(firstPt);

         Line line = new Line(pts);

         KmlOutputStream kos = new KmlOutputStream(new FileOutputStream("out.kml"));
         Feature f = new Feature();
         f.setGeometry(new Point(line.getCenter()));
         kos.write(f);
         kos.close();
         // final String content = bos.toString();
         // System.out.println

         LinearRing ring = new LinearRing(pts, true);
         assertTrue(ring.clippedAtDateLine());
     }

     @Test
     public void testWrapDateLine() throws Exception {
         // create outline of Fiji islands which wrap international date line
         List<Point> pts = new ArrayList<Point>();
         final Point firstPt = new Point(-16.68226928264316, 179.900033693558);
         pts.add(firstPt);
         pts.add(new Point(-16.68226928264316, -179.65));
         pts.add(new Point(-17.01144405215603, -180));
         pts.add(new Point(-17.01144405215603, 179.900033693558));
         pts.add(firstPt);
         Line line = new Line(pts);
         assertTrue(line.clippedAtDateLine());

         // (179d 52' 30" W, 16d 50' 49" S)
         // Geodetic2DPoint cp = line.getCenter();

         KmlOutputStream kos = new KmlOutputStream(new FileOutputStream("out.kml"));
         Feature f = new Feature();
         f.setGeometry(line); // new Point(line.getCenter()));
         kos.write(f);
         kos.close();
      }

     @Test
     public void testAtPoles() throws Exception {
         // create outline of antarctica
         List<Point> pts = new ArrayList<Point>();
         // bounds inside antarctica continent
         final Point firstPt = new Point(-64.2378603202, -57.1573913081);
         pts.add(firstPt);
         pts.add(new Point(-70.2956070281, 26.0747738693));
         pts.add(new Point(-66.346745474, 129.2349114494));
         pts.add(new Point(-72.8459462179, -125.7310989568));

         // region larger than the antarctica bounds
         //final Point firstPt = new Point(-53.7060839150483,-56.70426734285595);
         //pts.add(firstPt);
         //pts.add(new Point(-56.89265096502702,35.93083180403215));
         //pts.add(new Point(-36.63475488023045,122.7617316232436));
         //pts.add(new Point(-51.44838835062461,-135.9100978589071));

         pts.add(firstPt);

         Line line = new Line(pts);

         Geodetic2DPoint cp = line.getCenter();

         // Fctr=(1d 45' 7" E, 68d 32' 31" S) -68.54190326905 1.7519062462999895
         // System.out.println("Fctr=" + cp + " " + cp.getLatitudeAsDegrees() + " " + cp.getLongitudeAsDegrees());

         final Geodetic2DBounds bbox = line.getBoundingBox();
         assertTrue(bbox != null && bbox.contains(cp));
         // south=-72.8459462179 north=-64.2378603202
         // if (bbox.getSouthLat().inDegrees() > bbox.getNorthLat().inDegrees()) System.out.println("XXX: lat sign flipped"); // debug
         // System.out.println("south=" + bbox.getSouthLat().inDegrees() + " north=" + bbox.getNorthLat().inDegrees());

         KmlOutputStream kos = new KmlOutputStream(new FileOutputStream("out.kml"));
         kos.write(new ContainerStart());
         Feature f = new Feature();
         line.setTessellate(true);
         f.setGeometry(line);
         kos.write(f);

         f.setGeometry(new Point(cp));
         kos.write(f);

         f = new Feature();
         // bbox=(westLon=125d 43' 52" W, southLat=72d 50' 45" S) .. (129d 14' 6" E, northLat=64d 14' 16" S)
         // System.out.println("bbox=" + bbox);
         line = new Line(bbox);

         line.setTessellate(true);
         f.setGeometry(line);
         kos.write(f);

         kos.write(new ContainerEnd());
         kos.close();

         // LinearRing ring = new LinearRing(pts, true); // -> Error: LinearRing cannot self-intersect
         // assertEquals(cp, ring.getCenter());
     }
     */

    @Test
    public void testRegionAtIDL() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        kos.write(new ContainerStart());
        Feature f = new Feature();
        Geodetic2DPoint nw = new Point(45, 90).asGeodetic2DPoint();
        Geodetic2DPoint se = new Point(0, 180).asGeodetic2DPoint();
        Geodetic2DBounds bounds = new Geodetic2DBounds(nw, se);
        f.setRegion(bounds);
        TaggedMap region = f.getRegion();
        region.put("minLodPixels", "128");
        region.put("maxLodPixels", "1024");
        assertEquals(45.0, region.getDoubleValue(IKml.NORTH), 1e-8);
        assertEquals(0.0, region.getDoubleValue(IKml.SOUTH), 1e-8);
        assertEquals(180.0, region.getDoubleValue(IKml.EAST), 1e-8);
        assertEquals(90.0, region.getDoubleValue(IKml.WEST), 1e-8);
        kos.write(f); // Feature1

        f = new Feature();
        TaggedMap region2 = new TaggedMap("Region");
        // east will be normalized to +180 in KML output
        region2.put("east", "-180");
        region2.put("west", "90");
        region2.put("north", "45");
        region2.put("south", "0");
        region2.put("minLodPixels", "128");
        region2.put("maxLodPixels", "1024");
        f.setRegion(region2);
        kos.write(f); // Feature2
        kos.write(new ContainerEnd());
        kos.close();

        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        IGISObject o = kis.read();
        assertTrue(o instanceof DocumentStart);
        o = kis.read(); // ContainerStart
        assertNotNull(o);
        o = kis.read(); // Feature1
        //System.out.println(o);
        assertTrue(o instanceof Feature);
        TaggedMap region3 = ((Feature) o).getRegion();
        //System.out.println("region3="+region3);
        assertEquals(region, region3);
        o = kis.read(); // Feature2
        //System.out.println(o);
        assertTrue(o instanceof Feature);
        region = ((Feature) o).getRegion();
        assertNotNull(region);
        assertEquals(45.0, region.getDoubleValue(IKml.NORTH), 1e-8);
        assertEquals(0.0, region.getDoubleValue(IKml.SOUTH), 1e-8);
        assertEquals(180.0, region.getDoubleValue(IKml.EAST), 1e-8);
        assertEquals(90.0, region.getDoubleValue(IKml.WEST), 1e-8);
        kis.close();
    }

    @Test
    public void testRegionAtPole() throws Exception {
        List<Point> pts = new ArrayList<>(5);
        // 3km box that closely matches google earth lat/lon grids lines
        // ctr=(65d 0' 0" E, 89d 54' 18" S) -89.905 65.0
        // bbox=(60d 0' 0" E, 89d 54' 36" S) .. (70d 0' 0" E, 89d 54' 0" S)
        final Point firstPt = new Point(-89.90, 70.0);
        pts.add(firstPt);
        pts.add(new Point(-89.90, 60.0));
        pts.add(new Point(-89.91, 60.0));
        pts.add(new Point(-89.91, 70.0));
        pts.add(firstPt);

        // 68km box at poles wrap IDT
        // Fctr=(143d 7' 27" E, 89d 35' 49" S) -89.59704336185 143.12414264670002
        // bbox westLon/southLat = (11d 14' 23" E, 89d 38' 21" S) .. (84d 59' 29" W, 89d 33' 17" S)
        //final Point firstPt = new Point(-89.6392352247,94.8251951251);
        //pts.add(firstPt);
        //pts.add(new Point(-89.56,-156.6102625609));
        //pts.add(new Point(-89.56,-84.9913917659));
        //pts.add(new Point(-89.6060326809, 11.2396770593));

        // ~500 km box
        // Fctr=(2d 19' 3" E, 87d 10' 35" S) -87.17634638613053 2.317439417192705
        // bbox=(westLon/southLat = 124d 21' 52" W, 87d 21' 40" S) .. (128d 59' 57" E, 86d 59' 30" S)
        //final Point firstPt = new Point(-87.2332504991516, -51.52016400098244);
        //pts.add(firstPt);
        //pts.add(new Point(-86.99157891594791, 54.83615976433807));
        //pts.add(new Point(-87.31129990631749, 128.9992986082008));
        //pts.add(new Point(-87.36111385631315, -124.3644197738154));

        Line line = new Line(pts);
        line.setTessellate(true);
        Geodetic2DPoint cp = line.getCenter();

        //System.out.println("Fctr=" + cp + " " + cp.getLatitudeAsDegrees() + " " + cp.getLongitudeAsDegrees());

        final Geodetic2DBounds bbox = line.getBoundingBox();
        assertTrue(bbox != null && bbox.contains(cp));

        //System.out.println("bbox=" + bbox);
        assertTrue(bbox.getNorthLat().inDegrees() > bbox.getSouthLat().inDegrees());
        //System.out.println("north=" + bbox.getNorthLat().inDegrees() + " south=" + bbox.getSouthLat().inDegrees());
        // north=-89.90 south=-89.91
        //west=60.0 east=70.03

        //System.out.println(bbox.getWestLon().inDegrees() + " " + bbox.getEastLon().inDegrees()); // west=60.0 east=70.0 degs
        //System.out.println(bbox.getWestLon().inRadians() + " " + bbox.getEastLon().inRadians()); // west=1.0 1.2
        // assertTrue(bbox.getEastLon().inDegrees() > bbox.getWestLon().inDegrees()); // fails with 68 km box

        Geodetic2DBounds bounds = new Geodetic2DBounds(bbox);
        bounds.grow(100); // grow 100 meters larger
        assertTrue(bounds.contains(bbox));
        for (Point pt : pts) {
            assertTrue(bounds.contains(pt.asGeodetic2DPoint()));
        }

        // if (bbox.getSouthLat().inDegrees() > bbox.getNorthLat().inDegrees()) System.out.println("XXX: lat sign flipped"); // debug
        // System.out.println("south=" + bbox.getSouthLat().inDegrees() + " north=" + bbox.getNorthLat().inDegrees());

        KmlOutputStream kos = new KmlOutputStream(new FileOutputStream(KML));
        kos.write(new ContainerStart());
        Feature f = new Feature();
        line.setTessellate(true);
        f.setGeometry(line);
        kos.write(f);

        f.setGeometry(new Point(cp));
        kos.write(f);

        f = new Feature();
        // System.out.println("bbox=" + bbox);
        // bbox <coordinates>:
        //60,-89.91
        //60,-89.90
        //70,-89.90
        //70,-89.91
        //60,-89.91
        //</coordinates>
        line = new Line(bbox);

        line.setTessellate(true);
        f.setGeometry(line);
        kos.write(f);

        MGRS mgrs = new MGRS(new MGRS(cp).toString(2)); // BAN0904
        // System.out.println(mgrs);
        bounds = mgrs.getBoundingBox();
        line = new Line(bounds);
        assertTrue(bounds.intersects(bbox));
        assertTrue(bbox.intersects(bounds));
        line.setTessellate(true);
        f.setName("mgrs bbox");
        f.setGeometry(line);
        kos.write(f);

        kos.write(new ContainerEnd());
        kos.close();
    }

    @Test
    public void testNegation() throws IOException, XMLStreamException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);

        ContainerStart cs = new ContainerStart();
        cs.setVisibility(false);
        cs.setOpen(false);
        kos.write(cs);

        NetworkLink nl = new NetworkLink();
        nl.setVisibility(false);
        nl.setOpen(false);
        kos.write(nl);

        kos.write(new ContainerEnd());
        kos.close();

        //System.out.println(bos.toString());

        KmlInputStream kis = new KmlInputStream(new ByteArrayInputStream(bos.toByteArray()));
        IGISObject o = kis.read();
        assertTrue(o instanceof DocumentStart);
        for (int i = 0; i < 2; i++) {
            o = kis.read();
            assertTrue(o instanceof Common);
            Common c = (Common) o;
            Boolean visibility = c.getVisibility();
            assertNotNull(visibility);
            assertFalse(visibility);
            if (c instanceof ContainerStart)
                assertFalse(((ContainerStart) c).isOpen());
            else if (c instanceof NetworkLink)
                assertFalse(((NetworkLink) c).isOpen());
        }
    }

}
