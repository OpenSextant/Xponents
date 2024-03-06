/****************************************************************************************
 *  PointCountingVisitor.java
 *
 *  Created: Jul 30, 2009
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
package org.opensextant.giscore.output.shapefile;

import java.util.List;

import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.StreamVisitorBase;

/**
 * Visit a set of polygon and ring objects and sum the points. 
 * 
 * @author DRAND
 *
 */
public class PolygonCountingVisitor extends StreamVisitorBase {
	private int pointCount = 0;
	
	@Override
	public void visit(LinearRing ring) {
		List<Point> pts = ring.getPoints();
		int size = pts.size();
		if (size > 0) {
			pointCount += size;
		}
	}

	/**
	 * @return the point count after the accept method is called on the given
	 * geometry(ies).
	 */
	public int getPointCount() {
		return pointCount;
	}

	/**
	 * Allow reuse
	 */
	public void resetCount() {
		pointCount = 0;
	}
}
