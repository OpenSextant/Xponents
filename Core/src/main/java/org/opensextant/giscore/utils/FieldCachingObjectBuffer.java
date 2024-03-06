/****************************************************************************************
 *  ShapefileObjectBuffer.java
 *
 *  Created: Dec 16, 2009
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
package org.opensextant.giscore.utils;

import org.opensextant.giscore.GISFactory;

/**
 * Version of object buffer that caches simple fields
 * 
 * @author DRAND
 */
public class FieldCachingObjectBuffer extends ObjectBuffer {
    
	/**
	 * Ctor
	 */
	public FieldCachingObjectBuffer() {
		super(GISFactory.inMemoryBufferSize.get(), new SimpleFieldCacher());
	}
	
	/**
	 * Ctor
     * @param size Number of elements in object buffer
     */
	public FieldCachingObjectBuffer(int size) {
		super(size, new SimpleFieldCacher());
	}
}
