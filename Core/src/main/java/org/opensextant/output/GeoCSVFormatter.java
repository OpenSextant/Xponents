/*
 *
 * Copyright 2015 The MITRE Corporation.
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
 */
package org.opensextant.output;

import java.io.File;
import java.io.FileOutputStream;

import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.processing.ProcessingException;

/**
 * A results formatter for shapefile output.
 *
 * @author Rich Markeloff, MITRE Corp. Initial version created on Jan 6, 2012
 *         history
 *         -------
 *         - Changed this variety of CSV to be Geo-specific; Other non-geo data
 *         could be
 *         written to CSV easily.
 */
public final class GeoCSVFormatter extends GISDataFormatter {

    /**
     * @throws ProcessingException
     */
    public GeoCSVFormatter() throws ProcessingException {
        super();
        this.outputExtension = ".csv";
        this.doc_type = DocumentType.CSV;
        this.outputType = "CSV";
        this.includeOffsets = true;
        this.includeCoordinate = true;
        this.allowNonGeo = false;
    }

    /**
     * Create the output stream appropriate for the output type.
     *
     * @throws Exception on err
     */
    @Override
    protected void createOutputStreams() throws Exception {

        File csv = new File(getOutputFilepath());

        checkOverwrite(csv);

        FileOutputStream fos = new FileOutputStream(csv);
        this.os = GISFactory.getOutputStream(DocumentType.CSV, fos);
    }
}
