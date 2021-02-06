package org.opensextant.extractors.test;

import org.opensextant.extractors.geo.GazetteerUpdateProcessorFactory;
import static org.junit.Assert.*;

import org.junit.Test;

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
