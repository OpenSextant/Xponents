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

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * DMSOrdinate represents all the various fields a WGS84 cartesian coordinate
 * could have. degree/minute/second, as well as fractional minutes and
 * fractional seconds.
 *
 * Patterns may have symbols which further indicate if a pattern is a literal
 * decimal number (e.g., 33-44, 33.44, 33.444, 33-4444) of if the numbers are in
 * minutes/seconds units (33:44).
 *
 * @author ubaldino
 */
public final class DMSOrdinate {

    /**
     *
     */
    public final static int LAT_MAX = 90;
    /**
     *
     */
    public final static int LON_MAX = 180;
    private float degrees = 0;
    private float minutes = 0;
    private float seconds = 0;
    private boolean is_latitude = false;
    /**
     *
     */
    public Hemisphere hemisphere = new Hemisphere();
    public boolean has_hemi = false;
    private double value = 0;
    /**
     *
     */
    public String text = null;
    /**
     * The raw input elements
     */
    private Map<String, String> _elements = null;
    /**
     * The normalized DMS components
     */
    private Map<String, String> _normal = null;

    /**
     * DMS ordinates can be made up of degrees, minutes, seconds and then
     * decimal minutes and decimal seconds
     *
     * fractional minutes dddd are formatted as ".dddd" then parsed; ADD to
     * existing minutes fractional seconds dddd are formatted as ".dddd" then
     * parsed; ADD to existing seconds
     *
     * decimal minutes d.ddd.. are parsed as float, divide by 60 to get # of
     * degrees decimal seconds d.ddd.. "" "" , divide by 3600 to get # of
     * degrees
     *
     *
     * All Constructors must set is_latitude from a given flag. This is used for
     * range validation.
     *
     * @param elements
     * @param islat
     * @param text
     * @throws XCoordException
     */
    public DMSOrdinate(java.util.Map<String, String> elements, boolean islat, String text)
            throws XCoordException {

        // VALID XCoord elements or groups:
        /* degLat, degLon       dd, ddd   variable length
         * minLat, minLon       0-59,     ""
         * secLat, secLon       0-59,     ""
         * decMinLat, decMinLon 0.00 to 59.99... variable length, arbitrary precision
         * decMinLat3, decMinLon3 0-000 to 59-999... variable length, arbitrary precision
         * decSecLat, decSecLon --- does not exist; need test cases for sub-second accuracy most use decimal degree d.dddddd...
         * 
         * dmsDegLat, dmsDegLon        "dd", "ddd"
         * dmsMinLat, dmsMinLon,       "00" to "59"
         * dmsSecLat, dmsSecLon,       "00" to "59"
         * fractMinLat, fracMinLon       d to dddddd  part of some other string, but represents 0.ddddd minutes
         * fractMinLat3, fractMinLon3  ddd to dddddd  "" "" 
         */

        _elements = elements;
        normalize_hemisphere(text, _elements, islat);

        boolean _parse_state = false;
        _normal = new HashMap<>();

        is_latitude = islat;

        try {

            if (islat) {
                _parse_state = digest_latitude_match();
            } else {
                _parse_state = digest_longitude_match();
            }

        } catch (Exception fmterr) {
            // do nothing
            // possible report parse errors in debug mode.
            throw new XCoordException("Format Error", fmterr);
        }

        if (!_parse_state) {
            throw new XCoordException("Unable to parse match due to " + (islat ? "LAT" : "LON"));
        }

        set_normalized_text();
        value = toDecimal();
    }

    /**
     *
     * @return
     */
    public boolean hasHemisphere() {
        // return hemisphere.polarity != NO_HEMISPHERE_VALUE;
        return has_hemi;
    }

    /**
     * Get the cartesian value for this ordinate
     *
     * @return
     */
    public double getValue() {
        return value;
    }
    /**
     *
     */
    public boolean has_degrees = false;
    /**
     *
     */
    public boolean has_minutes = false;
    /**
     *
     */
    public boolean has_seconds = false;
    /**
     *
     */
    protected final static DecimalFormat field = new DecimalFormat("00");
    /**
     *
     */
    protected final static DecimalFormat field3 = new DecimalFormat("000");
    // Decimal format out to 6 places for min/sec DMS fields.
    /**
     *
     */
    protected final static DecimalFormat fieldDec = new DecimalFormat("00.######");

    /**
     * Get back a normalized version of what you found. Yield zero-padded
     * version of coordinate, e.g., if DMS xdeg M' SS" is parsed we want
     * xxx:MM:SS fully padded
     *
     * 45 33N-00811 given 45:33.00811 normalized, DEG:MIN.mmmmm
     *
     * Full format should be:
     *
     * hDDD.ddd hDDD:MM.mmm hDDD:MM.mmm:SS.sss unlikely -- if this happens --
     * both fractional minutes and fractional seconds, pattern has errors
     * hDDD:MM:SS.sss
     *
     * .ddd, .mmm, .sss --- as many decimal places as there are h --- +/-
     * hemisphere
     *
     */
    protected void set_normalized_text() {

        String d = _normal.get("deg");
        String m = _normal.get("min");
        String fmin = _normal.get("fmin");
        String s = _normal.get("sec");
        String fsec = _normal.get("fsec");

        StringBuilder buf = new StringBuilder();
        if (hemisphere.polarity == -1) {
            buf.append("-");
        } else {
            buf.append("+");
        }

        has_degrees = true;

        // This cascades -- degrees, assumed.  Minutes and then possibly Seconds
        // but we won't ever have seconds w/out deg or minutes first.
        /*
         if (this.is_latitude) {
         buf.append(field.format(this.degrees));  // LAT degree
         } else {
         buf.append(field3.format(this.degrees)); // LON degree     
         } */
        buf.append(d);  // LAT degree

        if (m == null) {
            // Degrees only or Decimal degrees.   Quietly fail.
            this.text = buf.toString();
            return;
        }

        has_minutes = true;
        buf.append(":");
        //buf.append(fieldDec.format(minutes));
        buf.append(m);

        // has_seconds is not reliable.  fractional minutes may be part of minutes field

        // Reconstituing fractional minutes or seconds
        // decimal part may have been separated from minutes, so it is likely
        // minutes is int, and fmin is another number.  
        // TODO:  formatting logic could be cleaner here.
        //
        if (fmin != null) {
            has_seconds = true;  // Seconds precision in the form of fractional minutes.
            // buf.append(".");
            buf.append(fmin);  // append text as-is.  No arithmetic.
        }

        // TODO:  either minutes is decimal, OR fmin is available OR seconds are available.
        if (s != null) {
            has_seconds = true;
            buf.append(":");
            //buf.append(fieldDec.format(seconds));
            buf.append(s);

            if (fsec != null) {
                // buf.append(".");
                buf.append(fsec);
            }
        }

        this.text = buf.toString();
    }

    private void saveField(String fieldNorm, String val) {
        _normal.put(fieldNorm, val);
    }

    private Integer getIntValue(String field, String norm) {
        String val = _elements.get(field);
        if (val == null) {
            return null;
        }

        saveField(norm, val);
        return new Integer(val);
    }

    /**  Convert numbers like "8.888" or "8-888" to decimal numbers.
     */
    private Float getDecimalValue(String field, String norm) {
        String val = _elements.get(field);
        if (val == null) {
            return null;
        }
        if (val.contains("-")) {
            // Log this situation
            val = val.replaceFirst("-", ".");
        }
        saveField(norm, val);
        return new Float(val);
    }

    /**
     * As a result of this, the fsec field text will be saved with decimal
     * point.  Convert "-888" to the decimal part of a value, e.g., "0.888"
     */
    private Float getFractionValue(String field, String norm) {
        String val = _elements.get(field);
        if (val == null) {
            return null;
        }

        if (val.startsWith("-")) {
            // Log this situation
            val = val.replaceFirst("-", ".");
        } else if (!val.startsWith(".")) {
            // already has a decimal point?
            val = "." + val;
        }

        saveField(norm, val);
        return Float.parseFloat(val);
    }

    /**
     *
     * @return
     */
    public final boolean digest_latitude_match() {
        // DEGREES
        Integer deg = getIntValue("degLat", "deg");
        Integer deg2 = getIntValue("dmsDegLat", "deg");
        Float deg3 = getDecimalValue("decDegLat", "deg");
        if (deg != null) {
            degrees = deg.intValue();
        } else if (deg2 != null) {
            degrees = deg2.intValue();
        } else if (deg3 != null) {
            degrees = deg3.floatValue();
        } else {
            return false;
        }

        // MINUTES
        Integer min = getIntValue("minLat", "min");
        Integer min2 = getIntValue("dmsMinLat", "min");
        Float min3 = getDecimalValue("decMinLat", "min");
        Float min3dash = getDecimalValue("decMinLat3", "min");

        if (min != null) {
            minutes = min.intValue();
        } else if (min2 != null) {
            minutes = min2.intValue();
        } else if (min3 != null) {
            minutes = min3.floatValue();
        } else if (min3dash != null) {
            minutes = min3dash.floatValue();
        }

        Float min_fract = getFractionValue("fractMinLat", "fmin");
        Float min_fract2 = getFractionValue("fractMinLat3", "fmin"); // variation 2, is a 3-digit or longer fraction
        if (min_fract != null) {
            minutes += min_fract.floatValue();
        } else if (min_fract2 != null) {
            minutes += min_fract2.floatValue();
        }

        // SECONDS
        Integer sec = getIntValue("secLat", "sec");
        Integer sec2 = getIntValue("dmsSecLat", "sec");

        if (sec != null) {
            seconds = sec.intValue();
        } else if (sec2 != null) {
            seconds = sec2.intValue();
        }

        Float fsec = getFractionValue("fractSecLat", "fsec");
        Float fsec2 = getFractionValue("fractSecLatOpt", "fsec");
        if (fsec != null) {
            seconds += fsec.floatValue();
        } else if (fsec2 != null) {
            seconds += fsec2.floatValue();
        }

        return true;
    }

    /**
     * This is a copy of the logic for digest_latitude_match; All I replace is
     * "Lat" with "Lon"
     *
     * @return
     */
    public final boolean digest_longitude_match() {
        // DEGREES
        Integer deg = getIntValue("degLon", "deg");
        Integer deg2 = getIntValue("dmsDegLon", "deg");
        Float deg3 = getDecimalValue("decDegLon", "deg");
        if (deg != null) {
            degrees = deg.intValue();
        } else if (deg2 != null) {
            degrees = deg2.intValue();
        } else if (deg3 != null) {
            degrees = deg3.floatValue();
        } else {
            return false;
        }

        // MINUTES
        Integer min = getIntValue("minLon", "min");
        Integer min2 = getIntValue("dmsMinLon", "min");
        Float min3 = getDecimalValue("decMinLon", "min");
        Float min3dash = getDecimalValue("decMinLon3", "min");

        if (min != null) {
            minutes = min.intValue();
        } else if (min2 != null) {
            minutes = min2.intValue();
        } else if (min3 != null) {
            minutes = min3.floatValue();
        } else if (min3dash != null) {
            minutes = min3dash.floatValue();
        }

        Float min_fract = getFractionValue("fractMinLon", "fmin");
        Float min_fract2 = getFractionValue("fractMinLon3", "fmin"); // variation 2, is a 3-digit or longer fraction
        if (min_fract != null) {
            minutes += min_fract.floatValue();
        } else if (min_fract2 != null) {
            minutes += min_fract2.floatValue();
        }


        // SECONDS
        Integer sec = getIntValue("secLon", "sec");
        Integer sec2 = getIntValue("dmsSecLon", "sec");

        if (sec != null) {
            seconds = sec.intValue();
        } else if (sec2 != null) {
            seconds = sec2.intValue();
        }

        Float fsec = getFractionValue("fractSecLon", "fsec");
        Float fsec2 = getFractionValue("fractSecLonOpt", "fsec");
        if (fsec != null) {
            seconds += fsec.floatValue();
        } else if (fsec2 != null) {
            seconds += fsec2.floatValue();
        }

        return true;
    }

    /**
     * Normalize a hemisphere pattern: +, -, NSEW, which may either be
     * preceeding coordinate or after it. As well, it may be optional, so lack
     * of a hemisphere may match to null. In which case, -- NO_HEMISPHERE --
     * caller should assume POSITIVE hemisphere is implied. further filtering by
     * caller is warranted.
     *
     * @param text
     * @param elements
     * @param islat
     */
    protected void normalize_hemisphere(String text,
            java.util.Map<String, String> elements, boolean islat) {

        int hemi = (islat ? getLatHemisphereSign(elements) : getLonHemisphereSign(elements));

        if (!hemisphere.isAlpha()) {
            coord_symbol = hasCoordinateSymbols(text);
        }

        has_hemi = hemi != NO_HEMISPHERE_VALUE;

        if (!has_hemi) {
            hemi = POS_HEMI; // Un-specified hemisphere defaults to POSITIVE           
            /*
             if (hasSymbols()) {
             hemi = POS_HEMI; // Un-specified hemisphere defaults to POSITIVE           
             } else {
             hemi = POS_HEMI;
             } */
        }

        hemisphere.polarity = hemi; // -1 or 1 only.
    }

    /**
     *
     * @return
     */
    public boolean hasSymbols() {
        return coord_symbol != null;
    }
    /**
     *
     */
    public final static String[] COORDINATE_SYMBOLS = {"°", "º", "'", "\"", ":", "lat", "lon", "geo", "coord", "deg"};
    private String coord_symbol = null;

    /**
     *
     * @param text
     * @return
     */
    private String hasCoordinateSymbols(String text) {
        String val = text.toLowerCase();
        for (String ch : COORDINATE_SYMBOLS) {
            if (val.contains(ch)) {
                return ch;
            }
        }
        return null;
    }
    /**
     */
    public final static String WEST = "W";
    /**
     */
    public final static String SOUTH = "S";
    /**
     */
    public final static String NORTH = "N";
    /**
     */
    public final static String EAST = "E";
    /**
     */
    public final static String NEGATIVE = "-";
    /**
     */
    public final static String POSITIVE = "+";
    /**
     */
    public final static int NO_HEMISPHERE = 0;
    /**
     */
    public final static int NO_HEMISPHERE_VALUE = -0x10;
    /**
     */
    public final static int POS_HEMI = 1;
    /**
     */
    public final static int NEG_HEMI = -1;
    /**
     */
    public final static Map<String, Integer> HEMI_MAP = new HashMap<String, Integer>();

    static {

        HEMI_MAP.put(WEST, -1);
        HEMI_MAP.put(SOUTH, -1);
        HEMI_MAP.put(EAST, 1);
        HEMI_MAP.put(NORTH, 1);
        HEMI_MAP.put(NEGATIVE, -1);
        HEMI_MAP.put(POSITIVE, 1);
    }

    /**
     *
     * @param hemi
     * @return
     */
    public static String get_hemisphere_symbol(int hemi) {
        if (hemi == -1) {
            return NEGATIVE;
        } else if (hemi == 1) {
            return POSITIVE;
        }
        return "UNK";
    }

    /**
     *
     * @param val
     * @return
     */
    public static int get_hemisphere_sign(String val) {
        if (val == null) {
            return NO_HEMISPHERE_VALUE;
        }
        Integer s = HEMI_MAP.get(val.trim().toUpperCase());
        if (s != null) {
            return s.intValue();
        }
        return NO_HEMISPHERE;
    }

    /**
     * @see getLonHemisphereSign
     *
     * @param elements list of trimmed components of the match
     * @return
     */
    protected int getLatHemisphereSign(java.util.Map<String, String> elements) {

        String hemiLat = elements.get("hemiLat");
        String hemiSignLat = elements.get("hemiLatSign");
        String hemiLatPre = elements.get("hemiLatPre");

        if (hemiLatPre != null) {
            hemisphere.symbol = hemiLatPre;
            return get_hemisphere_sign(hemiLatPre);
        } else if (hemiLat != null) {
            hemisphere.symbol = hemiLat;
            return get_hemisphere_sign(hemiLat);
        } else if (hemiSignLat != null) {
            hemisphere.symbol = hemiSignLat;
            return get_hemisphere_sign(hemiSignLat);
        }

        return NO_HEMISPHERE_VALUE;

    }

    /**
     * Given a list of match groups find the first Longitude Hemisphere group
     *
     * hemiLon W, E group used mostly for DMS, DM, DD formats hemiLonSign +, -
     * group allowed only for specific formats; +/- may appear before any number
     * not just coords. hemiLonPre W, E, +, -
     *
     * @param elements list of trimmed components of the match
     * @return
     */
    protected int getLonHemisphereSign(java.util.Map<String, String> elements) {

        String hemiLon = elements.get("hemiLon");
        String hemiSignLon = elements.get("hemiLonSign");
        String hemiLonPre = elements.get("hemiLonPre");

        if (hemiLonPre != null) {
            hemisphere.symbol = hemiLonPre;
            return get_hemisphere_sign(hemiLonPre);
        } else if (hemiLon != null) {
            hemisphere.symbol = hemiLon;
            return get_hemisphere_sign(hemiLon);
        } else if (hemiSignLon != null) {
            hemisphere.symbol = hemiSignLon;
            return get_hemisphere_sign(hemiSignLon);
        }

        return NO_HEMISPHERE_VALUE;
    }

    /**
     * Only used for literal decimal degrees that require little parsing.
     *
     * @param deg
     * @param min
     * @param sec
     * @param islat
     * @param hemi_sign
     */
    public DMSOrdinate(int deg, int min, int sec, boolean islat, int hemi_sign) {
        degrees = deg;
        minutes = min;
        seconds = sec;
        is_latitude = islat;
        hemisphere.polarity = hemi_sign;

        value = toDecimal();
    }

    /**
     * Unused as of ver1.0
     *
     * @param deg
     * @param min
     * @param sec
     * @param msec
     * @param islat
     * @param hemi_sign
     */
    public DMSOrdinate(int deg, int min, int sec, int msec, boolean islat, int hemi_sign) {
        this(deg, min, sec, islat, hemi_sign);
        if (msec > 0) {
            seconds += 0.001 * (float) msec;
        }
        value = toDecimal();
    }

    /**
     * Unused
     *
     * @param deg
     * @param min
     * @param sec
     * @param islat
     * @param hemi_sign
     * @throws java.text.ParseException
     */
    public DMSOrdinate(String deg, String min, String sec, boolean islat, int hemi_sign)
            throws java.text.ParseException {
        if (deg == null) {
            throw new java.text.ParseException("Degrees may not be null", 1);
        }
        degrees = Integer.parseInt(deg);
        text = deg;

        is_latitude = islat;
        hemisphere.polarity = hemi_sign;

        if (hemi_sign < 0) {
            text = "-" + text;
        }

        if (min != null) {
            minutes = Integer.parseInt(min);
            text += ":" + min;
        }

        if (sec != null) {
            seconds = Integer.parseInt(sec);
            text += ":" + sec;
        }

        value = toDecimal();
    }

    /**
     *
     * @return
     */
    public boolean validate() {
        if (is_latitude && Math.abs(value) >= LAT_MAX) {
            return false;
        }
        if (!is_latitude && Math.abs(value) >= LON_MAX) {
            return false;
        }
        return true;
    }

    /**
     * toDD() Return the decimal value to the extent it makes sense. That is,
     * calculating minutes or seconds where MIN or SEC fields are out of bounds
     * for those units does not make sense, return at least Deg or Deg/Min.
     *
     * Validation of the ordinate is a second step to be done by caller.
     *
     * @return
     */
    public double toDecimal() {

        //float value = 0.0f;
        //boolean too_high = (is_latitude && degrees >= LAT_MAX) | (!is_latitude && degrees >= LON_MAX);

        //if (too_high) {
        //    return value;
        //}
        if (minutes >= 60) {
            // This much we know, Degrees are valid? 
            return (double) hemisphere.polarity * degrees;
        }
        if (seconds >= 60) {
            // At least you have degress and minutes, right?
            return (double) hemisphere.polarity * (degrees + (minutes / 60));
        }

        return (double) hemisphere.polarity * (degrees + (minutes / 60) + (seconds / 3600));
    }
}
