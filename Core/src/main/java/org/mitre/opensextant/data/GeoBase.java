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
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
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
//   OpenSextant Commons
// *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */

package org.mitre.opensextant.data;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class GeoBase {

    /**
     *
     */
    protected String key = null;
    /**
     *
     */
    public String name = null;

    /**
     * Creates an empty GeoBase location object
     * @param pk 
     * @param n 
     */
    public GeoBase(){
    }
    
    /**
     * Creates a new instance of GeoBase
     * @param pk 
     * @param n 
     */
    public GeoBase(String pk, String n) {
        this.key = pk;
        this.name = n;
    }

    /** Generic ID  
     * @return 
     */
    public String getKey() {
        return key;
    }

    /**
     *
     * @param k
     */
    public void setKey(String k) {
        key = k;
    }

    /** Bean support
     * @param N 
     */
    public final void setName(String N) {
        name = N;
    }

    /** Generic Name
     * @return 
     */
    public String getName() {
        return name;
    }

    /** Generic label -- anything more sophisticated needs attention
     * E.g. to use Key + Name or just Key for a label would be very specific
     * @return 
     */
    @Override
    public String toString() {
        return getName();
    }
    
    // canonical form is decimal degree
    protected double latitude;
    protected double longitude;

    /**
     * @return lat in degrees
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * @param latitude
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /** 
     * @return lon in degrees
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * @param longitude
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    private String geohash;

    /** Set and get Geohash -- this is delegated to caller
     *  as core processing need not have a geohash generated when lat/lon is set.
     * @param gh 
     */
    public void setGeohash(String gh) {
        geohash = gh;
    }

    /**
     *
     * @return
     */
    public String getGeohash() {
        return geohash;
    }
}
