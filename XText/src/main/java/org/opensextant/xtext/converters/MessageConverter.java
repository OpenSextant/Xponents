package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.Content;
import org.opensextant.xtext.ConvertedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* 
 * This Mail Message parser/converter should do its work on *.msg or *.eml files saved to disk as standard RFC822 
 * documents.   A single message doc may have attachments, nested emails, etc.  The input here is a single message file
 * 
 * The organization of such files is determined by the caller app.  If content is retrieved from an email account, 
 * it could be organized to reflect the account's email folders or not.  One thing is certain:  document count multiplies 
 * when we try to convert multimedia message into its individual artifacts.
 * 
 * File.msg
 *  +attached.doc
 *  +imagery.jpg
 * 
 * This one file (with two attachments) then becomes in "XText" speak:
 * 
 * xtext/File.msg.txt         text of message, here File.msg.txt backs the original based on file name alone. It contains the parent metadata of the mail msg.
 * File/attached.doc          attachment one
 * File/imagery.jpg           attachment two
 * 
 * File/xtext/attached.doc.txt    text of one,
 * File/xtext/imagery.jpg.txt     text of two.
 * 
 *  ... 1 file becomes 5 additional files, in this example.
 * 
 * Ho hum...
 *  https://issues.apache.org/jira/browse/TIKA-1222 -- Attachments are not parsed.
 */
//import org.apache.tika.parser.mail.RFC822Parser;

public class MessageConverter extends ConverterAdapter {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private Session noSession = Session.getDefaultInstance(new Properties());
    private int attachmentNumber = 0;

    /**
     * 
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc) throws IOException {

        attachmentNumber = 0;
        try {
            // Connect to the message file
            // 
            MimeMessage msg = new MimeMessage(noSession, in);
            return convertMimeMessage(msg, doc);
        } catch (Exception xerr) {
            throw new IOException("Unable to parse content", xerr);
        } finally {
            in.close();
        }
    }

    /**
     * Convert the MIME Message with or without the File doc.
     *  -- live email capture from a mailbox:  you have the MimeMessage; there is no File object
     *  -- email capture from a filesystem:   you retrieved the MimeMessage from a File object
     *  
     * @param msg
     * @param doc
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public ConvertedDocument convertMimeMessage(Message msg, File doc) throws MessagingException, IOException {
        ConvertedDocument parentMsgDoc = new ConvertedDocument(doc);
        setMailAttributes(parentMsgDoc, msg);

        StringBuilder rawText = new StringBuilder();
        // Since content is taken from file system, use file name
        String messageFilePrefix = (doc != null ? FilenameUtils.getBaseName(doc.getName()) : parentMsgDoc.id);

        // Find all attachments and plain text.
        parseMessage(msg, parentMsgDoc, rawText, messageFilePrefix);

        parentMsgDoc.setText(rawText.toString());

        return parentMsgDoc;
    }

    /**
     * Copy innate Message metadata into the ConvertedDocument properties to save that metadata in the normal place.
     * This metadata will also be replicated down through children items to reflect the fact the attachment was sent via this message.
     * 
     * @param msgdoc
     * @param message
     * @throws MessagingException
     */
    private void setMailAttributes(ConvertedDocument msgdoc, Message message) throws MessagingException {
        msgdoc.id = getMessageID(message);
        String mailSubj = message.getSubject();
        msgdoc.addTitle(mailSubj);
        msgdoc.addCreateDate(message.getSentDate());

        Address[] sender = message.getFrom();
        String sender0 = null;
        if (sender != null && sender.length > 0) {
            sender0 = sender[0].toString();
            msgdoc.addAuthor(sender0);
        }

        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "msgid", msgdoc.id);
        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "sender", sender0);
        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "date", message.getSentDate().toString());
        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "subject", mailSubj);

    }

    /**
     * Retrieve the Identifier part of a message, that is <id@server> we want the "id" part.
     * 
     * @param message
     * @return
     * @throws MessagingException 
     */
    public static String getMessageID(Message message) throws MessagingException {
        String[] msgIds = message.getHeader("Message-Id");
        if (msgIds == null || msgIds.length == 0) {
            //logger.error("No Message ID!");
            return null;
        }
        String msgId = null;
        String msgLocalId = null;
        //String msgIdFilename = null;
        msgId = parseMessageId(msgIds[0]);
        String[] msgid_parts = msgId.split("@");
        msgLocalId = msgId;
        if (msgid_parts.length > 1) {
            msgLocalId = msgid_parts[0];
        }
        return msgLocalId;
    }

    public static String MAIL_KEY_PREFIX = "mail:";

    /**
     * Whacky...  each child attachment will have some knowledge about the containing mail messsage which carried it.
     * 
     * @param parent
     * @param child
     */
    private void copyMailAttrs(ConvertedDocument parent, Content child) {

        for (String key : parent.getProperties().keySet()) {
            if (key.startsWith(MAIL_KEY_PREFIX)) {
                String val = parent.getProperty(key);
                if (val != null) {
                    child.meta.setProperty(key, val);
                }
            }
        }
    }

    /**
     * This is a recursive parser that pulls off attachments into Child content or saves plain text as main message text.
     * Calendar invites are ignored.
     * 
     * @param bodyPart
     * @param parent
     * @param buf
     * @throws IOException
     */
    public void parseMessage(Part bodyPart, ConvertedDocument parent, StringBuilder buf, String msgPrefixId)
            throws IOException {

        InputStream partIO = null;
        ++attachmentNumber;

        try {

            PartMetadata meta = new PartMetadata(bodyPart);
            String charset = (meta.charset == null ? "UTF-8" : meta.charset);
            String filename = bodyPart.getFileName();
            String fileext = meta.getPossibleFileExtension();
            if (filename != null) {
                fileext = FilenameUtils.getExtension(filename);
                logger.debug("original filename: " + filename);
            }

            boolean hasExtension = StringUtils.isNotBlank(fileext);
            if (!hasExtension) {
                logger.debug("Unknown message part");
                fileext = "dat";
            }

            if (filename == null && attachmentNumber > 1) {
                filename = String.format("%s-Att%d.%s", msgPrefixId, attachmentNumber, fileext);
            }

            logger.debug("Charset for part is: {}, given {}", charset, meta.charset);

            /*
             * Using isMimeType to determine the content type avoids fetching
             * the actual content data until we need it.
             */
            // IGNORE types: calendar.
            if (meta.isCalendar()) {
                logger.debug("{}# Ignore item", msgPrefixId);
                return;
            }

            if (meta.isHTML()) {
                //
                logger.debug("{}# Save HTML part as its own file", msgPrefixId);

            } else if (bodyPart.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) bodyPart.getContent();
                int count = mp.getCount();
                for (int i = 0; i < count; i++) {
                    // This step does not actually save any content, it calls
                    // itself to continue to break down the parts into the
                    // finest grained elements, at which point
                    parseMessage(mp.getBodyPart(i), parent, buf, msgPrefixId);
                }

                // Exit point
                return;

            } else if (bodyPart.isMimeType("message/rfc822")) {

                /* normal mail message body */
                parseMessage((Part) bodyPart.getContent(), parent, buf, msgPrefixId);
                // Exit point
                return;
            } else {
                Object part = bodyPart.getContent();
                if (part instanceof String) {

                    logger.debug("{}# Save String MIME part", msgPrefixId);
                    buf.append((String) part);
                    buf.append("\n===========");

                    // Exit point
                    return;

                } else if (part instanceof InputStream) {

                    // Retrieve byte stream.
                    Content child = createChildContent(filename, (InputStream) part);
                    copyMailAttrs(parent, child);
                    parent.addRawChild(child);

                    // Exit point.
                    return;
                } else {
                    /* MCU: identify unknown MIME parts */
                    logger.debug("Skipping this an unknown bodyPart type: " + part.getClass().getName());
                    //return;
                }
            }

            if (bodyPart instanceof MimeBodyPart && !bodyPart.isMimeType("multipart/*")) {

                String disposition = bodyPart.getDisposition();
                logger.debug("{}# Saving {} ", msgPrefixId, filename);
                if (disposition == null || disposition.equalsIgnoreCase(Part.ATTACHMENT)) {

                    Content child = createChildContent(filename, ((MimeBodyPart) bodyPart).getRawInputStream());
                    copyMailAttrs(parent, child);
                    parent.addRawChild(child);
                    return;
                }
            }

        } catch (MessagingException e2) {
            logger.error("Extraction Failed on Messaging Exception", e2);
        } finally {
            if (partIO != null) {
                partIO.close();
            }
        }
    }

    /**
     * More conveniently create Child item.
     * @param file_id
     * @param input
     * @return
     * @throws IOException
     */
    private Content createChildContent(String file_id, InputStream input) throws IOException {
        Content child = new Content();
        child.id = file_id;
        child.content = IOUtils.toByteArray(input);
        child.meta.setProperty(ConvertedDocument.CHILD_ENTRY_KEY, file_id);

        return child;
    }

    /** Parse out charset encoding spec from MIME content-type header 
     */
    private final static Pattern charsetPat = Pattern.compile("charset=([-_\\w]+)");

    /**
     * Help determine charset, object type, filename if any, and file extension
     * Mainly to guide how to parse, filter and employ the text content of this Part.
     * 
     * @author ubaldino
     *
     */
    class PartMetadata {

        public String mimeType = null;
        public String charset = null;
        private boolean istext = false;
        private boolean ishtml = false;
        private boolean iscal = false;
        private boolean isImage = false;

        public PartMetadata(Part bodyPart) throws MessagingException {

            if (bodyPart.isMimeType("text/plain")) {
                istext = true;
            } else if (bodyPart.isMimeType("text/html")) {
                ishtml = true;
            } else if (bodyPart.isMimeType("text/calendar")) {
                iscal = true;
            }

            String filename = bodyPart.getFileName();
            if (filename != null) {
                String ext = FilenameUtils.getExtension(filename);
                iscal = (iscal || (ext.equalsIgnoreCase("ics") || ext.equalsIgnoreCase("ical")));

                isImage = (FileUtility.getFileDescription(filename) == FileUtility.IMAGE_MIMETYPE);
            }

            if (istext || ishtml) {
                String header = bodyPart.getContentType();
                charset = parseCharset(header);
            }
        }

        public boolean isImage() {
            return isImage;
        }

        public boolean isCalendar() {
            return iscal;
        }

        public boolean isHTML() {
            return ishtml;
        }

        public boolean isText() {
            return istext;
        }

        public String getPossibleFileExtension() {
            if (isHTML()) {
                return "html";
            }
            if (isText()) {
                return "txt";
            }
            return null;
        }
    }

    /**
     * 
     * @param mimespec
     * @return
     */
    public static String parseCharset(String mimespec) {
        Matcher m = charsetPat.matcher(mimespec);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Get File Extension for known types. Otherwise MIME part should provide a
     * file name for such things. TODO: possibly switch to MIME4J and Apache
     * James
     * 
     * @param mimetype
     * @return
     */
    public static String getFileExtension(String mimetype) {
        if ("text/plain".equalsIgnoreCase(mimetype)) {
            return "txt";
        }
        if ("text/html".equalsIgnoreCase(mimetype)) {
            return "html";
        }
        return null;
    }

    /**
     * Create a safe filename from arbitrary text. That is no special shell
     * operators $, #, ?, >, <, *, ' ', etc.
     * 
     * @param text
     * @return file name constructed from input text and underscores in place of
     *         special chars.
     */
    public static String createSafeFilename(String text) {
        String tmp = TextUtils.squeeze_whitespace(text).replaceAll("[\"'&;.“”)(%$?:<>\\*#~!@\\\\/ ]", "_");

        for (int x = tmp.length() - 1; x > 0; --x) {
            char ch = tmp.charAt(x);
            if (ch != '_') {
                tmp = tmp.substring(0, x + 1);
                break;
            }
        }

        return tmp;
    }

    private static Pattern msgId_Cleaner = Pattern.compile("<(.+)>");

    /**
     * Parse 'id' from '<id>'
     * 
     * @param smtpId
     * @return
     */
    public static String parseMessageId(String smtpId) {
        Matcher regex = msgId_Cleaner.matcher(smtpId);
        if (regex.matches()) {
            String msgId = regex.group(1);
            return msgId;
        }
        return smtpId;
    }
}
