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
package org.opensextant.xtext.collectors.sharepoint;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

import org.apache.http.HttpResponse;
import org.opensextant.ConfigException;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.ExclusionFilter;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.Collector;
import org.opensextant.xtext.collectors.web.CrawlFilter;
import org.opensextant.xtext.collectors.web.HyperLink;
import org.opensextant.xtext.collectors.web.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: generalize this so there is a single web crawler and the site implementation might be
 * sharepoint, generic HTTP, or other.  The objective of this crawler is to collect documents from
 * sharepoint.  Landing pages, e.g. HTML for sites and sub-sites,  themselves will not be harvested.
 *
 * @author ubaldino
 *
 */
public class DefaultSharepointCrawl extends SharepointClient implements ExclusionFilter, Collector,
CrawlFilter {
    /**
     * A collection listener to consult as far as how to record the found &amp; converted content
     * as well as to determine what is worth saving.
     *
     */
    protected CollectionListener listener = null;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private boolean allowCurrentSiteOnly = true;
    private boolean allowCurrentDirOnly = false;

    /**
     * Instantiates a new default sharepoint crawl.
     *
     * @param srcSite site url
     * @param destFolder output folder
     * @param u user ID
     * @param p password
     * @param dom domain
     * @throws MalformedURLException on err
     * @throws ConfigException on err
     */
    public DefaultSharepointCrawl(String srcSite, String destFolder, String u, String p, String dom)
            throws MalformedURLException, ConfigException {
        super(srcSite, destFolder, u, p, dom);
    }

    /**
     * Important that you set a listener if you want to see what was captured.
     * As well as optimize future harvests.  Listener tells the collector if the item in question was harvested or not.
     * @param l listener to use
     */
    public void setListener(CollectionListener l) {
        listener = l;
    }

    /**
     * For web crawl, this default crawler considers flash, video media, etc. to be out of scope.
     * Other HREF links, like mailto:xyz@me.com  are also items to avoid.
     * Method is left open so you may override.
     * @param path a url
     */
    @Override
    public boolean filterOutFile(String path) {
        String url = path.toLowerCase();
        if (url.endsWith(".flv")) {
            return true;
        }
        if (url.endsWith(".mp4")) {
            return true;
        }
        if (url.startsWith("mailto:")) {
            return true;
        }
        return false;
    }

    /**
     * Run the collection.
     * Make sure you have set your converter and collection listener
     * If you have a converter that also has a conversion listener, whoa!!  good luck.
     * This web crawl example is meant to provide the mechanics of the conversion listener
     * as implemented by the collection listener.
     * The details on how actions at collection time differ from conversion time are TBD.
     *
     * @throws IOException on err
     */
    @Override
    public void collect() throws IOException {
        collectItems(this.getSite());
    }

    /**
     * Override this if you have differnt ideas about what URL patterns are of interest.
     * DEFAULT FILTER OUT:  video files, page anchors, mailto links
     * @param link found link
     * @return true if link should be ignored.
     */
    public boolean filterOut(HyperLink link) {
        if (filterOutFile(link.getAbsoluteURL())) {
            return true;
        }
        //        if (link.isPageAnchor()) {
        //            log.debug("Filter out anchor link {}", link);
        //            return true;
        //        }
        return false;
    }

    /**
     * recursive folder crawl through sharepoint site. This is where docs are
     * converted and recorded.
     * TODO: test more completely the depths of recursive folders this supports.
     *
     * @param link URL to collect
     * @throws IOException on err
     */
    public void collectItems(URL link) throws IOException {

        if (depth >= MAX_DEPTH) {
            log.info("Maximum Depth reached with link: {}", link);
            return;
        }

        HttpResponse page = getPage(link);
        String rawData = WebClient.readTextStream(page.getEntity().getContent());

        Collection<SPLink> items = parseContentPage(rawData, link);

        ++depth;
        for (SPLink l : items) {

            if (filterOut(l)) {
                log.debug("Filtering out {}", l);
                continue;
            }

            if (this.isAllowCurrentSiteOnly() && !(l.isCurrentSite() || l.isCurrentHost())) {
                // Page represented by link, l, is on another website.
                log.info("Not on current site: {}", l);
                continue;
            }

            // Download artifacts
            if (l.isFile()) {
                pause();

                try {
                    String oid = TextUtils.text_id(l.getAbsoluteURL());

                    try {
                        if (listener != null && listener.exists(oid)) {
                            continue;
                        }
                    } catch (Exception err1) {
                        log.error("Collection Listener error", err1);
                        continue;
                    }

                    // create URL for link and download artifact.
                    // encode URL prior to retrieval.
                    //
                    HttpResponse itemPage = getPage(l.getURL());

                    // B. Drop files in archive mirroring the original
                    // Sharepoint site structure.
                    File itemSaved = createArchiveFile(l.getNormalPath(), false /*not dir*/);
                    WebClient.downloadFile(itemPage.getEntity(), itemSaved.getAbsolutePath());

                    convertContent(itemSaved, l);

                } catch (Exception fileErr) {
                    log.error("Item for URL {} was not saved due to a net or IO issue.",
                            l.getAbsoluteURL(), fileErr);

                }
            }
            // Traverse sub-folders, N-deep?
        }

        // D. Get Folders at this level.
        //
        for (SPLink l : items) {
            // Download folders, recursively.
            if (l.isSharepointFolder()) {
                try {
                    collectItems(l.getSimplifiedFolderURL());
                } catch (Exception fileErr) {
                    log.error("Folder URL {} was not saved due to a net or IO issue.",
                            l.getSimplifiedFolderURL(), fileErr);
                }
            }
            // Traverse sub-folders, N-deep?
        }

        --depth;

    }

    /**
     * TODO: redesign so both Web crawl and Sharepoint crawl share this common routine:
     * copy copy copy -- see DefaultWebCrawl
     *
     * convert and record a downloaded item, given the item and its source URL.
     * @param item item
     * @param link original URL where item was found
     * @throws IOException on err
     * @throws ConfigException on err
     * @throws NoSuchAlgorithmException on err
     */
    protected void convertContent(File item, HyperLink link) throws IOException, ConfigException,
    NoSuchAlgorithmException {

        if (item == null || link == null) {
            throw new IOException("Bad data - null values for file and link...");
        }

        if (converter == null && listener != null) {
            log.debug("Link {} was saved to {}", link.getAbsoluteURL(), item.getAbsolutePath());
            listener.collected(item);
            return;
        }

        /**
         *  Convert the item.
         */
        ConvertedDocument doc = null;
        if (item.exists()) {
            // record with a success state.
            doc = converter.convert(item);

            if (doc != null) {
                doc.setDefaultID();
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

    /* (non-Javadoc)
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#setAllowCurrentDirOnly(boolean)
     */
    @Override
    public void setAllowCurrentDirOnly(boolean allowCurrentDirOnly) {
        this.allowCurrentDirOnly = allowCurrentDirOnly;
    }

    /* (non-Javadoc)
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#isAllowCurrentSiteOnly()
     */
    @Override
    public boolean isAllowCurrentSiteOnly() {
        return allowCurrentSiteOnly;
    }

    /* (non-Javadoc)
     * @see org.opensextant.xtext.collectors.web.CrawlFilter#setAllowCurrentSiteOnly(boolean)
     */
    @Override
    public void setAllowCurrentSiteOnly(boolean allowCurrentSiteOnly) {
        this.allowCurrentSiteOnly = allowCurrentSiteOnly;
    }
}
