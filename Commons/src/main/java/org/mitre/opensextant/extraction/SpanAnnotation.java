/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mitre.opensextant.extraction;

/**
 * @deprecated  Merged into TextEntity
 * @author ubaldino
 */
 class SpanAnnotation {


    /** */
    public String match_id = null;
    
    /** the matched text; the entity extracted */
    public String text = null;

    // any difference between offset and offset+length?
    // start and end are equivalent.
    /** starting offset of text match */
    public int start = -1;
    /** end offset of match */
    public int end = -1;
    
    /**
     *
     */
    public boolean is_submatch = false;
    /**
     *
     */
    public boolean is_overlap = false;
    /**
     *
     */
    public boolean is_duplicate = false;

    /**
     *
     */
    private SpanAnnotation() {
        // populate attrs as needed;
    }

    /** get the length of the matched text 
     * @return 
     */
    public int match_length() {
        if (start < 0) {
            // Match not initialized
            return 0;
        }
        return end - start;
    }

    /**
     *
     * @return
     */
    public String toString() {
        return text + " @" + start + ":" + end ;
    }        
}
