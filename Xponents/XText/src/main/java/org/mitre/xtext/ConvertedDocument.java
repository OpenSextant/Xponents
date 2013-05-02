/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xtext;

import java.io.File;
import net.sf.json.JSONObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.mitre.opensextant.util.FileUtility;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public final class ConvertedDocument {

    public static String DEFAULT_EMBED_FOLDER = "xtext";
    private static SimpleDateFormat dtfmt = new SimpleDateFormat("yyyy-MM-dd");
    public final static String[] fields = {
        // Dublin Core style metadata fields
        "title",
        "author",
        "creator_tool",
        "pub_date",
        "keywords",
        "subject",
        "filepath",
        "encoding",
        //
        // -- XText metadata.
        "filtered",
        "converter",
        "conversion_date",
        "encrypted",
        "filesize",
        "textsize"
    };
    public final static Set<String> valid_fields = new HashSet<>(Arrays.asList(fields));
    public String filepath = null;
    public String filename = null;
    public Date filetime = null;
    public String relative_path = null;
    public String textpath = null;
    public String payload = null;
    private String encoding = null;
    JSONObject meta = new JSONObject();
    public static boolean overwrite = true;
    /** Duration in Milliseconds to convert */
    protected int conversion_time = 0;
    public boolean is_plaintext = false;
    public boolean is_converted = false;
    public boolean do_convert = true;
    /** Represents if conversion was actually saved or not
     *   OR if file was retrieved from cache successfully.
     */
    public boolean is_cached = false;
    public static boolean CONVERT_TO_UNIX_EOL = true;
    private File file = null;
    public long filesize = -1;

    public ConvertedDocument() {
        // Used only for uncaching previously saved converted docs.
    }

    public ConvertedDocument(File item) {
        this.filepath = item.getAbsolutePath();
        this.filename = item.getName();
        this.filetime = new Date(item.lastModified());
        addProperty("filepath", this.filepath);
        addProperty("conversion_date", dtfmt.format(new Date()));
        //addProperty("filetime", dtfmt.format(this.filetime));

        is_plaintext = filename.toLowerCase().endsWith(".txt");

        // is okay
        this.file = item;
        this.filesize = file.length();
        addProperty("filesize", this.filesize);
    }

    /** set encoding property
     */
    public void setEncoding(String enc) {
        this.encoding = enc;
        addProperty("encoding", enc);
    }

    /** 
     */
    //private boolean is_output_in_final_encoding() {
    //    return (this.is_plaintext && OUTPUT_ENCODING.equalsIgnoreCase(encoding));
    //}

    /** 
     */
    public void setPayload(String buf) throws UnsupportedEncodingException {
        this.payload = buf;
        //int rawlen = buf.length();

        // Now figure out if we have a converted document or not.
        if (do_convert) {
            if (CONVERT_TO_UNIX_EOL) {
                payload = payload.replace("\r\n", "\n");
            }
            payload = payload.trim();
            is_converted = true;
        } else if (is_plaintext) {
            is_converted = false;
            textpath = this.filepath;
        }

        meta.put("textsize", payload.length());
    }

    /** */
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

    public void addDate(String d) {
        meta.put("pub_date", d);
    }

    public void addDate(java.util.Calendar d) {
        if (d == null) {
            return;
        }
        meta.put("pub_date", dtfmt.format(d.getTime()));
    }

    public void addConverter(Class c) {
        meta.put("converter", c.getName());
    }

    public void addTitle(String a) {
        meta.put("title", a);
    }

    public void addAuthor(String a) {
        meta.put("author", a);
    }

    public void setPathRelativeTo(String container) {
        this.relative_path = getRelativePath(container, this.filepath);
    }

    /**  Given file /a/b/c.txt   find me just the relative part to some root.
     *   That is, for example, if we care more about the b folder regardless of that it is
     * physically located in /a.  Perform:
     *   
     *   getRelativePath( "/a", "/a/b/c.txt")  ===> b/c.txt
     *   
     */
    public static String getRelativePath(String root, String p) {
        String _path = p.replace(root, "");
        if (_path.length() > 0) {
            if (_path.charAt(0) == File.separatorChar) {
                _path = _path.substring(1);
            }
        }
        return _path;
    }
    public final static String OUTPUT_ENCODING = "UTF-8";

    /**   relative_path is original relative to input folder 
     *    TOOD: cleanup
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
            return relpath.substring(0, relpath.length() - 4) + "-utf8.txt";
        }
    }

    /** Save payload to a folder, outputDir;
     * 
     *  {HEADER_META}\n
     *  \n
     *  Buffer
     * 
     * Text File is saved to your desintation output folder
     * Files that are UTF-8 already will not be saved, copied or moved.
     */
    public void save(String outputDir) throws IOException {

        if (is_converted) {
            File target = new File(outputDir + File.separator + getNewPath(this.relative_path));
            this._saveConversion(target);
        }
    }

    /** Similar to save(), but forces the output folder to be ./xtext/
     *  in the same folder as the input archive.
     *     a/b/c/file.xxx  to 
     *     a/b/c/xtext/file.xxx.txt
     */
    public void saveEmbedded() throws IOException {
        if (is_converted) {
            String container = new File(this.filepath).getParent();
            File target = new File(container + File.separator + DEFAULT_EMBED_FOLDER + File.separator + getNewPath(this.filename));
            this._saveConversion(target);
        }
    }

    /** This provides some means for retrieving previously converted files.
     * .... to avoid converted them.
     * @return doc ConvertedDocument from cache, otherwise null
     */
    public static ConvertedDocument getEmbeddedConversion(File obj) throws IOException {
        String container = obj.getParent();

        // Make path -- TODO: generalize this path builder stuff.
        StringBuilder path = new StringBuilder();
        path.append(container);
        path.append(File.separator);
        path.append(DEFAULT_EMBED_FOLDER);
        path.append(File.separator);

        // I now have a path name that was likely the one stored in cache.
        // Return the ConvertedDocument if exists at this path.
        // Otherwise it is not in cache.... so converter must convert and save.
        //
        // This instance finds file:./xtext/F.ext.txt  for a file:./F.ext
        //
        return _uncacheConversion(path, obj.getName());
    }

    private static ConvertedDocument _uncacheConversion(StringBuilder path, String fname) throws IOException {
        // Common 
        if (fname.endsWith(".txt")) {
            String cachedFile = FilenameUtils.getBaseName(fname);
            path.append(cachedFile);
            path.append("-utf8.txt");
        } else {
            path.append(fname);
            path.append(".txt");
        }
        File target = new File(path.toString());
        if (target.exists()) {
            return ConvertedDocument.getCachedDocument(target);
        }
        return null;
    }

    /** This provides some means for retrieving previously converted files.
     * .... to avoid converted them.
     */
    public static ConvertedDocument getCachedConversion(String cacheDir, File inputDir, File obj) throws IOException {
        //String cachedFile = FilenameUtils.getBaseName(obj.getName());

        String rel_path = getRelativePath(inputDir.getAbsolutePath(), obj.getParentFile().getAbsolutePath());

        // Make path -- TODO: generalize this path builder stuff.
        StringBuilder path = new StringBuilder();
        path.append(cacheDir);
        path.append(File.separator);
        //path.append(inputDir.getName());
        //path.append(File.separator);
        if (rel_path.length() > 0) {
            path.append(rel_path);
            path.append(File.separator);
        }


        // I now have a path name that was likely the one stored in cache.
        // Return the ConvertedDocument if exists at this path.
        // Otherwise it is not in cache.... so converter must convert and save.
        //
        // This instance finds file:/<output-path>/<input-dir-name>/<relative-path-to-file>.txt
        //                 (shorter: /O/D/relpath/file.ext.txt )
        // 
        //         for     binary /inputpath/D/relpath/file.ext
        //
        //   you gave me:  C:\data\source\
        //   you said output goes to 
        //                 D:\archives\
        // 
        //   I found file   C:\data\source\something\file.doc
        // 
        //   Which is to be cached at:
        //                 D:\archives\source\something\file.doc.txt
        //                 ^^^^^^^^^^^|inputdir|relpath^^^^^^^^^^^^^^
        //                 outputdir  |        |
        // 
        //   IFFF a conversion happened.
        //   If no conversion was made, then the original file is either
        //   unconvertable or it is already valid UTF-8 or ASCII-only text/plain.
        //
        return _uncacheConversion(path, obj.getName());
    }

    /** Internal function for saving payload in the XText format.
     */
    protected void _saveConversion(File target) throws IOException {

        if (!ConvertedDocument.overwrite) {
            if (target.exists()) {
                textpath = target.getAbsolutePath();
                // throw new IOException("Not overwriting");
                return;
            }
        }

        FileUtility.makeDirectory(target.getParentFile());
        StringBuilder buf = new StringBuilder();

        // META data cannot be empty.
        // if (meta.isEmpty()) {
        //    buf.append("{}");
        // SAVE conversions with a minimal Base64-encoded header
        // which when decoded is a JSON structure of metadata properties.
        buf.append(payload);
        buf.append("\n\n");
        buf.append(XT_LABEL);
        buf.append(Base64.encodeBase64String(meta.toString().getBytes()));
        buf.append("\n");
        FileUtility.writeFile(buf.toString(), target.getAbsolutePath(), OUTPUT_ENCODING, false);

        textpath = target.getAbsolutePath();
        this.is_cached = true;
    }
    public static String XT_LABEL = "XT:";

    /** Given a path, retrieve a document. */
    public static ConvertedDocument getCachedDocument(String filepath) throws IOException {
        return getCachedDocument(new File(filepath));
    }

    /** 
     * Given a path, retrieve a document parsing out the XText format.
     */
    public static ConvertedDocument getCachedDocument(File fconv) throws IOException {
        String buf = FileUtility.readFile(fconv);
        int x = buf.lastIndexOf("\n\n");

        // Get Base64 encoded header
        String header = buf.substring(x).trim();
        if (!header.startsWith(XT_LABEL)) {
            // NOT an XText cache
            return null;
        }
        ConvertedDocument doc = new ConvertedDocument();
        // Decode JSON
        String json = new String(Base64.decodeBase64(header.substring(XT_LABEL.length())));
        doc.meta = JSONObject.fromObject(json);

        // Set plain text payload
        doc.payload = buf.substring(0, x);

        // Retrieve values for useful attrs.
        doc.encoding = doc.getProperty("encoding");
        doc.filepath = doc.getProperty("filepath");
        doc.filesize = Long.parseLong(doc.getProperty("filesize"));
        doc.textpath = fconv.getAbsolutePath();
        doc.is_cached = true;

        return doc;

    }
}
