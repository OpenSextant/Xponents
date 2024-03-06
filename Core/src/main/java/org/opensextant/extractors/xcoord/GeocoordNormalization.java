/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
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
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.MGRS;
import org.opensextant.geodesy.UTM;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.TextUtils;

/**
 *
 * @author ubaldino
 */
public final class GeocoordNormalization {

    private static final GeocoordMatchFilter MGRS_FILTER = new MGRSFilter();
    private static final GeocoordMatchFilter DMS_FILTER = new DMSFilter();

    private static final boolean DMLAT = true;
    private static final boolean DMLON = false;

    /**
     * The match object is normalized, setting the coord_text and other data from
     * parsing "text" and knowing which pattern family was matched.
     *
     * @param m      match
     * @param groups fields
     * @throws NormalizationException if entity or match is not parseable
     */
    public static void normalize_coordinate(GeocoordMatch m, Map<String, TextEntity> groups)
            throws NormalizationException {

        // Hoaky Java 6 issue: REGEX does not use named groups, so here we map both the value to
        // a text/offset pair (in groups) and provide just the key/text pair (_elements)
        //
        Map<String, String> fieldValues = new HashMap<>();
        for (String name : groups.keySet()) {
            TextEntity val = groups.get(name);
            fieldValues.put(name, val.getText());
        }

        m.precision.precision = PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION;

        // Extract m.regex_groups, and ordered list of group names
        // align with matcher.group(N)
        // value = matcher.group(N)
        // name = m.regex_groups.itemAt(N)
        //
        if (m.cce_family_id == XConstants.DD_PATTERN) {
            // get lat text
            // lon text -- remove whitespace from both
            // coord_text = lat + ' ' + lon
            // set lat, lon
            //
            // decDegLat, decDegLon, degSym, hemiLat, hemiLon
            //
            DMSOrdinate ddlat = new DMSOrdinate(groups, fieldValues, DMLAT, m.getText());
            DMSOrdinate ddlon = new DMSOrdinate(groups, fieldValues, DMLON, m.getText());

            // Yield a cooridnate-only version of text; "+42.4440 -102.3333"
            // preserving the innate precision given in the original text.
            //
            m.lat_text = ddlat.text;
            m.lon_text = ddlon.text;
            m.setSeparator(groups);
            m.setCoordinate(ddlat, ddlon);

            /*
             * DD filters enabled.
             * To Disable: XCoord.RUNTIME_FLAGS XOR XConstants.DD_FILTERS_ON
             */
            if ((XCoord.RUNTIME_FLAGS & XConstants.DD_FILTERS_ON) > 0) {
                /*
                 * With FILTERS ON if lat/lon have no ALPHA hemisphere, i.e., ENSW and if lat/lon text for match
                 * has no COORD symbology then this is likely not a DD coordinate -- filter out.
                 */
                if (!ddlon.hemisphere.isAlpha() && !ddlat.hemisphere.isAlpha()) {
                    if (!ddlat.hasSymbols()) {
                        m.setFilteredOut(true);
                    }
                }
            } else {
                // DD filters OFF, so do not filter out
                m.setFilteredOut(!GeodeticUtility.validateCoordinate(m.getLatitude(), m.getLongitude()));
            }

            m.coord_text = m.lat_text + " " + m.lon_text;
            set_precision(m);

        } else if (m.cce_family_id == XConstants.DM_PATTERN) {
            // get lat text
            // lon text -- remove whitespace from both
            // coord_text = lat + ' ' + lon
            // set lat, lon
            //
            DMSOrdinate dmlat = new DMSOrdinate(groups, fieldValues, DMLAT, m.getText());
            DMSOrdinate dmlon = new DMSOrdinate(groups, fieldValues, DMLON, m.getText());

            m.lat_text = dmlat.text;
            m.lon_text = dmlon.text;
            m.setSeparator(groups);
            m.setCoordinate(dmlat, dmlon);

            if (!m.isFilteredOut()) {
                m.setFilteredOut(m.evaluateInvalidDashes(fieldValues) || m.evaluateInvalidPunct(fieldValues));
            }
            m.coord_text = m.lat_text + " " + m.lon_text;
            set_precision(m);

        } else if (m.cce_family_id == XConstants.DMS_PATTERN) {
            // remove whitespace
            // set lat, lon
            //
            DMSOrdinate dmlat = new DMSOrdinate(groups, fieldValues, DMLAT, m.getText());
            DMSOrdinate dmlon = new DMSOrdinate(groups, fieldValues, DMLON, m.getText());

            m.lat_text = dmlat.text;
            m.lon_text = dmlon.text;
            m.setSeparator(groups);
            m.setCoordinate(dmlat, dmlon);

            if (!m.isFilteredOut()) {
                m.setFilteredOut(m.evaluateInvalidDashes(fieldValues) || m.evaluateInvalidPunct(fieldValues));
            }
            m.coord_text = m.lat_text + " " + m.lon_text;
            set_precision(m);

        } else if (m.cce_family_id == XConstants.MGRS_PATTERN) {

            // Capture the normalized coord text just to aid in reporting in
            // error situations
            //
            m.coord_text = TextUtils.delete_whitespace(m.getText());

            // TODO: make use of multiple answers.
            try {
                MGRS[] mgrs_candidates = MGRSParser.parseMGRS(m.getText(), m.coord_text, fieldValues);

                // Hopefully 1 candidate, but maybe 2 are found.
                // 1 is normal. 2 arise from having odd-digit offsets in
                // NorthingEasting
                //
                if (mgrs_candidates.length > 0) {
                    MGRS mgrs = mgrs_candidates[0];

                    m.coord_text = mgrs.toString();
                    Geodetic2DPoint pt = mgrs.toGeodetic2DPoint();
                    m.setLatitude(pt.getLatitudeAsDegrees());
                    m.setLongitude(pt.getLongitudeAsDegrees());

                    m.setBalanced(true);

                    if (mgrs_candidates.length == 2) {
                        mgrs = mgrs_candidates[1];
                        GeocoordMatch m2 = new GeocoordMatch(m.start, m.end);
                        m2.copy(m);

                        m2.coord_text = mgrs.toString();
                        pt = mgrs.toGeodetic2DPoint();
                        m2.setLatitude(pt.getLatitudeAsDegrees());
                        m2.setLongitude(pt.getLongitudeAsDegrees());
                        m2.setBalanced(true); // Really balanced?

                        m.addOtherInterpretation(m2);
                    }
                }
                String offsets = fieldValues.get("Easting_Northing");
                int len = offsets.length();
                if (len < 11) {
                    m.precision.precision = PrecisionScales.MGRS_offset_precision_list[len];
                    m.precision.digits = len / 2;
                }

            } catch (java.lang.IllegalArgumentException parseErr) {
                // .debug("Failed to parse MGRS pattern with text=" + m.getText() + " COORD?:"
                // + m.coord_text, parseErr);
                // No normalization was possible as this match represents an invalid MGRS value
                //
                m.setFilteredOut(true);
            } catch (Exception err) {
                throw new NormalizationException("Failed to parse MGRS", err);
            }

        } else if (m.cce_family_id == XConstants.UTM_PATTERN) {

            m.coord_text = TextUtils.delete_whitespace(m.getText());

            try {
                UTM utm = UTMParser.parseUTM(m.coord_text, fieldValues);
                if (utm != null) {
                    Geodetic2DPoint pt = utm.getGeodetic();
                    m.setLatitude(pt.getLatitudeAsDegrees());
                    m.setLongitude(pt.getLongitudeAsDegrees());
                    m.coord_text = utm.toString();
                    PrecisionScales.setUTMPrecision(m);
                }
            } catch (java.lang.IllegalArgumentException parseErr) {
                throw new NormalizationException(
                        String.format("Failed to parse UTM. text=%s coord=%s", m.getText(), m.coord_text), parseErr);
                // No normalization done.
            } catch (Exception err) {
                throw new NormalizationException("Failed to parse UTM pattern", err);
            }
        }
    }

    /**
     * Not all pattens might have filters. This "filter_out" implies you should
     * evaluate the
     * MatchFilter.stop() method on any implementation.
     *
     * @param m the match
     * @return true if match is invalid and should be marked as filtered out
     */
    public static boolean filter_out(GeocoordMatch m) {
        // Different reasons to filter out coordinate matches.

        if (m.isFilteredOut()) {
            // Earlier parsing and normalization filtered this match out.
            return true;
        }

        // At this point the XY = (0,0). This does not sound useful.
        //
        if (m.isZero()) {
            return true;
        }

        // A this point extractor does not have a valid coordinate text. This is
        // an engineering error.
        if (m.coord_text == null) {
            // The match parser has not generated a normalized version of the
            // coordinate text
            // filter out.
            return true;
        }

        if (m.cce_family_id == XConstants.MGRS_PATTERN) {
            // Apply MGRS filter -- IF static RUNTIME_FLAGS say it is enabled
            if ((XCoord.RUNTIME_FLAGS & XConstants.MGRS_FILTERS_ON) > 0) {
                return MGRS_FILTER.stop(m);
            }
        } else if (m.cce_family_id == XConstants.DMS_PATTERN) {
            // Apply DMS filter also only if static flags say it is enabled. 
            if ((XCoord.RUNTIME_FLAGS & XConstants.DMS_FILTERS_ON) > 0) {
                return DMS_FILTER.stop(m);
            }
        }
        // possibly others.
        return false;
    }

    /**
     * Hueuristic for what style of fields are allowed in valid DD or DM/DMS
     * coordinates. This evaluates
     * if a lat/lon pair have disparate field specificity. A lat with Deg:Min should
     * not be paired with
     * a lon with Deg:Min:Sec:Subsec for example.
     *
     * @param lat latitude
     * @param lon longitude
     * @return true if specifity of lat and lon are reasonable.
     */
    public static boolean evaluateSpecificity(DMSOrdinate lat, DMSOrdinate lon) {

        // Assess if matched pair has comparable field specificity
        //
        if (lat.hasSeconds() && lon.hasSeconds()) {
            return true;
        }
        if (lat.hasMinutes() && lon.hasMinutes()) {
            return true;
        }
        if (lat.hasDegrees() && lon.hasDegrees()) {
            return true;
        }

        // Mismatched. Degrees or subdegree fields may not be paired up with Second
        // and/or subsecond fields.
        //
        if (lat.hasDegrees() && lon.hasSeconds() || lon.hasSeconds() && lat.hasDegrees()) {
            return false;
        }

        // This would be non-sensical
        // DD.dd DDD MM.m
        if (lat.hasSubDegrees() && lon.hasSubMinutes()) {
            return false;
        }
        return !lon.hasSubDegrees() || !lat.hasSubMinutes();

        // Okay, you got here, you have possible SUBDEGRE
    }

    /**
     * set the precision on a match based on the situation (match + pattern).
     *
     * @param m match
     */
    public static void set_precision(GeocoordMatch m) {

        if (m.cce_family_id == XConstants.DD_PATTERN) {
            // Precision is based on # of digits in LAT text, e.g., #.### = 3
            // decimal places
            PrecisionScales.setDDPrecision(m);
        } else if (m.cce_family_id == XConstants.DM_PATTERN) {
            // Precision is based on # of digits in LAT text, e.g., ##:##.### =
            // Deg/Min with 3 decimal places
            // m.precision.precision = PrecisionScales.DM_precision(m.lat_text);
            PrecisionScales.setDMSPrecision(m);
        } else if (m.cce_family_id == XConstants.DMS_PATTERN) {
            // Precision is based on # of digits in LAT text, e.g., ##:##:## =
            // Deg/Min/Sec
            // m.precision.precision = PrecisionScales.DM_precision(m.lat_text);
            PrecisionScales.setDMSPrecision(m);
        } else if (m.cce_family_id == XConstants.MGRS_PATTERN) {
            // Precision is based on # of digits in northing or easting text,
            // e.g., ZZZQD NNNN EEEE = 100m precision
            PrecisionScales.setMGRSPrecision(m);
        } else if (m.cce_family_id == XConstants.UTM_PATTERN) {
            // Precision is based on # of digits in northing or easting text;
            PrecisionScales.setUTMPrecision(m);
        } else {
            m.precision.precision = PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION;
        }
    }
}
