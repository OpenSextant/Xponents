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
public class MACAddress extends PoliMatch {

    public MACAddress() {
        super();
        normal_case = PoliMatch.LOWER_CASE;
    }
    
    public void normalize(){
        super.normalize();
        this.textnorm = this.textnorm.replace('-', ':');        
    }

    /*
    public MACAddress(String m) {
        super(m);
        normal_case = PoliMatch.LOWER_CASE;
    }

    public MACAddress(java.util.Map<String, String> elements, String m) {
        this(m);
        this.normalize();
        this.textnorm = this.textnorm.replace('-', ':');
    }
    */
}
