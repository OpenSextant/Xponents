package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.TextUtils;

import java.util.List;

public class PostalCodeFilter extends GeocodeRule {

    private static final int ERA_START_YEAR = 1975;
    private static final int ERA_END_YEAR = 2025;

    int minLen = -1;

    /**
     *
     * @param len - minimum length for a valid postal code.
     */
    public PostalCodeFilter(int len){
        minLen = len;
    }

    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            if (name.isValid()) {
                continue;
            }
            // IGNORE SHORT CODES
            if (name.getLength() < minLen) {
                name.addRule("ShortCode");
                name.setFilteredOut(true);
            }
            // IGNORE YEARS of recent times.
            else if (TextUtils.isNumeric(name.getText()) && name.getLength() == 4) {
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
