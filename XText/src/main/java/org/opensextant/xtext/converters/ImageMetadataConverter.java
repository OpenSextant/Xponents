package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.opensextant.data.LatLon;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.xtext.ConvertedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse mainly JPEG images or any others that have significant metadata headers
 * headers are tabulated and put into doc conversion as the text buffer (possibly not desirable).
 * And of course if there are loc/time info in the image, such things are pulled out.
 *
 * @author ubaldino
 *
 */
public class ImageMetadataConverter extends ConverterAdapter {
    private final Detector detector = new DefaultDetector();
    private final Parser parser = new AutoDetectParser(detector);
    private final ParseContext ctx = new ParseContext();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private boolean emitMinimalText = true;

    public final static String[] usefulFields = { "geo", "gps", "creation", "date", "model" };

    private final static Set<String> usefulFieldsSet = new HashSet<String>();
    static {
        usefulFieldsSet.addAll(Arrays.asList(usefulFields));
    }

    public ImageMetadataConverter() {
        ctx.set(Parser.class, parser);
    }

    /**
     * This form generates a TEXT version of the JPEG that has the minimal amount of text - GPS*, geo*, model, and creation (date).
     * @param mimimalText true if you wish to save minimal text with conversions; Otherwise default is to format all EXIF or other metadata properties as text
     */
    public ImageMetadataConverter(boolean mimimalText) {
        this();
        emitMinimalText = mimimalText;
    }

    /**
     * filter out irrelevant metadata for text.
     * @param metakey property name
     * @return if property is useful by our standards; see usefulfields
     */
    private static boolean isUseful(String metakey) {
        if (metakey == null) {
            return false;
        }
        String testKey = metakey.toLowerCase();
        for (String key : usefulFields) {
            if (key.contains(testKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Could pull in geodesy to do an Angle(lat,lon).toString() ...
     * @param yx  LatLon object 
     * @return formatted string of LL
     */
    private String formatCoord(LatLon yx) {

        if (GeodeticUtility.validateCoordinate(yx.getLatitude(), yx.getLongitude())) {
            String latHemi = "N";
            String lonHemi = "E";
            if (yx.getLatitude() < 0) {
                latHemi = "S";
            }
            if (yx.getLongitude() < 0) {
                lonHemi = "W";
            }

            return String.format("%2.6f%s %2.6f%s", Math.abs(yx.getLatitude()), latHemi,
                    Math.abs(yx.getLongitude()), lonHemi);
        } else {
            return String.format("invalid Lat %d x Lon %d", yx.getLatitude(), yx.getLongitude());
        }
    }

    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc)
            throws IOException {
        ConvertedDocument imgDoc = new ConvertedDocument(doc);
        imgDoc.setEncoding(ConvertedDocument.OUTPUT_ENCODING);
        imgDoc.is_plaintext = false;

        Metadata metadata = new Metadata();
        StringBuilder buf = new StringBuilder();
        BodyContentHandler handler = new BodyContentHandler();

        String type = "Image";
        String objName = null;
        if (doc != null) {
            objName = doc.getName();
            String ext = FilenameUtils.getExtension(doc.getName().toLowerCase());
            if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                type = "Photo";
            }
        }

        try {
            parser.parse(in, handler, metadata, ctx);

            if (objName == null) {
                objName = metadata.get(Metadata.RESOURCE_NAME_KEY);
            }

            // What is the signal to generate any text buffer at all?
            // Is it worth puttting out a full EXIF dump for a JPEG?
            //
            int mdCount = metadata.names().length;
            if (mdCount == 0) {
                // No meaningful text or other metadata.
                return null;
            }

            buf.append("Image Specifications\n===================\n");

            List<String> metaKeys = Arrays.asList(metadata.names());
            Collections.sort(metaKeys);

            for (String key : metaKeys) {
                if (this.emitMinimalText && !isUseful(key)) {
                    continue;
                }
                String val = metadata.get(key);
                if (StringUtils.isBlank(val)) {
                    val = "(N/A)";
                }
                buf.append(String.format("%s:\t%s\n", key, val));
            }

            // Title
            imgDoc.addTitle(String.format("%s: %s", type, objName));

            // Author
            imgDoc.addAuthor(metadata.get(TikaCoreProperties.CREATOR));

            // Date
            imgDoc.addCreateDate(metadata.getDate(TikaCoreProperties.CREATED));

            // Geographic
            String lat = metadata.get(TikaCoreProperties.LATITUDE);
            String lon = metadata.get(TikaCoreProperties.LONGITUDE);

            // Location if available.
            if (lat != null && lon != null) {
                logger.info("Found a location LAT={} LON={}", lat, lon);
                // imgDoc.addProperty("location", String.format("%2.8f,%3.8f", ));
                imgDoc.addUserProperty("location", String.format("%s, %s", lat, lon));
                try {
                    LatLon yx = GeodeticUtility.parseLatLon(lat, lon);
                    buf.append("Location:\t" + formatCoord(yx) + "\n");
                } catch (ParseException parseErr) {
                    //
                }
            }

            // EXIF and other text content
            imgDoc.setText(buf.toString());

            return imgDoc;
        } catch (Exception xerr) {
            throw new IOException("Unable to parse content", xerr);
        } finally {
            in.close();
        }
    }
}
