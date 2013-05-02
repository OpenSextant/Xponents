/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
package org.mitre.xtemporal;

import java.util.*;


/**
 *
 * @author ubaldino
 */
public class XTConstants {
    
    /**
     *
     */
    public final static int MDY_FAMILY  = 0;
    /**
     *
     */
    public final static int DATETIME_FAMILY  = 1;
    /**
     *
     */
    public final static int UNK_PATTERN = -2;
    
    /**
     *
     */
    public final static int ALL_PATTERNS=-1;
    
    /**
     *
     */
    protected final static Map<String,Integer> familyInt = new HashMap<String,Integer>();
    /**
     *
     */
    protected final static Map<Integer,String> familyLabel = new HashMap<Integer,String>();
    
    static {
        
        familyLabel.put(UNK_PATTERN, "UNK");
        familyLabel.put(MDY_FAMILY, "MDY");
        familyLabel.put(DATETIME_FAMILY, "DTM");

        familyInt.put("UNK", UNK_PATTERN);
        familyInt.put("DTM", DATETIME_FAMILY);
        familyInt.put("MDY", MDY_FAMILY);        
    }
    
    
    /**
     *
     * @param key
     * @return
     */
    public static int getPatternFamily(String key){
        Integer i = familyInt.get(key);
        if (i != null){
            return i.intValue();
        }
        return UNK_PATTERN;
    }
    
    
    /**
     *
     * @param id
     * @return
     */
    public static String getPatternFamily(int id){
        String key = familyLabel.get(id);
        if (key != null){
            return key;
        }
        return "UNK";        
    }
    
}
