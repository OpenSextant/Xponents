/****************************************************************************************
 *  ShapefileInputStream.java
 *
 *  Created: Jan 12, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
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
package org.opensextant.giscore.input.shapefile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.IAcceptSchema;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.input.GISInputStreamBase;
import org.opensextant.giscore.input.dbf.DbfInputStream;
import org.opensextant.giscore.utils.Args;

/**
 * Read one or more shapefiles in from a directory or from a zip input stream.
 *
 * @author DRAND
 */
public class ShapefileInputStream extends GISInputStreamBase {
	private static final String ms_tempDir = System.getProperty("java.io.tmpdir");

	private static final AtomicInteger ms_tempDirCounter = new AtomicInteger();

	/**
	 * The accepter, may be null, used to determine if a given schema is wanted
	 */
	private IAcceptSchema accepter;

	/**
	 * Working directory to hold shapefile data. If we are using a temp directory
	 * then this will be removed when close is called. If not it belongs to the
	 * caller and will be left alone.
	 */
	private File workingDir;

	/**
	 * Shapefiles found in the working directory. This will have all the found
	 * shapefiles. Shapefiles will actually be read based on the accepter if
	 * one is defined.
	 */
	private File[] shapefiles;

	/**
	 * The current shapefile being read. When we are done this will be set
	 * to the length of the shapefiles array.
	 * is yet been selected.
	 */
	private int currentShapefile = 0;

	/**
	 * The handler. The current handler is held here. If the handler returns
	 * {@code null} then the current shapefile's stream is empty. The first
	 * item returned from a new shapefile is the schema, and we can use that
	 * against the accepter to decide if the shapefile will be used or not. If
	 * it won't then we will move onto the next shapefile.
	 */
	private SingleShapefileInputHandler handler;

	/**
	 * This tracks if we're using a temp directory
	 */
	private final boolean usingTemp;

	/**
	 * Ctor
	 *
	 * @param stream   the stream containing a zip archive of the shapefile directory, or possibly a single shp file
	 * @param acceptor a function that determines if a schema should be used, may be {@code null}
	 * @throws IOException if an I/O error occurs
	 */
	public ShapefileInputStream(InputStream stream, IAcceptSchema acceptor) throws IOException {
		this(stream, new Object[]{acceptor});
	}
	
	/**
	 * Standard ctor for streams
	 * @param stream
	 * @param args
	 * @throws IOException
	 */
	public ShapefileInputStream(InputStream stream, Object[] args) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException(
					"stream should never be null");
		}
		
		Args argv = new Args(args);
		IAcceptSchema accepter = (IAcceptSchema) argv.get(IAcceptSchema.class, 0);
		ZipInputStream zipstream = null;
		InputStream shapestream = null;
		if (!(stream instanceof ZipInputStream)) {
			BufferedInputStream bufferedstream = new BufferedInputStream(stream);
			// Is this a .shp?
			bufferedstream.mark(10);
			byte[] header = new byte[4];
			if (bufferedstream.read(header) < 4)
				throw new IOException("not a shape file: invalid header");
			bufferedstream.reset();
			if (header[0] == 0 && header[1] == 0 && header[2] == 047 && header[3] == 012) {
				// SHP file
				shapestream = bufferedstream;
			} else if (header[0] == 'P' && header[1] == 'K' && header[2] == 0x03 && header[3] == 0x04) {
				// ZIP file
				zipstream = new ZipInputStream(bufferedstream);
			} else {
				throw new IOException("not a shape file: invalid header");
			}
		} else {
			zipstream = (ZipInputStream) stream;
		}

		File dir = new File(ms_tempDir,
				"temp" + ms_tempDirCounter.incrementAndGet());
		dir.mkdirs();
		// Remove existing content in the temp dir - important
		File[] files = dir.listFiles();
		if (files != null) {
			for (File content : files) {
				content.delete();
			}
		}
		usingTemp = true;
		if (zipstream != null) {
			ZipEntry entry = zipstream.getNextEntry();
			while (entry != null) {
				String name = entry.getName().replace('\\', '/');
				String[] parts = name.split("/");
				File file = new File(dir, parts[parts.length - 1]);
				FileOutputStream fos = new FileOutputStream(file);
				IOUtils.copy(zipstream, fos);
				IOUtils.closeQuietly(fos);
				entry = zipstream.getNextEntry();
			}
		} else {
			// otherwise shapestream != null 
			File shapefile = new File(dir, "input.shp");
			FileOutputStream fos = new FileOutputStream(shapefile);
			IOUtils.copy(shapestream, fos);
			fos.flush();
			IOUtils.closeQuietly(fos);
		}
		initialize(dir, accepter);
	}

	/**
	 * Ctor
	 *
	 * @param file     the location of the shapefile(s) as individual .shp file or directory
	 * @param accepter a function that determines if a schema should be used,
	 *                 may be {@code null}
	 * @throws IllegalArgumentException if file argument is {@code null}
	 */
	public ShapefileInputStream(File file, IAcceptSchema accepter) {
		this(file, new Object[] {accepter});
	}
	
	/**
	 * Standard ctor for streams
	 * @param file
	 * @param args
	 */
	public ShapefileInputStream(File file, Object[] args) {
		if (file == null) {
			throw new IllegalArgumentException("file argument should never be null");
		}
		IAcceptSchema accepter = (IAcceptSchema) (args.length > 0 ? args[0] : null);
		usingTemp = false;
		initialize(file, accepter);
	}

	/**
	 * Initialize the input stream
	 *
	 * @param file     the location of the shapefile(s) as individual .shp file or directory
	 * @param accepter
	 * @throws IllegalArgumentException if file argument does not exist, not shape file,
	 *                                  or no shape files in directory
	 */
	private void initialize(File file, IAcceptSchema accepter) {
		this.accepter = accepter;
		if (file.isDirectory()) {
			workingDir = file;
			shapefiles = workingDir.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					return pathname.getName().endsWith(".shp");
				}
			});
			// review: should we throw exception if no shape files found (shapefiles.length == 0) ??
		} else if (file.isFile() && file.getName().endsWith(".shp")) {
			workingDir = file.getParentFile();
			shapefiles = new File[]{file};
		} else {
			throw new IllegalArgumentException("Invalid shapefile location");
		}
	}

	public void close() {
		if (handler != null) {
			handler.close();
		}
		if (usingTemp && !workingDir.delete()) {
			workingDir.deleteOnExit();
		}
	}

	@NotNull
	@Override
	public Iterator<Schema> enumerateSchemata() throws IOException {
		File[] dbfs = workingDir.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".dbf");
			}
		});
		List<Schema> schemata = new ArrayList<>();
		for (File dbf : dbfs) {
			try(DbfInputStream dbfis = new DbfInputStream(dbf, null)) {
				Schema schema = (Schema) dbfis.read();
				if (schema != null){
					schemata.add(schema);
				}
			}
		}
		return schemata.iterator();
	}

	public IGISObject read() throws IOException {
		IGISObject rval = null;
		while (rval == null && currentShapefile < shapefiles.length) {
			if (handler == null) {
				handleNewShapefile(); // It will iterate
			} else {
				rval = handler.read();
				if (rval == null) {
					handler.close();
					currentShapefile++;
					handler = null;
				} else if ((rval instanceof Schema) && accepter != null) {
					if (!accepter.accept((Schema) rval)) {
						currentShapefile++; // Next
						handler.close();
						handler = null;
						rval = null; // null to force iteration
					}
				}
			}
		}
		return rval;
	}

	/**
	 * Calculate the shapefile basename and open the single handler to the
	 * new shapefile.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void handleNewShapefile() throws IOException {
		File shapefile = shapefiles[currentShapefile];
		String basename = shapefile.getName();
		int i = basename.indexOf(".shp");
		basename = basename.substring(0, i);
		handler = new SingleShapefileInputHandler(workingDir, basename);
	}
}
