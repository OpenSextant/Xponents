/**
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
package org.opensextant.extractors.xcoord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.Country;
import org.opensextant.data.Geocoding;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.extraction.TextEntity;
import org.opensextant.extraction.TextMatch;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.geodesy.MGRS;
import org.opensextant.util.GeodeticUtility;

/**
 * GeocoordMatch holds all the annotation data for the actual raw and normalized
 * coordinate.
 *
 * @see org.opensextant.extraction.TextMatch base class
 * @author ubaldino
 */
public class GeocoordMatch extends TextMatch implements Geocoding {

    /**
     * Just the coordinate text normalized
     */
    protected String coord_text = null; // MGRS, UTM, all, etc.
    /**
     */
    protected String lat_text = null; // just DD, DMS, DM
    /**
     */
    protected String lon_text = null; // ditto
    /**
     * decimal latitude w/sign
     */
    private double latitude = 0.0f;
    /**
     * decimal longitude w/sign
     */
    private double longitude = 0.0f;
    private DMSOrdinate lat = null;
    private DMSOrdinate lon = null;
    /**
     */
    public int cce_family_id = -1;
    /**
     */
    public String cce_variant = null;
    /**
     * inherent precision of the coordinate matched
     */
    public GeocoordPrecision precision = new GeocoordPrecision();
    private MGRS mgrs = null;
    private String gridzone = null;
    /**
     * TODO: improve parsing of matching fields, e.g., 1N 2deg 3"W Or use to
     * detect imbalanced matches where punctuation like dashes are give aways:
     * -1 34-32-55
     */
    private boolean balancedPrecision = false;

    /** count dashes other than hemispheres, +/- */
    protected int dashCount = 0;

    /**
     * Optional: country metadata.
     */
    private String countryCode = null;

    /**
     * a TextMatch that represents a coordinate found in free text.
     */
    public GeocoordMatch() {
        // populate attrs as needed;
        this.type = "coord";
        this.producer = "XCoord";
    }

    /**
     * Convenience method for determining if XY = 0,0.
     *
     * @return true, if is zero
     */
    public boolean isZero() {
        return (latitude == 0 && longitude == 0);
    }

    /**
     * Allow pattern rules to determine by any means if match is balanced.
     *
     * @param b
     *            if coordinate has balanced data/resolution e.g., MGRS with
     *            even digits of offset
     */
    public void setBalanced(boolean b) {
        balancedPrecision = b;
    }

    /**
     * Checks if is balanced.
     *
     * @return true, if is balanced
     */
    public boolean isBalanced() {
        return balancedPrecision;
    }

    /**
     * If you are given a vetted XY, use that.
     * 
     * @param yx
     *            latlon
     */
    public void setLatLon(LatLon yx) {
        if (yx != null) {
            this.setLatitude(yx.getLatitude());
            this.setLongitude(yx.getLongitude());
        }
    }

    /**
     * GeoBase interface.
     * 
     * @return null always. The raw matched object does not automatically
     *         provide an admin name. Override if other behavior is desired.
     */
    @Override
    public String getAdminName() {
        return null;
    }

    /**
     *
     * @param m
     *            the match to copy
     */
    public void copyMetadata(GeocoordMatch m) {

        this.copy(m);

        // XCoord-specific meta-data.
        // with the exception of geodetic data.
        this.setFilteredOut(m.isFilteredOut());
        this.cce_family_id = m.cce_family_id;
        this.precision = m.precision;

        // The above was added to allow other interpretations
        // to be added to a primary interpretation.
        // The stuff that varies is: coord_text, lat, lon
        // The invariant stuff is above: matching metadata.
        //
        // this.coord_text = m.coord_text;
    }

    /**
     * Set the ordinates back on the match; general filters are assessed.
     *
     * @param _lat
     *            lat obj
     * @param _lon
     *            lon obj
     */
    public void setCoordinate(DMSOrdinate _lat, DMSOrdinate _lon) {

        lat = _lat;
        lon = _lon;

        this.latitude = lat.getValue();
        this.longitude = lon.getValue();

        lat.setRelativeOffsets(start);
        lon.setRelativeOffsets(start);
        setRelativeOffset(start);

        if (!_lat.hasHemisphere() && !_lon.hasHemisphere() && !_lat.hasSymbols()) {
            // This coordinate has no hemisphere at all. Possible a bare pare of
            // floating point numbers?
            //
            this.setFilteredOut(true);
        } else {
            // This coordinate has an invalid lat or lon
            //
            this.setFilteredOut(!GeodeticUtility.validateCoordinate(latitude, longitude));
        }

        if (!this.isFilteredOut()) {
            // If not yet filtered out for some other reason, evaluate the
            // fields in the matched YX pair
            // If a degree lat is paired up with sub-second longitude then you
            // have a dire mismatch.
            //
            this.setFilteredOut(!GeocoordNormalization.evaluateSpecificity(lat, lon));
        }
    }

    public final String[] separators = { "latlonSepNoDash", "latlonSep", "xySep", "trivialSep" };
    protected int offsetSeparator = -1;
    protected String separator = null;

    /**
     *
     * @param fields
     *            regex fields to search
     */
    protected void setSeparator(Map<String, TextEntity> fields) {
        for (String k : separators) {
            TextEntity val = fields.get(k);
            if (val != null) {
                offsetSeparator = val.start;
                separator = val.getText();
                return;
            }
        }
    }

    /**
     * Note: this should only be called once.
     * 
     * @param s
     *            offset position
     */
    protected void setRelativeOffset(int s) {
        if (offsetSeparator >= 0) {
            offsetSeparator = offsetSeparator - s;
        }
    }

    /**
     * Evaluate DMS patterns only... evaluate if match contains dashes as field
     * separators or as hemispheres. Or both. If match contains dash sep on lat,
     * but not in lon, then the match is invalid. This suggests the match is not
     * a geocoordinate.
     *
     * @return true if coordinate is invalid because
     *
     * @throws NormalizationException
     */
    public boolean evaluateInvalidDashes() throws NormalizationException {
        if (lat == null || lon == null) {
            throw new NormalizationException("Set lat/lon first before evaluating dashes");
        }
        if (lat.offsetOrdinate < 0 || lon.offsetOrdinate < 0) {
            throw new NormalizationException("Degree offsets my exist");
        }

        // Relative offsets to text. Given the match, find where the degree
        // starts.
        //
        // LAT / LON pairs -- where does lat start and end?
        //
        // D:M:S H sep D:M:S H
        // H D:M:S sep H D:M:S
        // Choose the end of the latitude text based on the hemisphere.
        // OR Based on the start of the longitude hemisphere
        int x2 = lon.offsetOrdinate; // Remains as-is, by default.

        if (offsetSeparator > 0) {
            // Offsets are regex char 1.. based or 0.. based?
            x2 = offsetSeparator - 1; // Exclude the offset; should remain
                                      // as-is;
        } else if (lat.offsetHemi > 0 && lat.offsetHemi > lat.offsetDeg) {
            x2 = lat.offsetHemi + 1; // Include the hemisphere for Lat. +1
        }

        // By this point x2 offset should be exclusive end of LAT
        String latText = getText().substring(lat.offsetDeg, x2).trim();
        String lonText = getText().substring(lon.offsetOrdinate).trim();

        // TODO: This fails to work if "-" is used as a separator, <Lat> - <Lon>
        // But in certain situations it is helpful to know if dash as field
        // separators can reveal a false positive
        // for example in scientific data that employs patterns that look like
        // DMS or DM lat/lon
        //
        int lat_dashes = StringUtils.countMatches(latText, "-");
        int lon_dashes = StringUtils.countMatches(lonText, "-");

        // ASSUMPTION: LON follows LAT, so where LAT, -LON
        // the "-" may be part of the LAT field.
        if (lon.hemisphere.symbol != null) {

            if ("-".equals(lon.hemisphere.symbol)) {
                --lon_dashes;
            }
        }

        // Dash count is even?
        return lat_dashes != lon_dashes;
        // Caller should override this assessment if counting dashes in lat or
        // lon is irrelevant.
    }

    /**
     *
     * @return if match has minutes
     */
    public boolean hasMinutes() {
        if (lat != null) {
            return lat.has_minutes;
        }
        return false;
    }

    /**
     *
     * @return if match has seconds
     */
    public boolean hasSeconds() {
        if (lat != null) {
            return lat.has_seconds;
        }
        return false;
    }

    /**
     *
     * @param decval
     *            decimal lat
     */
    public void setLatitude(String decval) {
        lat_text = decval;
        latitude = Double.parseDouble(decval);
    }

    /**
     *
     * @param decval
     *            decimal lon
     */
    public void setLongitude(String decval) {
        lon_text = decval;
        longitude = Double.parseDouble(decval);
    }

    /**
     *
     * @return formatted lat based on lat precision
     */
    public String formatLatitude() {
        return PrecisionScales.format(this.latitude, this.precision.digits);
    }

    /**
     *
     * @return formatted longitude base on lon precision
     */
    public String formatLongitude() {
        return PrecisionScales.format(this.longitude, this.precision.digits);
    }

    /**
     * Precision value is in Meters. No more than 0.001 METER is really relevant
     * -- since this is really information extraction it is very doubtful that
     * you will have any confidence about extraction millimeter accuracy.
     *
     *
     * @return string number of whole meters of precision
     */
    public String formatPrecision() {
        return "" + (int) precision.precision;
    }

    /**
     * @return int number of whole meters of precision
     */
    @Override
    public int getPrecision() {
        return (int) precision.precision;
    }

    public void setPrecision(int m) {
        precision.precision = m;
    }

    /**
     * Convert the current coordinate to MGRS
     *
     * @return string version of MGRS
     */
    public String toMGRS() {
        if (mgrs == null) {
            mgrs = new MGRS(new Longitude(longitude, Angle.DEGREES), new Latitude(latitude, Angle.DEGREES));
            gridzone = mgrs.toString().substring(0, 5);
        }
        return mgrs.toString();
    }

    /**
     * Identifies the 100KM quad in which this point is contained.
     *
     * @return GZ MGRS GZD and Quad prefix. This is a unique 100KM square.
     */
    public String gridzone() {
        // Ensure conversion internally has taken place.
        toMGRS();

        return gridzone;
    }

    protected List<GeocoordMatch> interpretations = null;

    /**
     * The current instance is the main match. But should you be able to parse
     * out additional interpretations, add them
     *
     * @param m2
     *            secondary match
     */
    public void addOtherInterpretation(GeocoordMatch m2) {
        if (interpretations == null) {
            interpretations = new ArrayList<GeocoordMatch>();
        }
        interpretations.add(m2);
    }

    /**
     * Checks for other interpretations.
     *
     * @return true, if original match has multiple
     *         interpretations/normalizations.
     */
    public boolean hasOtherIterpretations() {
        return (interpretations != null && !interpretations.isEmpty());
    }

    /**
     * @return list of other interpreted matches
     */
    public List<GeocoordMatch> getOtherInterpretations() {
        return interpretations;
    }

    // ************************************
    //
    // Geocoding Interface
    //
    // ************************************
    /**
     * @return true. a Coordinate is a place
     *
     */
    @Override
    public boolean isPlace() {
        return true;
    }

    /**
     * Note the coordinate nature of this TextMatch/Geocoding takes precedence
     * over other flags isPlace, isCountry, etc.
     *
     * @return true.
     */
    @Override
    public boolean isCoordinate() {
        return true;
    }

    /**
     * @return false. coordinates are not country objects.
     */
    @Override
    public boolean isCountry() {
        return false;
    }

    @Override
    public boolean isAdministrative() {
        return false;
    }

    /**
     * TOOD: convey a realistic confidence metric for what was actually matched.
     * e.g. MGRS confidence when there are multiple interpretations may result
     * in lower confidence or whenever the parser suspects there is a typo in
     * the match or if the match contains items that are characteristic of false
     * positives.
     * 
     * @return
     */
    public double getConfidence() {
        return 0.90;
    }

    /**
     * 
     */
    private Place relatedPlace = null;
    private Country country = null;

    public void setRelatedPlace(Place location) {
        relatedPlace = location;
    }

    public Place getRelatedPlace() {
        return relatedPlace;
    }

    /**
     * @return null unless related place is set, then country code is inferred
     *         from related place.
     */
    @Override
    public String getCountryCode() {
        if (relatedPlace != null) {
            return relatedPlace.getCountryCode();
        }
        if (countryCode != null) {
            return countryCode;
        }
        if (country != null) {
            return country.getCountryCode();
        }
        return null;
    }

    public void setCountryCode(String cc) {
        this.countryCode = cc;
    }

    public void setCountry(Country c) {
        this.country = c;
    }

    /**
     * @return null unless related place is set, then ADM1 code is inferred from
     *         related place.
     */
    @Override
    public String getAdmin1() {
        if (relatedPlace != null) {
            return relatedPlace.getAdmin1();
        }
        return null;
    }

    /**
     * @return null
     */
    @Override
    public String getAdmin2() {
        if (relatedPlace != null) {
            return relatedPlace.getAdmin2();
        }
        return null;
    }

    /**
     * @return "S"
     */
    @Override
    public String getFeatureClass() {
        return "S";
    }

    /**
     * @return "COORD"
     */
    @Override
    public String getFeatureCode() {
        return "COORD";
    }

    /**
     * @return "Place ID" -- normalized coordinate text
     */
    @Override
    public String getPlaceID() {
        return coord_text;
    }

    /**
     * @return the place name is the coordinate as specified by the original
     *         data.
     */
    @Override
    public String getPlaceName() {
        if (name != null) {
            return name;
        }

        return getText();
    }

    /**
     * name - an alternate name for this coordinate
     */
    private String name = null;

    public void setPlaceName(String n) {
        name = n;
    }

    /**
     * Returns the exact pattern that matched.
     *
     * @return Pattern ID
     */
    @Override
    public String getMethod() {
        return this.pattern_id;
    }

    /**
     * This reuses TextMatch.pattern_id attr; Use get/setMethod() or pattern_id
     * as needed.
     * 
     * @param patId
     *            pattern ID
     */
    public void setMethod(String patId) {
        pattern_id = patId;
    }

    /**
     * @return lat in degrees
     */
    @Override
    public double getLatitude() {
        return latitude;
    }

    /**
     * @return text of the latitude
     */
    public String getLatText() {
        return this.lat_text;
    }

    /**
     * @return text of the longitude
     */
    public String getLonText() {
        return this.lon_text;
    }

    /**
     * @return lon in degrees
     */
    @Override
    public double getLongitude() {
        return longitude;
    }

    /**
     * @param y
     *            decimal latitude
     */
    @Override
    public void setLatitude(double y) {
        this.latitude = y;
    }

    /**
     * @param x,
     *            decimal longitude
     */
    @Override
    public void setLongitude(double x) {
        this.longitude = x;
    }

    /**
     * Null implementation -- Geocoding interface. Coordinates from XCoord do
     * not automatically report name of admin boundaries.
     */
    @Override
    public String getAdmin1Name() {
        if (relatedPlace != null) {
            return relatedPlace.getAdmin1Name();
        }
        return null;
    }

    /**
     * Null implementation -- Geocoding interface Coordinates from XCoord do not
     * automatically report name of admin boundaries.
     */
    @Override
    public String getAdmin2Name() {
        if (relatedPlace != null) {
            return relatedPlace.getAdmin2Name();
        }
        return null;
    }

    /**
     * Create a Place version of this coordinate -- that is, once we've found
     * the coordinate match if the match data is no longer needed we can produce
     * a geodetic Place from the TextMatch. This is helpful when you are more
     * interested in the Place metadata, e.g. plot the TextMatch; enrich the
     * TextMatch, etc
     *
     * But note, if you need match confidence, match offsets, etc. you retain
     * this TextMatch instance
     */
    public Place asPlace() {
        Place p = new Place(null, getText());
        if (this.lat_text != null && this.lon_text != null) {
            p.setPlaceID(String.format("%s,%s", this.lat_text, this.lon_text));
        }
        p.setLatLon(this);
        /*
         * Not check if precision is -1, as this API never sets negative
         * precision.
         */
        p.setPrecision(precision.precision < 1 ? 1 : (int) precision.precision);
        p.setMethod(this.getMethod());

        p.setFeatureClass(getFeatureClass());
        p.setFeatureCode(getFeatureCode());
        p.setCountryCode(getCountryCode());
        p.setAdmin1(getAdmin1());

        return p;
    }

    public boolean hasCoordinate() {
        return GeodeticUtility.isValidNonZeroCoordinate(this.latitude, this.longitude);
    }

    private String admin1PostalCode = null;
    private String placePostalCode = null;

    /**
     * State-level postal code, the corresponds usually to ADM1
     */
    public String getAdmin1PostalCode() {
        return admin1PostalCode;
    }

    /**
     * City-level postal code, that may be something like a zip.
     * Thinking world-wide, not everyone calls these zipcodes, as in the US.
     */
    public String getPlacePostalCode() {
        return placePostalCode;
    }

    public void setAdmin1PostalCode(String c) {
        this.admin1PostalCode = c;
    }

    public void setPlacePostalCode(String c) {
        this.placePostalCode = c;
    }

}
