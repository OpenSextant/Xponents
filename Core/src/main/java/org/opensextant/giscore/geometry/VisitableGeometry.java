/****************************************************************************************
 *  VisitableGeometry.java
 *
 *  Created: Oct 27, 2008
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2008
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
package org.opensextant.giscore.geometry;

import org.opensextant.giscore.IStreamVisitor;

/**
 * All visitable objects in giscore implement this interface.
 * 
 * @author DRAND
 */
public interface VisitableGeometry {
	/**
	 * Visit the object
	 * @param visitor the visitor to dispatch to, never {@code null}
	 */
    void accept(IStreamVisitor visitor);
}
