/****************************************************************************************
 *  FileGDBLibraryInitializer.java
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
package org.opensextant.giscore.input.gdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileGDBLibraryInitializer {
	private static final Logger log = LoggerFactory.getLogger(FileGDBLibraryInitializer.class);
	private static FileGDBLibraryLoader loader = null;
	private static boolean initialized = false;
	
	public synchronized static void initialize() {
		if (initialized) return;
		initialized = true;
		
		try {
			loader = new FileGDBLibraryLoader();
			loader.loadLibrary();
		} catch (Exception e) {
			log.error("Severe error trying to load FileGDB library", e);
		}
	}

}
