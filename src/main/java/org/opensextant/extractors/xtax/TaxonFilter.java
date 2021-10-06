package org.opensextant.extractors.xtax;

import java.util.regex.Pattern;

import org.opensextant.extraction.MatchFilter;

public class TaxonFilter extends MatchFilter {

    public TaxonFilter() {
        // no configuration
    }

    static Pattern anyInvalidPunct = Pattern.compile("[\\p{Punct}&&[^-_.'`]]+");

    public static boolean irregularPunctPatterns(final String t) {
        return anyInvalidPunct.matcher(t).find();
    }

    /**
     * Find any reason to filter out Taxons.
     */
    @Override
    public boolean filterOut(String val) {
        return irregularPunctPatterns(val);
    }
}
