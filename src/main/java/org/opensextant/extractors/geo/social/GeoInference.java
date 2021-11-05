package org.opensextant.extractors.geo.social;

import org.opensextant.data.Geocoding;

import java.util.Map;

/**
 * This is a light wrapper around TextMatch + Geocoding interfaces.
 * Place and GeocoordMatch objects are primary geocode payload,
 * but adds inferencing metadata at top level more clearly.
 * The intent is to allow other tools use GeoInference as an API object
 * that is independent of information extraction/matching technique or pipeline.
 *
 * @author ubaldino
 */
public class GeoInference {

    /** original data record that this inference is attached to */
    public String recordId = null;
    /**
     * Contributor -- what app, class or module generated the inference.
     */
    public String contributor = null;
    /**
     * a country, place or other geocoding that was inferred.
     */
    public Geocoding geocode = null;
    /**
     * your confidence in this inference
     */
    public int confidence = 0;
    /**
     * a short label for this type of location. E.g. device-location,
     * user-profile-loc, geo, etc.
     */
    public String inferenceName = null;
    /**
     * Any additional attributes you would like to add. Optional.
     */
    public Map<String, Object> attributes = null;
    /**
     * offset bounds. Borrowed from TextMatch.
     */
    public int start = -1;
    public int end = -1;
}
