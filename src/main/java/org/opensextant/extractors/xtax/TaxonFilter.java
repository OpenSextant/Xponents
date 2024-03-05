package org.opensextant.extractors.xtax;

import java.io.IOException;
import java.util.regex.Pattern;

import org.opensextant.extraction.TagFilter;

public class TaxonFilter extends TagFilter {


    public TaxonFilter() throws IOException {
        super();
    }

    static final Pattern anyInvalidPunct = Pattern.compile("[\\p{Punct}&&[^-_.'`]]+");

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
