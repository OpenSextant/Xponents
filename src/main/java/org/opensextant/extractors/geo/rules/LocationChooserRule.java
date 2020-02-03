package org.opensextant.extractors.geo.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensextant.data.Country;
import org.opensextant.data.Place;
import org.opensextant.extractors.geo.CountryCount;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceCount;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.util.GeodeticUtility;

/**
 * A final geocoding pass or two. Loop through candidates and choose the location that best fits the
 * context.
 * 
 * As needed cache chosen entries to optimize, e.g. co-referrenced places aformentioned in document.
 * Ideally, consider choosing a best place for the particular instance of a name, but percolate that
 * to the other mentions of that same name. Is it the same place? No need to disambiguate it
 * multiple times at this point.
 * 
 * @author ubaldino
 *
 */
public class LocationChooserRule extends GeocodeRule {

    /**
     * These are set.
     */
    private Map<String, CountryCount> countryContext = null;
    private Map<String, PlaceCount> boundaryContext = null;
    private Map<String, PlaceCount> namespace = new HashMap<>();
    private HashMap<String, CountryCount> inferredCountries = new HashMap<>();

    private int textCase = 0;

    public void setTextCase(int c) {
        textCase = c;
    }

    /**
     * These are accumulated.
     */
    private Map<String, Place> documentResolvedLocations = new HashMap<>();
    private Map<String, PlaceCandidate> documentCandidates = new HashMap<>();

    @Override
    public void reset() {
        documentResolvedLocations.clear();
        documentCandidates.clear();
        namespace.clear();
        inferredCountries.clear();
    }

    /**
     * Walk the entire list.
     */
    public void evaluate(List<PlaceCandidate> names) {

        // INPUTS: 
        //    histogram of country mentions
        //    resolved/relevant provinces (PlaceEvidence)
        //    resolved/relevant locations attached to places (PlaceEvidence)
        // 
        // MEASURES:  
        //    # of distinct countries == density, focus.  Is this document about one or two countries, 
        //    or is it a world news report on everything.
        //
        countryContext = countryObserver.countryMentionCount();
        boundaryContext = boundaryObserver.placeMentionCount();

        /* TODO:  DEBUG through location chooser using histograms 
         * of found and resolved place metadata.
         * 
         */
        if (log.isDebugEnabled()) {
            debuggingHistograms(names);
        }

        for (PlaceCandidate name : names) {
            if (name.isCountry) {
                this.assessConfidence(name);
                // No Place is chosen, but confidence will be set.
                continue;
            }
            if (name.isFilteredOut()) {
                continue;
            }

            if (name.getChosen() != null) {
                // documentResolvedLocations.put(name.getTextnorm(), name.getChosen());
                // CACHE?
                // DONE
                continue;
            }

            // + For each Name, stack evidence for a given geo or a class of geo (evidence applies to multiple candidate geos)
            // + Assign a weight for each geo based on innate features and evidence.
            // + Sort by final score
            // + Choose top score
            // + Cache result for a given NAME = CHOSEN, so we don't repeat the same logic unnecessarily.
            // 
            for (Place geo : name.getPlaces()) {
                evaluate(name, geo);
            }
            name.choose();
            if (name.getChosen() != null) {
                this.assessConfidence(name);
                /* Copy confidence AND method back to chosen place instance.  Method should be a richer reprensetation of
                 * method for inferencing. e.g., a summary of major branch of rules by which name+location is identified and geolocated.
                 */
                name.getChosen().setConfidence(name.getConfidence());
                name.getChosen().setMethod(PlaceGeocoder.METHOD_DEFAULT);
                documentResolvedLocations.put(name.getTextnorm(), name.getChosen());
            } else {
                log.info("Place name is ambiguous: {} in N={} places", name.getText(), name.distinctLocationCount());
            }
        }
    }

    /**
     * What can we learn from assembling better stats at the document level? Evidence breaks down into
     * concrete locations vs. inferred.
     * 
     * @param names
     */
    private void debuggingHistograms(List<PlaceCandidate> names) {
        /*
         * TODO:  Is this histogram helpful.?
         * 
         * Uniqueness or popularity of a given name.
         */
        for (PlaceCandidate name : names) {
            if (name.isFilteredOut()) {
                continue;
            }
            PlaceCount x = namespace.get(name.getTextnorm());
            if (x == null) {
                x = new PlaceCount();
                x.place = new Place(name.getTextnorm(), name.getTextnorm());
                x.total = names.size();
                namespace.put(name.getTextnorm(), x);
            } else {
                ++x.count;
            }
        }

        for (String cc : countryContext.keySet()) {
            CountryCount count = countryContext.get(cc);
            //log.debug("Country: {}/{} ({})", cc, count.country, count.count);
            log.debug("Country: {}", count);
        }

        for (PlaceCount count : boundaryContext.values()) {
            //log.debug("Boundary: {} ({})", count.place, count.count);
            log.debug("Boundary: {}", count);
            String cc = count.place.getCountryCode();
            CountryCount Ccnt = inferredCountries.get(cc);
            if (Ccnt == null) {
                Ccnt = new CountryCount();
                Ccnt.country = new Country(cc, cc);
                inferredCountries.put(cc, Ccnt);
            } else {
                ++Ccnt.count;
            }
        }

        log.debug("Places: {}/{}", namespace.size(), namespace);

    }

    protected static final double ADMIN_CONTAINS_PLACE_WT = 3.0;
    protected static final double COUNTRY_CONTAINS_PLACE_WT = 1.0;

    /**
     * An amount of points that would be distributed amongst feature types at each level, e.g., Country
     * names, ADM1, ADM2, PPL names.
     * 
     * If you have 2 different countries, one mentioned 4 times and the other mentioned 10 times you
     * might say the latter is more relevant regarding any ambiguous geography. With 14 mentions, that
     * second country is weighted 10/14 = 0.71 of the GLOBAL_POINTS for disambiguation.
     * 
     * Note, that if only one country appears in context, then it is very possible that these global
     * points will outweigh other over arching connections, such as rules for CITY,STATE or MAJOR PLACE
     * (POPULATION). That is okay -- if one single country is mentioned at all, then that seems to be a
     * big anchoring point for lots of ambiguities.
     */
    private static final int GLOBAL_POINTS = 5;

    /**
     * Yet unchosen location. Consider given evidence first, creating some weight there, then
     * introducing innate properties of possible locations, thereby amplifying the differences in the
     * candidates.
     * 
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {

        if (boundaryContext.isEmpty() && countryContext.isEmpty()) {
            return;
        }

        double countryScalar = 1.0;
        CountryCount ccnt = countryContext.get(geo.getCountryCode());
        if (ccnt != null) {
            countryScalar = GLOBAL_POINTS * ccnt.getRatio();
        }

        // Choose either boundary or country context to add in for this location.
        // This is inferred stuff from the document at large.
        if (geo.getHierarchicalPath() != null && boundaryContext.containsKey(geo.getHierarchicalPath())) {
            name.incrementPlaceScore(geo, countryScalar * ADMIN_CONTAINS_PLACE_WT);
        } else if (countryContext.containsKey(geo.getCountryCode())) {
            name.incrementPlaceScore(geo, countryScalar * COUNTRY_CONTAINS_PLACE_WT);
        }

        // Other local evidence.  
        // 
        for (PlaceEvidence ev : name.getEvidence()) {
            if (ev.wasEvaluated()) {
                continue;
            }
            ev.defaultHierarchicalPath();

            // Evaluate evidence
            if ((ev.getAdmin1() != null && geo.getAdmin1() != null)) {
                if (geo.getHierarchicalPath().equals(ev.getHierarchicalPath())) {
                    name.incrementPlaceScore(geo, ADMIN_CONTAINS_PLACE_WT);
                }
            } else {
                if (geo.getCountryCode().equals(ev.getCountryCode())) {
                    name.incrementPlaceScore(geo, COUNTRY_CONTAINS_PLACE_WT);
                }
            }

            ev.setEvaluated(true);
            log.debug("\tEvidence: {} {}", ev, ev.getAdmin1());
        }
    }

    /**
     * 
     */
    public static final int MATCHCONF_BARE_ACRONYM = 10;

    /**
     * The bare minimum confidence -- if rules negate confidence points, confidence may go below 20.
     */
    public static final int MATCHCONF_MINIMUM = 20;

    /**
     * Absolute Confidence: Many Locations matched a single name. No country is in scope; No country
     * mentioned in document, so this is very low confidence.
     */
    public static final int MATCHCONF_MANY_LOC = MATCHCONF_MINIMUM;

    /**
     * Absolute Confidence: Many locations matched, with multiple countries in scope So, Many countries
     * mentioned in document
     */
    public static final int MATCHCONF_MANY_COUNTRIES = 40;
    /**
     * Absolute Confidence: Many locations matched, but one country in scope. So, 1 country mentioned in
     * document
     */
    public static final int MATCHCONF_MANY_COUNTRY = 50;

    /**
     * Absolute Confidence: Name, Region; City, State; Capital, Country; etc. Patterns of qualified
     * places.
     */
    public static final int MATCHCONF_NAME_REGION = 75;

    /**
     * Absolute Confidence: Unique name in gazetteer.
     * Confidence is high, however this needs to be tempered by the number of gazetteers, coverage, and diversity
     */
    public static final int MATCHCONF_ONE_LOC = 70;

    /** Absolute Confidence: Geographic location of a named place lines up with a coordinate in-scope */
    public static final int MATCHCONF_GEODETIC = 90;

    /** Confidence Qualifier: The chosen place happens to be a major place, e.g., large city. */
    public static final int MATCHCONF_QUALIFIER_MAJOR_PLACE = 5;

    /** Confidence Qualifier: The chosen place happens to be in a country mentioned in the document */
    public static final int MATCHCONF_QUALIFIER_COUNTRY_MENTIONED = 5;

    /**
     * Confidence Qualifier: Ambiguous
     */
    public static final int MATCHCONF_QUALIFIER_AMBIGUOUS_NAME = -5;

    /**
     * Confidence Qualifier: Name appears in only one country.
     * 
     */
    public static final int MATCHCONF_QUALIFIER_UNIQUE_COUNTRY = 8;

    /** Confidence Qualifier: The chosen place scored high compared to the runner up */
    public static final int MATCHCONF_QUALIFIER_HIGH_SCORE = 5;
    /**
     * Confidence Qualifier: Start here if you have a lower case term that may be a place. -20 points or
     * more for lower case matches, however feat_class P and A win back 5 points; others are less likely
     * places.
     */
    public static final int MATCHCONF_QUALIFIER_LOWERCASE = -15;

    private static boolean isShort(int matchLen) {
        return matchLen <= NonsenseFilter.GENERIC_ONE_WORD;
    }

    /**
     * Confidence of your final chosen location for a given name is assembled as the sum of some
     * absolute metric plus some additional qualifiers. The absolute provides some context at the
     * document level, whereas the qualifiers are refinements.
     * 
     * <pre>
     *  conf = A + Q1 + Q2...  // this may change.
     * </pre>
     * 
     * @param pc
     */
    public void assessConfidence(PlaceCandidate pc) {

        /*
         * Countries are used to qualify other place names by way of geographic boundaries.
         * So the rules for assigning countries should not leverage place confidence too much, 
         * otherwise this becomes an artificial feedback loop.  
         * TODO: devise acceptable confidence for country name match vs. country code or other inference.
         */
        if (pc.isCountry) {
            if (pc.isAbbreviation) {
                pc.setConfidence(MATCHCONF_MINIMUM);
            } else {
                pc.setConfidence(MATCHCONF_NAME_REGION);
            }
            return;
        }

        if (pc.getChosen() == null && pc.distinctLocationCount() > 0) {
            // Either not evaluated yet or no good choice could be made.
            // Ambiguous location name.
            pc.setConfidence(MATCHCONF_MANY_LOC);
            return;
        }
        int points = 0;

        // This place candidate instance:
        // - total # of instances in gazetteer, e.g., getPlaces()
        // - distinct countries for those places, e.g.,       
        // 
        // Mutually Exclusive conditions:
        //======================
        if (pc.hasRule(CoordinateAssociationRule.COORD_PROXIMITY_RULE)) {
            points = MATCHCONF_GEODETIC;
        } else if (pc.distinctLocationCount() == 1 && countryObserver.countryCount() > 0) {
            points = MATCHCONF_ONE_LOC;
        } else if (countryObserver.countryCount() == 0 && pc.hasDiacritics && isShort(pc.getLength())) {
            points = MATCHCONF_MINIMUM;
        } else if (pc.hasRule(NameCodeRule.NAME_ADMCODE_RULE) || pc.hasRule(NameCodeRule.NAME_ADMNAME_RULE)) {
            points = MATCHCONF_NAME_REGION;
        } else if (countryObserver.countryCount() == 1) {
            points = MATCHCONF_MANY_COUNTRY;
        } else if (pc.getEvidence().isEmpty()) {
            points = assessLowConfidence(pc);
        } else if (countryObserver.countryCount() > 0) {
            points = MATCHCONF_MANY_COUNTRIES;
        } else {
            points = MATCHCONF_MANY_LOC;
        }

        // Pro-rate this a-priori confidence as 75% + (feature-weight*25%)
        //
        double featWeight = 0;
        FeatureClassMeta fc = FeatureRule.lookupFeature(pc.getChosen());
        if (fc != null) {
            featWeight = fc.factor;
        }
        points =  (int)((0.75 * points) + (0.25 * points * featWeight));

        // Any of these may occur.
        //======================
        //
        // Lower case?  Eh... language dependent.  
        // If you have mixed case documents, then lower case matches
        // immediately get low-confidence.
        if (pc.isLower()) {
            points += MATCHCONF_QUALIFIER_LOWERCASE;
            if (pc.getChosen().isAdministrative()) {
                points += 10;
            } else if (pc.getChosen().isPopulated()) {
                points += 5;
            }
        }

        /*
         * If we find random places that are short in length (1,2,3 chars)
         * then we mark them down in confidence. Send them along but low-confidence.
         * Administrative names, though may be short -- allow them to pass as-is.
         */
        if (!pc.getChosen().isAdministrative() && pc.getLength() < 5) {
            if (pc.getEvidence().isEmpty() && pc.hasDefaultRuleOnly()) {
                points -= 10;
            }
        }

        // TODO: work through ambiguities -- true ties.
        // AMBIGUOUS TIE:
        if (pc.isAmbiguous()) {
            Place p1 = pc.getChosen();
            Place p2 = pc.getSecondChoice();
            if (GeodeticUtility.distanceMeters(p1, p2) < SAME_LOCALITY_RADIUS) {
                points += 6;
            } else if (p1.isSame(p2)) {
                points += 4;
            } else if (sameBoundary(p1, p2)) {
                points += 3;
            } else if (sameCountry(p1, p2)) {
                points += 2;
            } else {
                points += MATCHCONF_QUALIFIER_AMBIGUOUS_NAME;
            }
        } else if (pc.getSecondChoiceScore() > 0) {
            // NOT AMBIGUOUS, but is first score much higher than all others?
            // That makes first choice more confident, especially in low-evidence situations.
            double a = pc.getChosen().getScore();
            double b = pc.getSecondChoiceScore();
            double scoreRatio = a / b; // Top score = 40, second score = 25
            if (scoreRatio > 1.2) { // 20% better
                points += MATCHCONF_QUALIFIER_HIGH_SCORE;
            }
        }

        if (pc.distinctCountryCount() == 1) {
            points += MATCHCONF_QUALIFIER_UNIQUE_COUNTRY;
        }

        // Is Major place?  Account for major place population separate from its designation.
        if (pc.hasRule(MajorPlaceRule.POP)) {
            points += MATCHCONF_QUALIFIER_MAJOR_PLACE;
        }
        if (pc.hasRule(MajorPlaceRule.ADMIN) || pc.hasRule(MajorPlaceRule.CAPITAL)) {
            points += MATCHCONF_QUALIFIER_MAJOR_PLACE;
        }
        // 

        if (this.countryObserver.countryObserved(pc.getChosen().getCountryCode())) {
            points += MATCHCONF_QUALIFIER_COUNTRY_MENTIONED;
        }

        if (textCase == LOWERCASE) {
            /* Simple lower case boost:  ADD a point for every character past a basic acronym (2-4 chars long). */
            points += pc.getLength() - 4;
        }

        pc.setConfidence(points);
    }

    private static final int SAME_LOCALITY_RADIUS = 10000; /* Meters */

    private int assessLowConfidence(PlaceCandidate pc) {
        /*
         * False positive tuning -- working with something that has only a default score.
         * Acronyms, No Evidence, default score. All pretty much the same amount of confidence.
         *
         * <pre>
         * TEXT   GEO MATCHED
         * ----   ----------
         * ABS    Abs          low confidence.  Acronym is intended. Mismatch.  If No other evidence, really low confidence
         * Abs    Abs          good match
         * Abs    ABS          not bad.  Id. matches ID for Idaho, for example.
         * Abs.   Abs.         good match.  Abbreviation matched abbreviation.  TODO.
         * Abs.   ABS          good match.  Abbreviation matched abbreviation or code.  TODO.
         * </pre>
         */
        //boolean noEvidence = pc.getEvidence().isEmpty();
        boolean isAcronym = pc.isUpper();
        boolean isMisMatchedAcronym = (pc.isUpper() && !pc.getChosen().isUppercaseName())
                || (!pc.isUpper() && pc.getChosen().isUppercaseName());

        int points = MATCHCONF_MINIMUM;

        if (pc.hasDefaultRuleOnly() && isMisMatchedAcronym) {
            points = MATCHCONF_BARE_ACRONYM;
        } else if (isAcronym) {
            // Acronym with some evidence.
            points = MATCHCONF_BARE_ACRONYM + 3;
        }
        return points;
    }

}
