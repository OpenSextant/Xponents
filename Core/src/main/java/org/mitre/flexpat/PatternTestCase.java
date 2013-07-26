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
package org.mitre.flexpat;

/**
 *
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
     * @param _id
     * @param _family
     * @param _text
     */
    public PatternTestCase(String _id, String _family, String _text){
    
        this.id = _id;
        this.family = _family;
        this.text = _text;        
    }
}
