package org.opensextant.extractors.geo;

import org.opensextant.data.Place;

/**
 * Place metrics. Everything other than coords, countries, and filtered-out
 * 
 * @author ubaldino
 *
 */
public class PlaceCount {
    public int count = 1;
    private double ratio = 0;
    public Place place = null;
    public int total = 1;

    /**
     * given a total number of ALL place mentions,
     * you can derive a ratio, e.g., text ABC is 45% about province1, 34% about province2, etc.
     */

    public double getRatio() {
        ratio = (double) count / total;
        return ratio;
    }

    public String toString() {
        return String.format("%s (%d or %03.1f pct)", place.getName(), count, 100*getRatio());
    }    
}
