package org.opensextant.xtext.converters;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.Content;
import org.opensextant.xtext.ConvertedDocument;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * 
 * Needs further testing.  Usage notes:
 * 
 * From calling class, try using parser as this:
 * 
    RecursiveContentParser recursiveParser = new RecursiveContentParser(parser);
    // recursiveParser.reset();
    //// Hack: nature of document -- if it is a compound doc -- should be determined ahead of time.
    // 
    // boolean isRecursive = isCompoundObject(doc.getName());
    // List<Content> childEntries = null;
    //if (isRecursive) {
    //    recursiveParser.parse(input, handler, metadata, ctx);
    //    childEntries = recursiveParser.getChildren();
    //}
    *
     * report embedded items, although currently it might just be metadata.
     * If content here is strictly metadata --- content is NULL -- then 
     * downstream usage should test for that and not try to manage child content, e.g,. 
     * writing content to xtext folder on disk.
     
    
    if (isRecursive) {
        for (Content child : childEntries) {
            textdoc.addRawChild(child);
        }
    }
   
     *
     * given a path or a name of object, determine if by default it is usually a compound document, e.g., Mail message.
     * @param pathOrName
     * @return
     
    public boolean isCompoundObject(String pathOrName) {
        // For now only one silly example.
        return FileUtility.getFileDescription(pathOrName) == FileUtility.MESSAGE_MIMETYPE;
    }
 * 
 * @author tallison,  Tika committer
 * @author ubaldino
 *
 */
public class RecursiveContentParser extends ParserDecorator {
    private static final long serialVersionUID = 1L;
    private List<Content> embeddedChildren = new ArrayList<Content>();
    int i = 0;

    public RecursiveContentParser(Parser parser) {
        super(parser);
    }
    
    public void reset(){
        embeddedChildren.clear();
    }

    private final static String[] filenameFields = {Metadata.RESOURCE_NAME_KEY, Metadata.TIKA_MIME_FILE};
    @Override
    public void parse(InputStream stream, ContentHandler contentHandler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        Metadata tmpMetadata = new Metadata();
        String fName  = "";
        for (String fKey: filenameFields){
            fName = metadata.get(fKey);
            if (StringUtils.isNotBlank(fName)){
                break;
            } 
        }

        super.parse(stream, contentHandler, tmpMetadata, context);

        /*
         * Metadata for a single entry.
         */
        Content raw = new Content();
        //Properties fm = new Properties();
        //fm.setProperty(ConvertedDocument.CHILD_ENTRY_KEY, fName);
        raw.id = fName;
        raw.content  = null; // Would like payloads for individually identified objects here. 
        
        for (String name : tmpMetadata.names()) {
            String[] values = tmpMetadata.getValues(name);
            
            // Huh???
            for (int i = 0; i < values.length; ++i) {                
                raw.meta.setProperty(name, values[0]);
                System.out.println(name +"="+values[i]);
                //break;
            }
        }
        
        embeddedChildren.add(raw);
    }

    public List<Content> getChildren() {
        return Collections.unmodifiableList(embeddedChildren);
    }

}
