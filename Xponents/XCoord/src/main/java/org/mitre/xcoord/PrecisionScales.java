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

/*
 # 
 # http://en.wikipedia.org/wiki/Wikipedia:WikiProject_Geographical_coordinates#Precision 
 */
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ubaldino
 *
 */
public class PrecisionScales {

    /**
     *
     */
    public final static float DEFAULT_UNKNOWN_RESOLUTION = 111000f; // Lat 1deg = 111KM approx. maximum
    /**
     *
     */
    public final static float LAT_DEGREE_PRECISION = (int) (DEFAULT_UNKNOWN_RESOLUTION / 2);
    /**
     * Maximal error in METERS in coordinate with N decimal places; for up to 12
     * decimal places.
     */
    public final static double[] DD_precision_list = {
        LAT_DEGREE_PRECISION, // 0
        LAT_DEGREE_PRECISION / 10, // 1
        LAT_DEGREE_PRECISION / 100, // 2, ... etc.
        LAT_DEGREE_PRECISION / 1000,
        LAT_DEGREE_PRECISION / 10000,
        LAT_DEGREE_PRECISION / 100000,
        LAT_DEGREE_PRECISION / 1000000,
        LAT_DEGREE_PRECISION / 10000000,
        LAT_DEGREE_PRECISION / 100000000,
        LAT_DEGREE_PRECISION / 1000000000,
        LAT_DEGREE_PRECISION / 10000000000L,
        LAT_DEGREE_PRECISION / 100000000000L,
        LAT_DEGREE_PRECISION / 1000000000000L
    };

    /**
     * @param m 
     */
    public static void setDDPrecision(GeocoordMatch m) {
        m.precision.setDigits( count_DD_digits(m.lat_text) );
        m.precision.setDigits( count_DD_digits(m.lon_text) );

        if (m.precision.digits < DD_precision_list.length) {
            m.precision.precision = DD_precision_list[m.precision.digits];
        } else {
            m.precision.precision = DEFAULT_UNKNOWN_RESOLUTION;
        }
    }

    /**
     *
     * @param lat
     * @return
     */
    public static int count_DD_digits(String lat) {
        if (lat == null) {
            return 0;
        }

        // Try calculating the sig-figs on the string repr of the number.
        String[] parts = lat.split("[.]", 2);
        if (parts.length == 2) {
            return parts[1].length();
        }
        return 0;
    }

    /**
     * Counts all digits in latitude.
     *
     * @param lat
     * @return  
     */
    public static int count_DMS_digits(String lat) {
        if (lat == null) {
            return 0;
        }

        int digits = 0;
        for (char c : lat.toCharArray()) {
            if (Character.isDigit(c)) {
                ++digits;
            }
        }
        return digits;
    }
    /**
     *
     */
    public static float DMS_MIN_PREC = 900;  // +/- 1.85 KM at equator
    /**
     *
     */
    public static float DMS_SEC_PREC = 15;   // +/- 0.03 KM at equator, 60th of a minute

    /**
     * set precision on a DMS text coordinate
     *
     * @param m 
     */
    public static void setDMSPrecision(GeocoordMatch m) {
        m.precision.precision = LAT_DEGREE_PRECISION;

        if (m.lat_text != null) {
            int dig = count_DMS_digits(m.lat_text);
            int deg = Math.abs((int) m.latitude);
            if (deg < 10) {
                --dig;
            } else {
                //  10 <= Lat <= 90   ---> Means it is a 2digit string.
                --dig;
                --dig;
            }
            m.precision.digits = dig;

            if (dig >= 4) {
                m.precision.precision = DMS_SEC_PREC;

            } else if (dig >= 2) {
                m.precision.precision = DMS_MIN_PREC;
            }
            return;
        }
        
        
        if (m.hasSeconds()) {
            m.precision.precision = DMS_SEC_PREC;
            m.precision.digits = 5;  // 1/3600th of a degree
        } else if (m.hasMinutes()) {
            m.precision.precision = DMS_MIN_PREC;
            m.precision.digits = 2;  // 1/60th of a degree
        }
    }

    /**
     *
     * @param m
     */
    public static void setMGRSPrecision(GeocoordMatch m) {

        if (m.coord_text == null) {
            return;
        }

        /*
         * Given MGRS padded, but no whitespace -- the length indicates precision.
         */
        int len = m.coord_text.length();
        if (len < 17) {
            m.precision.precision = MGRS_precision_list[len];
            m.precision.digits = MGRS2DEC_digits[len];
        }
    }

    /**
     *
     * @param m
     */
    public static void setUTMPrecision(GeocoordMatch m) {
        m.precision.precision = UTM_precision(m.getText());
        m.precision.digits = 5; // seconds resolution
    }
    /**
     * Precision appears in pairs, as we tolerate some typo/errors in MGRs.
     *
     */
    public static float[] MGRS_precision_list = {
        100000, // 1  GZD
        100000, // 2  GZD
        100000, // 3  GZD
        100000, // 4  Q
        100000, // 5  Q
        100000, // 6, same as 5; odd # of chars usually means typo.  This would be first char in E/N'ing
        // ---------------------------
        10000, //  DEC PREC:  1 digit
        10000, //
        1000, //             3 digits
        1000, // MINUTES PREC 3 digits
        100, //              4 digits
        100, //              4
        10, // SECONDS PREC 6 digits
        10,
        1,
        1,
        0.1f, // 16
        0.1f // 17
    };
    /**
     *
     */
    public static int[] MGRS2DEC_digits = {
        0, // 1  GZD
        0, // 2  GZD
        0, // 3  GZD
        0, // 4  Q
        0, // 5  Q
        0, // 6, same as 5; odd # of chars usually means typo.  This would be first char in E/N'ing
        // ---------------------------
        1, //  DEC PREC:  1 digit
        1, //
        3, //             3 digits
        3, // MINUTES PREC 3 digits
        4, //              4 digits
        4, //              4
        6, // SECONDS PREC 6 digits
        6,
        7,
        7,
        8, // 16
        8 // 17
    };

    /**
     * For now default UTM precision to +/- 100m
     * @param utm 
     * @return 
     */
    public static float UTM_precision(String utm) {
        return 100f;
    }
    /**
     * Numeric formatters are cached here for performance. number of digits of
     * precision depends on lat (%2.12f) vs. lon (%3.12f)
     * WARNING: these are NOT thread-safe!
     */
    private static final DecimalFormat[] formatters;


    /* Static initialization here creates and caches all the numeric formatters 
     * TODO: however at runtime the formatters are cloned (as of v1.6a) so is there any benefit?
     */
    static {
      formatters = new DecimalFormat[12];
      formatters[0] = new DecimalFormat("0");
      StringBuilder buf = new StringBuilder("0.");
      for (int i = 1; i < formatters.length; i++) {
        buf.append('0');
        formatters[i] = new DecimalFormat(buf.toString());
      }
    }

    /**
     * This was deemed to be more Java like, however performs 10x slower than
     * format2() -- which unfortunately rounds too early.
     * @param f
     * @param digits
     * @return  
     */
    public static String format(double f, int digits) {
        //return (long)(f * digits)/digits);
        if (digits >= formatters.length) {
            return "" + f;
        }
        //Clone the formatter so that it's thread-safe
        // TODO: well, we would do this clone for every time this method is called.
        //     to make such things MT-safe and prevent too much impact on performance, we should do a 
        //     more thorough implementation review of such static variables.
        DecimalFormat df = (DecimalFormat) formatters[digits].clone();
        return df.format(f);
    }

    /**
     * Rounds the number to the given digits past the decimal point. The
     * code is pretty simple and is very fast as it doesn't use DecimalFormat
     * or printf.  See <a href="http://stackoverflow.com/a/5806991/92186">here</a>
     * for the algorithm.
     *
     * @param f 
     * @param digits 
     * @see tests in main()
     * @deprecated TODO needs to be re-evaluated
     * @author dsmiley
     * @return String formatted number
     */
    public static String format2(double f, int digits) {
        long rounderShift = 1;
        //TODO DWS: the comment here is probably obsolete since adding the round()
        // Must shift one past requested, as long conversion consistently rounds down
        //   42.3 == 1 digit of precision
        //   42.2999999 (in Java as a double, geesh.)
        //   42.29999 *10 ==> LONG = 422
        // Answer:  42.2  *Wrong.
        //   42.29999 *100 ==> LONG = 4229
        // Answer:  42.29  still wrong.
        //
        //TODO Math.pow() instead?  Same thing but we have a nice error check here.
        for (int i = 0; i <= digits; i++) {
            rounderShift *= 10;
            if (rounderShift < 1) {
                throw new IllegalArgumentException("Digits is too large: " + digits);
            }
        }
        long fShifted = Math.round(f * rounderShift);//truncates trailing decimals by conversion to long
        return "" + ((double) fShifted / rounderShift);
    }

    /**
     *
     * @param f
     * @param digits
     * @return
     */
    public static double bigDecimalRounder(double f, int digits) {
        return new BigDecimal(f).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /**
     * @deprecated see setDDPrecision
     * @param lat String version of decimal latitude, AS found raw
     * @return  
     */
    public static double DD_precision(String lat) {
        if (lat == null) {
            return -1;
        }

        if (!lat.contains(".")) {
            return LAT_DEGREE_PRECISION;
        }

        // Try calculating the sig-figs on the string repr of the number.
        String[] parts = lat.split("[.]", 2);
        if (parts.length == 2) {
            int sig_figs = parts[1].length();
            if (sig_figs < DD_precision_list.length) {
                return DD_precision_list[sig_figs];
            }
        }
        return DEFAULT_UNKNOWN_RESOLUTION;
    }
    /**
     * @deprecated Counting DMS digits is not accurate portrayal of precision
     * 02:02:33N 6 digits 2:2:33N 4 digits
     *
     * Ah same precision, but different number of digits.
     */
    public static float[] DM_precision_list = {
        DEFAULT_UNKNOWN_RESOLUTION, // 0
        DEFAULT_UNKNOWN_RESOLUTION, // 1
        LAT_DEGREE_PRECISION, // 2
        10000f, // 3
        1300f, // 4
        220f, // 5
        22f,
        2.2f,
        0.2f,
        0.02f,
        0.002f,
        0.0002f};

    /**
     * @deprecated use setDMSPrecision()
     * @param lat String version of decimal latitude, AS found raw
     * @return  
     */
    public static float DM_precision(String lat) {
        if (lat == null) {
            return -1;
        }

        /* Given an LAT / Y coordinate, determine the amount of precision therein.
         * @TODO: redo,  given "x xx xx" vs. "xx xx xx"  -- resolution is the same. Should only count
         * digits after 
         * 
         * LON / X coordinate can be up to 179.9999... so would always have one more leading digit than lat.
         * 
         */
        int digits = 0;
        for (char ch : lat.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits += 1;
            }
        }

        if (digits > 11) {
            return DEFAULT_UNKNOWN_RESOLUTION;
        }

        return DM_precision_list[digits];
    }
}
