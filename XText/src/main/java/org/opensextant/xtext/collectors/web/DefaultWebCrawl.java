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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.DateUtils;
import org.opensextant.ConfigException;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.ExclusionFilter;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A demonstration of how to use the WebClient to crawl a site and convert it as you go.
 * An optional collection listener is settable to let you do something with each item collected/converted.
 *
 * @author ubaldino
 *
 */
public class DefaultWebCrawl extends WebClient implements ExclusionFilter, Collector, CrawlFilter {

    /**
     * A collection listener to consult as far as how to record the found &amp; converted content
     * as well as to determine what is worth saving.
     *
     */
    protected CollectionListener listener = null;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private boolean allowCurrentSiteOnly = true;
    private boolean allowCurrentDirOnly = false;
    private HashSet<String> errorPages = new HashSet<>();
    private List<String> prefixFilters = new ArrayList<>();
    private List<String> prefixIgnore = new ArrayList<>();

    /**
     *
     * @param srcSite
     *            top level site
     * @param destFolder
     *            output folder
     * @throws MalformedURLException
     *             if srcSite is invalid format
     * @throws ConfigException
     *             if other setup error
     */
    public DefaultWebCrawl(String srcSite, String destFolder) throws MalformedURLException,
            ConfigException {
        super(srcSite, destFolder);
    }

    /**
     * Important that you set a listener if you want to see what was captured.
     * As well as optimize future harvests. Listener tells the collector if the item in question was harvested or not.
     * 
     * @param l
     *            listener to use
     */
    public void setListener(CollectionListener l) {
        listener = l;
    }

    public void addPrefixFilter(String u) {
        if (StringUtils.isNotBlank(u)) {
            this.prefixFilters.add(u);
        }
    }

    public void addPrefixFilters(Collection<String> arr) {
        if (arr != null) {
            this.prefixFilters.addAll(arr);
        }
    }

    public void addIgnoreFilter(String u) {
        if (StringUtils.isNotBlank(u)) {
            this.prefixIgnore.add(u);
        }
    }

    public void addIgnoreFilters(Collection<String> arr) {
        if (arr != null) {
            this.prefixIgnore.addAll(arr);
        }
    }

    /**
     * For web crawl, this default crawler considers flash, video media, etc. to be out of scope.
     * Other HREF links, like mailto:xyz@me.com are also items to avoid.
     * Method is left open so you may override.
     * 
     * @param path
     *            a url
     */
    @Override
    public boolean filterOutFile(String path) {
        String url = path.toLowerCase();
        if (url.startsWith("mailto:")) {
            return true;
        }
        if (url.endsWith(".atom") || url.endsWith(".rss")) {
            return true;
        }
        if (url.endsWith(".flv")) {
            return true;
        }
        if (url.endsWith(".mp4")) {
            return true;
        }
        if (url.contains("xmlrpc")) {
            return true;
        }
        return false;
    }

    /**
     * Run the collection.
     * Make sure you have set your converter and collection listener
     * If you have a converter that also has a conversion listener, whoa!! good luck.
     * This web crawl example is meant to provide the mechanics of the conversion listener
     * as implemented by the collection listener.
     * The details on how actions at collection time differ from conversion time are TBD.
     *
     * @throws IOException
     *             on collection err
     */
    @Override
    public void collect() throws IOException {
        try {
            collectItems(null, this.getSite());
        } catch (NoSuchAlgorithmException err) {
            log.error("Hashing error", err);
        }
    }

    /**
     * Override this if you have differnt ideas about what URL patterns are of interest.
     * DEFAULT FILTER OUT: video files, page anchors, mailto links
     * 
     * @param link
     *            a URL
     * @return true if link should be ignored.
     */
    public boolean filterOut(HyperLink link) {
        if (filterOutFile(link.getAbsoluteURL())) {
            return true;
        }
        if (link.isPageAnchor()) {
            log.debug("Filter out anchor link {}", link);
            return true;
        }

        return false;
    }

    /**
     * recursive folder crawl through a site. This is where docs are
     * converted and recorded.
     * As hashing algorithms are used in defining concise output paths, NoSuchAlgorithmException is thrown.
     *
     * @param _link
     *            a URL
     * @param startingSite
     *            the top level site
     * @throws IOException
     *             on err
     * @throws NoSuchAlgorithmException
     *             error that never happens
     */
    public void collectItems(String _link, URL startingSite) throws IOException,
            NoSuchAlgorithmException {
        String link = startingSite.toString();
        if (_link != null) {
            link = _link;
        }

        HyperLink thisLink = new HyperLink(link, new URL(link), getSite());

        if (errorPages.contains(thisLink.getAbsoluteURL())) {
            log.debug("Do not visit error pages tracked in this session; link: {}", link);
            return;
        }

        HttpResponse page = getPage(prepURL(link));

        /*
         * As of XText 1.4, this HTTP header does not appear to be avaiable often using this http API:
         */
        Header lastModStr = page.getFirstHeader("Last-Modified");
        Date lastMod = null;
        if (lastModStr != null) {
            lastMod = DateUtils.parseDate(lastModStr.getValue());
        }

        /*
         * 1.  Capture the page content represented by the requested link.
         *     It is saved to  FILE.html
         */
        String rawData = WebClient.readTextStream(page.getEntity().getContent());

        String thisPath = thisLink.getNormalPath();
        //if (StringUtils.isEmpty(thisPath)) {
        //    return;
        //}
        if (thisLink.isDynamic() && (!thisPath.endsWith("html"))) {
            thisPath = String.format("%s.html", thisPath);
        }
        File thisPage = createArchiveFile(thisPath, thisLink.isFolder());
        // OVERWRITE:
        if (!thisPage.exists()) {
            FileUtility.writeFile(rawData, thisPage.getAbsolutePath());
        }
        log.info("Starting in on {} from {} @ depth=" + depth, link, site);
        pause();

        ++depth;

        collectItemsOnPage(rawData, thisLink.getURL(), getSite());
    }

    /**
     * 
     * @param f file object
     * @throws IOException on err
     */
    public void collect(File f) throws IOException {
        String pageContent = FileUtility.readFile(f, "UTF-8");
        collectItemsOnPage(pageContent, getSite(), getSite());
    }

    /**
     * User filters are likely to be more general ALLOW, but specific DENIES within what is allowed.
     * 
     * @param path filepath
     * @return if user options filter out the given path
     */
    protected boolean userFilteredOut(final String path) {
        /*
         * Caller URL filters. Filter In.
         */
        boolean allow = true;
        if (this.prefixFilters.size() > 0) {
            allow = false;
            for (String filt : prefixFilters) {
                if (path.startsWith(filt)) {
                    allow = true;
                    break;
                }
            }
        }

        if (!allow) {
            return true;
        }

        /*
         * Okay, url was allowed, but does it fit a pattern that is to be filtered out?
         */
        if (this.prefixIgnore.size() > 0) {
            for (String filt : prefixIgnore) {
                if (path.startsWith(filt)) {
                    allow = false;
                    break;
                }
            }
        }

        return !allow;
    }

    /**
     * Internal method for parsing and harvesting from a single page and then crawling deeper, if instructed to do so.
     * 
     * @param pageContent raw HTML
     * @param url  url for HTML
     * @param site  top level url for site
     */
    protected void collectItemsOnPage(String pageContent, URL url, URL site) {

        Collection<HyperLink> items = parseContentPage(pageContent, url, site);

        /* 2. Collect items on this page.
         *
         */
        for (HyperLink l : items) {
            if (filterOut(l)) {
                continue;
            }

            if (this.isAllowCurrentSiteOnly() && !(l.isCurrentSite() || l.isCurrentHost())) {
                // Page represented by link, l, is on another website.
                log.debug("Not on current site: {}", l);
                continue;
            }

            if (this.isAllowCurrentDirOnly() && !l.isCurrentPage()) {
                // Page represented by link, l, is on another directory on same or site.
                log.debug("Not on current directory: {}", l);
                continue;
            }

            /* TODO: fix "key", as it represents not just path, but unique URLs
             * different URLs with same path would collide.
             * TODO: in general fix the ability to crawl off requested site.
             *  If that is really needed, this is not the crawling capability you want.
             *
             */
            String key = l.getNormalPath();
            if (key == null) {
                key = l.getAbsoluteURL();
            }

            if (found.containsKey(key)) {
                // We already did this.
                continue;
            }

            if (userFilteredOut(key)) {
                // We don't want to do this.
                log.debug("Filtered Out by User: {}", key);
                continue;
            }

            found.put(key, l);

            // B. Drop files in archive mirroring the original
            //

            if (saved.contains(l.getId())) {
                // in theory this item resolved to an item that was already saved.
                // ignore.
                continue;
            }

            // Download artifacts
            if (l.isFile() || l.isWebPage()) {
                pause();

                log.info("Pulling page {}", l);

                try {
                    // The default document ID will be an MD5 hash ID of the URL.
                    // This may differ for other collectors/harvesters/listeners
                    //
                    try {
                        if (listener != null && listener.exists(l.getId())) {
                            // You already collected this. So it will be ignored.
                            continue;
                        }
                    } catch (Exception err1) {
                        log.error("Collection Listener error", err1);
                        continue;
                    }

                    // create URL for link and download artifact.
                    HttpResponse itemPage = getPage(l.getURL());
                    // Regardless of the item's discovered path, determine
                    // the relative path.
                    if (itemPage.getStatusLine().getStatusCode() >= 400) {
                        this.errorPages.add(l.getAbsoluteURL());
                        log.error("Failing on this request, HTTP status>=400, LINK={}", l.getURL());
                        continue;
                    }

                    /*
                     * Identify the correct type of file this item is, from HTTP headers &amp; MIME, not just the link
                     */
                    Header contentType = itemPage.getEntity().getContentType();
                    if (contentType != null) {
                        l.setMIMEType(contentType.getValue());
                    }

                    /*
                     * Create a non-trivial path for the item.
                     * 
                     */
                    String fpath = l.getNormalPath();
                    if (l.isDynamic()) {
                        if (!fpath.endsWith(".html")) {
                            fpath = fpath + ".html";
                        }
                    }
                    File itemSaved = createArchiveFile(fpath, false);
                    File dir = new File(itemSaved.getParentFile().getAbsolutePath());
                    FileUtility.makeDirectory(dir);
                    l.setFilepath(itemSaved);
                    // CACHE the identify of this URL.
                    saved.add(l.getId());

                    WebClient.downloadFile(itemPage.getEntity(), itemSaved.getAbsolutePath());

                    convertContent(itemSaved, l);

                    // Continue to crawl deeper...
                    //
                    if (l.isWebPage() && depth <= MAX_DEPTH) {
                        collectItems(l.getAbsoluteURL(), site);
                    }
                } catch (Exception fileErr) {
                    log.error("Item for URL {} was not saved due to a net or IO issue.",
                            l.getAbsoluteURL(), fileErr);
                }
            }
        }
        --depth;
    }

    /**
     * convert and record a downloaded item, given the item and its source URL.
     * 
     * @param item
     *            item to convert
     * @param link
     *            link representing the original/source
     * @throws IOException
     *             on err
     * @throws ConfigException
     *             on err
     * @throws NoSuchAlgorithmException
     *             an error that never happens
     */
    protected void convertContent(File item, HyperLink link)
            throws IOException, ConfigException, NoSuchAlgorithmException {

        if (item == null || link == null) {
            throw new IOException("Bad data - null values for file and link...");
        }

        if (converter == null && listener != null) {
            log.debug("Link {} was saved to {}", link.getAbsoluteURL(), item.getAbsolutePath());
            listener.collected(item);
            return;
        }

        /**
         * Convert the item.
         */
        ConvertedDocument doc = null;
        if (item.exists()) {
            // record with a success state.
            doc = converter.convert(item);

            if (doc != null) {
                if (doc.textpath == null) {
                    log.error("Expecting the content to be non-null for {}", doc.getFilepath());
                    return;
                }
                //doc.setDefaultID();
                doc.setId(link.getId());
                doc.addSourceURL(link.getAbsoluteURL(), link.getReferrer());
                // This path must already exist
                doc.saveBuffer(new File(doc.textpath));

                if (listener != null) {
                    listener.collected(doc, item.getAbsolutePath());
                }
            } else {
                log.error("Document was not converted, FILE={}", item);
            }
        }
    }

    /**
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#isAllowCurrentDirOnly()
     */
    @Override
    public boolean isAllowCurrentDirOnly() {
        return allowCurrentDirOnly;
    }

    /**
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#setAllowCurrentDirOnly(boolean)
     */
    @Override
    public void setAllowCurrentDirOnly(boolean allowCurrentDirOnly) {
        this.allowCurrentDirOnly = allowCurrentDirOnly;
    }

    /**
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#isAllowCurrentSiteOnly()
     */
    @Override
    public boolean isAllowCurrentSiteOnly() {
        return allowCurrentSiteOnly;
    }

    /**
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#setAllowCurrentSiteOnly(boolean)
     */
    @Override
    public void setAllowCurrentSiteOnly(boolean allowCurrentSiteOnly) {
        this.allowCurrentSiteOnly = allowCurrentSiteOnly;
    }
}
