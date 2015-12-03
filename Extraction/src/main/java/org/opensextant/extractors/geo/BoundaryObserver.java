package org.opensextant.extractors.geo;

import java.util.Map;

import org.opensextant.data.Place;

/**
 * Emit a boundary event when you come across a concrete reference to a boundary,
 * e.g., county or state, district or prefecture.
 * 
 * @author ubaldino
 *
 */
public interface BoundaryObserver {

    public void boundaryLevel1InScope(Place p);

    public void boundaryLevel2InScope(Place p);

    /* TODO: Ocean boundaries or coastal/island boundaries in scope?
     * Disputed territory boundaries in scope?
     * 
     */

    /**
     * Calculates totals and ratios for the discovered set of boundaries, inferred or explicit.
     * 
     * @return counts for boundary places mentioned or inferred
     */
    public Map<String, PlaceCount> placeMentionCount();
}
