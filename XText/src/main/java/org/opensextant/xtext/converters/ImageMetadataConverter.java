package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
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
    private Detector detector = new DefaultDetector();
    private Parser parser = new AutoDetectParser(detector);
    private ParseContext ctx = new ParseContext();
    private Logger logger = LoggerFactory.getLogger(getClass());


    public ImageMetadataConverter() {
        ctx.set(Parser.class, parser);
    }

    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc) throws IOException {
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
            
            List<String> metaKeys =  Arrays.asList(metadata.names());
            Collections.sort(metaKeys);

            for (String key : metaKeys) {
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

            // EXIF and other text content
            imgDoc.setText(buf.toString());

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
            }

            return imgDoc;
        } catch (Exception xerr) {
            throw new IOException("Unable to parse content", xerr);
        } finally {
            in.close();
        }
    }

}
