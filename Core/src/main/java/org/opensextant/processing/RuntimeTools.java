package org.opensextant.processing;

public class RuntimeTools {

    /**
     * Easily digestible version of memory report.
     *
     * @return KB of memory
     */
    public static final int reportMemory() {
        Runtime R = Runtime.getRuntime();
        long usedMemory = R.totalMemory() - R.freeMemory();
        return (int) (usedMemory / 1024);
    }
}
