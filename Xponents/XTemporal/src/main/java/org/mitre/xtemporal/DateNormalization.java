/**
 *
 * Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xtemporal;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormat;

/**
 *
 * @author ubaldino
 */
public class DateNormalization {

    final static DateTime cal = DateTime.now(DateTimeZone.UTC);
    final static int YEAR = cal.getYear();
    final static int MILLENIUM = 2000;
    final static int CURRENT_YY = YEAR - MILLENIUM;
    ;
    final static int FUTURE_YY_THRESHOLD = CURRENT_YY + 2;
    final static int MAXIMUM_YEAR = 2020;
    // Use of year "15" would imply 1915 in this case.
    // Adjust 2-digit year threshold as needed.
    // Java default is 80/20.  2000 - 2032 is the assumed year for "00" through "32"
    // "33" is 1933
    //
    /**
     *
     */
    public static int INVALID_DATE = -1;
    /**
     *
     */
    public static int NO_YEAR = -1;
    /**
     *
     */
    public static int NO_MONTH = -1;
    /**
     *
     */
    public static int NO_DAY = -1;
    final static DateTimeFormatter fmt_month = DateTimeFormat.forPattern("MMM").withZoneUTC();
    final static DateTimeFormatter fmt_mm = DateTimeFormat.forPattern("MM").withZoneUTC();
    final static DateTimeFormatter fmt_ydm = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();

    /**
     * @param d
     * @return
     */
    public static String format_date(Date d) {
        return fmt_ydm.print(d.getTime());
    }

    /* 
     *  
     * #DEFINE MON_ABBREV  JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEPT?|OCT|NOV|DEC
    
     #DEFINE MON_NAME   [A-Z]{3}\w{0,8}
    
     #DEFINE YEAR         [12]\d{3}
     #DEFINE YY           \d\d
     #DEFINE YEARYY       \d{2,4}
     #DEFINE MM           [01]\d 
     #DEFINE DD           [0-3]\d
     #DEFINE SHORT_TZ     [A-Z]
    
     #DEFINE hh    [0-2]\d
     #DEFINE mm    [0-5]\d
     #DEFINE DOM         [0-3]?\d
     #DEFINE MONTH       [01]?\d
     #DEFINE LONG_TZ     [A-Z]{3,5}
     */
    /**
     * For now this reports only DATEs -- no TIME parsing is handled. DateFormat
     * would work,,... except the wild variation of matching formats would be
     * difficult to handle.
     *
     * @param elements
     * @return void
     * @throws java.text.ParseException
     */
    public static void normalize_date(java.util.Map<String, String> elements, DateMatch dt) throws
            java.text.ParseException {

        // Parse years.
        int year = normalize_year(elements);
        if (year == INVALID_DATE) {
            return;
        }

        if (year > MAXIMUM_YEAR) {
            // HHMM can look like a year, e.g., 2100h or 2300 PM
            return;
        }
        dt.resolution = DateMatch.TimeResolution.YEAR;

        int month = normalize_month(elements);
        if (month == INVALID_DATE) {
            month = normalize_month_name(elements);
        }

        if (month == INVALID_DATE) {
            return;
        }

        DateTime _cal = new DateTime(year, month, 1, 0, 0, DateTimeZone.UTC);

        dt.resolution = DateMatch.TimeResolution.MONTH;

        int dom = normalize_day(elements);
        // If you got this far, then assume Day of Month is 1 (first of the month)
        if (dom == INVALID_DATE) {
            // No date found, resolution is month
            dom = 1;
        } else if (dom == 0) {
            return;
        } else {
            dt.resolution = DateMatch.TimeResolution.DAY;
        }


        // Normalize Time fields found, H, M, s.SSS, etc.
        // 
        DateTime _cal2 = _cal.withDayOfMonth(dom);

        dt.datenorm = new Date(_cal2.getMillis());
    }

    /**
     *
     * @param elements
     * @return
     */
    public static int normalize_year(java.util.Map<String, String> elements) {

        // YEAR   yyyy
        // YY       yy 
        // YEARYY   yy or yyyy  
        String _YEAR = elements.get("YEAR");

        if (_YEAR != null) {
            //year = yy;            
            return getIntValue(_YEAR);
        }


        int year = INVALID_DATE;

        String _YY = elements.get("YY");
        if (_YY != null) {
            year = getIntValue(_YY);
        }

        String _YEARYY = elements.get("YEARYY");
        if (_YEARYY != null) {
            //
            if (_YEARYY.length() != 4 & _YEARYY.length() != 2) {
                return INVALID_DATE;
            }
            year = getIntValue(_YEARYY);
        }

        if (year == INVALID_DATE) {
            return INVALID_DATE;
        }

        if (year <= FUTURE_YY_THRESHOLD) {
            year += MILLENIUM;
        } else if (year <= 99) {
            year += 1900;
        }

        return year;
    }

    /**
     *
     * @param elements
     * @return
     * @throws java.text.ParseException
     */
    public static int normalize_month(java.util.Map<String, String> elements)
            throws java.text.ParseException {

        //  MM, MONTH  -- numeric 01-12
        //  MON_ABBREV, MON_NAME  -- text
        //
        String MM = elements.get("MM");
        String MON = elements.get("MONTH");

        int m = INVALID_DATE;
        if (MM != null) {
            m = getIntValue(MM);
        } else if (MON != null) {
            m = getIntValue(MON);
        }

        if (m <= 12) {
            return m;
        }
        return INVALID_DATE;
    }

    /**
     *
     * @param elements
     * @return
     * @throws java.text.ParseException
     */
    public static int normalize_month_name(java.util.Map<String, String> elements)
            throws java.text.ParseException {

        //  MM, MONTH  -- numeric 01-12
        //  MON_ABBREV, MON_NAME  -- text
        //
        String ABBREV = elements.get("MON_ABBREV");
        String NAME = elements.get("MON_NAME");
        String text = null;
        if (ABBREV != null) {
            text = ABBREV;
        } else if (NAME != null) {
            text = NAME;
        } else {
            return INVALID_DATE;
        }

        if (text.length() < 3) {
            return INVALID_DATE;
        }

        // Standardize on month trigraph, e.g. ,DEC can get DECEMBER or DECIEMBRE
        //
        DateTime mon = fmt_month.parseDateTime(text.substring(0, 3));
        return mon.getMonthOfYear();
    }

    /**
     *
     * @param elements
     * @return
     */
    public static int normalize_day(java.util.Map<String, String> elements) {
        int day = INVALID_DATE;

        if (elements.containsKey("DOM")) {
            // DOM, DD -- numeric
            int dom = getIntValue(elements.get("DOM"));
            if (dom != INVALID_DATE) {
                day = dom;
            }
        } else if (elements.containsKey("DD")) {
            int dd = getIntValue(elements.get("DD"));
            if (dd != INVALID_DATE) {
                day = dd;
            }
        }

        if (day <= 31 && day >= 0) {
            return day;
        }

        return INVALID_DATE;
    }

    /**
     *
     * @param val
     * @return
     */
    public static int getIntValue(String val) {
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (Exception parse_err) {
                //
            }
        }
        return INVALID_DATE;
    }
}
