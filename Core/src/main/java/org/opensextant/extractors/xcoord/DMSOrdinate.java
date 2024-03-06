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

import java.util.HashMap;
import java.util.Map;

import org.opensextant.extraction.NormalizationException;
import org.opensextant.extraction.TextEntity;

/**
 * DMSOrdinate represents all the various fields a WGS84 cartesian coordinate
 * could have.
 * degree/minute/second, as well as fractional minutes and fractional seconds.
 * Patterns may have symbols which further indicate if a pattern is a literal
 * decimal number (e.g.,
 * 33-44, 33.44, 33.444, 33-4444) of if the numbers are in minutes/seconds units
 * (33:44).
 *
 * @author ubaldino
 */
public final class DMSOrdinate {

    /**
     *
     */
    public static final int LAT_MAX = 90;
    /**
     *
     */
    public static final int LON_MAX = 180;
    private float degrees = -1;
    private float minutes = -1;
    private float seconds = -1;
    private boolean isLatitude = false;

    /** Where does the Degree value begin in text? */
    int offsetDeg = -1;
    int offsetOrdinate = -1;

    /**
     *
     */
    public Hemisphere hemisphere = new Hemisphere();
    public boolean has_hemi = false;
    private double value = 0;

    /**
     * Resolution field for DMS.ms
     */
    public enum Resolution {
        DEG, SUBDEG, MIN, SUBMIN, SEC, SUBSEC
    }

    /**
     * <pre>
     * Specificity is a field resolution, that is how many fields were specified in the match?
     *  Ex. D:M:S.ss  match is pretty fine -- sub-second
     *  Versus absolute resolution which looks at the significant-figures in the absolute value.
     *  Ex. D.dddddddddddd  is very fine, too. This can either be coarse or super-fine.
     *
     *  Lat/Lon specificity mismatches indicate false-positives, lat(DEG) with a lon(SUBMIN)
     *  its unlikely they are valid pair.
     * </pre>
     */
    public Resolution specificity = Resolution.DEG;

    /**
     *
     */
    public String text = null;
    /**
     * The raw input elements
     */
    private Map<String, String> fieldValues = null;
    private Map<String, TextEntity> fields = null;
    /**
     * The normalized DMS components
     */
    private Map<String, String> normalizedValues = null;

    /**
     * DMS ordinates can be made up of degrees, minutes, seconds and then decimal
     * minutes and decimal
     * seconds
     * fractional minutes dddd are formatted as ".dddd" then parsed; ADD to existing
     * minutes fractional
     * seconds dddd are formatted as ".dddd" then parsed; ADD to existing seconds
     * decimal minutes d.ddd.. are parsed as float, divide by 60 to get # of degrees
     * decimal seconds
     * d.ddd.. "" "" , divide by 3600 to get # of degrees
     * All Constructors must set is_latitude from a given flag. This is used for
     * range validation.
     *
     * @param fieldMatches the field matches
     * @param fieldVals    the field vals
     * @param islat        true if matched part is for latitude
     * @param t            the raw text of the matched coordinate
     * @throws NormalizationException if coordinate is not valid or unable to
     *                                normalize
     */
    public DMSOrdinate(Map<String, TextEntity> fieldMatches, Map<String, String> fieldVals, boolean islat, String t)
            throws NormalizationException {

        // VALID XCoord elements or groups:

        // degLat, degLon dd, ddd variable length
        // minLat, minLon 0-59, ""
        // secLat, secLon 0-59, ""
        // decMinLat, decMinLon 0.00 to 59.99...variable length, arbitrary precision
        // decMinLat3, decMinLon3 0-000 to 59-999... variable length, arbitrary precision
        // decSecLat, decSecLon --- does not exist; need test cases for sub-second accuracy most use
        // decimal degree d.dddddd...
        //
        // dmsDegLat, dmsDegLon "dd", "ddd" dmsMinLat, dmsMinLon, "00" to "59"
        // dmsSecLat, dmsSecLon, "00" to "59"
        // fractMinLat, fracMinLon d to dddddd part of some other string, but represents 0.ddddd minutes
        // fractMinLat3, fractMinLon3 ddd to dddddd "" ""

        fields = fieldMatches;
        fieldValues = fieldVals;

        normalize_hemisphere(t, fieldValues, islat);

        boolean _parse_state = false;
        normalizedValues = new HashMap<>();
        isLatitude = islat;

        try {
            if (islat) {
                _parse_state = digest_latitude_match();
            } else {
                _parse_state = digest_longitude_match();
            }

        } catch (Exception fmterr) {
            // do nothing - possible report parse errors in debug mode.
            throw new NormalizationException("Format Error", fmterr);
        }

        if (!_parse_state) {
            throw new NormalizationException("Unable to parse match due to " + (islat ? "LAT" : "LON"));
        }

        set_normalized_text();
        value = toDecimal();
    }

    /**
     * offsets for degree and hemisphere until this point are likely absolute within
     * a document Reset
     * them using this relative offset. Offsets will then be relative to text.
     *
     * @param matchStart offset of match into doc
     */
    public void setRelativeOffsets(int matchStart) {
        if (offsetDeg >= 0) {
            offsetDeg = offsetDeg - matchStart;

            // DEFAULT: is to set ordinate where degrees start.
            offsetOrdinate = offsetDeg;
        }
        // degreeOffset is not set?

        if (offsetHemi >= 0) {
            // Reset relative to start.
            offsetHemi = offsetHemi - matchStart;
            offsetOrdinate = Math.min(offsetHemi, offsetDeg);
        }
    }

    /**
     * @return true if match has Hemisphere
     */
    public boolean hasHemisphere() {
        return has_hemi;
    }

    /**
     * Get the cartesian value for this ordinate
     *
     * @return double
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
     * Get back a normalized version of what you found. Yield zero-padded version of
     * coordinate, e.g.,
     * if DMS xdeg M' SS" is parsed we want xxx:MM:SS fully padded
     * 45 33N-00811 given 45:33.00811 normalized, DEG:MIN.mmmmm
     * Full format should be:
     * hDDD.ddd hDDD:MM.mmm hDDD:MM.mmm:SS.sss unlikely -- if this happens -- both
     * fractional minutes
     * and fractional seconds, pattern has errors hDDD:MM:SS.sss
     * .ddd, .mmm, .sss --- as many decimal places as there are h --- +/- hemisphere
     */
    private void set_normalized_text() {

        String d = normalizedValues.get("deg");
        String m = normalizedValues.get("min");
        String fmin = normalizedValues.get("fmin");
        String s = normalizedValues.get("sec");
        String fsec = normalizedValues.get("fsec");

        StringBuilder buf = new StringBuilder();
        if (hemisphere.polarity == -1) {
            buf.append("-");
        } else {
            buf.append("+");
        }

        has_degrees = true;

        // This cascades -- degrees, assumed. Minutes and then possibly Seconds
        // but we won't ever have seconds w/out deg or minutes first.
        /*
         * if (this.is_latitude) { buf.append(field.format(this.degrees)); //
         * LAT degree } else { buf.append(field3.format(this.degrees)); // LON
         * degree }
         */
        buf.append(d); // LAT degree

        if (m == null) {
            // Degrees only or Decimal degrees. Quietly fail.
            this.text = buf.toString();
            return;
        }

        has_minutes = true;
        buf.append(":");
        // buf.append(fieldDec.format(minutes));
        buf.append(m);

        // has_seconds is not reliable. fractional minutes may be part of
        // minutes field

        // Reconstituing fractional minutes or seconds
        // decimal part may have been separated from minutes, so it is likely
        // minutes is int, and fmin is another number.
        // TODO: formatting logic could be cleaner here.
        //
        if (fmin != null) {
            has_seconds = true; // Seconds precision in the form of fractional minutes.
            buf.append(fmin); // append text as-is. No arithmetic.
        }

        // TODO: either minutes is decimal, OR fmin is available OR seconds are available.
        if (s != null) {
            has_seconds = true;
            buf.append(":");
            buf.append(s);

            if (fsec != null) {
                buf.append(fsec);
            }
        }

        this.text = buf.toString();
    }

    private void saveField(String fieldNorm, String val) {
        normalizedValues.put(fieldNorm, val);
    }

    /**
     * @param f
     * @param norm
     * @return
     */
    private Integer getIntValue(String f, String norm) {
        String val = fieldValues.get(f);
        if (val == null) {
            return null;
        }

        saveField(norm, val);
        return Integer.valueOf(val);
    }

    /**
     * Convert numbers like "8.888" or "8-888" to decimal numbers.
     */
    private Float getDecimalValue(String f, String norm) {
        String val = fieldValues.get(f);
        if (val == null) {
            return null;
        }
        if (val.contains("-")) {
            // Log this situation
            val = val.replaceFirst("-", ".");
        }
        saveField(norm, val);
        return Float.valueOf(val);
    }

    /**
     * As a result of this, the fsec field text will be saved with decimal point.
     * Convert "-888" to the
     * decimal part of a value, e.g., "0.888"
     */
    private Float getFractionValue(String f, String norm) {
        String val = fieldValues.get(f);
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

    public static final String[] degLatFields = {"degLat", "dmsDegLat", "decDegLat"};
    public static final String[] degLonFields = {"degLon", "dmsDegLon", "decDegLon"};

    private void findDegreeOffset(String[] fieldNames) {
        /*
         * Helpful for determining where the distinct coordinates start in the text.
         * Usually, Degrees are first.
         */
        for (String f : fieldNames) {
            TextEntity val = fields.get(f);
            if (val != null) {
                offsetDeg = val.start;
                return;
            }
        }
    }

    /**
     * This should cascade.
     *
     * <pre>
     * IF Degrees specified,
     *    then if minutes,
     *       then if seconds...
     * </pre>
     *
     * It would be difficult to skip minutes if DEG and SEC are only fields present.
     *
     * @return true if Latitude fields parse properly
     */
    public boolean digest_latitude_match() {

        findDegreeOffset(degLatFields);

        // DEGREES
        Integer deg = getIntValue("degLat", "deg");
        Integer deg2 = getIntValue("dmsDegLat", "deg");
        Float deg3 = getDecimalValue("decDegLat", "deg");
        // Default Res is DEG
        if (deg != null) {
            degrees = deg;
        } else if (deg2 != null) {
            degrees = deg2;
        } else if (deg3 != null) {
            degrees = deg3;
            specificity = Resolution.SUBDEG; // Decimal degree
        } else {
            return false;
        }

        // MINUTES
        Integer min = getIntValue("minLat", "min");
        Integer min2 = getIntValue("dmsMinLat", "min");
        Float min3 = getDecimalValue("decMinLat", "min");
        Float min3dash = getDecimalValue("decMinLat3", "min");

        if (min != null) {
            minutes = min;
        } else if (min2 != null) {
            minutes = min2;
        } else if (min3 != null) {
            minutes = min3;
        } else if (min3dash != null) {
            minutes = min3dash;
        }

        // minutes is unset? fail.
        if (minutes >= 0) {
            specificity = Resolution.MIN;

            Float min_fract = getFractionValue("fractMinLat", "fmin");
            Float min_fract2 = getFractionValue("fractMinLat3", "fmin");
            // variation 2, is a 3-digit or longer fraction

            if (min_fract != null || min_fract2 != null) {
                specificity = Resolution.SUBMIN;
                if (min_fract != null) {
                    minutes += min_fract;
                } else if (min_fract2 != null) {
                    minutes += min_fract2;
                }
            }
        }

        // SECONDS
        Integer sec = getIntValue("secLat", "sec");
        Integer sec2 = getIntValue("dmsSecLat", "sec");

        if (sec != null) {
            seconds = sec;
        } else if (sec2 != null) {
            seconds = sec2;
        }

        if (seconds >= 0) {

            specificity = Resolution.SEC;

            Float fsec = getFractionValue("fractSecLat", "fsec");
            Float fsec2 = getFractionValue("fractSecLatOpt", "fsec");
            if (fsec != null || fsec2 != null) {
                specificity = Resolution.SUBSEC;
                if (fsec != null) {
                    seconds += fsec;
                } else if (fsec2 != null) {
                    seconds += fsec2;
                }
            }
        }

        return true;
    }

    /**
     * This is a copy of the logic for digest_latitude_match; All I replace is "Lat"
     * with "Lon"
     *
     * @return true if longitude fields parse properly
     */
    public boolean digest_longitude_match() {
        findDegreeOffset(degLonFields);
        // DEGREES
        Integer deg = getIntValue("degLon", "deg");
        Integer deg2 = getIntValue("dmsDegLon", "deg");
        Float deg3 = getDecimalValue("decDegLon", "deg");
        if (deg != null) {
            degrees = deg;
        } else if (deg2 != null) {
            degrees = deg2;
        } else if (deg3 != null) {
            degrees = deg3;
            specificity = Resolution.SUBDEG; // Decimal degree
        } else {
            return false;
        }

        // MINUTES
        Integer min = getIntValue("minLon", "min");
        Integer min2 = getIntValue("dmsMinLon", "min");
        Float min3 = getDecimalValue("decMinLon", "min");
        Float min3dash = getDecimalValue("decMinLon3", "min");

        if (min != null) {
            minutes = min;
        } else if (min2 != null) {
            minutes = min2;
        } else if (min3 != null) {
            minutes = min3;
        } else if (min3dash != null) {
            minutes = min3dash;
        }

        if (minutes >= 0) {
            specificity = Resolution.MIN;
            Float min_fract = getFractionValue("fractMinLon", "fmin");
            Float min_fract2 = getFractionValue("fractMinLon3", "fmin");
            // variation 2, is a 3-digit or longer fraction

            if (min_fract != null || min_fract2 != null) {
                specificity = Resolution.SUBMIN;
                if (min_fract != null) {
                    minutes += min_fract;
                } else if (min_fract2 != null) {
                    minutes += min_fract2;
                }
            }
        }

        // SECONDS
        Integer sec = getIntValue("secLon", "sec");
        Integer sec2 = getIntValue("dmsSecLon", "sec");

        if (sec != null) {
            seconds = sec;
        } else if (sec2 != null) {
            seconds = sec2;
        }

        if (seconds >= 0) {

            specificity = Resolution.SEC;
            Float fsec = getFractionValue("fractSecLon", "fsec");
            Float fsec2 = getFractionValue("fractSecLonOpt", "fsec");

            if (fsec != null || fsec2 != null) {
                specificity = Resolution.SUBSEC;
                if (fsec != null) {
                    seconds += fsec;
                } else if (fsec2 != null) {
                    seconds += fsec2;
                }
            }
        }

        return true;
    }

    /**
     * Normalize a hemisphere pattern: +, -, NSEW, which may either be preceeding
     * coordinate or after
     * it. As well, it may be optional, so lack of a hemisphere may match to null.
     * In which case, --
     * NO_HEMISPHERE -- caller should assume POSITIVE hemisphere is implied. further
     * filtering by caller
     * is warranted.
     *
     * @param t        raw text of match
     * @param elements fields
     * @param islat    true if match represents latitude
     */
    void normalize_hemisphere(String t, java.util.Map<String, String> elements, boolean islat) {

        int hemi = (islat ? getLatHemisphereSign(elements) : getLonHemisphereSign(elements));

        if (!hemisphere.isAlpha()) {
            coordinateSymbol = hasCoordinateSymbols(t);
        }

        has_hemi = hemi != NO_HEMISPHERE_VALUE;

        if (!has_hemi) {
            hemi = POS_HEMI; // Un-specified hemisphere defaults to POSITIVE
            /*
             * if (hasSymbols()) { hemi = POS_HEMI; // Un-specified hemisphere
             * defaults to POSITIVE } else { hemi = POS_HEMI; }
             */
        }

        hemisphere.polarity = hemi; // -1 or 1 only.
    }

    /**
     * @return true if match has symbols
     */
    public boolean hasSymbols() {
        return coordinateSymbol != null;
    }

    /**
     *
     */
    public static final String[] COORDINATE_SYMBOLS = {"°", "º", "'", "\"", ":", "lat", "lon", "geo", "coord", "deg"};

    private String coordinateSymbol = null;

    /**
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
    public static final String WEST = "W";
    /**
     */
    public static final String SOUTH = "S";
    /**
     */
    public static final String NORTH = "N";
    /**
     */
    public static final String EAST = "E";
    /**
     */
    public static final String NEGATIVE = "-";
    /**
     */
    public static final String POSITIVE = "+";
    /**
     */
    public static final int NO_HEMISPHERE = 0;
    /**
     */
    public static final int NO_HEMISPHERE_VALUE = -0x10;
    /**
     */
    public static final int POS_HEMI = 1;
    /**
     */
    public static final int NEG_HEMI = -1;
    /**
     */
    public static final Map<String, Integer> HEMI_MAP = new HashMap<>();

    static {

        HEMI_MAP.put(WEST, -1);
        HEMI_MAP.put(SOUTH, -1);
        HEMI_MAP.put(EAST, 1);
        HEMI_MAP.put(NORTH, 1);
        HEMI_MAP.put(NEGATIVE, -1);
        HEMI_MAP.put(POSITIVE, 1);
    }

    /**
     * @param hemi pos/neg number 1 or -1
     * @return +/- symbol
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
     * @param val +/- sign
     * @return 1 or -1 polarity
     */
    public static int get_hemisphere_sign(String val) {
        if (val == null) {
            return NO_HEMISPHERE_VALUE;
        }
        Integer s = HEMI_MAP.get(val.trim().toUpperCase());
        if (s != null) {
            return s;
        }
        return NO_HEMISPHERE;
    }

    public static final String[] hemiLatFields = {"hemiLat", "hemiLatSign", "hemiLatPre"};

    public static final String[] hemiLonFields = {"hemiLon", "hemiLonSign", "hemiLonPre"};

    int offsetHemi = -1;

    private void findHemiOffset(String[] fieldNames) {
        /*
         * Helpful for determining where the distinct coordinates start in the text.
         * Usually, Degrees are first.
         */
        for (String f : fieldNames) {
            TextEntity val = fields.get(f);
            if (val != null) {
                offsetHemi = val.start;
                return;
            }
        }
    }

    /**
     * @see #getLonHemisphereSign(Map)
     * @param elements list of trimmed components of the match
     * @return polarity of hemisphere from fields
     */
    public int getLatHemisphereSign(java.util.Map<String, String> elements) {

        findHemiOffset(hemiLatFields);

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
     * hemiLon W, E group used mostly for DMS, DM, DD formats hemiLonSign +, - group
     * allowed only for
     * specific formats; +/- may appear before any number not just coords.
     * hemiLonPre W, E, +, -
     *
     * @param elements list of trimmed components of the match
     * @return polarity of hemisphere from fields
     */
    public int getLonHemisphereSign(java.util.Map<String, String> elements) {
        findHemiOffset(hemiLonFields);

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
     * @deprecated For XCoord purposes use the DMSOrdinate(map, map, boolean, text)
     *             form.
     * @param deg       degrees
     * @param min       minutes
     * @param sec       seconds
     * @param islat     true if match is latitude
     * @param hemi_sign polarity of hemisphere
     */
    @Deprecated
    public DMSOrdinate(int deg, int min, int sec, boolean islat, int hemi_sign) {
        degrees = deg;
        minutes = min;
        seconds = sec;
        isLatitude = islat;
        hemisphere.polarity = hemi_sign;

        value = toDecimal();
    }

    /**
     * Construct a lat or lon ordinate from given values.
     *
     * @deprecated For XCoord purposes use the DMSOrdinate(map, map, boolean, text)
     *             form.
     * @param deg       degrees
     * @param min       minutes
     * @param sec       seconds
     * @param msec      millis
     * @param islat     true if match is latitude
     * @param hemi_sign polarity of hemisphere
     */
    @Deprecated
    public DMSOrdinate(int deg, int min, int sec, int msec, boolean islat, int hemi_sign) {
        this(deg, min, sec, islat, hemi_sign);
        if (msec > 0) {
            seconds += 0.001 * msec;
        }
        value = toDecimal();
    }

    /**
     * Construct a lat or lon ordinate from given values.
     *
     * @deprecated For XCoord purposes use the DMSOrdinate(map, map, boolean, text)
     *             form.
     * @param deg       degrees
     * @param min       minutes
     * @param sec       seconds
     * @param islat     isLat
     * @param hemi_sign polarity
     * @throws java.text.ParseException on parsing error or if values are null
     */
    @Deprecated
    public DMSOrdinate(String deg, String min, String sec, boolean islat, int hemi_sign)
            throws java.text.ParseException {
        if (deg == null) {
            throw new java.text.ParseException("Degrees may not be null", 1);
        }
        degrees = Integer.parseInt(deg);
        text = deg;

        isLatitude = islat;
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
     * toDD() Return the decimal value to the extent it makes sense. That is,
     * calculating minutes or
     * seconds where MIN or SEC fields are out of bounds for those units does not
     * make sense, return at
     * least Deg or Deg/Min.
     * Validation of the ordinate is a second step to be done by caller.
     *
     * @return
     */
    public double toDecimal() {

        if (minutes >= 60 || minutes < 0) {
            // This much we know, Degrees are valid?
            return (double) hemisphere.polarity * degrees;
        }
        if (seconds >= 60 || seconds < 0) {
            // At least you have degress and minutes, right?
            return (double) hemisphere.polarity * (degrees + (minutes / 60));
        }

        return (double) hemisphere.polarity * (degrees + (minutes / 60) + (seconds / 3600));
    }

    public boolean hasDegrees() {
        return specificity == Resolution.DEG || specificity == Resolution.SUBDEG;
    }

    public boolean hasSubDegrees() {
        return specificity == Resolution.SUBDEG;
    }

    public boolean hasMinutes() {
        return specificity == Resolution.MIN || specificity == Resolution.SUBMIN;
    }

    public boolean hasSubMinutes() {
        return specificity == Resolution.SUBMIN;
    }

    public boolean hasSeconds() {
        return specificity == Resolution.SEC || specificity == Resolution.SUBSEC;
    }

    public boolean hasSubSeconds() {
        return specificity == Resolution.SUBSEC;
    }

}
