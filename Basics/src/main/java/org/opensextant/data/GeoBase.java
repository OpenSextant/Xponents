/**
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//

package org.opensextant.data;

import org.opensextant.util.GeodeticUtility;

/**
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class GeoBase implements LatLon {

    /**
     * identifier ID
     */
    protected String key = null;

    /**
     * any name associated with this place
     */
    public String name = null;

    /**
     * Creates an empty GeoBase location object
     */
    public GeoBase() {
    }

    /**
     * Creates a new instance of GeoBase
     * @param placeId place Id
     * @param nm place name
     */
    public GeoBase(String placeId, String nm) {
        this.key = placeId;
        this.name = nm;
    }

    public GeoBase(double lat, double lon) {
        this.latitude = lat;
        this.longitude = lon;
    }

    /**
     * @return place id
     */
    public String getKey() {
        return key;
    }

    /**
     * legacy nomenclature.  Place ID is better.
     * @param k place Id
     */
    public void setKey(String k) {
        key = k;
    }

    /**
     * @param nm name for this location
     */
    public final void setName(String nm) {
        name = nm;
    }

    /**
     * @return  name of location
     */
    public String getName() {
        return name;
    }

    /** Generic label -- anything more sophisticated needs attention
     * E.g. to use Key + Name or just Key for a label would be very specific
     * @return string repr of the location
     */
    @Override
    public String toString() {
        return getName();
    }

    // canonical form is decimal degree
    protected double latitude = 0;
    protected double longitude = 0;

    /**
     * @return lat in degrees
     */
    @Override
    public double getLatitude() {
        return latitude;
    }

    /**
     * @param latitude in degrees
     */
    @Override
    public void setLatitude(double lat) {
        this.latitude = lat;
    }

    /**
     * @return longitude in degrees
     */
    @Override
    public double getLongitude() {
        return longitude;
    }

    /**
     * @param longitude in degrees
     */
    @Override
    public void setLongitude(double lon) {
        this.longitude = lon;
    }

    private String geohash;

    /** Set and get Geohash -- this is delegated to caller
     *  as core processing need not have a geohash generated when lat/lon is set.
     * @param gh geohash
     */
    public void setGeohash(String gh) {
        geohash = gh;
    }

    /**
     *
     * @return geohash
     */
    public String getGeohash() {
        return geohash;
    }

    /** Convenience method
     * @param geo lat/lon pair
     */
    public void setLatLon(LatLon geo) {
        this.latitude = geo.getLatitude();
        this.longitude = geo.getLongitude();
    }

    /**
     * Convenience method for checking if lat/lon was set to other than 0,0 (default)
     * @return
     */
    public boolean hasCoordinate(){
        return GeodeticUtility.isValidNonZeroCoordinate(latitude, this.longitude);
    }
}
