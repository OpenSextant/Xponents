/*
 NOTICE

 This software (or technical data) was produced for the U. S.
 Government under contract W15P7T-08-C-F600, and is subject to the
 Rights in Data-General Clause 52.227-14 - Alternate IV (June 1987)

 (c) 2007-2012 The MITRE Corporation. All Rights Reserved.
 */

/*
 * FileUtility.java
 *
 * Created on Oct 1, 2007, 12:35:33 AM
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mitre.opensextant.util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author ubaldino
 */
public class FileUtility {

    /**
     *
     * @param buffer
     * @param fname
     * @return
     * @throws IOException
     */
    public static boolean writeFile(String buffer, String fname) throws IOException {
        return writeFile(buffer, fname, "UTF-8", false);
    }

    /**
     * @param buffer 
     * @param fname 
     * @param enc 
     * @param append 
     * @return
     * @throws IOException  
     */
    public static boolean writeFile(String buffer, String fname, String enc, boolean append)
            throws IOException {
        if (fname == null || enc == null || buffer == null) {
            throw new IOException("Null values cannot be used to write out file.");
        }

        FileOutputStream file = new FileOutputStream(fname, append); // APPEND
        OutputStreamWriter fout = new OutputStreamWriter(file, enc);
        fout.write(buffer, 0, buffer.length());
        fout.flush();
        fout.close();
        return true;
    }

    /** Caller is responsible for write flush, close, etc.
     * @param fname file path
     * @param enc encoding
     * @param append  true = append data to existing file.
     * @return 
     * @throws IOException  
     */
    public static OutputStreamWriter getOutputStream(String fname, String enc, boolean append)
            throws IOException {
        return new OutputStreamWriter(new FileOutputStream(fname, append), enc);
    }

    /** Caller is responsible for write flush, close, etc.
     * @param fname 
     * @param enc
     * @return 
     * @throws IOException  
     */
    public static OutputStreamWriter getOutputStream(String fname, String enc)
            throws IOException {
        return getOutputStream(fname, enc, false);
    }

    /** Getting an input stream from a file.... Is this easier?
     * @param fname
     * @param enc 
     * @return 
     * @throws IOException  
     */
    public static InputStreamReader getInputStream(String fname, String enc)
            throws IOException {
        return new InputStreamReader(new FileInputStream(fname), enc);
    }

    /**
     *
     * @param fname
     * @return
     * @throws IOException
     */
    public static String readFile(String fname)
            throws IOException {
        return readFile(new File(fname), default_encoding);
    }

    /**
     *
     * @param f - File object
     * @return
     * @throws IOException
     */
    public static String readFile(File f)
            throws IOException {
        return readFile(f, default_encoding);
    }
    /**
     *
     */
    public static String default_encoding = "UTF-8";
    /**
     *
     */
    public static int default_buffer = 0x800;

    /**
     * Slurps a text file into a string and returns the string.
     * @param fileinput 
     * @param enc
     * @return
     * @throws IOException  
     */
    public static String readFile(File fileinput, String enc)
            throws IOException {
        if (fileinput == null) {
            return null;
        }

        FileInputStream instream = new FileInputStream(fileinput);
        byte[] inputBytes = new byte[instream.available()];
        instream.read(inputBytes);
        instream.close();
        return new String(inputBytes, enc);
    }

    /** Given a file get the byte array
     * @param fileinput 
     * @return 
     * @throws IOException 
     */
    public static byte[] readBytesFrom(File fileinput)
            throws IOException {
        if (fileinput == null) {
            return null;
        }

        FileInputStream instream = new FileInputStream(fileinput);
        byte[] inputBytes = new byte[instream.available()];
        instream.read(inputBytes);
        instream.close();
        return inputBytes;
    }

    /**
     *
     * @param fname
     * @return
     * @throws IOException
     */
    public static String readGzipFile(String fname) throws IOException {
        if (fname == null) {
            return null;
        }

        FileInputStream instream = new FileInputStream(fname);
        GZIPInputStream gzin =
                new GZIPInputStream(new BufferedInputStream(instream), default_buffer);

        byte[] inputBytes = new byte[default_buffer];
        StringBuilder buf = new StringBuilder();

        int readcount = 0;
        while ((readcount = gzin.read(inputBytes, 0, default_buffer)) != -1) {
            buf.append(new String(inputBytes, 0, readcount, default_encoding));
        }
        instream.close();
        gzin.close();

        return buf.toString();

    }

    /**
     *
     * @param text
     * @param fname
     * @return
     * @throws IOException
     */
    public static boolean writeGzipFile(String text, String fname) throws IOException {
        if (fname == null || text == null) {
            return false;
        }

        FileOutputStream outstream = new FileOutputStream(fname);
        GZIPOutputStream gzout =
                new GZIPOutputStream(new BufferedOutputStream(outstream), default_buffer);

        gzout.write(text.getBytes(default_encoding));

        gzout.flush();
        gzout.finish();

        gzout.close();
        outstream.close();
        return true;

    }

    /** Utility for making dirs
     * @param testDir 
     * @return 
     * @throws IOException 
     */
    public static boolean makeDirectory(File testDir /*, Logger log*/)
            throws IOException {
        if (testDir == null) {
            return false;
        }

        // if (log!=null) { log.info( "Check dir "+testDir.getPath() + " is dir=" + testDir.isDirectory() ); } 

        if (testDir.isDirectory()) {
            return true;
        }

        // if (log!=null) { log.info( "Make dir? Path=" + testDir.getPath() + " status="+testDir.mkdirs()); }
        // If directory does not exist, then create
        return testDir.mkdirs();
    }

    /** Utility for making dirs
     * @param dir 
     * @return
     * @throws IOException  
     */
    public static boolean makeDirectory(String dir)
            throws IOException {
        if (dir == null) {
            return false;
        }

        return makeDirectory(new File(dir));
    }

    /** 
     * @param directory 
     * @return 
     * @author T. Allison, MITRE
     */
    public static boolean removeDirectory(File directory) {
        //taken from http://www.java2s.com/Tutorial/Java/0180__File/Removeadirectoryandallofitscontents.htm


        if (directory == null) {
            return false;
        }
        if (!directory.exists()) {
            return true;
        }
        if (!directory.isDirectory()) {
            return false;
        }

        String[] list = directory.list();

        // Some JVMs return null for File.list() when the
        // directory is empty.
        if (list != null) {
            for (int i = 0; i < list.length; i++) {
                File entry = new File(directory, list[i]);
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

    /** Generate some path with a unique date/time stamp
     * @param D
     * @param F
     * @param Ext 
     * @return  
     */
    public static String generateUniquePath(String D, String F, String Ext) {
        return D + File.separator + generateUniqueFilename(F, Ext);
    }

    /** Generate some filename with a unique date/time stamp
     * @param F 
     * @param Ext
     * @return  
     */
    public static String generateUniqueFilename(String F, String Ext) {

        SimpleDateFormat fileDateFmt = new SimpleDateFormat("_yyyyMMdd,HHmmss,S");

        return F + fileDateFmt.format(new Date()) + Ext;
    }

    /**
     * Get the parent File of a given file. 
     *     defensively create new file in case file is a relative file.
     * That is, this returns a file object for the absolute path of the parent of argument f.
     * 
     * worst case:
     *   getParent( ../ )  ==> "../../",  but resolve that to a real path.
     * 
     * @param f 
     * @return 
     * @author T. Allison
     */
    public static File getParent(File f) {
        return new File(f.getAbsolutePath()).getParentFile();
    }

    /** If you already have a file object, get the basename.
     * @param f 
     * @param ext 
     * @return 
     * @deprecated:  Replaced by Apache Commons IO FilenameUtils.getBasename()
     * 
     */
    // public static String getBasename(File f, String ext) {
    /** Deprecated: LineNumberReader is deprecated.
     * @param filepath 
     * @return 
     * @throws FileNotFoundException
     * @throws IOException  
     * @deprecated  LineNumber reader is deprecated
     */
    public static LineNumberReader getLineReader(String filepath)
            throws FileNotFoundException, IOException {
        return new LineNumberReader(
                new StringReader(FileUtility.readFile(filepath)));
    }

    /** Simple filter
     * @param ext
     * @return  
     */
    public static FilenameFilter getFilenameFilter(String ext) {
        return new AnyFilenameFilter(ext);
    }

    /** get the base name of a file, given any file extension.
     *  This will find the right-most instance of a file extension and return
     *  the left hand side of that as the file basename.
     * 
     * commons io FilenameUtils says nothing about arbitrarily long file extensions, 
     *   e.g., file.a.b.c.txt
     *         => "file"  + "a.b.c.txt"
     * 
     * @param p 
     * @param ext 
     * @return 
     */
    public static String getBasename(String p, String ext) {
        if (p == null) {
            return null;
        }
        String fn = FilenameUtils.getBaseName(p);
        if (ext == null || ext.isEmpty()) {
            return fn;
        }
        if (fn.toLowerCase().endsWith(ext)){
            int lastidx = fn.length() - ext.length() - 1;
            return fn.substring(0, lastidx);            
        }
        return fn;
    }

    /**
     * @param fname
     * @return  
     */
    public static String filenameCleaner(String fname) {

        if (fname == null) {
            return null;
        }
        if (fname.length() == 0) {
            return null;
        }

        char[] text = fname.toCharArray();
        StringBuilder cleaned_text = new StringBuilder();

        for (char c : text) {
            cleaned_text.append(normalizeFilenameChar(c));
        }

        return cleaned_text.toString();
    }

    /**
     * Get a directory that does not conflict with an existing directory.
     * Returns null if that is not possible within the maxDups.
     * 
     * @param dir 
     * @param dupeMarker 
     * @param maxDups 
     * @return 
     * @author T. Allison
     * NOT THREAD SAFE!
     */
    public static File getSafeDir(File dir, String dupeMarker, int maxDups) {

        if (!dir.exists()) {
            return dir;
        }
        String base = dir.getName();
        for (int i = 1; i < maxDups; i++) {
            File tmp = new File(dir.getParentFile(), base + dupeMarker + i);
            if (!tmp.isDirectory()) {
                return tmp;
            }
        }
        return null;
    }

    /**
     * @author T. Allison
     * @param f
     * @param dupeMarker
     * @param maxDups 
     * @return 
     */
    public static File getSafeFile(File f, String dupeMarker, int maxDups) {
        if (!f.exists()) {
            return f;
        }

        int suffixInd = f.getName().lastIndexOf(".");
        String base = f.getName().substring(0, suffixInd);
        String suffix = (suffixInd + 1 <= f.getName().length()) ? f.getName().substring(suffixInd + 1) : "";
        for (int i = 1; i < maxDups; i++) {
            File tmp = new File(f.getParentFile(), base + dupeMarker + i + "." + suffix);
            if (!tmp.exists()) {
                return tmp;
            }
        }
        return null;
    }
    /**
     *
     */
    public static char FILENAME_REPLACE_CHAR = '_';

    // Tests for valid filename chars for simple normalization
    // A-Z, a-z, _-, 0-9,
    /**
     *
     * @param c
     * @return
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

    /** A way of determining OS
     * @return 
     */
    public static boolean isWindowsSystem() {
        String val = System.getProperty("os.name");

        /**
         if (val == null) {
         //log.warn("Could not verify OS name");
         return false;
         } else {
         //log.debug("Operating System is " + val);
         }*/
        return (val != null ? val.contains("Windows") : false);
    }
    /**
     *
     */
    public final static String COMMENT_CHAR = "#";

    /**  A generic word list loader.  Part of the Meso Utility API
     * @param resourcepath 
     * @param case_sensitive 
     * @author ubaldino, MITRE Corp
     * @return Set containing unique words found in resourcepath
     * @throws IOException  
     */
    public static Set<String> loadDictionary(String resourcepath, boolean case_sensitive)
            throws IOException {

        InputStream io = FileUtility.class.getResourceAsStream(resourcepath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(io, default_encoding));

        Set<String> dict = new HashSet<String>();
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
