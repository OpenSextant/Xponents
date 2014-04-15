/**
 *
 * Copyright 2013-2014 OpenSextant.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.xtext.collectors.mailbox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;

import org.apache.commons.lang.StringUtils;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.ConversionListener;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.Collector;
import org.opensextant.xtext.converters.MessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMailCrawl extends MailClient implements ConversionListener, Collector {

    /**
     * A collection listener to consult as far as how to record the found & converted content
     * as well as to determine what is worth saving.
     * 
     */
    protected CollectionListener listener = null;
    private Logger log = LoggerFactory.getLogger(getClass());

    public DefaultMailCrawl(MailConfig cfg, String archive) {
        super(cfg, archive);
    }

    final static SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyyMMdd");

    private File createDateFolder(Date d) {
        String dateKey = dateKeyFormat.format(d.getTime());

        File dateFolder = new File(archiveRoot + File.separator + dateKey);
        if (!dateFolder.exists()) {
            if (!dateFolder.mkdir()) {
                return null;
            }
        }
        return dateFolder;
    }

    private File createMessageFolder(File parent, String msgid) {

        File msgFolder = new File(parent.getAbsolutePath() + File.separator + msgid);
        if (!msgFolder.exists()) {
            if (!msgFolder.mkdir()) {
                return null;
            }
        }
        return msgFolder;
    }

    /**
     * Important that you set a listener if you want to see what was captured.
     * As well as optimize future harvests.  Listener tells the collector if the item in question was harvested or not.
     * @param l
     */
    public void setListener(CollectionListener l) {
        listener = l;
    }

    @Override
    public void setConverter(XText conversionManager) {
        converter = conversionManager;
        if (converter != null) {
            converter.setConversionListener(this);
        }
    }

    /**
     * Email 
     * @throws IOException 
     */
    @Override
    public void handleConversion(ConvertedDocument doc, String filepath) {

        if (listener == null) {
            // nothing to do.
            return;
        }

        if (doc == null) {
            log.debug("Item was not converted, FILE={}", filepath);
            return;
        }

        try {
            // Converted document is discovered, then enters this interface method.
            //
            listener.collected(doc, filepath);

            if (doc.hasChildren()) {
                // NOTE:  our internal ID for children documents may not match what is preserved on disk in XText metadata.
                // 
                for (ConvertedDocument child : doc.getChildren()) {

                    // This creates a new ID out of the parent doc id and the attachment filename.
                    String uniqueValue = String.format("%s#%s", doc.id, child.filename);
                    child.setId(TextUtils.text_id(uniqueValue));

                    // Record the child attachment.
                    listener.collected(child, child.filepath);
                }
            }
        } catch (IOException err) {
            log.error(
                    "Failed to record or manage the email message and/or its attachments, FILE={}",
                    filepath);
        }
    }

    /**
     * TODO:
     * 
     * pull all mail messages, 
     * - create reasonable  FILE.msg  file name 
     * - use XText to iterate over each msg file for conversion
     * - reimplement 
     * 
     */
    public void collect() throws MessagingException {

        File dateFolder = createDateFolder(new Date());
        if (dateFolder == null) {
            log.error("Unable to create directory: " + dateFolder);
            return;
        }

        connect();
        Message[] messages = getMessages();
        if (messages == null) {
            log.info("No messages available - Exiting MailClient now");
            disconnect();
            return;
        }

        int readCount = 0;
        int totalCount = 0;
        int available = messages.length;
        int errCount = 0;

        // Loop through all available messages
        // Exit early if 10 'serious' errors are encountered.
        //
        for (Message message : messages) {

            ++totalCount;

            // Exit if too many errors.
            if (errCount > 10) {
                break;
            }

            try {

                if (config.doneReading(messages.length, readCount)) {
                    // Done here.
                    break;
                }

                /**
                 * Silently ignore deleted messages; items deleted while we were
                 * in session
                 */
                if (message.isExpunged()) {
                    log.info("Message deleted during session; Unable to collect. Mail Subj: {}",
                            message.getSubject());
                    continue;
                }

                boolean newMessage = !message.isSet(Flags.Flag.SEEN);

                log.debug("Message Subject: " + message.getSubject() + "  new?: " + newMessage);

                boolean setForDeleteNow = false;
                if (message.getSubject() == null) {
                    log.info("Empty message title MSG number=" + message.getMessageNumber());
                    continue;
                }

                if ((!config.isReadNewMessagesOnly() || newMessage)) {

                    // 1. Identify the email message.
                    //    and determine if you need to capture it again.
                    // 
                    String messageFilename = MessageConverter.createSafeFilename(message
                            .getSubject());
                    if (messageFilename.length() > 60) {
                        messageFilename = messageFilename.substring(0, 60);
                    }
                    if (StringUtils.isBlank(messageFilename)) {
                        messageFilename = "No_Subject";
                    }

                    String msgId = MessageConverter.getMessageID(message);
                    if (msgId == null) {
                        log.error("How can a message ID be null? SUBJ={}", message.getSubject());
                        continue;
                    }

                    try {
                        if (listener != null && listener.exists(msgId)) {
                            // You already collected this item. Ignoring.
                            // 
                            continue;
                        }
                    } catch (Exception err1) {
                        log.error("Collection error with mail.", err1);
                        continue;
                    }

                    readCount++;

                    if (log.isDebugEnabled()) {
                        log.debug("Message: {}", message.getSubject());
                        String msg = String.format("Processing message: %s / %s of available: %s",
                                readCount, totalCount, available);
                        log.debug(msg);
                    }

                    try {
                        File msgFolder = createMessageFolder(dateFolder, msgId);

                        // Save the file and do the conversion
                        // 
                        String msgFilepath = String.format("%s/%s.eml", msgFolder, messageFilename);
                        File msgFile = new File(msgFilepath);
                        OutputStream msgIO = new FileOutputStream(msgFile);
                        // Requirement:  Write data to disk first, saving a ".eml" file.
                        message.writeTo(msgIO);

                        // NOTE: here the act of converting the ".eml" file now invokes 
                        // the default MessageConverter logic and finally calls this as the ConversionListener
                        // 
                        converter.convertFile(msgFile);

                    } catch (Exception msgErr) {
                        log.error("Failed reading, saving document", msgErr);
                    }

                    if (!config.isReadOnly() && config.isDeleteOnRead()) {
                        message.setFlag(Flags.Flag.DELETED, true);
                        String dbg = String.format("Processing message: %d / %d of available:%d",
                                readCount, totalCount, available);
                        log.debug(dbg);
                        setForDeleteNow = true;
                    }
                }

                // NOT a new message and we want to delete old
                //
                if (!newMessage && config.isDeleteOld() && !setForDeleteNow && !config.isReadOnly()) {
                    message.setFlag(Flags.Flag.DELETED, true);
                    log.debug("Deleting message: #{}", totalCount);
                }

            } catch (javax.mail.FolderClosedException connErr) {
                ++errCount;
            } catch (MessagingException me) {
                log.error("Failed to read messsage #{}", totalCount, me);
                ++errCount;
            }
        }

        disconnect();
    }

}
