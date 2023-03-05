package org.opensextant.extractors.geo.rules;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.MatchSchema;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.util.TextUtils;


/**
 * A trivial location chooser rule.
 */
public class PostalLocationChooser extends GeocodeRule {

    final Pattern separators = Pattern.compile("[-\\s+]");

    /**
     * confidence increment
     */
    private static final int INCR = 5;

    /** Based on amount of linked geography produce a base level confidence metric for this particular tuple.
     *
     * @param name
     * @return
     */
    private int baseConfidence(PlaceCandidate name) {
        Map<String, Place> tuple = name.getLinkedGeography();
        if (tuple == null) {
            return 0;
        }
        int conf = 5 * tuple.size(); //
        int resolutionLevel = 4;
        for (String slot : PlaceCandidate.KNOWN_GEO_SLOTS) {
            if (name.hasLinkedGeography(slot)) {
                conf += resolutionLevel * 2;
            }
            --resolutionLevel;
        }
        return conf;
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate name : names) {
            log.debug("Candidate? {}", name);
            if (name.isFilteredOut()) {
                // NOTE: unpaired postal codes or other locations are usually filtered out
                // because they are simply unqualified postal references.  You will see them,
                // if requesting filtered tags, but confidence will be unassigned or 0.
                continue;
            }
            if (name.getChosen() != null) {
                continue;
            }

            name.choose();

            int conf = baseConfidence(name);
            boolean paired = conf > 0;

            // lexical confidence:  5 points per digit/char.
            // + 10 points for alphanumeric mix.
            // + 10 points for appropriate punct in middle, e.g. XXXX YYY or XXXX-YYY
            // + 10 points if location is unique.
            // + 20 points if location is qualified by other location e.g., 'NY 10001'

            // NOTE if there is only ONE location with this tag
            // then we choose it.  Otherwise there is no easy way to select the location in isolation.

            // EXAMPLES (ORDER DOES NOT MATTER HERE).  Rules above push confidence higher
            //           country, postal  === 10 points base
            //    prov,  country, postal  === 30 points base
            //                    postal  === 10 points base

            // Qualified location  ... PROVINCE ZIPCODE ...
            ScoredPlace locScore = name.getChosen();
            Place loc = locScore.getPlace();
            loc.setMethod(defaultMethod);
            conf += INCR;
            conf += (int) (locScore.getScore() / 10);

            // Unique location?
            if (name.getPlaces().size() == 1) {
                conf += INCR;
            }
            /* Assign the match label */
            name.setType(MatchSchema.VAL_PLACE);

            // Length?
            conf += 2 * name.getLength();

            /* For postal codes only: */
            if (loc.isPostal()) {
                /* Assign the match label */
                name.setType(MatchSchema.VAL_POSTAL);

                // Separators?
                if (5 < name.getLength() && name.getLength() < 10) {
                    Matcher m = separators.matcher(name.getText());
                    if (m.find()) {
                        // Patterns with separators where separator occurs after 2-chars seem more credible.
                        if (m.start() >= 2) {
                            conf += INCR;
                        }
                        name.addRule("PostalCodeFormat");
                    }
                }
                // Mixed Alphanumeric patterns have more uniqueness
                if (!TextUtils.isNumeric(name.getText())) {
                    conf += INCR;
                }
            } else if (!paired) {
                /* IS NOT a postal code and is unpaired, so it is noise. */
                // Any other match is filtered out, e.g., trivial country names.
                // Looking to emit qualified postal code pairs
                name.setFilteredOut(true);
            }

            name.setConfidence(conf);

            /* Assign Match ID */
            name.defaultMatchId();
            loc.setInstanceId(name.getMatchId());
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {/* No-op */}
}

