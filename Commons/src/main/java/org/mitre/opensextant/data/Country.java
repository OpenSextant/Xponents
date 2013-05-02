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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mitre.opensextant.data;

import java.util.Set;
import java.util.HashSet;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class Country extends GeoBase {
    /** ISO 2-character country code */
    public String CC_ISO2 = null;
    /** ISO 3-character country code */
    public String CC_ISO3 = null;
    /** FIPS 10-4 2-character country code */
    public String CC_FIPS = null;
    
    /** Any list of country alias names. */
    private Set<String> aliases = new HashSet<String>();
    
    private Set<String> regions = new HashSet<String>();

    /**
     *
     * @param iso2
     * @param nm
     */
    public Country(String iso2, String nm){
        super(iso2, nm);
        CC_ISO2 = this.key;
        this.country_id = this.key;
    }
    
    /** Country is also known as some list of aliases
     * @param nm 
     */
    public void addAlias(String nm){
        aliases.add(nm);
    }
    /**
     *
     * @return
     */
    public Set<String> getAliases(){
        return aliases;
    }
    
        /** Country is also known as some list of aliases
         * @param regionid 
     */
    public void addRegion(String regionid){
        regions.add(regionid);
    }
    /**
     *
     * @return
     */
    public Set<String> getRegions(){
        return regions;
    }   
}
