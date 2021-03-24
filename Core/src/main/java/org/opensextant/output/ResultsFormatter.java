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

import org.opensextant.ConfigException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.ProcessingException;

/**
 * Interface for classes that generate output from corpora that have been
 * processed by OpenSextant.
 *
 * @author Rich Markeloff, MITRE Corp.
 *         Initial version created on Jul 13, 2011
 */
public interface ResultsFormatter {

    /**
     * A more convenient way of passing in a list of parameters.
     */
    void setParameters(Parameters params);

    /**
     * @return name of job
     */
    String getJobName();

    /**
     * Set the path to the output directory.
     *
     * @param pathname
     */
    void setOutputDir(String pathname);

    /**
     * Set the name of the output file.
     *
     * @param filename
     */
    void setOutputFilename(String filename);

    /**
     * Get the type of output produced by this formatter.
     *
     * @return type of output
     */
    String getOutputType();

    /**
     * Get the path to the output file.
     *
     * @return file path of output
     */
    String getOutputFilepath();

    /**
     * Formats the results obtained from processing a corpus through OpenSextant.
     * Returns a string to display to the user. Typically this will be HTML to be
     * shown in a browser.
     *
     * @param result
     * @return A message for the user
     * @throws ProcessingException formatting error
     */
    String formatResults(ExtractionResult result) throws ProcessingException;

    void start(String nm) throws ProcessingException;

    void finish();

    /**
     * @param f field
     * @throws ConfigException if not consistent with schema
     */
    void addField(String f) throws ConfigException;

    /**
     * @param f field
     * @throws ConfigException if not consistent with schema
     */
    void removeField(String f) throws ConfigException;
}
