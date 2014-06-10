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

import org.opensextant.util.FileUtility;
import org.opensextant.xtext.converters.EmbeddedContentConverter;
import org.opensextant.xtext.converters.ImageMetadataConverter;
import org.opensextant.xtext.converters.MessageConverter;
import org.opensextant.xtext.converters.TextTranscodingConverter;
import org.opensextant.xtext.converters.TikaHTMLConverter;
import org.opensextant.xtext.converters.ArchiveNavigator;
import org.opensextant.xtext.converters.DefaultConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.tika.io.IOUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;

/**
 * 
 * Traverse a folder and return text versions of the documents found. Archiving
 * the text only copies at an output location of your choice.
 * 
 * <pre>
 * 
 * if input is a file, convert. Done.
 * 
 * if input is an archive, unpack in temp space, iterate over dir, convert each.
 * Done
 * 
 * if input is a folder iterate over dir, convert each. Done
 * </pre>
 * 
 * TEXT OUTPUT form includes a JSON document header with metadata properties
 * from the original item. These are valid elements of the conversion process.
 * We try to maintain them apart from the true, readable text of the document.
 * 
 * 
 * Add a ConversiontListener to XText instance to capture the converted document
 * as it comes out of the main loop for converting archives and folders.
 * 
 * extractText() runs over any file type and extracts text, saving it pushing
 * events to one optional listener
 * 
 * convertFile(File) will convert a single file, returning a ConvertedDocument
 * 
 * 
 * 
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public final class XText implements iFilter, iConvert {

    private Logger log = LoggerFactory.getLogger(getClass());
    private boolean save = true;
    private boolean zone_web_content = false;
    private String archiveRoot = null;
    private String inputRoot = null;
    private String tempRoot = null;
    protected String inputNode = null;
    /**
     * Embedded mode
     */
    private boolean save_in_folder = false;
    private boolean save_children_in_folder = true;

    /**
     * Archive mode: save to the archive root rather than in the directory the
     * file came from. Either embedded mode or archive mode.
     */
    private boolean extractEmbedded = true;

    /**
     * XText default is 1 MB of text
     */
    private int maxBuffer = DefaultConverter.MAX_TEXT_SIZE;
    private long maxFileSize = FILE_SIZE_LIMIT;

    protected Set<String> archive_types = new HashSet<String>();
    /**
     *
     */
    public static Map<String, iConvert> converters = new HashMap<String, iConvert>();
    private iConvert defaultConversion;
    private iConvert embeddedConversion;
    private Set<String> requested_types = new HashSet<String>();
    private Set<String> ignore_types = new HashSet<String>();

    /**
     */
    public XText() {
        defaults();
    }

    public void enableOverwrite(boolean b) {
        ConvertedDocument.overwrite = b;
    }

    /**
     * This enables saving in an archive and disables saving with input.
     *
     * @param root
     * @throws IOException
     * @see #enableSaveInArchive(boolean)
     * @see #enableSaveWithInput(boolean)
     */
    public void setArchiveDir(String root) throws IOException {
        if (root == null) {
            throw new IOException("Archive cannot be null");
        }

        // User tried setting a non-null archive... so implicitly they are not saving with input
        //
        this.enableSaveInArchive(true);
        archiveRoot = fixPath(root);

        File test = new File(archiveRoot);

        if (!test.exists() || !test.isDirectory()) {
            throw new IOException("Archive root directory must exist");
        }

    }

    /**
     * Set the temp working directory
     * 
     * @deprecated Currently unused temp folder
     * @param tmp
     * @throws IOException
     */
    public void setTempDir(String tmp) throws IOException {
        if (tmp == null) {
            throw new IOException("Temp Dir cannot be null");
        }
        tempRoot = FilenameUtils.normalizeNoEndSeparator(tmp, true);
        if (tempRoot == null) {
            throw new IOException("Temp Dir is invalid");
        }
        File test = new File(tempRoot);

        if (!test.exists() || !test.isDirectory()) {
            throw new IOException("Temp Dir must exist");
        }
    }

    public void setMaxBufferSize(int sz) {
        maxBuffer = sz;
    }

    //public void setMaxFileSize(long sz) {
    //    maxFileSize = sz;
    //}

    public void setMaxFileSize(int sz) {
        maxFileSize = (long) sz;
    }

    /**
     * Use Tika HTML de-crapifier. Default: No scrubbing.
     */
    public void enableHTMLScrubber(boolean b) {
        zone_web_content = b;
    }

    public void enableEmbeddedExtraction(boolean b) {
        extractEmbedded = b;
    }

    /**
     * The overall flag to save converted output or not. DEFAULT: true = save
     * it; provided caller specifies either saveWithInput or provides an
     * archiveRoot
     * 
     * @param b
     */
    public void enableSaving(boolean b) {
        save = b;
    }

    /**
     * Save converted content with input. Xtext creates a new "xtext" folder in
     * the containing folder of the current file. This is disabled if a
     * non-null, pre-existing archive root is set.
     *
     * <pre>
     * input is:     a/b/c.doc
     * saved as:     a/b/xtext/c.doc.txt
     *
     * DEFAULT: do not save in input folder
     * </pre>
     * @see #setArchiveDir(java.lang.String)
     */
    public void enableSaveWithInput(boolean b) {
        save_in_folder = b;
    }

    /**
     * Experimental.
     * 
     * ON by default.  If you have email, for example, folder/A.eml
     * then children will appear at folder/A_eml/child.doc  for some child.doc attachment.
     * Behavior may differ in each case.  But essentially, this flag directs XText to write back to inputRoot
     *
     * Embedded parent/child docs (email, compound docs, etc) are special cases,
     * @param b
     */
    public void enableSaveChildrenWithInput(boolean b) {
        save_children_in_folder = b;
    }

    /**
     * Saving to an archive specified by the caller; This is inferred if a
     * non-null, pre-existing archive root is set; DEFAULT: do not save in
     * archive.
     *
     * <pre>
     * input is:   a/b/c.doc
     * output is:  archiveRoot/a/b/c.doc.txt
     * </pre>
     * @see #setArchiveDir(java.lang.String)
     */
    public void enableSaveInArchive(boolean b) {
        save_in_folder = !b;
    }

    /**
     * Add the file extension for the file type you wish to convert. if Tika
     * supports it by default it should be no problem.
     */
    public void convertFileType(String ext) {
        requested_types.add(ext.toLowerCase());
    }

    /**
     * Ignore files ending with.... or of type ext. No assumption of case is
     * made. This is case sensitive.
     */
    public void ignoreFileType(String ext) {
        if (ext != null) {
            ignore_types.add(ext.toLowerCase());
        }
    }

    private ConversionListener postProcessor = null;

    /**
     * A conversion listener is any outside application or routine that will do
     * something more with the converted document. If unset nothing happens. ;)
     */
    public void setConversionListener(ConversionListener processor) {
        postProcessor = processor;
    }

    /**
     * is the input an archive?
     */
    public boolean isArchive(String fpath) {
        String ext = FilenameUtils.getExtension(fpath);
        if (ext == null) {
            return false;
        }
        return archive_types.contains(ext.toLowerCase());

    }

    private String outputNode;

    /**
     * If you are caching conversions in an archive folder, A, then 
     * this generally sets your ouputNode to /A/name/
     * 
     * An items saved here will be of the form /A/name/relative/path
     * For an input that came from /some/input/name/relative/path
     * 
     * @param name
     */
    public void setOutputNode(String name) throws IOException {
        if (!save) {
            // Not saving output.
            return;
        }

        if (archiveRoot == null) {
            outputNode = inputRoot;
            return;
        }
        if (name == null) {
            outputNode = archiveRoot;
            return;
        }

        // Finally, use a "archiveRoot/name"
        //
        outputNode = createPath(archiveRoot, name);
    }

    protected long total_conv_time = 0;
    protected int average_conv_time = 0;
    protected int total_conversions = 0;

    /**
     * Records overall counts and conversion times for documents converted.
     * This may not account for error'd documents.
     * 
     * @param d
     */
    protected void trackStatistics(ConvertedDocument d) {
        if (d != null) {
            total_conv_time += d.conversion_time;
        }
        ++total_conversions;
    }

    public void reportStatistics() {
        average_conv_time = (int) ((float) total_conv_time / total_conversions);
        log.info("TOTAL of N=" + total_conversions + " documents converted"
                + "\n With an average time (ms) of " + average_conv_time);
    }

    protected long start_time = 0;
    protected long stop_time = 0;

    /**
     * Override the current setting for input Root
     * 
     * @param tmpInput
     */
    public void setInputRoot(String tmpInput) {
        this.inputRoot = tmpInput;
        if (inputRoot != null) {
            inputRoot = fixPath(tmpInput);
        }
    }

    /**
     * Most of the path mechanics are string-based, rather than file-system based,
     * so path adjustments are best done to be sure all paths from configuration
     * or from inputs should conform to a common convention.  paths will be more like URLs, using
     * "/" as the standard path separator.
     *
     * @param p
     * @return
     */
    protected static String fixPath(String p) {
        if (p == null) {
            return null;
        }
        return p.replace('\\', '/');
    }

    /**
     * The main entry point to converting compound documents and folders.
     */
    public void extractText(String filepath) throws IOException {

        start_time = System.currentTimeMillis();

        String path = FilenameUtils.normalize(filepath, true);
        if (path == null) {
            throw new IOException("Failed to normalize the path: " + filepath);
        }

        File input = new File(path);
        if (!input.exists()) {
            throw new IOException("Non existent input FILE=" + input.getAbsolutePath());
        }

        if (isArchive(path)) {
            convertArchive(input);
        } else if (input.isFile()) {
            setInputRoot(input.getParent());
            inputNode = input.getParentFile().getName();
            setOutputNode(inputNode);
            convertFile(input);
        } else if (input.isDirectory()) {
            setInputRoot(input.getAbsolutePath());
            inputNode = input.getName();
            setOutputNode(inputNode);
            convertFolder(input);
        }

        stop_time = System.currentTimeMillis();

        log.info("Output can be accessed at " + this.archiveRoot);

        reportStatistics();
    }

    public boolean filterOutFile(File input) {
        //
        //
        if (ConvertedDocument.DEFAULT_EMBED_FOLDER.equals(input.getParentFile().getName())) {
            return true;
        }

        return filterOutFile(input.getAbsolutePath());
    }

    public final static String DEFAULT_EMBED_FOLDER_IN_PATH = String.format("/%s/",
            ConvertedDocument.DEFAULT_EMBED_FOLDER);
    public final static String DEFAULT_EMBED_FOLDER_IN_WINPATH = String.format("\\%s\\",
            ConvertedDocument.DEFAULT_EMBED_FOLDER);

    /**
     * Filter the type of files to ignore.
     */
    @Override
    public boolean filterOutFile(String filepath) {

        // Filter out any of our own xtext caches 
        //
        if (filepath.contains(DEFAULT_EMBED_FOLDER_IN_PATH)
                || filepath.contains(DEFAULT_EMBED_FOLDER_IN_WINPATH)) {
            return true;
        }

        String n = FilenameUtils.getBaseName(filepath);
        if (n.startsWith(".")) {
            return true;
        }
        if (filepath.contains(".svn")) {
            return true;
        }

        // ignore '-utf8.txt' as XText likely generated them.
        //
        if (n.endsWith(ConvertedDocument.CONVERTED_TEXT_EXT)) {
            return true;
        }

        String ext = FilenameUtils.getExtension(filepath).toLowerCase();
        return !requested_types.contains(ext);
    }

    /**
     * NOTE: Use of File() or FilenameUtils.concat() are OS dependent, here
     * what we want is more like a URL string representation always using /a/b/c/
     * Instead of potentially \ and/or / mixed.
     * @param dir
     * @param item
     * @return
     * @throws IOException
     */
    private static String createPath(String dir, String item) throws IOException {
        return String.format("%s/%s", fixPath(dir), item);
    }

    /**
     * Unpack an archive and convert items found.
     * Given (input)/A.zip
     * The zip is dearchived to 
     *    (input)/A_zip/     
     * or (archive)/(input)/A_zip
     * 
     * Items are then converted in either folder for the conversion archiving; depending on your choice of embedded vs. non-embedded
     * 
     */
    public void convertArchive(File input) throws IOException {

        if (!this.save_in_folder && !this.save_children_in_folder && this.archiveRoot == null) {
            log.error(
                    "Sorry -- if not saving in input folder, you must provide a separate archive to contain ZIP and other archives that are extracted.  Ignoring FILE={}",
                    input);
            return;
        }

        String archive_name = FilenameUtils.getBaseName(input.getName());
        String archive_ext = FilenameUtils.getExtension(input.getName());
        inputNode = String.format("%s_%s", archive_name, archive_ext);
        // Set output name to input name.  That is, once we extract A.zip to ./(originals)/A_zip/   this de-archived folder will 
        // Also exist in ./(converted)/A_zip/  or ./(originals)/A_zip/xtext/ embedded.
        //
        setOutputNode(inputNode);

        String saveArchiveTo = null;

        // unpack, traverse, convert, save
        if (this.save_children_in_folder) {
            saveArchiveTo = createPath(input.getParentFile().getAbsolutePath(), inputNode);
        } else {
            // Save converted items in a parallel archive for this zip archive.
            saveArchiveTo = createPath(this.archiveRoot, inputNode);
        }

        this.setInputRoot(saveArchiveTo);
        ArchiveNavigator unpacker = new ArchiveNavigator(saveArchiveTo, this, this);
        unpacker.overwrite = ConvertedDocument.overwrite;
        unpacker.unpack(input);
    }

    /**
     * Arbitrary 32 MB limit on file size. Maybe this should be dependent on the
     * file type.
     */
    public final static long FILE_SIZE_LIMIT = 0x2000000;

    /**
     * This is the proxy interface for traversing archives.
     * 
     * Archive Navigator will call this interface to convert and post-process So
     * XText itself is a super-converter, whereas the items in the converter pkg
     * are stateless, simple conversions.
     * 
     * this interface implementation calls XText.convertFile() which in turn
     * deals with the details of saving and archiving items
     * 
     * Items retrieved from Archive Navigator are deleted from their temp space.
     */
    @Override
    public ConvertedDocument convert(File input) throws IOException {
        return convertFile(input);
    }

    /**
     * Unsupported iConvert interface method. To convert text from a String obj
     * rather than a File obj, you would instantiate a converter implementation
     * for the data you think you are converting. E.g., if you know you have a
     * buffer of HTML content and want to save it as text, call
     * TikaHTMLConverter().convert( buffer ) directly.
     * 
     */
    @Override
    public ConvertedDocument convert(String data) throws IOException {
        throw new IOException("Unsupported interface:  To convert text or binary data directly "
                + "you must use an instance of a XText converter, e.g., TikaHTMLConverter");
    }

    public ConvertedDocument convertFile(File input) throws IOException {
        return convertFile(input, null);
    }

    /**
     * Convert one file and save it off. We ignore hidden files and files in
     * hidden folders, e.g., .cvs_ignore, mycode/.svn/abc.txt
     * 
     * This is the end of the line for the conversion logic; convertFile figures
     * out if it should return the cached version or attempt a conversion; it
     * also tries to save children items As children items may require special
     * attention they are not converted -- caller can pass in ConversionListener
     * and can deal with children file objects on their end.
     * 
     * @param input
     * @return converted document object
     * @throws java.io.IOException
     */
    public ConvertedDocument convertFile(File input, ConvertedDocument parent) throws IOException {

        if (parent == null && filterOutFile(input)) {
            return null;
        }

        if (this.inputRoot == null) {
            throw new IOException(
                    "Please set an input root; convertFile() was called outside of extractText()");
        }

        String fname = input.getName();

        String ext = FilenameUtils.getExtension(fname).toLowerCase();
        if (ignore_types.contains(ext)) {
            return null;
        }

        if (!requested_types.contains(ext)) {
            return null;
        }

        log.debug("Converting FILE=" + input.getAbsolutePath());

        /*
         * Handle archives.
         */
        if (isArchive(fname)) {
            convertArchive(input);

            // NULL here implies the actual file, A.zip does not have any text representation itself.
            // However its children do.
            return null;
        }

        /*
         * Otherwise this is a normal file...
         */
        if (FileUtils.sizeOf(input) > maxFileSize) {
            log.info("Valid File is too large FILE=" + input.getAbsolutePath());
            return null;
        }

        boolean cachable = true;
        iConvert converter = converters.get(ext);
        if (converter == null) {
            if (extractEmbedded && EmbeddedContentConverter.isSupported(ext)) {
                converter = embeddedConversion;
                cachable = false; // Such content is processed every time.  Oh well... 
            } else {
                converter = defaultConversion;
            }
        }

        ConvertedDocument textDoc = null;

        // ------------------
        // Retrieve previous conversions
        // ------------------
        if (cachable && !ConvertedDocument.overwrite && this.save) {
            if (this.save_in_folder) {
                // Uncache a file close to the original F <== ./xtext/F.txt
                textDoc = ConvertedDocument.getEmbeddedConversion(input);
            } else if (this.inputRoot != null) {
                // Only if the caller is using the XText API extracText(), then
                // will this work.
                // If user is trying to call convertFile(path) directly all the
                // various optimizations here
                // will not necessarily make sense.
                //
                //
                // Uncache a file in some other tree of archives that aligns
                // with the tree of the original source.
                // .../mine/source/path/F <==== /archive/source/path/F.txt
                textDoc = ConvertedDocument.getCachedConversion(this.outputNode, this.inputRoot,
                        input);
            }
        }

        // ------------------
        // Convert or Read object, IFF no cache exists for that object.
        // ------------------
        if (textDoc == null) {
            // Measure how long conversions take.
            long t1 = System.currentTimeMillis();

            try {
                textDoc = converter.convert(input);
            } catch (Exception convErr) {
                if (log.isDebugEnabled()) {
                    log.debug("Conversion error FILE={}", input.getPath(), convErr);
                } else {
                    log.error("Conversion error FILE={} MSG={}", input.getPath(),
                            convErr.getMessage());
                }
            }
            long t2 = System.currentTimeMillis();
            int duration = (int) (t2 - t1);
            if (textDoc != null) {
                // Buffer can be null. If you got this far, you are interested
                // in the file, as it passed
                // all filters above. Return the document with whatever metadata
                // it found.
                // if (textDoc.buffer == null) {
                // throw new
                // IOException("Engineering error: Doc converted, but converter failed to setText()");
                // }
                if (this.save && textDoc.is_converted) {
                    // Get Parent info in there.
                    if (parent != null) {
                        textDoc.setParent(parent);
                    }

                    if (this.save_in_folder) {
                        // Saves close to original in ./text/ folder where
                        // original resides.
                        textDoc.saveEmbedded();
                    } else {
                        textDoc.setPathRelativeTo(inputRoot, this.save_children_in_folder);
                        textDoc.save(outputNode);
                    }
                    // Children items will be persisted in the same folder
                    // structure where the textdoc.textpath resides.
                    // That is, Email or Embedded objects will be parsed are
                    // saved in ./xtext/ folder or in the separate archive.
                    // But this must be down now, as we have all the dynamic
                    // metadata + raw artifacts; As it is all written out to
                    // disk,
                    // it will be written out together.
                    //
                    if (textDoc.hasRawChildren()) {
                        convertChildren(textDoc);

                        // 1. children saved to disk
                        // 2. children converted.
                        // 3. children attached to parent here.
                        // 'textdoc' should now be well endowed with all the
                        // children metadata.
                    }
                }
            } else {
                textDoc = new ConvertedDocument(input);
            }

            textDoc.conversion_time = duration;
            if (textDoc.filetime == null) {
                textDoc.filetime = textDoc.getFiletime();
            }
        }

        /*
         * Conversion Listeners are called only for parent documents. That is
         * for an email with 4 attachments, this listener is called on the
         * parent email message, but not for the individual 4 attachments. The
         * final parent document here will have all Raw Children (bytes +
         * metadata) and Converted Children (ConvertedDocument obj) Caller will
         * have to detect if returned item via listener is a Parent with
         * Children.
         * 
         * Behavior here is TBD.
         */
        if (postProcessor != null && parent == null) {
            postProcessor.handleConversion(textDoc, input.getAbsolutePath());
        }

        trackStatistics(textDoc);
        return textDoc;
    }

    /**
     * Navigate a folder trying to convert each file and return something to the
     * listener. Do not sacrifice the entire job if one file fails, so exception
     * is trapped in loop
     * 
     */
    public void convertFolder(File input) throws IOException {
        java.util.Collection<File> files = FileUtils.listFiles(input, new SuffixFileFilter(
                FILE_FILTER, IOCase.INSENSITIVE), FileFilterUtils.trueFileFilter());
        for (File f : files) {
            try {
                convertFile(f);
            } catch (Exception convErr) {
                log.error("Conversion error, FILE=" + f.getPath(), convErr);
            }
        }
    }

    /**
     * Save children objects for a given ConvertedDocument to a location....
     * convert those items immediately, saving the Parent metadata along with
     * them. You should have setParent already
     * 
     * @param parentDoc
     * @throws IOException
     */
    public void convertChildren(ConvertedDocument parentDoc) throws IOException {
        parentDoc.evalParentChildContainer();
        FileUtility.makeDirectory(parentDoc.parentContainer);
        String targetPath = parentDoc.parentContainer.getAbsolutePath();

        for (Content child : parentDoc.getRawChildren()) {
            if (child.content == null) {
                log.error("Attempted to write out child object with no content {}", child.id);
                continue;
            }

            OutputStream io = null;
            try {
                // We just assume for now Child ID is filename.
                // Alternatively, child.meta.getProperty(
                // ConvertedDocument.CHILD_ENTRY_KEY )
                // same result, just more verbose.
                //
                File childFile = new File(FilenameUtils.concat(targetPath, child.id));
                io = new FileOutputStream(childFile);
                IOUtils.write(child.content, io);

                ConvertedDocument childConv = convertFile(childFile, parentDoc);
                if (childConv != null) {
                    if (childConv.is_converted) {
                        // Push down all child metadata down to ConvertedDoc
                        for (String k : child.meta.stringPropertyNames()) {
                            String val = child.meta.getProperty(k);
                            childConv.addUserProperty(k, val);
                        }
                        // Save cached version once again.
                        childConv.saveBuffer(new File(childConv.textpath));
                    }

                    if (child.mimeType != null) {
                        try {
                            childConv.setMimeType(new MimeType(child.mimeType));
                        } catch (MimeTypeParseException e) {
                            log.warn("Invalid mime type encountered: {} ignoring.", child.mimeType);
                        }
                    }

                    parentDoc.addChild(childConv);
                }
            } catch (Exception err) {
                log.error("Failed to write out child {}, but will continue with others", child.id,
                        err);
            } finally {
                if (io != null) {
                    io.close();
                }
            }
        }
    }

    /**
     * TODO: this is called by default. duh. To change behavior, adjust
     * settings before setup() is called
     */
    public void defaults() {

        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null) {
            tmp = "/tmp/xtext";
        }
        try {
            setTempDir(tmp);
        } catch (Exception err) {
            log.error("You have accepted the defaults, but the temp dir is not available.", err);
        }

        archive_types.add("zip");
        archive_types.add("gz");
        archive_types.add("tar");
        archive_types.add("tgz");
        archive_types.add("tar.gz");
        // archive_types.add("7z");

        // Get from a config file.
        requested_types.add("doc");
        requested_types.add("docx");
        requested_types.add("pdf");
        requested_types.add("htm");
        requested_types.add("html");
        requested_types.add("txt"); // only for encoding conversions.
        requested_types.add("msg");
        requested_types.add("eml");
        requested_types.add("emlx");
        requested_types.add("ppt");
        requested_types.add("pptx");
        requested_types.add("xlsx");
        requested_types.add("xls");
        requested_types.add("rtf");

        // Only Photographic images will be supported by default.
        // BMP, GIF, PNG, ICO, etc. must be added by caller.
        //
        requested_types.add("jpg");
        requested_types.add("jpeg");

        // requested_types.add("log"); // Uncommon. Caller must expclitly add
        // raw data types and archives.

        defaultConversion = new DefaultConverter(maxBuffer);
        embeddedConversion = new EmbeddedContentConverter(maxBuffer);
    }

    /**
     * Start over.
     */
    public void clearSettings() {
        requested_types.clear();
        converters.clear();
    }

    /**
     * If by this point you have taken items out of the requested types the
     * converters will not be setup. E.g., if you don't want PDF or HTML
     * conversion - those resources will not be initialized.
     */
    public void setup() throws IOException {

        if (!this.save_in_folder && this.archiveRoot == null) {
            throw new IOException(
                    "If not saving conversions with your input folders, you must provide an archive path");
        }
        // Invoke converter instances only as requested types suggest.
        // If caller has removed file types from the list, then

        String mimetype = "txt";
        if (requested_types.contains(mimetype)) {
            converters.put(mimetype, new TextTranscodingConverter());
        }

        mimetype = "html";
        if (requested_types.contains(mimetype)) {
            iConvert _conv = new TikaHTMLConverter(this.zone_web_content);
            converters.put(mimetype, _conv);
            converters.put("htm", _conv);
            converters.put("xhtml", _conv);

            requested_types.add("htm");
            requested_types.add("xhtml");
        }

        MessageConverter emailParser = new MessageConverter();
        mimetype = "eml";
        if (requested_types.contains(mimetype)) {
            converters.put("eml", emailParser);
        }
        mimetype = "msg";
        if (requested_types.contains(mimetype)) {
            converters.put("msg", emailParser);
        }

        ImageMetadataConverter imgConv = new ImageMetadataConverter();
        String[] imageTypes = { "jpeg", "jpg" };
        for (String img : imageTypes) {
            if (requested_types.contains(img)) {
                converters.put(img, imgConv);
            }
        }

        // ALWAYS ignore our own text conversions or those of others.
        // So here all known convertable types will need a filter for their
        // conversion, e.g.,
        // pdf => ignore pdf.txt
        // doc => ignore doc.txt
        //
        for (String t : requested_types) {
            ignoreFileType(t + ".txt");
        }

        FILE_FILTER = requested_types.toArray(new String[requested_types.size()]);
    }

    /**
     *
     */
    private String[] FILE_FILTER = null;

    /**
     * Call after setup() has run to add all supported/requested file types
     */
    public Set<String> getFileTypes() {
        return requested_types;
    }

    public static void usage() {
        System.out.println("XText -i input  [-h] [-o output] [-e]");
        System.out.println("  input is file or folder");
        System.out.println("  output is a folder where you want to archive converted docs");
        System.out.println("  -e embeds the saved conversions in the input folder under 'xtext/'");
        System.out.println("  NOTE: -e has same effect as setting output to input");
        System.out.println("  -h enables HTML scrubbing");
    }

    public static void main(String[] args) {

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("XText", args, "hei:o:");

        String input = null;
        String output = null;
        boolean embed = false;
        boolean filter_html = false;
        try {

            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'i':
                    input = opts.getOptarg();
                    break;
                case 'o':
                    output = opts.getOptarg();
                    break;
                case 'h':
                    filter_html = true;
                    break;
                case 'e':
                    embed = true;
                    System.out
                            .println("Saving conversions to Input folder.  Output folder will be ignored.");
                    break;
                default:
                    XText.usage();
                    System.exit(1);
                }
            }
        } catch (Exception err) {
            XText.usage();
            System.exit(1);
        }

        if (input == null) {
            System.out.println("An input argument is required, e.g., -Dinput=/Folder/...");
            System.exit(-1);
        }

        // Setting LANG=en_US in your shell.
        //
        // System.setProperty("LANG", "en_US");
        XText xt = new XText();
        xt.enableOverwrite(true); // Given this is a test application, we will
                                  // overwrite every time XText is called.
        xt.enableSaving(true);
        xt.enableSaveWithInput(embed); // creates a ./text/ Folder locally in
                                       // directory.
        xt.enableHTMLScrubber(filter_html);

        try {
            if (output == null && !embed) {
                output = "xtext-output";
                FileUtility.makeDirectory(output);
                System.out.println("Default output folder is $PWD/" + output);
            }
            // Notice this main program requires an output path.
            xt.setArchiveDir(output);

            xt.setup();
            xt.extractText(input);
        } catch (IOException ioerr) {
            ioerr.printStackTrace();
        }
    }
}
