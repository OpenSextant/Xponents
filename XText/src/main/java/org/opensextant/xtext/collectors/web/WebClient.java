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
import org.apache.http.impl.client.HttpClientBuilder;
import org.opensextant.ConfigException;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * Simple client that pulls down HTML from a web site, acquire files and crawl sub-folders.
 * This is not a generalize web crawler. It specifically looks for meaningful content, such as HTML pages, document
 * downloads, etc.
 */
public class WebClient {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Prep url. This ensures that found URLs that may contain whitespace
     * are properly converted to proper URL format/escaping.
     *
     * @param u
     *            URL string
     * @return URL object
     * @throws MalformedURLException
     *             the malformed url exception
     */
    public static URL prepURL(String u) throws MalformedURLException {
        /**
         * TODO: require outside caller encode URL properly.
         * For now, whitespace is only main issue.
         */
        String encoded = u.replaceAll(" ", "%20");
        return new URL(encoded);
    }

    /**
     * Prep url path.
     *
     * @param u
     *            the u
     * @return the string
     * @throws MalformedURLException
     *             the malformed url exception
     */
    public static String prepURLPath(String u) throws MalformedURLException {
        /**
         * TODO: require outside caller encode URL properly.
         * For now, whitespace is only main issue.
         */
        return u.replaceAll(" ", "%20");
    }

    /**
     * Instantiates a new web client.
     *
     * @param siteUrl
     *            the url to collect.
     * @param archive
     *            the destination archive. Keep in mind, this is the location of downloaded originals.
     *            Use Xtext instance to manage where/how you convert those originals.
     * @throws MalformedURLException
     *             if URL given is bad
     * @throws ConfigException
     *             the config exception
     */
    public WebClient(String siteUrl, String archive) throws MalformedURLException, ConfigException {
        setSite(siteUrl);
        archiveRoot = archive;
    }

    /** The archive root. */
    protected String archiveRoot = null;
    private String proxy = null;

    /** The server. */
    protected String server = null;

    /** The site. */
    protected URL site = null;

    /** The proxy host. */
    protected HttpHost proxyHost = null;

    /** The interval. */
    protected int interval = 100; // milliseconds wait between web requests.

    /** The converter. */
    protected XText converter = null;

    /**
     * Configure.
     *
     * @throws ConfigException
     *             the config exception
     */
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
     * the WebClient constructor takes an archive path. As you pass in a conversion manager here, make sure that the
     * archive root there matches what is used her in the WebClient. If you are using Xtext in embedded mode, then do
     * not worry.
     * the archive is ignored.
     *
     * @param conversionManager
     *            converter, an XText instance
     */
    public void setConverter(XText conversionManager) {
        converter = conversionManager;
    }

    /**
     * Creates the archive file.
     *
     * @param relpath
     *            relative path for this object
     * @param isDir
     *            the is dir
     * @return full path
     * @throws IOException
     *             on I/O error
     */
    protected File createArchiveFile(String relpath, boolean isDir) throws IOException {
        String itemArchivedPath = archiveRoot + Collector.PATH_SEP + relpath;
        File itemSaved = new File(itemArchivedPath.replaceAll("//", "/"));
        if (isDir) {
            FileUtility.makeDirectory(itemSaved);
        } else {
            itemSaved.getParentFile().mkdirs();
        }
        return itemSaved;
    }

    /** */
    protected Map<String, HyperLink> found = new HashMap<String, HyperLink>();

    /** */
    protected Set<String> saved = new HashSet<String>();

    /**
     * current depth of the crawl at any time.
     */
    protected int depth = 0;

    /**
     * Maximum number of levels that will be crawled.
     */
    public final static int MAX_DEPTH = 5;

    /**
     * Allow a proxy host to be set given the URL.
     * Assumes port 80, no user/password.
     *
     * @param hosturl
     *            proxy URL
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

    public void setProxy(String h, int port) {
        proxyHost = new HttpHost(h, port);
    }

    boolean useSystemProperties = false;

    /**
     * @param b flag to enable use of System Properties to get proxy settings, etc.
     */
    public void enableSystemProperties(boolean b) {
        this.useSystemProperties = b;
    }

    /**
     * Sets the site.
     *
     * @param url
     *            the new site
     * @throws MalformedURLException
     *             the malformed url exception
     */
    public void setSite(String url) throws MalformedURLException {
        site = new URL(url);
        server = new URL(url).getHost();
    }

    /**
     * Gets the site.
     *
     * @return the URL object
     */
    public URL getSite() {
        return site;
    }

    /**
     * Gets the server.
     *
     * @return server hostname
     */
    public String getServer() {
        return server;
    }

    /**
     * TODO: Update to use HTTP client "HttpClients....build()" method of creating and tailoring HttpClient
     * using the proxy and cookie settings, as well as any other tuning.
     *
     * Override if your context requires a different style of HTTP client.
     * 
     * @return HttpClient 4.x object
     */
    public HttpClient getClient() {
        HttpClientBuilder clientHelper = null;

        if (this.useSystemProperties) {
            clientHelper = HttpClientBuilder.create().useSystemProperties();
        } else {
            clientHelper = HttpClientBuilder.create();
            if (proxyHost != null) {
                clientHelper.setProxy(proxyHost);
            }
        }

        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY).build();

        HttpClient httpClient = clientHelper.setDefaultRequestConfig(globalConfig).build();

        return httpClient;
    }

    /**
     * Tests the availability of the currently configured source.
     *
     * @throws ConfigException
     *             error which means resource is unavailable.
     */
    public void testAvailability() throws ConfigException {

        if (site == null) {
            throw new ConfigException("Engineering Error: site was not set.");
        }

        try {
            getPage(site);
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
     * Sets the interval.
     *
     * @param i
     *            interval
     */
    public void setInterval(int i) {
        interval = i;
    }

    /**
     * Pause.
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
     * Get a web page that requires NTLM authentication.
     *
     * @param siteURL
     *            URL
     * @return response for the URL
     * @throws IOException
     *             on error
     */
    public HttpResponse getPage(URL siteURL) throws IOException {
        HttpClient httpClient = getClient();
        HttpGet httpget = new HttpGet();

        try {
            URI address = siteURL.toURI();
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

    private static final Pattern HREF_MATCH = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /**
     * Recursively parse a site page, limiting the crawl to local items
     * contained within the current folder/page
     * This finds only obvious HREF anchors and filters out problematic ones:
     * 
     * <pre>
     *  "/"
     *  "../xxxxxxx/"
     *  "#"
     *  "javascript:xxxxxx"
     * </pre>
     * 
     * TODO: pass in or set an allow filter. sometimes caller knows which content is worth
     * following, e.g., ../abc_folder/morecontent.htm and such URLs should be resolved absolutely to avoid
     * recapture repeatedly.
     *
     * @param html
     *            HTML text buffer
     * @param pageUrl
     *            the page url
     * @param siteUrl
     *            the site url
     * @return a list of found links
     */
    public Collection<HyperLink> parseContentPage(String html, URL pageUrl, URL siteUrl) {
        Map<String, HyperLink> contentLinks = new HashMap<String, HyperLink>();
        Matcher matches = HREF_MATCH.matcher(html);
        while (matches.find()) {
            String link = matches.group(1).trim();
            String link_lc = link.toLowerCase();

            if ("/".equals(link) || "#".equals(link)) {
                continue;
            }
            if (link_lc.startsWith("#") || link_lc.startsWith("javascript")) {
                continue;
            }
            if (link_lc.startsWith("mailto:")) {
                log.info("Ignore Mailto {}", link_lc);
                continue;
            }

            if (link.endsWith("/")) {
                link = link.substring(0, link.length() - 1);
            }

            try {
                HyperLink l = new HyperLink(link, pageUrl, siteUrl);
                if (l.isResource()) {
                    continue;
                }
                if (!contentLinks.containsKey(l.toString())) {
                    log.debug("Found link {}", link);
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
     * TODO: test reading website content with different charset encodings to see if the resulting String
     * is properly decoded.
     *
     * @param io
     *            IO stream
     * @return content of the stream
     * @throws IOException
     *             I/O error
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
     *
     * @param entity
     *            http entity obj
     * @param destPath
     *            output path
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static void downloadFile(HttpEntity entity, String destPath) throws IOException {
        org.apache.commons.io.IOUtils.copy(entity.getContent(), new FileOutputStream(destPath));
    }

    private String name = "Unamed Web crawler";

    /**
     * Set a name of this client for tracking puropses, e.g., in multiple threads
     *
     * @param n
     *            the new name
     */
    public void setName(String n) {
        name = n;
    }

    /**
     * Get name of client
     *
     * @return the name
     */
    public String getName() {
        return name;
    }
}
