/*
 * Copyright 2013 ubaldino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.Converter;

// TODO: Auto-generated Javadoc
/**
 * The Class ConverterAdapter.
 *
 * @author ubaldino
 */
public abstract class ConverterAdapter implements Converter {

    /**
     * Conversion implementation.
     *
     * @param in the in
     * @param doc the doc
     * @return the converted document
     * @throws IOException Signals that an I/O exception has occurred.
     */
    protected abstract ConvertedDocument conversionImplementation(InputStream in, File doc) throws IOException;

    /**
     * Not an iConvert interface, yet.
     * This would take great care in all implementations to ensure the converters do not rely on
     * the "File doc" argument.
     *
     * @param data stream
     * @return the converted document
     * @throws IOException on err
     */
    //@Override
    public ConvertedDocument convert(InputStream data) throws IOException {
        return conversionImplementation(data, null);
    }

    /**
     * Yield a ConvertedDocument with no File metadata. Underlying
     * implementation opens and closes a stream to read the string. Metadata is
     * derived solely from the text provided, e.g., length, conversion time,
     * encoding.
     *
     * @param data stream
     * @return the converted document
     * @throws IOException on err
     */
    @Override
    public ConvertedDocument convert(String data) throws IOException {
        return conversionImplementation(TikaInputStream.get(IOUtils.toInputStream(data)), null);
    }

    /**
     * Yield a ConvertedDocumented with all the file metadata and payload, to
     * the greatest degree possible. Underlying implementation opens and closes
     * a stream to read the file. In other words implementation of converters
     * should close the given input stream.
     *
     * @param doc  raw file
     * @return the converted document
     * @throws IOException on err
     */
    @Override
    public ConvertedDocument convert(java.io.File doc) throws IOException {
        InputStream input = TikaInputStream.get(doc.toURI().toURL());
        return conversionImplementation(input, doc);
    }
}
