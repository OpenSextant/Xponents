package org.opensextant.extractors.geo;

import java.util.Map;

import org.opensextant.data.Country;

public interface CountryObserver {
    /**
     * Use a country code to signal that a country was mentioned.
     * 
     * @param cc
     */
    public void countryInScope(String cc);

    /**
     * Use a country object to signal a country was mentioned or is in scope
     */
    public void countryInScope(Country C);

    /**
     * Have you seen this country before?
     * 
     * @param cc country code
     * @return true if observer saw country
     */
    public boolean countryObserved(String cc);

    /**
     * Have you seen this country before?
     * 
     * @param C country object
     * @return true if observer saw country
     */

    public boolean countryObserved(Country C);

    public int countryCount();

    /**
     * Calculates totals and ratios for the discovered set of countries.
     * 
     * @return
     */
    public Map<String, CountryCount> countryMentionCount();
}
