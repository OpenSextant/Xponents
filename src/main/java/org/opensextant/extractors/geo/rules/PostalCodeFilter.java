package org.opensextant.extractors.geo.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

public class PostalCodeFilter extends GeocodeRule {

    int minLen;

    final Pattern validPunct = Pattern.compile("^[-A-Z0-9 ]+$", Pattern.CASE_INSENSITIVE);

    /**
     *
     * @param len - minimum length for a valid postal code.
     */
    public PostalCodeFilter(int len) {
        minLen = len;
    }

    /**
     * Match only alphanumeric codes. space and dash are allowed.
     * @param t
     * @return True if contains invalid punct
     */
    public boolean hasInvalidPunct(String t) {
        Matcher m = validPunct.matcher(t);
        return !m.matches();
    }

    private static final int ERA_START_YEAR = 1975;
    private static final int ERA_END_YEAR = 2025;
    private static final int YEAR_LEN = 4;

    private boolean filterOutYears(String txt) {

        /* DATE filters for disambiguating bettween year and postal code patterns:
         * Let's use +/- 25 years around the millenium for now.
         */
        if (txt.length() != YEAR_LEN) {
            return false;
        }

        // IGNORE YEARS of recent times.
        try {
            int possibleYear = Integer.parseInt(txt);
            /* Arbitrary 50-year period we will ignore for any 4-digit postal codes. */
            return ERA_START_YEAR < possibleYear && possibleYear < ERA_END_YEAR;
        } catch (NumberFormatException nfe) {
            // No worries.  Not a year.
            return false;
        }
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            if (name.isValid()) {
                // Order of Rules is mildly important
                continue;
            }
            if (!name.hasPostal()) {
                //... But this only applies to POSTAL codes.
                continue;
            }

            // Rules apply only to postal codes; while this processing chain will see
            // country and administrative features as well. So, only A/POST from here down.
            //
            if (hasInvalidPunct(name.getText())) {
                name.addRule("InvalidPunct");
                name.setFilteredOut(true);
            }

            // IGNORE SHORT CODES
            if (name.getLength() < minLen) {
                name.addRule("ShortCode");
                name.setFilteredOut(true);
            }

            // IGNORE TOKENS LOOKiNG LiKE YEARS
            if (!name.isValid()) {
                // This only applies to names that have not been previously validated.
                // For example, "2002" could have been marked as valid prior to this rule.
                if (name.hasPostal() && filterOutYears(name.getText())) {
                    name.addRule("YearCode");
                    name.setFilteredOut(true);
                }
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {  /* no op*/ }
}
