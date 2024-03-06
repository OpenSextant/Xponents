/****************************************************************************************
 *  UrlRef.java
 *
 *  Created: Dec 1, 2008
 *
 *  (C) Copyright MITRE Corporation 2006
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/

/*
 This class contains several slightly modified helper methods directly
 from the JDOM 1.1 Verifier.java source code clearly marked at the end
 of this class starting with isXMLNameCharacter().

 $Id: Verifier.java,v 1.55 2007/11/10 05:28:59 jhunter Exp $

Copyright (C) 2000-2007 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */
package org.opensextant.giscore.input.kml;


import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code UrlRef} manages the encoding/decoding of internally created
 * KML/KMZ URLs to preserve the association between the parent KMZ file
 * and its relative file reference. Handles getting an InputStream to KML
 * linked resources whether its a fully qualified URL or a entry in a KMZ file.
 * <p>
 * If {@code KmlReader} is used to read a KMZ resource then href values for
 * relative URLs will be rewritten as special URIs such that the links can be
 * fetched later using {@code UrlRef} to wrap the URI and return InputStream
 * to the resource. A UrlRef is created for each linked resource (e.g. NetworkLink,
 * GroundOverlay, ScreenOverlay, Style with IconStyle, etc.) during reading and
 * an internalized URI is used to reference the resource.  If the parent file/URL
 * is a KMZ file and link is a relative URL then the association is preserved
 * otherwise the URL is treated normally.
 * <p>
 * For example suppose we have a KMZ resource at <code><a href="http://server/test.kmz">...</a></code>
 * and its root KML document includes a supporting KML file through
 * a relative link {@code kml/include.kml}. The URI to this networkLink
 * is returned as {@code kmzhttp://server/test.kmz?file=kml/include.kml}.
 * UrlRef strips the "kmz" prefix and the "?file=" suffix from the URI resolving
 * the resource as having a parent URL as <code><a href="http://server/test.kmz">...</a></code>
 * and a relative link to the file as {@code kml/include.kml}.
 * <p>
 * Use {@code getURI()} to get the internal URI and {@code getUrl()}
 * to return the original URL.
 * <p>
 * Includes static validation methods with minor modifications from org.jdom.Verifier:
 * {@code isXMLNameCharacter()}, {@code isXMLNameStartCharacter()}, etc.
 *
 * @author Jason Mathews
 */
public final class UrlRef implements java.io.Serializable {

	private static final Logger log = LoggerFactory.getLogger(UrlRef.class);

	private static final long serialVersionUID = 1L;

	// private static final Logger log = LoggerFactory.getLogger(UrlRef.class);

	private final URI uri;
	private final URL url;
	private final String kmzRelPath;

	public static final String MIME_TYPE_KMZ = "application/vnd.google-earth.kmz";
	public static final String MIME_TYPE_KML = "application/vnd.google-earth.kml+xml";

	// Accept: application/vnd.google-earth.kml+xml, application/vnd.google-earth.kmz, image/*, */*
	private static final String ACCEPT_STRING = MIME_TYPE_KML + ", " + MIME_TYPE_KMZ + ", image/*, */*";

	// User-Agent:
	// GoogleEarth/5.2.1.1329(Windows;Microsoft Windows (5.1.2600.3);en-US;kml:2.2;client:Free;type:default)
	// GoogleEarth/5.2.1.1547(Windows;Microsoft Windows (5.1.2600.3);en-US;kml:2.2;client:Free;type:default)
	// GoogleEarth/6.2.1.6014(Windows;Microsoft Windows (6.1.7601.1);en;kml:2.2;client:Free;type:default)
	public static final String USER_AGENT = "GoogleEarth/5.2.1.1588(Windows;Microsoft Windows (5.1.2600.3);en-US;kml:2.2;client:Free;type:default)";

	/**
     * Pattern to match absolute URLs (e.g. <a href="http://host/file">...</a>, ftp://host/file, file:/path/file, etc.
     * Also matches <code>C:/path/file</code> which is not strictly a URL but it should
     * not be interpreted as a relative path either.
     */
	private static final Pattern absUrlPattern = Pattern.compile("^[a-zA-Z]+:/");

	private static SSLSocketFactory sslFactory;

	private static volatile boolean httpsInit;

	/**
	 * Convert URL to internalized "kmz" URI with absolute URL of parent KMZ and the kmz
	 * file path, which is the relative path to target file inside the KMZ. This allows
	 * access to the resources within the KMZ file.
	 *
	 * @param url		   URL for KML/KMZ resource, never <code>null</code>
	 * @param kmzFilePath  relative path within the parent KMZ archive to where the KML, overlay image,
	 *                      model, etc. is located. This is same reference as would be used
	 *                      in HREF element of NetworkLink/Link or GroundOverlay/Icon in KML.
	 *                      Use null if not referring to a sub-element within the KMZ file, which
	 *                      defaults to the root KML file.
	 * @throws URISyntaxException   if URL has a missing relative file path or fails to construct properly
	 * @throws NullPointerException if URL is null
	 */
	public UrlRef(URL url, String kmzFilePath) throws URISyntaxException {
		this.url = url;
		if (kmzFilePath == null) {
			this.uri = url.toURI();
			this.kmzRelPath = null; // defaults to the root KML file
			return;
		}
		String urlStr = url.toExternalForm();
		// cleanup bad paths if needed
		if (kmzFilePath.startsWith("/"))
			kmzFilePath = kmzFilePath.substring(1);
		if (kmzFilePath.startsWith("./"))
			kmzFilePath = kmzFilePath.substring(2);
		while (kmzFilePath.startsWith("../"))
			kmzFilePath = kmzFilePath.substring(3);
		if (kmzFilePath.isEmpty())
			throw new URISyntaxException(urlStr, "Missing relative file path");
		StringBuilder buf = new StringBuilder();
		// append kmz to front of the URL to mark as a special URI
		buf.append("kmz").append(urlStr);
		//System.out.println("path="+url.getPath());
		//System.out.println("file="+url.getFile());
		//System.out.println("query="+url.getQuery());
		if (url.getQuery() == null)
			buf.append('?');
		else
			buf.append('&');
		// append target file to URI as query part
		buf.append("file=").append(kmzFilePath);
		this.uri = new URI(buf.toString());
		this.kmzRelPath = kmzFilePath;
	}

	/**
	 * Wrap URI with URLRef and decode URI if its an internal
	 * kmz reference denoted with a "kmz" prefix to the absolute URI
	 * (e.g. kmzfile:/C:/projects/giscore/data/kml/kmz/dir/content.kmz?file=kml/hi.kml).
	 * Non-internal kmz URIs will be treated as normal URLs.
	 *
	 * @param uri URI for KML/KMZ resource, never <code>null</code>
	 * @throws MalformedURLException If a protocol handler for the URL could not be found,
	 *                               if uri is not absolute, or if some other error occurred while constructing the URL
	 * @throws NullPointerException  if uri is <code>null</code>
	 */
	public UrlRef(URI uri) throws MalformedURLException {
		this.uri = uri;
		String urlStr = uri.toString();
		if (!urlStr.startsWith("kmz")) {
			// if uri is not absolute then URI.toURL() throws IllegalArgumentException
			if (!uri.isAbsolute()) throw new MalformedURLException("URI is not absolute");
			URL urlPart;
			try {
				urlPart = uri.toURL();
			} catch(MalformedURLException e) {
				// sometimes URLs in KML are expressed as absolute file paths (e.g. C:/path/file.kml)
				File file = new File(uri.toString());
				if (file.isFile()) {
					urlPart = file.toURI().toURL();
					log.warn("Absolute file path in URL: {}", urlPart);
				} else {
					throw e;
				}
			}
			url = urlPart;
			kmzRelPath = null;
			return;
		}

		// handle special KMZ-encoded URI
		StringBuilder buf = new StringBuilder();
		int ind = urlStr.lastIndexOf("file=");
		// if ind == -1 then not well-formed KMZ URI
		if (ind <= 0) throw new MalformedURLException("Invalid KMZ URI missing file parameter");
		buf.append(urlStr, 3, ind - 1);
		// System.out.println("\trestored kmz_rel_path=" + urlStr.substring(ind + 5));
		url = new URL(buf.toString());
		kmzRelPath = urlStr.substring(ind + 5);
	}

	/**
	 * Determines if a UrlRef references a linked reference (networked linked KML,
	 * image overlay, icon, model, etc.) in a KMZ file.
	 *
	 * @return true if UrlRef reprensents a linked reference in a KMZ file
	 */
	public boolean isKmz() {
		return kmzRelPath != null;
	}

	/**
	 * Opens a connection to this {@code UrlRef} and returns an
	 * {@code InputStream} for reading from that connection.
	 *
	 * @return an input stream for reading from the resource represented by the {@code UrlRef}.
	 * @throws FileNotFoundException if referenced link was not found in the parent KMZ resource
	 * @throws IOException		   if an I/O error occurs
	 */
	public InputStream getInputStream() throws IOException {
		return getInputStream((Proxy) null);
	}

	/**
	 * Opens a connection to this {@code UrlRef} and returns an
	 * {@code InputStream} for reading from that connection.
	 *
	 * @param proxy the Proxy through which this connection
	 *              will be made. If direct connection is desired,
	 *              {@code null} should be specified.
	 * @return an input stream for reading from the resource represented by the {@code UrlRef}.
	 * @throws FileNotFoundException if referenced link was not found in the parent KMZ resource
	 * @throws IOException		   if an I/O error occurs
	 */
	public InputStream getInputStream(Proxy proxy) throws IOException {
		// check if non-KMZ URI
		if (kmzRelPath == null)
			return getInputStream(url, proxy);

		String kmzPath = kmzRelPath;
		// if whitespace appears in networkLink URLs then it's commonly escaped to %20
		// so need to convert back to spaces to match exactly how it is stored in KMZ file
		final boolean isEscaped = kmzPath.contains("%20");
		if (isEscaped) {
			kmzPath = kmzPath.replace("%20", " "); // unescape all escaped whitespace chars
		}
		URLConnection conn = getConnection(url, proxy);
		ZipInputStream zis = new ZipInputStream(conn.getInputStream());
		boolean closeOnExit = true;
		try {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				if (isEscaped)
					name = name.replace("%20", " "); // unescape all escaped whitespace chars
				// find matching KML file in archive
				if (kmzPath.equals(name)) {
					closeOnExit = false;
					return zis;
				}
			}
		} finally {
			// must close ZipInputStream if failed to find entry
			if (closeOnExit)
				IOUtils.closeQuietly(zis);
		}
		// If href does not exist in KMZ then try with respect to parent context.
		// check if target exists outside of KMZ file in same context (file system or URL root).
		// e.g. http://kml-samples.googlecode.com/svn/trunk/kml/kmz/networklink/hier.kmz
		/*
		try {
			return getInputStream(new URL(url, kmzRelPath), proxy);
		} catch (IOException ioe) {
			// attempt to find target at same context of parent failed
		}
		*/
		throw new FileNotFoundException("Relative URL not found in KMZ: " + kmzPath);
	}

	/**
	 * This method gets the correct input stream for a URL.  Attempts to
	 * determine if URL is a KMZ (compressed KML file) first by the returned
	 * content type from the {@code URLConnection} and it that fails then
	 * by checking if a .kmz extension appears at end of the file name.
	 * If stream is for a KMZ file then the stream is advanced until the first
	 * KML file is found in the stream.
	 *
	 * @param url The url to the KML or KMZ file
	 * @return The InputStream used to read the KML source.
	 * @throws java.io.IOException when an I/O error prevents a document
	 *                             from being fully parsed.
	 */
	public static InputStream getInputStream(URL url) throws IOException {
		return getInputStream(url, null);
	}

	/**
	 * This method gets the correct input stream for a URL.  Attempts to
	 * determine if URL is a KMZ (compressed KML file) first by the returned
	 * content type from the {@code URLConnection} and it that fails then
	 * by checking if a .kmz extension appears at end of the file name.
	 * If stream is for a KMZ file then the stream is advanced until the first
	 * KML file is found in the stream.
	 *
	 * @param url   The url to the KML or KMZ file, never <code>null</code>
	 * @param proxy the Proxy through which this connection
	 *              will be made. If direct connection is desired,
	 *              {@code null} should be specified.
	 * @return The InputStream used to read the KML source.
	 * @throws FileNotFoundException	if a root KML file is not found in the KMZ resource
	 * @throws IOException			  if an I/O error has occurred
	 * @throws IllegalArgumentException if content size is &lt;= 0
	 * @throws NullPointerException	 if url is <code>null</code>
	 */
	public static InputStream getInputStream(URL url, Proxy proxy) throws IOException {
		// Open the connection
		URLConnection conn = getConnection(url, proxy);

		// Note: just looking at file extension may not be enough to indicate its KMZ vs KML (misnamed, etc.)
		// proper way might be to use PushbackInputStream and check first characters of stream.
		// KMZ/ZIP header should be PK\003\004
		// In rare occasions a KML file ends with ".kmz" file extension -- Google Earth allows this.
		String contentType = conn.getContentType();
		// contentType could end with mime parameters (e.g. application/vnd.google-earth.kmz; encoding=...)
		if (contentType != null && contentType.startsWith(MIME_TYPE_KMZ) || url.getPath().toLowerCase().endsWith(".kmz")) {
			// kmz file requires special handling
			boolean closeOnExit = true;
			InputStream is = null;
			ZipInputStream zis = null;
			boolean hasEntries = false;
			try {
				is = conn.getInputStream();
				zis = new ZipInputStream(is);
				ZipEntry entry;
				//   Simply find first kml file in the archive.
				//
				//   Note that KML documentation loosely defines that it takes first root-level KML file
				//   in KMZ archive as the main KML document but Google Earth (version 4.3 as of Dec-2008)
				//   actually takes the first kml file regardless of name (e.g. doc.kml which is convention only)
				//   and whether its in the root folder or subfolder. Otherwise would need to keep track
				//   of the first KML found but continue if first KML file is not in the root level then
				//   backtrack in stream to first KML if no root-level KML is found.
				while ((entry = zis.getNextEntry()) != null) {
					hasEntries = true;
					// find first KML file in archive
					if (entry.getName().toLowerCase().endsWith(".kml")) {
						closeOnExit = false;
						return zis; // start reading from stream
					}
				}
				if (hasEntries) {
					// valid ZIP stream found but only with non-kml entries
					throw new FileNotFoundException("Failed to find KML content in KMZ file: " + url);
				}
				// otherwise if stream is mis-categorized and not KMZ then may want to try again as raw KML bytes...
			} catch (ZipException ze) {
				// if hasEntries true then stream is KMZ (ZIP) but ran into a fatal problem
				if (hasEntries) throw ze;
				// otherwise if stream is mis-categorized and not KMZ then may want to try again as raw KML bytes...
			} finally {
				if (closeOnExit) {
					IOUtils.closeQuietly(zis);
					IOUtils.closeQuietly(is);
				}
			}
			// if here then did not find any ZipEntries so can probably assume stream is mis-categorized
			// and not KMZ so try again as raw KML bytes...
			conn = getConnection(url, proxy);
			//return new BufferedInputStream(conn.getInputStream());
		}

		// Else read the raw bytes.
		return new BufferedInputStream(conn.getInputStream());
		// TODO: if resource mis-categorized and really KMZ then may want strategy to re-try as ZipInputStream
	}

	private static URLConnection getConnection(URL url, Proxy proxy) throws IOException {
		URLConnection conn = proxy == null ? url.openConnection() : url.openConnection(proxy);

		// Set HTTP headers to emulate a typical Google Earth client
		//
		// Examples:
		//
		//  Accept: application/vnd.google-earth.kml+xml, application/vnd.google-earth.kmz, image/*, */*
		//  Cache-Control: no-cache
		//  User-Agent: GoogleEarth/5.0.11337.1968(Windows;Microsoft Windows XP (Service Pack 3);en-US;kml:2.2;client:Free;type:default)
		//
		//  Accept: application/vnd.google-earth.kml+xml, application/vnd.google-earth.kmz, image/*, */*
		//  Cache-Control: no-cache
		//  User-Agent: GoogleEarth/4.3.7284.3916(Windows;Microsoft Windows XP (Service Pack 3);en-US;kml:2.2;client:Free;type:default)
		//
		//  Accept-Charset: iso-8859-1,*,utf-8
		//  Accept-Encoding: gzip,deflate
		//  Accept-Language: en-us,en,*
		if (conn instanceof HttpURLConnection) {
			HttpURLConnection httpConn = (HttpURLConnection) conn;
			httpConn.setRequestProperty("Accept", ACCEPT_STRING);
			httpConn.setRequestProperty("User-Agent", USER_AGENT);
			if (httpConn instanceof HttpsURLConnection) {
				HttpsURLConnection conn1 = (HttpsURLConnection) httpConn;
				conn1.setHostnameVerifier(new HostnameVerifier() {
					public boolean verify(String hostname, SSLSession session) {
						return true;
					}
				});
				setDefaultSSLSocketFactory(conn1);
			}
		}

		// Connect to get the response headers
		conn.connect();
		return conn;
	}

	/**
	 * @return the internal URI of the UrlRef, never {@code null}
	 */
	@NotNull
	public URI getURI() {
		return uri;
	}

	/**
     * Returns original external URL. If "normal" URL then
     * URL will be returned same as the URI. If internal "kmz"
     * URI (e.g. kmzhttp://server/test.kmz?file=kml/include.kml)
     * then URL returned is <code><a href="http://server/test.kmz">...</a></code>.
     *
     * @return original external URL, never {@code null}
     */
	@NotNull
	public URL getURL() {
		return url;
	}

	/**
	 * Gets the relative path to the KMZ resource if UrlRef represents
	 * a linked reference (networked linked KML, image overlay, icon, model,
	 * etc.) in a KMZ file.  For example this would be how the Link href was
	 * explicitly defined in a NetworkLink, IconStyle, or GroundOverlay.
	 *
	 * @return relative path to the KMZ resource otherwise {@code null}
	 */
	// @CheckForNull

	public String getKmzRelPath() {
		return kmzRelPath;
	}

	public int hashCode() {
		return uri.hashCode();
	}

	public boolean equals(Object other) {
		if (other == null) return false;
		if (this == other) return true;
		if (getClass() != other.getClass())
			return false;
		return uri.equals(((UrlRef)other).uri);
	}
	/**
	 * Normalize and convert internal "URI" form to portable URL form.
	 * For example {@code kmzfile:/C:/giscore/data/kml/content.kmz?file=kml/hi.kml}
	 * is converted into {@code file:/C:/giscore/data/kml/content.kmz/kml/hi.kml}.
	 * Non-file URIs only strip the kmz prefix and keep file= parameter.
	 *
	 * @return portable human-readable URL as formatted String
	 */
	public String toString() {
		String s = uri.toString();
		// skip over kmz prefix in URLs for human-readable output
		// kmz prefix is for internal use only
		if (s.startsWith("kmz")) {
			s = s.substring(3);
			// at end have either have ?file= or &file=
			// rewrite if file: protocol
			//
			// normalized example:
			// internal representation:
			// 	file:/C:/projects/giscore/data/kml/kmz/dir/content.kmz?file=kml/hi.kml
			// returned string representation:
			// 	file:/C:/projects/giscore/data/kml/kmz/dir/content.kmz/kml/hi.kml
			if ("kmzfile".equals(uri.getScheme())) {
				int ind = s.lastIndexOf("file=");
				if (ind > 0) {
					char ch = s.charAt(--ind);
					if (ch == '?' || ch == '&')
						s = s.substring(0, ind) + "/" + s.substring(ind + 6);
				}
			}
		}
		return s;
	}

	/**
     * Quick test if href is an absolute URL. This means
     * either the string starts with "file:" or matches the regular expression ^[a-zA-Z]+://
     * <BR>For example, the following match true:
     * <ul>
     * <li> <a href="http://mw1.google.com/gigapxl/r1_c0.jpg">...</a>
     * <li> file:C:/path/test.kml
     * <li> file:/C:/path/test.kml
     * </ul>
     *
     * @param href URL to test
     * @return true if URL appears to be an absolute URL
     */
	public static boolean isAbsoluteUrl(String href) {
		return href.startsWith("file:") || absUrlPattern.matcher(href).lookingAt();
	}

	/**
     * Escape invalid characters in URI string.
     * Must escape [] and whitespace characters
     * (e.g. <a href="http://mw1.google.com/mw-earth-vectordb/kml-samples/gp/seattle/gigapxl/$">...</a>[level]/r$[y]_c$[x].jpg)
     * which would throw an URISyntaxException if URI is created from this URI string.
     *
     * @param href The string to be parsed into a URI
     * @return escaped URI string
     */
	public static String escapeUri(String href) {
		/*
		   URI-reference = [ absoluteURI | relativeURI ] [ "#" fragment

		   excluded characters from URI syntax:

		   control     = <US-ASCII coded characters 00-1F and 7F hexadecimal>
		   space       = <US-ASCII coded character 20 hexadecimal>
		   delims      = "<" | ">" | "#" | "%" | <">

		   Other characters are excluded because gateways and other transport
		   agents are known to sometimes modify such characters, or they are
		   used as delimiters.

		   unwise      = "{" | "}" | "|" | "\" | "^" | "[" | "]" | "`"

		   Data corresponding to excluded characters must be escaped in order to
		   be properly represented within a URI.

		   Note within a query component, these characters are reserved:
			";" | "/" | "?" | ":" | "@" | "&" | "=" | "+" | "," | "$"

		   http://www.ietf.org/rfc/rfc2396.txt
		 */
		StringBuilder buf = new StringBuilder(href.length());
		for (char c : href.toCharArray()) {
			if (c <= 0x20 || c >= 0x7f)
				buf.append('%').append(String.format("%02X", (int) c));
			else
				switch (c) {
					// case ' ': // %20
					// excluded delim characters from URI syntax
					case '"': // %22
					case '<':
					case '>':
						// unwise characters
					case '{':
					case '}':
					case '|': // %7C: otherwise java.net.URISyntaxException: Illegal character
					case '\\':
					case '^':
					case '[':
					case ']':
						// NOTE: if URL is IPv6 format then may not want to encode brackets if enclosing IPv6 host address
						// e.g. (http://[1080:0:0:0:8:800:200C:417A]/index.html)
					case '`':
						buf.append('%').append(String.format("%02X", (int) c));
						// note '#" is allowed in URI construction only once
						// if '%' appears it must be followed by 2 hex-decimal chars
						break;
					default:
						// characters e.g. A-Za-z0-9_-+:?/ are not encoded
						buf.append(c);
				}
		}
		String newVal = buf.toString();
		if (log.isDebugEnabled() && newVal.length() != href.length())
			log.debug("Escaped illegal characters in URL: " + href);
		return newVal;
	}

	/**
	 * Test for relative identifier. True if string matches the set
	 * of strings for NCName production in [Namespaces in XML].
	 * Useful to test if target is reference to identifier in KML document
	 * (e.g. StyleMap referencing local identifier of a Style).
	 *
	 * @param str the String to check, may be null
	 * @return true if string matches an XML identifier reference
	 */
	public static boolean isIdentifier(String str) {
		return isIdentifier(str, false);
	}

	/**
	 * Test for relative identifier. True if string matches the set
	 * of strings for NCName production in [Namespaces in XML].
	 * Useful to test if target is reference to identifier in KML document
	 * (e.g. StyleMap referencing local identifier of a Style).
	 *
	 * @param str			 the String to check, may be null
	 * @param allowWhitespace Flag to allow whitespace in identifier other than first character
	 *                        Whitespace isn't allowed by the spec but often appears in many public KML documents
	 *                        (this is generated and allowed by Google Earth) so allow relaxing the rules.
	 * @return true if string matches an XML identifier reference
	 */
	public static boolean isIdentifier(String str, boolean allowWhitespace) {
		/*
				 * check if string matches NCName production
				 *  NCName ::=  (Letter | '_') (NCNameChar)*  -- An XML Name, minus the ":"
				 *  NCNameChar ::=  Letter | Digit | '.' | '-' | '_' | CombiningChar | Extender
				 */
		// Cannot be empty or null
		if (str == null) {
			return false;
		}
		int len = str.length();
		if (len == 0) return false;

		// First char must be Letter or '_'
		char c = str.charAt(0);
		if (!isXMLNameStartCharacter(c)) return false;

		for (int i = 1; i < len; i++) {
			c = str.charAt(i);
			if (c == '%') {
				// check for URI escaping mechanism (% HH, where HH is the hexadecimal notation of the byte value).
				// next 2 characters must be hexadecimal digits
				if (i + 2 >= len) {
					// System.out.format(" XX: bad i=%d len=%d%n",i,len);
					return false;
				}
				if (!isHexDigit(str.charAt(++i)) || !isHexDigit(str.charAt(++i))) {
					//System.out.println(" not hexdecimal");
					return false;
				}
			} else if (!isXMLNameCharacter(c) && (!allowWhitespace || c != ' ')) {
				// if (c != '.' && c != '-' && c != '_' && !Character.isLetterOrDigit(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Determines if the specified character is a hexadecimal digit [0-9,a-f,A-F].
	 *
	 * @param ch the character to be tested.
	 * @return {@code true} if the character is a hexadecimal digit;
	 *         {@code false} otherwise.
	 */
	public static boolean isHexDigit(char ch) {
		// digit(ch,16) == -1 then no hexadecimal character [0-9,a-f,A-F]
		return Character.digit(ch, 16) >= 0;
	}

	//////////////////////////////////////////////////////
	// util functions below copied from org.jdom.Verifier source.
	//
	// A utility class to handle well-formedness checks on names, data, and other
	// verification tasks for JDOM.
	//////////////////////////////////////////////////////

	/**
	 * This is a utility function for determining whether a specified
	 * character is a name character according to production 4 of the
	 * XML 1.0 specification.
	 *
	 * @param c {@code char} to check for XML name compliance.
	 * @return {@code boolean} true if it's a name character,
	 *         false otherwise.
	 */
	public static boolean isXMLNameCharacter(char c) {

		return (isXMLLetter(c) || isXMLDigit(c) || c == '.' || c == '-'
				|| c == '_' || /* c == ':' || */ isXMLCombiningChar(c)
				|| isXMLExtender(c));
	}

	/**
	 * This is a utility function for determining whether a specified
	 * character is a legal name start character according to production 5
	 * of the XML 1.0 specification.
	 *
	 * @param c {@code char} to check for XML name start compliance.
	 * @return {@code boolean} true if it's a name start character,
	 *         false otherwise.
	 */
	public static boolean isXMLNameStartCharacter(char c) {

		// NOTE: JDOM implementation has this production allowing names to begin with
		// colons which is Namespaces in XML Recommendation disallows.

		return (isXMLLetter(c) || c == '_' /* || c ==':' */);

	}

	/**
	 * This is a utility function for determining whether a specified character
	 * is a letter according to production 84 of the XML 1.0 specification.
	 *
	 * @param c {@code char} to check for XML name compliance.
	 * @return {@code String} true if it's a letter, false otherwise.
	 */
	public static boolean isXMLLetter(char c) {
		// Note that order is very important here.  The search proceeds
		// from lowest to highest values, so that no searching occurs
		// above the character's value.  BTW, the first line is equivalent to:
		// if (c >= 0x0041 && c <= 0x005A) return true;

		if (c < 0x0041) return false;
		if (c <= 0x005a) return true;
		if (c < 0x0061) return false;
		if (c <= 0x007A) return true;
		if (c < 0x00C0) return false;
		if (c <= 0x00D6) return true;
		if (c < 0x00D8) return false;
		if (c <= 0x00F6) return true;
		if (c < 0x00F8) return false;
		if (c <= 0x00FF) return true;
		if (c < 0x0100) return false;
		if (c <= 0x0131) return true;
		if (c < 0x0134) return false;
		if (c <= 0x013E) return true;
		if (c < 0x0141) return false;
		if (c <= 0x0148) return true;
		if (c < 0x014A) return false;
		if (c <= 0x017E) return true;
		if (c < 0x0180) return false;
		if (c <= 0x01C3) return true;
		if (c < 0x01CD) return false;
		if (c <= 0x01F0) return true;
		if (c < 0x01F4) return false;
		if (c <= 0x01F5) return true;
		if (c < 0x01FA) return false;
		if (c <= 0x0217) return true;
		if (c < 0x0250) return false;
		if (c <= 0x02A8) return true;
		if (c < 0x02BB) return false;
		if (c <= 0x02C1) return true;
		if (c == 0x0386) return true;
		if (c < 0x0388) return false;
		if (c <= 0x038A) return true;
		if (c == 0x038C) return true;
		if (c < 0x038E) return false;
		if (c <= 0x03A1) return true;
		if (c < 0x03A3) return false;
		if (c <= 0x03CE) return true;
		if (c < 0x03D0) return false;
		if (c <= 0x03D6) return true;
		if (c == 0x03DA) return true;
		if (c == 0x03DC) return true;
		if (c == 0x03DE) return true;
		if (c == 0x03E0) return true;
		if (c < 0x03E2) return false;
		if (c <= 0x03F3) return true;
		if (c < 0x0401) return false;
		if (c <= 0x040C) return true;
		if (c < 0x040E) return false;
		if (c <= 0x044F) return true;
		if (c < 0x0451) return false;
		if (c <= 0x045C) return true;
		if (c < 0x045E) return false;
		if (c <= 0x0481) return true;
		if (c < 0x0490) return false;
		if (c <= 0x04C4) return true;
		if (c < 0x04C7) return false;
		if (c <= 0x04C8) return true;
		if (c < 0x04CB) return false;
		if (c <= 0x04CC) return true;
		if (c < 0x04D0) return false;
		if (c <= 0x04EB) return true;
		if (c < 0x04EE) return false;
		if (c <= 0x04F5) return true;
		if (c < 0x04F8) return false;
		if (c <= 0x04F9) return true;
		if (c < 0x0531) return false;
		if (c <= 0x0556) return true;
		if (c == 0x0559) return true;
		if (c < 0x0561) return false;
		if (c <= 0x0586) return true;
		if (c < 0x05D0) return false;
		if (c <= 0x05EA) return true;
		if (c < 0x05F0) return false;
		if (c <= 0x05F2) return true;
		if (c < 0x0621) return false;
		if (c <= 0x063A) return true;
		if (c < 0x0641) return false;
		if (c <= 0x064A) return true;
		if (c < 0x0671) return false;
		if (c <= 0x06B7) return true;
		if (c < 0x06BA) return false;
		if (c <= 0x06BE) return true;
		if (c < 0x06C0) return false;
		if (c <= 0x06CE) return true;
		if (c < 0x06D0) return false;
		if (c <= 0x06D3) return true;
		if (c == 0x06D5) return true;
		if (c < 0x06E5) return false;
		if (c <= 0x06E6) return true;
		if (c < 0x0905) return false;
		if (c <= 0x0939) return true;
		if (c == 0x093D) return true;
		if (c < 0x0958) return false;
		if (c <= 0x0961) return true;
		if (c < 0x0985) return false;
		if (c <= 0x098C) return true;
		if (c < 0x098F) return false;
		if (c <= 0x0990) return true;
		if (c < 0x0993) return false;
		if (c <= 0x09A8) return true;
		if (c < 0x09AA) return false;
		if (c <= 0x09B0) return true;
		if (c == 0x09B2) return true;
		if (c < 0x09B6) return false;
		if (c <= 0x09B9) return true;
		if (c < 0x09DC) return false;
		if (c <= 0x09DD) return true;
		if (c < 0x09DF) return false;
		if (c <= 0x09E1) return true;
		if (c < 0x09F0) return false;
		if (c <= 0x09F1) return true;
		if (c < 0x0A05) return false;
		if (c <= 0x0A0A) return true;
		if (c < 0x0A0F) return false;
		if (c <= 0x0A10) return true;
		if (c < 0x0A13) return false;
		if (c <= 0x0A28) return true;
		if (c < 0x0A2A) return false;
		if (c <= 0x0A30) return true;
		if (c < 0x0A32) return false;
		if (c <= 0x0A33) return true;
		if (c < 0x0A35) return false;
		if (c <= 0x0A36) return true;
		if (c < 0x0A38) return false;
		if (c <= 0x0A39) return true;
		if (c < 0x0A59) return false;
		if (c <= 0x0A5C) return true;
		if (c == 0x0A5E) return true;
		if (c < 0x0A72) return false;
		if (c <= 0x0A74) return true;
		if (c < 0x0A85) return false;
		if (c <= 0x0A8B) return true;
		if (c == 0x0A8D) return true;
		if (c < 0x0A8F) return false;
		if (c <= 0x0A91) return true;
		if (c < 0x0A93) return false;
		if (c <= 0x0AA8) return true;
		if (c < 0x0AAA) return false;
		if (c <= 0x0AB0) return true;
		if (c < 0x0AB2) return false;
		if (c <= 0x0AB3) return true;
		if (c < 0x0AB5) return false;
		if (c <= 0x0AB9) return true;
		if (c == 0x0ABD) return true;
		if (c == 0x0AE0) return true;
		if (c < 0x0B05) return false;
		if (c <= 0x0B0C) return true;
		if (c < 0x0B0F) return false;
		if (c <= 0x0B10) return true;
		if (c < 0x0B13) return false;
		if (c <= 0x0B28) return true;
		if (c < 0x0B2A) return false;
		if (c <= 0x0B30) return true;
		if (c < 0x0B32) return false;
		if (c <= 0x0B33) return true;
		if (c < 0x0B36) return false;
		if (c <= 0x0B39) return true;
		if (c == 0x0B3D) return true;
		if (c < 0x0B5C) return false;
		if (c <= 0x0B5D) return true;
		if (c < 0x0B5F) return false;
		if (c <= 0x0B61) return true;
		if (c < 0x0B85) return false;
		if (c <= 0x0B8A) return true;
		if (c < 0x0B8E) return false;
		if (c <= 0x0B90) return true;
		if (c < 0x0B92) return false;
		if (c <= 0x0B95) return true;
		if (c < 0x0B99) return false;
		if (c <= 0x0B9A) return true;
		if (c == 0x0B9C) return true;
		if (c < 0x0B9E) return false;
		if (c <= 0x0B9F) return true;
		if (c < 0x0BA3) return false;
		if (c <= 0x0BA4) return true;
		if (c < 0x0BA8) return false;
		if (c <= 0x0BAA) return true;
		if (c < 0x0BAE) return false;
		if (c <= 0x0BB5) return true;
		if (c < 0x0BB7) return false;
		if (c <= 0x0BB9) return true;
		if (c < 0x0C05) return false;
		if (c <= 0x0C0C) return true;
		if (c < 0x0C0E) return false;
		if (c <= 0x0C10) return true;
		if (c < 0x0C12) return false;
		if (c <= 0x0C28) return true;
		if (c < 0x0C2A) return false;
		if (c <= 0x0C33) return true;
		if (c < 0x0C35) return false;
		if (c <= 0x0C39) return true;
		if (c < 0x0C60) return false;
		if (c <= 0x0C61) return true;
		if (c < 0x0C85) return false;
		if (c <= 0x0C8C) return true;
		if (c < 0x0C8E) return false;
		if (c <= 0x0C90) return true;
		if (c < 0x0C92) return false;
		if (c <= 0x0CA8) return true;
		if (c < 0x0CAA) return false;
		if (c <= 0x0CB3) return true;
		if (c < 0x0CB5) return false;
		if (c <= 0x0CB9) return true;
		if (c == 0x0CDE) return true;
		if (c < 0x0CE0) return false;
		if (c <= 0x0CE1) return true;
		if (c < 0x0D05) return false;
		if (c <= 0x0D0C) return true;
		if (c < 0x0D0E) return false;
		if (c <= 0x0D10) return true;
		if (c < 0x0D12) return false;
		if (c <= 0x0D28) return true;
		if (c < 0x0D2A) return false;
		if (c <= 0x0D39) return true;
		if (c < 0x0D60) return false;
		if (c <= 0x0D61) return true;
		if (c < 0x0E01) return false;
		if (c <= 0x0E2E) return true;
		if (c == 0x0E30) return true;
		if (c < 0x0E32) return false;
		if (c <= 0x0E33) return true;
		if (c < 0x0E40) return false;
		if (c <= 0x0E45) return true;
		if (c < 0x0E81) return false;
		if (c <= 0x0E82) return true;
		if (c == 0x0E84) return true;
		if (c < 0x0E87) return false;
		if (c <= 0x0E88) return true;
		if (c == 0x0E8A) return true;
		if (c == 0x0E8D) return true;
		if (c < 0x0E94) return false;
		if (c <= 0x0E97) return true;
		if (c < 0x0E99) return false;
		if (c <= 0x0E9F) return true;
		if (c < 0x0EA1) return false;
		if (c <= 0x0EA3) return true;
		if (c == 0x0EA5) return true;
		if (c == 0x0EA7) return true;
		if (c < 0x0EAA) return false;
		if (c <= 0x0EAB) return true;
		if (c < 0x0EAD) return false;
		if (c <= 0x0EAE) return true;
		if (c == 0x0EB0) return true;
		if (c < 0x0EB2) return false;
		if (c <= 0x0EB3) return true;
		if (c == 0x0EBD) return true;
		if (c < 0x0EC0) return false;
		if (c <= 0x0EC4) return true;
		if (c < 0x0F40) return false;
		if (c <= 0x0F47) return true;
		if (c < 0x0F49) return false;
		if (c <= 0x0F69) return true;
		if (c < 0x10A0) return false;
		if (c <= 0x10C5) return true;
		if (c < 0x10D0) return false;
		if (c <= 0x10F6) return true;
		if (c == 0x1100) return true;
		if (c < 0x1102) return false;
		if (c <= 0x1103) return true;
		if (c < 0x1105) return false;
		if (c <= 0x1107) return true;
		if (c == 0x1109) return true;
		if (c < 0x110B) return false;
		if (c <= 0x110C) return true;
		if (c < 0x110E) return false;
		if (c <= 0x1112) return true;
		if (c == 0x113C) return true;
		if (c == 0x113E) return true;
		if (c == 0x1140) return true;
		if (c == 0x114C) return true;
		if (c == 0x114E) return true;
		if (c == 0x1150) return true;
		if (c < 0x1154) return false;
		if (c <= 0x1155) return true;
		if (c == 0x1159) return true;
		if (c < 0x115F) return false;
		if (c <= 0x1161) return true;
		if (c == 0x1163) return true;
		if (c == 0x1165) return true;
		if (c == 0x1167) return true;
		if (c == 0x1169) return true;
		if (c < 0x116D) return false;
		if (c <= 0x116E) return true;
		if (c < 0x1172) return false;
		if (c <= 0x1173) return true;
		if (c == 0x1175) return true;
		if (c == 0x119E) return true;
		if (c == 0x11A8) return true;
		if (c == 0x11AB) return true;
		if (c < 0x11AE) return false;
		if (c <= 0x11AF) return true;
		if (c < 0x11B7) return false;
		if (c <= 0x11B8) return true;
		if (c == 0x11BA) return true;
		if (c < 0x11BC) return false;
		if (c <= 0x11C2) return true;
		if (c == 0x11EB) return true;
		if (c == 0x11F0) return true;
		if (c == 0x11F9) return true;
		if (c < 0x1E00) return false;
		if (c <= 0x1E9B) return true;
		if (c < 0x1EA0) return false;
		if (c <= 0x1EF9) return true;
		if (c < 0x1F00) return false;
		if (c <= 0x1F15) return true;
		if (c < 0x1F18) return false;
		if (c <= 0x1F1D) return true;
		if (c < 0x1F20) return false;
		if (c <= 0x1F45) return true;
		if (c < 0x1F48) return false;
		if (c <= 0x1F4D) return true;
		if (c < 0x1F50) return false;
		if (c <= 0x1F57) return true;
		if (c == 0x1F59) return true;
		if (c == 0x1F5B) return true;
		if (c == 0x1F5D) return true;
		if (c < 0x1F5F) return false;
		if (c <= 0x1F7D) return true;
		if (c < 0x1F80) return false;
		if (c <= 0x1FB4) return true;
		if (c < 0x1FB6) return false;
		if (c <= 0x1FBC) return true;
		if (c == 0x1FBE) return true;
		if (c < 0x1FC2) return false;
		if (c <= 0x1FC4) return true;
		if (c < 0x1FC6) return false;
		if (c <= 0x1FCC) return true;
		if (c < 0x1FD0) return false;
		if (c <= 0x1FD3) return true;
		if (c < 0x1FD6) return false;
		if (c <= 0x1FDB) return true;
		if (c < 0x1FE0) return false;
		if (c <= 0x1FEC) return true;
		if (c < 0x1FF2) return false;
		if (c <= 0x1FF4) return true;
		if (c < 0x1FF6) return false;
		if (c <= 0x1FFC) return true;
		if (c == 0x2126) return true;
		if (c < 0x212A) return false;
		if (c <= 0x212B) return true;
		if (c == 0x212E) return true;
		if (c < 0x2180) return false;
		if (c <= 0x2182) return true;
		if (c == 0x3007) return true;						  // ideographic
		if (c < 0x3021) return false;
		if (c <= 0x3029) return true;  // ideo
		if (c < 0x3041) return false;
		if (c <= 0x3094) return true;
		if (c < 0x30A1) return false;
		if (c <= 0x30FA) return true;
		if (c < 0x3105) return false;
		if (c <= 0x312C) return true;
		if (c < 0x4E00) return false;
		if (c <= 0x9FA5) return true;  // ideo
		if (c < 0xAC00) return false;
		return c <= 0xD7A3;
	}

	/**
	 * This is a utility function for determining whether a specified
	 * Unicode character
	 * is a digit according to production 88 of the XML 1.0 specification.
	 *
	 * @param c {@code char} to check for XML digit compliance
	 * @return {@code boolean} true if it's a digit, false otherwise
	 */
	public static boolean isXMLDigit(char c) {

		if (c < 0x0030) return false;
		if (c <= 0x0039) return true;
		if (c < 0x0660) return false;
		if (c <= 0x0669) return true;
		if (c < 0x06F0) return false;
		if (c <= 0x06F9) return true;
		if (c < 0x0966) return false;
		if (c <= 0x096F) return true;

		if (c < 0x09E6) return false;
		if (c <= 0x09EF) return true;
		if (c < 0x0A66) return false;
		if (c <= 0x0A6F) return true;
		if (c < 0x0AE6) return false;
		if (c <= 0x0AEF) return true;

		if (c < 0x0B66) return false;
		if (c <= 0x0B6F) return true;
		if (c < 0x0BE7) return false;
		if (c <= 0x0BEF) return true;
		if (c < 0x0C66) return false;
		if (c <= 0x0C6F) return true;

		if (c < 0x0CE6) return false;
		if (c <= 0x0CEF) return true;
		if (c < 0x0D66) return false;
		if (c <= 0x0D6F) return true;
		if (c < 0x0E50) return false;
		if (c <= 0x0E59) return true;

		if (c < 0x0ED0) return false;
		if (c <= 0x0ED9) return true;
		if (c < 0x0F20) return false;
		return c <= 0x0F29;
	}

	/**
	 * This is a utility function for determining whether a specified character
	 * is a combining character according to production 87
	 * of the XML 1.0 specification.
	 *
	 * @param c {@code char} to check.
	 * @return {@code boolean} true if it's a combining character,
	 *         false otherwise.
	 */
	public static boolean isXMLCombiningChar(char c) {
		// CombiningChar
		if (c < 0x0300) return false;
		if (c <= 0x0345) return true;
		if (c < 0x0360) return false;
		if (c <= 0x0361) return true;
		if (c < 0x0483) return false;
		if (c <= 0x0486) return true;
		if (c < 0x0591) return false;
		if (c <= 0x05A1) return true;

		if (c < 0x05A3) return false;
		if (c <= 0x05B9) return true;
		if (c < 0x05BB) return false;
		if (c <= 0x05BD) return true;
		if (c == 0x05BF) return true;
		if (c < 0x05C1) return false;
		if (c <= 0x05C2) return true;

		if (c == 0x05C4) return true;
		if (c < 0x064B) return false;
		if (c <= 0x0652) return true;
		if (c == 0x0670) return true;
		if (c < 0x06D6) return false;
		if (c <= 0x06DC) return true;

		if (c < 0x06DD) return false;
		if (c <= 0x06DF) return true;
		if (c < 0x06E0) return false;
		if (c <= 0x06E4) return true;
		if (c < 0x06E7) return false;
		if (c <= 0x06E8) return true;

		if (c < 0x06EA) return false;
		if (c <= 0x06ED) return true;
		if (c < 0x0901) return false;
		if (c <= 0x0903) return true;
		if (c == 0x093C) return true;
		if (c < 0x093E) return false;
		if (c <= 0x094C) return true;

		if (c == 0x094D) return true;
		if (c < 0x0951) return false;
		if (c <= 0x0954) return true;
		if (c < 0x0962) return false;
		if (c <= 0x0963) return true;
		if (c < 0x0981) return false;
		if (c <= 0x0983) return true;

		if (c == 0x09BC) return true;
		if (c == 0x09BE) return true;
		if (c == 0x09BF) return true;
		if (c < 0x09C0) return false;
		if (c <= 0x09C4) return true;
		if (c < 0x09C7) return false;
		if (c <= 0x09C8) return true;

		if (c < 0x09CB) return false;
		if (c <= 0x09CD) return true;
		if (c == 0x09D7) return true;
		if (c < 0x09E2) return false;
		if (c <= 0x09E3) return true;
		if (c == 0x0A02) return true;
		if (c == 0x0A3C) return true;

		if (c == 0x0A3E) return true;
		if (c == 0x0A3F) return true;
		if (c < 0x0A40) return false;
		if (c <= 0x0A42) return true;
		if (c < 0x0A47) return false;
		if (c <= 0x0A48) return true;

		if (c < 0x0A4B) return false;
		if (c <= 0x0A4D) return true;
		if (c < 0x0A70) return false;
		if (c <= 0x0A71) return true;
		if (c < 0x0A81) return false;
		if (c <= 0x0A83) return true;
		if (c == 0x0ABC) return true;

		if (c < 0x0ABE) return false;
		if (c <= 0x0AC5) return true;
		if (c < 0x0AC7) return false;
		if (c <= 0x0AC9) return true;
		if (c < 0x0ACB) return false;
		if (c <= 0x0ACD) return true;

		if (c < 0x0B01) return false;
		if (c <= 0x0B03) return true;
		if (c == 0x0B3C) return true;
		if (c < 0x0B3E) return false;
		if (c <= 0x0B43) return true;
		if (c < 0x0B47) return false;
		if (c <= 0x0B48) return true;

		if (c < 0x0B4B) return false;
		if (c <= 0x0B4D) return true;
		if (c < 0x0B56) return false;
		if (c <= 0x0B57) return true;
		if (c < 0x0B82) return false;
		if (c <= 0x0B83) return true;

		if (c < 0x0BBE) return false;
		if (c <= 0x0BC2) return true;
		if (c < 0x0BC6) return false;
		if (c <= 0x0BC8) return true;
		if (c < 0x0BCA) return false;
		if (c <= 0x0BCD) return true;
		if (c == 0x0BD7) return true;

		if (c < 0x0C01) return false;
		if (c <= 0x0C03) return true;
		if (c < 0x0C3E) return false;
		if (c <= 0x0C44) return true;
		if (c < 0x0C46) return false;
		if (c <= 0x0C48) return true;

		if (c < 0x0C4A) return false;
		if (c <= 0x0C4D) return true;
		if (c < 0x0C55) return false;
		if (c <= 0x0C56) return true;
		if (c < 0x0C82) return false;
		if (c <= 0x0C83) return true;

		if (c < 0x0CBE) return false;
		if (c <= 0x0CC4) return true;
		if (c < 0x0CC6) return false;
		if (c <= 0x0CC8) return true;
		if (c < 0x0CCA) return false;
		if (c <= 0x0CCD) return true;

		if (c < 0x0CD5) return false;
		if (c <= 0x0CD6) return true;
		if (c < 0x0D02) return false;
		if (c <= 0x0D03) return true;
		if (c < 0x0D3E) return false;
		if (c <= 0x0D43) return true;

		if (c < 0x0D46) return false;
		if (c <= 0x0D48) return true;
		if (c < 0x0D4A) return false;
		if (c <= 0x0D4D) return true;
		if (c == 0x0D57) return true;
		if (c == 0x0E31) return true;

		if (c < 0x0E34) return false;
		if (c <= 0x0E3A) return true;
		if (c < 0x0E47) return false;
		if (c <= 0x0E4E) return true;
		if (c == 0x0EB1) return true;
		if (c < 0x0EB4) return false;
		if (c <= 0x0EB9) return true;

		if (c < 0x0EBB) return false;
		if (c <= 0x0EBC) return true;
		if (c < 0x0EC8) return false;
		if (c <= 0x0ECD) return true;
		if (c < 0x0F18) return false;
		if (c <= 0x0F19) return true;
		if (c == 0x0F35) return true;

		if (c == 0x0F37) return true;
		if (c == 0x0F39) return true;
		if (c == 0x0F3E) return true;
		if (c == 0x0F3F) return true;
		if (c < 0x0F71) return false;
		if (c <= 0x0F84) return true;

		if (c < 0x0F86) return false;
		if (c <= 0x0F8B) return true;
		if (c < 0x0F90) return false;
		if (c <= 0x0F95) return true;
		if (c == 0x0F97) return true;
		if (c < 0x0F99) return false;
		if (c <= 0x0FAD) return true;

		if (c < 0x0FB1) return false;
		if (c <= 0x0FB7) return true;
		if (c == 0x0FB9) return true;
		if (c < 0x20D0) return false;
		if (c <= 0x20DC) return true;
		if (c == 0x20E1) return true;

		if (c < 0x302A) return false;
		if (c <= 0x302F) return true;
		if (c == 0x3099) return true;
		return c == 0x309A;

	}

	/**
	 * This is a utility function for determining whether a specified
	 * character is an extender according to production 88 of the XML 1.0
	 * specification.
	 *
	 * @param c {@code char} to check.
	 * @return {@code String} true if it's an extender, false otherwise.
	 */
	public static boolean isXMLExtender(char c) {

		if (c < 0x00B6) return false;  // quick short circuit

		// Extenders
		if (c == 0x00B7) return true;
		if (c == 0x02D0) return true;
		if (c == 0x02D1) return true;
		if (c == 0x0387) return true;
		if (c == 0x0640) return true;
		if (c == 0x0E46) return true;
		if (c == 0x0EC6) return true;
		if (c == 0x3005) return true;

		if (c < 0x3031) return false;
		if (c <= 0x3035) return true;
		if (c < 0x309D) return false;
		if (c <= 0x309E) return true;
		if (c < 0x30FC) return false;
		return c <= 0x30FE;
	}

	/**
	 * Allow HTTPS to URLs with self-signed certificates, etc.
	 * @param conn
	 */
	private static void setDefaultSSLSocketFactory(HttpsURLConnection conn) {
		if (!httpsInit) {
			httpsInit = true; // only initialize once
			try {
				SSLContext sc = SSLContext.getInstance("SSL");

				// Create a trust manager that does not validate certificate chains
				TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(X509Certificate[] certs, String authType) {
					}

					public void checkServerTrusted(X509Certificate[] certs, String authType) {
					}
				}
				};

				sc.init(null, trustAllCerts, new java.security.SecureRandom());
				UrlRef.sslFactory = sc.getSocketFactory();
				log.trace("XXX: initialize SSLSocketFactory");
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				log.debug("", e);
			}
		}
		if (sslFactory != null) {
			// Install the all-trusting trust manager
			conn.setSSLSocketFactory(sslFactory);
		}
	}
}
