/****************************************************************************************
 *  IGISInputStream.java
 *
 *  Created: Jan 26, 2009
 *
 *  @author DRAND
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
package org.opensextant.giscore.input;


import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;

/**
 * Read gis objects from a source. The objects can be thought of as occupying
 * a queue fed by the source object. The interface does not guarantee thread
 * safety, read the implementation classes for details. The methods here
 * are meant to be analogues of those in {@link java.io.InputStream}.
 * <p>
 * GIS streams are generally not thread safe.
 * 
 * @author DRAND
 */
public interface IGISInputStream extends Closeable {
	/**
	 * @return the next GIS object present in the source, or {@code null}
	 * if there are no more objects present.
	 * @throws IOException if an I/O error occurs
	 */
    // @CheckForNull

	IGISObject read() throws IOException;
	
	/**
	 * Search for and return an iterator on the schema within the given data 
	 * store. 
	 * 
	 * N.B. At this time this may be unimplemented for some input streams.
	 * 
	 * @return an iterator over the schemata, never {@code null}
     * 
	 * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if not supported in implementation 
	 */
    @NotNull
	Iterator<Schema> enumerateSchemata() throws IOException;
	
	/**
	 * Close the input stream, freeing any resources held.
	 */
	void close();
}
