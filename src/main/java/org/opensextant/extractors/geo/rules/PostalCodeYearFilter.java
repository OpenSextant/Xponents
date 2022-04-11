package org.opensextant.extractors.geo.rules;

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

public class PostalCodeYearFilter extends GeocodeRule {

    /** DATE filters for disambiguating bettween year and postal code patterns:
     * Let's use +/- 25 years around the millenium for now.
     */
    private static final int ERA_START_YEAR = 1975;
    private static final int ERA_END_YEAR = 2025;
    private static final int YEAR_LEN = 4;


    /**
     */
    public PostalCodeYearFilter() {
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            if (name.isValid()) {
                // Order of Rules is mildly important.
                // If this is associated with an ADMIN boundary
                // then this postal code will pass.
                continue;
            }

            if (name.hasPostal() && name.getLength() == YEAR_LEN) {
                // IGNORE YEARS of recent times.
                try {
                    int possibleYear = Integer.parseInt(name.getText());
                    /* Arbitrary 50-year period we will ignore for any 4-digit postal codes. */
                    if (ERA_START_YEAR < possibleYear && possibleYear < ERA_END_YEAR) {
                        name.addRule("YearCode");
                        name.setFilteredOut(true);
                    }
                } catch (NumberFormatException nfe) {
                    // No worries.  Not a year.
                }
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {  /* no op*/ }
}
