package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.opensextant.xtext.Content;
import org.opensextant.xtext.ConvertedDocument;

/**
 * Untested OLE Message Converter
 * 
 * @author ubaldino
 *
 */
public class OLEMessageConverter extends ConverterAdapter {

    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc) throws IOException {
        ConvertedDocument msgDoc = new ConvertedDocument(doc);

        try {
            MAPIMessage msg = new MAPIMessage(in);

            // If your message is Latin-1 text... there is no real easy way to get bytes of raw message text
            // to ensure it is UTF-8
            // TextTranscodingConverter.setTextAndEncoding(doc, msg.getM);
            // By default this may be UTF-8 text.
            msgDoc.setText(msg.getTextBody());
            /* Would prefer not to set encoding here without knowing  or attempting to derive it properly */
            msgDoc.setEncoding(ConvertedDocument.OUTPUT_ENCODING);

            AttachmentChunks[] chunks = msg.getAttachmentFiles();
            
            for (AttachmentChunks c : chunks) {
                Content child = new Content();
                child.id = getAttachmentName(c.attachLongFileName, c.attachFileName);
                child.content = c.attachData.getValue();
                msgDoc.addRawChild(child);
            }

            // Get a subject line.
            try {
                msgDoc.addTitle(msg.getSubject());
            } catch (ChunkNotFoundException err) {
                msgDoc.addTitle("(MIME error: unable to get subject)");
            }

            // Get a date line.
            try {
                msgDoc.addCreateDate(msg.getMessageDate());
            } catch (ChunkNotFoundException err) {
                // 
            }

            // Get author.
            try {
                msgDoc.addAuthor(msg.getDisplayFrom());
            } catch (ChunkNotFoundException err) {
                msgDoc.addAuthor("(MIME error: unable to get sender)");
            }

            return msgDoc;

        } catch (Exception xerr) {
            throw new IOException("Unable to parse content", xerr);
        } finally {
            in.close();
        }
    }


    /*
     * get a name for the attachment.
     */
    private String getAttachmentName(Object longName, Object shortName) {
        if (longName != null) {
            return longName.toString();
        }
        if (shortName != null) {
            return shortName.toString();
        }
        return null;
    }

}
