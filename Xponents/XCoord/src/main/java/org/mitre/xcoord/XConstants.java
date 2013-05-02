/**
 *
 *  Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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

import java.util.*;

/**
 *
 * @author ubaldino
 */
public final class XConstants {
    
    /**
     * 
     */
    public final static int UNK_PATTERN=0;
    /**
     * 
     */
    public final static int DD_PATTERN=1;
    /**
     * 
     */
    public final static int DM_PATTERN=2;
    /**
     * 
     */
    public final static int DMS_PATTERN=3;
    /**
     * 
     */
    public final static int MGRS_PATTERN=4;
    /**
     * 
     */
    public final static int UTM_PATTERN=5;
    /**
     * 
     */
    public final static int ALL_PATTERNS=-1;
    
    
    /**
     *
     */
    public final static int UNFILTERED = -1;
    /**
     *
     */
    public final static int FILTERED_OUT = 0;
    /**
     *
     */
    public final static int FILTERED_IN = 1;
    
    
    /** RUNTIME FLAGS: filter DMS coordinates */
    public final static int DMS_FILTERS_ON =  0x01;
    /** RUNTIME FLAGS: filter Decimal Degree coordinates -- primarily for bare floating point numbers w/out hemisphere or other symbols
     */
    public final static int DD_FILTERS_ON  =  0x02;
    /** RUNTIME FLAGS: filter MGRS coordinates -- date patterns and NUM PER NUM  patterns e.g., the ratio "4 per 4000" is not MGRS*/
    public final static int MGRS_FILTERS_ON = 0x04;
    /** RUNTIME FLAGS: filter all coordinate patterns that have filters */
    public final static int FLAG_ALL_FILTERS = (MGRS_FILTERS_ON|DD_FILTERS_ON|DMS_FILTERS_ON);
    
    /** RUNTIME FLAGS: extract context or not */
    public final static int FLAG_EXTRACT_CONTEXT = 0x10;
    
    
    /**
     * 
     */
    protected final static Map<String,Integer> familyInt = new HashMap<>();
    /**
     * 
     */
    protected final static Map<Integer,String> familyLabel = new HashMap<>();
    
    static {
        
        familyLabel.put(UNK_PATTERN, "UNK");
        familyLabel.put(DD_PATTERN, "DD");
        familyLabel.put(DM_PATTERN, "DM");
        familyLabel.put(DMS_PATTERN, "DMS");
        familyLabel.put(MGRS_PATTERN, "MGRS");
        familyLabel.put(UTM_PATTERN, "UTM");

        familyInt.put("UNK", UNK_PATTERN);
        familyInt.put("DD", DD_PATTERN);
        familyInt.put("DM", DM_PATTERN);
        familyInt.put("DMS", DMS_PATTERN);
        familyInt.put("MGRS", MGRS_PATTERN);
        familyInt.put("UTM", UTM_PATTERN);
        
    }
    
    
 
    /**
     * Get the CCE family for the given string/key
     * @param nm
     * @return
     */
    public static int get_CCE_family(String nm){
        
        Integer id = familyInt.get(nm);
        if (id!=null){
            return id.intValue();
        }
        
        return UNK_PATTERN;
    }
    
    /**
     * Get the CCE family for the given XConstants enum id
     * @param id
     * @return
     */
    public static String get_CCE_family(int id){
        
        String nm = familyLabel.get(id);
        if (nm!=null){
            return nm;
        }
        
        return "UNK";
    }

}
