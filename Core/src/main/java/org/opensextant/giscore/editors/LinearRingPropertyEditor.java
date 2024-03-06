/****************************************************************************************
 *  LinearRingPropertyEditor.java
 *
 *  Created: Jul 9, 2009
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
package org.opensextant.giscore.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;

/**
 * Convert a string to a ring. The values are a series of 
 * geodetic points as converted by the superclass.
 * <p>
 * Example: 
 * 12.11/24.1; 12.21/24.1; 12.21/24.3
 * 
 * @author DRAND
 */
public class LinearRingPropertyEditor extends PointPropertyEditor {
	@Override
	public String getAsText() {
		LinearRing ring = (LinearRing) getValue();
		StringBuilder b = new StringBuilder(15 * ring.getPoints().size());
		PointPropertyEditor ppe = new PointPropertyEditor();	
		for(Point pnt : ring.getPoints()) {
			if (b.length() > 0) {
				b.append("; ");
			}
			ppe.setValue(pnt);
			b.append(ppe.getAsText());
		}
		return b.toString();
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		PointPropertyEditor ppe = new PointPropertyEditor();
		StringTokenizer tokens = new StringTokenizer(text, ";");
		List<Point> pts = new ArrayList<>();
		while(tokens.hasMoreTokens()) {
			String lltoken = tokens.nextToken();
			ppe.setAsText(lltoken);
			pts.add((Point) ppe.getValue());
		}
		LinearRing ring = new LinearRing(pts);
		setValue(ring);
	}
}
