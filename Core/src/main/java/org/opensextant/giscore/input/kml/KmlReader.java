/*
 *  KmlReader.java
 *
 *  @author Jason Mathews
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 */
package org.opensextant.giscore.input.kml;


import java.io.*;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.input.IGISInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper to {@link KmlInputStream} that handles various house cleaning of parsing
 * KML and KMZ sources.  Caller does not need to know if target is KML or KMZ resource.
 * <p>
 * Handles the following tasks:
 * <ul>
 * <li>read from KMZ/KML files or URLs transparently
 * <li>re-writing of URLs inside KMZ files to resolve relative URLs
 * <li>rewrites relative URLs of NetworkLinks, inline or shared IconStyles, and
 * 	 Screen/GroundOverlays with respect to parent URL.
 *   Use {@link UrlRef} to get InputStream of links and resolve URI to original URL.
 * <li>recursively read all features from referenced NetworkLinks
 * </ul>
 *
 * @author Jason Mathews, MITRE Corp.
 * Created: Mar 5, 2009 9:12:19 AM
 */
public class KmlReader extends KmlBaseReader implements IGISInputStream {

    private static final Logger log = LoggerFactory.getLogger(KmlReader.class);

    private InputStream iStream;

    private final KmlInputStream kis;

    private final List<URI> gisNetworkLinks = new ArrayList<>();

    private int maxLinkCount = 500;
    private boolean maxLinkCountExceeded;

    private Proxy proxy;

    private boolean rewriteStyleUrls;

    private boolean ignoreInactiveRegionNetworkLinks;

    private int skipCount;

    /**
     * Creates a {@code KmlStreamReader} and attempts to read
     * all GISObjects from a stream created from the {@code URL}.
     * @param url   the KML or KMZ URL to be opened for reading.
     * @throws java.io.IOException if an I/O error occurs
     */
    public KmlReader(URL url) throws IOException {
        this(url, null);
    }

    /**
     * Creates a {@code KmlStreamReader} and attempts to read
     * all GISObjects from a stream created from the {@code URL}.
     *
     * @param url   the KML or KMZ URL to be opened for reading, never <code>null</code>.
     * @param proxy the Proxy through which this connection
     *             will be made. If direct connection is desired,
     *             {@code null} should be specified.
     *
     * @throws java.io.IOException if an I/O error occurs
     * @throws NullPointerException if url is <code>null</code>
     */
    public KmlReader(URL url, Proxy proxy) throws IOException {
        this.proxy = proxy;
        iStream = UrlRef.getInputStream(url, proxy);
        try {
            kis = new KmlInputStream(iStream);
        } catch (IOException e) {
            IOUtils.closeQuietly(iStream);
            throw e;
        }
        if (iStream instanceof ZipInputStream) compressed = true;
        baseUrl = url;
    }

    /**
     * Creates a {@code KmlReader} and attempts
     * to read all GISObjects from the {@code File}.
     *
     * @param      file   the KML or KMZ file to be opened for reading, never <code>null</code>.
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if file is <code>null</code>
     */
    @SuppressWarnings("unchecked")
    public KmlReader(File file) throws IOException {
        if (file.getName().toLowerCase().endsWith(".kmz")) {
            // Note: some "KMZ" files fail validation using ZipFile but work with ZipInputStream
            ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // simply find first kml file in the archive
                // see note on KMZ in UrlRef.getInputStream() method for more detail
                if (entry.getName().toLowerCase().endsWith(".kml")) {
                    iStream = zis;
                    // indicate that the stream is for a KMZ compressed file
                    compressed = true;
                    break;
                }
            }
            if (iStream == null) {
                IOUtils.closeQuietly(zis);
                throw new FileNotFoundException("Failed to find KML content in file: " + file);
            }
        } else {
            // treat as normal .kml text file
            iStream = new BufferedInputStream(new FileInputStream(file));
        }

        try {
            kis = new KmlInputStream(iStream);
        } catch (IOException e) {
            IOUtils.closeQuietly(iStream);
            throw e;
        }

        URL url;
        try {
            url = file.toURI().toURL();
        } catch (Exception e) {
            // this should not happen
            log.warn("Failed to convert file URI to URL: " + e);
            url = null;
        }
        baseUrl = url;
    }

    /**
     * Create KmlReader using provided InputStream.
     *
     * @param is  input stream for the kml content, never {@code null}
     * @param isCompressed  True if the input stream is a compressed stream (e.g. KMZ resource)
     *				in which case relative links are resolved with respect to the baseUrl
     *              as KMZ "ZIP" entries as opposed to using the baseUrl as the parent URL context only.
     * @param baseUrl the base URL context from which relative links are resolved
     * @param proxy the Proxy through which URL connections
     *             will be made. If direct connection is desired,
     *             {@code null} should be specified.
     * @throws IOException if an I/O error occurs
     */
    public KmlReader(InputStream is, boolean isCompressed, URL baseUrl, Proxy proxy) throws IOException {
        try {
            kis = new KmlInputStream(is);
        } catch (IOException e) {
            IOUtils.closeQuietly(is);
            throw e;
        }
        this.proxy = proxy;
        compressed = isCompressed || is instanceof ZipInputStream;
        iStream = is;
        this.baseUrl = baseUrl;
    }

    /**
     * Create KmlReader using provided InputStream. Automatically determines
     * if source is KMZ or KML stream by checking the content.
     *
     * @param is  input stream for the kml/kmz content, never {@code null}
     * @param baseUrl the base URL context from which relative links are resolved.
     * 				If {@code null} then reader will not be able to resolve relative links.
     * @param proxy the Proxy through which URL connections
     *             will be made. If direct connection is desired,
     *             {@code null} should be specified.
     * @throws IOException if an I/O error occurs
     */
    public KmlReader(InputStream is, URL baseUrl, Proxy proxy) throws IOException {
        ZipInputStream zis = null;
        if (is instanceof ZipInputStream) {
            zis = (ZipInputStream) is;
        } else {
            PushbackInputStream pbis = new PushbackInputStream(is, 2);
            byte[] hdr = new byte[2];
            if (pbis.read(hdr) < 2) throw new EOFException();
            pbis.unread(hdr);
            // KMZ/ZIP source must start with bytes "PK" or 0x504b
            // expected ZIP header: PK\003\004 (common), PK\005\006 (empty archive), or PK\007\008 (spanned archive)
            if (hdr[0] == 0x50 && hdr[1] == 0x4b) {
                // compressed input stream - handle as KMZ source
                zis = new ZipInputStream(pbis);
            } else {
                // source not valid KMZ so treat as ASCII KML source
                iStream = pbis;
            }
        }

        if (zis != null) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // System.out.println("zip entry: " + entry.getName());
                // simply find first kml file in the archive
                // see note on KMZ in UrlRef.getInputStream() method for more detail
                if (entry.getName().toLowerCase().endsWith(".kml")) {
                    iStream = zis;
                    // indicate that the stream is for a KMZ compressed file
                    compressed = true;
                    break;
                }
            }
            if (iStream == null) {
                IOUtils.closeQuietly(zis);
                throw new FileNotFoundException("Failed to find KML content in stream");
            }
        }

        try {
            kis = new KmlInputStream(iStream);
        } catch (IOException e) {
            IOUtils.closeQuietly(iStream);
            throw e;
        }

        this.proxy = proxy;
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the encoding style of the XML data.
     * @return the character encoding, defaults to "UTF-8". Never null.
     */
    @NotNull
    public String getEncoding() {
        return kis.getEncoding();
    }

    /**
     * Get list of NetworkLinks visited.  If {@code importFromNetworkLinks()} was
     * called then this will be the complete list including all NetworkLinks
     * that are reachable starting from the base KML document and recursing
     * into all linked KML sources.
     *
     * @return list of NetworkLink URIs
     */
    @NotNull
    public List<URI> getNetworkLinks() {
        return gisNetworkLinks;
    }

    /**
     * Get maximum number of NetworkLinks that are allowed to be processed when
     * importing nested KML content. Default=500.
     */
    public int getMaxLinkCount() {
        return maxLinkCount;
    }

    /**
     * Set maximum number of NetworkLinks that are allowed to be processed when
     * importing nested KML content. <P> Setting <code>maxLinkCount</code> = 0
     * disables this check allowing infinite number of nested content.
     * <BR><B>WARNING:</B> If target KML source is deep-nested like a
     * super-overlay then disabling this check should be done with caution.
     * @param maxLinkCount Maximum number of NetworkLinks allowed when
     * 	importing network links. Set <code>maxLinkCount</code> = 0 to disable
     * 	this check and allow infinite number of nested content.
     */
    public void setMaxLinkCount(int maxLinkCount) {
        this.maxLinkCount = maxLinkCount <= 0 ? Integer.MAX_VALUE : maxLinkCount;
    }

    /**
     * Flag set true only if the max link count limit has been exceeded on
     * import of NetworkLinks after calling {@link #importFromNetworkLinks()}.
     * @return true if network link count reached, otherwise false
     */
    public boolean isMaxLinkCountExceeded() {
        return maxLinkCountExceeded;
    }

    /**
     * Reads next gis object from the stream.
     * @return the next gis object present in the source, or {@code null}
     * if there are no more objects present.
     * @throws IOException if an I/O error occurs
     */
    // @CheckForNull

    public IGISObject read() throws IOException {
        return read(kis, null, null);
    }

    private IGISObject read(IGISInputStream inputStream, UrlRef parent, List<URI> networkLinks) throws IOException {
        IGISObject gisObj = inputStream.read();
        if (gisObj == null) return null;

        final Class<? extends IGISObject> aClass = gisObj.getClass();
        if (aClass == Feature.class) {
            Feature f = (Feature) gisObj;
            checkStyleUrl(parent, f); // rewrite relative-links in styleURL as absolute URLs
            StyleSelector style = f.getStyle();
            if (style != null) {
                // handle IconStyle href if defined
                checkStyleType(parent, style);
            }
        } else if (aClass == ContainerStart.class) {
            final ContainerStart cs = (ContainerStart) gisObj;
            checkStyleUrl(parent, cs);
            for (StyleSelector s : cs.getStyles()) {
                checkStyleType(parent, s);
            }
        } else if (gisObj instanceof NetworkLink) {
            // handle NetworkLink href
            NetworkLink link = (NetworkLink) gisObj;
            TaggedMap region = link.getRegion();
            // check/ignore networklinks if region not in view
            if (ignoreInactiveRegionNetworkLinks && checkRegion(region)) {
                log.debug("ignore out of region NetworkLink");
                skipCount++;
            } else {
                checkStyleUrl(parent, link);
                // adjust URL with httpQuery and viewFormat parameters
                // if parent is compressed and URL is relative then rewrite URL
                //log.debug("link href=" + link.getLink());
                URI uri = getLinkHref(parent, link.getLink());
                if (uri != null) {
                    //log.debug(">link href=" + link.getLink());
                    if (!gisNetworkLinks.contains(uri)) {
                        gisNetworkLinks.add(uri);
                        if (networkLinks != null) networkLinks.add(uri);
                    } else log.debug("duplicate NetworkLink href");
                } else
                    log.debug("NetworkLink href is empty or missing");
                // Note: NetworkLinks can have inline Styles & StyleMaps
            }
        } else if (gisObj instanceof Overlay) {
            // handle GroundOverlay, ScreenOverlay or PhotoOverlay href
            Overlay o = (Overlay) gisObj;
            checkStyleUrl(parent, o);
            TaggedMap icon = o.getIcon();
            String href = icon != null ? trimToNull(icon, HREF) : null;
            if (href != null) {
                // note PhotoOverlays may have entity replacements in URL
                // see http://code.google.com/apis/kml/documentation/photos.html
                // e.g. http://mw1.google.com/mw-earth-vectordb/kml-samples/gp/seattle/gigapxl/$[level]/r$[y]_c$[x].jpg</href>
                // Given zoom level Google Earth client maps this URL to URLs such as this: level=1 => .../0/r0_c0.jpg and level=3 => 3/r3_c1.jpg
                // TODO: GroundOverlay Icon is same kml:LinkType as NetworkLink Link element
                // and URL needs to reflect viewFormat and httpQuery parameters.
                // Maybe need to call getLinkHref() rather than getLink()
                URI uri = getLink(parent, href);
                if (uri != null) {
                    href = uri.toString();
                    // store rewritten overlay URL back to property store
                    icon.put(HREF, href);
                    // can we have a GroundOverlay W/O LINK ??
                }
            }
            // Note: Overlays can have inline Styles & StyleMaps but should not be relevant to icon style hrefs
        } else if (aClass == Style.class) {
            // handle IconStyle href if defined
            checkStyle(parent, (Style) gisObj);
        } else if (aClass == StyleMap.class) {
            // check StyleMaps with inline Styles...
            checkStyleMap(parent, (StyleMap) gisObj);
        }

        return gisObj;
    }

    /**
     * Check for relative URLs in styleUrl value and rewrite
     * to absolute URLs with respect to its parent URL context.
     * @param parent Parent URL context
     * @param f This common feature to check
     */
    private void checkStyleUrl(UrlRef parent, Common f) {
        if (rewriteStyleUrls && baseUrl != null) {
            String styleUrl = f.getStyleUrl();
            // check for relative URLs (e.g. style.kml#blue-icon)
            if (StringUtils.isNotEmpty(styleUrl)
                    && !UrlRef.isAbsoluteUrl(styleUrl) && styleUrl.indexOf('#') > 0) {
                //System.out.println("XXX: Relative Style href: " + styleUrl);
                URI uri = getLink(parent, styleUrl);
                if (uri != null) {
                    styleUrl = uri.toString();
                    // store rewritten relative URL back as absolute
                    f.setStyleUrl(styleUrl);
                    log.debug("XXX: rewrite relative styleUrl: {}", styleUrl);
                }
            }
        }
    }

    private void checkStyleType(UrlRef parent, StyleSelector s) {
        if (s instanceof Style) {
            // normalize iconStyle hrefs
            checkStyle(parent, (Style) s);
        } else if (s instanceof StyleMap) {
            checkStyleMap(parent, (StyleMap) s);
        }
    }

    private void checkStyleMap(UrlRef parent, StyleMap sm) {
        for (Iterator<Pair> it = sm.getPairs(); it.hasNext(); ) {
            Pair pair = it.next();
            if (rewriteStyleUrls && baseUrl != null) {
                String styleUrl = pair.getStyleUrl();
                // check for relative URLs (e.g. style.kml#blue-icon)
                if (StringUtils.isNotEmpty(styleUrl)
                        && !UrlRef.isAbsoluteUrl(styleUrl) && styleUrl.indexOf('#') > 0) {
                    // System.out.println("XXX: Relative StyleMap pair href: " + styleUrl);
                    URI uri = getLink(parent, styleUrl);
                    if (uri != null) {
                        styleUrl = uri.toString();
                        // store rewritten relative URL back as absolute
                        pair.setStyleUrl(styleUrl);
                        log.debug("XXX: rewrite relative StyleMap pair styleUrl: {}", styleUrl);
                    }
                }
            }
            StyleSelector style = pair.getStyleSelector();
            if (style instanceof Style) {
                // normalize iconStyle hrefs
                checkStyle(parent, (Style) style);
            }
            // ignore nested StyleMaps
        }
    }

    private void checkStyle(UrlRef parent, Style style) {
        if (style.hasIconStyle()) {
            String href = style.getIconUrl();
            // rewrite relative URLs with UrlRef to include context with parent source
            // note: could also use URI.isAbsolute() to test rel vs abs URL
            if (StringUtils.isNotEmpty(href) && !UrlRef.isAbsoluteUrl(href)) {
                //System.out.println("XXX: Relative iconStyle href: " + href);
                URI uri = getLink(parent, href);
                if (uri != null) {
                    href = uri.toString();
                    // store rewritten overlay URL back to property store
                    style.setIconUrl(href);
                }
            }
        }
    }

    /**
     * Recursively imports KML objects from all visited NetworkLinks starting
     * from the base KML document.  This must be called after reader is closed
     * otherwise an IllegalArgumentException will be thrown. <P>
     * <B>WARNING:</B> Use this method with caution. Loading a KML document
     * that is deeply nested like a super-overlay could load a large number
     * of KML NetworkLinks each with a large number of features. Use
     * {@link #setMaxLinkCount(int)} to restrict number of nested network links.
     * If limit exceeded then maxLinkCountExceeded will be set to <code>true</code>.
     *
     * @return list of visited networkLink URIs, empty list if
     * 			no reachable networkLinks are found, never null
     * @throws IllegalArgumentException if reader is still open
     */
    public List<IGISObject> importFromNetworkLinks() {
        return _importFromNetworkLinks(null);
    }

    /**
     * Recursively imports KML objects from all visited NetworkLinks starting
     * from the base KML document.  Callback is provided to process each feature
     * as the networkLinks are parsed.  This must be called after reader is closed
     * otherwise an IllegalArgumentException will be thrown. <P>
     * Use {@link #setMaxLinkCount(int)} to restrict number of nested network links.
     * If limit exceeded then maxLinkCountExceeded will be set to <code>true</code>.
     *
     * @param handler ImportEventHandler is called when each new GISObject is encountered
     * 			during parsing. This cannot be null.
     * @throws IllegalArgumentException if ImportEventHandler is null or
     * 			reader is still open when invoked
     */
    public void importFromNetworkLinks(ImportEventHandler handler) {
        if (handler == null) throw new IllegalArgumentException("handler cannot be null");
        _importFromNetworkLinks(handler);
    }

    /**
     * Recursively imports KML objects from all visited NetworkLinks starting
     * from the base KML document.  This must be called after reader is closed
     * otherwise an IllegalArgumentException will be thrown.
     * If limit exceeded then maxLinkCountExceeded will be set to <code>true</code>.
     *
     * @param handler ImportEventHandler is called when a new GISObject is parsed
     * @return list of visited networkLink URIs if no callback handler is specified,
     *      empty list if no reachable networkLinks are found or non-null call handler is provided
     * @throws IllegalArgumentException if reader is still opened
     */
    private List<IGISObject> _importFromNetworkLinks(ImportEventHandler handler) {
        if (iStream != null) throw new IllegalArgumentException("reader must first be closed");
        if (gisNetworkLinks.isEmpty()) return Collections.emptyList();
        List<IGISObject> linkedFeatures = new ArrayList<>();

        // keep track of URLs visited to prevent revisits
        Set<URI> visited = new HashSet<>();
        LinkedList<URI> networkLinks = new LinkedList<>();
        networkLinks.addAll(gisNetworkLinks);
        while (!networkLinks.isEmpty()) {
            URI uri = networkLinks.removeFirst();
            if (visited.add(uri)) {
                if (visited.size() > maxLinkCount) {
                    log.warn("Max NetworkLink count exceeded: max links=" + maxLinkCount);
                    maxLinkCountExceeded = true;
                    break;
                }
                InputStream is = null;
                try {
                    UrlRef ref = new UrlRef(uri);
                    // NOTE: if network link is a KML file with a .kmz extension or vice versa then it may fail.
                    // Determination also uses the HTTP mime type for the resource.
                    try {
                        is = ref.getInputStream(proxy);
                        if (is == null) continue;
                    } catch (FileNotFoundException nfe) {
                        // If href does not exist in KMZ then try with respect to parent context.
                        // Check if target exists outside of KMZ file in same context (file system or URL root).
                        // e.g. http://kml-samples.googlecode.com/svn/trunk/kml/kmz/networklink/hier.kmz
                        final URL tempUrl = new URL(ref.getURL(), ref.getKmzRelPath());
                        //log.info("XXX: tryURL\n\t{}", tempUrl); // debug
                        is = UrlRef.getInputStream(tempUrl, proxy);
                        if (is == null) continue;
                        ref = new UrlRef(tempUrl, null);
                    }
                    int oldSize = networkLinks.size();
                    int oldFeatSize = linkedFeatures.size();

                    log.debug("Parse networkLink: {}", ref);
                    try (KmlInputStream kis = new KmlInputStream(is)) {
                        IGISObject gisObj;
                        while ((gisObj = read(kis, ref, networkLinks)) != null) {
                            if (handler != null) {
                                if (!handler.handleEvent(ref, gisObj)) {
                                    // clear out temp list of links to abort following networkLinks
                                    log.info("Abort following networkLinks");
                                    networkLinks.clear();
                                    break;
                                }
                            } else
                                linkedFeatures.add(gisObj);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        if (oldFeatSize != linkedFeatures.size())
                            log.debug("*** got features from network link ***");
                        if (oldSize != networkLinks.size())
                            log.debug("*** got new URLs from network link ***");
                    }
                } catch (ConnectException | FileNotFoundException e) {
                    log.error("Failed to import from network link: " + uri + "\n" + e);
                    if (handler != null) handler.handleError(uri, e);
                } catch (Exception e) {
                    log.error("Failed to import from network link: " + uri, e);
                    if (handler != null) handler.handleError(uri, e);
                } finally {
                    IOUtils.closeQuietly(is);
                }
            }
        } // while

        return linkedFeatures;
    }

    /**
     * Short-cut help method to read all GISObjects closing the stream and returning
     * the list of GIS objects.  This is useful for most KML documents that can fit into memory
     * otherwise read() should be used directly to iterate over each object.
     *
     * @return list of objects
     * @throws IOException if an I/O error occurs
     */
    @NotNull
    public List<IGISObject> readAll() throws IOException {
        List<IGISObject> features = new ArrayList<>();
        try {
            IGISObject gisObj;
            while ((gisObj = read(kis, null, null)) != null) {
                features.add(gisObj);
            }
        } finally {
            close();
        }
        return features;
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * Once the reader has been closed, further read() invocations may throw an IOException.
     * Closing a previously closed reader has no effect.
     */
    public void close() {
        if (iStream != null) {
            kis.close();
            IOUtils.closeQuietly(iStream);
            iStream = null;
        }
    }

    /**
     * Set proxy through which URL connections will be made for network links.
     * If direct connection is desired,  {@code null} should be specified.
     * This proxy will be used if {@code importFromNetworkLinks()} is called.
     * @param proxy
     */
    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    /**
     * Get proxy through which URL connections will be made for network links.
     */
    public Proxy getProxy() {
        return proxy;
    }

    public boolean isRewriteStyleUrls() {
        return rewriteStyleUrls;
    }

    /**
     * Set flag to rewrite styleUrls from relative to absolute with respect
     * to its parent URL context. Otherwise may not be able to correctly resolve
     * relative links resulting features from multiple NetworkLinks with
     * different base URLs.
     * @param rewriteStyleUrls True to enable styleUrl rewriting
     */
    public void setRewriteStyleUrls(boolean rewriteStyleUrls) {
        this.rewriteStyleUrls = rewriteStyleUrls;
    }

    /**
     * Flag to ignore networkLinks if the Region is inactive/out-of-view
     * as determined by checking view with BBOX values in viewFormatLabel.
     * @see #setViewFormat(String, String)
     */
    public boolean isIgnoreInactiveRegionNetworkLinks() {
        return ignoreInactiveRegionNetworkLinks;
    }

    public void setIgnoreInactiveRegionNetworkLinks(boolean value) {
        this.ignoreInactiveRegionNetworkLinks = value;
    }

    /**
     * Returns number of features skipped including NetworkLinks that had regions
     * that were out of view. This is only applicable if {@link #isIgnoreInactiveRegionNetworkLinks}
     * returns a true value.
     * @return number of skipped features
     */
    public int getSkipCount() {
        return skipCount;
    }

    /**
     * ImportEventHandler interface used for callers to implement handling
     * of GISObjects encountered as NetworkLinks are parsed. If the callback
     * handleEvent() method returns false then recursion is aborted no more
     * NetworkLink features are processed.
     * <pre>
     * KmlReader reader = new KmlReader(new URL(
     *   "<a href="http://kml-samples.googlecode.com/svn/trunk/kml/NetworkLink/visibility.kml">...</a>"))
     * ... // read all features from reader
     * reader.close();
     * // reader stream must be closed (all features processed) before trying
     * // to import features from NetworkLinks.
     * reader.importFromNetworkLinks(
     *    new KmlReader.ImportEventHandler() {
     *          public boolean handleEvent(UrlRef ref, IGISObject gisObj)
     *       {
     *            // do something with gisObj
     *            return true;
     *       }
     *       public void handleError(URI uri, Exception e) {
     *           // optionally do something with exceptions
     *       }
     *    });</pre>
     *
     * @see KmlReader#importFromNetworkLinks(ImportEventHandler)
     */
    public interface ImportEventHandler {
        /**
         * The KmlReader will invoke this method for each GISObject encountered during parsing.
         * All elements will be reported in document order. Return false to abort importing
         * features from network links.
         *
         * @param ref UriRef for NetworkLink resource
         * @param gisObj new IGISObject object. This will never be null.
         * @return Return true to continue parsing and recursively follow NetworkLinks,
         *         false stops following NetworkLinks.
         */
        boolean handleEvent(UrlRef ref, IGISObject gisObj);

        /**
         * Error handler
         * @param uri URI for NetworkLink resource
         * @param e Exception thrown
         */
        void handleError(URI uri, Exception e);
    }

    @NotNull
    public Iterator<Schema> enumerateSchemata() throws IOException {
        throw new UnsupportedOperationException();
    }
}
