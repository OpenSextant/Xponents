/**
 *
 * Copyright 2009-2013 The MITRE Corporation.
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
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.opensextant.xtext.converters;

import java.io.InputStream;

import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
//import org.xml.sax.ContentHandler;
import org.apache.tika.sax.BodyContentHandler;
//import org.apache.tika.sax.ToTextContentHandler;
import org.opensextant.util.TextUtils;

import java.io.IOException;

import org.opensextant.xtext.ConvertedDocument;

/**
 * Default conversion is almost a pass through from Tika's auto parser and BodyContentHandler.
 * Encoding, author, create date and title are saved to ConvertedDoc.  The text of the document
 * is stripped of extra blank lines.
 * 
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class DefaultConverter extends ConverterAdapter {

    /* 1 MB of text from a given document */
    public final static int MAX_TEXT_SIZE = 0x100000;
    private Detector detector = new DefaultDetector();
    private Parser parser = new AutoDetectParser(detector);
    private ParseContext ctx = new ParseContext();

    private int maxBuffer = MAX_TEXT_SIZE;

    public DefaultConverter() {
        ctx.set(Parser.class, parser);
    }

    public DefaultConverter(int sz) {
        this();
        maxBuffer = sz;
    }

    /**
     * Common implementation -- take an input stream and return a ConvertedDoc;
     * 
     * @param input
     * @param doc
     * @return
     * @throws IOException
     *             if underlying Tika parser/writer had an IO problem, an parser
     *             problem, or MAX_TEXT_SIZE is reached.
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream input, java.io.File doc) throws IOException {
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(maxBuffer);

        try {
            parser.parse(input, handler, metadata, ctx);
        } catch (Exception xerr) {
            throw new IOException("Unable to parse content", xerr);
        } finally {
            input.close();
        }
        ConvertedDocument textdoc = new ConvertedDocument(doc);

        textdoc.addTitle(metadata.get(Metadata.TITLE));
        textdoc.setEncoding(metadata.get(Metadata.CONTENT_ENCODING));
        textdoc.addCreateDate(metadata.get(Metadata.CREATION_DATE));
        textdoc.addAuthor(metadata.get(Metadata.AUTHOR));

        textdoc.setText(TextUtils.reduce_line_breaks(handler.toString()));

        return textdoc;
    }
}
