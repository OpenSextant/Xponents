package org.opensextant.xtext.collectors;

import java.io.File;
import java.io.IOException;

import org.opensextant.xtext.ConvertedDocument;

public interface CollectionListener {

    /**
     * Listen for collected and converted document
     */
    public void collected(ConvertedDocument doc, String filepath) throws IOException;

    /**
     * Listen for collected raw files.
     */
    public void collected(File doc) throws IOException;
    
    /**
     * If the item identified by ID already exists, the collector will pass by it quietly
     * @param oid
     * @return true if item exists in the caller's collection.  Implementor can then make a decision what to do if item exists or not.
     */
    public boolean exists(String oid) ;
}
