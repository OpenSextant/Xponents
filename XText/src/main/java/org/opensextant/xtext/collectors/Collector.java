package org.opensextant.xtext.collectors;

public interface Collector {

    /**
     * 
     * @return
     */
    public String getName();
    /**
     * 
     * @return
     */
    public void collect() throws Exception;
}
