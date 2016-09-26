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

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.io.IOUtils;

import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

/**
 * Text converter that detects original encoding to include pure ASCII, Latin1, UTF-8 etc
 * Whereas Tika does not make a not of pure-ASCII texts.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TextTranscodingConverter extends ConverterAdapter {

    private final static CharsetDetector chardet = new CharsetDetector();
    private final static int IGNORE_THRESHOLD_SIZE = 1024; // 1KB
    private final static int IGNORE_THRESHOLD_CONF = 65; // 0 to 100

    /**
     * A converter that tries to get a decent encoding ASCII, UTF-8 or other,
     * and then the buffer converted or not.
     *
     * IF ASCII OR UTF-8 accept file as is, do not convert, alter buffer...
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
                data = IOUtils.toByteArray(in);
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

        boolean is_ascii = TextUtils.isASCII(data);
        if (is_ascii) {
            textdoc.do_convert = false;
            textdoc.setEncoding("ASCII");
            textdoc.setText(new String(data));
        } else {
            chardet.setText(data);
            CharsetMatch cs = chardet.detect();
            if (ConvertedDocument.OUTPUT_ENCODING.equalsIgnoreCase(cs.getName())) {
                textdoc.do_convert = false;
            } else if (data.length < IGNORE_THRESHOLD_SIZE && cs.getConfidence() < IGNORE_THRESHOLD_CONF) {
                textdoc.do_convert = false;
            }
            textdoc.setEncoding(cs.getName());
            textdoc.setText(new String(data, cs.getName()));
        }

        return textdoc;
    }

    /**
     * If you have a buffer of text for a document and are unable to get a provided charset,
     * try this static method.  Better than nothing. This does not imply that the original document is a plain text doc.
     * It could be an object that was parsed adhoc.  We cannot make any assumption about
     * the state of the conversion.  This only sets String buffer and charset.
     *
     * @param doc the doc
     * @param data the byte data to test
     * @throws UnsupportedEncodingException on err
     */
    public static void setTextAndEncoding(ConvertedDocument doc, byte[] data) throws UnsupportedEncodingException {
        boolean is_ascii = TextUtils.isASCII(data);
        if (is_ascii) {
            doc.setEncoding("ASCII");
            doc.setText(new String(data));
            return;
        }

        chardet.setText(data);
        CharsetMatch cs = chardet.detect();

        doc.setEncoding(cs.getName());
        doc.setText(new String(data, cs.getName()));
    }
}
