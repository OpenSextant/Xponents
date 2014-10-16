/**
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
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 *
 */
package org.opensextant.output;

import java.io.File;

import org.opensextant.ConfigException;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.events.ContainerEnd;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.data.Geocoding;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.processing.ProcessingException;

/**
 * This is the base class for classes that convert document annotations to
 * GISCore features. Subclasses differ chiefly by choice of text string for the
 * description field. For some types of documents (e.g., news articles) the
 * sentence containing the annotation is a good choice, but for other types
 * (e.g., spreadsheets) sentence splitting may not be successful and the line of
 * text containing the annotation is a better choice.
 * 
 * @author Rich Markeloff, MITRE Corp. Initial version created on Dec 20, 2011
 * 
 * @author Marc C. Ubaldino, MITRE Corp. Refactored, redesigned package, 2013.
 */
public abstract class GISDataFormatter extends AbstractFormatter {

    /**
     *
     */
    protected DocumentType doc_type = null;
    /**
     *
     */
    protected IGISOutputStream os = null;
    /**
     *
     */
    protected boolean groupByDocument = false;
    private int id = 0;
    protected GISDataModel gisDataModel;
    protected boolean allowNonGeo = false; /* For vanilla CSV, where match does not have lat/lon */

    /**
     *
     */
    public GISDataFormatter() {
        this.debug = log.isDebugEnabled();
    }

    public void setGisDataModel(GISDataModel gisDataModel) {
        this.gisDataModel = gisDataModel;
    }

    /**
     * Use Default GIS Data Model based on current state of formatter
     */
    public void setGisDataModel() {
        this.gisDataModel = new GISDataModel(getJobName(), includeOffsets, includeCoordinate);
    }

    /**
     * Start output.
     */
    @Override
    public void start(String containerName) throws ProcessingException {

        /**
         * apply default GIS data model
         */
        if (this.gisDataModel == null) {
            setGisDataModel();
        }

        try {
            createOutputStreams();
        } catch (Exception create_err) {
            throw new ProcessingException(create_err);
        }

        DocumentStart ds = new DocumentStart(doc_type);
        this.os.write(ds);
        this.os.write(gisDataModel.getSchema());

        ContainerStart containerStart = new ContainerStart();
        containerStart.setType("Folder");
        containerStart.setName(containerName);
        this.os.write(containerStart);
    }

    @Override
    public void finish() {
        if (this.os == null) {
            return;
        }

        ContainerEnd containerEnd = new ContainerEnd();
        this.os.write(containerEnd);

        try {
            closeOutputStreams();
        } catch (Exception closer) {
            log.error("ERROR finalizing data file ", closer);
        }
    }

    protected File createTempFolder(String key) {
        File tempDir = new File(this.outputParams.tempDir + File.separator + key + "_"
                + System.currentTimeMillis());
        tempDir.mkdirs();
        return tempDir;
    }

    /**
     * 
     * @throws Exception
     */
    @Override
    protected void closeOutputStreams() throws Exception {
        if (this.os != null) {
            this.os.close();
        }
    }

    /**
     * This helps you figure out what to put in the GIS products.
     */
    protected boolean filterOut(TextMatch geo) {

        if (geo.isFilteredOut()) {
            return true;
        }
        if (this.allowNonGeo) {
            return false;
        }
        if (geo instanceof Geocoding) {
            Geocoding geocoding = (Geocoding) geo;

            if (!outputParams.output_coordinates && geocoding.isCoordinate()) {
                return true;
            } else if (!outputParams.output_countries && geocoding.isCountry()) {
                return true;
            } else if (!outputParams.output_places && geocoding.isPlace()) {
                return true;
            }
        }

        return false;
    }

    /**
     * This allows you to add either output of a Corpus processor
     * (gate.Document, gate.Corpus) or to add a Geocoding Result directly
     * 
     */
    @Override
    public void writeGeocodingResult(ExtractionResult rowdata) {
        boolean error = false;

        log.debug("Adding data for File {} Count={}", rowdata.recordFile, rowdata.matches.size());

        for (TextMatch g : rowdata.matches) {

            if (filterOut(g)) {
                continue;
            }
            // Increment ID
            id++;

            log.debug("Add {}#{}", id, g);

            // Only TextMatches that implement the Geocoding interface are
            // allowed here:

            try {
                for (Feature row : gisDataModel.buildRows(id, (Geocoding) g, g, rowdata.attributes,
                        rowdata)) {
                    log.debug("FEATURE: {}", row);
                    this.os.write(row);
                }

            } catch (ConfigException fieldErr) {

                if (!error) {
                    log.error("OUTPUTTER, ERR=" + fieldErr);
                }
                error = true;
            }
        }
    }

    /**
     * @param fld
     */
    @Override
    public void addField(String fld) throws ConfigException {
        gisDataModel.addField(fld);
    }

    /**
     * 
     * @param fld
     */
    @Override
    public void removeField(String fld) throws ConfigException {
        gisDataModel.removeField(fld);
    }
}
