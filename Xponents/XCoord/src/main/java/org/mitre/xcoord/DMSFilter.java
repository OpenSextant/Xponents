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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
//import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * DMS Filters include ignoring these patterns: dd-dd-dd HH:MM:ss (where
 * dd-dd-dd HH-MM-ss would be a valid coordinate as the field separators for
 * lat/lon are the same).
 *
 * Assumption: Date fields are separated by / and -; Time fields are separated
 * by : So if you have a lat/lon with a [date] [time] pattern it is most likely
 * not a [lat] [lon] pattern.
 *
 * As of 2012 Fall -- DMS-02 is very open ended pattern and matches parts of
 * dates
 *
 * @author ubaldino
 */
public class DMSFilter implements MatchFilter {

    /**
     *
     */
    public final static String[] general_formats = {
        "yy-dd-mm HH:MM:ss",
        "mm-dd-yy HH:MM:ss"
    };
    /**
     *
     */
    /* UNUSED year filter stuff.
     public Date today = new Date();
     public final static int MAX_YEARS_AGO = 50;  // If valid date/year found -- what is worth filtering?
     public Calendar cal = Calendar.getInstance();
     public static int CURRENT_YEAR = 0;
     */
    /**
     *
     */
    public List<DateFormat> general_dates = new ArrayList<DateFormat>();

    /**
     *
     */
    public DMSFilter() {

        //cal.setTime(today);
        //CURRENT_YEAR = cal.get(Calendar.YEAR);

        for (String fmt : general_formats) {
            DateFormat df = new SimpleDateFormat(fmt);
            // turn off lenient date parsing
            df.setLenient(false);
            general_dates.add(df);
        }
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
     * stop a match if it is a bad DMS pattern, e.g., date or date/time.
     *
     * @param m
     * @return
     */
    @Override
    public boolean stop(GeocoordMatch m) {
        
        // We can pass patterns matching raw text that do not start
        // with numbers.  The date formats filtered here are strictly numeric dates+time
        // If I see N42-44 or +42-44 etc... for example, then we can exit now. 
        //
        String _text = m.getText();
        char ch = _text.charAt(0);
        if (!Character.isDigit(ch)){
            return false;
        }
        
        for (DateFormat df : general_dates) {
            try {
                Date D = df.parse(_text);
                if (D != null) {
                    return true;
                }

            } catch (Exception ignoreErr) {
            }
        }

        return false;
    }
}
