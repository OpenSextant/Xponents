package org.opensextant.xtext.converters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
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

//import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeUtility;
import javax.mail.internet.MimeUtility;

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
    private List<String> textEncodings = new LinkedList<String>();

    /**
     * 
     */
    @Override
    protected ConvertedDocument conversionImplementation(InputStream in, File doc)
            throws IOException {

        attachmentNumber = 0;
        textEncodings.clear();
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
    public ConvertedDocument convertMimeMessage(Message msg, File doc) throws MessagingException,
            IOException {
        ConvertedDocument parentMsgDoc = new ConvertedDocument(doc);
        parentMsgDoc.is_RFC822_attachment = true;
        //parentMsgDoc.setEncoding(parseCharset(msg.getContentType()));

        setMailAttributes(parentMsgDoc, msg);

        StringBuilder rawText = new StringBuilder();
        // Since content is taken from file system, use file name
        String messageFilePrefix = (doc != null ? FilenameUtils.getBaseName(doc.getName())
                : parentMsgDoc.id);

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
    private void setMailAttributes(ConvertedDocument msgdoc, Message message)
            throws MessagingException {
        String msg_id = getMessageID(message);
        if (msg_id==null){
            return;
        }
        msgdoc.id = getShorterMessageID(msg_id);

        String mailSubj = message.getSubject();
        msgdoc.addTitle(mailSubj);

        Address[] sender = message.getFrom();
        String sender0 = null;
        if (sender != null && sender.length > 0) {
            sender0 = sender[0].toString();
            msgdoc.addAuthor(sender0);
        }

        Date d = message.getSentDate();
        String dt = (d != null ? d.toString() : "");
        msgdoc.addCreateDate(d != null ? d : msgdoc.filetime);

        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "msgid", msg_id);
        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "sender", sender0);

        msgdoc.addUserProperty(MAIL_KEY_PREFIX + "date", dt);
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
        msgId = extractAngleValue(msgIds[0]);
        String[] msgid_parts = msgId.split("@");
        msgLocalId = msgId;
        if (msgid_parts.length > 1) {
            msgLocalId = msgid_parts[0];
        }
        return msgLocalId;
    }

    /**
     * Given a global msg ID, create an ID that should be relatively unique.
     * 
     * @param globalId
     * @return
     */
    public static String getShorterMessageID(String globalId) {
        String msgId = extractAngleValue(globalId);
        String[] msgid_parts = msgId.split("@");

        String shorter = msgId;
        if (msgid_parts.length > 1) {
            shorter = msgid_parts[0];
        }
        
        // Clean up MSG ID
        // The same ID that is used to archive will be used to record in DB.
        //
        shorter = TextUtils.replaceAny(shorter, "#$.%~", "_");

        return shorter;
    }

    public static String MAIL_KEY_PREFIX = "mail:";

    /**
     * Whacky...  each child attachment will have some knowledge about the containing mail messsage which carried it.
     * 
     * @param parent
     * @param child
     */
    private void copyMailAttrs(ConvertedDocument parent, Content child) {

        if (child.encoding != null) {
            child.meta.setProperty("encoding", child.encoding);
        }

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
    public void parseMessage(Part bodyPart, ConvertedDocument parent, StringBuilder buf,
            String msgPrefixId) throws IOException {

        InputStream partIO = null;
        ++attachmentNumber;

        try {

            PartMetadata meta = new PartMetadata(bodyPart);
            //String charset = (meta.charset == null ? "UTF-8" : meta.charset);
            textEncodings.add(meta.charset);

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

            logger.debug("Charset for part is {}", meta.charset);

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
                boolean isTextPlain = bodyPart.isMimeType("text/plain");
                if (part instanceof String) {

                    /* We will take the first charset encoding found for the body text of hte message.
                     *  If there are HTML views of the data, those individual documents will be child documents with their own encodings.
                     */
                    if (meta.charset != null && parent.getEncoding() == null) {
                        parent.setEncoding(meta.charset);
                    }
                    String text = (String) part;
                    if (!isTextPlain) {
                        // Decode TEXT from MIME base64 or QP encoded data.
                        // TODO: Is this necessary? The mime libraries seem to handle base64 unencoding automatically
                        // (at least for text/plain attachments). -jgibson
                        logger.debug("{}# Save String MIME part", msgPrefixId);
                        if (meta.isQP() || meta.isBase64()) {
                            try {
                                partIO = IOUtils.toInputStream(text);
                                byte[] textBytes = decodeMIMEText(partIO, meta.transferEncoding);
                                if (meta.charset != null) {
                                    text = new String(textBytes, meta.charset);
                                } else {
                                    text = new String(textBytes);
                                }
                            } catch (Exception decodeErr) {
                                logger.error("Decoding error with bare text in body of message");
                            }
                        } else {
                            logger.debug("Other encoding is unaccounted: {}", meta.transferEncoding);
                        }
                    }

                    if (meta.isAttachment()) {
                        Content child = createBaseChildContent(filename, meta);
                        if (child.encoding == null) {
                            child.encoding = "UTF-8";
                        }

                        child.content = text.getBytes(child.encoding);
                        copyMailAttrs(parent, child);
                        parent.addRawChild(child);
                    } else {
                        // Note, before trying any of these decoding trick

                        buf.append(TextUtils.delete_controls(text));

                        buf.append("\n*******************\n");
                        // Note, the "=XX" sequence is reserved for RFC822 encoding of special chars and non-ASCII.
                        // So I avoid using "=====".... as a separator.
                    }

                    // Exit point
                    return;

                } else if (part instanceof InputStream) {

                    // Retrieve byte stream.
                    partIO = (InputStream) part;
                    Content child = createChildContent(filename, partIO, meta);
                    copyMailAttrs(parent, child);
                    parent.addRawChild(child);

                    // Exit point.
                    return;
                } else {
                    /* MCU: identify unknown MIME parts */
                    logger.debug("Skipping this an unknown bodyPart type: "
                            + part.getClass().getName());
                    //return;
                }
            }

            if (bodyPart instanceof MimeBodyPart && !bodyPart.isMimeType("multipart/*")) {

                logger.debug("{}# Saving {} ", msgPrefixId, filename);
                if (meta.disposition == null || meta.isAttachment) {

                    partIO = ((MimeBodyPart) bodyPart).getRawInputStream();
                    Content child = createChildContent(filename, partIO, meta);

                    copyMailAttrs(parent, child);
                    if (meta.isHTML() && (meta.isInline() || (!meta.isAttachment()))) {
                        child.meta.setProperty(MAIL_KEY_PREFIX + "html-body", "true");
                    }

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
     * Abstract the encoding issue.  
     * @param data 
     * @param enc  a transfer encoding named in the multipart header, see MimeUtility.decode() for more detail
     * @return byte data for the stream.  Caller still has to decode to proper charset.
     * @throws IOException 
     * @throws Exception 
     */
    private static byte[] decodeMIMEText(InputStream data, String enc) throws Exception {
        InputStream decodedContent = null;
        try {
            decodedContent = MimeUtility.decode(data, enc);
            return IOUtils.toByteArray(decodedContent);
        } finally {
            if (decodedContent != null) {
                decodedContent.close();
            }
        }

    }

    /**
     * More conveniently create Child item. This will attempt to decode the multipart encoding, mainly "quoted-printable" data should be decoded prior to saving.
     * Lastly, the content bytes are always left as their native charset encoding.
     * Versus, text strings, which will be automatically in parseMessage() and saved as UTF-8 
     * 
     * @param file_id
     * @param input
     * @return
     * @throws IOException
     */
    private Content createChildContent(String file_id, InputStream input, PartMetadata meta)
            throws IOException {
        Content child = createBaseChildContent(file_id, meta);
        // Plain text is likely handled up above as (String)part are encountered in-line.
        // Here HTML attachments need to be decoded.
        if (meta.isHTML() && (meta.isQP() || meta.isBase64())) {
            try {
                child.content = decodeMIMEText(input, meta.transferEncoding);
            } catch (Exception decoderErr) {
                logger.error("MIME Decoding failed with parameters: {}", meta.mimeType);
            }
        } else {
            logger.debug("Other encoding is unaccounted: {}", meta.transferEncoding);
        }

        // Default or last resort.
        if (child.content == null) {
            child.content = IOUtils.toByteArray(input);
        }

        return child;
    }

    /**
     * Create a Child item with all of the metadata populated correctly.
     *
     * @param file_id
     * @param meta
     * @return
     */
    private Content createBaseChildContent(String file_id, PartMetadata meta) {
        Content child = new Content();
        child.id = file_id;
        child.encoding = meta.charset;
        child.meta.setProperty(ConvertedDocument.CHILD_ENTRY_KEY, file_id);

        child.meta.setProperty(MAIL_KEY_PREFIX + "disposition",
                (meta.disposition == null ? "none" : meta.disposition));

        if (meta.contentId != null) {
            child.meta.setProperty(MAIL_KEY_PREFIX + "content-id", meta.contentId);
        }

        child.mimeType = meta.mimeType;

        return child;
    }

    /** Parse out charset encoding spec from MIME content-type header 
     */
    private final static Pattern charsetPat = Pattern.compile("charset=['\"]?([-_\\w]+)['\"]?");

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
        public String transferEncoding = null;
        public String disposition = null;
        public String contentId = null;

        private boolean istext = false;
        private boolean ishtml = false;
        private boolean iscal = false;
        private boolean isImage = false;
        private boolean isAttachment = false;
        private boolean isInline = false;
        public String desc = "data";

        @Override
        public String toString() {
            return mimeType + " charset=" + charset + " desc=" + desc;
        }

        public PartMetadata(Part bodyPart) throws MessagingException {

            mimeType = bodyPart.getContentType();

            if (bodyPart.isMimeType("text/plain")) {
                istext = true;
                desc = "text";
            } else if (bodyPart.isMimeType("text/html")) {
                ishtml = true;
                desc = "HTML";
            } else if (bodyPart.isMimeType("text/calendar")) {
                iscal = true;
                desc = "Calendar-Invite";
            }

            String filename = bodyPart.getFileName();
            if (filename != null) {
                String ext = FilenameUtils.getExtension(filename);
                iscal = (iscal || (ext.equalsIgnoreCase("ics") || ext.equalsIgnoreCase("ical")));

                isImage = (FileUtility.getFileDescription(filename) == FileUtility.IMAGE_MIMETYPE);

                desc = "Image";
            }

            if (istext || ishtml) {
                String header = bodyPart.getContentType();
                charset = parseCharset(header);
                if (charset == null) {
                    String[] x = bodyPart.getHeader("Content-Type");
                    if (x.length > 0) {
                        charset = parseCharset(x[0]);
                    }
                }

                String[] headers = bodyPart.getHeader("Content-Transfer-Encoding");
                if (headers != null && headers.length > 0) {
                    transferEncoding = headers[0];
                }
            }

            disposition = bodyPart.getDisposition();
            if (Part.ATTACHMENT.equals(disposition)) {
                isAttachment = true;
            } else if (Part.INLINE.equals(disposition)) {
                isInline = true;
            }

            String[] contentIds = bodyPart.getHeader("Content-Id");
            if (contentIds != null && contentIds.length > 0 && (!StringUtils.isBlank(contentIds[0]))) {
                contentId = extractAngleValue(contentIds[0]);
            }
        }

        /** is QP encoding 
         */
        public boolean isQP() {
            if (transferEncoding == null) {
                return false;
            }
            return "quoted-printable".equalsIgnoreCase(transferEncoding);
        }

        public boolean isBase64() {
            if (transferEncoding == null) {
                return false;
            }
            return "base64".equalsIgnoreCase(transferEncoding);

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

        public boolean isAttachment() {
            return isAttachment;
        }

        public boolean isInline() {
            return isInline;
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
        //String cs = MimeUtility.javaCharset(mimespec);
        //if (cs != null) { return cs;}
        // Ah, thanks for nothing, JavaMail.   MIME content-type given is "a/b; charset='c'"  the response should be "c".
        // JavaMail cannot pull out the char set from content-type header.  

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
        String tmp = TextUtils.squeeze_whitespace(text).replaceAll(
                "[\"'&;.“”)(%$?:<>\\*#~!@\\\\/ ]", "_");

        // Trim trailing "__" from resulting file name.
        for (int x = tmp.length() - 1; x > 0; --x) {
            char ch = tmp.charAt(x);
            if (ch != '_') {
                tmp = tmp.substring(0, x + 1);
                break;
            }
        }

        return tmp;
    }

    private static final Pattern ANGLE_EXTRACTOR = Pattern.compile("<(.+)>");

    /**
     * Parse 'value' from '<value>'
     *
     * @param value
     * @return
     */
    private static String extractAngleValue(String value) {
        Matcher regex = ANGLE_EXTRACTOR.matcher(value);
        if (regex.matches()) {
            String msgId = regex.group(1);
            return msgId;
        }
        return value;
    }
}
