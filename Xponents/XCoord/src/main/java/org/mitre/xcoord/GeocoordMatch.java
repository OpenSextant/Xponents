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

import java.util.ArrayList;
import java.util.List;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.geodesy.MGRS;
import org.mitre.flexpat.TextMatch;
import org.mitre.opensextant.data.GeodeticUtility;

/**
 * GeocoordMatch holds all the annotation data for the actual raw and normalized
 * coordinate.
 *
 * @see org.mitre.flexpat.TextMatch base class
 * @author ubaldino
 */
public class GeocoordMatch extends TextMatch {

    /**
     * Just the coordinate text normalized
     */
    public String coord_text = null; // MGRS, UTM, all, etc.
    /**
     */
    public String lat_text = null;  // just DD, DMS, DM
    /**
     */
    public String lon_text = null;  // ditto
    /**
     * decimal latitude w/sign
     */
    public double latitude = 0.0f;
    /**
     * decimal longitude w/sign
     */
    public double longitude = 0.0f;
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
     * @see XCoord extract_coordinates is only routine that populates this
     * TextMatch
     */
    public GeocoordMatch() {
        // populate attrs as needed;
    }

    /**
     *
     * @param m
     */
    public void copyMetadata(GeocoordMatch m) {

        this.copy(m);

        // XCoord-specific meta-data.
        //   with the exception of geodetic data.
        this.setFilteredOut(m.isFilteredOut());
        this.cce_family_id = m.cce_family_id;
        this.precision = m.precision;

        // The above was added to allow other interpretations
        // to be added to a primary interpretation.
        // The stuff that varies is:  coord_text, lat, lon
        // The invariant stuff is above: matching metadata.
        // 
        // this.coord_text = m.coord_text;

    }


    /**
     *
     * @param _lat
     * @param _lon
     */
    public void setCoordinate(DMSOrdinate _lat, DMSOrdinate _lon) {

        lat = _lat;
        lon = _lon;

        this.latitude = lat.getValue();
        this.longitude = lon.getValue();

        if (!_lat.hasHemisphere() && !_lon.hasHemisphere() && !_lat.hasSymbols()) {
            // This coordinate has no hemisphere at all.  Possible a bare pare of floating point numbers?
            // 
            this.setFilteredOut(true);
        } else {
            // This coordinate has an invalid lat or lon
            // 
            this.setFilteredOut( ! GeodeticUtility.validateCoordinate(latitude, longitude) );
        }
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
}
