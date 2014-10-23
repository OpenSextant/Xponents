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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.opensextant.ConfigException;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple client that pulls down HTML from a web site, acquire files and crawl sub-folders.
 * This is not a generalize web crawler.  It specifically looks for meaningful content, such as HTML pages, document downloads, etc.
 */
public class WebClient {

    private Logger log = LoggerFactory.getLogger(getClass());

    /**  
     * @param siteUrl  the url to collect.
     * @param archive  the destination archive. Keep in mind, this is the location of downloaded originals.
     *        Use Xtext instance to manage where/how you convert those originals.
     * 
     * @throws MalformedURLException 
     */
    public WebClient(String siteUrl, String archive) throws MalformedURLException, ConfigException {
        setSite(siteUrl);
        archiveRoot = archive;
    }

    protected String archiveRoot = null;
    private String proxy = null;
    protected String server = null;
    protected String site = null;
    protected HttpHost proxyHost = null;
    protected int interval = 100; // milliseconds wait between web requests.
    protected XText converter = null;

    public void configure() throws ConfigException {
        // Test if the site exists and is reachable
        testAvailability();

        // Test is your destination archive exists
        if (archiveRoot != null) {
            File test = new File(archiveRoot);
            if (!(test.isDirectory() && test.exists())) {
                throw new ConfigException(
                        "Destination archive does not exist. Caller must create prior to creation.");
            }
        }
    }

    /**
     * Caller should construct their own conversionManager and pass that in.
     * NOTE: since the web client can operate without an instance of XText, e.g., just run a crawl with no conversion
     * the WebClient constructor takes an archive path.  As you pass in a conversion manager here, make sure that the 
     * archive root there matches what is used her in the WebClient.  If you are using Xtext in embedded mode, then do not worry.
     * the archive is ignored.
     * 
     * @param conversionManager
     */
    public void setConverter(XText conversionManager) {
        converter = conversionManager;
    }

    /**
     * 
     * @param relpath
     * @return
     */
    protected File createArchiveFile(String relpath) {
        String itemArchivedPath = archiveRoot + Collector.PATH_SEP + relpath;
        File itemSaved = new File(itemArchivedPath.replaceAll("//", "/"));
        itemSaved.getParentFile().mkdirs();
        return itemSaved;
    }

    protected Map<String, HyperLink> found = new HashMap<String, HyperLink>();
    protected Set<String> saved = new HashSet<String>();

    /**
     * current depth of the crawl at any time.
     */
    protected int depth = 0;

    /** Maximum number of levels that will be crawled.
     */
    public final static int MAX_DEPTH = 10;

    /**
     * Allow a proxy host to be set given the URL.
     * Assumes port 80, no user/password.
     * 
     * @param hosturl
     */
    public void setProxy(String hosturl) {
        proxy = hosturl;
        int port = 80;
        String host = proxy;
        if (proxy.contains(":")) {
            String[] hp = proxy.split(":");
            host = hp[0];
            port = Integer.parseInt(hp[1]);
        }
        proxyHost = new HttpHost(host, port);
    }

    public void setSite(String url) throws MalformedURLException {
        site = url;
        server = new URL(url).getHost();
    }

    public String getSite() {
        return site;
    }

    public String getServer() {
        return server;
    }

    /**
     * TODO: Update to use HTTP client "HttpClients....build()" method of creating and tailoring HttpClient
     * using the proxy and cookie settings, as well as any other tuning.
     * 
     * Override if your context requires a different style of HTTP client.
     * @return
     */
    public HttpClient getClient() {
        HttpClientBuilder clientHelper = HttpClientBuilder.create();

        if (proxyHost != null) {
            clientHelper.setProxy(proxyHost);
        }

        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY).build();

        HttpClient httpClient = clientHelper.setDefaultRequestConfig(globalConfig).build();

        return httpClient;
    }

    /**
     * Tests the availability of the currently configured source.
     */
    public void testAvailability() throws ConfigException {

        if (site == null) {
            throw new ConfigException("Engineering Error: site was not set.");
        }

        try {
            HttpResponse page = getPage(site);
            return;
        } catch (Exception err) {
            throw new ConfigException(
                    String.format("%s failed to collect URL %s", getName(), site), err);
        }
    }

    /**
     * clears state of crawl.
     */
    public void reset() {
        // Clear list of distinct items found
        this.found.clear();
        // Clear list of items tracked/saved in this session.
        this.saved.clear();
    }

    /**
     * 
     * @param i
     */
    public void setInterval(int i) {
        interval = i;
    }

    /**
     * 
     */
    protected void pause() {
        if (interval > 0) {
            try {
                Thread.sleep(interval);
            } catch (Exception err) {

            }
        }
    }

    /**
     * Get a web page that requires NTLM authentication
     * 
     * @param siteURL
     * @return
     * @throws IOException
     */
    public HttpResponse getPage(String siteURL) throws IOException {
        HttpClient httpClient = getClient();
        HttpGet httpget = new HttpGet();

        try {
            /**
             * TODO: require outside caller encode URL properly.
             * For now, whitespace is only main issue.
             */
            String encodedURL = siteURL.replaceAll(" ", "%20");
            URI address = new URI(encodedURL);
            httpget.setURI(address);
            HttpResponse response = httpClient.execute(httpget);

            if (response.getStatusLine().getStatusCode() == 404) {
                throw new IOException("HTTP Page " + siteURL + " not found");
            }

            return response;
        } catch (URISyntaxException ioerr) {
            throw new IOException(ioerr);
        }
    }

    /**
     * Recursively parse a site page, limiting the crawl to local items
     * contained within the current folder/page
     * This finds only obvious HREF anchors and filters out problematic ones:
     * <pre>
     *  "/"
     *  "../xxxxxxx/"
     *  "#"
     *  "javascript:xxxxxx"
     * </pre>
     * 
     * @param html
     *            HTML text buffer
     * @return
     */
    public Collection<HyperLink> parseContentPage(String html, String pageUrl, String siteUrl) {
        Map<String, HyperLink> contentLinks = new HashMap<String, HyperLink>();
        Pattern href_matcher = Pattern.compile("href=\"([^\"]+)\"");
        Matcher matches = href_matcher.matcher(html);
        while (matches.find()) {
            String link = matches.group(1).trim();
            String link_lc = link.toLowerCase();
            if ("/".equals(link) || "#".equals(link) || link_lc.startsWith("javascript")
                    || link.startsWith("../")) {
                continue;
            }
            if (link.endsWith("/")) {
                link = link.substring(0, link.length() - 1);
            }

            try {
                HyperLink l = new HyperLink(link, pageUrl, siteUrl);
                if (!contentLinks.containsKey(l.toString())) {
                    log.info("Found link {}", link);
                    contentLinks.put(l.toString(), l);
                }
            } catch (Exception err) {
                log.error("Failed to parse URL {}", link, err);
            }

        }

        return contentLinks.values();
    }

    /**
     * Reads a data stream as text as the default encoding.
     * TODO:  test reading website content with different charset encodings to see if the resulting String
     * is properly decoded.
     */
    public static String readTextStream(InputStream io) throws IOException {
        Reader reader = new InputStreamReader(io);
        StringWriter buf = new StringWriter();

        int ch;
        while ((ch = reader.read()) >= 0) {
            buf.write(ch);
        }
        reader.close();
        io.close();

        return buf.toString();
    }

    /**
     * Reads an HttpEntity object, saving it to the path 
     * 
     * REF: http://stackoverflow.com/questions/10960409/how-do-i-save-a-file-
     * downloaded-with-httpclient-into-a-specific-folder
     */
    public static void downloadFile(HttpEntity entity, String destPath) throws IOException {
        org.apache.commons.io.IOUtils.copy(entity.getContent(), new FileOutputStream(destPath));
    }

    private String name = "Unamed Web crawler";

    public void setName(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }
}
