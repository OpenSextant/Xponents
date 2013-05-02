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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * MGRS Filters include ignoring these patterns:
 *
 * <ul> <li> 1234 </li> <li> 123456 </li> <li> 12345678 </li> <li> 1234567890
 * </li> <li> Recent calendar dates of the form ddMMMyyyy, "14DEC1990" (MGRS:
 * 14D EC 19 90 </li> <li> Recent calendar dates with time, ddMMHHmm,
 * "14DEC1200" Noon on 14DEC. </li> <li> </li>
 *
 * </ul>
 *
 * @author ubaldino
 */
public class MGRSFilter implements MatchFilter {

    /** DateFormat used to check for dates that look like MGRS i.e. ddMMMyyyy
     */
    public List<DateFormat> df = new ArrayList<DateFormat>();
    /**
     */
    public Date today = new Date();
    /**
     */
    public static int MAX_YEARS_AGO = 80;  // If valid date/year found -- what is worth filtering?
    /**
     */
    public Calendar cal = null;
    /**
     */
    public int CURRENT_YEAR = 0;
    public int CURRENT_YY = 0;
    /**
     */
    public Set<String> IGNORE_SEQ = new HashSet<>();

    /**
     *
     */
    public MGRSFilter() {
        DateFormat _df = new java.text.SimpleDateFormat("ddMMMyyyy");
        // turn off lenient date parsing
        _df.setLenient(false);
        df.add(_df);

        DateFormat _df2 = new java.text.SimpleDateFormat("dMMMyy");
        // turn off lenient date parsing
        _df2.setLenient(true);
        df.add(_df2);

        cal = Calendar.getInstance();
        cal.setTime(today);
        CURRENT_YEAR = cal.get(Calendar.YEAR);
        CURRENT_YY = CURRENT_YEAR - 2000;

        IGNORE_SEQ.add("1234");
        IGNORE_SEQ.add("123456");
        IGNORE_SEQ.add("12345678");
        IGNORE_SEQ.add("1234567890");
    }

    /**
     * pass a match
     *
     * @param m
     * @return
     */
    @Override
    public boolean pass(GeocoordMatch m) {
        return !stop(m);
    }

    /**
     * stop a match
     *
     * @param m
     * @return
     */
    @Override
    public boolean stop(GeocoordMatch m) {

        int len = m.coord_text.length();
        if (len < 6) {
            return true;
        }
        // IGNORE numeric sequences
        // 
        String mgrs_offsets = m.coord_text.substring(5);
        if (IGNORE_SEQ.contains(mgrs_offsets)) {
            return true;
        }

        String _text = m.getText();
        // IGNORE rates or ratios spelled out:
        //        # PER # 
        //  e.g., 4 PER 100 
        // 
        String[] found = _text.split(" ");
        if (found.length > 2) {
            if ("per".equalsIgnoreCase(found[1])) {
                return true;
            }
        }

        for (DateFormat format : df) {
            if (isValidDate(m.coord_text, len, format)) {
                return true;
            }
        }

        // Nothing found.
        return false;
    }

    /** 
     * TODO: exploit XTemp to make light work of date/time normalization
     * .... however this filtering is very specific.
     * 
     * @param txt
     * @param len
     * @param fmt
     * @return 
     */
    private boolean isValidDate(String txt, int len, DateFormat fmt) {
        try {
            String dt;

            if (len > 9) {
                dt = txt.substring(0, 9);
            } else {
                dt = txt;
            }
            Date D = fmt.parse(dt);

            // 30JAN2010   -- date 2 years ago, that is a valid MGRS pattern.
            // Filter out.
            cal.setTime(D);
            int yr = cal.get(Calendar.YEAR);

            // Reformat 2-digit year into current millenium.
            // And then try... 
            if (yr <= CURRENT_YY) {
                yr += 2000;
            } else if (yr <= 100) {
                yr += 1900;
            }

            if (CURRENT_YEAR - yr < MAX_YEARS_AGO) {
                // Looks like a valid, recent year
                return true;
            }


            String hh = txt.substring(5, 7);
            String mm = txt.substring(7, 9);

            int hours = Integer.parseInt(hh);
            int minutes = Integer.parseInt(mm);
            if (hours < 24 && minutes < 60) {
                // Looks like a valid HHMM time of day.
                return true;
            }

        } catch (Exception err) {
            // NOT a date.
        }
        return false;
    }
}
