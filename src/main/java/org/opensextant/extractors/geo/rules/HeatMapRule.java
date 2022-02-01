package org.opensextant.extractors.geo.rules;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;
import org.opensextant.extractors.geo.ScoredPlace;

import java.util.*;

public class HeatMapRule extends GeocodeRule {

    final Map<String, List<Place>> heatmap = new HashMap<>();
    final Map<String, Set<String>> heatmapNames = new HashMap<>();
    final Set<String> visitedPlaces = new HashSet<>();
    final Map<String, List<PlaceCandidate>> mentionMap = new HashMap<>();
    private boolean useAdminBoundary = true;

    public static final String HEATMAP_RULE = "CollocatedNames.geohash";
    public static final String HEATMAP_ADMIN_RULE = "CollocatedNames.boundary";
    private static final String IGNORE_FEATURES = "URS"; // OK: A, P, H, T, L, V.
    private static final int AVERAGE_NAME_LEN = 8;

    @Override
    public void reset() {
        heatmap.clear();
        visitedPlaces.clear();
        heatmapNames.clear();
        useAdminBoundary = true;
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {

        setGeohash(geo);

        /* Trivial short names or locations without metadata are not considered */
        if (name.isCountry || name.isContinent) {
            return;
        }
        if (name.getLength() < 4) {
            return;
        }
        if (geo.getFeatureCode() == null || geo.getFeatureClass() == null) {
            return;
        }
        if (IGNORE_FEATURES.contains(geo.getFeatureClass())) {
            return;
        }

        // String feature key = X/XXX/geohash.
        String placeID = internalPlaceID(geo);

        addMention(name, geo);
        if (visitedPlaces.contains(placeID)) {
            return;
        }
        visitedPlaces.add(placeID);
        // String gh2 = geo.getGeohash().substring(0, 2); // Geohash 2-char is too
        // coarse to be useful.
        String gh3 = geo.getGeohash().substring(0, 3);
        String gh4 = geo.getGeohash().substring(0, 4);

        // Geometric buckets
        // addPlace(geo, gh2);
        addPlace(name.getNDTextnorm(), geo, gh3);
        addPlace(name.getNDTextnorm(), geo, gh4);
        // Geographic/geopolitical buckets:
        if (useAdminBoundary) {
            addPlace(name.getNDTextnorm(), geo, geo.getHierarchicalPath());
        }
    }

    /**
     * Add a place NAME to a grid or geohash to create clusters.
     * 
     * @param name
     * @param geo
     * @param gh
     */
    private void addPlace(String name, Place geo, String gh) {
        if (gh == null) {
            return;
        }
        Set<String> bucketNames = heatmapNames.computeIfAbsent(gh, newBucket -> new HashSet<>());
        bucketNames.add(name);

        List<Place> bucket = heatmap.computeIfAbsent(gh, newPlaceList -> new ArrayList<>());
        bucket.add(geo);
    }

    /**
     * Map Geo Place ID to PlaceCandidate that referred it.
     * 
     * @param name
     * @param geo
     */
    private void addMention(PlaceCandidate name, Place geo) {

        List<PlaceCandidate> tracking = mentionMap.computeIfAbsent(geo.getPlaceID(), k-> new ArrayList<>());
        if (!tracking.contains(name)) {
            tracking.add(name);
        }
    }

    /**
     * Identify the distinct potential named places by location
     * Group places by geohash bins at two levels geohash 3 and geohash 4.
     * Give greater weight to those places in finer grained buckets.
     * I.e., If the various named places in the document appear closer together,
     * then it is likely those locations are the true ones so increment their score.
     */
    public void evaluate(List<PlaceCandidate> names) {

        /*
         * When a large document has more than N possible names, it can be tough to
         * reliably heat-map possible locations. Every province has a Springfield, etc.
         * For large encyclopedic documents that cover the world, state/province
         * boundaries become useless.
         */
        useAdminBoundary = names.size() < 50 && this.countryObserver.countryCount() < 5;

        /*
         * Aggregate Named places geographically into buckets
         */
        for (PlaceCandidate name : names) {
            if (name.isFilteredOut()) {
                log.debug("Ignore for HeatMap: {}", name.getText());
                continue;
            }
            for (ScoredPlace geoScore : name.getPlaces()) {
                Place geo = geoScore.getPlace();
                evaluate(name, geo);
            }
        }

        /*
         * Increment score objectively for buckets with higher density of distinct
         * places
         * in areas of varying size. Smaller areas are more dense (higher score),
         * All places in a bucket will be raised together.
         */
        visitedPlaces.clear();
        Set<Integer> offsets = new HashSet<>();
        double nameDiversity = 0;
        double locationSpecifity = 0;
        double evidenceWeight = 0;

        for (String loc : heatmap.keySet()) {
            Set<String> distinctNames = heatmapNames.get(loc);
            // Nothing to be gained by trivial clusters of points
            if (distinctNames.size() < 2) {
                // Nothing to be gained by mentions of the same name at the same location
                // Looking for diverse named places in clusters.
                continue;
            }
            /*
             * Parameters:
             * Admin rule vs. Geohash -- boundary is either "CC.AA" or a geohash ~
             * resolution 3-4 works best.
             * Admin rule is a HASC (hiearchical code) for the province/state, but can be
             * very broad, e.g. Alaska or Texas are big.
             * location len is 2, 3, or 4 (an approximation of the precision of the bucket).
             * countScale is intended to increase with # of named points in a bucket and
             * with precision.
             */
            boolean isAdminRule = loc.contains(".");
            String ruleName = isAdminRule ? HEATMAP_ADMIN_RULE : String.format("%s%d", HEATMAP_RULE, loc.length());
            int locLength = isAdminRule ? 2 : loc.length();
            int countScale = isAdminRule ? 2 : distinctNames.size();

            // Weighting:
            // Weight increases with location specificity
            // And total number of distinct named places in that bucket.
            // BUT the distinct names have to be somewhat unique -- Longer or rarer names
            // are more convincing, whereas shorter terms may be false-positives. So scale #
            // of names by average length of name.
            // Average name length of 8 is good.
            // Example: 4 distinct names all of length 3,4,5,6 offer an average len 4.5.
            // Scale 4 names by 4.5/8 = 2.25
            // Example: 2 distinct names all of length 10,12 offer an average len 11.
            // Scale 2 names by 11/8 = 2.75
            int nameLen = 0;
            for (String nm : distinctNames) {
                nameLen += nm.length();
            }

            // Note -- # of names cancels out in weighting. Looking at # of characters in
            // names in a bucket is a good weighting.
            // Example: Adde, Harti, Ras -- numerous short names appear many countries but
            // do not offer a lot of signal.
            nameDiversity = (double) nameLen / AVERAGE_NAME_LEN;
            locationSpecifity = (double) locLength / 2;
            evidenceWeight = countScale * (locationSpecifity + nameDiversity) / 10;

            List<Place> places = heatmap.get(loc);
            if (places == null) {
                log.debug("experimental HeatMap not working");
                continue;
            }
            for (Place pl : places) {
                String pid = internalPlaceID(pl);
                if (visitedPlaces.contains(pid)) {
                    continue;
                }
                visitedPlaces.add(pid);
                PlaceEvidence ev = new PlaceEvidence(pl, ruleName, evidenceWeight);
                List<PlaceCandidate> trackedMentions = mentionMap.get(pl.getPlaceID());
                if (trackedMentions == null) {
                    // Something is wrong if you get here.
                    continue;
                }

                // Increment the score once for a given geo location.
                for (PlaceCandidate name2 : trackedMentions) {
                    if (offsets.contains(name2.start)) {
                        continue;
                    }
                    offsets.add(name2.start);
                    name2.addEvidence(ev);
                    name2.incrementPlaceScore(pl, ev.getWeight(), ruleName);
                    log.debug("\t{} {}", ruleName, pl);
                }
            }
            log.debug("{} {} {}", loc, distinctNames, places);
        }
        /* CLEAR here as memory consumption or object trails may linger with unwanted consequences. */
        reset();
    }
}
