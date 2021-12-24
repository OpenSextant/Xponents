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
import org.opensextant.giscore.output.gdb.FileGdbOutputStream;

/**
 * A results formatter for FileGDB output.
 *
 * @author Rich Markeloff, MITRE Corp. Initial version created on Jan 6, 2012
 */
public class GDBFormatter extends GISDataFormatter {

    /**
     *
     */
    public GDBFormatter() {
        this.outputExtension = ".gdb";
        this.doc_type = DocumentType.FileGDB;
        this.outputType = "FileGDB";
    }

    /**
     * Create the output stream appropriate for the output type.
     *
     * @throws Exception on err
     */
    @Override
    protected void createOutputStreams() throws Exception {

        File gdb = new File(getOutputFilepath());

        checkOverwrite(gdb);
        // gdb.mkdirs();

        File _temp = createTempFolder(this.outputType);
        File zipfile = new File(_temp + File.separator + gdb.getName() + ".zip");
        Object[] args = new Object[1];
        args[0] = gdb;
        // Underlying IO stream remains open until finish.
        this.os = new FileGdbOutputStream(new ZipOutputStream(new FileOutputStream(zipfile)), args);
    }
}
