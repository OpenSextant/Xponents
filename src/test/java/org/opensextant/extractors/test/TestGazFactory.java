package org.opensextant.extractors.test;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensextant.extractors.geo.GazetteerUpdateProcessorFactory;

public class TestGazFactory {

    /**
     * Trivial gazetteer factory filters.
     */
    @Test
    public void testFactoryFilters() {
        assertTrue(GazetteerUpdateProcessorFactory.ignoreShortAlphanumeric("to 6", "S"));
        assertTrue(GazetteerUpdateProcessorFactory.ignoreShortAlphanumeric("Jim's 6", "S"));
        assertTrue(!GazetteerUpdateProcessorFactory.ignoreShortAlphanumeric("Jim's 6", "P"));
    }

}
