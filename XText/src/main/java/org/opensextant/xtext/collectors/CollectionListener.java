package org.opensextant.xtext.collectors;

import java.io.File;
import java.io.IOException;

import org.opensextant.xtext.ConvertedDocument;

public interface CollectionListener {

    /**
     * Listen for collected and converted document
     */
    public void collected(ConvertedDocument doc) throws IOException;

    /**
     * Listen for collected raw files.
     */
    public void collected(File doc) throws IOException;
    
    /**
     * If the item identified by ID already exists, the collector will pass by it quietly
     * @param oid
     * @return
     */
    public boolean exists(String oid) ;
}
