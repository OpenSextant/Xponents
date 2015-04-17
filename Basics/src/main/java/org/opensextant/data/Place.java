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

import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.GeodeticUtility;

/**
 * 
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 * @author David P. Lutz, MITRE, dlutz at mitre dot org
 */
public class Place extends GeoBase implements Comparable<Object>, Geocoding {

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
     * @param placeId  primary key or ID for this place 
     * @param nm  place name
     */
    public Place(String placeId, String nm) {
        super(placeId, nm);
    }

    public Place() {
        super();
    }

    public Place(double lat, double lon) {
        super(lat, lon);
    }

    protected char name_type = 0;

    public void setName_type(char t) {
        name_type = t;
    }

    public char getName_type() {
        return name_type;
    }

    protected Country country  = null;
    /**
     * Set the country object and the local country ID code.
     * @param c
     */
    public void setCountry(Country c){
        country = c;
        if (country!=null){
            country_id = country.country_id;
        }
    }
    
    /**
     * get the country object; generally optional.
     * @return
     */
    public Country getCountry(){
        return country;
    }
    /**
     *
     */
    protected String country_id = null;

    /**
     * Compat: set country_id aka CountryCode
     * @param cc a country code.  Caller's choice as far as code code standard used.
     */
    public void setCountryCode(String cc) {
        country_id = cc;
    }

    @Override
    public String getCountryCode() {
        return country_id;
    }

    private String featureClass = null;

    @Override
    public String getFeatureClass() {
        return featureClass;
    }

    public void setFeatureClass(String featureClass) {
        this.featureClass = featureClass;
    }

    private String featureCode = null;

    @Override
    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    /**
     * Wrapper around GeoBase.setKey for compat
     * @param id place identity
     */
    public final void setPlaceID(String id) {
        setKey(id);
    }

    @Override
    public String getPlaceID() {
        return getKey();
    }

    public final void setPlaceName(String nm) {
        setName(nm);
    }

    @Override
    public final String getPlaceName() {
        return getName();
    }

    @Override
    public String getAdmin1() {
        return admin1;
    }

    public void setAdmin1(String key) {
        admin1 = key;
    }

    @Override
    public String getAdmin2() {
        return admin2;
    }

    public void setAdmin2(String key) {
        admin2 = key;
    }

    private String source = null;

    protected String adminName = null;
    protected String admin1Name = null;
    protected String admin2Name = null;

    /**
     * Get the original source of this information.
     * @return source gazetteer
     */
    public String getSource() {
        return source;
    }

    public void setSource(String src) {
        source = src;
    }

    /** @return true if name value here is an abbreviation, e.g., Mass.
     */
    public boolean isAbbreviation() {
        return GeonamesUtility.isAbbreviation(name_type);
    }

    /**
     * Is this Place a Country?
     * 
     * @return true if this is a country or "country-like" place
     */
    @Override
    public boolean isCountry() {
        return GeonamesUtility.isCountry(getFeatureCode());
    }

    @Override
    public boolean isPlace() {
        return !isCountry();
    }

    @Override
    public boolean isCoordinate() {
        return false;
    }

    @Override
    public boolean isAdministrative() {
        return GeonamesUtility.isAdministrative(featureClass);
    }

    /**
     * Is this Place a State or Province?
     * 
     * @return - true if this is a State, Province or other first level admin
     *         area
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
     * @return name bias
     */
    public Double getName_bias() {
        return name_bias;
    }

    /**
     * 
     * @param bias name bias, float
     */
    public void setName_bias(Double bias) {
        name_bias = bias;
    }

    /**
     * The ID bias is a measure of the a priori likelihood that a mention of
     * this name refers to this particular place.
     * @return identity bias
     */
    public Double getId_bias() {
        return id_bias;
    }

    /**
     * 
     * @param bias identity bias
     */
    public void setId_bias(Double bias) {
        id_bias = bias;
    }

    @Override
    public String toString() {
        if (getName() != null) {
            return this.getName() + "(" + this.getAdmin1() + "," + this.getCountryCode() + ","
                    + this.getFeatureCode() + ")";
        } else if (this.latitude != Double.NaN) {
            return String.format("%2.3f, %3.3f", this.latitude, this.longitude);
        }
        return "unset";
    }

    /**
     * two Places with the same PlaceID are the same "place" two Places with
     * different PlaceIDs ARE PROBABLY different "places"
     * @param other another Place
     */
    @Override
    public int compareTo(Object other) {
        if (!(other instanceof Place)) {
            return 0;
        }
        Place tmp = (Place) other;
        return this.getKey().compareTo(tmp.getKey());
    }

    private int precision = -1;

    /**
     * Xponents version of precision is number of meters of error, approximately.
     * precision = 15 means the lat/lon on this Place object is within 15 m of the true location.
     * 
     * @param prec, meters of error
     */
    public void setPrecision(int prec) {
        precision = prec;
    }

    /**
     * Get the relative precision of this feature; in meters of error
     * @see setPrecision
     * @return precision, meters of error.
     */
    @Override
    public int getPrecision() {
        if (precision > 0) {
            return precision;
        } else {
            return GeodeticUtility.getFeaturePrecision(this.featureClass, this.featureCode);
        }
    }

    protected String method = null;

    public void setMethod(String m) {
        method = m;
    }

    /**
     * The method by which the geolocation was determined; GAZ, COUNTRY, etc.
     * are typical methods for reference data XCoord or other tools can parse;
     * so the exact method for such patterns should be revealed here.
     * 
     * @return method of geocoding;
     */
    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getAdminName() {
        return adminName;
    }

    /**
     * 
     * @param adminName name of the administrative boundary that contains this place.
     */
    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    @Override
    public String getAdmin1Name() {
        return admin1Name;
    }

    @Override
    public String getAdmin2Name() {
        return admin2Name;
    }

    public void setAdmin1Name(String n) {
        admin1Name = n;
    }

    public void setAdmin2Name(String n) {
        admin2Name = n;
    }
}
