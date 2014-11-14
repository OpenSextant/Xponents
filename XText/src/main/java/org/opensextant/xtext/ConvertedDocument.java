/**
 *
 * Copyright 2009-2013 The MITRE Corporation.
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
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.opensextant.xtext;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import javax.activation.MimeType;

import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;
import org.opensextant.data.DocInput;

/**
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public final class ConvertedDocument extends DocInput {

    /**
     */
    public final static char UNIVERSAL_PATH_SEP = '/';

    private static final DateTimeFormatter dtfmt = DateTimeFormat.forPattern("yyyy-MM-dd");

    /** 
     * The url where this document (html, image, doc download) was found
     * The url-referrer the page containing the url.
     */
    public final static String URL_FIELD = "url";
    public final static String URL_REFERRER_FIELD = "url-referrer";

    public final static String[] fields = {
            // Dublin Core style metadata fields
            "title", "author", "creator_tool", "pub_date", "keywords", "subject", "filepath",
            "encoding",
            //
            // XText metadata.
            "filtered", "converter", "conversion_date", "encrypted", "filesize", "textsize",

            // Consideration for compound documents; if this instance is a child doc then what is the parent?
            "xtext_id", // REQUIRED -- the current document ID.
            "xtext_parent_id", "xtext_parent_path",

            // Additional metadata for web content.
            URL_FIELD, URL_REFERRER_FIELD };

    /**
     * Converters will populate metadatata. If the entry is an object or a file, its name will reflect that. 
     * Interpreting the entry name as a file name on a file system is up to the recipient.  E.g., Mail attachments 
     * might be file names;  Embedded objects may be object IDs.
     */
    public final static String CHILD_ENTRY_KEY = "entry.name";
    /**
     * if you are a child document/object, then 
     */
    public ConvertedDocument parent = null;
    private List<ConvertedDocument> children = null;
    private List<Content> childrenContent = null;

    public final static Set<String> valid_fields = new HashSet<String>(Arrays.asList(fields));
    public String filepath = null;

    public String filename = null;
    public String extension = null;
    public String basename = null;
    public Date filetime = null;
    public Date create_date = null;
    public String create_date_text = null;
    public String relative_path = null;
    public String textpath = null;
    public String encoding = null;
    private MimeType mimeType = null;
    JSONObject meta = new JSONObject();
    protected static boolean overwrite = true;
    /**
     * Duration in Milliseconds to convert
     */
    protected int conversion_time = 0;
    public boolean is_plaintext = false;
    public boolean is_converted = false;

    /**
     * Mail messages are ridiculous complex compound documents.
     *   The parent document and all its attachments are marked as  is_RFC822_attachment = true.
     *   HTML and text formats are most susceptible to encoding issues.
     */
    public boolean is_RFC822_attachment = false;
    public boolean is_webArchive = false;
    public boolean do_convert = true;
    /**
     * Represents if conversion was actually saved or not OR if file was
     * retrieved from cache successfully.
     */
    public boolean is_cached = false;
    public static boolean CONVERT_TO_UNIX_EOL = true;
    private File file = null;
    private File folder = null;

    public long filesize = -1;

    private boolean isChild = false;
    private boolean isParent = true; // Default

    public ConvertedDocument() {
        // Used only for uncaching previously saved converted docs.
        super(null, null);
    }

    public boolean WINDOWS_OS = FileUtility.isWindowsSystem();

    /**
     * Instantiates a new converted document.
     *
     * @param item file on disk
     */
    public ConvertedDocument(File item) {
        super(null, null);

        if (item != null) {
            this.file = item;

            this.filepath = item.getAbsolutePath();
            if (WINDOWS_OS) {
                // An effort to normalize paths.  This should have no effect on existing
                // Caches of data on existing *nix deployments. TOOD: Look at how URL could be used
                // e.g., file:/xyz/file.txt
                //       file:/C:/xyz/file.txt    are OS-independent and use "/" always.
                //
                this.filepath = PathManager.fixPath(filepath);
            }
            this.folder = item.getParentFile();
            this.filename = item.getName();
            this.filetime = getFiletime();
            this.is_plaintext = FileUtility.isPlainText(filename);
            this.filesize = file.length();
            this.extension = FilenameUtils.getExtension(filename);
            this.basename = FilenameUtils.getBaseName(filename);

            addProperty("filesize", this.filesize);
            addProperty("filepath", this.filepath);

            addProperty("conversion_date", dtfmt.print(new Date().getTime()));

            // Fill out TextInput basics:
            setId(this.filepath);
        }
    }

    /**
     * Record a URL that represents the source of the document.
     * 
     * @param url  the url to the item, e.g., http:/a.b.com/folder/my.doc
     * @param referringURL  the url where the doc was found, e.g., http:/a.b.com/folder/
     */
    public void addSourceURL(String url, String referringURL) {
        this.addProperty(URL_FIELD, url);
        this.addProperty(URL_REFERRER_FIELD, referringURL);
    }

    /**
     * Not that helpful: isChild or not is more meaningful.
     * @return true if this instance is a Parent item.
     */
    public boolean isParent() {
        return isParent;
    }

    /**
     * @return true if instance is a child document/object that was contained in or attached to some other document.
     */
    public boolean isChild() {
        return isChild;
    }

    public void setIsChild(boolean b) {
        isChild = b;
        isParent = !isChild;
    }

    protected File parentContainer = null;

    /**
     * Representation of the parent containing document.
     * @param par  parent obj
     */
    public void setParent(ConvertedDocument par) {
        // supporting only one level of nesting here.  Parents have children.
        // Parents do not have parents, etc.  Children do not have children.
        //
        isChild = true;
        isParent = false;

        parent = par;
        if (parent != null) {
            meta.put("xtext_parent_id", this.parent.id);
            meta.put("xtext_parent_path", this.parent.filepath);
            // Currently, Parent must be alread converted; and if any text output exists 
            // and was cached, then park children in the same parent folder.
            // 
            parentContainer = parent.parentContainer;
        }
    }

    /**
     * If this doc is a Parent doc, then evaluate what its "container" should be, that is to house child objects and their conversions.
     * If it is a child, ignore -- ensure child.parentContainer = child.parent.parentContainer
     * Children do not get to choose.
     * @deprecated  -- prefer to have children archived with originals always.  If you are pulling off binary data from originals (email, compound docs, etc) you will go nuts tracking it all.
     * @param saveEmbedded if embedded children should be saved to disk
     */
    @Deprecated
    public void evalParentContainer(boolean saveEmbedded) {
        if (!isParent) {
            return;
        }
        if (parentContainer != null) {
            return;
        }
        // This is the parent now.
        String parPath = null;
        parentContainer = null;
        String parName = String.format("%s_%s", basename, extension);

        if (saveEmbedded) {
            // parent obj is at Parent.xyz
            // parent textpath is at xtext/Parent.xyz.txt
            // create  ./xtext/../Parent
            // child is at      
            // parent textpath ../../Parent/
            parPath = new File(textpath).getParentFile().getParent();
        } else {
            parPath = new File(textpath).getParent();
        }
        parentContainer = new File(PathManager.makePath(parPath, parName));
    }

    /**
     * Evaluate a folder for archiving children close to where the parent original resides.
     * Whereas evalParentContainer(boolean) which tries to choose if children are archived embedded with parents in ./xtext
     * or in a parallel archive.
     */
    public void evalParentChildContainer() {
        if (!isParent) {
            return;
        }
        if (parentContainer != null) {
            return;
        }
        String parName = String.format("%s_%s", basename, extension);
        String parPath = folder.getAbsolutePath();

        parentContainer = new File(PathManager.makePath(parPath, parName));
    }

    /**
     * Add children converte docs.
     * @param ch child doc
     */
    public void addChild(ConvertedDocument ch) {
        if (children == null) {
            children = new ArrayList<ConvertedDocument>();
        }
        /** You are adding a child item to a parent that is marked as an RFC822 document, so naturally the
         * child is now an RFC822 attachment. 
         */
        if (is_RFC822_attachment) {
            ch.is_RFC822_attachment = true;
        }
        children.add(ch);
    }

    /**
     * 
     * @return true if this is a parent and has ConvertedDocument children.
     */
    public boolean hasChildren() {
        return (children != null && !children.isEmpty());
    }

    public void addRawChild(Content child) {
        if (childrenContent == null) {
            childrenContent = new ArrayList<Content>();
        }
        childrenContent.add(child);
    }

    /**
     * true if this is a parent and has raw Content children, e.g., raw bytes + metadata
     * which can in turn be saved as Files and then Converted to children
     * 
     * @return true if instance is a parent and it has non-trivial children
     */
    public boolean hasRawChildren() {
        return (childrenContent != null && !childrenContent.isEmpty());
    }

    public List<Content> getRawChildren() {
        return childrenContent;
    }

    public List<ConvertedDocument> getChildren() {
        return children;
    }

    /**
     * All properties are added as a string
     * @return  new Map of properties;  Copy of the internal JSON properties
     */
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<String, String>();

        for (Object fld : meta.keySet()) {
            props.put(fld.toString(), meta.getString(fld.toString()));
        }
        return props;
    }

    /**
     * DocInput abstraction.  "Identity" of a document is subjective. By default it is the
     * filepath, but could easily be set to MD5 digest, UUID, or some external record ID for this item.
     * @param ident id of this instance. 
     */
    public void setId(String ident) {
        this.id = ident;
    }

    /**
     * @param enc text encoding 
     */
    public void setEncoding(String enc) {
        this.encoding = enc;
        addProperty("encoding", enc);
    }

    /**
     * get the charset encoding.
     * 
     * @return the character set encoding set by metadata discovery or by the setEncoding() method.
     */
    public String getEncoding() {
        return this.encoding;
    }

    /**
     * Get the mime type of the document, may be {@code null}.
     *
     * @return The mime type of the document, if available.
     */
    public MimeType getMimeType() {
        return mimeType;
    }

    /**
     * Set the mime type of the document, may be {@code null}.
     *
     * @param mimeType the mime type of the document.
     */
    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * get Filetime from original file.
     */
    public Date getFiletime() {
        if (filetime != null) {
            return filetime;
        }
        if (this.file != null) {
            return new Date(this.file.lastModified());
        }
        return null;
    }

    /**
     * DocInput interface: getText
     *
     * @return buffer - the text
     */
    @Override
    public String getText() {
        return this.buffer;
    }

    /**
     * DocInput interface: getFilepath
     *
     * @return path to file
     */
    @Override
    public String getFilepath() {
        return this.filepath;
    }

    public File getFolder() {
        return this.folder;
    }

    public File getFile() {
        return this.file;
    }

    /**
     * DocInput interface: getTextpath
     *
     * @return path to text file conversion. Null if original is either ASCII or
     * Unicode text.
     */
    @Override
    public String getTextpath() {
        return this.textpath;
    }

    /**
     * Reports if the doc has text available, after it was converted.
     * NOTE: this is false if you ask before it is converted.
     * 
     * @return true if there is text available.  false if the converters have not tried to set text or they tried and found no text.
     */
    public boolean hasText() {
        return buffer.length() > 0;
    }

    /**
     * Set default ID only after all conversion and all metadata has been acquired.
     * MD5 hash of text, if text is available, or of the filepath if file is empty.
     * 
     */
    public void setDefaultID() {
        if (hasText()) {
            id = TextUtils.text_id(getText());
        } else {
            id = TextUtils.text_id(filepath);
        }
    }

    /**
     * The whole point of this mess:  get the text from the original. It is set here and line endings normalized to unix line endings, \n
     */
    public void setText(String buf) throws UnsupportedEncodingException {
        this.buffer = buf;

        if (StringUtils.isBlank(buffer)) {
            return;
        }

        // Now figure out if we have a converted document or not.
        if (do_convert) {
            if (CONVERT_TO_UNIX_EOL) {
                buffer = buffer.replace("\r\n", "\n");
            }
            buffer = buffer.trim();
            is_converted = true;
        } else if (is_plaintext) {
            is_converted = false;
            textpath = this.filepath;
        }

        meta.put("textsize", buffer.length());
    }

    /**
     * @param k key for property
     * @return metadata value for k
     */
    public String getProperty(String k) {
        return meta.optString(k);
    }

    private boolean checkField(String k) {
        return valid_fields.contains(k);
    }

    public void addProperty(String k, String v) {
        if (!checkField(k)) {
            return;
        }
        meta.put(k, v);
    }

    /*
     * Add a custom property of your own. No validation here.
     * Use addProperty to add only valid core fields. 
     */
    public void addUserProperty(String k, String v) {
        meta.put(k, v);
    }

    public void addProperty(String k, long i) {
        if (!checkField(k)) {
            return;
        }
        meta.put(k, i);

    }

    public void addProperty(String k, boolean b) {
        if (!checkField(k)) {
            return;
        }
        meta.put(k, b);
    }

    /**
     * Create date text is added only on conversion. If doc conversion is
     * retrieved from cache, caller should rely more on the "pub_date" property.
     */
    public void addCreateDate(String d) {
        this.create_date_text = d;
        meta.put("pub_date", d);
    }

    /**
     * Create date obj is added only on conversion. If doc conversion is
     * retrieved from cache, caller should rely more on the "pub_date" property.
     */
    public void addCreateDate(java.util.Calendar d) {
        if (d == null) {
            return;
        }

        create_date = d.getTime();
        meta.put("pub_date", dtfmt.print(create_date.getTime()));
    }

    /**
     * For convenience,... add using Date obj
     * @param d
     */
    public void addCreateDate(java.util.Date d) {
        if (d == null) {
            return;
        }

        create_date = d;
        meta.put("pub_date", dtfmt.print(create_date.getTime()));
    }

    public void setCreateDate() {
        if (getProperty("pub_date") != null) {
            setCreateDate(getProperty("pub_date"));
        }
    }

    /**
     * string should be valid yyyy-mm-dd
     * @param ymd
     */
    public void setCreateDate(String ymd) {
        if (StringUtils.isBlank(ymd)) {
            return;
        }
        DateTime joda = dtfmt.parseDateTime(ymd);
        if (joda != null) {
            create_date = joda.toDate();
        }
    }

    public void addConverter(Class<?> c) {
        meta.put("converter", c.getName());
    }

    public void addTitle(String a) {
        meta.put("title", a);
    }

    public void addAuthor(String a) {
        meta.put("author", a);
    }

    /**
     * Find the relative path where this item should reside.
     * <pre>
     *   Given file  /source/a/b/c.xyz
     *   where will reside in archive?    /archive/.../source/a/b/c.xyz
     *   </pre>
     * Parent/Child relationship is complicated still.
     * @param container folder that represents the parent document
     */
    public void setPathRelativeTo(String container, boolean childrenWithParent) {
        String relPath = container;
        if (isChild && parentContainer != null && !childrenWithParent) {
            relPath = parentContainer.getAbsolutePath();
        }

        this.relative_path = PathManager.getRelativePath(relPath, this.filepath);
    }

    public final static String OUTPUT_ENCODING = "UTF-8";
    public final static String CONVERTED_TEXT_EXT = "-utf8.txt";

    /**
     * relative_path is original relative to input folder TOOD: cleanup
     */
    private String getNewPath(String relpath) {

        if (!this.is_plaintext) {
            return relpath + ".txt";
        }

        if (OUTPUT_ENCODING.equalsIgnoreCase(encoding)) {
            return relpath;
        } else {
            // Remove ".txt" at end of file replacing it with something to denote
            // It is a transcoded text file.
            return relpath.substring(0, relpath.length() - 4) + CONVERTED_TEXT_EXT;
        }
    }

    /**
     * Save buffer to a folder, outputDir;
     *
     * {HEADER_META}\n \n Buffer
     *
     * Text File is saved to your desintation output folder Files that are UTF-8
     * already will not be saved, copied or moved.
     */
    public void save(String outputDir) throws IOException {
        if (outputDir == null) {
            throw new NullPointerException("outputDir was null");
        }
        if (is_converted) {
            File target = new File(PathManager.makePath(outputDir, getNewPath(this.relative_path)));
            this._saveConversion(target);
        }
    }

    /**
     * Similar to save(), but forces the output folder to be ./xtext/ in the
     * same folder as the input archive. a/b/c/file.xxx to
     * a/b/c/xtext/file.xxx.txt
     */
    public void saveEmbedded() throws IOException {
        if (is_converted) {
            String container = (parentContainer != null ? parentContainer.getAbsolutePath()
                    : new File(this.filepath).getParent());

            String targetPath = PathManager.getEmbeddedPath(container, getNewPath(this.filename));

            File target = new File(targetPath);
            this._saveConversion(target);
        }
    }

    /**
     * Internal function for saving buffer in the XText format.
     * IF the converted original file as a date/time later than that of the cached conversion, 
     * this conversion cache will be overwritten.
     * 
     */
    protected void _saveConversion(File target) throws IOException {

        if (!ConvertedDocument.overwrite && target.exists()) {
            // Don't save, Not overwriting.
            if (file.lastModified() < target.lastModified()) {
                this.is_cached = true;
                return;
            }
        }

        if (this.filetime == null) {
            this.filetime = new Date(target.lastModified());
        }

        meta.put("filetime", this.filetime.getTime());

        // Tracking Parent/Child objects.
        meta.put("xtext_id", this.id);

        FileUtility.makeDirectory(target.getParentFile());
        saveBuffer(target);
        textpath = PathManager.fixPath(target.getAbsolutePath());
        this.is_cached = true;
    }

    /**
     * 
     * @return id of parent document. 
     */
    public String getParentID() {
        return meta.optString("xtext_parent_id");
    }

    /**
     * 
     * @return path of parent document
     */
    public String getParentPath() {
        return meta.optString("xtext_parent_path");
    }

    public static String XT_LABEL = "XT:";

    /**
     * Internally save Buffer with its metadata to a given filepath
     * Expert mode:  use this only if you know what you are doing.
     *    You can add additional metadata to the meta sheet using addProperty()
     *    Then overwrite existing doc conversions
     * @param target cached file to save a conversion.
     * @throws IOException
     */
    public void saveBuffer(File target) throws IOException {
        StringBuilder buf = new StringBuilder();

        // META data cannot be empty.
        // if (meta.isEmpty()) {
        //    buf.append("{}");
        // SAVE conversions with a minimal Base64-encoded header
        // which when decoded is a JSON structure of metadata properties.
        buf.append(buffer);
        buf.append("\n\n");
        buf.append(XT_LABEL);
        buf.append(Base64.encodeBase64String(meta.toString().getBytes()));
        buf.append("\n");
        FileUtility.writeFile(buf.toString(), target.getAbsolutePath(), OUTPUT_ENCODING, false);
    }
}
