package org.opensextant.extractors.geo.rules;

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.ScoredPlace;

public class PostalCodeAssociationRule extends GeocodeRule {

    public static final String POSTAL_ASSOC_RULE = "PostalAssociation";
    int proximity = 20;

    /**
     * Determine if you have one of each, ADM1 and POSTAL.   COUNTRY and POSTAL is also fine.
     * e.g., MA 01721   is a qualified postal code.
     * e.g., MA MA      is nonsense; it looks like two province codes together.
     * e.g., Garden City NJ  USA 01721   is fine.  POSTAL code follows country.
     *
     * @param geo1
     * @param geo2
     * @return true if place mentions complement each other.
     */
    public static boolean complementaryPostal(final Place geo1, final Place geo2) {

        /* ADM POST */
        if ((geo1.isAdmin1() || geo1.isCountry()) && geo2.isPostal()) {
            return true;
        }
        /* POST ADM1 */
        return (geo2.isAdmin1() || geo2.isCountry()) && geo1.isPostal();
    }

    /**
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
                    p1.linkGeography(p2, geo1Postal ? "admin" : "postal", geo2);
                    p2.linkGeography(p1, geo1Postal ? "postal" : "admin", geo1);
                    p1.incrementPlaceScore(geo1, 5.0, POSTAL_ASSOC_RULE);
                    p2.incrementPlaceScore(geo2, 5.0, POSTAL_ASSOC_RULE);

                    // Match ID tracking helps with the integrity of serialized results.
                    geo1.setInstanceId(p1.getMatchId());
                    geo2.setInstanceId(p2.getMatchId());
                } else if (sameCountry(geo1, geo2)) {
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
                    p1.linkGeography(p2, geo1Postal ? "country" : "postal", geo2);
                    p2.linkGeography(p1, geo1Postal ? "postal" : "country", geo1);
                    p1.incrementPlaceScore(geo1, 3.0, POSTAL_ASSOC_RULE);
                    p2.incrementPlaceScore(geo2, 3.0, POSTAL_ASSOC_RULE);

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
           At least one match must be a postal code. It may be before or after.

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
            PlaceCandidate before = null;
            PlaceCandidate after = null;
            if (x > 0) {
                before = names.get(x - 1);
            }
            if (x < count - 1) {
                after = names.get(x + 1);
            }
            if (before != null && match.isWithinChars(before, proximity) && !before.isFilteredOut()) {
                alignGeography(match, before);
            }
            if (after != null && match.isWithinChars(after, proximity) && !after.isFilteredOut()) {
                alignGeography(match, after);
            }
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {/* no-op*/}
}

