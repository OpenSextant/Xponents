/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
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
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
package org.opensextant.util;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author ubaldino
 */
public class FileUtility {

    /** private constructor */
    private FileUtility(){}

    /**
     * Write file, UTF-8 is default charset here.
     *
     * @param buffer text to save
     * @param fname  name of file to save
     * @return status true if file was written
     * @throws IOException if file had IO errors.
     */
    public static boolean writeFile(String buffer, String fname) throws IOException {
        return writeFile(buffer, fname, DEFAULT_ENCODING, false);
    }

    /**
     * @param buffer text to save
     * @param fname  name of file to save
     * @param enc    text encoding
     * @param append if you wish to add to existing file.
     * @return status if written
     * @throws IOException if file had IO errors.
     */
    public static boolean writeFile(String buffer, String fname, String enc, boolean append) throws IOException {
        if (fname == null || enc == null || buffer == null) {
            throw new IOException("Null values cannot be used to write out file.");
        }

        try (final FileOutputStream file = new FileOutputStream(fname, append)) {// APPEND
            final OutputStreamWriter fout = new OutputStreamWriter(file, enc);
            fout.write(buffer, 0, buffer.length());
            fout.flush();
            return true;
        }
    }

    /**
     * Caller is responsible for write flush, close, etc.
     *
     * @param fname  file path
     * @param enc    encoding
     * @param append true = append data to existing file.
     * @return stream writer
     * @throws IOException if stream could not be opened
     */
    public static OutputStreamWriter getOutputStream(String fname, String enc, boolean append) throws IOException {
        return new OutputStreamWriter(new FileOutputStream(fname, append), enc);
    }

    /**
     * Caller is responsible for write flush, close, etc.
     *
     * @param fname file name
     * @param enc   text encoding
     * @return stream writer
     * @throws IOException if stream could not be openeed
     */
    public static OutputStreamWriter getOutputStream(String fname, String enc) throws IOException {
        return getOutputStream(fname, enc, false);
    }

    /**
     * Getting an input stream from a file.
     *
     * @param f   file object
     * @param enc encoding of text data
     * @return reader
     * @throws IOException if failure reading file or using encoding.
     */
    public static InputStreamReader getInputStreamReader(File f, String enc) throws IOException {
        return getInputStream(f, enc);
    }

    public static InputStreamReader getInputStream(String fname, String enc) throws IOException {
        return getInputStream(new File(fname), enc);
    }

    public static InputStreamReader getInputStream(File f, String enc) throws IOException {
        if (f.getName().toLowerCase().endsWith(".gz")) {
            return new InputStreamReader(new GZIPInputStream(new FileInputStream(f)), enc);
        } else {
            return new InputStreamReader(new FileInputStream(f), enc);
        }
    }

    /**
     * Simple check if a file is typed as a Spreadsheet Tab-delimited .txt files or
     * .dat files may be
     * valid spreadsheets, however this method does not look inside files.
     *
     * @param filepath path to file
     * @return true if file represents one of the various spreadsheet file formats
     */
    public static boolean isSpreadsheet(String filepath) {
        final String testpath = filepath.toLowerCase();
        return (testpath.endsWith(".csv") || testpath.endsWith(".xls") || testpath.endsWith(".xlsx"));
    }

    /**
     * Using Commons getExtension(), determine if the filename represents an image
     * media type.
     *
     * @param filepath path to file
     * @return if file represents any type of image
     */
    public static boolean isImage(String filepath) {
        if (filepath == null) {
            return false;
        }
        final String ext = FilenameUtils.getExtension(filepath.toLowerCase());
        return imageTypeMap.containsKey(ext);
    }

    /**
     * Checks file extension of given filepath to see if the format is a known video
     * type.
     *
     * @param filepath file name or path
     * @return true if file is likely an video file format.
     */
    public static boolean isVideo(String filepath) {
        if (filepath == null) {
            return false;
        }
        final String ext = FilenameUtils.getExtension(filepath.toLowerCase());
        return VID_MIMETYPE.equals(filetypeMap.get(ext));
    }

    /**
     * Checks file extension of given filepath to see if the format is a known audio
     * type.
     *
     * @param filepath file name or path
     * @return true if file is likely an audio file format.
     */
    public static boolean isAudio(String filepath) {
        if (filepath == null) {
            return false;
        }
        final String ext = FilenameUtils.getExtension(filepath.toLowerCase());
        return AUD_MIMETYPE.equals(filetypeMap.get(ext));
    }

    /**
     * Check if a file is an archive
     *
     * @param filepath path to file
     * @return boolean true if file ends with .zip, .tar, .tgz, .gz (includes
     * .tar.gz)
     */
    public static boolean isArchiveFile(String filepath) {
        final String testpath = filepath.toLowerCase();
        return testpath.endsWith(".zip") || testpath.endsWith(".tar") || testpath.endsWith(".tgz")
                || testpath.endsWith(".gz"); // || testpath.endsWith(".tar.gz");
    }

    /**
     * Allow checking of a file extention; NO prefix "."
     *
     * @param ext extension to test
     * @return boolean true if file ends with .zip, .tar, .tgz, .gz (includes
     * .tar.gz)
     */
    public static boolean isArchiveFileType(String ext) {
        final String x = ext.toLowerCase();
        return x.equals("zip") || x.equals("tar") || x.equals("tgz") || x.equals("gz") || x.equals("tar.gz");
    }

    /**
     * Test is a path or file extension ends with .txt NPE if null is passed in.
     *
     * @param filepath path or extension, including "."
     * @return true if is .txt or .TXT
     */
    public static boolean isPlainText(String filepath) {
        return filepath.toLowerCase().endsWith(".txt");
    }

    /**
     * @param filepath path to file
     * @return buffer from file
     * @throws IOException on error
     */
    public static String readFile(String filepath) throws IOException {
        return readFile(new File(filepath), DEFAULT_ENCODING);
    }

    /**
     * @param filepath path to file
     * @return buffer from file
     * @throws IOException on error
     */
    public static String readFile(File filepath) throws IOException {
        return readFile(filepath, DEFAULT_ENCODING);
    }

    /**
     *
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     *
     */
    private static final int IO_BUFFER_SIZE = 0x800;

    /**
     * Slurps a text file into a string and returns the string.
     *
     * @param fileinput file object
     * @param enc       text encoding
     * @return buffer from file
     * @throws IOException on error
     */
    public static String readFile(File fileinput, String enc) throws IOException {
        if (fileinput == null) {
            return null;
        }

        try (final FileInputStream instream = new FileInputStream(fileinput)) {
            return new String(IOUtils.toByteArray(instream), enc);
        }
    }

    /**
     * @param filepath path to file
     * @return text buffer, UTF-8 decoded
     * @throws IOException on error
     */
    public static String readGzipFile(String filepath) throws IOException {
        if (filepath == null) {
            return null;
        }

        try (FileInputStream instream = new FileInputStream(filepath);
             GZIPInputStream gzin = new GZIPInputStream(new BufferedInputStream(instream), IO_BUFFER_SIZE)) {
            return new String(IOUtils.toByteArray(gzin), DEFAULT_ENCODING);
        }
    }

    /**
     * @param text     buffer to write
     * @param filepath path to file
     * @return status true if file was written
     * @throws IOException on error
     */
    public static boolean writeGzipFile(String text, String filepath) throws IOException {
        if (filepath == null || text == null) {
            return false;
        }

        try (FileOutputStream outstream = new FileOutputStream(filepath);
             GZIPOutputStream gzout = new GZIPOutputStream(new BufferedOutputStream(outstream), IO_BUFFER_SIZE)) {

            gzout.write(text.getBytes(DEFAULT_ENCODING));
            gzout.flush();
            gzout.finish();
            return true;
        }
    }

    /**
     * Utility for making dirs
     *
     * @param testDir dir to test
     * @return if directory was created or if it already exists
     * @throws IOException if testDir was not created
     */
    public static boolean makeDirectory(File testDir) throws IOException {
        if (testDir == null) {
            return false;
        }

        if (testDir.isDirectory() && testDir.exists()) {
            return true;
        }

        if (testDir.isFile() && testDir.exists()) {
            throw new IOException("Cannot overwrite existing file with a directory of the same name.");
        }
        return testDir.mkdirs();
    }

    /**
     * Utility for making dirs
     *
     * @param dir dirPath
     * @return if directory was created or if it already exists
     * @throws IOException if testDir was not created
     */
    public static boolean makeDirectory(String dir) throws IOException {
        if (dir == null) {
            return false;
        }

        return makeDirectory(new File(dir));
    }

    /**
     * Java oddity - recursive removal of a directory
     *
     * @param directory dir to remove
     * @return if all contents and dir itself was removed.
     * @author T. Allison, MITRE
     */
    public static boolean removeDirectory(File directory) {
        // taken from
        // http://www.java2s.com/Tutorial/Java/0180__File/Removeadirectoryandallofitscontents.htm

        if (directory == null) {
            return false;
        }
        if (!directory.exists()) {
            return true;
        }
        if (!directory.isDirectory()) {
            return false;
        }

        final String[] list = directory.list();

        // Some JVMs return null for File.list() when the
        // directory is empty.
        if (list != null) {
            for (String s : list) {
                final File entry = new File(directory, s);
                if (entry.isDirectory()) {
                    if (!removeDirectory(entry)) {
                        return false;
                    }
                } else {
                    if (!entry.delete()) {
                        return false;
                    }
                }
            }
        }

        return directory.delete();
    }

    /**
     * Generate some path with a unique date/time stamp
     *
     * @param D   directory
     * @param F   filename
     * @param Ext file extension
     * @return unique path
     */
    public static String generateUniquePath(String D, String F, String Ext) {
        return D + File.separator + generateUniqueFilename(F, Ext);
    }

    /**
     * Generate some filename with a unique date/time stamp
     *
     * @param F   filename
     * @param Ext file extension
     * @return unique filename
     */
    public static String generateUniqueFilename(String F, String Ext) {

        final SimpleDateFormat fileDateFmt = new SimpleDateFormat("_yyyyMMdd,HHmmss,S");

        return F + fileDateFmt.format(new Date()) + Ext;
    }

    /**
     * @param f the file in question.
     * @return the parent File of a given file.
     */
    public static File getParent(File f) {
        return new File(f.getAbsolutePath()).getParentFile();
    }

    /**
     * Simple filter
     *
     * @param ext the extension to filter on
     * @return filename filter
     */
    public static FilenameFilter getFilenameFilter(String ext) {
        return new AnyFilenameFilter(ext);
    }

    /**
     * get the base name of a file, given any file extension. This will find the
     * right-most instance of
     * a file extension and return the left hand side of that as the file basename.
     * commons io FilenameUtils says nothing about arbitrarily long file extensions,
     * e.g.,
     * file.a.b.c.txt split into ("file" + "a.b.c.txt")
     *
     * @param p   path
     * @param ext extension
     * @return basename of path, less the extension
     */
    public static String getBasename(String p, String ext) {
        if (p == null) {
            return null;
        }
        final String fn = FilenameUtils.getBaseName(p);
        if (ext == null || ext.isEmpty()) {
            return fn;
        }
        if (fn.toLowerCase().endsWith(ext)) {
            final int lastidx = fn.length() - ext.length() - 1;
            return fn.substring(0, lastidx);
        }
        return fn;
    }

    /**
     * On occasion file path may contain unicode chars, however as the is encoded,
     * it may not be
     * decodable by OS/FS.
     *
     * @param path path to normalize
     * @return filename
     */
    public static String getValidFilename(String path) {
        return TextUtils.normalizeUnicode(path);
    }

    /**
     * Another utility to deal with unicode in filenames
     *
     * @param fname name to clean
     * @return cleaner filenname
     */
    public static String filenameCleaner(String fname) {

        if (fname == null) {
            return null;
        }
        if (fname.length() == 0) {
            return null;
        }

        final char[] text = fname.toCharArray();
        final StringBuilder cleaned_text = new StringBuilder();

        for (final char c : text) {
            cleaned_text.append(normalizeFilenameChar(c));
        }

        return cleaned_text.toString();
    }

    /**
     * Get a directory that does not conflict with an existing directory. Returns
     * null if that is not
     * possible within the maxDups.
     *
     * @param dir        directory
     * @param dupeMarker incrementor
     * @param maxDups    max incrementor
     * @return file object
     * @author T. Allison NOT THREAD SAFE!
     */
    public static File getSafeDir(File dir, String dupeMarker, int maxDups) {

        if (!dir.exists()) {
            return dir;
        }
        final String base = dir.getName();
        for (int i = 1; i < maxDups; i++) {
            final File tmp = new File(dir.getParentFile(), base + dupeMarker + i);
            if (!tmp.isDirectory()) {
                return tmp;
            }
        }
        return null;
    }

    /**
     * @param f          file obj
     * @param dupeMarker incrementor
     * @param maxDups    max incrementor
     * @return new file
     * @author T. Allison
     */
    public static File getSafeFile(File f, String dupeMarker, int maxDups) {
        if (!f.exists()) {
            return f;
        }

        final int suffixInd = f.getName().lastIndexOf(".");
        final String base = f.getName().substring(0, suffixInd);
        final String suffix = (suffixInd + 1 <= f.getName().length()) ? f.getName().substring(suffixInd + 1) : "";
        for (int i = 1; i < maxDups; i++) {
            final File tmp = new File(f.getParentFile(), base + dupeMarker + i + "." + suffix);
            if (!tmp.exists()) {
                return tmp;
            }
        }
        return null;
    }

    /**
     * Char to use in place of special chars when scrubbing filenames.
     */
    public static final char FILENAME_REPLACE_CHAR = '_';

    /**
     * Tests for valid filename chars for simple normalization A-Z, a-z, _-, 0-9,
     *
     * @param c character to allow
     * @return given character or replacement char
     */
    protected static char normalizeFilenameChar(char c) {

        if (c >= 'A' && c <= 'Z') {
            return c;
        }
        if (c >= 'a' && c <= 'z') {
            return c;
        }
        if (c >= '0' && c <= '9') {
            return c;
        }
        if (c == '_' || c == '-') {
            return c;
        } else {
            return FILENAME_REPLACE_CHAR;
        }
    }

    /**
     * A way of determining OS Beware, OS X has Darwin in its full OS name.
     *
     * @return if OS is windows-based
     */
    public static boolean isWindowsSystem() {
        final String val = System.getProperty("os.name");
        return (val != null && val.contains("Windows"));
    }

    /**
     * Char used in config files, dict files.
     */
    public static final String COMMENT_CHAR = "#";

    /**
     * A generic word list loader.
     *
     * @param resourcepath   classpath location of a resource
     * @param case_sensitive if terms are loaded with case preserved or not.
     * @return Set containing unique words found in resourcepath
     * @throws IOException on error, resource does not exist
     * @author ubaldino, MITRE Corp
     */
    public static Set<String> loadDictionary(String resourcepath, boolean case_sensitive) throws IOException {
        InputStream io = FileUtility.class.getResourceAsStream(resourcepath);
        if (io == null) {
            throw new IOException("Resource " + resourcepath + "  was not found");
        }
        return loadDict(io, case_sensitive);
    }

    /**
     * A generic word list loader.
     *
     * @param resourcepath   classpath location of a resource
     * @param case_sensitive if terms are loaded with case preserved or not.
     * @return Set containing unique words found in resourcepath
     * @throws IOException on error, resource does not exist
     * @author ubaldino, MITRE Corp
     */
    public static Set<String> loadDictionary(URL resourcepath, boolean case_sensitive) throws IOException {
        return loadDict(resourcepath.openStream(), case_sensitive);
    }

    /**
     * The do all method. Load the dictionary from stream This closes the stream
     * when done.
     *
     * @param io             stream
     * @param case_sensitive true if data should be loaded preserving case
     * @return set of phrases from file.
     * @throws IOException on IO error
     */
    public static Set<String> loadDict(InputStream io, boolean case_sensitive) throws IOException {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(io, DEFAULT_ENCODING))) {

            final Set<String> dict = new HashSet<>();
            String newline = null;
            String test = null;
            while ((newline = reader.readLine()) != null) {
                test = newline.trim();
                if (test.startsWith(COMMENT_CHAR) || test.length() == 0) {
                    continue;
                }
                if (case_sensitive) {
                    dict.add(test);
                } else {
                    dict.add(test.toLowerCase());
                }
            }
            return dict;
        }
    }

    /**
     * Load a word list from a file path.
     *
     * @param resourcepath   File object to load
     * @param case_sensitive if dictionary is loaded with case or not.
     * @return a Set object containing distinct dictionary terms
     * @throws IOException if load fails
     */
    public static Set<String> loadDictionary(File resourcepath, boolean case_sensitive) throws IOException {
        try (InputStream io = new FileInputStream(resourcepath)) {
            return loadDict(io, case_sensitive);
        }
    }

    //
    //
    // Working with file types
    //
    //
    private static final HashMap<String, String> filetypeMap = new HashMap<>();
    public static final String IMAGE_MIMETYPE = "image";
    public static final String DOC_MIMETYPE = "document";
    public static final String MESSAGE_MIMETYPE = "message";
    public static final String APP_MIMETYPE = "application";
    public static final String VID_MIMETYPE = "video";
    public static final String AUD_MIMETYPE = "audio";
    public static final String FOLDER_MIMETYPE = "folder";
    public static final String FEED_MIMETYPE = "feed";
    public static final String DATA_MIMETYPE = "data";
    public static final String WEBARCHIVE_MIMETYPE = "web archive";
    public static final String WEBPAGE_MIMETYPE = "web page";
    public static final String SPREADSHEET_MIMETYPE = "spreadsheet";
    public static final String NOT_AVAILABLE = "other";
    public static final String GIS_MIMETYPE = "GIS data";

    private static final HashMap<String, String> imageTypeMap = new HashMap<>();

    static {

        // Image
        imageTypeMap.put("jpg", IMAGE_MIMETYPE);
        imageTypeMap.put("jpeg", IMAGE_MIMETYPE);
        imageTypeMap.put("jp2", IMAGE_MIMETYPE);
        imageTypeMap.put("jpx", IMAGE_MIMETYPE);
        imageTypeMap.put("ico", IMAGE_MIMETYPE);
        imageTypeMap.put("bmp", IMAGE_MIMETYPE);
        imageTypeMap.put("gif", IMAGE_MIMETYPE);
        imageTypeMap.put("png", IMAGE_MIMETYPE);
        imageTypeMap.put("tif", IMAGE_MIMETYPE);
        imageTypeMap.put("tiff", IMAGE_MIMETYPE);
        filetypeMap.putAll(imageTypeMap);

        filetypeMap.put("", NOT_AVAILABLE);

        // GIS Data
        filetypeMap.put("gdb", GIS_MIMETYPE);
        filetypeMap.put("shp", GIS_MIMETYPE);
        filetypeMap.put("kml", GIS_MIMETYPE);
        filetypeMap.put("kmz", GIS_MIMETYPE);

        // Data
        filetypeMap.put("dat", DATA_MIMETYPE);
        filetypeMap.put("xml", DATA_MIMETYPE);
        filetypeMap.put("rdf", DATA_MIMETYPE);

        // Archive
        filetypeMap.put("mht", WEBARCHIVE_MIMETYPE);
        filetypeMap.put("mhtml", WEBARCHIVE_MIMETYPE);

        filetypeMap.put("csv", SPREADSHEET_MIMETYPE);
        filetypeMap.put("xls", SPREADSHEET_MIMETYPE);
        filetypeMap.put("xlsx", SPREADSHEET_MIMETYPE);

        filetypeMap.put("htm", WEBPAGE_MIMETYPE);
        filetypeMap.put("html", WEBPAGE_MIMETYPE);

        // Docs
        filetypeMap.put("odf", DOC_MIMETYPE);
        filetypeMap.put("doc", DOC_MIMETYPE);
        filetypeMap.put("ppt", DOC_MIMETYPE);
        filetypeMap.put("pdf", DOC_MIMETYPE);
        filetypeMap.put("ps", DOC_MIMETYPE);
        filetypeMap.put("vsd", DOC_MIMETYPE);
        filetypeMap.put("txt", DOC_MIMETYPE);
        filetypeMap.put("pptx", DOC_MIMETYPE);
        filetypeMap.put("docx", DOC_MIMETYPE);

        // Messages
        filetypeMap.put("eml", MESSAGE_MIMETYPE);
        filetypeMap.put("emlx", MESSAGE_MIMETYPE);
        filetypeMap.put("msg", MESSAGE_MIMETYPE);
        filetypeMap.put("sms", MESSAGE_MIMETYPE);

        // Apps
        filetypeMap.put("do", APP_MIMETYPE);
        filetypeMap.put("aspx", APP_MIMETYPE);
        filetypeMap.put("asp", APP_MIMETYPE);
        filetypeMap.put("axd", APP_MIMETYPE);
        filetypeMap.put("js", APP_MIMETYPE);
        filetypeMap.put("php", APP_MIMETYPE);
        filetypeMap.put("vbs", APP_MIMETYPE);
        filetypeMap.put("vb", APP_MIMETYPE);
        filetypeMap.put("vba", APP_MIMETYPE);

        // Video
        filetypeMap.put("mov", VID_MIMETYPE);

        filetypeMap.put("rm", VID_MIMETYPE);
        filetypeMap.put("wmv", VID_MIMETYPE);
        filetypeMap.put("mp4", VID_MIMETYPE);
        filetypeMap.put("mpeg", VID_MIMETYPE);
        filetypeMap.put("mpg", VID_MIMETYPE);

        // Audio
        filetypeMap.put("au", AUD_MIMETYPE);
        filetypeMap.put("wma", AUD_MIMETYPE);
        filetypeMap.put("mp3", AUD_MIMETYPE);
        filetypeMap.put("ra", AUD_MIMETYPE);

        // Data Feed
        filetypeMap.put("rss", FEED_MIMETYPE);
    }

    /**
     * Get a plain language name of the type of file. E.g., document, image,
     * spreadsheet, web page.
     * Rather than the MIME type technical descriptor.
     *
     * @param url item to describe
     * @return plain language description of the URL
     */
    public static String getFileDescription(String url) {
        if (url == null) {
            return NOT_AVAILABLE;
        }

        // ------------

        /*
         * path: http://a/b.htm
         */
        final String test = url.toLowerCase();

        /*
         * path: /a/b/
         */
        if (url.endsWith("/") && !test.startsWith("http")) {
            return FOLDER_MIMETYPE;
        }

        final String urlTestExtension = FilenameUtils.getExtension(test);

        /*
         * Known file type.
         */
        final String urlMimeType = filetypeMap.get(urlTestExtension);
        if (urlMimeType != null) {
            return urlMimeType;
        }

        /*
         * path: .../abc.rss
         */
        if (test.contains("rss")) {
            return FEED_MIMETYPE;
        }

        if (test.startsWith("http:") || test.startsWith("https:")) {
            return WEBPAGE_MIMETYPE;
        }

        /*
         * path: /some/default/path
         */
        if (url.contains("/")) {
            return FOLDER_MIMETYPE;
        }

        /*
         * Give up.
         */
        return NOT_AVAILABLE;
    }

    /**
     * Check if path or URL is a webpage. This is helpful for looking at found URLs
     * in unstructured
     * data.
     *
     * @param link a URL
     * @return true if link looks like a URL (ie., if it starts with http: or
     * https:)
     */
    public static boolean isWebURL(String link) {
        if (link == null) {
            return false;
        }
        String test = link.toLowerCase();
        return test.startsWith("http:") || test.startsWith("https:");
    }

    /**
     * Tell if the file is JSON/Gzip
     *
     * @param path input file path
     * @return true if is file ends with json.gz or contains json and ends with .gz
     */
    public static boolean isJSONGzip(String path) {
        String test = path.toLowerCase();
        if (test.endsWith("json.gz")) {
            return true;
        }
        return test.contains("json") && test.endsWith(".gz");
    }
}
