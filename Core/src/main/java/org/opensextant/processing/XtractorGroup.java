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
package org.opensextant.processing;

import java.util.List;
import java.util.ArrayList;

import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extraction.TextInput;
import org.opensextant.extraction.DocInput;
import org.opensextant.output.ResultsFormatter;
import org.opensextant.processing.progress.ProgressListener;
import org.opensextant.processing.progress.ProgressMonitor;
import org.opensextant.processing.progress.ProgressMonitorBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Group of Xponent Extractors. An Extractor has a simple interface:
 * 
 * <pre>
 * +configure() + extract()
 * </pre>
 * 
 * Configure any Extractor; add it to the stack here;
 * 
 * Once you have added Extractors to your XtractorGroup, call
 * XtractorGroup.setup()
 * 
 * Since a single processor of several may throw an exception, while others
 * succeed, The API does not throw exceptions failing a document completely. If
 * you need access to exceptions thrown by each processor or formatter, then you
 * would adapt the XtractorGroup here, but re-implementing the internal loops.
 * 
 * @author ubaldino
 */
public class XtractorGroup {

    /**
     * API: child implementations have access to the core list of extractors.
     */
    protected List<Extractor> extractors = new ArrayList<Extractor>();
    /**
     * API: child implementations have access to the core list of extractors.
     */
    protected List<ResultsFormatter> formatters = new ArrayList<ResultsFormatter>();
    /**
     * API: child implementations should recreate their own logger.
     */
    protected Logger log = LoggerFactory.getLogger(getClass());
    /**
     * API: child implementations have access to accumulated errors; reset()
     * clears errors and other state.
     */
    protected List<String> currErrors = new ArrayList<String>();

    protected ProgressMonitor progressMonitor = new ProgressMonitorBase();

    /**
     */
    public XtractorGroup() {
    }

    public void addExtractor(Extractor xprocessor) {
        xprocessor.setProgressMonitor(progressMonitor);
        extractors.add(xprocessor);
    }

    public void addFormatter(ResultsFormatter formatter) {
        formatters.add(formatter);
    }

    public void addProgressListener(ProgressListener listener) {
        progressMonitor.addProgressListener(listener);
    }

    public void removeProgressListener(ProgressListener listener) {
        progressMonitor.removeProgressListener(listener);
    }

    /**
     * Process one input. If you have no need for formatting output at this time
     * use this. If you have complext ExtractionResults where you want to add
     * meta attributes, then you would use this approach
     */
    public List<TextMatch> process(TextInput input) {
        List<TextMatch> oneResultSet = new ArrayList<TextMatch>();
        progressMonitor.setNumberOfSteps(extractors.size());

        /**
         * Process all extraction and compile on a single list.
         */
        for (Extractor x : extractors) {
            try {
                List<TextMatch> results = x.extract(input);
                x.markComplete();
                if (results != null && !results.isEmpty()) {
                    oneResultSet.addAll(results);
                }
            } catch (ExtractionException loopErr) {
                log.error("Extractor=" + x.getName() + "on Input=" + input.id, loopErr);
                currErrors.add("Extractor=" + x.getName() + " ERR=" + loopErr.getMessage());
            }
        }
        progressMonitor.completeDocument();
        return oneResultSet;
    }

    /**
     * Format each result; Some formatters may pass on results For example,
     * Shapefile formatter accepts only Geocoding-capable TextMatch.
     */
    public int format(ExtractionResult compilation) {
        int status = 2;

        for (ResultsFormatter fmt : formatters) {
            try {
                fmt.formatResults(compilation);
                status = 1;
            } catch (ProcessingException fmtErr) {
                log.error("Formatter=" + fmt.getOutputType(), fmtErr);
                currErrors.add("Formatter=" + fmt.getOutputType() + " ERR=" + fmtErr.getMessage());
            }
        }

        return status;
    }

    /**
     * Use only if you intend to shutdown.
     */
    public void cleanupAll() {
        for (Extractor x : extractors) {
            x.cleanup();
        }
    }

    /**
     * DRAFT: still figuring out the rules for 'reset' between processing or
     * inputs.
     */
    public void reset() {
        currErrors.clear();
    }

    /**
     * Processes input content against all extractors and all formatters This
     * does not throw exceptions, as some processing may fail, while others
     * succeed. TODO: Processing/Formatting details would have to be retrieved
     * by calling some other method that is statefully tracking such things.
     * 
     * @param input
     * @return status -1 failure, 0 nothing found, 1 found matches and
     *         formatted; 2 found content but nothing formatted. them.
     */
    public int processAndFormat(TextInput input) {
        reset();

        ExtractionResult compilation = new ExtractionResult(input.id);

        if (input instanceof DocInput) {
            compilation.recordFile = ((DocInput) input).getFilepath();
            compilation.recordTextFile = ((DocInput) input).getTextpath();
        }
        compilation.matches = process(input);

        if (compilation.matches.isEmpty()) {
            return 0; // nothing found
        }

        int status = format(compilation);

        return status;
    }
}
