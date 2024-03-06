/****************************************************************************************
 *  GISFactory.java
 *
 *  Created: Jan 28, 2009
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensextant.giscore.data.DocumentTypeRegistration;
import org.opensextant.giscore.data.FactoryDocumentTypeRegistry;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.output.IGISOutputStream;

/**
 * Factory class which creates concrete instantiations of input and output
 * streams for various formats.
 * 
 * @author DRAND
 */
public class GISFactory {
	
	
	/**
	 * The size of the buffer that should be used when buffering content
	 * in memory. Right now this is a per feature class buffer size.
	 */
	public final static AtomicInteger inMemoryBufferSize = new AtomicInteger(2000);
	
	/**
	 * Input stream factory
	 * 
	 * @param type
	 *            the type of the document, never {@code null}.
	 * @param stream
	 *            an input stream to the document or document contents, never
	 *            {@code null}. If stream is for a KMZ (ZIP) source then
	 *            {@code KmlReader} should used instead.
	 * @param arguments
	 *            the additional arguments needed by the constructor, the type
	 *            or types depend on the constructor
	 * @return a gis input stream, never {@code null}
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws IllegalArgumentException
	 *             if type or stream are null
	 * @see org.opensextant.giscore.input.kml.KmlReader
	 */
	public static IGISInputStream getInputStream(DocumentType type,
			InputStream stream, Object... arguments) throws IOException {
		if (type == null) {
			throw new IllegalArgumentException("type should never be null");
		}
		if (stream == null) {
			throw new IllegalArgumentException("stream should never be null");
		}
		
		DocumentTypeRegistration docreg = FactoryDocumentTypeRegistry.get(type);
		
		if (docreg != null) {
			docreg.checkArguments(true, arguments);		
			try {
				return docreg.getInputStream(stream, arguments);
			} catch (InstantiationException e) {
				throw new IOException(e);
			}
		} else {
			throw new UnsupportedOperationException(
					"Cannot create an input stream for type " + type);
		}
	}

	/**
	 * Input stream factory
	 * 
	 * @param type
	 *            the type of the document, never {@code null}.
	 * @param file
	 *            a file containing the document, never {@code null}. If
	 *            file is a KMZ (ZIP) file then {@code KmlReader} should
	 *            used instead.
	 * @param arguments
	 *            the additional arguments needed by the constructor, the type
	 *            or types depend on the constructor
	 * @return a gis input stream, never {@code null}
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws IllegalArgumentException
	 *             if type or file are null or file does not exist
	 * @see org.opensextant.giscore.input.kml.KmlReader
	 */
	public static IGISInputStream getInputStream(DocumentType type, File file,
			Object... arguments) throws IOException {
		if (type == null) {
			throw new IllegalArgumentException("type should never be null");
		}
		if (file == null || !file.exists()) {
			throw new IllegalArgumentException(
					"file should never be null and must exist");
		}

		DocumentTypeRegistration docreg = FactoryDocumentTypeRegistry.get(type);
		
		try {
			if (docreg != null) {
				if (docreg.hasFileCtor()) {
					docreg.checkArguments(true, arguments);		
					return docreg.getInputStream(file, arguments);
				} else {
					return docreg.getInputStream(new FileInputStream(file), arguments);
				}
			} else {
				throw new UnsupportedOperationException(
						"Cannot create an input stream for type " + type);
			}
		} catch(InstantiationException e) {
			throw new IOException(e);
		}

	}

	/**
	 * Output stream factory
	 * 
	 * @param type
	 *            the type of the document to be written, never
	 *            {@code null}.
	 * @param outputStream
	 *            the output stream used to save the generated GIS file or
	 *            files. If target output is KMZ then {@code KmlWriter}
	 *            should be used instead.
	 * @param arguments
	 *            the additional arguments needed by the constructor, the type
	 *            or types depend on the constructor
	 * @return a gis output stream, never {@code null}.
	 * @throws IOException
	 *             if an I/O error occurs
	 * @see org.opensextant.giscore.output.kml.KmlWriter
	 */
	public static IGISOutputStream getOutputStream(DocumentType type,
			OutputStream outputStream, Object... arguments) throws IOException {
		if (type == null) {
			throw new IllegalArgumentException("type should never be null");
		}
		if (outputStream == null && !DocumentType.Shapefile.equals(type)) {
			throw new IllegalArgumentException(
					"outputStream should never be null");
		}

		DocumentTypeRegistration docreg = FactoryDocumentTypeRegistry.get(type);
		
		try {
			if (docreg == null) {
				throw new UnsupportedOperationException("Cannot create an output stream for type " + type);
			} else {
				docreg.checkArguments(false, arguments);	
				return docreg.getOutputStream(outputStream, arguments);
			}
		} catch (InstantiationException e) {
			throw new IOException(e);
		}
	}
}
