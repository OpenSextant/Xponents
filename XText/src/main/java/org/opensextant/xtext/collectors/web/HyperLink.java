/**
 *
 * Copyright 2013-2014 OpenSextant.org
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
package org.opensextant.xtext.collectors.web;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import static org.apache.commons.lang3.StringUtils.isBlank;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

// TODO: Auto-generated Javadoc
/**
 * A representation of a harvested hyperlink. Normalization of found URL attempts to derive:
 * <ul>
 * <li>is item a file or dynamic, generated HTML?</li>
 * <li>is item a folder or a page?</li>
 * <li>what is the relation between this page and its containing folder and hosting site? Is this link
 * resident hosted on the originally crawled site?</li>
 * <li>What is the proper file extension for a found link? A link itself does not always reflect the MIME Type and file
 * "save-as" filename...
 * </li>
 * 
 * </ul>
 * 
 * @author ubaldino
 *
 */
public class HyperLink {

    /** raw URL string */
    protected String urlValue = null;
    /**
     * the link as found.
     */
    protected String urlNominal = null;

    /** The referrer url. */
    protected URL referrerURL = null;

    /** The absolute url. */
    protected URL absoluteURL = null;

    /** The site url. */
    protected URL siteURL = null;

    /** The is absolute. */
    protected boolean isAbsolute = false;

    /** The params. */
    protected Properties params = new Properties();

    /** The is current page. */
    protected boolean isCurrentPage = false;

    /** The is current site. */
    protected boolean isCurrentSite = false;

    /** The is current host. */
    protected boolean isCurrentHost = false;

    /** The site value. */
    protected String siteValue = null;

    /** The archive file. */
    protected File archiveFile = null;

    /** The path extension. */
    protected String pathExtension = null;

    /** The archive file extension. */
    protected String archiveFileExtension = null;

    /** The mime type. */
    protected String mimeType = null;

    /** The is folder. */
    protected boolean isFolder = false;

    /** The query. */
    protected String query = null;

    /** The directory. */
    protected String directory = null;

    /** The is dynamic. */
    private boolean isDynamic = false;

    /** The link id. */
    private String linkId = null;

    /**
     * a physical path that represents the URL uniquely.
     */
    protected String normalizedPath = null;

    /**
     * URL wrangling, mainly to take a found URL and adapt it so it looks like a file path safe for a file system.
     *
     * @param link
     *            found link
     * @param referringLink
     *            - Normalized, absolute URL string
     * @param site
     *            top level site
     * @throws MalformedURLException
     *             on err
     * @throws NoSuchAlgorithmException
     *             on err
     * @throws UnsupportedEncodingException
     *             on err, when URL contains poorly encoded characters
     */
    public HyperLink(String link, URL referringLink, URL site) throws MalformedURLException,
            NoSuchAlgorithmException, UnsupportedEncodingException {
        urlValue = link;
        urlNominal = link;
        siteURL = site;
        siteValue = site.toString();
        referrerURL = referringLink;
        String url_lc = urlNominal.toLowerCase();
        String site_lc = siteValue.toLowerCase();
        // If referrer, e.g. page containing this link is a folder or file, detect that.
        //   "/a/b/c"  is a folder but ensure referrer is tracked as "/a/b/c/" with trailing slash here.
        //  Otherwise, url is a page.
        String base_lc = referrerURL.toString().toLowerCase();
        boolean isReferrerFolder = false;
        String urlPath = null;

        isAbsolute = (url_lc.startsWith("http:") || url_lc.startsWith("https:"));

        if (!isAbsolute) {
            absoluteURL = new URL(referrerURL, urlValue);
            urlValue = absoluteURL.toString();
        } else {
            absoluteURL = new URL(urlValue);
        }

        // Use this to represent the object identity.
        linkId = TextUtils.text_id(getAbsoluteURL());
        query = absoluteURL.getQuery();

        urlPath = absoluteURL.getPath().toLowerCase();
        pathExtension = FilenameUtils.getExtension(urlPath);
        String referrerExt = FilenameUtils.getExtension(base_lc);

        isFolder = isFolder(url_lc, pathExtension);
        isReferrerFolder = isFolder(referrerURL.getPath(), referrerExt);

        String abs_lc = absoluteURL.toString().toLowerCase();

        String path = absoluteURL.getPath();
        if (isBlank(path)) {
            normalizedPath = "./";
            isFolder = true;
        } else {
            normalizedPath = path;
            if (normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
        }

        // Optional
        boolean derivedPath = deriveFilepathFromQuery();

        if (!derivedPath) {
            String p = FilenameUtils.normalize(normalizedPath);
            if (p == null) {
                throw new MalformedURLException("Unable to parse/normalize path for: "
                        + normalizedPath);
            }
            normalizedPath = p;
        }

        if (isFolder) {
            directory = new File(normalizedPath).getPath();
        } else {
            directory = new File(normalizedPath).getParent();
        }

        if (directory == null) {
            directory = path;
        }

        if (!isFolder) {
            archiveFileExtension = FilenameUtils.getExtension(normalizedPath);
        }

        // If base/referring page is a directory see if it is in same folder
        // as current link
        //
        String dirB = base_lc;
        if (isReferrerFolder && !dirB.endsWith("/")) {
            dirB = dirB + "/";
        } else if (!isReferrerFolder) {
            int b = base_lc.lastIndexOf('/');
            dirB = base_lc.substring(0, b);
        }

        int s = site_lc.lastIndexOf('/');
        String siteDir = site_lc.substring(0, s);

        isCurrentSite = abs_lc.startsWith(siteDir);
        if (isCurrentSite) {
            if (isFolder) {
                isCurrentPage = abs_lc.startsWith(dirB);
            } else {
                int a = abs_lc.lastIndexOf('/');
                String dirA = abs_lc.substring(0, a) + "/";
                isCurrentPage = dirA.startsWith(dirB);
            }
        }
        String linkHost = absoluteURL.getHost();
        String siteHost = siteURL.getHost();
        isCurrentHost = linkHost.equalsIgnoreCase(siteHost);
    }

    /**
     * get the generated link ID
     *
     * @return the id
     */
    public String getId() {
        return linkId;
    }

    private boolean isFolder(String url, String ext) {
        if (url.endsWith(".") || url.endsWith("/")) {
            return true;
        }
        if (isBlank(ext)) {
            return true;
        }
        return false;
    }

    /**
     * Given a URL a.b/path?param=val&param=val....
     * Derive any meaningful filename from param values in the query.
     *
     * @return true, if successful
     */
    private boolean deriveFilepathFromQuery() {
        if (isBlank(query)) {
            return false;
        }

        /*
         * Obscure means for identifying a better file name + extension
         * under which we save this content.
         */
        isDynamic = true;
        parseURL();
        for (Object p : params.keySet()) {
            String val = params.getProperty(p.toString());
            if (val.length() > 8 && isCommonFile(val)) {
                normalizedPath = String.format("%s/%s", normalizedPath,
                        val);
                isDynamic = false;
                isFolder = false;
                return true;
            }
        }

        /* We have a query, but other means of naming the file, so we'll use
         * current path + MD5 file name +'.html'
         * */
        try {
            normalizedPath = String.format("%s/%s.html", normalizedPath,
                    TextUtils.text_id(query));
            isFolder = false;
            return true;
        } catch (Exception ignore) {
            // NOTE: this never happens.
        }

        // And this would also never happen.
        return false;

    }

    /** The default mime. */
    private static MimeTypes defaultMIME = TikaConfig.getDefaultConfig().getMimeRepository();

    /**
     * Set the MIME type of a found link, i.e., once you'ved downloaded the content you then know the ContentType
     * possibly.
     * Which may differ from your perception of the URL path
     * 
     * - reset the file extension,
     * - reset the path
     * - folder vs. file
     * 
     * Set the MIME Type, file type, path, etc... prior to saving content to disk.
     *
     * @param t
     *            the new MIME type
     */
    public void setMIMEType(String t) {
        mimeType = t;
        if (mimeType == null) {
            return;
        }

        try {
            MimeType mt;
            /* Isolate the MIME type without parameters.
             * 
             */
            mt = defaultMIME.forName(t.split(";", 2)[0]);
            if (mt != null) {
                fixPathExtension(mt.getExtension());
            }
        } catch (MimeTypeException ignore) {
            // Hmm.
        }
    }

    /** The mime equivalences. */
    private static HashMap<String, String> mimeEquivalences = new HashMap<>();

    static {
        mimeEquivalences.put("htm", "html");
        mimeEquivalences.put("html", "htm");
        mimeEquivalences.put("jpg", "jpeg");
        mimeEquivalences.put("jpeg", "jpg");
    }

    /**
     * Not comparing any null values.
     * 
     * Consider if b='x' and a='y', are a and b like MIME types.
     * example: .html ?= .htm
     *
     * @param a
     *            a string
     * @param b
     *            a string
     * @return true, if successful
     */
    private static boolean equivalentFileType(String a, String b) {
        if (isBlank(a)) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        String a1 = mimeEquivalences.get(a);
        if (a1 != null) {
            return a1.equals(b);
        }

        String b1 = mimeEquivalences.get(b);
        if (b1 != null) {
            return b1.equals(a);
        }
        return false;
    }

    /**
     * set the path extension, IFF it is significantly different.
     *
     * @param mimeExt
     *            the mime extension
     */
    private void fixPathExtension(String mimeExt) {

        if (isBlank(mimeExt)) {
            return;
        }
        String ext = mimeExt.replace(".", "");
        if (equivalentFileType(archiveFileExtension, ext)) {
            // Do nothing.  new file extension is nothing new.
            return;
        }
        /*
         * Replace the new mime-based file extension
         */
        if (archiveFileExtension == null) {
            archiveFileExtension = ext;
            normalizedPath = String.format("%s.%s", normalizedPath, ext);
            isFolder = false;
        } else {
            int x = normalizedPath.lastIndexOf(archiveFileExtension);
            String p = normalizedPath.substring(0, x);
            archiveFileExtension = ext;
            normalizedPath = String.format("%s%s", p, ext);
            isFolder = false;
        }
    }

    /**
     * Checks if is folder.
     *
     * @return true, if is folder
     */
    public boolean isFolder() {
        return isFolder;
    }

    /**
     * Get the referrer link used at creation time.
     *
     * @return the referrer
     */
    public String getReferrer() {
        return referrerURL.toString();
    }

    /**
     * Sets the filepath.
     *
     * @param p
     *            the new filepath
     */
    public void setFilepath(File p) {
        archiveFile = p;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        File f = new File(absoluteURL.getPath());
        return f.getName();
    }

    /**
     * Get the relative path of the URL within the site hierarchy if possible.
     *
     * @return the normal path
     */
    public String getNormalPath() {
        return normalizedPath;
    }

    /**
     * tests if URL API detected a path, e.g., non-zero string following
     * host:port/(path)
     *
     * @return true, if successful
     */
    public boolean hasPath() {
        return absoluteURL.getPath().length() > 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return absoluteURL.toString();
    }

    /**
     * trivial test for dynamic content.
     *
     * @return true, if is dynamic
     */
    public boolean isDynamic() {
        // Page is NOT dynamic content as determined by other methods
        if (!isDynamic) {
            return false;
        }

        // Page is Dynamic - yes or no - by look up alone.
        return isDynamic(urlValue, pathExtension);
    }

    /**
     * Checks if is resource.
     *
     * @return true, if is resource
     */
    public boolean isResource() {
        return isResource(urlValue, pathExtension);
    }

    /**
     * list of dynamic pages, e.g., items to avoid.
     */
    private final static Set<String> dynamicPages = new HashSet<String>();

    /** The Constant resourcePages. */
    private final static Set<String> resourcePages = new HashSet<String>();

    static {
        dynamicPages.add("asp");
        dynamicPages.add("aspx");
        dynamicPages.add("jsp");
        dynamicPages.add("cgi");
        dynamicPages.add("php");
        dynamicPages.add("pl");
        dynamicPages.add("dhtml");
        dynamicPages.add("js");
    }

    static {
        resourcePages.add("css");
        resourcePages.add("ico");
    }

    /**
     * Checks if is dynamic.
     *
     * @param url
     *            the url
     * @return true, if is dynamic
     */
    public static boolean isDynamic(String url) {
        if (isBlank(url)) {
            return false;
        }
        String norm = url.toLowerCase();
        String ext = FilenameUtils.getExtension(norm);
        return isDynamic(url, ext);
    }

    /**
     * Checks if is resource.
     *
     * @param url
     *            the url
     * @return true, if is resource
     */
    public static boolean isResource(String url) {
        if (isBlank(url)) {
            return false;
        }
        String norm = url.toLowerCase();
        String ext = FilenameUtils.getExtension(norm);
        return isResource(norm, ext);
    }

    /**
     * Checks if is resource.
     *
     * @param url
     *            -- currently unused.
     * @param ext
     *            lower case.
     * @return true, if is resource
     */
    public static boolean isResource(String url, String ext) {
        return resourcePages.contains(ext);
    }

    /**
     * Checks if is dynamic.
     *
     * @param url
     *            -- currently unused.
     * @param ext
     *            lower case.
     * @return true, if is dynamic
     */
    public static boolean isDynamic(String url, String ext) {
        return dynamicPages.contains(ext);
    }

    /**
     * Checks if is web page.
     *
     * @return true, if is web page
     */
    public boolean isWebPage() {
        if (isDynamic()) {
            return true;
        }
        final String desc = FileUtility.getFileDescription(urlValue);
        if (desc == FileUtility.WEBPAGE_MIMETYPE) {
            return true;
        }
        // Test case: http://a.b.com/my/page
        //  Not query, no file extension.
        // 
        if (urlValue.contains("/") && !urlValue.contains("?") && desc == FileUtility.NOT_AVAILABLE) {
            return true;
        }
        return isDynamic(absoluteURL.getPath());
    }

    /**
     * Checks if is file.
     *
     * @return true, if is file
     */
    public boolean isFile() {
        return isCommonFile(urlValue);
    }

    /**
     * Checks if is common file.
     *
     * @param v
     *            a path
     * @return if path is a common type of file.
     */
    public static boolean isCommonFile(String v) {
        if (FileUtility.getFileDescription(v) == FileUtility.DOC_MIMETYPE
                || FileUtility.isArchiveFile(v)
                || FileUtility.getFileDescription(v) == FileUtility.SPREADSHEET_MIMETYPE
                || FileUtility.getFileDescription(v) == FileUtility.GIS_MIMETYPE) {
            return true;
        }
        // Other conditions?

        return false;

    }

    /**
     * Given this URL, a, found on page, p, determine if a is a local anchor to
     * p itself.
     * 
     * <pre>
     *  /x/y.html     a page.
     *  /x/y.html#tag anchor to y.html
     *  abc.html      link to other page
     * 
     *  http://z.z.z/z.html#tag      Hmmm, this is a page anchor to the absolute page in the URL, z.html.
     * 
     * </pre>
     * 
     * TODO: possibly use the isLocalAnchor() vs. isAnchor() metaphor.
     *
     * @return true, if is page anchor
     */
    public boolean isPageAnchor() {
        String p = absoluteURL.getPath();
        if (isAbsolute()) {
            return p.contains("#");
        }
        if (isBlank(p)|| isBlank(referrerURL.getPath())) {
            return false;
        }
        String file = FileUtility.getBasename(p, "");
        /* 
         * Traditional anchors, but also scripting IDs.
         */
        if (file.startsWith("#") || file.startsWith("%")) {
            return true;
        }

        if (!p.startsWith(referrerURL.getPath())) {
            // Not parent/child relationship here.
            return false;
        }
        return false;
    }

    /**
     * Parses the url.
     */
    protected void parseURL() {
        /**
         * Fails to parse out param around View={xyz} in sharepoint URL
         * List<NameValuePair> params = URLEncodedUtils.parse(new
         * URI(getAbsoluteURL()), "UTF-8"); for (NameValuePair p : params) { if
         * ("RootFolder".equals(p.getName())) { return p.getValue(); } }
         */

        String qry = absoluteURL.getQuery();
        String[] kvlist = qry.split("&");
        for (String param : kvlist) {
            if (isBlank(param)) {
                continue;
            }
            if (!param.contains("=")) {
                params.put(param, ""); // empty value.
                continue;
            }
            String[] kv = param.split("=", 2);
            params.put(kv[0], kv[1]);
        }
    }

    /**
     * Checks if is absolute.
     *
     * @return true, if is absolute
     */
    public boolean isAbsolute() {
        return isAbsolute;
    }

    /**
     * If a URL is fully-qualified protocol + server, then it is not relative.
     *
     * @return true, if is relative
     */
    public boolean isRelative() {
        return !isAbsolute;
    }

    /**
     * Trivial test to see if this link matches the HTML page/URL from which it
     * came. That is, we want to know if the page contains a relative link to
     * itself.
     *
     * @param test
     *            a URL
     * @return true, if is current page
     */
    public boolean isCurrentPage(String test) {
        if (test == null) {
            return false;
        }
        return test.equalsIgnoreCase(urlValue);
    }

    /**
     * Checks if is current host.
     *
     * @return true, if is current host
     */
    public boolean isCurrentHost() {
        return isCurrentHost;
    }

    /**
     * Checks if is current page.
     *
     * @return true, if is current page
     */
    public boolean isCurrentPage() {
        return isCurrentPage;
    }

    /**
     * Checks if is current site.
     *
     * @return true, if is current site
     */
    public boolean isCurrentSite() {
        return isCurrentSite;
    }

    /**
     * Get absolute URL; limitations -- this is not intended for general use It
     * is a mere concatenation of parent + rel path. "../../....." paths are not
     * supported fully.
     *
     * @return the absolute url
     */
    public String getAbsoluteURL() {
        return absoluteURL.toString();
    }

    /**
     * Gets the url.
     *
     * @return URL object for this link. It is an absolute URL.
     */
    public URL getURL() {
        return absoluteURL;
    }

    /**
     * Gets the directory.
     *
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }
}
