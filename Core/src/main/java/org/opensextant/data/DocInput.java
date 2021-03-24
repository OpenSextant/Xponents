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
package org.opensextant.data;

/**
 * Use only for cases where you have document inputs instead of raw records.
 *
 * @author ubaldino
 */

public abstract class DocInput extends TextInput {

    public DocInput(String id, String buffer) {
        super(id, buffer);
    }

    /**
     * get the original document
     *
     * @return path to original
     */
    public abstract String getFilepath();

    /**
     * get the optional text version of the document;
     *
     * @return path to a text version or conversion of the original.
     */
    public abstract String getTextpath();

    /**
     * @return string buffer
     */
    public abstract String getText();

}
