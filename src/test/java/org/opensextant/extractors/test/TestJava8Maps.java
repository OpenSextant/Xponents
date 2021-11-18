package org.opensextant.extractors.test;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.HashMap;

public class TestJava8Maps {

    /**
     *  A quick demonstration of Java 8 practices to leverage.
     */
    @Test
    public void testHashKeys(){
        HashMap<String, Integer> m = new HashMap<>();
        m.get("ABC");
        m.computeIfAbsent("ABC", newVal -> Integer.valueOf(4));
        assertTrue( m.containsKey("ABC"));
    }
}
