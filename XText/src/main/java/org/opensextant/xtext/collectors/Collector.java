package org.opensextant.xtext.collectors;

public interface Collector {

    final static char PATH_SEP = '/';

    /**
     *
     * @return name of the collector
     */
    String getName();

    /**
     * Invokes collection.
     */
    void collect() throws Exception;
}
