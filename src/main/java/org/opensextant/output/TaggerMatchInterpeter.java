package org.opensextant.output;

import org.opensextant.data.Geocoding;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;

public class TaggerMatchInterpeter implements MatchInterpreter {


    /**
     * Trivial override of the default Match Interpreter on GISDataFormatter.
     * See Examples for usage.
     * 
     * @param m
     * @return
     */
    public Geocoding getGeocoding(TextMatch m) {
        Geocoding geocoding = null;
        if (m instanceof Geocoding) {
            geocoding = (Geocoding) m;
        } else if (m instanceof PlaceCandidate) {
            geocoding = ((PlaceCandidate) m).getChosen();
        }
        return geocoding;
    }
}
