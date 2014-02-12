/**
 Copyright 2009-2013 The MITRE Corporation.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 ** **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government
 * under Contract No. W15P7T-12-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
 **************************************************   */
package org.opensextant.extraction;

import java.util.List;

import org.opensextant.data.TextInput;
import org.opensextant.processing.progress.ProgressMonitor;

/**
 * For now, this interface is closer to an AbstractExtractor
 * where a clean interface might be
 *   output = Extractor.extract(input)
 * This interface specifies more
 *
 * @author ubaldino
 */
public interface Extractor {

    /** optional constant - a universal doc ID holder*/
    public final static String NO_DOC_ID = "no-docid";

    public String getName();

    /** Configure an Extractor using defaults for that extractor
     */
    public void configure() throws ConfigException;

    /** Configure an Extractor using a config file named by a path
     * @param patfile configuration file path
     */
    public void configure(String patfile) throws ConfigException;

    /** Configure an Extractor using a config file named by a URL
     * @param patfile configuration URL
     */
    public void configure(java.net.URL patfile) throws ConfigException;

    /** Useuful for working with batches of inputs that have an innate row ID + buffer pairing */
    public List<TextMatch> extract(TextInput input) throws ExtractionException;
    /** Useful for working with text buffers adhoc. Fewer assumptions about input data here.*/
    public List<TextMatch> extract(String input) throws ExtractionException;

    public void setProgressMonitor(ProgressMonitor progressMonitor);
    public void updateProgress(double progress);
    public void markComplete();
    
    public void cleanup();

}
