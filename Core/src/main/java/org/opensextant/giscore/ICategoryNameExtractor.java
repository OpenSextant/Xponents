/****************************************************************************************
 *  ICategoryNameExtractor.java
 *
 *  Created: Apr 16, 2009
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
package org.opensextant.giscore;

import org.opensextant.giscore.events.Row;

/**
 * Takes a Row and extracts a container name by processing the row.
 * 
 * @author DRAND
 */
public interface ICategoryNameExtractor {
	/**
	 * Extract a category name for a given row
	 * @param row the row, or a subclass of the row, never {@code null}
	 * @return the category name, never {@code null} but could be empty
	 */
	String extractCategoryName(Row row);
}
