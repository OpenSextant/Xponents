/**
            Copyright 2013 The MITRE Corporation.

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

package org.mitre.opensextant.poli;

import org.mitre.flexpat.TextMatch;
import java.util.Map;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class PoliMatch extends TextMatch {

    public String textnorm = null;
    public static int UPPER_CASE = 1;
    public static int LOWER_CASE = 2;
    public static int FOUND_CASE = 0;
    public int normal_case = FOUND_CASE;
    protected Map<String,String> match_groups = null;

    /** */
    public PoliMatch() {
    }
    
    public PoliMatch(String t) {
        this.text = t;
    }
    public PoliMatch(Map<String,String> groups, String t) {
        this.text = t;
        this.match_groups = groups;
    }
    
    public void setGroups(Map<String,String> groups){
        this.match_groups = groups;
    }

    /** */
    public void normalize() {
        if (this.text == null) {
            return;
        }
        if (normal_case == FOUND_CASE) {
            this.textnorm = this.text;
        } else if (normal_case == UPPER_CASE) {
            this.textnorm = this.text.toUpperCase();
        } else if (normal_case == LOWER_CASE) {
            this.textnorm = this.text.toLowerCase();
        }
    }
}
