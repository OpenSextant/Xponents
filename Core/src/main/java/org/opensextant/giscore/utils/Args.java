/****************************************************************************************
 *  Args.java
 *
 *  Created: May 3, 2013
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

/**
 * Helper to deal with argument arrays in streams. Checks arguments before
 * returning to assure types are correct so casts will not fail. Instead
 * throws illegal argument exceptions.
 * 
 * @author DRAND
 */
public class Args {
	private final Object[] args;
	
	public Args(Object[] args) {
		if (args == null)
			this.args = new Object[0];
		else
			this.args = args;
	}
	
	public Object get(Class type, int i) {
		Object value = args.length > i ? args[i] : null;
		if (value == null)
			return null;
		if (type.getClass().equals(type) || type.getClass().isAssignableFrom(type.getClass())) {
			return value;
		} else {
			throw new IllegalArgumentException("Argument " + i + " was of the wrong type, should have been of type " + type.getSimpleName());
		}		
	}

}
