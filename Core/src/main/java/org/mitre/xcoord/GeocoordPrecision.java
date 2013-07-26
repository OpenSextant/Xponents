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

/**
 *
 * @author ubaldino
 */
public class GeocoordPrecision {

    /**
     * +/- # of Meters of error
     */
    public double precision = 0.0;
    /**
     * # of M/S/s digits in a D:M:S string for a lat or lon
     */
    //public int precision_dms_digits = 0;
    /**
     * # of decimal places in D.ddd... string for a lat or lon
     */
    //public int precision_dec_digits = 0;
    public int digits = 0;

    /** Augment number of digits in precision -- choose the maximum amount 
     * 
     * if in coord (a,b) if a has more digits of precision than b, use a's precision.
     * This is really only a matter of typos, where typist may have added 4 digits instead of 5, for example.
     * @param d 
     */
    public void setDigits(int d) {
        if (d > digits) {
            digits = d;
        }
    }
}
