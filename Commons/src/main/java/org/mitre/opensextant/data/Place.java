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

import java.io.Serializable;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class Place extends GeoBase implements Comparable<Object>, Serializable {

    private static final long serialVersionUID = 2389068012345L;
    /**
     * For normalization purposes tracking the Province may be helpful.
     * Coordinate and Place both share this common field. However no need to
     * create an intermediate parent-class yet.
     *
     * Province is termed ADM1 -- or the first level of administrative boundary
     */
    protected String admin1 = null;
    protected String admin2 = null;

    /**
     * Creates a new instance of Geobase
     *
     * @param pk
     * @param n
     */
    public Place(String pk, String n) {
        super(pk, n);
    }
    protected char name_type = 0;

    public void setName_type(char t) {
        name_type = t;
    }

    public char getName_type() {
        return name_type;
    }

    /**
     *
     */
    protected String country_id = null;

    /**
     * Compat: set country_id aka CountryCode
     */
    public void setCountryCode(String cc) {
        country_id = cc;
    }

    public String getCountryCode() {
        return country_id;
    }
    
        private String featureClass = null;

    public String getFeatureClass() {
        return featureClass;
    }

    public void setFeatureClass(String featureClass) {
        this.featureClass = featureClass;
    }
    private String featureCode = null;

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    /**
     * Wrapper around GeoBase.setKey for compat
     */
    public final void setPlaceID(String id) {
        setKey(id);
    }

    /**
     * Wrapper around GeoBase.getKey for compat
     */
    public String getPlaceID() {
        return getKey();
    }

    public final void setPlaceName(String nm) {
        setName(nm);
    }

    public final String getPlaceName() {
        return getName();
    }

    public String getAdmin1() {
        return admin1;
    }

    public void setAdmin1(String key) {
        admin1 = key;
    }

    public String getAdmin2() {
        return admin2;
    }

    public void setAdmin2(String key) {
        admin2 = key;
    }

    /**
     */
    public boolean isAbbreviation() {
        return GeonamesUtility.isAbbreviation(name_type);
    }

    /**
     * Is this Place a Country?
     *
     * @return - true if this is a country or "country-like" place
     */
    public boolean isCountry() {
        return GeonamesUtility.isCountry(getFeatureCode());
    }

    /**
     * Is this Place a State or Province?
     *
     * @return - true if this is a State, Province or other first level admin
     * area
     */
    public boolean isAdmin1() {
        return GeonamesUtility.isAdmin1(getFeatureCode());
    }

    /**
     * Is this Place a National Capital?
     *
     * @return - true if this is a a national Capital area
     */
    public boolean isNationalCapital() {
        return GeonamesUtility.isNationalCapital(getFeatureCode());
    }
    // the a priori estimates
    private Double name_bias;
    private Double id_bias;

    /**
     * The name bias is a measure of the a priori likelihood that a mention of
     * this place's name actually refers to a place.
     */
    public Double getName_bias() {
        return name_bias;
    }

    public void setName_bias(Double bias) {
        name_bias = bias;
    }

    /**
     * The ID bias is a measure of the a priori likelihood that a mention of
     * this name refers to this particular place.
     */
    public Double getId_bias() {
        return id_bias;
    }

    public void setId_bias(Double bias) {
        id_bias = bias;
    }

    @Override
    public String toString() {
        return this.getName() + "(" + this.getAdmin1() + ","
                + this.getCountryCode() + "," + this.getFeatureCode() + ")";
    }

    /* two Places with the same PlaceID are the same "place"
     * two Places with different PlaceIDs ARE PROBABLY different "places"
     */
    @Override
    public int compareTo(Object other) {
        if (!(other instanceof Place)) {
            return 0;
        }
        Place tmp = (Place) other;
        return this.getKey().compareTo(tmp.getKey());
    }
}
