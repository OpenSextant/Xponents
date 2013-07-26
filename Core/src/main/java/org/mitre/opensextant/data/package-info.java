/**
 * This data model abstracts the fundamental relationships between geographic data 
 * that we care about when using gazetteer data:
 * <pre>
 *  GeoBase (id, name, country_id, lat/lon )
 *   + Coordinate - null id and country_id;   adds province_id
 *   + Place      - generic container for any named place
 *   + Country    - structure of Country level data - country codes & aliases
 * </pre>
 */
package org.mitre.opensextant.data;

