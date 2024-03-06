/****************************************************************************************
 *  CirclePropertyEditor.java
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

import java.beans.PropertyEditorSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensextant.giscore.geometry.Circle;
import org.opensextant.giscore.geometry.Point;

/**
 * Convert a circle value, which is a radius in meters plus a lat/lon value.
 * Example: 39000;33.12/45.2
 * 
 * @author DRAND
 *
 */
public class CirclePropertyEditor extends PropertyEditorSupport {
	private static final Pattern circlep = Pattern.compile("(.*);(.*)");
	
	@Override
	public String getAsText() {
		Circle circle = (Circle) getValue();
		PointPropertyEditor ppe = new PointPropertyEditor();
		ppe.setValue(new Point(circle.getCenter()));
		return circle.getRadius() + ";" + ppe.getAsText();
	}

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		Matcher m = circlep.matcher(text);
		if (m.matches()) {
			Double radius = Double.valueOf(m.group(1));
			PointPropertyEditor ppe = new PointPropertyEditor();
			ppe.setAsText(m.group(2));
			Point center = (Point) ppe.getValue();
			Circle c = new Circle(center.getCenter(), radius);
			setValue(c);
		} else {
			throw new IllegalArgumentException("Input " + text + " did not match a circle pattern");
		}
		
	}

}
