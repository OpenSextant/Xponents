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
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
package org.opensextant.extraction;

import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;

/**
 * For now, this interface is closer to an AbstractExtractor
 * where a clean interface might be
 * output = Extractor.extract(input)
 * This interface specifies more
 *
 * @author ubaldino
 */
public interface Extractor {

    /** optional constant - a universal doc ID holder */
    String NO_DOC_ID = "no-docid";

    String getName();

    /**
     * Configure an Extractor using defaults for that extractor.
     *
     * @throws ConfigException
     *                         the config exception
     */
    void configure() throws ConfigException;

    /**
     * Configure an Extractor using a config file named by a path.
     *
     * @param patfile
     *                configuration file path
     * @throws ConfigException
     *                         the config exception
     */
    void configure(String patfile) throws ConfigException;

    /**
     * Configure an Extractor using a config file named by a URL.
     *
     * @param patfile
     *                configuration URL
     * @throws ConfigException
     *                         the config exception
     */
    void configure(java.net.URL patfile) throws ConfigException;

    /**
     * Useuful for working with batches of inputs that have an innate row ID +
     * buffer pairing.
     *
     * @param input
     *              text input
     * @return the list of TextMatch
     * @throws ExtractionException
     *                             error if underlying extractor(s) fail
     */
    List<TextMatch> extract(TextInput input) throws ExtractionException;

    /**
     * Useful for working with text buffers adhoc. Fewer assumptions about input
     * data here.
     *
     * @param input
     *              text input, as a string
     * @return the list of TextMatch
     * @throws ExtractionException
     *                             error if underlying extractor(s) fail
     */
    List<TextMatch> extract(String input) throws ExtractionException;

    /**
     * Resource management. This cleanup routine usually in turn calls some
     * shutdown, disconnect, etc.
     */
    void cleanup();

}
