/**
 *
 *  Copyright 2012-2013 The MITRE Corporation.
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
 * @author mubaldino
 */
package org.opensextant.extractors.xcoord;

import java.util.Map;

import org.opensextant.extraction.NormalizationException;
import org.opensextant.util.TextUtils;
import org.opensextant.geodesy.*;

/**
 *
 */
public final class GeocoordNormalization  {

    private static final MatchFilter mgrs_filter = new MGRSFilter();
    private static final MatchFilter dms_filter = new DMSFilter();

    private static final boolean DMLAT = true;
    private static final boolean DMLON = false;

    /**
     * The match object is normalized, setting the coord_text and other data from
     * parsing "text" and knowing which pattern family was matched.
     *
     * @param m
     * @param groups
     * @return void
     * @throws XCoordException
     */
    public static void normalize_coordinate(GeocoordMatch m, Map<String, String> groups)
            throws NormalizationException {

        // Extract m.regex_groups, and ordered list of group names
        //         align with matcher.group(N)
        //    value = matcher.group(N)
        //    name  = m.regex_groups.itemAt(N)
        //
        if (m.cce_family_id == XConstants.DD_PATTERN) {
            // get lat text
            //     lon text  -- remove whitespace from both
            // coord_text = lat + ' ' + lon
            // set lat, lon
            //
            //  decDegLat, decDegLon, degSym, hemiLat, hemiLon
            //
            DMSOrdinate ddlat = new DMSOrdinate(
                    groups, DMLAT, m.getText());

            DMSOrdinate ddlon = new DMSOrdinate(
                    groups, DMLON, m.getText());

            //m.setLatitude(groups.get("decDegLat"), lat_hemi);
            //m.setLongitude(groups.get("decDegLon"), lon_hemi);
            // Yield a cooridnate-only version of text; "+42.4440 -102.3333"
            // preserving the innate precision given in the original text.
            //

            m.lat_text = ddlat.text;
            m.lon_text = ddlon.text;
            m.setCoordinate(ddlat, ddlon);

            /**
             *  DD filters enabled.
             *     Disable:
             *        XCoord.RUNTIME_FLAGS XOR XConstants.DD_FILTERS_ON
             */
            if ((XCoord.RUNTIME_FLAGS & XConstants.DD_FILTERS_ON) > 0) {
                /**
                 * With FILTERS ON
                 *    if lat/lon have no ALPHA hemisphere -- ENSW
                 *        and if lat/lon text for match has no COORD symbology
                 *             then this is likely not a DD coordinate -- filter out.
                 */
                if (!ddlon.hemisphere.isAlpha() && !ddlat.hemisphere.isAlpha()) {
                    if (!ddlat.hasSymbols()) {
                        m.setFilteredOut(true);
                    }
                }
            }

            m.coord_text = m.lat_text + " " + m.lon_text;

        } else if (m.cce_family_id == XConstants.DM_PATTERN) {
            // get lat text
            //     lon text  -- remove whitespace from both
            // coord_text = lat + ' ' + lon
            // set lat, lon
            //
            DMSOrdinate dmlat = new DMSOrdinate(
                    groups, DMLAT, m.getText());

            DMSOrdinate dmlon = new DMSOrdinate(
                    groups, DMLON, m.getText());

            m.lat_text = dmlat.text;
            m.lon_text = dmlon.text;
            m.setCoordinate(dmlat, dmlon);

            m.coord_text = m.lat_text + " " + m.lon_text;

        } else if (m.cce_family_id == XConstants.DMS_PATTERN) {
            // remove whitespace
            // set lat, lon
            //
            DMSOrdinate dmlat = new DMSOrdinate(
                    groups, DMLAT, m.getText());

            DMSOrdinate dmlon = new DMSOrdinate(
                    groups, DMLON, m.getText());

            m.lat_text = dmlat.text;
            m.lon_text = dmlon.text;
            m.setCoordinate(dmlat, dmlon);

            m.coord_text = m.lat_text + " " + m.lon_text;

        } else if (m.cce_family_id == XConstants.MGRS_PATTERN) {

            // Capture the normalized coord text just to aid in reporting in error situations
            //
            m.coord_text = TextUtils.delete_whitespace(m.getText());

            // TODO:  make use of multiple answers.
            try {
                MGRS[] mgrs_candidates = MGRSParser.parseMGRS(m.getText(), m.coord_text, groups);

                // Hopefully 1 candidate, but maybe 2 are found.
                // 1 is normal.  2 arise from having odd-digit offsets in NorthingEasting
                //
                if (mgrs_candidates != null) {
                    MGRS mgrs = mgrs_candidates[0];

                    m.coord_text = mgrs.toString();
                    Geodetic2DPoint pt = mgrs.toGeodetic2DPoint();
                    m.setLatitude( pt.getLatitudeAsDegrees() );
                    m.setLongitude( pt.getLongitudeAsDegrees() );

                    if (mgrs_candidates.length == 2) {
                        mgrs = mgrs_candidates[1];
                        GeocoordMatch m2 = new GeocoordMatch();
                        m2.copy(m);

                        m2.coord_text = mgrs.toString();
                        pt = mgrs.toGeodetic2DPoint();
                        m2.setLatitude( pt.getLatitudeAsDegrees() );
                        m2.setLongitude( pt.getLongitudeAsDegrees() );

                        m.addOtherInterpretation(m2);
                    }
                }
            } catch (java.lang.IllegalArgumentException parseErr) {
                throw new NormalizationException("Failed to parse MGRS pattern with text=" + m.getText() + " COORD?:" + m.coord_text, parseErr);
                // No normalization done.
            } catch (Exception err) {
                throw new NormalizationException("Failed to parse MGRS", err);
            }

        } else if (m.cce_family_id == XConstants.UTM_PATTERN) {

            m.coord_text = TextUtils.delete_whitespace(m.getText());

            try {
                UTM utm = UTMParser.parseUTM(m.coord_text, groups);
                if (utm != null) {
                    Geodetic2DPoint pt = utm.getGeodetic();
                    m.setLatitude( pt.getLatitudeAsDegrees() );
                    m.setLongitude( pt.getLongitudeAsDegrees() );

                    m.coord_text = utm.toString();
                }
            } catch (java.lang.IllegalArgumentException parseErr) {
                throw new NormalizationException("Failed to parse UTM pattern with text=" + m.getText() + " COORD?:" + m.coord_text, parseErr);
                // No normalization done.
            } catch (Exception err) {
                throw new NormalizationException("Failed to parse UTM pattern", err);
            }
        }
    }

    /**
     * Not all pattens might have filters. This "filter_out" implies you should
     * evaluate the MatchFilter.stop() method on any implementation.
     *
     * @param m
     * @return
     */
    public static boolean filter_out(GeocoordMatch m) {
        // Different reasons to filter out coordinate matches.

        if (m.isFilteredOut()) {
            // Earlier parsing and normalization filtered this match out.
            return true;
        }

        // At this point the XY = (0,0).  This does not sound useful.
        //
        if (m.isZero()) {
            return true;
        }

        // A this point extractor does not have a valid coordinate text.  This is an engineering error.
        if (m.coord_text == null) {
            // The match parser has not generated a normalized version of the coordinate text
            // filter out.
            return true;
        }

        // Apply MGRS filter  -- IF static RUNTIME_FLAGS say it is enabled
        if (m.cce_family_id == XConstants.MGRS_PATTERN) {
            if ((XCoord.RUNTIME_FLAGS & XConstants.MGRS_FILTERS_ON) > 0) {
                return mgrs_filter.stop(m);
            }
        } /**  Apply DMS filter also only if static flags say it is enabled.
         */
        else if (m.cce_family_id == XConstants.DMS_PATTERN) {
            if ((XCoord.RUNTIME_FLAGS & XConstants.DMS_FILTERS_ON) > 0) {
                return dms_filter.stop(m);
            }
        }
        // possibly others.
        return false;
    }

    /**
     *
     * @param m
     */
    public static void set_precision(GeocoordMatch m) {

        if (m.cce_family_id == XConstants.DD_PATTERN) {
            // Precision is based on # of digits in LAT text, e.g., #.### = 3 decimal places
            PrecisionScales.setDDPrecision(m);
        } else if (m.cce_family_id == XConstants.DM_PATTERN) {
            // Precision is based on # of digits in LAT text, e.g., ##:##.### = Deg/Min with 3 decimal places
            //m.precision.precision = PrecisionScales.DM_precision(m.lat_text);
            PrecisionScales.setDMSPrecision(m);
        } else if (m.cce_family_id == XConstants.DMS_PATTERN) {
            // Precision is based on # of digits in LAT text, e.g., ##:##:## = Deg/Min/Sec
            //m.precision.precision = PrecisionScales.DM_precision(m.lat_text);
            PrecisionScales.setDMSPrecision(m);
        } else if (m.cce_family_id == XConstants.MGRS_PATTERN) {
            // Precision is based on # of digits in northing or easting text, e.g., ZZZQD NNNN EEEE = 100m precision
            PrecisionScales.setMGRSPrecision(m);
        } else if (m.cce_family_id == XConstants.UTM_PATTERN) {
            // Precision is based on # of digits in northing or easting text;
            PrecisionScales.setUTMPrecision(m);
        } else {
            m.precision.precision = PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION;
        }
    }
}
