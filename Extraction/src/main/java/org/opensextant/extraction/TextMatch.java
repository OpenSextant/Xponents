/**
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
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.opensextant.extraction;

/**
 * A variation on TextEntity that also records pattern metadata
 *
 * @author ubaldino
 */
public class TextMatch extends TextEntity {

    /**
     * the ID of the pattern that extracted this
     */
    public String pattern_id = null;
    
    /** 
     * A short label or tag representing the matcher, extractor, tagger, etc. that produced this match.
     */
    public String producer = null;

    /** Type, as in Annotation type or code.
     */
    protected String type = "generic";

    public String getType(){
        return type;
    }
    
    /**
     * Allow matchers and taggers to set a type label, e.g., pattern family or other string.
     * @param t type
     */
    public void setType(String t){
        type = t;
    }

    /**
     *
     */
    public TextMatch() {
    }

    /**
     * @return string representation
     */
    @Override
    public String toString() {
        return text + " @(" + start + ":" + end + ") matched by " + this.pattern_id;
    }

    /**
     * @param m a text match to copy to this instance
     */
    public void copy(TextMatch m) {
        super.copy(m);

        this.pattern_id = m.pattern_id;
    }

    private boolean filtered_out_state = false;

    public boolean isFilteredOut() {
        return filtered_out_state;
    }

    public void setFilteredOut(boolean b) {
        filtered_out_state = b;
    }
}
