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
package org.opensextant.processing;

import org.opensextant.data.DocInput;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.ExtractionResult;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.output.ResultsFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A Group of Xponent Extractors. An Extractor has a simple interface:
 *
 * <pre>
 * +configure() + extract()
 * </pre>
 * <p>
 * Configure any Extractor; add it to the stack here;
 * Once you have added Extractors to your XtractorGroup, call XtractorGroup.setup()
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
    protected List<Extractor> extractors = new ArrayList<>();
    /**
     * API: child implementations have access to the core list of extractors.
     */
    protected List<ResultsFormatter> formatters = new ArrayList<>();
    /**
     * API: child implementations should recreate their own logger.
     */
    protected Logger log = LoggerFactory.getLogger(getClass());
    /**
     * API: child implementations have access to accumulated errors; reset()
     * clears errors and other state.
     */
    protected List<String> currErrors = new ArrayList<>();

    public void addExtractor(Extractor xprocessor) {
        extractors.add(xprocessor);
    }

    public void addFormatter(ResultsFormatter formatter) {
        formatters.add(formatter);
    }

    /**
     * Process one input. If you have no need for formatting output at this time
     * use this. If you have complext ExtractionResults where you want to add
     * meta attributes, then you would use this approach
     */
    public List<TextMatch> process(TextInput input) {
        List<TextMatch> oneResultSet = new ArrayList<>();

        /**
         * Process all extraction and compile on a single list.
         */
        for (Extractor x : extractors) {
            try {
                List<TextMatch> results = x.extract(input);
                if (results != null) {
                    oneResultSet.addAll(results);
                }
            } catch (ExtractionException loopErr) {
                log.error("Extractor=" + x.getName() + "on Input=" + input.id, loopErr);
                currErrors.add("Extractor=" + x.getName() + " ERR=" + loopErr.getMessage());
            }
        }
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
     * succeed.
     * <p>
     * TODO: Processing/Formatting details would have to be retrieved
     * by calling some other method that is statefully tracking such things.
     *
     * @param input
     * @return status -1 failure, 0 nothing found, 1 found matches and
     * formatted; 2 found content but nothing formatted. them.
     */
    public int processAndFormat(TextInput input) {
        reset();

        ExtractionResult compilation = new ExtractionResult(input.id);

        if (input instanceof DocInput) {
            compilation.recordFile = ((DocInput) input).getFilepath();
            compilation.recordTextFile = ((DocInput) input).getTextpath();
        }
        compilation.matches = process(input);
        compilation.input = input;

        if (compilation.matches.isEmpty()) {
            return 0; // nothing found
        }

        int status = format(compilation);

        return status;
    }
}
