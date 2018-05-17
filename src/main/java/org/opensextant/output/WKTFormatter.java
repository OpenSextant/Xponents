/**
 * Copyright 2012-2015 The MITRE Corporation.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.


 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
**/
package org.opensextant.output;

import java.io.File;
import java.io.FileOutputStream;

import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.processing.ProcessingException;

/**
 * A formatter for WKT outut.
 *
 * @author Rich Markeloff, MITRE Corp. Initial version created on Feb 7, 2012
 */
public final class WKTFormatter extends GISDataFormatter {

    /**
     *
     * @throws ProcessingException
     */
    public WKTFormatter() throws ProcessingException {
        this.outputExtension = ".wkt";
        this.doc_type = DocumentType.WKT;
        this.outputType = "WKT";
    }

    /**
     * Create the output stream appropriate for the output type.
     * @throws Exception on err
     */
    @Override
    protected void createOutputStreams() throws Exception {

        File wkt = new File(getOutputFilepath());

        checkOverwrite(wkt);

        FileOutputStream fos = new FileOutputStream(wkt);
        this.os = GISFactory.getOutputStream(DocumentType.WKT, fos);
    }
}
