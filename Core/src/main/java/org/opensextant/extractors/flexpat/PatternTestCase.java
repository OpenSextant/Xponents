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
package org.opensextant.extractors.flexpat;

import org.opensextant.extraction.TextMatch;

/**
 * @author ubaldino
 */
public class PatternTestCase {

    /**
     *
     */
    public String id = null;
    /**
     *
     */
    public String text = null;
    /**
     *
     */
    public String family = null;
    /**
     *
     */
    public int family_id = -1;
    /**
     *
     */
    public boolean true_positive = true;

    /**
    *
    */
    public TextMatch match = null;
    /**
    *
    */
    public String remarks = null;

    /**
     * @param _id     pattern identifier
     * @param _family family of pattern
     * @param _text   text for test case
     */
    public PatternTestCase(String _id, String _family, String _text) {

        this.id = _id;
        this.family = _family;
        this.text = _text;
    }

    /**
     * Set the test remarks and IFF the word "fail" is in the comment, the test is
     * indicated as a true negative.
     *
     * @param rmks
     */
    public void setRemarks(String rmks) {
        remarks = rmks;
        if (rmks != null && rmks.toLowerCase().contains("fail")) {
            this.true_positive = false;
        }
    }
}
