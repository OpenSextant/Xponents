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
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

/**
 * A representation of a harvested hyperlink.  Normalization of found URL attempts to derive:
 * <ul>
 * <li>is item a file or dynamic, generated HTML?</li>
 * <li>is item a folder or a page?</li>
 * <li>what is the relation between this page and its containing folder and hosting site?  Is this link
 * resident hosted on the originally crawled site?</li>
 * <li>What is the proper file extension for a found link?  A link itself does not always reflect the MIME Type and file "save-as" filename...
 * </li>
 * 
 * </ul>
 * @author ubaldino
 *
 */
public class HyperLink {

    protected String urlValue = null;
    /**
     * the link as found.
     */
    protected String urlNominal = null;
    protected URL referrerURL = null;
    protected URL absoluteURL = null;
    protected URL siteURL = null;
    protected boolean isAbsolute = false;
    protected Properties params = new Properties();
    protected boolean isCurrentPage = false;
    protected boolean isCurrentSite = false;
    protected boolean isCurrentHost = false;
    protected String siteValue = null;
    protected File archiveFile = null;
    protected String pathExtension = null;
    protected String archiveFileExtension = null;
    protected String mimeType = null;
    protected boolean isFolder = false;
    protected String query = null;
    protected String directory = null;
    private boolean isDynamic = false;
    private String linkId = null;

    /**
     * a physical path that represents the URL uniquely.
     */
    protected String normalizedPath = null;

    /**
     * URL wrangling, mainly to take a found URL and adapt it so it looks like a file path safe for a file system.
     * 
     * @param link
     * @param referringLink  - Normalized, absolute URL string
     * @param site
     * @throws MalformedURLException
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
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
        String base_lc = referrerURL.toString().toLowerCase();
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

        if (url_lc.endsWith(".") || StringUtils.isEmpty(pathExtension) || url_lc.endsWith("/")) {
            isFolder = true;
        }

        String abs_lc = absoluteURL.toString().toLowerCase();

        String path = absoluteURL.getPath();
        if (StringUtils.isBlank(path)) {
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
        int b = base_lc.lastIndexOf('/');
        String dirB = base_lc.substring(0, b);

        int s = site_lc.lastIndexOf('/');
        String siteDir = site_lc.substring(0, s);

        isCurrentSite = abs_lc.startsWith(siteDir);
        if (isCurrentSite) {
            if (isFolder) {
                isCurrentPage = abs_lc.startsWith(dirB);
            } else {
                int a = abs_lc.lastIndexOf('/');
                String dirA = abs_lc.substring(0, a);
                isCurrentPage = dirA.startsWith(dirB);
            }
        }
        String linkHost = absoluteURL.getHost();
        String siteHost = siteURL.getHost();
        isCurrentHost = linkHost.equalsIgnoreCase(siteHost);
    }

    public String getId() {
        return linkId;
    }

    /**
     * Given a URL  a.b/path?param=val&param=val....
     * Derive any meaningful filename from param values in the query.
     * 
     */
    private boolean deriveFilepathFromQuery() {
        if (StringUtils.isBlank(query)) {
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

    private static MimeTypes defaultMIME = TikaConfig.getDefaultConfig().getMimeRepository();

    /**
     * Set the MIME type of a found link, i.e., once you'ved downloaded the content you then know the ContentType possibly.
     * Which may differ from your perception of the URL path
     * 
     * - reset the file extension,
     * - reset the path
     * - folder vs. file
     * 
     * Set the MIME Type, file type, path, etc... prior to saving content to disk.
     * 
     * @param t
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
     * @param b
     * @return
     */
    private static boolean equivalentFileType(String a, String b) {
        if (StringUtils.isBlank(a)) {
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
     * set the path extension, IFF it is significantly different
     * @param ext
     */
    private void fixPathExtension(String mimeExt) {

        if (StringUtils.isBlank(mimeExt)) {
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

    public boolean isFolder() {
        return isFolder;
    }

    /**Get the referrer link used at creation time.
     *
     * @return the referrer
     */
    public String getReferrer() {
        return referrerURL.toString();
    }

    public void setFilepath(File p) {
        archiveFile = p;
    }

    public String getName() {
        File f = new File(absoluteURL.getPath());
        return f.getName();
    }

    /**
     * Get the relative path of the URL within the site hierarchy if possible.
     *
     * @return
     */
    public String getNormalPath() {
        return normalizedPath;
    }

    /**
     * tests if URL API detected a path, e.g., non-zero string following
     * host:port/(path)
     *
     * @return
     */
    public boolean hasPath() {
        return absoluteURL.getPath().length() > 0;
    }

    @Override
    public String toString() {
        return absoluteURL.toString();
    }

    /**
     * trivial test for dynamic content.
     *
     * @return
     */
    public boolean isDynamic() {
        // Page is NOT dynamic content as determined by other methods
        if (!isDynamic) {
            return false;
        }

        // Page is Dynamic - yes or no - by look up alone.
        return isDynamic(urlValue, pathExtension);
    }

    public boolean isResource() {
        return isResource(urlValue, pathExtension);
    }

    /**
     * list of dynamic pages, e.g., items to avoid.
     */
    private final static Set<String> dynamicPages = new HashSet<String>();
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

    public static boolean isDynamic(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String norm = url.toLowerCase();
        String ext = FilenameUtils.getExtension(norm);
        return isDynamic(url, ext);
    }

    public static boolean isResource(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String norm = url.toLowerCase();
        String ext = FilenameUtils.getExtension(norm);
        return isResource(norm, ext);
    }

    /**
     *
     * @param url  -- currently unused.
     * @param ext lower case.
     * @return
     */
    public static boolean isResource(String url, String ext) {
        return resourcePages.contains(ext);
    }

    /**
     *
     * @param url  -- currently unused.
     * @param ext lower case.
     * @return
     */
    public static boolean isDynamic(String url, String ext) {
        return dynamicPages.contains(ext);
    }

    public boolean isWebPage() {
        if (isDynamic()) {
            return true;
        }
        if (FileUtility.getFileDescription(urlValue) == FileUtility.WEBPAGE_MIMETYPE) {
            return true;
        }
        return isDynamic(absoluteURL.getPath());
    }

    public boolean isFile() {
        return isCommonFile(urlValue);
    }

    /**
     * 
     * @param v a path
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
     * @return
     */
    public boolean isPageAnchor() {
        if (isAbsolute()) {
            return absoluteURL.getPath().contains("#");
        }
        if (StringUtils.isBlank(absoluteURL.getPath())
                || StringUtils.isBlank(referrerURL.getPath())) {
            return false;
        }
        String p1 = absoluteURL.getPath();
        if (p1.startsWith("#")) {
            return true;
        }

        if (!p1.startsWith(referrerURL.getPath())) {
            // Not parent/child relationship here.
            return false;
        }
        // Both paths at this point represent path within the same site
        int x = referrerURL.getPath().length();
        int y = absoluteURL.getPath().length();
        if (x >= y) {
            return false;
        }
        char ch = absoluteURL.getPath().charAt(x + 1);
        return ('#' == ch);
    }

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
            if (StringUtils.isBlank(param)) {
                continue;
            }
            if (!param.contains("=")) {
                params.put(param, null);
                continue;
            }
            String[] kv = param.split("=", 2);
            params.put(kv[0], kv[1]);
        }
    }

    public boolean isAbsolute() {
        return isAbsolute;
    }

    /**
     * If a URL is fully-qualified protocol + server, then it is not relative.
     */
    public boolean isRelative() {
        return !isAbsolute;
    }

    /**
     * Trivial test to see if this link matches the HTML page/URL from which it
     * came. That is, we want to know if the page contains a relative link to
     * itself.
     */
    public boolean isCurrentPage(String test) {
        if (test == null) {
            return false;
        }
        return test.equalsIgnoreCase(urlValue);
    }

    public boolean isCurrentHost() {
        return isCurrentHost;
    }

    public boolean isCurrentPage() {
        return isCurrentPage;
    }

    public boolean isCurrentSite() {
        return isCurrentSite;
    }

    /**
     * Get absolute URL; limitations -- this is not intended for general use It
     * is a mere concatenation of parent + rel path. "../../....." paths are not
     * supported fully.
     *
     * @return
     */
    public String getAbsoluteURL() {
        return absoluteURL.toString();
    }

    /**
     *
     * @return URL object for this link. It is an absolute URL.
     */
    public URL getURL() {
        return absoluteURL;
    }

    public String getDirectory() {
        return directory;
    }
}
