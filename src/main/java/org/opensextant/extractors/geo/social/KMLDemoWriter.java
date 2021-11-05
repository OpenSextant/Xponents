package org.opensextant.extractors.geo.social;

import org.opensextant.data.Language;
import org.opensextant.data.social.Message;
import org.opensextant.data.social.Tweet;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Style;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.utils.Color;
import org.opensextant.util.TextUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A cleaner approach to outputting geocoding data to KML using GISCore.
 * See also Xponents GISDataFormatter, FormatterFactory.getInstance("KML")
 * See also Xponents-Examples where the concept of GIS outputs is somewhat
 * generalized, but
 * not very flexible.
 *
 * @author ubaldino
 */
public class KMLDemoWriter implements Closeable {

    protected IGISOutputStream outputStream = null;
    Namespace gxNs = Namespace.getNamespace("gx", IKml.NS_GOOGLE_KML_EXT);
    Namespace kmlNs = Namespace.getNamespace(IKml.KML_NS);

    public KMLDemoWriter(String path) throws IOException {
        File kml = new File(path);

        FileOutputStream fos = new FileOutputStream(kml);

        try {
            outputStream = GISFactory.getOutputStream(DocumentType.KML, fos);
            DocumentStart ds = new DocumentStart(DocumentType.KML);
            ds.getNamespaces().add(kmlNs);
            // ds.getNamespaces().add(gxNs);
            outputStream.write(ds);
            outputStream.write(new ContainerStart());
        } catch (Exception err) {
            throw new IOException("Unlikely error", err);
        }
    }

    static Style ugeoStyle = new Style();
    static Style geoStyle = new Style();
    static Style mentionGeoStyle = new Style();
    static Map<String, Style> styles = new HashMap<>();

    static {
        // ugeoStyle.setBalloonStyle(Color.BLUE, null, null, null);
        // geoStyle.setBalloonStyle(Color.ORANGE, null, null, null);
        ugeoStyle.setIconStyle(Color.CYAN, 0.8);
        geoStyle.setIconStyle(Color.ORANGE, 0.8);
        mentionGeoStyle.setIconStyle(Color.MAGENTA, 0.8);
        styles.put("geo", geoStyle);
        styles.put("ugeo", ugeoStyle);
        styles.put("geo-mention", ugeoStyle);
    }

    /**
     * Save off a tweet and its geo.
     * TODO: confidence number should be more standardized and put into a 0..100
     * scale
     * ahead of time. Currently, 'confidence' might be a raw score as in ADNA
     *
     * @param t
     *          tweet
     * @param a
     *          annotation which must have a coordinate "lat", "lon"
     */
    public void write(Tweet t, GeoInference a) {

        Feature f = new Feature();
        // LABEL -- Place name + Inference
        String locName = a.geocode.getPlaceName();

        String label = String.format("%s %s (%s, %d%%)", a.inferenceName, locName, a.contributor, a.confidence);
        f.setName(label);
        f.setStyle(styles.get(a.inferenceName));

        // LOCATION
        f.setGeometry(new Point(a.geocode.getLatitude(), a.geocode.getLongitude()));

        // MESSAGE
        Language l = TextUtils.getLanguage(t.lang);
        String lname = l != null ? l.getName() : t.lang;
        StringBuilder text = new StringBuilder(String.format("@%s \"%s\"<br>in %s", t.authorID, t.getText(), lname));

        // DATE/TIME metadata
        if (t.timezone != null) {
            text.append(String.format("<br>TZ=%s", t.timezone));
        }
        if (t.utcOffset != Message.UNSET_UTC_OFFSET) {
            text.append(String.format("<br>UTC=%s", t.utcOffsetHours));
        }
        // Given Geo-data
        if (t.authorGeo != null) {
            text.append("<br>ugeo=");
            text.append(t.authorGeo);
        }
        if (t.statusGeo != null) {
            text.append("<br>geo=");
            text.append(t.statusGeo);
        }
        f.setDescription(text.toString());

        // TIMESTAMP
        f.setStartTime(t.date);

        outputStream.write(f);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

}
