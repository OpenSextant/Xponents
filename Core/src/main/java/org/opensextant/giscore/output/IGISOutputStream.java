/****************************************************************************************
 *  IGISOutputStream.java
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
package org.opensextant.giscore.output;

import java.io.Closeable;

import org.opensextant.giscore.events.IGISObject;

/**
 * A stream that accepts GIS objects to be consumed. Generally it consumes
 * GIS objects to be written to a given sink.
 * <p>
 * GIS output streams are generally not thread safe.
 * 
 * @author DRAND
 */
public interface IGISOutputStream extends Closeable {
	/**
	 * Write the given object.
	 * 
	 * @param object the object to be written, never {@code null}.
	 */
	void write(IGISObject object);
}
