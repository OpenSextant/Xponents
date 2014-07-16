package org.opensextant.xtext.collectors;

public interface Collector {

    public final static char PATH_SEP = '/';
    
    /**
     * 
     * @return name of the collector
     */
    public String getName();
    
    /**
     * Invokes collection.
     */
    public void collect() throws Exception;
}
