/****************************************************************************************
 *  LibraryLoader.java
 *
 *  Created: Feb 7, 2013
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2013
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
package org.opensextant.giscore.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a basic package where the libraries are stored, save the library
 * to the filesystem and then load the library into the runtime. Optimize
 * not overwriting the library if the current resource has the same modification
 * time as the current library or earlier.
 * 
 * Storage example:
 * 
 * org.opensextant.giscore.filegdb.
 * 		win64
 * 			libname.properties (contains attr: value pairs. Modified 
 * 								date stored as modified: yyyy/mm/dd)
 * 			libname.dll
 * 		mac64
 * 			libname.properties
 * 			libname.so
 * 		linux64
 * 			libname.properties
 * 			libname.so 
 * 
 * properties in property file:
 * modified: time value
 * filename: library name
 * 
 * @author DRAND
 *
 */
public class LibraryLoader {
	private static final Logger log = LoggerFactory.getLogger(LibraryLoader.class);

	private Properties props;
	private final String libPackage;
	private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd");
	protected String osarch;
	private final String libname;
	private final Package parent;
	private final boolean tryLibraryPath;

	/**
	 * Convert os.name from system property to something useful
	 * @return
	 */
	public String osName() {
		String osn = System.getProperty("os.name").toLowerCase();
		
		if (osn.contains("win")) {
			osn = "win"; 
		} else if (osn.contains("linux")) {
			osn = "linux";
		} else if (osn.contains("mac")) {
			osn = "mac";
		}
		
		return osn;
	}
	
	/**
	 * Convert the architecture information into a 64/32 flag
	 * @return
	 */
	public String osArch() {
		String arch = System.getProperty("os.arch");
		
		return arch.contains("64") ? "64" : "32";
	}
	
	/**
	 * Convert the package name into a path appropriate for getResource
	 * @param p
	 * @return
	 */
	public String packagePath(Package p) {
		return "/" + p.getName().replaceAll("\\.", "/");
	}
	
	/**
	 * Ctor
	 * @param parent
	 * @param libname
	 * @throws IOException
	 */
	public LibraryLoader(Package parent, String libname, boolean tryLibraryPath) throws IOException {
		osarch = osName() + osArch();
		libPackage = packagePath(parent) + "/" + osarch;
		this.libname = libname;
		this.parent = parent;
		this.tryLibraryPath = tryLibraryPath;
	}
	
	/**
	 * General load library method to be used during normal initialization
	 * @throws IOException
	 * @throws ParseException
	 */
	public void loadLibrary() throws IOException, ParseException {
		if(tryLibraryPath) {
			try {
				System.loadLibrary(libname);
				return;
			} catch(UnsatisfiedLinkError e) {
				String message = "Could not find library " + libname + " on java.library.path.  Falling back to unpacking the built-in version.";
				if(log.isDebugEnabled()) {
					log.debug(message, e);
				} else {
					log.warn(message);
				}
			}
		}
		String propertiespath = libPackage + "/" + libname + ".properties";
		InputStream is = getClass().getResourceAsStream(propertiespath);
		if(is == null) {
			throw new FileNotFoundException("Could not find the bundled native code properties file: " + propertiespath + " your platform: " + osarch + " may not be supported.");
		}
		try {
			props = new Properties();
			props.load(is);
		} finally {
			IOUtils.closeQuietly(is);
		}
		File tempDir = new File(System.getProperty("java.io.tmpdir"));
		File libDir = new File(tempDir, "libraryLoader");
		if (! libDir.exists()) {
			if (! libDir.mkdir()) {
				throw new IOException("Could not create library directory");
			}
		}
		String filename = props.getProperty("filename");
		if (StringUtils.isBlank(filename)) {
			throw new IllegalStateException("Properties do not specify a filename");
		}
		String mod = props.getProperty("modified");
		Date modtime;
		if (StringUtils.isNotBlank(mod)) {
			modtime = fmt.parse(mod);
		} else {
			modtime = new Date();
		}
		File libFile = new File(libDir, filename);
		boolean skip = false;
		if (libFile.exists()) {
			// Check if new enough
			Date fileLastModified = new Date(libFile.lastModified());
			if (fileLastModified.after(modtime)) {
				skip = true;
			}
		}
		if (! skip) {
			String libpath = libPackage + "/" + filename;
			is = getClass().getResourceAsStream(libpath);
			if(is == null) {
				throw new IllegalStateException("Could not find the bundled native library file: " + libpath);
			}
			FileOutputStream os = null;
			try {
				os = new FileOutputStream(libFile);
				IOUtils.copy(is, os);
			} finally {
				IOUtils.closeQuietly(is);
				IOUtils.closeQuietly(os);
			}
		}
		System.load(libFile.getAbsolutePath());
	}
}
