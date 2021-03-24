package org.opensextant.output;

import org.opensextant.data.Geocoding;
import org.opensextant.extraction.TextMatch;

public interface MatchInterpreter {

    /**
     * For a given match in text return the geocoding object
     * for that match, if one exists.
     *
     * @param m TextMatch -- which may or may not be geographic in nature.
     * @return
     */
    Geocoding getGeocoding(TextMatch m);

}
