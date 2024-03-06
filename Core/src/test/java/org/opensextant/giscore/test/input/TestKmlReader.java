package org.opensextant.giscore.test.input;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.GroundOverlay;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.NetworkLink;
import org.opensextant.giscore.events.Style;
import org.opensextant.giscore.events.TaggedMap;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.KmlReader;
import org.opensextant.giscore.input.kml.UrlRef;
import org.opensextant.giscore.output.kml.KmlOutputStream;
import org.opensextant.giscore.test.output.TestKmlOutputStream;
import static org.junit.Assert.*;

/**
 * @author Jason Mathews, MITRE Corp.
 * Date: Mar 30, 2009 1:12:51 PM
 */
public class TestKmlReader implements IKml {

    /**
     * Test loading KMZ file with network link containing embedded KML
     * then load the content from the NetworkLink.
     *
     * @throws IOException if an I/O error occurs
     */
    // @Test
    public void testKmzNetworkLinks() throws IOException {
        File file = new File("data/kml/kmz/dir/content.kmz");
        KmlReader reader = new KmlReader(file);
        List<IGISObject> features = reader.readAll(); // implicit close
        assertEquals(5, features.size());

        IGISObject o = features.get(2);
        assertTrue(o instanceof NetworkLink);
        NetworkLink link = (NetworkLink) o;
        final URI linkUri = KmlReader.getLinkUri(link);
        assertNotNull(linkUri);
        // href = kmzfile:/C:/giscoreHome/data/kml/kmz/dir/content.kmz?file=kml/hi.kml
        assertTrue(linkUri.toString().endsWith("content.kmz?file=kml/hi.kml"));

        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        List<URI> networkLinks = reader.getNetworkLinks();
        assertEquals(1, networkLinks.size());
        assertEquals(2, linkedFeatures.size());
        o = linkedFeatures.get(1);
        assertTrue(o instanceof Feature);
        Feature ptFeat = (Feature) o;
        Geometry geom = ptFeat.getGeometry();
        assertTrue(geom instanceof Point);

        // import same KMZ file as URL
        URL url = file.toURI().toURL();
        KmlReader reader2 = new KmlReader(url);
        List<IGISObject> features2 = reader2.readAll();
        List<IGISObject> linkedFeatures2 = reader2.importFromNetworkLinks();
        List<URI> networkLinks2 = reader2.getNetworkLinks();
        assertEquals(5, features2.size());
        assertEquals(1, networkLinks2.size());
        assertEquals(2, linkedFeatures2.size());
        // NetworkLinked Feature -> DocumentStart + Feature
        TestKmlOutputStream.checkApproximatelyEquals(ptFeat, linkedFeatures2.get(1));
    }

    @Test
    public void testInputStream() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        KmlOutputStream kos = new KmlOutputStream(bos);
        Feature f = new Feature();
        f.setName("test");
        f.setGeometry(new Point(new Geodetic2DPoint(
                new Longitude(2, Angle.DEGREES),
                new Latitude(48, Angle.DEGREES))));
        kos.write(f);
        kos.close();
        byte[] bytes = bos.toByteArray();
        KmlReader reader = new KmlReader(new ByteArrayInputStream(bytes),
                new URL("http://localhost/test.kml"), null);
        List<IGISObject> features = reader.readAll(); // implicit close
        assertFalse(features.isEmpty());
        assertEquals(2, features.size());
        assertEquals(f, features.get(1));
    }

    // @Test
    public void testLimitNetworkLinks() throws IOException {
        File file = new File("data/kml/kmz/networklink/hier.kmz");
        KmlReader reader = new KmlReader(file);
        reader.setMaxLinkCount(1);
        reader.readAll();
        assertFalse(reader.isMaxLinkCountExceeded());

        // without limit size=4 / with limit size=2
        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        assertTrue(reader.isMaxLinkCountExceeded());
        assertEquals(2, linkedFeatures.size());

        List<URI> networkLinks = reader.getNetworkLinks();
        assertEquals(2, networkLinks.size());
    }

    /**
     * Test loading compressed byte stream for KMZ via InputStream
     *
     * @throws IOException if an I/O error occurs
     */
    // @Test
    public void testKmzUrl() throws IOException {
        URL url = new File("data/kml/kmz/networklink/hier.kmz").toURI().toURL();
        InputStream is = UrlRef.getInputStream(url);
        KmlReader reader = new KmlReader(is, true, url, null);
        List<IGISObject> features = reader.readAll(); // implicit close
        assertFalse(features.isEmpty());
        List<URI> networkLinks = reader.getNetworkLinks();
        assertEquals(2, networkLinks.size());

        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        assertEquals(4, linkedFeatures.size());
    }

    /**
     * Test loading compressed byte stream for KMZ with NonProxy Proxy via InputStream
     *
     * @throws IOException if an I/O error occurs
     */
    // @Test
    public void testUrlProxy() throws IOException {
		/*
		<kml xmlns="http://www.opengis.net/kml/2.2">
		 <Document>
		  <NetworkLink>
			<Link>
			  <href>within.kml</href>
			</Link>
		  </NetworkLink>
		  <NetworkLink>
			<Link>
			  <href>outside.kml</href>
			</Link>
		  </NetworkLink>
		 </Document>
		</kml>
		*/
        URL url = new File("data/kml/kmz/networklink/hier.kmz").toURI().toURL();
        InputStream is = UrlRef.getInputStream(url, Proxy.NO_PROXY);
        KmlReader reader = new KmlReader(is, true, url, Proxy.NO_PROXY);
        List<IGISObject> features = reader.readAll(); // implicit close
        assertFalse(features.isEmpty());
        List<URI> networkLinks = reader.getNetworkLinks();
        assertEquals(2, networkLinks.size());
    }

    /**
     * Targets of NetworkLinks may exist inside a KMZ as well as outside at
     * the same context as the KMZ resource itself so test such a KMZ file.
     *
     * @throws IOException if an I/O error occurs
     */
    // @Test
    public void testKmzOutsideNetworkLinks() throws IOException {
        File file = new File("data/kml/kmz/networklink/hier.kmz");
        // e.g. http://kml-samples.googlecode.com/svn/trunk/kml/kmz/networklink/hier.kmz
        KmlReader reader = new KmlReader(file);
        List<IGISObject> objs = reader.readAll(); // implicit close
        assertEquals(5, objs.size());
        /*
        for(IGISObject obj : objs) {
            if (obj instanceof NetworkLink) {
                NetworkLink nl = (NetworkLink)obj;
                URI linkUri = KmlReader.getLinkUri(nl);
                assertNotNull(linkUri);
                UrlRef urlRef = new UrlRef(linkUri);
                InputStream is = null;
                try {
                    is = urlRef.getInputStream();
                } finally {
                    IOUtils.closeQuietly(is);
                }
            }
        }
        */
        List<URI> networkLinks = reader.getNetworkLinks();
        assertEquals(2, networkLinks.size());

        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        assertEquals(4, linkedFeatures.size());

        // System.out.println("linkedFeatures=" + linkedFeatures);
        // within.kml ->  <name>within.kml</name>
        // outside.kml -> name>outside.kml</name>
        IGISObject o1 = linkedFeatures.get(1);
        assertTrue(o1 instanceof Feature && "within.kml".equals(((Feature) o1).getName()));
        IGISObject o3 = linkedFeatures.get(3);
        assertTrue(o3 instanceof Feature && "outside.kml".equals(((Feature) o3).getName()));
    }

    // @Test
    public void testNetworkLinksWithCallback() throws IOException {
        File file = new File("data/kml/kmz/networklink/hier.kmz");
        KmlReader reader = new KmlReader(file);
        List<IGISObject> objs = reader.readAll(); // implicit close
        assertEquals(5, objs.size());
        final List<IGISObject> linkedFeatures = new ArrayList<>();
        reader.importFromNetworkLinks(new KmlReader.ImportEventHandler() {
            public boolean handleEvent(UrlRef ref, IGISObject gisObj) {
                linkedFeatures.add(gisObj);
                return false; // explicitly force import to abort
            }

            public void handleError(URI uri, Exception ex) {
                //ignore
            }
        });
        List<URI> networkLinks = reader.getNetworkLinks();
        assertEquals(2, networkLinks.size());
        // only one feature is added before import is aborted
        assertEquals(1, linkedFeatures.size());
    }

    /**
     * Test loading KMZ file with 2 levels of network links
     * recursively loading each NetworkLink.
     *
     * @throws IOException if an I/O error occurs
     */
    // @Test
    public void testMultiLevelNetworkLinks() throws IOException {
        File file = new File("data/kml/NetworkLink/multiLevelNetworkLinks2.kmz");
        KmlReader reader = new KmlReader(file);
        List<IGISObject> objs = reader.readAll(); // implicit close
        assertEquals(5, objs.size());
        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        List<URI> networkLinks = reader.getNetworkLinks();

        assertEquals(2, networkLinks.size());
        assertEquals(7, linkedFeatures.size());
        IGISObject o = linkedFeatures.get(6);
        assertTrue(o instanceof Feature);
        Feature ptFeat = (Feature) o;
        Geometry geom = ptFeat.getGeometry();
        assertTrue(geom instanceof Point);
    }

    /**
     * Test loading KMZ file with 2 levels of network links
     * recursively loading each NetworkLink using callback to handle
     * objects found in network links.
     *
     * @throws IOException if an I/O error occurs                             `
     */
    // @Test
    public void testMultiLevelNetworkLinksWithCallback() throws IOException {
        File file = new File("data/kml/NetworkLink/multiLevelNetworkLinks2.kmz");
        KmlReader reader = new KmlReader(file);
        List<IGISObject> objs = reader.readAll(); // implicit close
        assertEquals(5, objs.size());
        final List<IGISObject> linkedFeatures = new ArrayList<>();
        reader.importFromNetworkLinks(new KmlReader.ImportEventHandler() {
            public boolean handleEvent(UrlRef ref, IGISObject gisObj) {
                linkedFeatures.add(gisObj);
                return true;
            }

            public void handleError(URI uri, Exception ex) {
                //ignore
            }
        });
        List<URI> networkLinks = reader.getNetworkLinks();

        assertEquals(2, networkLinks.size());
        assertEquals(7, linkedFeatures.size());
        IGISObject o = linkedFeatures.get(6);
        assertTrue(o instanceof Feature);
        Feature ptFeat = (Feature) o;
        Geometry geom = ptFeat.getGeometry();
        assertTrue(geom instanceof Point);
    }

    // @Test
    public void testRewriteStyleUrls() throws Exception {
        KmlReader reader = new KmlReader(new File("data/kml/Style/remote-style.kml"));
        reader.setRewriteStyleUrls(true);
        int count = 0;
        try {
            IGISObject gisObj;
            while ((gisObj = reader.read()) != null) {
                count++;
                if (gisObj.getClass() == Feature.class) {
                    Feature f = (Feature) gisObj;
                    if ("relative".equals(f.getId())) {
                        final String styleUrl = f.getStyleUrl();
                        assertTrue(styleUrl.startsWith("file:"));
                        URL url = new URL(styleUrl);
                        int len = url.openConnection().getContentLength();
                        // external style file exists and must have length greater than 0
                        assertTrue(len > 0);
                    }
                }
            }
        } finally {
            reader.close();
        }
        assertEquals(9, count);
    }

    // @Test
    public void testRelativeKmzStyles() throws Exception {
        //System.out.println("*** testRelativeKmzStyles");
        KmlReader reader = new KmlReader(new File("data/kml/Style/rel-styles.kmz"));
        reader.setRewriteStyleUrls(true);
        List<IGISObject> features = reader.readAll(); // implicit close
        assertEquals(5, features.size());
        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        assertEquals(11, linkedFeatures.size());
        for (IGISObject gisObj : linkedFeatures) {
            if (gisObj.getClass() == Feature.class) {
                Feature f = (Feature) gisObj;
                final String styleUrl = f.getStyleUrl();
                if ("P2.2".equals(f.getName())) {
                    assertEquals("#localStyle_relUrl", styleUrl);
                    // System.out.println(styleUrl);
                } else {
                    // kmzfile:/C:/projects/giscore/data/kml/Style/rel-styles.kmz?file=styles.kml#style1
                    assertTrue(styleUrl.startsWith("kmzfile:"));
                    UrlRef urlRef = new UrlRef(new URI(styleUrl));
                    assertTrue(urlRef.isKmz());
                    assertTrue(urlRef.getKmzRelPath().startsWith("styles.kml#"));
                }
            }
        }
    }

    /**
     * Test ground overlay from KMZ file target
     */
    // @Test
    public void testKmzFileOverlay() throws Exception {
        // target overlay URI -> kmzfile:/C:/projects/giscore/data/kml/GroundOverlay/etna.kmz?file=etna.jpg
        checkGroundOverlay(new KmlReader(new File("data/kml/GroundOverlay/etna.kmz")));
    }

    /**
     * Test ground overlays with KML from URL target
     */
    // @Test
    public void testUrlOverlay() throws Exception {
        // target overlay URI -> file:/C:/projects/giscore/data/kml/GroundOverlay/etna.jpg
        checkGroundOverlay(new KmlReader(new File("data/kml/GroundOverlay/etna.kml").toURI().toURL()));
    }

    private void checkGroundOverlay(KmlReader reader) throws Exception {
        List<IGISObject> features = reader.readAll(); // implicit close
        assertEquals(2, features.size());
        IGISObject obj = features.get(1);
        assertTrue(obj instanceof GroundOverlay);
        GroundOverlay o = (GroundOverlay) obj;
        TaggedMap icon = o.getIcon();
        String href = icon != null ? icon.get(HREF) : null;
        assertNotNull(href);
        //System.out.println(href);
        UrlRef urlRef = new UrlRef(new URI(href));
        //System.out.println(urlRef);
        InputStream is = null;
        try {
            is = urlRef.getInputStream();
            BufferedImage img = ImageIO.read(is);
            assertNotNull(img);
            assertEquals(418, img.getHeight());
            assertEquals(558, img.getWidth());
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    // create test KmlReader that allows access to getLinkHref() for testing
    private static class StubKmlReader extends KmlReader {
        StubKmlReader(File file) throws IOException {
            super(file);
        }

        URI checkLink(UrlRef parent, TaggedMap links) {
            return getLinkHref(parent, links);
        }
    }

    // @Test
    public void testLinkHref() throws IOException {
        String href = "http://127.0.0.1/kmlsvc";

        final File file = new File("data/kml/Placemark/placemark.kml");
        StubKmlReader reader = new StubKmlReader(file);

        // If you specify a <viewRefreshMode> of onStop and do not include the <viewFormat> tag in the file,
        // the following information is automatically appended to the query string: BBOX=[bboxWest],...
        realTestLink(reader, new String[]{"href", href,
                // viewFormat = null
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP}, "?BBOX=-180", null);
        // expected -> http://127.0.0.1/kmlsvc?BBOX=-180,-45,180,90

        // If you specify an empty <viewFormat> tag, no viewFormat information is appended to the query string.
        realTestLink(reader, new String[]{"href", href,
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP, VIEW_FORMAT, ""}, null, "BBOX=");
        // expected -> http://127.0.0.1/kmlsvc

        // HREF with existing http query parameters - append parameters with '&' don't add another "?" to the URL
        realTestLink(reader, new String[]{"href", href + "?",
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP}, "?BBOX=-180", null);
        // expected -> http://127.0.0.1/kmlsvc?BBOX=-180,-45,180,90
        realTestLink(reader, new String[]{"href", href + "?foo=bar",
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP}, "&BBOX=-180", null);
        // expected -> http://127.0.0.1/kmlsvc?foo=bar&BBOX=-180,-45,180,90

        // encodes whitespace
        realTestLink(reader, new String[]{"href", href,
                "httpQuery", "p=foo bar"}, "p=foo%20bar", null);
        // expected -> http://127.0.0.1/kmlsvc?p=foo%20bar

        realTestLink(reader, new String[]{"href", href,
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_REGION, REFRESH_MODE, REFRESH_MODE_ON_INTERVAL,
                "httpQuery", "clientVersion=[clientVersion]&kmlVersion=[kmlVersion]&lang=[language]",
                "viewFormat", "BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]"}, "kmlVersion=2.2", "BBOX=0,0,0,0");
        // expected -> http://127.0.0.1/kmlsvc?clientVersion=5.2.1.1588&kmlVersion=2.2&lang=en&BBOX=-180,-45,180,90

        // test [name] strings in httpQuery/viewFormat that are not part of standard set - there are passed as-is
        realTestLink(reader, new String[]{"href", href,
                // viewRefreshMode=never (default), refreshMode=onChange (default)
                "httpQuery", "foo=[bar]&lang=[language]"}, "foo=%5Bbar%5D", "BBOX=");
        // expected -> http://127.0.0.1/kmlsvc?foo=%5Bbar%5D&lang=en
        realTestLink(reader, new String[]{"href", href,
                // viewRefreshMode=never (default), refreshMode=onChange (default)
                "viewFormat", "foo=[bar]"}, "foo=%5Bbar%5D", "BBOX=");
        // expected -> http://127.0.0.1/kmlsvc?foo=%5Bbar%5D

        // If you specify an empty <viewFormat> tag, no information is appended to the query string
        // but null value with <viewRefreshMode> of onStop gets default BBOX fields
        realTestLink(reader, new String[]{"href", href,
                "viewFormat", "",
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP}, null, "BBOX");
        // expected -> http://127.0.0.1/kmlsvc

        // cameraLat/Lon viewFormat entities
        realTestLink(reader, new String[]{"href", href,
                "viewFormat", "c=[cameraLat],[cameraLon],[cameraAlt]",
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_REQUEST}, "?c=0,0,0", "[cameraLon]");
        // expected -> http://127.0.0.1/kmlsvc?c=0,0,0

        // file href will not have any viewFormat or httpQuery parameters appended to URL
        realTestLink(reader, new String[]{"href", file.toURI().toASCIIString(),
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP, REFRESH_MODE, REFRESH_MODE_ON_EXPIRE,
                "viewFormat", "BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]"}, null, "BBOX=");
        // expected -> file:/C:/projects/giscore/data/kml/Placemark/placemark.kml

        realTestLink(reader, new String[]{"href", href,
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP, REFRESH_MODE, REFRESH_MODE_ON_CHANGE,
                "viewFormat", "BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]&terrain=[terrainEnabled]"}, "BBOX=", "BBOX=0,0,0,0");
        // expected > http://127.0.0.1/kmlsvc?BBOX=-180,-45,180,90&terrain=1

        // viewRefreshMode = never (default) - Ignore changes in the view. Also ignore <viewFormat> parameters, if any (sent as all 0's).
        realTestLink(reader, new String[]{"href", href,
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_NEVER, REFRESH_MODE, REFRESH_MODE_ON_EXPIRE,
                "viewFormat", "BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]"}, "BBOX=0,0,0,0", null);
        realTestLink(reader, new String[]{"href", href,
                // viewRefreshMode=never (default), refreshMode=onChange (default)
                "viewFormat", "BBOX=[bboxWest],[bboxSouth],[bboxEast],[bboxNorth]"}, "BBOX=0,0,0,0", null);
        // expected -> http://127.0.0.1/kmlsvc?BBOX=0,0,0,0

        // override the default Link settings for target Google Earth client
        KmlReader.setHttpQuery("clientVersion", "6.0.3.2197");
        reader.setViewFormat("lookatHeading", "5");
        realTestLink(reader, new String[]{"href", href,
                VIEW_REFRESH_MODE, VIEW_REFRESH_MODE_ON_STOP, "viewFormat", "heading=[lookatHeading]",
                "httpQuery", "clientVersion=[clientVersion]"}, "clientVersion=6.0.3.2197&heading=5", null);
        // expected -> http://127.0.0.1/kmlsvc?clientVersion=6.0.3.2197&heading=5
    }

    private void realTestLink(StubKmlReader reader, String[] values, String expectedSubstring, String notSubstring) {
        TaggedMap links = new TaggedMap(LINK);
        for (int i = 0; i < values.length; i += 2)
            links.put(values[i], values[i + 1]);
        // System.out.println("XXX:" + links);
        URI uri = reader.checkLink(null, links);
        String href = uri.toString();
        // System.out.println("XXX:" + href);
        if (expectedSubstring != null) assertTrue(href.contains(expectedSubstring));
        if (notSubstring != null) assertFalse(href.contains(notSubstring));
        // System.out.println();
    }

    /**
     * Test IconStyle with KML from URL target with relative URL to icon
     * @throws Exception
     */
    // @Test
    public void testIconStyle() throws Exception {
        checkIconStyle(new KmlReader(new File("data/kml/Style/styled_placemark.kml").toURI().toURL()));
    }

    /**
     * Test IconStyle from KMZ file target with icon inside KMZ
     * @throws Exception
     */
    // @Test
    public void testKmzIconStyle() throws Exception {
        checkIconStyle(new KmlReader(new File("data/kml/kmz/iconStyle/styled_placemark.kmz")));
    }

    // @Test
    public void testIgnoreRegions() throws IOException {
        KmlReader reader = new KmlReader(new File("data/kml/Region/networkLink-regions.kmz"));
        reader.setIgnoreInactiveRegionNetworkLinks(true);
        assertTrue(reader.isIgnoreInactiveRegionNetworkLinks());
        assertEquals(reader.readAll().size(), 8);
        // NOTE: readAll() calls close() when done
        assertEquals(reader.getSkipCount(), 0);
        assertEquals(reader.getNetworkLinks().size(), 5);

        reader = new KmlReader(new File("data/kml/Region/networkLink-regions.kmz"));
        // define view around San Francisco which intersects all but one region
        reader.setViewFormat(IKml.BBOX_NORTH, "37.83");
        reader.setViewFormat(IKml.BBOX_SOUTH, "37.79");
        reader.setViewFormat(IKml.BBOX_EAST, "-122.45");
        reader.setViewFormat(IKml.BBOX_WEST, "-122.5");
        reader.setIgnoreInactiveRegionNetworkLinks(true);
        assertEquals(reader.readAll().size(), 8);
        assertEquals(reader.getSkipCount(), 1);
        assertEquals(reader.getNetworkLinks().size(), 4);

        reader = new KmlReader(new File("data/kml/Region/networkLink-regions.kmz"));
        // define view outside all regions
        reader.setIgnoreInactiveRegionNetworkLinks(true);
        reader.setViewFormat(IKml.BBOX_NORTH, "10");
        reader.setViewFormat(IKml.BBOX_SOUTH, "-10");
        reader.setViewFormat(IKml.BBOX_EAST, "10");
        reader.setViewFormat(IKml.BBOX_WEST, "-10");
        assertEquals(reader.readAll().size(), 8);
        assertEquals(reader.getSkipCount(), 5);
        assertEquals(reader.getNetworkLinks().size(), 0);
    }

    private void checkIconStyle(KmlReader reader) throws Exception {
        List<IGISObject> features = new ArrayList<>();
        try {
            IGISObject gisObj;
            while ((gisObj = reader.read()) != null) {
                features.add(gisObj);
            }
        } finally {
            reader.close();
        }
		/*
		for(Object o : features) {
			System.out.println(" >" + o.getClass().getName());
		}
		System.out.println();
		*/
        assertEquals(2, features.size());

        IGISObject obj = features.get(1);
        assertTrue(obj instanceof Feature);
        Feature f = (Feature) obj;
        Style style = (Style) f.getStyle();
        assertTrue(style.hasIconStyle());
        String href = style.getIconUrl();
        assertNotNull(href);
        UrlRef urlRef = new UrlRef(new URI(href));
        InputStream is = null;
        try {
            is = urlRef.getInputStream();
            BufferedImage img = ImageIO.read(is);
            assertNotNull(img);
            assertEquals(80, img.getHeight());
            assertEquals(80, img.getWidth());
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

}
