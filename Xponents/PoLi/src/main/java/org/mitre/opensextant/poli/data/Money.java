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

package org.mitre.opensextant.poli.data;

import org.mitre.opensextant.poli.PoliMatch;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class Money extends PoliMatch {

    public Money() {
        super();
        normal_case = PoliMatch.UPPER_CASE;
    }

    public Money(String m) {
        super(m);
        normal_case = PoliMatch.UPPER_CASE;
    }

    public Money(java.util.Map<String, String> elements, String m) {
        this(m);
        this.match_groups = elements;
    }
    
    public float value = -1;
    public String currency = null;
     
    public void normalize() {
        super.normalize();

        /* retrieve fields from this.match_groups
         * create value and currency, and even a normalized text version of the amount
         * Consider LOCALE -- is it european or US?  Is the number separator "," or "."?
         * 
         * fields:
         *    currency_sym
         *    currency_nom
         *    currency_magnitude  -- if "mil", then you multiple the amt by 10^6, right?
         *    money_amount
         */
    }    
}
