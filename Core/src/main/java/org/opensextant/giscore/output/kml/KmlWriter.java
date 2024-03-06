/*
 *  KmlWriter.java
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
package org.opensextant.giscore.output.kml;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.UrlRef;
import org.opensextant.giscore.output.IGISOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper to {@code KmlOutputStream} for handling the common steps needed
 * to create basic KML or KMZ files.
 * <p>
 * Handles the following tasks:
 *
 * <ul>
 * <li>write to KMZ/KML files transparently. If file has a .kmz file extension (or .zip) then a KMZ (ZIP)
 *    file is created with that file name.
 * <li>discards empty containers if ContainerStart is immediately followed by a ContainerEnd element
 *    in a successive write() call.
 * <li>write Files or contents from inputStream to entries in KMZ for networkLinked content,
 *    overlay images, icons, etc.
 * </ul>
 * 
 * Complements the {@link org.opensextant.giscore.input.kml.KmlReader} class. Advanced KML
 * support with more direct access may require using the {@link KmlOutputStream}
 * or {@link KmzOutputStream} classes directly.
 * 
 * @author Jason Mathews, MITRE Corp.
 * Created: Mar 13, 2009 10:06:17 AM
 */
public class KmlWriter implements IGISOutputStream {

    private static final Logger log = LoggerFactory.getLogger(KmlWriter.class);

    private KmlOutputStream kos;
    private ZipOutputStream zoS;
    private ContainerStart waiting;
	private final boolean compressed;

    /**
     * Construct a <code>KmlWriter</code> which starts writing a KML document into
     * the specified KML or KMZ file.  If file name ends with .kmz or .zip extension
     * then a compressed KMZ (ZIP) file is produced with the main KML document
     * stored as "doc.kml" in the root directory. <p>
     *
     * For details on .KMZ files see tutorial at
     * <a href="http://code.google.com/apis/kml/documentation/kmzarchives.html">...</a>
     *
     * @param file the file to be opened for writing.
     * @param encoding the encoding to use, if null default encoding (UTF-8) is assumed
     * @throws IOException if an I/O error occurs
     */
    public KmlWriter(File file, String encoding) throws IOException {
        String name = file.getName().toLowerCase();
        // if  filename ends in .zip create then treat as .KMZ file ending with .ZIP extension
        compressed = name.endsWith(".kmz") || name.endsWith(".zip"); 
        OutputStream os = new FileOutputStream(file);
        try {
            if (compressed) {
                BufferedOutputStream boS = new BufferedOutputStream(os);
                // Create the doc.kml file inside of a zip entry
                zoS = new ZipOutputStream(boS);
                ZipEntry zEnt = new ZipEntry("doc.kml");
                zoS.putNextEntry(zEnt);
                kos = new KmlOutputStream(zoS, encoding);
            } else {
                kos = new KmlOutputStream(os, encoding);
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
		// TODO: consider adding KmlWriter(InputStream is, boolean compress) constructor
    }

    /**
     * Construct a KmlWriter which starts writing a KML document into
     * the specified KML or KMZ file.  If filename ends with .kmz or .zip extension
     * then a compressed KMZ (ZIP) file is produced with the main KML document
     * stored as "doc.kml" in the root directory. <p>
     *
     * For details on .KMZ files see "Creating a .kmz Archive" section
     * of <a href="http://code.google.com/apis/kml/documentation/kml_21tutorial.html">...</a>
     *
     * @param file the file to be opened for writing.
     * @throws IOException if an I/O error occurs
     */
	public KmlWriter(File file) throws IOException {
        this(file, null);
    }

	/**
	 * Construct a KmlWriter with KmlOutputStream. Basically wraps a KmlOutputStream
	 * with {@code KmlWriter}.
	 *
	 * @param os the KmlOutputStream to be opened for writing, never null.
	 */
	public KmlWriter(KmlOutputStream os) {
		compressed = false;
		kos = os;
		// note: could check kos if wraps an underlying ZipOutputStream 
		// compress = kos.getStream() instanceof ZipOutputStream
	}

    /**
     * Tests whether the output file is a compressed KMZ file.
     *
     * @return {@code true} if the output file is a compressed KMZ file;
     *          {@code false} otherwise*
     *
     * @return
     */
    public boolean isCompressed() {
        return compressed;
    }

	/**
	 * Write file contents into entry of compressed KMZ file.  File can itself be
	 * KML, image, model or other file.  Contents are not parsed or validated.
	 * This must be called after entire KML for main document "doc.kml" is written.
	 *
	 * @param file file to write into the KMZ
	 * @param entryName the entry name for file as it will appear in the KMZ.
	 *	This should be a root-level or relative file path (e.g. myOtherData.kml or images/image.png).
	 *	As in any other zip file, entry names must be unique.
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if arguments are null or KmlWriter is not writing
	 * 			a compressed KMZ file
	 */
	public void write(File file, String entryName) throws IOException {
		write(new FileInputStream(file), entryName);
    }

	/**
	 * Write contents from InputStream into file named localName in compressed KMZ file.
	 * This must be called after entire KML for main document doc.kml is written.
	 * Note the InputStream is closed upon exit of this method.
	 *
	 * @param is InputStream to write into the KMZ 
	 * @param entryName the entry name for file as it will appear in the KMZ.
	 *	This should be a root-level or relative file path (e.g. myOtherData.kml or images/image.png).
	 *	As in any other zip file, entry names must be unique.
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if arguments are null
	 * @throws IllegalStateException if KmlWriter is not writing
	 * 			a compressed KMZ file
	 */
	public void write(InputStream is, String entryName) throws IOException {
		if (is == null) throw new IllegalArgumentException("InputStream cannot be null"); 
        try {
			if (!compressed)
            	throw new IllegalStateException("Not a compressed KMZ file. Cannot add arbitrary content to non-KMZ output");
        	if (StringUtils.isBlank(entryName))
            	throw new IllegalArgumentException("localName must be non-blank file name");
			if (zoS == null) throw new IOException("stream is already closed");
			if (kos != null) {
				kos.closeWriter();
				zoS.closeEntry();
			}
			ZipEntry zEnt = new ZipEntry(entryName.trim());
			zoS.putNextEntry(zEnt);
			// copy input to output
			// write contents to entry within compressed KMZ file
			IOUtils.copy(is, zoS);
			zoS.closeEntry();
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	/**
	 * Write GISObject into KML output stream.
	 * 
	 * @param object IGISObject object to write
	 * 
	 * @throws IllegalStateException if KmlOutputStream is closed.
	 *  If underlying processing throws an XMLStreamException then it will rethrow
	 *  it wrapped with an IllegalStateException.
	 */
	@Override
	public void write(IGISObject object) {
		if (kos == null) throw new IllegalStateException("cannot write after stream is closed");
		// log.info("> Write: " + object.getClass().getName());
		if (object != null) {
			if (object instanceof ContainerStart) {
				// defer writing ContainerStart objects so empty containers can be dropped
				if (waiting != null) {
					kos.write(waiting);
				}
				waiting = (ContainerStart)object;
			} else {
				if (waiting != null) {
					if (object instanceof ContainerEnd) {
						// if have ContainerStart followed by ContainerEnd then ignore empty container
						// unless have waiting elements to flush (e.g. Styles)
						waiting = null;
						return;
					}
					kos.write(waiting);
					waiting = null;
				}
				kos.write(object);
			}
		}
    }

    /**
     * Close this KmlWriter and free any resources associated with the
     * writer including underlying stream.
     */
    @Override
	public void close() {
		close(true);
    }

	/**
     * Close this KmlWriter and free any resources associated with the
     * writer.
	 * @param closeStream  Flag to close the underlying stream. If false then
	 * underlying stream is left open otherwise closed along with other resources. 
	 */
    public void close(boolean closeStream) {
		// If we have any waiting element (waiting != null) then
        // we have a ContainerStart with no matching ContainerEnd so ignore it
		if (kos != null)
			try {
                kos.closeWriter();
			} catch (IOException e) {
				log.warn("Failed to close writer", e);
			}
        // if we're writing zipStream then need to close the entry before closing the underlying stream
        if (zoS != null) {
            try {
                zoS.closeEntry();
            } catch (IOException e) {
                log.error("Failed to close Zip Entry", e);
            }
			IOUtils.closeQuietly(zoS);
			zoS = null;
		}
        if (kos != null && closeStream) {
            kos.closeStream(); // close underlying closing the underlying XmlOutputStreamBase.stream
            kos = null;
        }

        waiting = null;		
	}

	/**
	 * @param href href URI to normalize
	 * @return Return normalized href, null if normal or failed to normalize
	 */
	private static String fixHref(String href) {
		if (href != null && href.startsWith("kmz")) {
			try {
				final URI uri = new URI(href);
				if (uri.isAbsolute()) {
					return new UrlRef(uri).getKmzRelPath();
				}
			} catch (MalformedURLException | URISyntaxException e) {
				// ignore
			}
		}
		return null;
	}

	/**
     * Normalize and restore URLs from internal URIs as rewritten in {@link org.opensextant.giscore.input.kml.KmlReader#read()}
     * if applicable. Only IGISObjects that haves URL attributes may be affected (i.e.,
     * NetworkLink, Overlay, and Style) and only if original href had a
     * relative URL which gets rewritten to include the parent KML/KMZ document.
     * <P>
     * For example, given a relative URL href=child.kml in NetworkLink
     * root KML document (doc.kml) from base resource URL <a href="http://target/test.kmz">...</a>
     * gets rewritten as kmzhttp://target/test.kmz?file=child.kml from which to
     * resolve the child.kml document. The normalized form of this URI is the
     * original "child.href" value.
     *
     * @param o IGISObject to normalize, never null
     */
	public static void normalizeUrls(IGISObject o) {
		// following must be in sync with "normalization" and rewriting
		// as defined in KmlReader.read().
		final Class<? extends IGISObject> aClass = o.getClass();
		if (aClass == Feature.class) {
			Feature f = (Feature)o;
			StyleSelector style = f.getStyle();
			if (style != null) {
				// handle IconStyle href if defined
				checkStyleType(style);
			}
		} else if (aClass == ContainerStart.class) {
			for (StyleSelector style : ((ContainerStart)o).getStyles()) {
				// normalize iconStyle hrefs
				checkStyleType(style);
			}
		} else if (o instanceof NetworkLink) {
			NetworkLink nl = (NetworkLink) o;
			TaggedMap link = nl.getLink();
			if (link != null) {
				String href = fixHref(link.get(IKml.HREF));
				// check for treated URLs and normalized them so they work outside
				// this package (e.g. with Google Earth client).
				if (href != null) link.put(IKml.HREF, href);
			}
			// Note: NetworkLinks can have inline Styles & StyleMaps but no normalization needed at this time
		} else if (o instanceof Overlay) {
			// handle GroundOverlay, PhotoOverlay, or ScreenOverlay href
			Overlay ov = (Overlay) o;
			TaggedMap icon = ov.getIcon();
			if (icon != null) {
				String href = fixHref(icon.get(IKml.HREF));
				if (href != null) icon.put(IKml.HREF, href);
			}
			// Note: Overlays can have inline Styles & StyleMaps but no normalization needed at this time
			// since only icon styles need normalization
		} else if (aClass == Style.class) {
			// normalize iconStyle hrefs
			checkStyle((Style) o);
		} else if (aClass == StyleMap.class) {
			checkStyleMap((StyleMap) o);
		}
	}

	private static void checkStyleType(StyleSelector style) {
		if (style instanceof Style) {
			// normalize iconStyle hrefs
			checkStyle((Style)style);
		} else if (style instanceof StyleMap) {
			checkStyleMap((StyleMap)style);
		}
	}

	private static void checkStyleMap(StyleMap sm) {
		for(java.util.Iterator<Pair> it = sm.getPairs(); it.hasNext(); ) {
			Pair pair = it.next();
			StyleSelector style = pair.getStyleSelector();
			if (style instanceof Style) {
				// normalize iconStyle hrefs
				checkStyle((Style)style);
			}
			// ignore nested StyleMaps
		}
	}

	private static void checkStyle(Style style) {
		if (style.hasIconStyle()) {
			String href = fixHref(style.getIconUrl());
			if (href != null)
				style.setIconUrl(href);
			// otherwise URL was not normalized and left unchanged
		}
	}
}
