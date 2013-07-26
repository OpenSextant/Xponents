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

import java.io.IOException;
import org.mitre.xtext.ConvertedDocument;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.poi.util.IOUtils;
import org.mitre.opensextant.util.FileUtility;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class TextTranscodingConverter extends ConverterAdapter {

    private final CharsetDetector chardet = new CharsetDetector();
    private final static int IGNORE_THRESHOLD_SIZE = 1024; // 1KB
    private final static int IGNORE_THRESHOLD_CONF = 65;   // 0 to 100

    /**
     * A converter that tries to get a decent encoding ASCII, UTF-8 or other,
     * and then the payload converted or not.
     *
     * IF ASCII OR UTF-8 accept file as is, do not convert, alter payload...
     * ELSE file must be read in and converted.
     *
     * CAVEAT: If file is short and low-confidence for encoding detection ALSO
     * do not convert. Treat as a plain text file.
     */
    @Override
    protected ConvertedDocument conversionImplementation(java.io.InputStream in, java.io.File doc) throws IOException {

        ConvertedDocument textdoc = new ConvertedDocument(doc);

        byte[] data = null;

        if (in != null) {
            // Get byte data from input stream or file
            if (doc != null) {
                data = FileUtility.readBytesFrom(doc);
            } else {
                IOUtils.readFully(in, data);
            }
            in.close();
        }

        // Encoding heuristics here.....
        //
        // Objective:  mark small plain text payloads with unknown character set
        //             as not worthy of conversion.  Leave them as plain/text
        //             indeed they might even be straight Unicode
        // 
        // Test for ASCII only first, otherwise try to detect the best charset for the text 
        //
        textdoc.is_plaintext = true;

        boolean is_ascii = StupidASCIIDetector.isASCII(data);
        if (is_ascii) {
            textdoc.do_convert = false;
            textdoc.setEncoding("ASCII");
            textdoc.setPayload(new String(data));
        } else {
            chardet.setText(data);
            CharsetMatch cs = chardet.detect();
            if (ConvertedDocument.OUTPUT_ENCODING.equalsIgnoreCase(cs.getName())) {
                textdoc.do_convert = false;
            } else if (data.length < IGNORE_THRESHOLD_SIZE
                    && cs.getConfidence() < IGNORE_THRESHOLD_CONF) {
                textdoc.do_convert = false;
            }
            textdoc.setEncoding(cs.getName());
            textdoc.setPayload(new String(data, cs.getName()));
        }

        return textdoc;
    }

    /* Tika-based solution for reading plain text was not working out.
     * it called everything latin-1 even if it was just ascii....
     * no harm really, but also no insight into encoding dection
     * 
     import java.io.FileInputStream;
     import java.io.IOException;
     import java.io.InputStream;
     import java.io.BufferedInputStream;
     import org.apache.tika.parser.Parser;
     import org.apache.tika.parser.txt.TXTParser;
     import org.apache.tika.parser.ParseContext;
     import org.apache.tika.metadata.Metadata;
     import org.xml.sax.ContentHandler;
     //import org.apache.tika.sax.ToTextContentHandler;
     import org.apache.tika.sax.*;
     */
    //@Override
    /*
     public ConvertedDocument convertWithTika(java.io.File doc) throws IOException {
     //private Parser parser = new TXTParser();
     //private ParseContext ctx = new ParseContext();

     byte[] data = FileUtility.readBytesFrom(doc);
     cd.setText(data);
     CharsetMatch cs = cd.detect();

     //Metadata metadata = new Metadata();
     // Body handler does not introduce extra characters as did ToText...
     //
        
     ContentHandler tikasax = new BodyContentHandler(); // new ToTextContentHandler();
     try {
     parser.parse(data, tikasax, metadata, ctx);
     } catch (Exception xerr) {
     throw new IOException("Unable to parse content", xerr);
     }
     input.close();
        

     ConvertedDocument textdoc = new ConvertedDocument(doc);

     //textdoc.addTitle(metadata.get(Metadata.TITLE));
     //textdoc.setEncoding(metadata.get(Metadata.CONTENT_ENCODING));
     //textdoc.addDate(metadata.get(Metadata.CREATION_DATE));
     //textdoc.addAuthor(metadata.get(Metadata.AUTHOR));

     //textdoc.setPayload(TextUtils.reduce_line_breaks(tikasax.toString()));
     //textdoc.setPayload(tikasax.toString());

     return textdoc;
     }
     */
}
