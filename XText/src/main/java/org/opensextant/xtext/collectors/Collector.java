package org.opensextant.xtext.collectors;

public interface Collector {

    public final static char PATH_SEP = '/';
    
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
