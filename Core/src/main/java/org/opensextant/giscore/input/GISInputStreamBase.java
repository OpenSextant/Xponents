/****************************************************************************************
 *  GISInputStreamBase.java
 *
 *  Created: Jan 26, 2009
 *
 *  @author DRAND
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
 ***************************************************************************************/
package org.opensextant.giscore.input;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;

/**
 * Base class that handles the mark and reset behavior.
 * 
 * @author DRAND
 * 
 */
public abstract class GISInputStreamBase implements IGISInputStream {
	/**
	 * Buffered elements that should be returned. This allows a single call to
	 * read to find several elements and return them in the right order.
	 */
	private final LinkedList<IGISObject> buffered = new LinkedList<>();

	/**
	 * Add an element to be return later to the beginning of the list
	 * 
	 * @param obj
	 */
	protected void addFirst(IGISObject obj) {
		buffered.addFirst(obj);
	}

	/**
	 * Add an element to be return later to the end of the list
	 * 
	 * @param obj
	 */
	protected void addLast(IGISObject obj) {
		buffered.add(obj);
	}

	/**
	 * @return
	 */
	protected IGISObject readSaved() {
		return buffered.removeFirst();
	}

	/**
	 * @return
	 */
	protected boolean hasSaved() {
		return !buffered.isEmpty();
	}

	@NotNull
	public Iterator<Schema> enumerateSchemata() throws IOException {
		throw new UnsupportedOperationException();
	}
}
