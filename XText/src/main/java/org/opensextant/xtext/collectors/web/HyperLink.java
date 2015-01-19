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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

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
    protected boolean isFolder = false;
    protected String query = null;
    protected String directory = null;
    
    /**
     * a physical path that represents the URL uniquely. 
     */
    protected String normalizedPath = null;

    /**
     * 
     * @param link
     * @param referringLink  - Normalized, absolute URL string
     * @param site
     * @throws MalformedURLException
     */
    public HyperLink(String link, URL referringLink, URL site) throws MalformedURLException {
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
        query = absoluteURL.getQuery();

        urlPath = absoluteURL.getPath().toLowerCase();
        pathExtension = FilenameUtils.getExtension(urlPath);

        if (url_lc.endsWith(".") || StringUtils.isEmpty(pathExtension)
                || url_lc.endsWith("/")) {
            isFolder = true;
        }

        String abs_lc = absoluteURL.toString().toLowerCase();

        String path = absoluteURL.getPath();
        if (StringUtils.isBlank(path)) {
            normalizedPath = null;
        } else {

            if (path.charAt(0) == '/') {
                normalizedPath = path.substring(1);
            }
            if (normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            if (StringUtils.isNotBlank(query)) {
                normalizedPath = String.format("%s/%s.html", normalizedPath,
                        TextUtils.text_id(query));
            }
            
            if (isFolder){
                directory = new File(normalizedPath).getPath();
            } else {
                directory = new File(normalizedPath).getParent();
            }
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

    //    /**
    //     * @param referrer the referrer to set
    //     */
    //    public void setReferrer(String referrer) {
    //        this.referrer = referrer;
    //    }

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

    public String toString() {
        return absoluteURL.toString();
    }

    /**
     * trivial test for dynamic content.
     * 
     * @return
     */
    public boolean isDynamic() {
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
        if (FileUtility.getFileDescription(urlValue) == FileUtility.DOC_MIMETYPE
                || FileUtility.isArchiveFile(urlValue)
                || FileUtility.getFileDescription(urlValue) == FileUtility.SPREADSHEET_MIMETYPE
                || FileUtility.getFileDescription(urlValue) == FileUtility.GIS_MIMETYPE) {
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
    
    public boolean isCurrentHost(){
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
}
