package org.opensextant.extractors.geo;

import org.opensextant.data.Country;

/**
 * Country metrics
 * 
 * @author ubaldino
 *
 */
public class CountryCount {
    public int count = 1;
    public int total = 1;
    private double ratio = 0;
    public Country country = null;

    /**
     * given a total number of ALL country mentions,
     * you can derive a ratio, e.g., text ABC is 45% about country1, 34% about country2, etc.
     * Set total attribute before calling this. 
     * @return double
     */
    public double getRatio() {
        ratio = (double) count / total;
        return ratio;
    }

    public String toString() {
        return String.format("%s (%d or %03.1f pct)", country.getCountryCode(), count, 100*getRatio());
    }
}
