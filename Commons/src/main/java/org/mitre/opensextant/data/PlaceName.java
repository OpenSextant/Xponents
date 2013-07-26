/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mitre.opensextant.data;

/**
 * A container for  storing metadata about the actual name of a place or places.
 * There is nothing geodetic about this, nor is there any connection between this and the GATE processing objects.
 * TODO: converge on a common data model.
 * @author ubaldino
 */
public class PlaceName {
    
    /** A phonetic representation of name */
    public String name = null;
    
    /** Useful to have done, however lower case vs. IC4U normalization 
     *  is the question -- TODO: "namenorm" should be some language, script-dependent
     *  version of the name, normalized
     */
    public String namenorm = null;    
    /**
     *
     */
    public int namelen = 0;
    
    /** Optional slot for you to populate -- language dependent */
    public int ngram_count = 0;
    
    /** A phonetic representation of name -- algorithm dependent*/
    public String phoneme = null;
    
    /**
     *
     * @param nm
     */
    public PlaceName(String nm){
        name = normalizeName(nm);
        namelen = name.length();
        namenorm = name.toLowerCase();
    }
    
    // script
    // language
    
    /**
     *
     * @param n
     * @return
     */
    public static String normalizeName(String n){
        return n.trim();
    }
    
}
