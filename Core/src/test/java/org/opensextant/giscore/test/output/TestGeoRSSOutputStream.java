package org.opensextant.giscore.test.output;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.output.XmlOutputStreamBase;
import org.opensextant.giscore.output.rss.GeoRSSOutputStream;
import org.opensextant.giscore.output.rss.IRss;
import org.opensextant.giscore.test.TestGISBase;

import static org.opensextant.giscore.test.TestSupport.OUTPUT;

/**
 * @author Jason Mathews, MITRE Corp.
 * Date: Jun 6, 2009 7:43:55 PM
 */
public class TestGeoRSSOutputStream extends TestGISBase implements IRss {

    /**
     * Simple test to output GeoRSS
     * @throws IOException
     * @throws XMLStreamException
     */
    // @Test
    public void testGeoRss() throws IOException, XMLStreamException {
        exportRss(new File("data/kml/Placemark/placemarks.kml"));
        exportRss(new File("data/kml/MultiGeometry/testLayers.kml"));
    }

    private void exportRss(File file) throws IOException, XMLStreamException {
        Namespace NS = Namespace.getNamespace("ext", "http://giscore.mitre.org/ext");
        IGISInputStream kis = GISFactory.getInputStream(DocumentType.KML, new FileInputStream(file));
        String name = file.getName();
        int ind = name.lastIndexOf('.');
        if (ind > 0) name = name.substring(0,ind) + ".xml";
        else name += ".xml";
        File out = new File(OUTPUT  + "/" + name);

        // create mappings of non-rss element names to explicit namespaces
        Map<String, Namespace> namespaceMap = new HashMap<>(1);
        namespaceMap.put("test", NS);

        Map<String,Object> channelMap = new HashMap<>();
        channelMap.put(TITLE, "Test GeoRSS Feed");
        channelMap.put(LINK, "http://giscore.mitre.org");
        channelMap.put(DESCRIPTION, "this is a test");
        channelMap.put("test", "this is an extended element"); // uses "ext:" namespace prefix in output

        GeoRSSOutputStream os = new GeoRSSOutputStream(new FileOutputStream(out), XmlOutputStreamBase.ISO_8859_1,
                namespaceMap, channelMap);
        try {
            IGISObject current;
            while ((current = kis.read()) != null) {
                if (current instanceof Feature) {
                    Feature f = (Feature)current;
                    Date date = new Date();
                    f.putData(new SimpleField("test"), Long.toHexString(date.getTime()));
                    f.putData(new SimpleField(PUB_DATE), date); // or use start/end times ??
                    //System.out.println(f);
                }
                os.write(current);
            }
        } finally {
            kis.close();
        }
        try {
            os.close();
        } catch (IOException e) {
        }
    }

    // @Test
	public void testMultiGeometries() throws Exception {
        File out = new File(OUTPUT + "/testMultiGeometries-rss.xml");
        GeoRSSOutputStream os = new GeoRSSOutputStream(new FileOutputStream(out),
                XmlOutputStreamBase.ISO_8859_1, null, null);
        List<Feature> feats = getMultiGeometries();
        for (Feature f : feats) {
            os.write(f);
        }
        os.close();
    }

}
