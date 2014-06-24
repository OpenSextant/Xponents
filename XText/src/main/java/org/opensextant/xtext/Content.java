package org.opensextant.xtext;

import java.util.Properties;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class Content {

    /**
     * Trivial pairing of a named/ID'd byte array, e.g., embedded object/file pulled from a containing document.
     */
    public Content() {

    }

    /**
     * any identifier for tracking the content.  If this is an object with no name or ID, caller should assign an enumeration, e.g. 
     * parentID + "_part" + N,  e.g., docA_part4
     */
    public String id = null;

    public String encoding = null;

    /**
     * The mime type of the content.
     */
    public String mimeType = null;

    /**
     * a raw byte array for the content in motion.
     * This helps capture raw data before you have made a decision to write the data out and where
     */
    public byte[] content = null;

    /**
     * a proxy for the metadata sheet that eventually will end up in ConvertedDocument.meta
     */
    public Properties meta = new Properties();

    /**
     * carry Tika properties where possible
     */
    public Metadata tikaMetadata = null;
    /**
     * carry Tika MediaType where possible
     */
    public MediaType tikaMediatype = null;
}
