package org.opensextant.xtext.collectors.mailbox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
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
     * A collection listener to consult as far as how to record the found & converted content
     * as well as to determine what is worth saving.
     * 
     */
    protected CollectionListener listener = null;
    private Logger log = LoggerFactory.getLogger(getClass());
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
     * @param pstFilepath
     * @throws IOException
     */

    public OutlookPSTCrawler(String pstFilepath) throws IOException {
        this(new File(pstFilepath));
    }

    /**
     * 
     * @param pstFile
     * @throws IOException
     */
    public OutlookPSTCrawler(File pstFile) throws IOException {
        pst = pstFile;
        if (!pst.exists()) {
            throw new IOException("PST file does not exist: " + pstFile.getAbsolutePath());
        }
        defaultOutputName = FilenameUtils.getBaseName(pst.getName()) + "_pst";
    }

    public void configure() throws ConfigException {
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
    public void collect() throws Exception {
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
        PSTFile pstStore = new PSTFile(pst);
        processFolder(pstStore.getRootFolder());
    }

    private XText converter = null;

    /**
     * If a converter is provided, it will be used to convert attachments.
     * PST API does not provide access to full email stream, so most objects - tasks, mail, contacts, etc.
     * are retrieved as text already.
     * 
     * Caller is responsible for mananging the XText caching options.  
     *   
     * @param conversionManager
     */
    public void setConverter(XText conversionManager) {
        converter = conversionManager;
    }

    private int depth = 0;
    private int MAX_DEPTH = 10;

    /**
     * 
     * @param folder
     * @throws PSTException
     * @throws IOException
     * @throws ConfigException
     */
    protected void processFolder(PSTFolder folder) throws PSTException, IOException,
            ConfigException {
        log.info("Folder:" + folder.getDisplayName());
        ++depth;
        if (depth >= MAX_DEPTH) {
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
     * @param grp
     * @param sub
     * @return  grouping.
     * @throws IOException 
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

    public String processAppointment(String groupName, String folderName, PSTAppointment appt)
            throws IOException {
        File appts = createGroupFolder(groupName, folderName);
        String fname = MessageConverter.createSafeFilename(appt.getDisplayName());

        StringBuilder buf = new StringBuilder();
        buf.append(appt.getDisplayName());
        buf.append(" appointment");
        buf.append(ITEM_SEP);

        formatFields(parseValidEntries(appt.toString()), buf);

        String savedPath = makePath(appts, fname + ".txt");
        FileUtility.writeFile(buf.toString(), savedPath);
        return savedPath;
    }

    private static String ITEM_SEP = "\n=====================\n";

    /**
     * Save contact to a file.  This uses the less elegant "toString()" method, which prints to buffer all fields 
     * even if they are empty or null. 
     * 
     * @param folderName
     * @param contact
     * @return saved path
     * @throws IOException 
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
     * @param folderName  containing folder
     * @param list
     * @return saved path
     * 
     * @throws IOException
     * @throws PSTException 
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
     * @param pff_string
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
     * @param dir
     * @param fname
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
     * @param msg
     * @throws IOException
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
     * @param container
     * @param msgSubj
     * @param msgId
     * @return  absolute path to file that will contain all related info for a given message.
     * @throws IOException 
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
        msgId = TextUtils.text_id(msgId);

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
     * @param msg
     * @throws PSTException
     * @throws IOException
     * @throws ConfigException 
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
     * @param stream
     * @param savePath
     * @throws IOException
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

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Map string buffer output to fielded items, by parsing line items.
     * 
     * @author ubaldino
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
