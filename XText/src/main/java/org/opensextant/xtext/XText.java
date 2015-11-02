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
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//   OpenSextant XText
// *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */
package org.opensextant.xtext;

import static org.apache.commons.lang3.StringUtils.isBlank;
import gnu.getopt.LongOpt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.tika.io.IOUtils;
import org.opensextant.ConfigException;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.collectors.ArchiveNavigator;
import org.opensextant.xtext.collectors.mailbox.OutlookPSTCrawler;
import org.opensextant.xtext.converters.DefaultConverter;
import org.opensextant.xtext.converters.EmbeddedContentConverter;
import org.opensextant.xtext.converters.ImageMetadataConverter;
import org.opensextant.xtext.converters.MessageConverter;
import org.opensextant.xtext.converters.TextTranscodingConverter;
import org.opensextant.xtext.converters.TikaHTMLConverter;
import org.opensextant.xtext.converters.WebArchiveConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public final class XText implements ExclusionFilter, Converter {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private boolean scrubHTML = false;

    private final PathManager paths = new PathManager();

    public PathManager getPathManager() {
        return paths;
    }

    /** flag to manage if children are extracted or not.
     */
    private boolean extractEmbedded = false;

    /**
     * XText default is 1 MB of text
     */
    private int maxBuffer = DefaultConverter.MAX_TEXT_SIZE;
    /**
     * Heuristic - HTML content is likely 5x, maybe a lot more, the size of the
     * plain text it contains.  So with 1 MB the target max text size, 5 MB would be
     * the largest HTML document accepted here, by default.
     */
    private final int maxHTMLBuffer = 5 * maxBuffer;
    private long maxFileSize = FILE_SIZE_LIMIT;

    protected Set<String> archiveFileTypes = new HashSet<String>();

    /**
     *
     */
    public static Map<String, Converter> converters = new HashMap<String, Converter>();
    private Converter defaultConversion;
    private Converter embeddedConversion;
    private final Set<String> requestedFileTypes = new HashSet<String>();
    private final Set<String> ignoreFileTypes = new HashSet<String>();
    private boolean allowNoExtension = false;

    /**
     */
    public XText() {
        defaults();
    }

    public void enableOverwrite(boolean b) {
        ConvertedDocument.overwrite = b;
    }

    /**
     * Sets the archive dir.
     *
     * @param root the new archive dir
     * @throws IOException on err
     * @deprecated  use getPathManager().setConversionRoot( path )
     */
    @Deprecated
    public void setArchiveDir(String root) throws IOException {
        paths.setConversionCache(root);
    }

    public void setMaxBufferSize(int sz) {
        maxBuffer = sz;
    }

    public void setMaxFileSize(int sz) {
        maxFileSize = sz;
    }

    /**
     * Set if your app requires file extensions or not.
     * @param b true to enable
     */
    public void enableNoFileExtension(boolean b) {
        allowNoExtension = b;
    }

    /**
     * Use Tika HTML de-crapifier. Default: No scrubbing.
     *
     * @param b true if you wish to de-crapify, I mean scrape HTML content
     */
    public void enableHTMLScrubber(boolean b) {
        scrubHTML = b;
    }

    /**
     * enable/disable the extraction of embedded child documents in found documents.
     * Using embedded extraction may yield many small sub documents, aka children.
     * @param b true to enable
     */
    public void enableEmbeddedExtraction(boolean b) {
        extractEmbedded = b;
    }

    /**
     * The overall flag to save converted output or not. DEFAULT: true = save
     * it; provided caller specifies either saveWithInput or provides an
     * archiveRoot
     *
     * @param b true to enable
     */
    public void enableSaving(boolean b) {
        paths.enableSaving(b);
    }

    /**
     * Add the file extension for the file type you wish to convert. if Tika
     * supports it by default it should be no problem.
     * Adding requested file types here only allows the API to know by-file extension
     * what types to filter in and convert.  Without a file extension, the file
     * still needs to be ingested and converted to identify the file type.
     * @param ext a file extension to convert
     */
    public void convertFileType(String ext) {
        requestedFileTypes.add(ext.toLowerCase());
    }

    /**
     * Ignore files ending with.... or of type ext. No assumption of case is
     * made. This is case sensitive.
     * @param ext a file extension to NOT convert
     */
    public void ignoreFileType(String ext) {
        if (ext != null) {
            ignoreFileTypes.add(ext.toLowerCase());
        }
    }

    private ConversionListener postProcessor = null;

    /**
     * A conversion listener is any outside application or routine that will do
     * something more with the converted document. If unset nothing happens. ;)
     * @param processor a lisenter that handles the documents that have been found
     */
    public void setConversionListener(ConversionListener processor) {
        postProcessor = processor;
    }

    private boolean useTikaPST = false;

    public void enableTikaPST(boolean flag) {
        useTikaPST = flag;
    }

    /**
     * is the input an archive?.
     *
     * @param fpath the fpath
     * @return true, if is archive
     */
    public boolean isArchive(String fpath) {
        String ext = FilenameUtils.getExtension(fpath);
        if (ext == null) {
            return false;
        }
        return archiveFileTypes.contains(ext.toLowerCase());
    }

    public boolean isPST(String fpath) {
        return isPSTExtension(FilenameUtils.getExtension(fpath));
    }

    public static boolean isPSTExtension(String ext) {
        if (ext == null) {
            return false;
        }
        return ("pst".equalsIgnoreCase(ext));
    }

    protected long total_conv_time = 0;
    protected int average_conv_time = 0;
    protected int total_conversions = 0;

    /**
     * Records overall counts and conversion times for documents converted.
     * This may not account for error'd documents.
     *
     * @param d ConvertedDocument
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
     * Optional API routine.  If XText is used as a main program, this is the entry point for extraction/collection.
     * If XText is used as an API, caller may use convertFile() directly without engaging in the setup and assumptions behind this convenience method.
     * The main entry point to converting compound documents and folders.
     * @param filepath item from which we extract text
     * @throws IOException err
     * @throws ConfigException err
     */
    public void extractText(String filepath) throws IOException, ConfigException {

        start_time = System.currentTimeMillis();

        log.info("Conversion.  INPUT PATH={}", filepath);
        String path = FilenameUtils.normalize(new File(filepath).getAbsolutePath(), true);
        if (path == null) {
            throw new IOException("Failed to normalize the path: " + filepath);
        }

        File input = new File(path);
        if (!input.exists()) {
            throw new IOException("Non existent input FILE=" + path);
        }

        /* Filter on absolute path */
        if (PathManager.isXTextCache(path)) {
            throw new ConfigException(
                    "XText cannot be directed to extract text from its own cache files. "
                            + "Move the cache files out of ./xtext/ folders if you really need to do this.");
        }

        if (isArchive(input.getName())) {
            // Archive will collect originals to "export"
            // Archive will save conversions to "output"
            // PathManager is STATEFUL for as long as this archive is processing
            // If an archive is uncovered while traversing files, its contents can be dumped to the child export folder.
            convertArchive(input);
        } else if (isPST(input.getName()) && !useTikaPST) {
            this.convertOutlookPST(input);
        } else if (input.isFile()) {
            // If prefix is not set, then conversion will be dumped flatly to output area.
            paths.setInputRoot(input);
            convertFile(input);
        } else if (input.isDirectory()) {
            paths.setInputRoot(input);
            convertFolder(input);
        }

        stop_time = System.currentTimeMillis();

        if (paths.isSaving()) {
            if (paths.isSaveWithInput()) {
                log.info(
                        "Output can be accessed at from the input folder {} in 'xtext' sub-folders",
                        input.getParent());
            } else {
                log.info("Output can be accessed at " + paths.getConversionCache());
            }
        }

        reportStatistics();
    }

    /**
     * Filter out File object if it is an XText conversion of some sort. That is, if
     * file "./a/b/c/xtext/file.doc.txt  is found, it is omitted because it is contained in "./xtext"
     *
     * @param input file obj
     * @return true if file's immediate parent is named 'xtext'
     */
    private boolean filterOutFile(File input) {
        //
        //
        if (PathManager.isXTextCache(input)) {
            return true;
        }

        return filterOutFile(input.getAbsolutePath());
    }

    /**
     * Filter the type of files to ignore.
     */
    @Override
    public boolean filterOutFile(String filepath) {

        // Filter out any of our own xtext caches
        //
        if (PathManager.isXTextCache(filepath)) {
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

        String ext = FilenameUtils.getExtension(filepath);
        if (isBlank(ext)) {
            if (allowNoExtension) {
                return false;
            }
            return true;
        }
        return !requestedFileTypes.contains(ext.toLowerCase());
    }

    /**
     * Unpack an archive and convert items found.
     * Given (input)/A.zip
     * The zip is dearchived to
     *    (input)/A_zip/
     * or (archive)/(input)/A_zip
     *
     * Items are then converted in either folder for the conversion archiving; depending on your choice of embedded vs. non-embedded
     * @param input archive file object
     * @throws IOException on err
     * @throws ConfigException on err
     *
     */
    public void convertArchive(File input) throws IOException, ConfigException {

        if (!paths.verifyArchiveExport(input.getAbsolutePath())) {
            return;
        }

        File saveFolder = paths.getArchiveExportDir(input);
        String savePrefix = paths.getStipPrefixPath();

        paths.setStripPrefixPath(saveFolder.getAbsolutePath());
        paths.setInputRoot(saveFolder);

        ArchiveNavigator deArchiver = new ArchiveNavigator(input, saveFolder.getAbsolutePath(),
                this, this);
        deArchiver.overwrite = ConvertedDocument.overwrite;

        log.info("\tArchive Found ({}). Expanding to {}", input, saveFolder);

        deArchiver.collect();

        // Done:
        paths.setStripPrefixPath(savePrefix);
    }

    /**
     *
     * @param input input PST object
     * @throws IOException on err
     * @throws ConfigException on err
     */
    public void convertOutlookPST(File input) throws ConfigException, IOException {
        if (!paths.isSaving()) {
            log.error("Warning -- PST file found, but save = true is required to parse it.  Enable saving and chose a cache folder");
        }

        OutlookPSTCrawler pst = new OutlookPSTCrawler(input);
        pst.setConverter(this);
        pst.overwriteMode = ConvertedDocument.overwrite;
        pst.incrementalMode = true;

        File saveFolder = paths.getArchiveExportDir(input);
        String savePrefix = paths.getStipPrefixPath();

        paths.setStripPrefixPath(saveFolder.getAbsolutePath());
        paths.setInputRoot(saveFolder);
        pst.setOutputPSTDir(saveFolder);
        pst.configure();

        log.info("\tPST Email Archive Found ({}). Expanding to {}", input, saveFolder);

        try {
            pst.collect();
        } catch (Exception err) {
            throw new ConfigException("Unable to fully digest PST file " + input, err);
        }

        // Done:
        paths.setStripPrefixPath(savePrefix);
    }

    /**
     * Arbitrary 32 MB limit on file size. Maybe this should be dependent on the
     * file type.
     */
    public static final long FILE_SIZE_LIMIT = 0x2000000;

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
     * @param input file
     * @throws ConfigException on err
     * @throws IOException on err
     */
    @Override
    public ConvertedDocument convert(File input) throws IOException, ConfigException {
        return convertFile(input);
    }

    /**
     * Unsupported iConvert interface method. To convert text from a String obj
     * rather than a File obj, you would instantiate a converter implementation
     * for the data you think you are converting. E.g., if you know you have a
     * buffer of HTML content and want to save it as text, call
     * TikaHTMLConverter().convert( buffer ) directly.
     *
     * @param data raw data
     * @return the converted document
     * @throws IOException on err
     */
    @Override
    public ConvertedDocument convert(String data) throws IOException {
        throw new IOException("Unsupported interface:  To convert text or binary data directly "
                + "you must use an instance of a XText converter, e.g., TikaHTMLConverter");
    }

    /**
     * Convert file.
     *
     * @param input the input
     * @return the converted document
     * @throws IOException on err
     * @throws ConfigException on err
     */
    public ConvertedDocument convertFile(File input) throws IOException, ConfigException {
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
     * @param input child input obj to convert
     * @param parent parent in which child was found
     * @return converted document object
     * @throws IOException on err
     * @throws ConfigException on err
     */
    public ConvertedDocument convertFile(File input, ConvertedDocument parent) throws IOException,
    ConfigException {

        if (parent == null && filterOutFile(input)) {
            return null;
        }

        if (paths.isSaving()) {
            if (!paths.isSaveWithInput() && !paths.hasInputRoot()) {

                throw new IOException(
                        "Please set an input root; convertFile() was called in save/cache mode without having PathManager setup");
            }
        }

        String fname = input.getName();

        String ext = FilenameUtils.getExtension(fname).toLowerCase();
        if (!allowNoExtension) {
            if (ignoreFileTypes.contains(ext)) {
                return null;
            }

            if (!requestedFileTypes.contains(ext)) {
                return null;
            }
        }

        log.debug("Converting FILE=" + input.getAbsolutePath());

        /*
         * Handle archives or PST files. Or other large compound single file.
         */
        if (isArchive(fname)) {
            convertArchive(input);

            // NULL here implies the actual file, A.zip does not have any text representation itself.
            // However its children do.
            return null;
        } else if (isPSTExtension(ext) && !useTikaPST) {
            convertOutlookPST(input);
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
        Converter converter = converters.get(ext);
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
        if (cachable && !ConvertedDocument.overwrite && paths.isSaving()) {
            textDoc = paths.getCachedConversion(input);
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
                throw new IOException("Conversion error FILE=" + input.getPath(), convErr);
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
                if (paths.isSaving() && textDoc.is_converted) {
                    // Get Parent info in there.
                    if (parent != null) {
                        textDoc.setParent(parent);
                    }

                    paths.saveConversion(textDoc);

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
     * @param input the input
     * @throws IOException on err
     */
    public void convertFolder(File input) throws IOException {
        java.util.Collection<File> files = FileUtils.listFiles(input, new SuffixFileFilter(
                fileFilters, IOCase.INSENSITIVE), FileFilterUtils.trueFileFilter());
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
     * @param parentDoc parent conversion
     * @throws IOException on err
     */
    public void convertChildren(ConvertedDocument parentDoc) throws IOException {

        if (parentDoc.is_webArchive) {
            // Web Archive is a single document.  Only intent here is to convert to a single text document.
            //
            return;
        }

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

        archiveFileTypes.add("zip");
        archiveFileTypes.add("gz");
        archiveFileTypes.add("tar");
        archiveFileTypes.add("tgz");
        archiveFileTypes.add("tar.gz");
        // archive_types.add("7z");

        // Get from a config file.
        requestedFileTypes.add("doc");
        requestedFileTypes.add("docx");
        requestedFileTypes.add("pdf");
        requestedFileTypes.add("htm");
        requestedFileTypes.add("html");
        requestedFileTypes.add("txt"); // only for encoding conversions.
        requestedFileTypes.add("msg");
        requestedFileTypes.add("eml");
        requestedFileTypes.add("emlx");
        requestedFileTypes.add("ppt");
        requestedFileTypes.add("pptx");
        requestedFileTypes.add("xlsx");
        requestedFileTypes.add("xls");
        requestedFileTypes.add("rtf");

        // Testing:
        requestedFileTypes.add("dot");
        requestedFileTypes.add("dotx");
        requestedFileTypes.add("odt");
        requestedFileTypes.add("odf");
        requestedFileTypes.add("docm");

        // Web Archives.
        requestedFileTypes.add("mht");
        //requestedFileTypes.add("wps");  MS Works?  No tika support really.

        // Only Photographic images will be supported by default.
        // BMP, GIF, PNG, ICO, etc. must be added by caller.
        //
        requestedFileTypes.add("jpg");
        requestedFileTypes.add("jpeg");

        // Limited PST support here.  PST will not behave the same as other files.
        // Its closer to a Zip archive than an ordinary file.
        requestedFileTypes.add("pst");

        // requested_types.add("log"); // Uncommon. Caller must expclitly add
        // raw data types and archives.
    }

    /**
     * Start over.
     */
    public void clearSettings() {
        requestedFileTypes.clear();
        converters.clear();
    }

    /**
     * If by this point you have taken items out of the requested types the
     * converters will not be setup. E.g., if you don't want PDF or HTML
     * conversion - those resources will not be initialized.
     * @throws IOException on err
     */
    public void setup() throws IOException {

        defaultConversion = new DefaultConverter(maxBuffer);
        embeddedConversion = new EmbeddedContentConverter(maxBuffer);

        paths.configure();

        // Invoke converter instances only as requested types suggest.
        // If caller has removed file types from the list, then

        String mimetype = "txt";
        if (requestedFileTypes.contains(mimetype)) {
            converters.put(mimetype, new TextTranscodingConverter());
        }

        mimetype = "html";
        if (requestedFileTypes.contains(mimetype)) {
            Converter webConv = new TikaHTMLConverter(this.scrubHTML, maxHTMLBuffer);
            converters.put(mimetype, webConv);
            converters.put("htm", webConv);
            converters.put("xhtml", webConv);

            requestedFileTypes.add("htm");
            requestedFileTypes.add("xhtml");
        }

        MessageConverter emailParser = new MessageConverter();
        mimetype = "eml";
        if (requestedFileTypes.contains(mimetype)) {
            converters.put(mimetype, emailParser);
        }
        mimetype = "msg";
        if (requestedFileTypes.contains(mimetype)) {
            converters.put(mimetype, emailParser);
        }
        WebArchiveConverter webArchiveParser = new WebArchiveConverter();
        mimetype = "mht"; /* RFC822 */
        if (requestedFileTypes.contains(mimetype)) {
            converters.put(mimetype, webArchiveParser);
        }

        ImageMetadataConverter imgConv = new ImageMetadataConverter();
        String[] imageTypes = { "jpeg", "jpg" };
        for (String img : imageTypes) {
            if (requestedFileTypes.contains(img)) {
                converters.put(img, imgConv);
            }
        }

        // ALWAYS ignore our own text conversions or those of others.
        // So here all known convertable types will need a filter for their
        // conversion, e.g.,
        // pdf => ignore pdf.txt
        // doc => ignore doc.txt
        //
        for (String t : requestedFileTypes) {
            ignoreFileType(t + ".txt");
        }

        fileFilters = requestedFileTypes.toArray(new String[requestedFileTypes.size()]);
    }

    /**
     *
     */
    private String[] fileFilters = null;

    /**
     * Call after setup() has run to add all supported/requested file types
     * @return file types as a set
     */
    public Set<String> getFileTypes() {
        return requestedFileTypes;
    }

    public static void usage() {
        System.out.println();
        System.out.println("==========XText Usage=============");
        System.out
        .println("XText --input input  [--help] "
                + "\n\t[--embed-conversion | --output folder ]   "
                + "\n\t[--embed-children   | --export folder] "
                + "\n\t[--clean-html]   [--strip-prefix path]");
        System.out.println(" --help  print this message");
        System.out.println(" --input  where <input> is file or folder");
        System.out.println(" --output  where <folder> is output is a folder where you want to archive converted docs");
        System.out.println(" --embed-children embeds the saved conversions in the input folder under 'xtext/'");
        System.out.println(" --embed-conversion embeds the extracted children binaries in the input folder");
        System.out.println("     (NOT the conversions, the binaries from Archives, PST, etc)");
        System.out.println("     Default behavior is to extract originals to output archive.");
        System.out.println(" --export folder\tOpposite of -c. Extract children and save to <folder>");
        System.out.println("     NOTE: -e has same effect as setting output to input");
        System.out.println(" -clean-html enables HTML scrubbing");
        System.out.println("========================");
    }

    /**
     * Purely for logging when using the cmd line variation.
     * *
     * @author ubaldino
     *
     */
    static class MainProgramListener implements ConversionListener {

        private final Logger log = LoggerFactory.getLogger(getClass());

        @Override
        public void handleConversion(ConvertedDocument doc, String path) {
            boolean converted = false;
            if (doc != null) {
                converted = doc.is_converted;
            }
            log.info("Converted. FILE={} Status={}, Converted={}", path, doc != null, converted);
        }
    }

    public static void main(String[] args) {

        LongOpt[] options = { new LongOpt("input", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
                new LongOpt("output", LongOpt.REQUIRED_ARGUMENT, null, 'o'),
                new LongOpt("export", LongOpt.REQUIRED_ARGUMENT, null, 'x'),
                new LongOpt("strip-prefix", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
                new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
                new LongOpt("clean-html", LongOpt.NO_ARGUMENT, null, 'H'),
                new LongOpt("embed-conversion", LongOpt.NO_ARGUMENT, null, 'e'),
                new LongOpt("embed-children", LongOpt.NO_ARGUMENT, null, 'c'),
                new LongOpt("tika-pst", LongOpt.NO_ARGUMENT, null, 'T') };

        // "hcex:i:o:p:"
        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("XText", args, "", options);

        String input = null;
        String output = null;
        boolean embed = false;
        boolean filter_html = false;
        boolean saveChildrenWithInput = false;
        String saveChildrenTo = null;
        String prefix = null;

        XText xt = new XText();

        try {
            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {

                case 0:
                    // Long opt processed.

                    break;

                case 'i':
                    input = opts.getOptarg();                    
                    break;
                case 'o':
                    output = opts.getOptarg();
                    break;
                case 'H':
                    filter_html = true;
                    break;
                case 'c':
                    saveChildrenWithInput = true;
                    break;
                case 'x':
                    saveChildrenTo = opts.getOptarg();
                    break;
                case 'p':
                    prefix = opts.getOptarg();
                    break;
                case 'e':
                    embed = true;
                    System.out
                    .println("Saving conversions to Input folder.  Output folder will be ignored.");
                    break;
                case 'T':
                    xt.enableTikaPST(true);
                    break;
                case 'h':
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

        xt.enableOverwrite(true); // Given this is a test application, we will
        // overwrite every time XText is called.
        xt.enableSaving(embed || output != null);
        xt.getPathManager().enableSaveWithInput(embed); // creates a ./text/ Folder locally in
        // directory.
        xt.enableHTMLScrubber(filter_html);
        xt.getPathManager().enableSaveChildrenWithInput(saveChildrenWithInput);

        // If user wishes to strip input paths of some prefix
        // Output will be dumped in the resulting relative path.
        xt.getPathManager().setStripPrefixPath(prefix);

        // Manage the extraction of compound files -- archives, PST mailbox file, etc.
        // ... others?
        if (!saveChildrenWithInput && saveChildrenTo != null) {
            xt.getPathManager().setExtractedChildrenCache(saveChildrenTo);
        }

        try {
            if (output == null && !embed) {
                output = "output";
                xt.enableSaving(true); // Will save to output dir.
                FileUtility.makeDirectory(output);
                System.out.println("Default output folder is $PWD/" + output);
            }
            // Notice this main program requires an output path.
            if (output != null) {
                xt.getPathManager().setConversionCache(output);
            }
            // Set itself to listen, as this is the main program.
            xt.setConversionListener(new MainProgramListener());

            xt.setup();
            xt.extractText(input);
        } catch (IOException ioerr) {
            XText.usage();
            ioerr.printStackTrace();
        } catch (ConfigException cfgerr) {
            XText.usage();
            cfgerr.printStackTrace();
        }
    }
}
