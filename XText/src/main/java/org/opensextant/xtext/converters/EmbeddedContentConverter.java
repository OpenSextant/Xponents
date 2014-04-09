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
import java.util.Set;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
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
        supportedTypes.add("xls");
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
     * @param fileext
     * @return
     */
    public static boolean isSupported(String fileext) {
        return supportedTypes.contains(fileext);
    }

    /**
     * Convert Embedded documents in the supported types to a folder of the embedded items.
     * Trivial embedded icons and other components will not be extracted
     * 
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc) throws IOException {

        ConvertedDocument compoundDoc = super.conversionImplementation(in, doc);
        if (!isSupported(compoundDoc.extension)) {
            return compoundDoc; // Not really compound by our standards here.
        }

        ParserContainerExtractor extractor = new ParserContainerExtractor();
        EmbeddedObjectExtractor objExtractor = new EmbeddedObjectExtractor(compoundDoc, false);

        TikaInputStream tikaStream = null;
        try {
            tikaStream = TikaInputStream.get(doc);
            extractor.extract(tikaStream, extractor, objExtractor);
        } catch (TikaException e) {
            throw new IOException("Stream parsing problem");
        } finally {
            tikaStream.close();
        }

        return compoundDoc;
    }

    private final static Set<String> filterableMeta = new HashSet<String>();
    static {
        filterableMeta.add("application/x-emf");
        filterableMeta.add("application/x-msmetafile");
        filterableMeta.add("image/png");
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

        EmbeddedObjectExtractor(ConvertedDocument par, boolean filterTrivia) throws IOException {
            parent = par;
        }

        /**
         * Certain items are trivial.
         * 
         * @param mediaType
         * @return
         */
        public boolean filterOutTrivialObjects(String mediaType) {
            if (filterableMeta.contains(mediaType)) {
                return true;
            }
            return false;
        }

        public void handle(String filename, MediaType mediaType, InputStream stream) {
            Metadata md = new Metadata();
            ++objectCount;
            String ext = "dat";

            if (filterOutTrivialObjects(mediaType.toString())) {
                log.info("Filtering out object " + mediaType);
                return;
            }

            try {
                MimeType mimeType = TikaConfig.getDefaultConfig().getMimeRepository()
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
                filename = String.format("%s,Part_%s_%d.%s", parent.basename, filename, objectCount, ext);
            }
            
            if (filename.contains("/")){
                filename = filename.replace("/", "_");
            }

            log.debug("Embbedded object file={} has filename? {}", filename, has_fname);

            md.add(Metadata.RESOURCE_NAME_KEY, filename);

            Content child = new Content();
            child.id = filename;
            child.meta.setProperty(ConvertedDocument.CHILD_ENTRY_KEY, filename);

            try {
                child.content = IOUtils.toByteArray(stream);
            } catch (IOException e1) {
                log.error("Embedded object IO error", e1);
            }

            parent.addRawChild(child);
        }
    }
}
