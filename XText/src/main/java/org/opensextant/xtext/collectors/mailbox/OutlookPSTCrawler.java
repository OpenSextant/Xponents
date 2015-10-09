package org.opensextant.xtext.collectors.mailbox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opensextant.ConfigException;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.Collector;
import org.opensextant.xtext.converters.MessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pff.PSTAppointment;
import com.pff.PSTAttachment;
import com.pff.PSTContact;
import com.pff.PSTDistList;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;
import com.pff.PSTRecipient;

/**
 * OutlookPSTCrawler traverses a PST file and pulls out: E-Mail files + attachments, Contacts, Appointments, etc.
 * saving originals in a reasonable folder structure.
 *
 *  Mail in particular will be foldered by date-line, then short subject name folder.
 *
 *  Given SomeFile.PST, the resultant output folder created will look like the example below.
 *  <pre>
 *    SomeFile_PST/
 *      + Contacts
 *      + Appointments
 *      + Mail-Inbox
 *      +--2014-04-05
 *         +-- RE__Shipping_Invoice/
 *             +-- RE__Shipping_Invoice.eml
 *             +-- Invoice.PDF
 *  </pre>
 *
 *  Supposedly on April 5th, 2014, an email was received (Mail-Inbox); titled "RE: Shipping Invoice".
 *  To make the archival of such content obvious, the subject line is converted to a safe folder/filename.
 *
 *  Usage:
 *
 *  <code>
 *   crawler = OutlookPSTCrawler( path )
 *   //crawer...  set modes, set listener, converter, etc.
 *   crawler.configure()
 *   crawler.collect()
 *
 *   </code>
 * @author ubaldino
 *
 */
public class OutlookPSTCrawler implements Collector {

    /**
     * A collection listener to consult as far as how to record the found &amp; converted content
     * as well as to determine what is worth saving.
     *
     */
    protected CollectionListener listener = null;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final DateTimeFormatter FOLDER_DATE = DateTimeFormat.forPattern("yyyy-MM-dd");
    private File pst = null;
    private String defaultOutputName = null;
    private File outputDir = null; // The parent folder that will contain the output.  /tmp/
    private File outputPSTDir = null; // The output folder.  /tmp/My_pst

    /**
     * Incremental mode simply allows you to reuse the same folder to contain the output interleaving new items
     */
    public boolean incrementalMode = true;

    /**
     * Overwite mode allows the crawler to overwrite existing folders, attachments, objects etc by the same name.
     */
    public boolean overwriteMode = false;

    /**
     *
     * @param pstFilepath  input PST
     * @throws IOException if file fails to load
     */

    public OutlookPSTCrawler(String pstFilepath) throws IOException {
        this(new File(pstFilepath));
    }

    /**
     *
     * @param pstFile  input PST
     * @throws IOException if file fails to load
     */
    public OutlookPSTCrawler(File pstFile) throws IOException {
        pst = pstFile;
        if (!pst.exists()) {
            throw new IOException("PST file does not exist: " + pstFile.getAbsolutePath());
        }
        defaultOutputName = FilenameUtils.getBaseName(pst.getName()) + "_pst";
    }

    /**
     * Beware -- you can set the path for the PST output (outputPSTDir) or you can set the path its parent path (outputDir).
     * Outside apps may want to control the path setup.   To use the default,  setOutputDir(); configure();
     * @throws ConfigException if output folder could not be set
     */
    public void configure() throws ConfigException {

        if (outputPSTDir == null) {

            if (outputDir == null) {
                throw new ConfigException("Output Dir is not configured");
            }

            if (outputDir.exists()) {
                outputPSTDir = new File(String.format("%s/%s", outputDir.getAbsolutePath(),
                        defaultOutputName));
                if (!incrementalMode && outputPSTDir.exists()) {
                    throw new ConfigException(
                            "Output Dir contains target, but you are not in overwrite mode");
                }

                if (!outputPSTDir.exists()) {
                    try {
                        FileUtility.makeDirectory(outputPSTDir);
                    } catch (IOException err) {
                        throw new ConfigException("Unable to create target", err);
                    }
                }
            } else {
                throw new ConfigException(
                        "Please create containing output directory. DIR does not exist:"
                                + outputDir.getAbsolutePath());
            }
        }

        log.info(" Input: PST =  " + pst.getAbsolutePath());
        log.info(" Modes: Incremental =" + incrementalMode);
        log.info(" Modes: Overwrite =" + overwriteMode);
        log.info(" Output: Target " + outputPSTDir);
    }

    @Override
    public String getName() {
        return "OutlookPSTCrawler by XText";
    }

    @Override
    public void collect() throws IOException, ConfigException {
        //
        // Logic:  Traverse PST file.
        //     it contains mail, contacts, tasks, notes, other stuff?
        //
        // Replicate folder structure discovered.
        // Mail and date-oriented items should be filed by date. For now, YYYY-MM-DD is fine.
        //
        // For mail messages, review DefaultMailCralwer:
        //  - for each message
        //    save message to disk;  create parent folder to contain message contents
        //    run text conversion individually on attachments.
        //
        //  - structure:
        //    ./Mail/
        //         2014-04-09/messageABC.eml
        //         2014-04-09/messageABC/attachment1.doc

        log.info("Traversing PST Folders for FILE={}", pst);
        try {
            PSTFile pstStore = new PSTFile(pst);
            processFolder(pstStore.getRootFolder());
        } catch (PSTException err) {
            throw new ConfigException("Failure with PST traversal", err);
        }
    }

    private XText converter = null;

    /**
     * If a converter is provided, it will be used to convert attachments.
     * PST API does not provide access to full email stream, so most objects - tasks, mail, contacts, etc.
     * are retrieved as text already.
     *
     * Caller is responsible for mananging the XText caching options.
     *
     * @param conversionManager  XText instance
     */
    public void setConverter(XText conversionManager) {
        converter = conversionManager;
    }

    private int depth = 0;
    private final int maxDepth = 10;

    /**
     *
     * @param folder  found folder from PST
     * @throws PSTException PST API error
     * @throws IOException I/O failure
     * @throws ConfigException  XText configuration error
     */
    protected void processFolder(PSTFolder folder) throws PSTException, IOException,
    ConfigException {
        log.info("Folder:" + folder.getDisplayName());
        ++depth;
        if (depth >= maxDepth) {
            --depth;
            log.error("MAX DEPTH reached. Avoid infinite recursion");
            return;
        }
        if (folder.hasSubfolders()) {
            Vector<PSTFolder> children = folder.getSubFolders();
            for (PSTFolder child : children) {
                processFolder(child);
            }
        }

        log.info("\t\tProcessing content items");

        int count = folder.getContentCount();
        if (count > 0) {
            PSTObject msg = null;
            while ((msg = folder.getNextChild()) != null) {
                // As libPST is organized with PSTMessage (email) being a base class, it must only be used as a default.
                // Try all other subclasses first.
                //
                String savedItem = null;
                if (msg instanceof PSTContact) {
                    savedItem = processContact("Contacts", folder.getDisplayName(),
                            (PSTContact) msg);
                } else if (msg instanceof PSTDistList) {
                    savedItem = processDistList("Lists", folder.getDisplayName(), (PSTDistList) msg);
                } else if (msg instanceof PSTAppointment) {
                    savedItem = processAppointment("Appointments", folder.getDisplayName(),
                            (PSTAppointment) msg);
                } else if (msg instanceof PSTMessage) {
                    processMessage(folder.getDisplayName(), (PSTMessage) msg);
                } else {
                    log.info("\tItem: {}; Type:{} created at {}", msg.getDisplayName(),
                            msg.getMessageClass(), msg.getCreationTime());
                }

                if (savedItem != null && listener != null) {
                    listener.collected(new File(savedItem));
                }
            }
        }
        --depth;
    }

    /**
     *
     * @param grp  A major group of Outlook objects
     * @param sub  a subgroup
     * @return resulting grouping string that represents an output subfolder
     * @throws IOException on I/O failure
     */
    protected File createGroupFolder(String grp, String sub) throws IOException {
        String groupFolder = null;
        if (sub.equalsIgnoreCase(grp)) {
            groupFolder = grp;
        } else {
            groupFolder = String.format("%s/%s", grp, sub);
        }

        File grpFile = new File(String.format("%s/%s", this.outputPSTDir.getAbsolutePath(),
                groupFolder));
        if (!grpFile.exists()) {
            FileUtility.makeDirectory(grpFile);
        }
        return grpFile;

    }

    /**
     *
     * @param groupName  group name
     * @param folderName  folder name
     * @param appt   PST API object
     * @return file name of processed object
     * @throws IOException err
     * @throws PSTException err
     * @throws ConfigException err
     */
    public String processAppointment(String groupName, String folderName, PSTAppointment appt)
            throws IOException, PSTException, ConfigException {
        File appts = createGroupFolder(groupName, folderName);
        String fname = MessageConverter.createSafeFilename(appt.getSubject());

        StringBuilder buf = new StringBuilder();
        buf.append(appt.getSubject());
        buf.append(" appointment");
        buf.append(ITEM_SEP);

        List<String> rList = getRecipients(appt);
        if (rList != null) {
            addHeaderText(buf, "X-recipients", StringUtils.join(rList, "; "));
        } else {
            addHeaderText(buf, "X-recipients", "No Recipients");
        }

        // Get a list of attachments.
        List<String> attFiles = processAttachments(appt, appts);
        if (attFiles != null && !attFiles.isEmpty()) {
            addHeaderText(buf, "X-attchments", StringUtils.join(attFiles, "; "));
        }
        buf.append("\n\n");
        buf.append(appt.getBody().trim());

        //formatFields(parseValidEntries(appt.toString()), buf);

        String savedPath = makePath(appts, fname + ".txt");
        FileUtility.writeFile(buf.toString(), savedPath);
        return savedPath;
    }

    /**
     *
     * @param appt  PST API object
     * @return list of recipients
     * @throws PSTException err
     * @throws IOException err
     */
    public List<String> getRecipients(PSTAppointment appt) throws PSTException, IOException {

        int recipients = appt.getNumberOfRecipients();
        if (recipients > 0) {
            List<String> rList = new ArrayList<String>();
            for (int r = 0; r < recipients; ++r) {
                PSTRecipient R = appt.getRecipient(r);
                rList.add(String.format("%s <%s>", R.getDisplayName(), R.getEmailAddress()));
            }
            return rList;
        }
        return null;
    }

    private static String ITEM_SEP = "\n=====================\n";

    /**
     * Save contact to a file.  This uses the less elegant "toString()" method, which prints to buffer all fields
     * even if they are empty or null.
     *
     * @param groupName  string
     * @param folderName string
     * @param contact   PST API object
     * @return saved path
     * @throws IOException on err
     */
    public String processContact(String groupName, String folderName, PSTContact contact)
            throws IOException {
        File contacts = createGroupFolder(groupName, folderName);
        String fname = MessageConverter.createSafeFilename(contact.getDisplayName());

        StringBuilder buf = new StringBuilder();
        buf.append(contact.getDisplayName());
        buf.append(" contact");
        buf.append(ITEM_SEP);

        formatFields(parseValidEntries(contact.toString()), buf);
        String savedPath = makePath(contacts, fname + ".txt");
        FileUtility.writeFile(buf.toString(), savedPath);
        return savedPath;
    }

    /**
     * Distribution Lists take from Contacts
     *
     * @param groupName  string
     * @param folderName string
     * @param list  PST API list
     * @return saved path
     *
     * @throws IOException on err
     * @throws PSTException on err
     */
    public String processDistList(String groupName, String folderName, PSTDistList list)
            throws IOException, PSTException {
        File contacts = createGroupFolder(groupName, folderName);
        String fname = MessageConverter.createSafeFilename(list.getDisplayName());

        StringBuilder buf = new StringBuilder();
        buf.append(list.getDisplayName());
        buf.append(" distribution list");
        buf.append(ITEM_SEP);
        buf.append("Address:\t" + list.getEmailAddress() + "\n");
        buf.append("Comment:\t" + list.getComment() + "\n");
        buf.append("\n");

        Object[] members = list.getDistributionListMembers();
        for (Object member : members) {
            formatFields(parseValidEntries(member.toString()), buf);
            buf.append("\n\t-------\n");
        }

        String savedPath = makePath(contacts, fname + ".txt");
        FileUtility.writeFile(buf.toString(), savedPath);
        return savedPath;
    }

    protected static void formatFields(List<OutlookPSTCrawler.Field> fields, StringBuilder buf) {

        for (Field f : fields) {
            buf.append(f.label);
            buf.append(":\t");
            buf.append(f.value);
            buf.append("\n");
        }
    }

    protected static String formatField(Field f) {
        return String.format("%s:\t%s\n", f.label, f.value);
    }

    /**
     * Retrieve a variety of fields from a PSTObject.toString() -- most of them will be empty.
     * This method will try to find in the string the non-null entries.
     *
     * @param pff_string string.
     * @return list of field values.
     */
    protected static List<OutlookPSTCrawler.Field> parseValidEntries(String pff_string) {

        if (StringUtils.isBlank(pff_string)) {
            return null;
        }

        List<OutlookPSTCrawler.Field> result = new ArrayList<OutlookPSTCrawler.Field>();
        String[] kvPairs = pff_string.split("\n");
        for (String kv : kvPairs) {

            String[] fieldVal = kv.split(":\\s*", 2);

            if (fieldVal.length == 2) {
                if (StringUtils.isNotBlank(fieldVal[1])) {
                    Field f = new OutlookPSTCrawler.Field(fieldVal[0], fieldVal[1]);
                    result.add(f);
                }
            }
        }

        return (result.isEmpty() ? null : result);
    }

    /**
     * Apache Commons file utils "concat(dir, file)" makes a mess of file names.
     * Java can support "/" equally well on all platforms.
     * there is no apparent need to use platform specific file separators in this context.
     * @param dir  contianing dir
     * @param fname sub folder
     * @return full path.
     */
    private static String makePath(File dir, String fname) {
        return String.format("%s/%s", dir.getAbsolutePath(), fname);
    }

    public void processMessage(String folderName, PSTMessage msg) throws PSTException, IOException,
    ConfigException {

        String dateKey = FOLDER_DATE.print(msg.getCreationTime().getTime());
        log.info("\tItem: {}; created at {}", msg.getSubject(), dateKey);
        File dateFolder = new File(String.format("%s/%s/%s", this.outputPSTDir.getAbsolutePath(),
                folderName, dateKey));

        if (!dateFolder.exists()) {
            FileUtility.makeDirectory(dateFolder);
        }

        String msgSubj = msg.getSubject();

        if (StringUtils.isBlank(msg.getSubject())) {
            msgSubj = "NO_SUBJECT";
        }

        // Create a folder to contain all the message content.
        File msgFolder = createFolder(dateFolder, msgSubj, msg.getInternetMessageId());

        // Get a list of attachments.
        List<String> attFiles = processAttachments(msg, msgFolder);

        // Create the final text version of the email message.  TODO:  RFC822 dump would be ideal.
        String msgFile = saveMailMessage(msg, msgFolder, attFiles, msgSubj);

        /* TODO: Send msg to listener after attachments are saved, as you might want to report
         * attachment metadata along with msg file.
         */
        if (listener != null) {
            listener.collected(new File(msgFile));
        }
    }

    /**
     * Archive a PST Mail Message as a Text file.
     *
     * @param msg PST API message
     * @param msgFolder output folder
     * @param attFiles attachments
     * @param subj subject
     * @return final saved path
     * @throws IOException err saving message
     */
    public String saveMailMessage(PSTMessage msg, File msgFolder, List<String> attFiles, String subj)
            throws IOException {
        String msgName = MessageConverter.createSafeFilename(subj);

        String msgText = msg.getBody();
        StringBuilder buf = new StringBuilder();

        // Replicating some of SMTP Header / RFC822 metadata here.
        //
        addHeaderText(buf, "From", getSender(msg));
        addHeaderText(buf, "To", getRecipients(msg));
        addHeaderText(buf, "Subject", subj); // msg.getSubject()
        addHeaderText(buf, "Date", msg.getCreationTime());
        addHeaderText(buf, "MessageId", msg.getInternetMessageId());
        addHeaderText(buf, "X-container-file", this.pst.getName());
        //addHeaderText(buf, "X-saved-on", );
        if (attFiles != null && !attFiles.isEmpty()) {
            addHeaderText(buf, "X-attchments", StringUtils.join(attFiles, "; "));
        }
        buf.append("\n\n");
        buf.append(msgText);
        String savedPath = makePath(msgFolder, msgName + ".txt");
        FileUtility.writeFile(buf.toString(), savedPath);

        return savedPath;

    }

    private static String getSender(PSTMessage msg) {
        String sentBy = msg.getSenderName();
        String sentByEmail = msg.getSenderEmailAddress();

        if (StringUtils.isNotBlank(sentBy) && StringUtils.isNotBlank(sentByEmail)) {
            return String.format("%s <%s>", sentBy, sentByEmail);
        }
        if (StringUtils.isNotBlank(sentByEmail)) {
            return String.format("<%s>", sentByEmail);
        }
        if (StringUtils.isNotBlank(sentBy)) {
            return sentByEmail;
        }
        return "Unknown Sender";
    }

    private static String getRecipients(PSTMessage msg) {
        return msg.getRecipientsString();
    }

    private static void addHeaderText(StringBuilder buf, String field, Object value) {
        buf.append(field);
        buf.append(":\t");
        if (value != null) {
            buf.append(value.toString());
        } else {
            buf.append("(empty)");
        }
        buf.append("\n");
    }

    /**
     * length of message folder name that contains a complete email.
     */
    public static int MESSAGE_FOLDER_LEN = 40;

    /**
     * An arbitrary method of finding a unique path for a MIME message, without opening up content on disk.
     * This creates the folder if needed.
     *
     * TODO:  given we want the folder structure to be intuitive and readable, the file names may not reflect uniqueness
     * where subject lines for email may be repetitive.  By contrast, message IDs are not duplicative.  The length of file names
     * from using both message ID and subject line is an issue.  This routine attempts to get a relatively unique path using both.
     *
     * @param container  output container relative path
     * @param msgSubj message subject
     * @param msgId message ID
     * @return  absolute path to file that will contain all related info for a given message.
     * @throws IOException err
     */
    protected File createFolder(File container, String msgSubj, String msgId) throws IOException {

        if (msgId == null) {
            throw new IOException("RFC Error - MessageId is null.");
        }
        // Get the message
        String msgName = MessageConverter.createSafeFilename(msgSubj);
        if (msgName.length() > MESSAGE_FOLDER_LEN) {
            msgName = msgName.substring(0, MESSAGE_FOLDER_LEN);
        }

        // Message Ids for SMTP can be quite generic.  No good way to find unique value, unless you try a hash/digest of some sort.
        //
        try {
            msgId = TextUtils.text_id(msgId);
        } catch (Exception err) {
            log.error("Hashing err - Message ID left as-is.", err);
        }

        // Get first 2 chars of message ID + last 2 chars.
        int l = msgId.length();
        String uniqueness = msgId.substring(0, 2) + msgId.substring(l - 3, l - 1);

        File msgFolder = new File(String.format("%s/%s_%s", container, msgName, uniqueness));
        if (!msgFolder.exists()) {
            FileUtility.makeDirectory(msgFolder);
        }
        return msgFolder;
    }

    /**
     * REFERENCE:  libpst home page, https://code.google.com/p/java-libpst/
     * @author com.pff
     * @author ubaldino -- cleaned up name checks.
     * @param msg  PST API message
     * @param msgFolder output target
     * @return list of attachment filenames
     * @throws PSTException err
     * @throws IOException err
     * @throws ConfigException err
     */
    public List<String> processAttachments(PSTMessage msg, File msgFolder) throws PSTException,
    IOException, ConfigException {

        int numberOfAttachments = msg.getNumberOfAttachments();
        List<String> attachmentFilenames = new ArrayList<String>();
        for (int x = 0; x < numberOfAttachments; x++) {
            PSTAttachment attach = msg.getAttachment(x);
            // both long and short filenames can be used for attachments
            String filename = attach.getLongFilename();
            if (StringUtils.isBlank(filename)) {
                filename = attach.getFilename();
                if (StringUtils.isBlank(filename)) {
                    filename = String.format("attachment%d", x + 1);
                }
            }

            File attPath = new File(String.format("%s/%s", msgFolder.getAbsolutePath(), filename));
            savePSTFile(attach.getFileInputStream(), attPath.getAbsolutePath());

            attachmentFilenames.add(filename);

            if (listener != null) {
                listener.collected(attPath);
            }

            if (converter != null) {
                converter.convert(attPath);
            }
        }

        return attachmentFilenames;
    }

    public static final int PST_INTERNAL_BLOCK_SIZE = 8176;

    /**
     * Guidance on I/O from PFF library authors, regarding getting data from PST format.
     *
     * @param stream  input
     * @param savePath ouput
     * @throws IOException err
     */
    private static void savePSTFile(InputStream stream, String savePath) throws IOException {

        FileOutputStream out = new FileOutputStream(savePath);
        //InputStream attachmentStream = attach.getFileInputStream();
        // 8176 is the block size used internally and should give the best performance
        byte[] buffer = new byte[PST_INTERNAL_BLOCK_SIZE];
        int count = stream.read(buffer);
        while (count == PST_INTERNAL_BLOCK_SIZE) {
            out.write(buffer);
            count = stream.read(buffer);
        }
        byte[] endBuffer = new byte[count];
        System.arraycopy(buffer, 0, endBuffer, 0, count);
        out.write(endBuffer);
        out.close();
        stream.close();
    }

    public File getOutputDir() {
        return outputDir;
    }

    /**
     * Set the parent container of PST output.
     * @param outputDir path to output
     */
    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * set the location of the output.
     * @param pstDir path of PST output
     */
    public void setOutputPSTDir(File pstDir) {
        this.outputPSTDir = pstDir;
    }

    /**
     * Map string buffer output to fielded items, by parsing line items.
     *
     */
    static class Field {
        public String label = null;
        public String value = null;

        public Field(String l, String v) {
            label = l;
            value = v;
        }
    }
}
