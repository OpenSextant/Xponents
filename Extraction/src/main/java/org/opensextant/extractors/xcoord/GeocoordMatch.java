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
package org.opensextant.extractors.xcoord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.geodesy.MGRS;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.extraction.TextEntity;
import org.opensextant.extraction.TextMatch;
import org.opensextant.data.Geocoding;
import org.opensextant.util.GeodeticUtility;

/**
 * GeocoordMatch holds all the annotation data for the actual raw and normalized
 * coordinate.
 * 
 * @see org.mitre.flexpat.TextMatch base class
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
     * @see XCoord extract_coordinates is only routine that populates this
     *      TextMatch
     */
    public GeocoordMatch() {
        // populate attrs as needed;
        this.type = "coord";
        this.producer = "XCoord";
    }

    /**
     * Convenience method for determining if XY = 0,0
     */
    public boolean isZero() {
        return (latitude == 0 && longitude == 0);
    }

    /** Allow pattern rules to determine by any means if match is balanced */
    public void setBalanced(boolean b) {
        balancedPrecision = b;
    }

    /** */
    public boolean isBalanced() {
        return balancedPrecision;
    }

    /**
     * 
     * @param m
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
     * Set the ordinates back on the match;  general filters are assessed.
     * 
     * @param _lat
     * @param _lon
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
            // If not yet filtered out for some other reason, evaluate the fields in the matched YX pair
            //  If a degree lat is paired up with sub-second longitude then you have a dire mismatch.
            // 
            this.setFilteredOut(! GeocoordNormalization.evaluateSpecificity( lat, lon));
        }
    }

    public final String[] separators = {"latlonSepNoDash","latlonSep", "xySep", "trivialSep"};
    protected int offsetSeparator = -1;
    protected String separator = null;
    
    /**
     * 
     * @param fields
     */
    protected void setSeparator(Map<String, TextEntity> fields){
        for (String k : separators){
            TextEntity val = fields.get(k);
            if (val!= null){
                offsetSeparator = val.start;
                separator = val.getText();
                return;
            }
        }
    }
    
    /**
     * TODO: this should only be called once.
     * @param s
     */
    protected void setRelativeOffset(int s){
        if (offsetSeparator>=0){
            offsetSeparator = offsetSeparator - s;
        }
    }
    /**
     * Evaluate DM/DMS patterns only...
     * 
     * @throws ExtractionException
     */
    public boolean evaluateDashes() throws NormalizationException{
        if (lat== null || lon == null){
            throw new NormalizationException("Set lat/lon first before evaluating dashes"); 
        }
        if (lat.offsetOrdinate < 0 || lon.offsetOrdinate < 0){
            throw new NormalizationException("Degree offsets my exist");             
        }
        
        // Relative offsets to text.  Given the match, find where the degree starts.
        // 
        // LAT / LON pairs -- where does lat start and end?
        // 
        //    D:M:S H sep D:M:S H
        //  H D:M:S sep H D:M:S
        // Choose the end of the latitude text based on the hemisphere.
        // OR  Based on the start of the longitude hemisphere        
        int x2 =  lon.offsetOrdinate;  // Remains as-is, by default. 
        
        if ( offsetSeparator > 0){            
            // Offsets are regex char 1.. based or 0.. based?
            x2 = offsetSeparator - 1;  // Exclude the offset; should remain as-is;
        } else if (lat.offsetHemi>0 && lat.offsetHemi>lat.offsetDeg){
            x2 = lat.offsetHemi + 1;  // Include the hemisphere for Lat. +1
        }
        
        String latText = getText().substring(lat.offsetDeg, x2).trim();  // By this point x2 offset should be exclusive end of LAT          
        String lonText = getText().substring(lon.offsetOrdinate).trim();
        
        // TODO: This fails to work if "-" is used as a separator, <Lat> - <Lon>
        // But in certain situations it is helpful to know if dash as field
        // separators can reveal a false positive
        // for example in scientific data that employs patterns that look like
        // DMS or DM lat/lon
        //
        int lat_dashes = StringUtils.countMatches(latText, "-");
        int lon_dashes = StringUtils.countMatches(lonText, "-");
        
        // ASSUMPTION:  LON follows LAT, so where LAT, -LON
        //  the "-" may be part of the LAT field.
        if (lon.hemisphere.symbol != null) {            
            
            if ("-".equals(lon.hemisphere.symbol)) {
                --lon_dashes;
            }
        }
        
        // Dash count is even?
        return lat_dashes != lon_dashes;
        // Caller should override this assessment if counting dashes in lat or lon is irrelevant.
    }
    /**
     * 
     * @return
     */
    public boolean hasMinutes() {
        if (lat != null) {
            return lat.has_minutes;
        }
        return false;
    }

    /**
     * 
     * @return
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
     */
    public void setLatitude(String decval) {
        lat_text = decval;
        latitude = Double.parseDouble(decval);
    }

    /**
     * 
     * @param decval
     */
    public void setLongitude(String decval) {
        lon_text = decval;
        longitude = Double.parseDouble(decval);
    }

    /**
     * 
     * @return
     */
    public String formatLatitude() {
        return PrecisionScales.format(this.latitude, this.precision.digits);
    }

    /**
     * 
     * @return
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
     * @return
     */
    public String formatPrecision() {
        return "" + (int) precision.precision;
    }

    @Override
    public int getPrecision() {
        return (int) precision.precision;
    }

    /**
     * Convert the current coordinate to MGRS
     * 
     * @return
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
     */
    public void addOtherInterpretation(GeocoordMatch m2) {
        if (interpretations == null) {
            interpretations = new ArrayList<GeocoordMatch>();
        }
        interpretations.add(m2);
    }

    public boolean hasOtherIterpretations() {
        return (interpretations != null && !interpretations.isEmpty());
    }

    public List<GeocoordMatch> getOtherInterpretations() {
        return interpretations;
    }

    // ************************************
    //
    // Geocoding Interface
    //
    // ************************************
    @Override
    public boolean isPlace() {
        return true;
    }

    /**
     * Note the coordinate nature of this TextMatch/Geocoding takes precedence
     * over other flags isPlace, isCountry, etc.
     * 
     * @return
     */
    @Override
    public boolean isCoordinate() {
        return true;
    }

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
     */
    // @Override
    public double getConfidence() {
        return 0.90;
    }

    @Override
    public String getCountryCode() {
        return "UNK";
    }

    @Override
    public String getAdmin1() {
        return "UNK";
    }

    @Override
    public String getAdmin2() {
        return "UNK";
    }

    @Override
    public String getFeatureClass() {
        return "S";
    }

    @Override
    public String getFeatureCode() {
        return "COORD";
    }

    @Override
    public String getPlaceID() {
        return coord_text;
    }

    @Override
    public String getPlaceName() {
        return getText();
    }

    /**
     * Returns the exact pattern that matched.
     * 
     * @return
     */
    @Override
    public String getMethod() {
        return this.pattern_id;
    }

    /**
     * @return lat in degrees
     */
    @Override
    public double getLatitude() {
        return latitude;
    }

    /**
     * @return lon in degrees
     */
    @Override
    public double getLongitude() {
        return longitude;
    }

    /**
     * @param latitude
     */
    @Override
    public void setLatitude(double lat) {
        this.latitude = lat;
    }

    /**
     * @param longitude
     */
    @Override
    public void setLongitude(double lon) {
        this.longitude = lon;
    }
}
