package org.opensextant.extractors.geo.rules;

//import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.CountryCount;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceCount;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.ScoredPlace;
import org.opensextant.util.GeonamesUtility;

/**
 * A final geocoding pass or two. Loop through candidates and choose
 * the location that best fits the context.
 * 
 * As needed cache chosen entries to optimize, e.g. co-referrenced places
 * aformentioned in document. Ideally, consider choosing a best place for the
 * particular instance of a name, but percolate that to the other mentions of that same name.
 * Is it the same place? No need to disambiguate it multiple times at this point.
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

        /* TODO:  Is this histogram helpful.?
         * 
         */
        if (log.isDebugEnabled()) {
            for (String cc : countryContext.keySet()) {
                CountryCount count = countryContext.get(cc);
                //log.debug("Country: {}/{} ({})", cc, count.country, count.count);
                log.debug("Country: {}", count);
            }

            for (PlaceCount count : boundaryContext.values()) {
                //log.debug("Boundary: {} ({})", count.place, count.count);
                log.debug("Boundary: {}", count);
            }

            log.debug("Places: {}/{}", namespace.size(), namespace);
        }

        for (PlaceCandidate name : names) {
            if (name.isFilteredOut() || name.isCountry) {
                continue;
            }

            if (name.getChosen() != null) {
                // documentResolvedLocations.put(name.getTextnorm(), name.getChosen());
                // CACHE?
                // DONE
                continue;
            }

            log.debug("Place: {}", name);

            // + For each Name, stack evidence for a given geo or a class of geo (evidence applies to multiple candidate geos)
            // + Assign a weight for each geo based on innate features and evidence.
            // + Sort by final score
            // + Choose top score
            // + Cache result for a given NAME = CHOSEN, so we don't repeat the same logic unnecessarily.
            // 
            for (Place geo : name.getPlaces()) {
                evaluate(name, geo);
            }
            if (name.getBestPlace() != null) {
                documentResolvedLocations.put(name.getTextnorm(), name.getChosen());
            } else {
                log.info("Place name is ambiguous: {}", name.getText());
            }
        }
    }

    /**
     * Comparator for scored place.
     * Scores equal or not? Is p1 score greater than p2?
     */
    public int compare(ScoredPlace p1, ScoredPlace p2) {
        if (p1.getScore() == p2.getScore()) {
            return 0;
        }
        return p1.getScore() - p2.getScore() > 0 ? 1 : -1;
    }

    /**
     * Yet unchosen location.
     * Consider given evidence first, creating some weight there,
     * then introducing innate properties of possible locations, thereby amplifying the
     * differences in the candidates.
     * 
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {

        // Choose either boundary or country context to add in for this location.
        // This is inferred stuff from the document at large.
        if (boundaryContext.containsKey(geo.getHierarchicalPath())) {
            name.incrementPlaceScore(geo, 5.0);
        } else if (countryContext.containsKey(geo.getCountryCode())) {
            name.incrementPlaceScore(geo, 2.0);
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
                    name.incrementPlaceScore(geo, 3.0);
                }
            } else {
                if (geo.getCountryCode().equals(ev.getCountryCode())) {
                    name.incrementPlaceScore(geo, 1.0);
                }
            }

            ev.setEvaluated(true);
            log.debug("\tEvidence: {} {}", ev, ev.getAdmin1());
        }
    }

    /**
     * 
     * @param ev
     * @param geo
     * @return
     * @deprecated Unused.
     */
    protected double scoreBoundary(Place ev, Place geo) {
        if (ev.getCountryCode() == null) {
            // Resolve this to a country. Sorry no known boundary.
            log.debug("Ignored Evidence:{}", ev);
            return 0;
        }
        int bs = 0;
        if (ev.getAdmin2() != null) {
            String evPath = GeonamesUtility.getHASC(ev.getCountryCode(), ev.getAdmin1(), ev.getAdmin2());
            String geoPath = GeonamesUtility.getHASC(geo.getCountryCode(), geo.getAdmin1(), geo.getAdmin2());
            if (evPath.equals(geoPath)) {
                ++bs;
            }
        } else if (ev.getAdmin1() != null) {
            ev.defaultHierarchicalPath();
            if (geo.getHierarchicalPath().equals(ev.getHierarchicalPath())) {
                ++bs;
            }
        } else if (ev.getCountryCode().equals(geo.getCountryCode())) {
            ++bs;
        }

        return bs;
    }
}
