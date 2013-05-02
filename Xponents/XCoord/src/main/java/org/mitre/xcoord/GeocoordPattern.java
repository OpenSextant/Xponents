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

 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xcoord;


import org.mitre.flexpat.RegexPattern;

/**
 * 
 * @author ubaldino
 */
public class GeocoordPattern extends RegexPattern {
    
    // Common Coordinate Enumeration (CCE) terminology
    /**
     * Only as defined in your configured patterns, e.g., "DM", "DMS";
     * However this set of values should align with XConstants enumerations.
     */
    public String cce_family  = null;
    /**
     * Only as defined in your configured patterns, e.g., the "01" in "DM-01"
     */
    public String cce_variant = null;
    
    /**
     * XConstants value for the family
     */
    public int cce_family_id = -1;
    ///public int line_number = -1; -- thought line number from config file would help.
    // but ID should suffice.
    
    /**
     * 
     * @param _fam
     * @param _variant
     * @param _description
     */
    public GeocoordPattern(String _fam, String _variant, String _description){
        super( _fam + "-" + _variant, _description);
        
        cce_family = _fam;
        cce_variant = _variant;        
        cce_family_id = XConstants.get_CCE_family(cce_family);
    }
        
}
