/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
 */
package org.opensextant.extractors.xcoord;

/*
 #
 # http://en.wikipedia.org/wiki/Wikipedia:WikiProject_Geographical_coordinates#Precision
 */
import java.text.DecimalFormat;

import org.opensextant.util.TextUtils;

/**
 * @author ubaldino
 */
public class PrecisionScales {

    /**
     *
     */
    public static final float DEFAULT_UNKNOWN_RESOLUTION = 111000f; // Lat 1deg = 111KM approx. maximum
    /**
     *
     */
    public static final float LAT_DEGREE_PRECISION = (int) (DEFAULT_UNKNOWN_RESOLUTION / 2);
    /**
     * Maximal error in METERS in coordinate with N decimal places; for up to 12
     * decimal places.
     */
    public static final double[] DD_precision_list = {
            //
            LAT_DEGREE_PRECISION, // 0
            //
            LAT_DEGREE_PRECISION / 10, // 1
            //
            LAT_DEGREE_PRECISION / 100, // 2, ... etc.
            //
            LAT_DEGREE_PRECISION / 1000,
            //
            LAT_DEGREE_PRECISION / 10000,
            // Approx 1m precision at equator:
            LAT_DEGREE_PRECISION / 100000,
            //
            LAT_DEGREE_PRECISION / 1000000,
            //
            LAT_DEGREE_PRECISION / 10000000,
            //
            LAT_DEGREE_PRECISION / 100000000,
            //
            LAT_DEGREE_PRECISION / 1000000000,
            //
            LAT_DEGREE_PRECISION / 10000000000L,
            //
            LAT_DEGREE_PRECISION / 100000000000L,
            //
            LAT_DEGREE_PRECISION / 1000000000000L };

    // Last entry above.
    static final double FINEST_DD_PRECISION = LAT_DEGREE_PRECISION / 1000000000000L;

    /**
     * Sets the precision on a decimal degrees match
     *
     * @param m given match
     */
    public static void setDDPrecision(GeocoordMatch m) {
        m.precision.setDigits(count_DD_digits(m.lat_text));
        m.precision.setDigits(count_DD_digits(m.lon_text));

        if (m.precision.digits < DD_precision_list.length) {
            m.precision.precision = DD_precision_list[m.precision.digits];
        } else if (m.precision.digits > DD_precision_list.length) {
            m.precision.precision = FINEST_DD_PRECISION;
        } else {
            m.precision.precision = DEFAULT_UNKNOWN_RESOLUTION;
        }
    }

    /**
     * Return XCoord precision (+/- meters) in latitude.
     *
     * @param lat string representing latitude
     * @return precision
     */
    public static GeocoordPrecision getDDPrecision(String lat) {
        GeocoordPrecision prec = new GeocoordPrecision();

        // Find the number of digits used in lat.
        prec.setDigits(count_DD_digits(lat));

        // Determine the error given the number of digits
        if (prec.digits < DD_precision_list.length) {
            prec.precision = DD_precision_list[prec.digits];
        } else if (prec.digits > DD_precision_list.length) {
            prec.precision = FINEST_DD_PRECISION;
        } else {
            prec.precision = DEFAULT_UNKNOWN_RESOLUTION;
        }
        return prec;
    }

    /**
     * Count the number of decimal places in a lat or lon text string.
     *
     * @param lat string representing latitude
     * @return number of digits in lat, as a proxy for precision
     */
    public static int count_DD_digits(String lat) {
        if (lat == null) {
            return 0;
        }
        int x = lat.indexOf('.');
        // "decimal point" Cannot be offset zero
        if (x <= 0) {
            return 0;
        }
        String decPart = lat.substring(x);
        return TextUtils.count_digits(decPart);
    }

    /**
     * Counts all digits in latitude.
     *
     * @param lat string representing latitude
     * @return number of digits in lat as a proxy for precision
     */
    public static int count_DMS_digits(String lat) {
        return TextUtils.count_digits(lat);
    }

    /**
     *
     */
    public static final float DMS_MIN_PREC = 900; // +/- 1.85 KM at equator
    /**
     *
     */
    public static final float DMS_SEC_PREC = 15; // +/- 0.03 KM at equator, 60th of a minute

    /**
     * set precision on a DMS text coordinate -- simply if the Match latitude "has
     * seconds"
     * then its precision is seconds, otherwise if it has minutes, then it is
     * precise to +/- 1 minute,
     * etc.
     * Default precision is half-degree ~ +/- 55KM.
     * Prior implementation was based on digit counting, whereas with decimal
     * degrees you must count digits
     * to infer precision.
     *
     * @param m DMS match
     */
    public static void setDMSPrecision(GeocoordMatch m) {
        m.precision.precision = LAT_DEGREE_PRECISION;

        if (m.hasSeconds()) {
            m.precision.precision = DMS_SEC_PREC;
            m.precision.digits = 5; // 1/3600th of a degree
        } else if (m.hasMinutes()) {
            m.precision.precision = DMS_MIN_PREC;
            m.precision.digits = 2; // 1/60th of a degree
        }
    }

    /**
     * @param m MGRS match
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
     * @param m UTM match
     */
    public static void setUTMPrecision(GeocoordMatch m) {
        m.precision.precision = UTM_precision(m.getText());
        m.precision.digits = 5; // seconds resolution
    }

    /**
     * Precision appears in pairs, as we tolerate some typo/errors in MGRs.
     */
    public static final float[] MGRS_precision_list = { 1000000, // 0 length MGRS -- no meaning.
            100000, // 1 GZD
            100000, // 2 GZD
            100000, // 3 GZD
            100000, // 4 Q
            100000, // 5 Q
            100000, // 6, same as 5; odd # of chars usually means typo. This would be first char in
                    // E/N'ing
            // ---------------------------
            10000, // DEC PREC: 1 digit
            10000, //
            1000, // 3 digits
            1000, // MINUTES PREC 3 digits
            100, // 4 digits
            100, // 4
            10, // SECONDS PREC 6 digits
            10, 1, 1, 0.1f, // 16
            0.1f // 17
    };

    public static final float[] MGRS_offset_precision_list = { 100000, // NONE. 0 or 1 digit of precision is meaningless.
            100000, // NONE.
            10000, // DEC PREC: 1 digit
            10000, //
            1000, // 4 digits
            1000, // MINUTES PREC 4 or 5 digits
            100, // 6 digits
            100, // 7
            10, // SECONDS PREC 8 digits. Goes to 99999
            10, //
            1   // 1-meter precision eeeee nnnnn
    };

    /**
     *
     */
    public static final int[] MGRS2DEC_digits = { 0, 0, // 1 GZD
            0, // 2 GZD
            0, // 3 GZD
            0, // 4 Q
            0, // 5 Q
            0, // 6, same as 5; odd # of chars usually means typo. This would be first char in
               // E/N'ing
               // ---------------------------
            1, // DEC PREC: 1 digit
            1, //
            3, // 3 digits
            3, // MINUTES PREC 3 digits
            4, // 4 digits
            4, // 4
            6, // SECONDS PREC 6 digits
            6, 7, 7, 8, // 16
            8 // 17
    };

    /**
     * For now default UTM precision to +/- 100m
     *
     * @param utm UTM string
     * @return precision
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

    /*
     * Static initialization here creates and caches all the numeric formatters
     * TODO: however at runtime the formatters are cloned (as of v1.6a) so is there
     * any benefit?
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
     *
     * @param f      value
     * @param digits digits to include in format
     * @return formatted value.
     */
    public static String format(double f, int digits) {
        if (digits >= formatters.length) {
            return Double.toString(f);
        }
        // Clone the formatter so that it's thread-safe
        // TODO: well, we would do this clone for every time this method is called.
        // to make such things MT-safe and prevent too much impact on performance, we
        // should do a
        // more thorough implementation review of such static variables.
        DecimalFormat df = (DecimalFormat) formatters[digits].clone();
        return df.format(f);
    }

}
