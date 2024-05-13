package org.opensextant.extractors.geo.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.data.MatchSchema;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;
import static org.opensextant.extractors.geo.PlaceCandidate.VAL_SAME_COUNTRY;

public class PostalCodeAssociationRule extends GeocodeRule implements MatchSchema {

    public static final String POSTAL_ASSOC_RULE = "PostalAssociation";
    final int proximity = 20;

    private String currentBuffer = null;
    static final Pattern validChar = Pattern.compile("[\\w\\s,.]");

    /**
     * Important - set buffer so spans for tuples can be assessed.
     * @param buf input buffer
     */
    public void setBuffer(String buf) {
        currentBuffer = buf;
    }

    /**
     * Determine if you have one of each, ADM1 and POSTAL.   COUNTRY and POSTAL is also fine.
     * e.g., MA 01721   is a qualified postal code.
     * e.g., MA MA      is nonsense; it looks like two province codes together.
     * e.g., Garden City NJ  USA 01721   is fine.  POSTAL code follows country.
     * <p>
     *    Garden City  01721   -- won't work if place is P/PPL only.  Must be ADM4 or PPLA for example.
     *    90120 01721          -- also will not work; Two postal codes next to each other.
     *    Garden City  01721-0045 --  Office park in a large metro area. That could work.
     * @param geo1 place obj
     * @param geo2 place obj
     * @return true if place mentions complement each other.
     */
    public static boolean complementaryPostal(final Place geo1, final Place geo2) {

        /* ADM POST */
        if ((geo1.isAdministrative() || geo1.isCountry()) && geo2.isPostal()) {
            return true;
        }
        /* POST ADM1 */
        return (geo2.isAdministrative() || geo2.isCountry()) && geo1.isPostal();
    }

    /**
     * detect if invalid punct characters exceed some limit.
     * Pattern of ADMIN, inner POSTAL must be tested to show "inner" span is not junk.
     *
     * @return True if punctuation between two matches is odd and invalid
     */
    private boolean exceedsInnerSpanPunctuation(String buf, PlaceCandidate p1, PlaceCandidate p2) {
        int x1 = p1.end < p2.start ? p1.end : p2.end;
        int x2 = p1.end < p2.start ? p2.start : p1.start;
        if (x1 >= x2) {
            return false;
        }
        String span = buf.substring(x1, x2);

        Matcher m = validChar.matcher(span);
        int charCount = 0;
        while (m.find()) {
            ++charCount;
        }
        // Considered allowing some punctuation -- but here we see if any char in span is not valid.
        return (span.length() - charCount) > 0;
    }

    /**
     * Align Postal geography -- by administrative boundary (preferred) or country
     * @param p1 PlaceCandidate
     * @param p2 PlaceCandidate
     */
    void alignGeography(final PlaceCandidate p1, final PlaceCandidate p2) {

        for (ScoredPlace geo1Score : p1.getPlaces()) {
            p1.defaultMatchId();
            Place geo1 = geo1Score.getPlace();
            for (ScoredPlace geo2Score : p2.getPlaces()) {
                Place geo2 = geo2Score.getPlace();
                // Lexical check:
                if (!complementaryPostal(geo1, geo2)) {
                    continue;
                }
                boolean geo1Postal = geo1.isPostal(); /* If postal, then geo2 must be other */
                boolean geo2Postal = geo2.isPostal(); /* If postal, then geo1 must be other */
                // Geographic checks:
                if (sameBoundary(geo1, geo2)) {
                    // Use this to collect evidence. Boundary for geo1 and geo2 are the same.
                    this.boundaryObserver.boundaryLevel1InScope(p1.getNDTextnorm(), geo1);
                    p1.markValid();
                    p2.markValid();
                    if (geo1Postal) {
                        p1.markAnchor();
                    }
                    if (geo2Postal) {
                        p2.markAnchor();
                    }
                    p1.linkGeography(p2, geo1Postal ? "admin" : VAL_POSTAL, geo2);
                    p2.linkGeography(p1, geo1Postal ? VAL_POSTAL : "admin", geo1);
                    p1.incrementPlaceScore(geo1, 5.0, POSTAL_ASSOC_RULE);
                    p2.incrementPlaceScore(geo2, 5.0, POSTAL_ASSOC_RULE);

                    // Match ID tracking helps with the integrity of serialized results.
                    geo1.setInstanceId(p1.getMatchId());
                    geo2.setInstanceId(p2.getMatchId());
                } else if (sameCountry(geo1, geo2)) {
                    // TODO: Consider loosening this association.  A Postal code really should line up with
                    // any named place by a known ADMIN code, and not just "same country".
                    // Use this to collect evidence. Boundary for geo1 and geo2 are the same.
                    this.countryObserver.countryInScope(geo1.getCountryCode());

                    p1.markValid();
                    p2.markValid();
                    if (geo1Postal) {
                        p1.markAnchor();
                    }
                    if (geo2Postal) {
                        p2.markAnchor();
                    }
                    p1.linkGeography(p2, geo1Postal ? VAL_SAME_COUNTRY : VAL_POSTAL, geo2);
                    p2.linkGeography(p1, geo1Postal ? VAL_POSTAL : VAL_SAME_COUNTRY, geo1);
                    p1.incrementPlaceScore(geo1, 2.0, POSTAL_ASSOC_RULE);
                    p2.incrementPlaceScore(geo2, 2.0, POSTAL_ASSOC_RULE);

                    // Match ID tracking helps with the integrity of serialized results.
                    geo1.setInstanceId(p1.getMatchId());
                    geo2.setInstanceId(p2.getMatchId());
                }
            }
        }
    }

    @Override
    public void evaluate(List<PlaceCandidate> names) {
        int count = names.size();

        /*
           At least one match must be a postal code. It may be before or after -- mutually exclusive matches
           No overlaps.

             ADM  POST
             POST ADM
             POST
             POST ADM POST ...   -- is a very rare scenario, but possible.  The rightmost pair wins.

             In each case we must traverse the geos per match to identify if match is postal or administrative.

             For the case of "POST" only instances -- see if a recurring country or province appears.  Here
             you are looking at "A/POST" features alone
         */
        for (int x = 0; x < names.size(); ++x) {
            PlaceCandidate match = names.get(x);
            if (match.isFilteredOut()) {
                continue;
            }
            if (x > 0) {
                PlaceCandidate before = names.get(x - 1);
                if (!before.isFilteredOut() && validPair(match, before) && before.isBefore(match)) {
                    alignGeography(match, before);
                }
            }
            if (x < count - 1) {
                PlaceCandidate after = names.get(x + 1);
                if (!after.isFilteredOut() && validPair(match, after) && after.isAfter(match)) {
                    alignGeography(match, after);
                }
            }
        }
        for (PlaceCandidate name : names) {
            if (name.isFilteredOut()) {
                continue;
            }
            if (name.hasPostal() && name.getRelated() == null) {
                // FILTER: remove bare POSTAL codes
                name.setFilteredOut(true);
            } else {
                // FILTER: remove bare ADM1 codes
                for (ScoredPlace geoScore : name.getPlaces()) {
                    if (name.isFilteredOut()) {
                        break;
                    }
                    this.evaluate(name, geoScore.getPlace());
                }
            }
        }
    }

    private boolean validPair(PlaceCandidate match, PlaceCandidate other) {
        // IF matches are near other
        //     check if punctuation is normal for POSTAL scenarios: "A B" or "A, B"
        if (match.isWithinChars(other, proximity) && !(match.isWithin(other) || other.isWithin(match))) {
            return !exceedsInnerSpanPunctuation(currentBuffer, match, other);
        }
        // OTHERWISE, not a valid pair.  Example  "A %% (B)"  or "A    text text     B"
        return false;
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /*
         * crazy rule -- review Administrative codes that collide with other abbreviations.
         * Looking for only (NAME | CODE + POSTAL) in a short span.  If its just "CODE" that appears bare,
         * then we filter it out as noise.
         */
        if (name.isShortName() && name.getRelated() == null && geo.isAdministrative()) {
            name.setFilteredOut(true);
        }
    }
}

