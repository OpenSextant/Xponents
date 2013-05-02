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
public class Coordinate extends GeoBase {

    /**
     * A coordinate with a textual value.
     * It has no identifying information other than the text.
     * 
     * If the text is normalized in some fashion to yield a lat/lon
     * use Coordinate.setLatitude(lat), etc to set coordinate values.
     * 
     * Normalize using XCoord as needed.
     * @param text 
     */
    public Coordinate(String text) {
        super(null, text);
    }
    
    /** For normalization purposes tracking the Province may be helpful.
     * Coordinate and Place both share this common field.  However no need to 
     * create an intermediate parent-class yet.
    */
    public String province_id = null;
}
