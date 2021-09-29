package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A trivial location chooser rule.
 */
public class PostalLocationChooser extends GeocodeRule {

    Pattern separators = Pattern.compile("[-\\s+]");

    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            log.info("Candidate? {}", name);
            if (name.isFilteredOut()) {
                continue;
            }
            if (name.getChosen() != null) {
                continue;
            }

            name.choose();

            int conf = 0;
            boolean paired = name.getRelated() != null;

            // lexical confidence:  5 points per digit/char.
            // + 10 points for alphanumeric mix.
            // + 10 points for appropriate punct in middle, e.g. XXXX YYY
            // + 10 points if location is unique.
            // + 20 points if location is qualified by other location e.g., 'NY 10001'

            // NOTE if there is only ONE location with this tag
            // then we choose it.  Otherwise there is no easy way to select the location in isolation.

            // Qualified location  ... PROVINCE ZIPCODE ...
            ScoredPlace loc = name.getChosen();
            if (loc != null && paired) {
                conf += 20;
                conf += (int) (loc.getScore() / 10);
            }

            // Unique location?
            if (name.getPlaces().size() == 1) {
                conf += 10;
            }
            /* Assign the match label */
            name.setType("place");

            // Length?
            conf += 5 * name.getLength();

            if (loc != null) {
                /* For postal codes only: */
                if (GeonamesUtility.isPostal(loc)) {
                    /* Assign the match label */
                    name.setType("postal");

                    // Separators?
                    if (5 < name.getLength() && name.getLength() < 10) {
                        Matcher m = separators.matcher(name.getText());
                        if (m.find()) {
                            if (m.start() > 2) {
                                conf += 10;
                            }
                            name.addRule("PostalCodeFormat");
                        }
                    }
                    // Mixed Alphanumeric patterns have more uniqueness
                    if (!TextUtils.isNumeric(name.getText())) {
                        conf += 10;
                    }
                } else if (!paired) {
                    /* IS NOT a postal code and is unpaired, so it is noise. */
                    // Any other match is filtered out, e.g., trivial country names.
                    // Looking to emit qualified postal code pairs
                    name.setFilteredOut(true);
                }
            }
            name.setConfidence(conf);
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {/* No-op */}
}

