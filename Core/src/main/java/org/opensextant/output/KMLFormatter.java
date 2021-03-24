/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
 * A formatter for KML output that uses GISCore API. This KML interpretation is
 * limited. Preferably use a more modern KML lib.
 *
 * @author Rich Markeloff, MITRE Corp. Initial version created on Jan 6, 2012
 */
public final class KMLFormatter extends GISDataFormatter {

    /**
     * @throws ProcessingException
     */
    public KMLFormatter() throws ProcessingException {
        this.outputExtension = ".kmz";
        this.doc_type = DocumentType.KMZ;
        this.outputType = "KML";
        this.groupByDocument = true;
        this.includeCoordinate = true;
        this.useFileHyperlink = true;
    }

    /**
     * Create the output stream appropriate for the output type.
     *
     * @throws Exception on err
     */
    @Override
    protected void createOutputStreams() throws Exception {

        File kml = new File(getOutputFilepath());

        checkOverwrite(kml);

        FileOutputStream fos = new FileOutputStream(kml);
        this.os = GISFactory.getOutputStream(DocumentType.KMZ, fos);
    }
}
