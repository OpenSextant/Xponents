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

import org.opensextant.ConfigException;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.ConversionListener;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.Collector;
import org.opensextant.xtext.converters.MessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class DefaultMailCrawl.
 */
public class DefaultMailCrawl extends MailClient implements ConversionListener, Collector {

    /**
     * A collection listener to consult as far as how to record the found &amp; converted content
     * as well as to determine what is worth saving.
     *
     */
    protected CollectionListener listener = null;
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Instantiates a new default mail crawl.
     *
     * @param cfg the cfg
     * @param archive the archive
     */
    public DefaultMailCrawl(MailConfig cfg, String archive) {
        super(cfg, archive);
    }

    /** The Constant dateKeyFormat. */
    final static SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyyMMdd");

    /**
     * Creates the date folder.
     *
     * @param d date
     * @return folder representing the date (e.g., collection date)
     */
    protected File createDateFolder(Date d) {
        String dateKey = dateKeyFormat.format(d.getTime());
        String path = String.format("%s%s%s", archiveRoot, Collector.PATH_SEP, dateKey);

        File dateFolder = new File(path);
        if (!dateFolder.exists()) {
            if (!dateFolder.mkdir()) {
                return null;
            }
        }
        return dateFolder;
    }

    /**
     * Creates the message folder.
     *
     * @param parent  parent container
     * @param msgid message ID
     * @return created folder that will contain the message and any related attachments.
     */
    protected File createMessageFolder(File parent, String msgid) {

        String path = String.format("%s%s%s", parent.getAbsolutePath(), Collector.PATH_SEP, msgid);
        File msgFolder = new File(path);
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
     * @param l listener to use
     */
    public void setListener(CollectionListener l) {
        listener = l;
    }


    /* (non-Javadoc)
     * @see org.opensextant.xtext.collectors.mailbox.MailClient#setConverter(org.opensextant.xtext.XText)
     */
    @Override
    public void setConverter(XText conversionManager) {
        converter = conversionManager;
        if (converter != null) {
            converter.setConversionListener(this);
        }
    }

    /**
     * Email parser, converter, recorder.  This routine handles one message that
     * may have a number of attachments (children)
     * 
     * IOException is logged if handling of children documents+conversions fails.
     * TODO: handleConversion should throw IOException or use listener to report errors for this document
     *
     * @param doc the doc
     * @param filepath the filepath
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
            // Parent doc will be ./A.eml
            // Child Attachments will be ./A_eml/b.doc
            //
            listener.collected(doc, filepath);

            if (doc.hasChildren()) {
                // NOTE:  our internal ID for children documents may not match what is preserved on disk in XText metadata.
                //
                for (ConvertedDocument child : doc.getChildren()) {

                    // This creates a new ID out of the parent doc id and the attachment filename.
                    String uniqueValue = String.format("%s,%s", doc.id, child.filename);
                    String _id = uniqueValue;
                    try {
                        _id = TextUtils.text_id(uniqueValue);
                    } catch (Exception err) {
                        log.error("hashing Error", err);
                    }
                    child.setId(_id);

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
     * @throws IOException on failure to connect or collect.
     */
    @Override
    public void collect() throws IOException, ConfigException {

        Date d = new Date();
        File dateFolder = createDateFolder(d);
        if (dateFolder == null) {
            log.error("Unable to create directory for: {}", d);
            return;
        }
        Message[] messages = null;
        try {
            connect();
            messages = getMessages();
            if (messages == null) {
                log.info("No messages available - Exiting MailClient now");
                disconnect();
                return;
            }
        } catch (MessagingException javaMailErr) {
            throw new IOException("Unable to connect or get messages", javaMailErr);
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
                String subject = message.getSubject();
                if (message.getSubject() == null) {
                    log.info("Empty message title MSG number=" + message.getMessageNumber());
                    //continue;
                    subject = "No_Subject";
                }

                if ((!config.isReadNewMessagesOnly() || newMessage)) {

                    // 1. Identify the email message.
                    //    and determine if you need to capture it again.
                    //
                    String messageFilename = MessageConverter.createSafeFilename(subject);
                    if (messageFilename.length() > 60) {
                        messageFilename = messageFilename.substring(0, 60);
                    }

                    String msgId = MessageConverter.getMessageID(message);

                    if (msgId == null) {
                        log.error("How can a message ID be null? SUBJ={}", message.getSubject());
                        continue;
                    }
                    msgId = MessageConverter.getShorterMessageID(msgId);

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

                    // Save file in archive, Convert it, etc.
                    int status = saveMessageToFile(dateFolder, message, msgId, messageFilename);
                    if (status < 0) {
                        ++errCount;
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
            } catch (IOException writeErr) {
                log.error("Failed to write msg.eml #{}", totalCount, writeErr);
                ++errCount;
            }
        }

        // Well, if work was actually done but you fail to close the connection
        // Its not a failure ... just make sure you figure out how to close cleanly.
        // Error on close is likely rare.
        try {
            disconnect();
        } catch (Exception javaMailErrOnClose) {
            log.error("Unkosher disconnect", javaMailErrOnClose);
        }
    }

    /**
     * A very specific MESSAGE -&gt;&gt; FILE archiving method.
     * Mail item will end up in:
     * 
     *   YYYYMMDD/MSGID/SUBJ.eml  .. the original email.
     *   YYYYMMDD/MSGID/SUBJ_eml/ .. attachments here..
     *
     * @param dateFolder the date folder
     * @param msg javamail message
     * @param oid  message ID
     * @param fname file name to save message
     * @return 0 on success, -1 on error
     * @throws IOException unknown I/O error.
     * @throws MessagingException the messaging exception
     */
    protected int saveMessageToFile(File dateFolder, Message msg, String oid, String fname)
            throws IOException, MessagingException {
        OutputStream msgIO = null;
        try {
            File msgFolder = createMessageFolder(dateFolder, oid);

            // Save the file and do the conversion
            //
            String msgFilepath = String.format("%s/%s.eml", msgFolder, fname);
            File msgFile = new File(msgFilepath);
            msgIO = new FileOutputStream(msgFile);
            // Requirement:  Write data to disk first, saving a ".eml" file.
            msg.writeTo(msgIO);

            // NOTE: here the act of converting the ".eml" file now invokes
            // the default MessageConverter logic and finally calls this as the ConversionListener
            //
            converter.convertFile(msgFile);
            return 0;

        } catch (Exception msgErr) {
            log.error("Failed reading, saving document", msgErr);
            return -1;
        } finally {
            msgIO.close();
        }
    }
}
