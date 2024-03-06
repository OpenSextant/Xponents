/****************************************************************************************
 *  SimpleFieldCacher.java
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.opensextant.giscore.events.SimpleField;

/**
 * This cacher recognizes and caches simple fields to reduce the overhead in the
 * object buffer.
 * 
 * @author DRAND
 */
public class SimpleFieldCacher implements IObjectCacher {
	/**
	 * Cached fields
	 */
	private final Map<SimpleField, Long> fields = new HashMap<>();

	/**
	 * Counter
	 */
	private final AtomicLong counter = new AtomicLong();

    /**
     * Add SimpleField to cache.
     *
     * @param field
     * @throws IllegalArgumentException if field is not SimpleField instance
     * @throws IllegalStateException if field is already in the collection  
     */
	@Override
	public void addToCache(Object field) {
        if (!(field instanceof SimpleField)) {
            throw new IllegalArgumentException("Field is not SimpleField");
        }
        final SimpleField sf = (SimpleField)field;
		if (fields.containsKey(sf)) {
			throw new IllegalStateException("Field is already in the collection");
		}
        fields.put(sf, counter.incrementAndGet());
	}

	@Override
	public Long getObjectOutputReference(Object field) {
		return fields.get(field);
	}

	@Override
	public boolean hasBeenCached(Object field) {
		return field instanceof SimpleField && fields.containsKey(field);
	}

	@Override
	public boolean shouldCache(Object field) {
		return field instanceof SimpleField;
	}
}
