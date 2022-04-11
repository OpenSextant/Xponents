package org.opensextant.extractors.geo.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;

public class PostalCodeFilter extends GeocodeRule {

    int minLen = -1;

    Pattern validPunct = Pattern.compile("^[-A-Z0-9 ]+$", Pattern.CASE_INSENSITIVE);

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
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {  /* no op*/ }
}
