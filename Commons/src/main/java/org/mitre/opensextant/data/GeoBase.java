/**
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
     *
     */
    public Country country = null;
    /**
     *
     */
    public String country_id = null;

    /**
     * Creates a new instance of Geobase
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
    public void setName(String N) {
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
    private Double latitude = null;
    private Double longitude = null;

    /**
     *
     * @return
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     *
     * @param latitude
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /**
     *
     * @return
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     *
     * @param longitude
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
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
    private char name_type = 0;

    public void setName_type(char t) {
        name_type = t;
    }

    public char getName_type() {
        return name_type;
    }

    public boolean isAbbreviation() {
        return name_type == ABBREVIATION_TYPE;
    }
    public final static char ABBREVIATION_TYPE = 'A';
    public final static char NAME_TYPE = 'N';
}
