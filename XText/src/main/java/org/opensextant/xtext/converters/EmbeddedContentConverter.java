/**
 *
 * Copyright 2014 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2014 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.extractor.EmbeddedResourceHandler;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.ParseContext;
import org.opensextant.xtext.Content;
import org.opensextant.xtext.ConvertedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedContentConverter extends DefaultConverter {
    ParseContext context = new ParseContext();
    Logger log = LoggerFactory.getLogger(getClass());

    private final static Set<String> supportedTypes = new HashSet<String>();

    static {
        supportedTypes.add("pptx");
        supportedTypes.add("ppt");
        supportedTypes.add("docx");
        supportedTypes.add("doc");
        supportedTypes.add("pdf");
        // Tika 1.5 test case only -
        // supportedTypes.add("xls");
    }

    public EmbeddedContentConverter() {
        super();
    }

    public EmbeddedContentConverter(int sz) {
        super(sz);
    }

    /**
     * If file type is NOT supported, the ConvertedDocument from the DefaultConverter will be returned.
     * if the file type is supported, the ConvertedDocument from the default is used as the parent to
     * organize the embedded items found within.  Embedded items are organized on disk with metadata.
     *
     * Supported = MS PPT/PPTX, DOC/DOCX, PDF
     * @param fileext ext
     * @return  true if file extension is supported.
     */
    public static boolean isSupported(String fileext) {
        if (StringUtils.isBlank(fileext)) {
            return false;
        }
        return supportedTypes.contains(fileext.toLowerCase());
    }

    /**
     * Convert Embedded documents in the supported types to a folder of the embedded items.
     * Trivial embedded icons and other components will not be extracted
     *
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc)
            throws IOException {
        ConvertedDocument compoundDoc = super.conversionImplementation(in, doc);

        String ext = FilenameUtils.getExtension(doc.getName());
        if (!isSupported(ext)) {
            // We know we don't support textifying compound docs of this type.  DONE!
            //
            return compoundDoc; // Not really compound by our standards here.
        }

        ParserContainerExtractor extractor = new ParserContainerExtractor();
        EmbeddedObjectExtractor objExtractor = new EmbeddedObjectExtractor(compoundDoc, true);

        TikaInputStream tikaStream = null;
        try {
            tikaStream = TikaInputStream.get(doc.toPath());
            extractor.extract(tikaStream, extractor, objExtractor);
            compoundDoc.is_converted = true;
            if (compoundDoc.hasRawChildren()) {
                // Create text buffer for this compound document here.
                // If raw children should be post-processed by some other means, that is up to caller.
                // This parent document at least contains a complete text representation of the content in the original doc.
                StringBuilder completeText = new StringBuilder();

                completeText.append(compoundDoc.getText());
                completeText.append("\n==Embedded Objects==\n");
                completeText.append(renderText(compoundDoc.getRawChildren()));

                compoundDoc.setText(completeText.toString());
                compoundDoc.is_converted = true;
                return compoundDoc;
            } else {
                // Okay, the complicated Embedded doc approach did not yied anything.
                // Try the simple approach.
                return compoundDoc;
            }
        } catch (Exception e) {
            throw new IOException("Stream parsing problem", e);
        } finally {
            tikaStream.close();
        }
    }

    private final DefaultConverter conv = new DefaultConverter();

    /**
     *
     * @param childObjects children
     * @return text assembled from children
     */
    private String renderText(List<Content> childObjects) {
        StringBuilder buf = new StringBuilder();
        for (Content c : childObjects) {

            buf.append(String.format("%n[Embedded: %s; %s]%n", c.id, c.tikaMediatype.toString()));
            try {
                // NOTE: To do this well, you may have to write bytes to disk as a valid file name
                //  And let Tika convert in full.

                ConvertedDocument text = conv.conversionImplementation(
                        TikaInputStream.get(c.content, c.tikaMetadata), null);
                buf.append(text.getText());
            } catch (IOException ioe) {
                buf.append("Unconvertable content");
            }

            buf.append("\n");
        }

        return buf.toString();
    }

    private final static Set<String> filterableMeta = new HashSet<String>();
    static {
        filterableMeta.add("application/x-emf");
        filterableMeta.add("application/x-msmetafile");

        // PNG is not trivial item, as are icons or EMF stuff.
        // filter out particular mime-types by situation in resource handler EmbeddedObjectExtractor
        // filterableMeta.add("image/png");
    }

    /**
     * Embedded extractor here saves embedded objects to folder structure.
     *
     * @author ubaldino
     *
     */
    class EmbeddedObjectExtractor implements EmbeddedResourceHandler {

        ConvertedDocument parent = null;
        int objectCount = 0;
        boolean filterOut = true;

        EmbeddedObjectExtractor(ConvertedDocument par, boolean filterTrivia) throws IOException {
            parent = par;
            filterOut = filterTrivia;
        }

        /**
         * Certain items are trivial.
         *
         * @param mediaType media/MIME type
         * @return true if object type should be filtered
         */
        public boolean filterOutTrivialObjects(String mediaType) {
            if (filterableMeta.contains(mediaType)) {
                return true;
            }
            if (filterOut) {
                if ("image/png".equalsIgnoreCase(mediaType)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * EmbeddedResourceHandler interface;  listen for objects and handle them as needed.
         */
        @Override
        public void handle(String filename, MediaType mediaType, InputStream stream) {
            Metadata md = new Metadata();
            ++objectCount;
            String ext = "dat";

            if (filterOutTrivialObjects(mediaType.toString())) {
                log.debug("Filtering out object " + mediaType);
                return;
            }
            MimeType mimeType = null;
            try {
                mimeType = TikaConfig.getDefaultConfig().getMimeRepository()
                        .getRegisteredMimeType(mediaType.toString());
                ext = mimeType.getExtension();

                log.debug("Embedded object type={}", mimeType);

                if (StringUtils.isBlank(ext)) {
                    ext = "dat";
                } else {
                    ext = ext.replace(".", "");
                }
            } catch (MimeTypeException e1) {
                log.error("Tika could not find a file type for " + mediaType, e1);
            }

            boolean has_fname = true;
            if (filename == null) {
                filename = String.format("%s,Part%d.%s", parent.basename, objectCount, ext);
                has_fname = false;
            } else if (filename.length() < 3) {
                filename = String.format("%s,Part_%s_%d.%s", parent.basename, filename,
                        objectCount, ext);
            }

            if (filename.contains("/")) {
                filename = filename.replace("/", "_");
            }

            log.debug("Embbedded object file={} has filename? {}", filename, has_fname);

            md.add(Metadata.RESOURCE_NAME_KEY, filename);

            Content child = new Content();
            child.id = filename;
            child.meta.setProperty(ConvertedDocument.CHILD_ENTRY_KEY, filename);
            if (mimeType != null) {
                child.mimeType = mimeType.toString();
            }
            // NOTE: this is redundant here; as we just created tika Metadata() object ourselves.
            child.tikaMetadata = md;
            child.tikaMediatype = mediaType;

            try {
                child.content = IOUtils.toByteArray(stream);
            } catch (IOException e1) {
                log.error("Embedded object IO error", e1);
            }

            if (child.content.length > 0) {
                parent.addRawChild(child);
            }
        }
    }
}
