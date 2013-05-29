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
package org.mitre.xtext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import org.apache.commons.io.FilenameUtils;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.apache.commons.io.FileUtils;
import org.mitre.xtext.converters.*;

/**
 *
 * Traverse a folder and return text versions of the documents found. Archiving
 * the text only copies at an output location of your choice.
 *
 *
 * if input is a file, convert. Done.
 *
 * if input is an archive, unpack in temp space, iterate over dir, convert each.
 * Done
 *
 * if input is a folder iterate over dir, convert each. Done
 *
 *
 * TEXT OUTPUT form includes a JSON document header with metadata properties
 * from the original item. These are valid elements of the conversion process.
 * We try to maintain them apart from the true, readable text of the document.
 *
 *
 * Add a ConversiontListener to XText instance to capture the converted document
 * as it comes out of the main loop for converting archives and folders.
 *
 * extract_text() runs over any file type and extracts text, saving it pushing
 * events to one optional listener
 *
 * convertFile(File) will convert a single file, returning a ConvertedDocument
 *
 *
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public final class XText implements iFilter, iConvert {

    private Logger log = LoggerFactory.getLogger(XText.class);
    public boolean save = true;
    public boolean zone_web_content = false;
    public String archiveRoot = null;
    public File inputRoot = null;
    public String tempRoot = null;
    protected String inputNode = null;
    public boolean save_in_folder = false;
    protected Set<String> archive_types = new HashSet<>();
    /**
     *
     */
    public static Map<String, iConvert> converters = new HashMap<>();
    private iConvert defaultConversion;
    private Set<String> requested_types = new HashSet<>();
    private Set<String> ignore_types = new HashSet<>();

    /**
     */
    public XText() {
        defaults();
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
        ignore_types.add(ext);
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

    public void setOutputNode(String name) {
        outputNode = archiveRoot + File.separator + name;
    }
    protected long total_conv_time = 0;
    protected int average_conv_time = 0;
    protected int total_conversions = 0;

    protected void trackStatistics(ConvertedDocument d) {
        if (d != null) {
            total_conv_time += d.conversion_time;
        }
        ++total_conversions;
    }

    protected void reportStatistics() {
        average_conv_time = (int) ((float) total_conv_time / total_conversions);
        log.info("TOTAL of N=" + total_conversions + " documents converted"
                + "\n With an average time (ms) of " + average_conv_time);
    }
    protected long start_time = 0;
    protected long stop_time = 0;

    /**
     * The main entry point to converting compound documents and folders.
     */
    public void extract_text(String path) throws IOException {

        start_time = System.currentTimeMillis();

        File input = new File(path);
        if (!input.exists()) {
            throw new IOException("Non existent input FILE=" + path);
        }

        if (isArchive(path)) {
            inputRoot = null; // Will be set by de-archiver temp.
            inputNode = FilenameUtils.getBaseName(path);
            setOutputNode(inputNode);
            convertArchive(input);
        } else if (input.isFile()) {
            inputRoot = input.getParentFile();
            inputNode = input.getParentFile().getName();
            setOutputNode(inputNode);
            convertFile(input);
        } else if (input.isDirectory()) {
            inputRoot = input;
            inputNode = input.getName();
            setOutputNode(inputNode);
            convertFolder(input);
        }

        stop_time = System.currentTimeMillis();

        log.info("Output can be accessed at " + this.archiveRoot);

        reportStatistics();
    }

    /**
     * Filter the type of files to ignore.
     */
    @Override
    public boolean filterOutFile(String filepath) {

        String n = FilenameUtils.getBaseName(filepath);
        if (n.startsWith(".")) {
            return true;
        }
        if (filepath.contains(".svn")) {
            return true;
        }

        String ext = FilenameUtils.getExtension(filepath);
        if (!requested_types.contains(ext)) {
            return true;
        }
        return false;
    }

    /**
     * Unpack an archive and convert items found.
     */
    public void convertArchive(File input) throws IOException {
        // unpack, traverse, convert, save

        ArchiveNavigator unpacker = new ArchiveNavigator(this.tempRoot, this, this);
        String inPath = unpacker.getWorkingDir() + File.separator + FilenameUtils.getBaseName(input.getName());
        input = new File(inPath);
        unpacker.unpack(input);
    }
    /**
     * Arbitrary 16 MB limit on file size. Maybe this should be dependent on the
     * file type.
     */
    public final static long FILE_SIZE_LIMIT = 0x1000000;

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
     * Convert one file and save it off.
     *
     * We ignore hidden files and files in hidden folders, e.g., .cvs_ignore,
     * mycode/.svn/abc.txt
     *
     */
    public ConvertedDocument convertFile(File input) throws IOException {
        // 
        // 
        if (ConvertedDocument.DEFAULT_EMBED_FOLDER.equals(input.getParentFile().getName())) {
            return null;
        }

        if (filterOutFile(input.getAbsolutePath())) {
            return null;
        }

        String fname = input.getName();

        String ext = FilenameUtils.getExtension(fname).toLowerCase();
        if (ignore_types.contains(ext)) {
            return null;
        }

        if (!requested_types.contains(ext)) {
            return null;
        }

        log.info("Converting FILE=" + input.getAbsolutePath());

        if (FileUtils.sizeOf(input) > FILE_SIZE_LIMIT) {
            log.info("Valid File is too large FILE=" + input.getAbsolutePath());
            return null;
        }

        iConvert converter = converters.get(ext);
        if (converter == null) {
            converter = defaultConversion;
        }

        ConvertedDocument textDoc = null;

        //------------------
        // Retrieve previous conversions
        //------------------
        if (!ConvertedDocument.overwrite && this.save) {
            if (this.save_in_folder) {
                // Uncache a file close to the original  F <== ./xtext/F.txt
                textDoc = ConvertedDocument.getEmbeddedConversion(input);
            } else {
                // Uncache a file in some other tree of archives that aligns
                // with the tree of the original source.
                //        .../mine/source/path/F  <====  /archive/source/path/F.txt
                textDoc = ConvertedDocument.getCachedConversion(this.outputNode, this.inputRoot, input);
            }
        }

        //------------------
        // Convert or Read object, IFF no cache exists for that object.
        //------------------
        if (textDoc == null) {
            long t1 = System.currentTimeMillis();
            textDoc = converter.convert(input);
            long t2 = System.currentTimeMillis();
            int duration = (int) (t2 - t1);
            if (textDoc != null && this.save && textDoc.is_converted) {
                if (this.save_in_folder) {
                    // Saves close to original in ./text/ folder where original resides.
                    textDoc.saveEmbedded();
                } else {
                    textDoc.setPathRelativeTo(inputRoot.getAbsolutePath());
                    textDoc.save(outputNode);
                }
            }

            if (textDoc != null) {
                textDoc.conversion_time = duration;
                if (textDoc.filetime == null) {
                    textDoc.filetime = textDoc.getFiletime();
                }
            }
        }


        if (postProcessor != null) {
            postProcessor.handleConversion(textDoc);
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
        java.util.Collection<File> files = FileUtils.listFiles(input, FILE_FILTER, true);
        for (File f : files) {
            try {
                convertFile(f);
            } catch (Exception convErr) {
                log.error("Conversion error, FILE=" + f.getPath(), convErr);
            }
        }
    }

    /**
     * TODO: this is called by default. duh. To changed behavior, adjust
     * settings before setup() is called
     */
    public void defaults() {

        archive_types.add("zip");
        archive_types.add("gz");
        archive_types.add("tar");
        archive_types.add("tgz");
        archive_types.add("tar.gz");

        // Get from a config file.
        requested_types.add("doc");
        requested_types.add("docx");
        requested_types.add("pdf");
        requested_types.add("htm");
        requested_types.add("html");
        requested_types.add("txt");  // only for encoding conversions.
        requested_types.add("msg");
        requested_types.add("eml");
        requested_types.add("ppt");
        requested_types.add("pptx");
        requested_types.add("xlsx");
        requested_types.add("xls");
        requested_types.add("rtf");

        defaultConversion = new DefaultConverter();
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
        defaults();

        // Invoke converter instances only as requested types suggest.
        // If caller has removed file types from the list, then 
        String mimetype = "doc";
        if (requested_types.contains(mimetype)) {
            converters.put(mimetype, new MSDocConverter());
        }

        //mimetype = "docx";
        //if (requested_types.contains(mimetype)) {
        //    converters.put(mimetype, new MSDocxConverter());
        //}

        mimetype = "pdf";
        if (requested_types.contains(mimetype)) {
            converters.put(mimetype, new PDFConverter());
        }

        mimetype = "txt";
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

        //converters.put("eml", new EMailConverter());
        //converters.put("*", new TextTranscodingConverter());

        //mimetype = "html";
        // ALWAYS ignore our own text conversions or those of others.
        // 
        for (String t : requested_types) {
            ignoreFileType(t + ".txt");
        }

        FILE_FILTER = requested_types.toArray(new String[requested_types.size()]);
    }
    /**
     *      */
    private String[] FILE_FILTER = null;

    /**
     * Call after setup() has run to add all supported/requested file types
     */
    public Set<String> getFileTypes() {
        return requested_types;
    }

    public static void main(String[] args) {
        // Setting LANG=en_US in your shell.
        // 
        // System.setProperty("LANG", "en_US");
        XText xt = new XText();
        xt.save = true;
        xt.save_in_folder = true; // creates a ./text/ Folder locally in directory.
        xt.zone_web_content = true;
        xt.archiveRoot = "/tmp/texts";
        xt.tempRoot = "/tmp/xtext";
        xt.save_in_folder = true;

        try {
            xt.setup();
            xt.extract_text(args[0]);
        } catch (IOException ioerr) {
            ioerr.printStackTrace();
        }
    }
}
