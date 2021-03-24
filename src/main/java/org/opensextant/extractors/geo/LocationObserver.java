package org.opensextant.extractors.geo;

import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;

/**
 * Apply this interface where application logic observes a coordinate or any
 * hard location reference.
 *
 * @author ubaldino
 */
public interface LocationObserver {
    /**
     * If a given geo is in scope, fire this event.
     *
     * @param geo
     */
    public void locationInScope(Geocoding geo);

    /**
     * The place know by the ID, p.getKey() or p.getPlaceID()
     * was it observed directly or indirectly in this document?
     *
     * @param p
     * @return
     */
    public boolean placeObserved(Place p);
}
