/****************************************************************************************
 *  IObjectCacher.java
 *
 *  Created: Dec 15, 2009
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


/**
 * Allows some subset of objects contained in the object stream to be
 * de-duplicated into a single copy and references on output and reified on
 * input into references. 
 * <p>
 * The process on input is not dependent on the output implementation.
 * 
 * @author DRAND
 * 
 */
public interface IObjectCacher {
	/**
	 * Take an output object and check to see if the object should be reduced to
	 * a reference.
	 * 
	 * @param outputObject
	 *            the object to consider, never {@code null}
	 * @return {@code true} indicates that this object should be cached and
	 *         reduced to a reference for future output.
	 */
	boolean shouldCache(Object outputObject);
	
	/**
	 * Predicate to determine if this object has already been cached.
	 * 
	 * @param outputObject the object, never {@code null}
	 * @return {@code true} if the object is present in the cache.
	 */
	boolean hasBeenCached(Object outputObject);
	
	/**
	 * Add to the cache for a later call
	 * @param outputObject the object, never {@code null}
	 */
	void addToCache(Object outputObject);

	/**
	 * Take an object that should be reduced to a reference as determined by an
	 * earlier call to {@link #shouldCache(Object)} and return the appropriate
	 * reference to the object. 
	 * 
	 * @param outputObject
	 *            the object to consider, never {@code null}.
	 * @return a non-{@code null} Long will be returned
	 * is inappropriate. Not enforced by the interface.
	 */
	Long getObjectOutputReference(Object outputObject);
}
