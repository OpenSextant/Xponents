/****************************************************************************************
 *  PointOffsetVisitor.java
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
package org.opensextant.giscore.output.gdb;

import java.util.ArrayList;
import java.util.List;

import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.MultiPoint;
import org.opensextant.giscore.output.StreamVisitorBase;
import org.opensextant.giscore.output.shapefile.PolygonCountingVisitor;

/**
 * Figure out the point offsets for the components,
 * the total point count and the part count.
 * 
 * @author DRAND
 *
 */
public class GeoOffsetVisitor extends StreamVisitorBase {
	private final PolygonCountingVisitor pv = new PolygonCountingVisitor();
	private final List<Integer> offsets = new ArrayList<>();
	private int partCount = 0;
	private int total = 0;
	
	@Override
	public void visit(MultiPoint points) {
		offsets.add(total);
		// Semantics of MP aren't right for FileGDB, instead treat as 1 part
		total += points.getNumPoints();
		partCount += 1;
	}
	
	@Override
	public void visit(LinearRing ring) {
		offsets.add(total);
		ring.accept(pv);
		total += pv.getPointCount();
		pv.resetCount();
		partCount += ring.getNumParts();
	}	

	@Override
	public void visit(Line line) {
		offsets.add(total);
		total += line.getNumPoints();
		partCount += line.getNumParts();
	}

	public int getPartCount() {
		return partCount;
	}

	public int getTotal() {
		return total;
	}

	public List<Integer> getOffsets() {
		return offsets;
	}	
}
