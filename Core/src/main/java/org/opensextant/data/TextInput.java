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

import org.opensextant.util.TextUtils;

/**
 * TextInput is a unit of data -- a tuple that represents the text and its
 * language and an identifier
 * for downstream processing, export formatting, databasing results keyed by
 * text identifier, etc.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TextInput {
    public String buffer = null;
    public String id = null;
    public String langid = null;
    /**
     * writable flags that represent some basic assement of your input text. Default
     * is mixed case where text
     * is neither upper or lower.
     */
    public boolean isLower = false;
    public boolean isUpper = false;

    /**
     * Heuristic -- text is neither predominately lower case or upper case.
     * @return
     */
    public boolean isMixedCase(){return !isLower && !isUpper;}

    /**
     * A simple input.
     * If this input is to be used with the normal OpenSextant pipelines, the caller
     * must ensure the input text is UTF-8 encoded content.
     *
     * @param tid any identifier for the text buffer
     * @param buf any textual content; any format or encoding.
     */
    public TextInput(String tid, String buf) {
        this.id = tid;
        this.buffer = buf;
    }

    private final LanguageCharacterization langHueristics = new LanguageCharacterization();

    /**
     * Language Characterization holds various heuristics and metrics about the input text
     * lang ID, if lang is CJK, Arabic, other.
     *
     * NOTE -- API user can specify language Characterization or user can use LangDetect wrapper
     * to pre-process language ID.  That result can be applied to the characterization to influence processing.
     * @return
     */
    public LanguageCharacterization getCharacterization() {
        return langHueristics;
    }

    /**
     * With buffer and language ID set,... characterize:
     * upper case
     * lower case
     * language group -- cjk, arabic, generic.
     */
    public void characterize() {
        if (this.buffer != null) {
            int[] textMetrics = TextUtils.measureCase(this.buffer);
            this.isUpper = TextUtils.isUpperCaseDocument(textMetrics);
            this.isLower = TextUtils.isLowerCaseDocument(textMetrics);
        }
        if (this.langid != null) {
            langHueristics.characterized = true;
            langHueristics.hasCJK = TextUtils.isCJK(this.langid);
            langHueristics.hasMiddleEastern = TextUtils.isMiddleEastern(this.langid);
        }
    }
}
