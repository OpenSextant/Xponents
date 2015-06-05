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

import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;

import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.parser.html.BoilerpipeContentHandler;

import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.util.TextUtils;

/**
 * A Tika HTML parser that reduces large amounts of empty lines in converted
 * HTML text.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TikaHTMLConverter extends ConverterAdapter {

    public static final int MAX_HTML_FILE_SIZE = 0x80000; // 0.5 MB
    HtmlParser parser = new HtmlParser();
    private boolean scrubHTMLArticle = false;
    private int maxHTMLDocumentSize = MAX_HTML_FILE_SIZE;

    /**
     * Initialize a reusable HTML parser.
     */
    public TikaHTMLConverter(boolean article_only) throws IOException {
        scrubHTMLArticle = article_only;
    }

    public TikaHTMLConverter(boolean article_only, int docSize) throws IOException {
        this(article_only);
        maxHTMLDocumentSize = docSize;
    }

    /**
     * a barebones HTML parser.
     *
     * <pre>
     * TODO: mis-encoded HTML entities are not decoded
     * properly. E.g., finding "&#8211;" (82xx range is dashes, quotes) for
     * example, does not decode correctly unless the page encoding is declared as UTF-8.
     * </pre>
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream input, File doc)
            throws IOException {
        Metadata metadata = new Metadata();

        // HTML Conversion here is simply not resetting its internal buffers
        // Its just accumulating and error out when it reaches MAX
        ContentHandler handler = new BodyContentHandler(maxHTMLDocumentSize);

        BoilerpipeContentHandler scrubbingHandler = null;
        if (scrubHTMLArticle) {
            scrubbingHandler = new BoilerpipeContentHandler(handler);
        }

        try {
            parser.parse(input, (scrubHTMLArticle ? scrubbingHandler : handler), metadata,
                    new ParseContext());
        } catch (Exception xerr) {
            throw new IOException("Unable to parse content", xerr);
        } finally {
            input.close();
        }
        ConvertedDocument textdoc = new ConvertedDocument(doc);

        textdoc.addTitle(metadata.get(TikaCoreProperties.TITLE));

        String text = null;
        if (scrubHTMLArticle) {
            text = scrubbingHandler.getTextDocument().getText(true, false);
        } else {
            text = handler.toString();
        }

        textdoc.setText(TextUtils.reduce_line_breaks(text));

        // -- Improve CHAR SET encoding answer.
        byte[] data = textdoc.buffer.getBytes();
        if (TextUtils.isASCII(data)) {
            textdoc.setEncoding("ASCII");
        } else {
            // Okay, okay... let Tika name whatever encoding it found or guessed
            // at.
            textdoc.setEncoding(metadata.get(Metadata.CONTENT_ENCODING));
        }

        // Indicate if we tried to filter the article at all.
        //
        textdoc.addProperty("filtered", scrubHTMLArticle);
        textdoc.addProperty("converter", TikaHTMLConverter.class.getName());

        return textdoc;
    }
}
