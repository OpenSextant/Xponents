package org.opensextant.extractors.geo;

import org.opensextant.data.Geocoding;

/**
 * Apply this interface where application logic observes a coordinate.
 * 
 * @author ubaldino
 *
 */
public interface CoordinateObserver {
    /**
     * If a given geo is in scope, fire this event.
     * 
     * @param geo
     */
    public void locationInScope(Geocoding geo);
}
