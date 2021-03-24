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
import java.util.zip.ZipOutputStream;

import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.processing.ProcessingException;

/**
 * A results formatter for shapefile output.
 *
 * @author Rich Markeloff, MITRE Corp. Initial version created on Jan 6, 2012
 */
public final class ShapefileFormatter extends GISDataFormatter {

    /**
     * @throws ProcessingException
     */
    public ShapefileFormatter() throws ProcessingException {
        this.outputExtension = "_shp";
        this.doc_type = DocumentType.Shapefile;
        this.outputType = "Shapefile";
    }

    /**
     * Create the output stream appropriate for the output type.
     *
     * @throws Exception on err
     */
    @Override
    protected void createOutputStreams() throws Exception {

        File shp = new File(getOutputFilepath());

        checkOverwrite(shp); // cleanly delete first.
        shp.mkdirs();          // now recreate.

        File _temp = createTempFolder(this.outputType);
        File zipfile = new File(_temp + File.separator + shp.getName() + ".zip");
        FileOutputStream fos = new FileOutputStream(zipfile);

        ZipOutputStream zos = new ZipOutputStream(fos);

        this.os = GISFactory.getOutputStream(DocumentType.Shapefile, zos, shp);
    }
}
