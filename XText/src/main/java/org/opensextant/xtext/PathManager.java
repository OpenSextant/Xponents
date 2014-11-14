package org.opensextant.xtext;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.opensextant.ConfigException;
import org.opensextant.util.FileUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PathManager -- a group of routines related to caching conversions and archive collections.  
 * It manages the path decisions given a variety of output parameters and the input object.
 * 
 * @author ubaldino
 *
 */
public class PathManager {

    private Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The folder where conversions are saved.
     */
    private String conversionCache = null;

    /**
     * a Prefix path caller wishes to remove from input files and archives.  This helps shorten paths in the cache.
     */
    private String stripPrefixPath = null;

    /**
     * inputRootName = the name of the input in the output cache and export cache.
     */
    private String inputRootName = null;

    /**
     * Embedded mode
     */
    private boolean saveConversionsWithOriginals = false;

    /**
     * saveExtractedChildrenWithOriginals  - determines how embedded items are archived, e.g., Email attachments, or embedded images.
     * They are children to some parent container -- XText yields two things:  the original child, and the conversion of the child. 
     * 
     * Example:  a.doc (child) saved from A.eml (parent)
     * 
     * saveExtractedChildrenWithOriginals = True;    a is saved in same folder where A exists  
     * saveExtractedChildrenWithOriginals = False;   a is saved in a separate output archive. 
     */
    private boolean saveExtractedChildrenWithOriginals = false;

    private boolean saving = false;

    /**
     * The overall flag to save converted output or not. DEFAULT: true = save
     * it; provided caller specifies either saveWithInput or provides an
     * archiveRoot
     * 
     * @param b
     */
    public void enableSaving(boolean b) {
        saving = b;
    }
    public boolean isSaving(){
        return saving;
    }

    public String getConversionCache() {
        return conversionCache;
    }

    /**
     * Set the prefix that will be removed from the leading part of paths as conversions are cached.
     * Have a long, long file path?  And want to shorten it in your cache... choose this prefix after thinking about it.
     * If you strip too much you may end up with name conflicts or not enough organization to the cached stuff.
     * 
     * NOTE: an error/warning is printed only if the prefix does not exist.  This is not an exception or error, as you 
     * might get the paths to items from some other method and they may not actually exist physically on disk.
     * 
     * @param p a prefix path that would be found in the absolute path of documents being converted.
     */
    public void setStripPrefixPath(String p) {
        stripPrefixPath = p;
        if (p != null) {
            if (!new File(p).exists()) {
                log.error("Warning prefix Path does not exist: {}", p);
            }
        }
    }

    public String getStipPrefixPath() {
        return stripPrefixPath;
    }

    public boolean hasInputRoot() {
        return (inputRootName != null);
    }

    /**
     * From the provided caching parameters set ahead of time, infer the location 
     * where this input should be located within the archive, relatively.  This 
     * should only be set once at the top level, that is 
     * <ul><li>if the call is to convert a single file, set it once for the file.
     * <li>if the call is to convert a folder, set it once.
     * <li>... for archives, etc. set it once!
     * </ul>
     * Do not set it for each file when traversing a folder contents.
     * <br>
     * NOTA BENE: Set conversion cache location first.
     * 
     * <pre>
     *   cache =  /output/converted/
     *   
     *   input =  /my/original/abc.zip  ==> /output/converted/abc_zip
     *   input =  /my/original/abc.doc  ==> /output/converted/abc.doc.txt
     *   input =  /my/original/abc/     ==> /output/converted/abc/
     *   
     *   
     *   prefix set, as prefix=/my
     *   
     *   input =  /my/original/abc.zip  ==> /output/converted/original/abc_zip
     *   input =  /my/original/abc.doc  ==> /output/converted/original/abc.doc.txt
     *   input =  /my/original/abc/     ==> /output/converted/original/abc/
     *   
     *  if saved-in-input, none of this applies. 
     * </pre> 
     * 
     * If you are caching conversions in an archive folder, A, then 
     * this generally sets your ouputNode to /A/name/
     * 
     * An items saved here will be of the form /A/name/relative/path
     * For an input that came from /some/input/name/relative/path
          * @throws IOException 
     */
    public void setInputRoot(File input) throws IOException {
        if (!saving) {
            return;
        }

        // Reset globals.
        // 
        inputRootName = (input.isDirectory() ? input.getName() : input.getParentFile().getName());

        outputNode = null;

        if (conversionCache != null) {

            // DEFAULT: for files and when not using strip path, the cache folder is literally used.
            outputNode = conversionCache;

            if (stripPrefixPath != null) {
                File testDir = input.isDirectory() ? input : input.getParentFile();
                outputNode = createPath(conversionCache, this.getStrippedInputPath(testDir));
            } else if (input.isDirectory()) {
                outputNode = createPath(conversionCache, inputRootName);
            }
        }
    }

    /**
     * This enables saving in an archive and disables saving with input.
     *
     * @param root
     * @throws IOException
     * @see #enableSaveInCache(boolean)
     * @see #enableSaveWithInput(boolean)
     */
    public void setConversionCache(String root) throws IOException {
        if (root == null) {
            throw new IOException("Archive cannot be null");
        }

        // User tried setting a non-null archive... so implicitly they are not saving with input
        //
        this.enableSaveInCache(true);
        conversionCache = fixPath(root);

        File test = new File(conversionCache);

        if (!test.exists() || !test.isDirectory()) {
            throw new IOException("Archive root directory must exist");
        }

        conversionCache = test.getAbsolutePath();
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
     * @see #setConversionCache(java.lang.String)
     */
    public void enableSaveWithInput(boolean b) {
        saveConversionsWithOriginals = b;
    }

    public boolean isSaveWithInput() {
        return saveConversionsWithOriginals;
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
        saveExtractedChildrenWithOriginals = b;
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
     * @see #setConversionCache(java.lang.String)
     */
    public void enableSaveInCache(boolean b) {
        saveConversionsWithOriginals = !b;
    }

    private String outputNode;

    /**
     * Caller is responsible for checking null.
     * 
     * @param path
     */
    public static String trimLeadingSlash(String path) {
        if (path.length() == 0) {
            return path;
        }
        if (path.charAt(0) == '/') {
            return path.substring(1);
        }
        return path;
    }

    /**
     * Prepares a relative path, stripped of the prefix if one is provided.
     * Otherwise, the input path is returned less a leading slash.
     * @param obj
     * @return stripped path
     */
    public String getStrippedInputPath(File obj) {
        String root = obj.getAbsolutePath();
        if (stripPrefixPath != null && root.startsWith(stripPrefixPath)) {
            root = root.substring(stripPrefixPath.length());
        }
        root = trimLeadingSlash(root);
        return root;
    }

    /**
     * Most of the path mechanics are string-based, rather than file-system based,
     * so path adjustments are best done to be sure all paths from configuration
     * or from inputs should conform to a common convention.  paths will be more like URLs, using
     * "/" as the standard path separator.
     *
     * TODO: commons-io FilenameUtils.normalize()  does not work quite right across platforms. Review, Retest.
     * 
     * @param p
     * @return  fixed path
     */
    protected static String fixPath(String p) {
        if (p == null) {
            return null;
        }
        String relPath = p.replace('\\', '/').replace("/./", "/");

        return relPath.startsWith("./") ? relPath.substring(2) : relPath;
    }

    /**
     * NOTE: Use of File() or FilenameUtils.concat() are OS dependent, here
     * what we want is more like a URL string representation always using /a/b/c/
     * Instead of potentially \ and/or / mixed.
     * @param dir
     * @param item
     * @return  path
     * @throws IOException
     */
    protected static String createPath(String dir, String item) throws IOException {
        File f = new File( String.format("%s/%s", dir, item) );
        return fixPath(f.getAbsolutePath());
    }

    private String extractedChildrenCache = null;

    public void setExtractedChildrenCache(String folder) {
        extractedChildrenCache = folder;
    }

    public String getExtractedChildrenCache() {
        return extractedChildrenCache;
    }

    /**
     * Run by XText.setup() to verify path issues.
     * 
     * @throws IOException
     */
    public void configure() throws IOException {
        if (saving && !this.saveConversionsWithOriginals && this.conversionCache == null) {
            throw new IOException(
                    "If not saving conversions with your input folders, you must provide an archive path");
        }

        if (extractedChildrenCache != null) {
            if (!new File(extractedChildrenCache).exists()) {
                throw new IOException(
                        "If saving child items from archives or PST files, you must create the parent folder first. Dir does not exist:"
                                + extractedChildrenCache);
            }
        }
    }

    /**
     * Wrapper around logic to save a conversion.  Save with input or save in other output folder.
     * 
     * @param textDoc
     * @throws IOException
     */
    public void saveConversion(ConvertedDocument textDoc) throws IOException {

        log.debug("FILE={}, cache-in={}", textDoc.getFile(), outputNode);

        if (this.saveConversionsWithOriginals) {
            // Saves close to original in ./text/ folder where
            // original resides.
            textDoc.saveEmbedded();
        } else {
            String searchPath = String.format("/%s/", inputRootName);
            textDoc.setPathRelativeTo(searchPath, this.saveExtractedChildrenWithOriginals);
            textDoc.save(outputNode);
        }
    }

    public ConvertedDocument getCachedConversion(File input) throws IOException {
        if (this.saveConversionsWithOriginals) {
            // Uncache a file close to the original F <== ./xtext/F.txt
            return getEmbeddedConversion(input);
        } else if (this.inputRootName != null) {
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
            return getCachedConversion(this.outputNode, this.inputRootName, input);
        }

        // Either no cache set or item was not converted.
        // Item may have not been converted due to error or simply it was already plain text.
        return null;
    }

    public File getArchiveExportDir(File input) throws ConfigException, IOException {

        String aName = FilenameUtils.getBaseName(input.getName());
        String aExt = FilenameUtils.getExtension(input.getName());
        String outputName = String.format("%s_%s", aName, aExt.toLowerCase());

        // Set output name to input name.  That is, once we extract A.zip to ./(originals)/A_zip/   this de-archived folder will 
        // Also exist in ./(converted)/A_zip/  or ./(originals)/A_zip/xtext/ embedded.
        //
        //setOutputNode(inputNode);

        String saveTo = null;
        // unpack, traverse, convert, save
        if (extractedChildrenCache != null) {
            // Save converted items in a parallel archive for this zip archive.
            saveTo = PathManager.createPath(extractedChildrenCache, outputName);
        } else if (this.saveExtractedChildrenWithOriginals) {
            saveTo = PathManager.createPath(input.getParentFile().getAbsolutePath(), outputName);
        } else {
            throw new ConfigException(
                    "Archive Files cannot be dearchived without a target folder to store child binaries");
        }

        File saveFolder = new File(saveTo);
        if (!saveFolder.exists()) {
            FileUtility.makeDirectory(saveFolder);
        }

        log.debug("ARCHIVE FILE={}, node-in={}, cache-in={}, export={}", input, outputName,
                outputNode, saveFolder);

        return saveFolder;
    }

    public boolean verifyArchiveExport(String input) {
        if (!this.saveConversionsWithOriginals && !this.saveExtractedChildrenWithOriginals
                && this.conversionCache == null) {
            log.error(
                    "Sorry -- if not saving in input folder, you must provide a separate "
                            + "archive to contain ZIP and other archives that are extracted.  Ignoring FILE={}",
                    input);
            return false;
        }

        return true;

    }

    public static String DEFAULT_EMBED_FOLDER = "xtext";

    /**
     * This provides some means for retrieving previously converted files. ....
     * to avoid converted them.
     *
     * @return doc ConvertedDocument from cache, otherwise null
     */
    public static ConvertedDocument getEmbeddedConversion(File obj) throws IOException {

        String cacheFolder = makePath(PathManager.fixPath(obj.getParent()), DEFAULT_EMBED_FOLDER);

        // I now have a path name that was likely the one stored in cache.
        // Return the ConvertedDocument if exists at this path.
        // Otherwise it is not in cache.... so converter must convert and save.
        //
        // This instance finds file:./xtext/F.ext.txt  for a file:./F.ext
        //
        return _uncacheConversion(cacheFolder, obj.getName());
    }

    /**
     * Given file /a/b/c.txt find me just the relative part to some root. That
     * is, for example, if we care more about the b folder regardless of that it
     * is physically located in /a. Perform:<pre>
     *
     * getRelativePath( "/a", "/a/b/c.txt") ===&gt; b/c.txt</pre>
     * @param root prefix path
     * @param p full path to an item.
     * @return relative path wrt root
     */
    public static String getRelativePath(String root, String p) {
        String _path = PathManager.fixPath(p);
        int x = _path.indexOf(root); // Possibly a relative root.
        if (x < 0) {
            return p; // "root" not found in p. No relation between root and path given.
        }
        return trimLeadingSlash(_path.substring(x));
    }

    /**
     * Pass in a folder.  and the name of the object to uncache.
     *
     * @param path  containing folder
     * @param fname  original file name sought
     * @return previously converted document or null if not found.
     * @throws IOException on error, likely from getCachedDocument
     */
    private static ConvertedDocument _uncacheConversion(String path, String fname)
            throws IOException {
        // Common
        String targetPath = null;
        if (fname.endsWith(".txt")) {
            String cachedFile = FilenameUtils.getBaseName(fname);
            targetPath = String.format("%s/%s-utf8.txt", path, cachedFile);
        } else {
            targetPath = String.format("%s/%s.txt", path, fname);
        }
        File target = new File(targetPath);
        if (target.exists()) {
            return getCachedDocument(target);
        }
        return null;
    }

    /**
     * This provides some means for retrieving previously converted files. ....
     * to avoid converted them.  This method takes the arguments and tries to infer the 
     * actual location of a cached item.
     * TODO:  For compound documents this needs more work.
     * 
     * @param cacheDir  shadow dir or separate archive path
     * @param inputDir  original input folder where this item came from
     * @param obj  the requested file.
     * @return the cached version of the conversion; null if not found or if no conversion was made.
     * @throws IOException
     */
    public static ConvertedDocument getCachedConversion(String cacheDir, String inputDir, File obj)
            throws IOException {
        String rel_path = getRelativePath(inputDir, obj.getParentFile().getAbsolutePath());

        // This folder contains the cached Item.
        String cacheFolder = makePath(cacheDir, rel_path);

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
        return _uncacheConversion(cacheFolder, obj.getName());
    }

    /**
     * Apache Commons file utils "concat(dir, file)" makes a mess of file names.  
     * Java can support "/" equally well on all platforms.  
     * there is no apparent need to use platform specific file separators in this context.
     * @param dir
     * @param fname
     * @return full path.
     */
    protected static String makePath(File dir, String fname) {
        return makePath(dir.getAbsolutePath(), fname);
    }

    /**
     * Apache Commons file utils "concat(dir, file)" makes a mess of file names.  
     * Java can support "/" equally well on all platforms.  
     * there is no apparent need to use platform specific file separators in this context.
     * @param dir
     * @param fname
     * @return full path.
     */
    protected static String makePath(String dir, String fname) {
        return String.format("%s%s%s", dir, ConvertedDocument.UNIVERSAL_PATH_SEP, fname);
    }

    public static String getEmbeddedPath(String container, String item) {
        StringBuilder path = new StringBuilder();
        path.append(container);
        path.append(ConvertedDocument.UNIVERSAL_PATH_SEP);
        path.append(DEFAULT_EMBED_FOLDER);
        path.append(ConvertedDocument.UNIVERSAL_PATH_SEP);
        path.append(item);

        return path.toString();

    }

    public final static String DEFAULT_EMBED_FOLDER_IN_PATH = String.format("/%s/",
            DEFAULT_EMBED_FOLDER);
    public final static String DEFAULT_EMBED_FOLDER_IN_WINPATH = String.format("\\%s\\",
            DEFAULT_EMBED_FOLDER);

    /**
     * Simple test to see if filepath contains "./xtext/" for windows path or unix path.
     * @param filepath
     * @return true if file parent is "/xtext/" or "\xtext\, case sensitive is found anywhere in path.
     */
    public final static boolean isXTextCache(String filepath) {
        if (filepath.contains(DEFAULT_EMBED_FOLDER_IN_PATH)) {
            return true;
        }
        // Less often used:
        if (filepath.contains(DEFAULT_EMBED_FOLDER_IN_WINPATH)) {
            return true;
        }
        return false;
    }

    /**
     * If a File is provided, this only checks the immediate parent folder.
     *  
     * @param obj
     * @return  true if file parent is "xtext", case sensitive.
     */
    public final static boolean isXTextCache(File obj) {
        return DEFAULT_EMBED_FOLDER.equals(obj.getParentFile().getName());
    }

    /**
     * Given a path, retrieve a document.
     */
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
        if (!header.startsWith(ConvertedDocument.XT_LABEL)) {
            // NOT an XText cache
            return null;
        }
        // Decode JSON
        String json = new String(Base64.decodeBase64(header.substring(ConvertedDocument.XT_LABEL
                .length())));
        JSONObject doc_meta = JSONObject.fromObject(json);
        String fpath = doc_meta.getString("filepath");

        ConvertedDocument doc = new ConvertedDocument(new File(fpath));
        doc.meta = doc_meta;

        // Set plain text buffer
        doc.buffer = buf.substring(0, x);

        // Retrieve values for useful attrs.
        doc.encoding = doc.getProperty("encoding");
        doc.filepath = fpath; /* note: path should already have been normalized, using "/" */
        doc.filesize = Long.parseLong(doc.getProperty("filesize"));
        doc.textpath = fconv.getAbsolutePath();
        doc.is_cached = true;
        doc.is_converted = true;

        doc.filetime = new Date(Long.parseLong(doc.getProperty("filetime")));
        doc.setCreateDate();        

        // DocInput requirement: provided id + file paths
        // If there is another Identifier to use,... caller will have an opportunity to set it
        // when the get the instance.
        //
        String idvalue = doc.meta.optString("xtext_id");
        doc.setId(idvalue != null ? idvalue : doc.filepath);

        return doc;
    }

}
