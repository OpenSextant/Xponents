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
package org.mitre.xtext.converters;

import org.xml.sax.ContentHandler;
import java.io.IOException;
import org.mitre.xtext.ConvertedDocument;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import java.io.*;
import org.mitre.opensextant.util.TextUtils;
import org.apache.tika.parser.html.BoilerpipeContentHandler;

/**
 * A Tika HTML parser that reduces large amounts of empty lines in converted
 * HTML text.
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class TikaHTMLConverter extends ConverterAdapter {

    public static final int MAX_HTML_FILE_SIZE = 0x80000; // 0.5 MB
    HtmlParser parser = new HtmlParser();
    private boolean scrub_article = false;

    /**
     * Initialize a reusable HTML parser.
     */
    public TikaHTMLConverter(boolean article_only) throws IOException {
        scrub_article = article_only;
    }

    /**
     * a barebones HTML parser.
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream input, java.io.File doc) throws java.io.IOException {
        Metadata metadata = new Metadata();

        // HTML Conversion here is simply not resetting its internal buffers
        // Its just accumulating and error out when it reaches MAX
        ContentHandler handler = new BodyContentHandler(MAX_HTML_FILE_SIZE);
        //ContentHandler article_handler = new BoilerpipeContentHandler(handler);

        BoilerpipeContentHandler scrubbingHandler = null;
        if (scrub_article) {
            scrubbingHandler = new BoilerpipeContentHandler(handler);
        }

        try {
            parser.parse(input, (scrub_article ? scrubbingHandler : handler), metadata, new ParseContext());
            input.close();
        } catch (Exception xerr) {
            input.close();
            throw new IOException("Unable to parse content", xerr);
        }
        ConvertedDocument textdoc = new ConvertedDocument(doc);

        textdoc.addTitle(metadata.get(Metadata.TITLE));

        String text = null;
        if (scrub_article) {
            text = scrubbingHandler.getTextDocument().getText(true, false);
        } else {
            text = handler.toString();
        }

        textdoc.setPayload(TextUtils.reduce_line_breaks(text));

        //-- Improve CHAR SET encoding answer.
        byte[] data = textdoc.payload.getBytes();
        if (StupidASCIIDetector.isASCII(data)) {
            textdoc.setEncoding("ASCII");
        } else {
            // Okay, okay... let Tika name whatever encoding it found or guessed at.
            textdoc.setEncoding(metadata.get(Metadata.CONTENT_ENCODING));
        }

        // Indicate if we tried to filter the article at all.
        // 
        textdoc.addProperty("filtered", scrub_article);
        textdoc.addProperty("converter", TikaHTMLConverter.class.getName());

        return textdoc;
    }
}
