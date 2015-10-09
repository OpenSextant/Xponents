package org.opensextant.xtext.collectors;

import java.io.IOException;

import org.opensextant.ConfigException;

public interface Collector {

    final static char PATH_SEP = '/';

    /**
     *
     * @return name of the collector
     */
    String getName();

    /**
     * Invokes collection.
     * @throws IOException if an I/O failure occurs
     * @throws ConfigException if a collector is mis-configured.
     */
    void collect() throws IOException, ConfigException;
}
