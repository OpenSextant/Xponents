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
     * For now this reports only DATE and standard TIME fields. Timezone is
     * still TODO.
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
        _cal = _cal.withDayOfMonth(dom);

        // For normal M/D/Y patterns, set the default time to noon, UTC
        // Overall, we want to ensure that the general yyyy-mm-dd form is not impacted
        // by time zone and default hour of 00:00;  -- this generally would yield a date format a day early for ALL US timezones.
        // 
        // Time res:  the presence of a field, hh, mm, or ss means the pattern has that level of resolution.
        // So even if time is 00:00:00Z  -- all zeroes -- the resolution is still SECONDS.
        // 
        int hour = normalize_time(elements, "hh");
        if (hour >= 0) {
            // Only if HH:MM... is present do we try to detect TZ.
            //
            DateTimeZone tz = normalize_tz(elements);
            if (tz != null) {
                _cal = _cal.withZone(tz);
            }
            
            // NON-zero hour.
            dt.resolution = DateMatch.TimeResolution.HOUR;
            int min = normalize_time(elements, "mm");
            if (min >= 0) {
                dt.resolution = DateMatch.TimeResolution.MINUTE;
                // NON-zero minutes
                _cal = _cal.withHourOfDay(hour);
                _cal = _cal.withMinuteOfHour(min);
            } else {
                // No minutes
                _cal = _cal.withHourOfDay(hour);
            }


        } else {
            // No hour; default is 12:00 UTC.
            _cal = _cal.withHourOfDay(12);
        }


        dt.datenorm = new Date(_cal.getMillis());
    }

    /**
     * Z or Zulu is not always recognized as UTC / GMT+0000
     */
    public static DateTimeZone normalize_tz(java.util.Map<String, String> elements) {

        if (elements.containsKey("SHORT_TZ")) {
            String tz = elements.get("SHORT_TZ");
            if ("Z".equalsIgnoreCase(tz)) {
                return DateTimeZone.UTC;
            }

            return DateTimeZone.forID(tz);

        } else if (elements.containsKey("LONG_TZ")) {
            String tz = elements.get("LONG_TZ");
            if ("Zulu".equalsIgnoreCase(tz)) {
                return DateTimeZone.UTC;
            }

            return DateTimeZone.forID(tz);
        }

        // Default is UTC+0.
        return null;
    }

    /**
     *
     * Given a field hh, mm, or ss, get field from map and normalize/validate
     * the value
     */
    public static int normalize_time(java.util.Map<String, String> elements, String tmField) {

        if (!elements.containsKey(tmField)) {
            return -1;
        }
        int val = getIntValue(elements.get(tmField));
        if (val < 0) {
            return -1;
        }

        if ("hh".equals("tmField")) {
            if (val < 24) {
                return val;
            }
        } else if ("mm".equals("tmField")) {
            if (val < 60) {
                return val;
            }
        } else if ("ss".equals("tmField")) {
            if (val < 60) {
                return val;
            }
        } else { 
            // Unknown field;
            return val;
        }

        return -1;
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
        boolean _is_4digit = false;
        boolean _is_year = false;

        if (_YEAR != null) {
            //year = yy;            
            return getIntValue(_YEAR);
        }

        int year = INVALID_DATE;

        String _YY = elements.get("YY");
        String _YEARYY = elements.get("YEARYY");
        if (_YY != null) {
            year = getIntValue(_YY);
            // NOTE: because we matched a YY field, this should ideally be in 
            //    an explicity format.
            _is_year = true;
        } else if (_YEARYY != null) {
            if (_YEARYY.startsWith("'")) {
                _is_year = true;
                _YEARYY = _YEARYY.substring(1);
            }

            if (_YEARYY.length() == 4) {
                _is_4digit = true;
                _is_year = true;
            } else if (_YEARYY.length() == 2) {
                // Special case: 00 yields 2000
                // this check here has no effect, as rule below considers "00" = 0, which is < FUTURE_YY_THRESHOLD
                if ("00".equals(_YEARYY)) {
                    _is_year = true;
                }
            } else {
                year = INVALID_DATE;
            }
            year = getIntValue(_YEARYY);
        }

        if (year == INVALID_DATE) {
            return INVALID_DATE;
        }

        if (year <= FUTURE_YY_THRESHOLD) {
            // TEST:  '12, '13, ... '15 == yield 2012, 2013, 2015 etc.
            //   limit is deteremined by current year + fuzzy limit.
            // is '18 2018 or 1918? What is your YY limit?
            // 
            year += MILLENIUM;
        } else if (year <= 99 && _is_year) {
            // Okay we got something beyond the threshold but is previous century likely
            // '21 => 1921
            // '44 => 1944
            // 44 =>?
            year += 1900;
        } else if (!_is_year && year > 31 && year <= 99) {
            // Okay its NOT a year
            //      its NOT a month
            // so "44" => 1944 is best guess.  not 1844, not 0044...
            // 
            year += 1990;
        } else if (!_is_year) {
            // Given two digit year that is possible day of month,... ignore!
            // JUN 17  -- no year given
            // JUN '17 -- is a year
            return INVALID_DATE;
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

        // How long is a month name really?  May is shortest, Deciembre or September are longest.
        if (text.length() < 3 || text.length() > 10) {
            return INVALID_DATE;
        }

        // TODO: Standardize on month trigraph, e.g. ,DEC can get DECEMBER or DECIEMBRE
        // False positivies:  "market 19" is not "mar 19" or "march 19"
        // 
        DateTime mon = fmt_month.parseDateTime(text /*.substring(0, 3)*/);
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
