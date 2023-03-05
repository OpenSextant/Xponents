package org.opensextant.extractors.test;

import org.junit.Test;
import org.opensextant.extractors.geo.rules.PostalCodeFilter;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestPostalFilters {


    private static final void print(String msg) {
        System.out.println(msg);
    }

    /**
     * Test detection of invalid punctuation in postal codes
     */
    @Test
    public void testPunctuation() {

        PostalCodeFilter rule = new PostalCodeFilter(2);
        // Invalid
        assertTrue(rule.hasInvalidPunct("AB%66"));
        assertTrue(rule.hasInvalidPunct("AB(66"));
        // Valid
        assertFalse(rule.hasInvalidPunct("AB66"));
        assertFalse(rule.hasInvalidPunct("ab66"));
        assertFalse(rule.hasInvalidPunct("AB-66"));
        assertFalse(rule.hasInvalidPunct("AB-66"));
        assertFalse(rule.hasInvalidPunct(" AB 66 "));
    }
}
