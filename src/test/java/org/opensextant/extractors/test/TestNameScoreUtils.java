package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.junit.Test;

public class TestNameScoreUtils {

    @Test
    public void test() {
        String a = "who dat";
        String b = "me too";
        int editDist = LevenshteinDistance.getDefaultInstance().apply(a, b);
        assertEquals(editDist, 6);

        editDist = LevenshteinDistance.getDefaultInstance().apply(a, a);
        assertEquals(editDist, 0);
        String c = "who dem";
        editDist = LevenshteinDistance.getDefaultInstance().apply(a, c);
        assertEquals(editDist, 2);
    }

}
