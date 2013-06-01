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

import java.io.InputStream;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.mitre.opensextant.util.TextUtils;
import java.io.IOException;
import org.mitre.xtext.ConvertedDocument;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class DefaultConverter extends ConverterAdapter {

    private Parser parser = new AutoDetectParser();
    private ParseContext ctx = new ParseContext();

    /**
     * Common implementation -- take an input stream and return a ConvertedDoc
     * @param input
     * @param doc
     * @return
     * @throws IOException
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream input, java.io.File doc) throws IOException {
        Metadata metadata = new Metadata();
        // Unfortunately due to some bugs in the HTML body content handler
        // I have become suspicious about how Tika SAX content handlers reset or not 
        // in error conditions.   HTML conversion appears to accumulate content
        ContentHandler tikasax = new ToTextContentHandler();

        try {
            parser.parse(input, tikasax, metadata, ctx);
            input.close();
        } catch (Exception xerr) {
            input.close();
            throw new IOException("Unable to parse content", xerr);
        }
        ConvertedDocument textdoc = new ConvertedDocument(doc);

        textdoc.addTitle(metadata.get(Metadata.TITLE));
        textdoc.setEncoding(metadata.get(Metadata.CONTENT_ENCODING));
        textdoc.addCreateDate(metadata.get(Metadata.CREATION_DATE));
        textdoc.addAuthor(metadata.get(Metadata.AUTHOR));

        textdoc.setPayload(TextUtils.reduce_line_breaks(tikasax.toString()));
        //textdoc.setPayload(tikasax.toString());

        return textdoc;
    }
}
