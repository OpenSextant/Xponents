/*
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
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

import static org.opensextant.util.GeodeticUtility.geohash;

/**
 * Place class represents all the metadata about a location.
 * Such location could be static data such as that in the gazetteer or something
 * dynamic or fabricated on the fly.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 * @author David P. Lutz, MITRE, dlutz at mitre dot org
 */
public class Place extends GeoBase implements Geocoding {

    /**
     * For normalization purposes tracking the Province may be helpful.
     * Coordinate and Place both share this common field. However no need to
     * create an intermediate parent-class yet.
     * Province is termed ADM1 -- or the first level of administrative boundary
     */
    protected String admin1 = null;
    protected String admin2 = null;

    /**
     * optional postal code, usually associated with ADM1 code. Typical not the
     * same value, but may correspond
     */
    private String admin1PostalCode = null;
    private String placePostalCode = null;

    private boolean isASCIIName = false;
    private boolean isUppercaseName = false;

    /**
     * Creates a new instance of GeoBase
     *
     * @param placeId primary key or ID for this place
     * @param nm      place name
     */
    public Place(String placeId, String nm) {
        super(placeId, null);
        this.setPlaceName(nm);
    }

    public Place() {
        super();
    }

    public Place(double lat, double lon) {
        super(lat, lon);
    }

    /**
     * Copy the basic gazetteer metadata to
     *
     * @param p
     */
    public void copyTo(Place p) {

        // General Name info
        p.setPlaceID(p.getPlaceID());
        p.setPlaceName(this.getPlaceName());
        p.setName_type(this.getName_type());
        p.setSource(this.getSource());

        // Geo feature
        p.setFeatureClass(this.getFeatureClass());
        p.setFeatureCode(this.getFeatureCode());

        // Geo location
        p.setLatLon(this);
        p.setGeohash(this.getGeohash());
        p.setCountry(this.getCountry());
        p.setCountryCode(this.getCountryCode());
        p.setAdmin1(this.getAdmin1());
        p.setAdmin2(this.getAdmin2());

        // Inferred metadata.
        p.setConfidence(this.getConfidence());
        p.setPrecision(this.getPrecision());
    }

    public boolean isASCIIName() {
        return this.isASCIIName;
    }

    public boolean isUppercaseName() {
        return this.isUppercaseName;
    }

    protected char name_type = 0;

    private boolean isAbbreviation = false;
    private boolean isCode = false;

    public void setName_type(char t) {
        name_type = t;
        isAbbreviation = GeonamesUtility.isAbbreviation(name_type);
        isCode = GeonamesUtility.isCode(name_type);
    }

    /**
     * test if Place is a "Name" -- not a code/abbrev or other.  Tests name_type == "N"
     *
     * @return
     */
    public boolean isName() {
        return name_type == 'N';
    }

    /**
     * Alias for "isAbbreviation() || isCode()"
     *
     * @return
     */
    public boolean isShortName() {
        return isAbbreviation || isCode;
    }

    public char getName_type() {
        return name_type;
    }

    protected Country country = null;

    /**
     * Set the country object and the local country ID code.
     *
     * @param c Country object which contains or is associated with this Place.
     */
    @Override
    public void setCountry(Country c) {
        country = c;
        if (country != null) {
            country_id = country.country_id;
        }
    }

    /**
     * get the country object; generally optional.
     *
     * @return the country object.
     */
    public Country getCountry() {
        return country;
    }

    /**
     *
     */
    protected String country_id = null;

    /**
     * Compat: set country_id aka CountryCode
     *
     * @param cc a country code. Caller's choice as far as code code standard
     *           used.
     */
    @Override
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

    public void setFeatureClass(String cls) {
        this.featureClass = cls;
    }

    private String featureCode = null;

    @Override
    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featCode) {
        this.featureCode = featCode;
    }

    private String featureDesig = null;

    /**
     * Returns a dynamically formatted feature string C/CODE  for class/code.
     */
    public String getFeatureDesignation() {
        if (featureDesig == null) {
            featureDesig = GeonamesUtility.getFeatureDesignation(getFeatureClass(),
                    getFeatureCode());
        }
        return featureDesig;
    }

    /**
     * Check if CC.AA coding of the features is the same.
     *
     * @param otherGeo
     * @return
     */
    public boolean sameBoundary(Place otherGeo) {
        return (otherGeo != null
                && this.getHierarchicalPath().equals(otherGeo.getHierarchicalPath()));
    }

    /**
     * Wrapper around GeoBase.setKey for compat
     *
     * @param id place identity
     */
    public final void setPlaceID(String id) {
        setKey(id);
    }

    @Override
    public String getPlaceID() {
        return getKey();
    }

    @Override
    public final void setPlaceName(String nm) {
        setName(nm);
        if (this.name != null) {
            try {
                isASCIIName = TextUtils.isASCII(TextUtils.removePunctuation(getName()));
            } catch (Exception err) {
                // Prefer not to silence errors here
                // TODO: throw exception for name normalization and related name parsing.
                isASCIIName = false;
            }

            isUppercaseName = TextUtils.isUpper(name);
            nonDiacriticName = TextUtils.phoneticReduction(name.toLowerCase(), isASCIIName);
        }
    }

    private String nonDiacriticName = null;

    /**
     * Returns a pre-computed Non-diacritic name
     * @return
     */
    public String getNDNamenorm() {
        return nonDiacriticName;
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
     * Represent the geographic hierarchy as a string
     * country/province/county/city.
     */
    private String hierarchicalPath = null;

    /**
     * Get the original source of this information.
     *
     * @return source gazetteer
     */
    public String getSource() {
        return source;
    }

    public void setSource(String src) {
        source = src;
    }

    /**
     * @return true if name value here is an abbreviation, e.g., Mass.
     */
    public boolean isAbbreviation() {
        return isAbbreviation;
    }

    public boolean isCode() {
        return isCode;
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
        return GeonamesUtility.isAdministrative(featureClass, featureCode);
    }

    /** Determines if this feature instance is a postal zone, coded "A/POST" */
    public boolean isPostal(){ return GeonamesUtility.isPostal(this); }

    /**
     * macro for detecting ADM1 or ADM2
     */
    public boolean isUpperAdmin() {
        return GeonamesUtility.isUpperAdminLevel(getFeatureCode());
    }

    /**
     * if feature class for this location is 'P' for populated place. TODO: Not
     * sure if this is part of Geocoding interface.
     *
     * @return true if feature class is typically populated
     */
    public boolean isPopulated() {
        return GeonamesUtility.isPopulated(featureClass);
    }

    /**
     * Is this Place a State or Province?
     *
     * @return - true if this is a State, Province or other first level admin area
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
    private int name_bias;
    private int id_bias;

    /**
     * The name bias is a measure of the a priori likelihood that a mention of
     * this place's name actually refers to a place.
     *
     * @return name bias
     */
    public int getName_bias() {
        return name_bias;
    }

    /**
     * @param bias name bias, float
     */
    public void setName_bias(int bias) {
        name_bias = bias;
    }

    /**
     * The ID bias is a measure of the a priori likelihood that a mention of
     * this name refers to this particular place.
     *
     * @return identity bias
     */
    public int getId_bias() {
        return id_bias;
    }

    /**
     * @param bias identity bias
     */
    public void setId_bias(int bias) {
        id_bias = bias;
    }

    @Override
    public String toString() {
        if (getName() != null) {
            return String.format("%s (%s, %s, %s)", getName(), getAdmin1(), this.getCountryCode(),
                    getFeatureCode());
        } else if (!Double.isNaN(this.latitude)) {
            return String.format("%2.3f, %3.3f", this.latitude, this.longitude);
        }
        return "unset";
    }

    public boolean isSame(Place other) {
        return compareTo(other) == 0;
    }

    /**
     * With multiple data sources there is no standard way of saying this place
     * == that place. So we compare features, locations, Ids, etc.
     *
     * @param other another Place
     * @return 1 if other is greater than current; 0 if equal, -1 if lesser
     */
    public int compareTo(Place other) {
        // Identity Matches?
        if (getKey() != null && getKey().equals(other.getKey())) {
            return 0;
        }
        if (getFeatureClass() == null) {
            return -1; // Bad source data. Must have feature classification.
        }

        if (!getFeatureClass().equals(other.getFeatureClass())) {
            return -1;
        }

        // Geohash: Same general location? Use 6 chars of geohash to get 2-3 KM resolution.
        if (other.hasCoordinate() && hasCoordinate()) {
            String g1 = geohash(other);
            String g2 = getGeohash();
            if (g2 == null) {
                g2 = geohash(this);
            }
            if (g2.startsWith(g1.substring(0, 8))) {
                return 0;
            }
        }

        return -1;
    }

    private int precision = -1;

    /**
     * Xponents version of precision is number of meters of error,
     * approximately. precision = 15 means the lat/lon on this Place object is
     * within 15 m of the true location.  Likewise, a precision of "50000" means 50Km of error in the location.
     *
     * @param prec, meters of error
     */
    @Override
    public void setPrecision(int prec) {
        precision = prec;
    }

    /**
     * Get the relative precision of this feature; in meters of error
     *
     * @return precision, meters of error.
     * @see #setPrecision(int)
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

    @Override
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
     * @param admName name of the administrative boundary that contains this
     *                place.
     */
    public void setAdminName(String admName) {
        this.adminName = admName;
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

    /**
     * This ensures at least a default hierarchichal path is set.
     */
    public String getHierarchicalPath() {
        if (this.hierarchicalPath == null) {
            defaultHierarchicalPath();
        }

        return hierarchicalPath;
    }

    public void setHierarchicalPath(String p) {
        this.hierarchicalPath = p;
    }

    /**
     * This sets the default to non-null value. Default hieararchy is:
     *
     * <pre>
     * CC.ADM1
     * CC
     * "" (empty string)
     * </pre>
     */
    public void defaultHierarchicalPath() {
        if (country_id != null && admin1 != null) {
            this.hierarchicalPath = GeonamesUtility.getHASC(this.country_id, this.admin1);
        } else if (country_id != null) {
            this.hierarchicalPath = this.country_id;
            // GeonamesUtility.getHASC(this.country_id, "0"); CC.0 ?
        } else {
            this.hierarchicalPath = "";
        }
    }

    public boolean isSpot() {
        return GeonamesUtility.isSpot(this.featureClass);
    }

    private int population = -1;

    public void setPopulation(int p) {
        population = p;
    }

    public int getPopulation() {
        return population;
    }

    /**
     * State-level postal code, the corresponds usually to ADM1
     */
    @Override
    public String getAdmin1PostalCode() {
        return admin1PostalCode;
    }

    /**
     * City-level postal code, that may be something like a zip. Thinking
     * world-wide, not everyone calls these zipcodes, as in the US.
     */
    @Override
    public String getPlacePostalCode() {
        return placePostalCode;
    }

    public void setAdmin1PostalCode(String c) {
        this.admin1PostalCode = c;
    }

    public void setPlacePostalCode(String c) {
        this.placePostalCode = c;
    }

    private int confidence = 0;

    @Override
    public int getConfidence() {
        return confidence;
    }

    @Override
    public void setConfidence(int c) {
        confidence = c;
    }

    private String instanceId = null;

    /** Use to identify a particular related object ID associated with this location.
     * 
     * This is intended purely for processing applications (not for general purpose use, as in plotting placemarks).  
     * For example use Place.instanceId to link
     * this place object with a mention of the place in a document.  If the document mentions the place
     * multiple times, you can be clear about which mention this instance matches.
     * @param id
     */
    public void setInstanceId(String id) {
        instanceId = id;
    }

    /**
     * see {@link #getInstanceId()}
     * @return
     */
    public String getInstanceId() {
        return instanceId;
    }
}
