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
 *
 * 
 * @author dlutz, MITRE  creator  (lutzdavp)
 * @author ubaldino, MITRE adaptor
 * @author swainza
 */
package org.mitre.xcoord;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.mitre.flexpat.*;
import org.mitre.opensextant.util.TextUtils;
import org.mitre.itf.geodesy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p> This is the culmination of various coordinate extraction efforts in
 * python and Java. This API poses no assumptions on input data or on execution.
 * </p>
 *
 * <p> Common Coordinate Enumeration (CCE) is a concept for enumerating the
 * coordinate representations. See XConstants for details.
 *
 * The basics of CCE include a family (DD, DMS, MGRS, etc.) and style (
 * enumerated in patterns config file). </p>
 *
 * <p> <ul> <b>Features of REGEX patterns file</b>: <li>DEFINE - a component of
 * a coord pattern to match</li> <li>RULE - a complete pattern to match</li>
 * <li>TEST - an example of the text the pattern should match in part or
 * whole.</li> </ul> </p>
 *
 * <p> <b>The Rules file</b> The Rules is an external text file containing rules
 * consisting of regular expressions used to identify geocoords. Below is an
 * example of what a simple rule might look like:
 *
 * <pre>
 * // Parts of a decimal degree Latitude/Longitude
 * #DEFINE	decDegLat	\d?\d\.\d{1,20}
 * #DEFINE	decDegLon	[0-1]?\d?\d\.\d{1,20}
 *
 * // TARGET: DD-xx, Decimal Deg, Preceding Hemisphere (a) H DD.DDDDDD° HDDD.DDDDDD°, optional deg symbol
 * #RULE   DD      01      &lt;hemiLatPre>\s?&lt;decDegLat>&lt;degSym>?\s*&lt;latlonSep>?\s*&lt;hemiLonPre>\s?&lt;decDegLon>&lt;degSym>?
 * #TEST   DD      01      N42.3, W102.4
 * </pre>
 *
 * Where the DEFINE statements relay fields that the PatternManager will recall
 * at runtime. The RULE is a composition of DEFINEs, other literals and regex
 * patterns. A rule must have a family and a rule ID within that family. And the
 * TEST statement (which is enumerated the same as the RULE family and ID). At
 * runtime all tests are further labeled with an incrementor, e.g. for TEST
 * "DD-01" might be the eighth test in the pattern file, so the test will be
 * labeled internally as DD-01#8.
 *
 * </p>
 *
 */
public final class PatternManager extends RegexPatternManager {

    protected Logger log;
    private final MatchFilter mgrs_filter = new MGRSFilter();
    private final MatchFilter dms_filter = new DMSFilter();
    private final TextUtils utility = new TextUtils();

    /**
     *
     * @param _patternfile
     * @throws MalformedURLException
     */
    public PatternManager(String _patternfile) throws MalformedURLException {
        super(_patternfile);
        log = LoggerFactory.getLogger(PatternManager.class);
    }

    /**
     *
     * @param _patternfile
     */
    public PatternManager(URL _patternfile) {
        super(_patternfile);
        log = LoggerFactory.getLogger(PatternManager.class);
    }
    /**
     *
     */
    public Map<Integer, Boolean> CCE_family_state = new HashMap<Integer, Boolean>();

    /**
     *
     * @throws IOException
     */
    public void initialization() throws IOException {
        super.initialize();
        if (debug) {
            log.debug(this.getConfigurationDebug());
        }
    }

    /**
     *
     * @param cce_fam 
     * @param enabled
     */
    public void enable_CCE_family(int cce_fam, boolean enabled) {
        CCE_family_state.put(cce_fam, enabled);

        // And re-set all such patterns.
        if (patterns_list.size() > 0) {
            for (RegexPattern repat : patterns_list) {

                GeocoordPattern pat = (GeocoordPattern) repat;
                // This seems like overkill, but just changing the states of 
                // patterns for the specified group of patterns.
                if (pat.cce_family_id == cce_fam) {
                    enable_pattern(pat);
                }
            }
        }
    }

    /**
     * enable an instance of a pattern based on the global settings.
     *
     * @param repat
     */
    @Override
    public void enable_pattern(RegexPattern repat) {
        GeocoordPattern p = (GeocoordPattern) repat;

        Boolean b = CCE_family_state.get(p.cce_family_id);
        if (b != null) {
            p.enabled = b.booleanValue();
        }
    }

    /**
     * Implementation must create a RegexPattern given the basic RULE define,
     * #RULE FAMILY RID REGEX PatternManager here adds compiled pattern and
     * DEFINES.
     *
     * @param fam
     * @param rule
     * @param desc
     * @return
     */
    @Override
    protected RegexPattern create_pattern(String fam, String rule, String desc) {
        return new GeocoordPattern(fam, rule, desc);
    }

    /**
     * Implementation has the option to check a pattern; For now invalid
     * patterns are only logged.
     *
     * @param repat
     * @return
     */
    @Override
    protected boolean validate_pattern(RegexPattern repat) {
        GeocoordPattern p = (GeocoordPattern) repat;
        if (p.cce_family_id == XConstants.UNK_PATTERN) {
            log.error("Invalid Pattern @ " + p.toString());
        }
        return (p.cce_family_id != XConstants.UNK_PATTERN);
    }

    /**
     * Implementation must create TestCases given the #TEST directive, #TEST RID
     * TID TEXT
     *
     * @param id
     * @param text
     * @param fam
     * @return
     */
    @Override
    protected PatternTestCase create_testcase(String id, String fam, String text) {
        return new TestCase(id, fam, text);
    }
    private final boolean DMLAT = true;
    private final boolean DMLON = false;

    /**
     * The match object is normalized, setting the coord_text and other data from 
     * parsing "text" and knowing which pattern family was matched.
     * 
     * @param m
     * @param groups
     * @return void
     * @throws XCoordException
     */
    public void normalize_coordinate(GeocoordMatch m, Map<String, String> groups)
            throws XCoordException {

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
                        m.filter_state = XConstants.FILTERED_OUT;
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
                    m.latitude = pt.getLatitudeAsDegrees();
                    m.longitude = pt.getLongitudeAsDegrees();

                    if (mgrs_candidates.length == 2) {
                        mgrs = mgrs_candidates[1];
                        GeocoordMatch m2 = new GeocoordMatch();
                        m2.copy(m);

                        m2.coord_text = mgrs.toString();
                        pt = mgrs.toGeodetic2DPoint();
                        m2.latitude = pt.getLatitudeAsDegrees();
                        m2.longitude = pt.getLongitudeAsDegrees();

                        m.addOtherInterpretation(m2);
                    }
                }
            } catch (java.lang.IllegalArgumentException parseErr) {
                log.debug("Failed to parse MGRS pattern with text=" + m.getText() + " COORD?:" + m.coord_text, parseErr);
                // No normalization done.
            } catch (Exception err) {
                throw new XCoordException("Failed to parse MGRS", err);
            }

        } else if (m.cce_family_id == XConstants.UTM_PATTERN) {

            m.coord_text = utility.delete_whitespace(m.getText());

            try {
                UTM utm = UTMParser.parseUTM(m.coord_text, groups);
                if (utm != null) {
                    Geodetic2DPoint pt = utm.getGeodetic();
                    m.latitude = pt.getLatitudeAsDegrees();
                    m.longitude = pt.getLongitudeAsDegrees();

                    m.coord_text = utm.toString();
                }
            } catch (java.lang.IllegalArgumentException parseErr) {
                log.debug("Failed to parse UTM pattern with text=" + m.getText() + " COORD?:" + m.coord_text, parseErr);
                // No normalization done.
            } catch (Exception err) {
                throw new XCoordException("Failed to parse UTM pattern", err);
            }
        }

        // return m.coord_text;
    }

    /**
     * Not all pattens might have filters. This "filter_out" implies you should
     * evaluate the MatchFilter.stop() method on any implementation.
     *
     * @param m
     * @return
     */
    public boolean filter_out(GeocoordMatch m) {
        // Different reasons to filter out coordinate matches.

        if (m.isFilteredOut()) {
            // Earlier parsing and normalization filtered this match out.
            return true;
        }

        // At this point the XY = (0,0).  This does not sound useful.
        // 
        if (m.latitude == 0 && m.longitude == 0) {
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
    public void set_precision(GeocoordMatch m) {

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
