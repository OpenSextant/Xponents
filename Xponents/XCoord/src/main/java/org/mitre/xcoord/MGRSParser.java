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

import java.util.Map;
import org.opensextant.geodesy.*;
import org.mitre.opensextant.util.TextUtils;

/**
 *
 * @author ubaldino
 */
public class MGRSParser {

    /**
     *
     * @param text
     * @param elements
     * @return
     */
    public static MGRS[] parseMGRS(String rawtext, String _text, Map<String, String> elements) {
        // pad MGRS
        // remove whitespace
        // set MGRS
        // set lat, lon

        String text = null;
        if (_text == null) {
            text = TextUtils.delete_whitespace(rawtext);
        } else {
            text = _text;
        }

        // Filter out trivial DD DEG MM pattern.
        // This may not be an issue -- how prevalent is the DD DEG MM DMS pattern?
        // Trivial test: 44 DEG 34 is not an MGRS pattern.
        if (text.length() < 6) {
            // less than 6 chars long this is either a zone with no offset
            //  or some sort of false positive.  Pattern should not match this
            return null;
        }

        if (text.length() < 8) {
            String _test = text.substring(2, 5);

            if (_test.equalsIgnoreCase("DEG")) {
                return null;
            }
        }

        String gzd = elements.get("MGRSZone");
        if (gzd != null) {
            // GZD Rule:  00 not allowed in 5-digit GZD
            //             0 not allowed in 4-digit
            int num1 = parseInt(gzd.substring(0, 1));
            int num2 = parseInt(gzd.substring(0, 2));

            if (num2 == 0 | (num1 == 0 && gzd.length() == 2)) {
                return null;
            }

            if (num1 < 0) {
                // Pattern should have never matched.
                return null;
            }

            // GZD Rule numbered zones not greate than 60
            if (num2 > 60) {
                return null;
            }
        }

        //---------------------------------------|
        // 
        // MGRS precision is 1m.  Quad is 100,000m sq so resolution is 5 digits + 5 digits with optional whitespace
        // 99999n 99999e  -- in MGRS we never see "m" units or N/E denoted explicitly
        // Occassionally, newlines or whitespace are interspersed in offset
        // minimal:
        // dd
        // ddddd ddddd  with an additional one or two white spaces.   The offsets start and end with numbers. Only whitespace between is optional. 
        // ddddd dddddd  additional digit in Easting  -- trailing 6th digit is a typo; trim off
        // dddddd ddddd  additional digit in Northing -- trailing 6th digit is a typo; trim off
        // ddddddddddd   Typo introduces ambiguity -- only correct thing is to split on halfway point +/- 1 digit and emit two answers
        // dd\nddd ddddd  Newline early in offset
        //---------------------------------------|
        String ne = elements.get("Easting_Northing");
        int digits = TextUtils.count_digits(ne);
        boolean odd_len = ((digits & 0x0001) == 1);
        if (!odd_len) {
            //----------------------------
            // Completely normal MGRS with even number of digits.
            // 
            // By this point you should have passed in normalized coordinate text - no whitespace
            //----------------------------
            // 
            return new MGRS[]{new MGRS(text)};
        } else {
            //----------------------------
            // Slightly obscure case that is possibly a typo or Easting/Northing disturbed.
            // 
            // The following logic for parsing is predominantly related to managing typos and rare cases.
            // < 5% of the instances seen fall into this category.
            // 
            //----------------------------

            int space_count = TextUtils.count_ws(ne);
            String nenorm;
            String Q = elements.get("MGRSQuad");

            StringBuilder mgrs1 = null;

            if (space_count == 0) {
                nenorm = ne;

                // ddddddddd   odd number of digits, no spaces.  
                // answer 1:  dddd ddddd  ==> N=dddd0
                // answer 2:  ddddd dddd  ==> E=dddd0
                int midpoint = (int) (nenorm.length() / 2);
                mgrs1 = new StringBuilder(ne);
                mgrs1.insert(midpoint, "0");  // N=dddd0,  add 0
                mgrs1.insert(0, Q);
                mgrs1.insert(0, gzd);

                StringBuilder mgrs2 = new StringBuilder(ne);
                mgrs2.append("0");   // E=dddd0  add 0
                mgrs2.insert(0, Q);
                mgrs2.insert(0, gzd);

                return new MGRS[]{
                            new MGRS(mgrs1.toString()),
                            new MGRS(mgrs2.toString())
                        };

            }

            nenorm = TextUtils.squeeze_whitespace(ne);
            space_count = TextUtils.count_ws(nenorm);
            int ws_index = nenorm.indexOf(" ");
            int midpoint = (int) (nenorm.length() / 2);

            // Even Split -- meaning easting northing appear to be good. But one needs to be fixed.
            // boolean even_split = Math.abs( midpoint - ws_index ) <= 1;
            // Given one of
            // dddd ddddd
            // ddddd dddd
            // dd ddddddd
            // where whitespace is ' ' or '\n' or '\r', etc.


            // GIVEN: dddd ddddd
            if (space_count == 1 && (ws_index+1) == midpoint) {
                mgrs1 = new StringBuilder(nenorm);
                // ANSWER: dddd0 ddddd
                mgrs1.insert(ws_index, "0");
                mgrs1.insert(0, Q);
                mgrs1.insert(0, gzd);

                // Just one answer:
                
                return new MGRS[]{new MGRS(TextUtils.delete_whitespace(mgrs1.toString()))};
            }

            if (space_count == 1 && (ws_index == midpoint)) {

                mgrs1 = new StringBuilder(nenorm);
                // ANSWER: ddddd dddd0
                mgrs1.append("0");
                mgrs1.insert(0, Q);
                mgrs1.insert(0, gzd);

                return new MGRS[]{new MGRS(TextUtils.delete_whitespace(mgrs1.toString()))};
            }


            // Given 
            //   ddd dd d
            //   ddddd ddd dd  
            //   etc. 
            //   You have a bunch of MGRS digits broken up by whitespace.
            //   This is really obscure case where formatting or content conversion
            //      or word processing interferred with the MGRS text.
            //   
            //  This is < 0.1% of the cases
            // 
            nenorm = TextUtils.delete_whitespace(ne);
            // ddddddddd   odd number of digits, no spaces.  
            // answer 1:  dddd ddddd  ==> N=dddd0
            // answer 2:  ddddd dddd  ==> E=dddd0
            midpoint = (int) (nenorm.length() / 2);
            mgrs1 = new StringBuilder(nenorm);
            mgrs1.insert(midpoint, "0");  // N=dddd0,  add 0
            mgrs1.insert(0, Q);
            mgrs1.insert(0, gzd);

            StringBuilder mgrs2 = new StringBuilder(nenorm);
            mgrs2.append("0");   // E=dddd0  add 0
            mgrs2.insert(0, Q);
            mgrs2.insert(0, gzd);

            return new MGRS[]{
                        new MGRS(mgrs1.toString()),
                        new MGRS(mgrs2.toString())
                    };
        }
    }

    /**
     *
     * @param x
     * @return
     */
    protected static int parseInt(String x) {
        try {
            return Integer.parseInt(x);
        } catch (Exception e) {
            return -1;
        }
    }
}
