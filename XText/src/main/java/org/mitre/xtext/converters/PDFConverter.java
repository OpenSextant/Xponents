/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xtext.converters;

import java.io.IOException;
import java.io.StringWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.util.PDFTextStripper;
import org.mitre.xtext.ConvertedDocument;
import org.mitre.xtext.iConvert;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class PDFConverter implements iConvert {

    private PDFTextStripper stripper = null;

    /** Initialize a reusable PDF engine.
     */
    public PDFConverter() throws IOException {
        stripper = new PDFTextStripper();
    }
    
    @Override
    public synchronized ConvertedDocument convert(String data) throws IOException {
        throw new IOException("PDF conversion as text blob is not supported here.  Send a File obj");
    }
    
    /** Implementation is informed by PDFBox authors.
     */
    @Override
    public synchronized ConvertedDocument convert(java.io.File doc) throws IOException {

        /*
         * Licensed to the Apache Software Foundation (ASF) under one or more
         * contributor license agreements.  See the NOTICE file distributed with
         * this work for additional information regarding copyright ownership.
         * The ASF licenses this file to You under the Apache License, Version 2.0
         * (the "License"); you may not use this file except in compliance with
         * the License.  You may obtain a copy of the License at
         *
         *      http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         */


        /**
         * Adapted from LucenePDFDocument.java from PDFBox lucene project
         *
         * This class is used to create a document for the lucene search engine.
         * This should easily plug into the IndexHTML or IndexFiles that comes with
         * the lucene project. This class will populate the following fields.
         * <table> <tr> <th>Lucene Field Name</th> <th>Description</th> </tr> <tr>
         * <td>path</td> <td>File system path if loaded from a file</td> </tr> <tr>
         * <td>url</td> <td>URL to PDF document</td> </tr> <tr> <td>contents</td>
         * <td>Entire contents of PDF document, indexed but not stored</td> </tr>
         * <tr> <td>summary</td> <td>First 500 characters of content</td> </tr> <tr>
         * <td>modified</td> <td>The modified date/time according to the url or
         * path</td> </tr> <tr> <td>uid</td> <td>A unique identifier for the Lucene
         * document.</td> </tr> <tr> <td>CreationDate</td> <td>From PDF meta-data if
         * available</td> </tr> <tr> <td>Creator</td> <td>From PDF meta-data if
         * available</td> </tr> <tr> <td>Keywords</td> <td>From PDF meta-data if
         * available</td> </tr> <tr> <td>ModificationDate</td> <td>From PDF
         * meta-data if available</td> </tr> <tr> <td>Producer</td> <td>From PDF
         * meta-data if available</td> </tr> <tr> <td>Subject</td> <td>From PDF
         * meta-data if available</td> </tr> <tr> <td>Trapped</td> <td>From PDF
         * meta-data if available</td> </tr> <tr> <td>Encrypted</td> <td>From PDF
         * meta-data if available</td> </tr> </table>
         *
         * @author <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
         * @version $Revision: 1.23 $
         *
         * @throws IOException If there is an error parsing the document.
         */
        PDDocument pdfDocument = null;
        ConvertedDocument textdoc = new ConvertedDocument(doc);

        try {
            pdfDocument = PDDocument.load(doc);

            if (pdfDocument.isEncrypted()) {
                //Just try using the default password and move on
                /**
                 *
                 * Exception in thread "main" java.lang.NoClassDefFoundError:
                 * org/bouncycastle/jce/provider/BouncyCastleProvider at
                 * org.apache.pdfbox.pdmodel.PDDocument.openProtection(PDDocument.java:1090)
                 * at
                 * org.apache.pdfbox.pdmodel.PDDocument.decrypt(PDDocument.java:594)
                 *
                 * CRYPTO stuff -- load BouncyCastle crypto JAR files. try {
                 * pdfDocument.decrypt(""); } catch (CryptographyException e) {
                 * throw new IOException("Error decrypting document(" + pdf_file
                 * + "): " + e); } catch (InvalidPasswordException e) { //they
                 * didn't suppply a password and the default of "" was wrong.
                 * throw new IOException( "Error: The document(" + pdf_file + ")
                 * is encrypted "); } finally { if (pdfDocument != null) {
                 * pdfDocument.close();} }
                 */
                textdoc.addProperty("encrypted", "YES");
            } else {

                //create a writer where to append the text content.
                StringWriter writer = new StringWriter();
                stripper.resetEngine();
                stripper.writeText(pdfDocument, writer);

                PDDocumentInformation info = pdfDocument.getDocumentInformation();
                if (info != null) {
                    textdoc.addAuthor(info.getAuthor());
                    try {
                        textdoc.addCreateDate(info.getCreationDate());
                    } catch (IOException io) {
                        //ignore, bad date but continue with indexing
                    }
                    textdoc.addProperty("creator_tool", info.getCreator());
                    textdoc.addProperty("keywords", info.getKeywords());
                    /* try {
                     metadata.add("ModificationDate", info.getModificationDate());
                     } catch (IOException io) {
                     //ignore, bad date but continue with indexing
                     } */
                    //metadata.add("Producer", info.getProducer());
                    textdoc.addProperty("subject", info.getSubject());
                    String ttl = info.getTitle();
                    if (ttl == null || "untitled".equalsIgnoreCase(ttl)) {
                        ttl = textdoc.filename;
                    }
                    textdoc.addTitle(ttl);
                    // metadata.add("Trapped", info.getTrapped());

                    // TODO: Character set is what?
                    textdoc.setEncoding("UTF-8");
                }

                // Note: the buffer to string operation is costless;
                // the char array value of the writer buffer and the content string
                // is shared as long as the buffer content is not modified, which will
                // not occur here.
                textdoc.setPayload(writer.getBuffer().toString());
            }
            return textdoc;

        } finally {
            if (pdfDocument != null) {
                pdfDocument.close();
            }
        }
    }
}
