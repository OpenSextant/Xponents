/****************************************************************************************
 *  IContainerNameStrategy.java
 *
 *  Created: Feb 24, 2009
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

import java.util.List;

/**
 * Derives a name for a container based on a feature. The feature's geometry
 * and attributes may be considered, as well as the schema (if any). There
 * is a default implementation in the esri subpackage.
 * 
 * @author DRAND
 *
 */
public interface IContainerNameStrategy {
	/**
	 * Derive the Container name from the feature information.
	 * @param path the parent container components, never {@code null} but
	 * may be empty
	 * @param feature the feature key, never {@code null}
	 * @return the container name, never {@code null} or empty.
	 */
	String deriveContainerName(List<String> path, FeatureKey feature);
}
