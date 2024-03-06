/****************************************************************************************
 *  EsriTypeVisitor.java
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

import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.output.StreamVisitorBase;

/**
 * Determine the type from the geometry. Ignore the non-geometry objects.
 * See <a href="http://www.esri.com/library/whitepapers/pdfs/shapefile.pdf">...</a>
 *
 * @author DRAND
 */
public class EsriTypeVisitor extends StreamVisitorBase {
	/**
	 * Type, numbers determined by ESRI for shapefiles
	 */
	private int type = 0;

	public int getType() {
		return type;
	}

	@Override
	public void visit(Line line) {
		type = line.is3D() ? 13 : 3; // PolyLineZ, PolyLine
	}

	@Override
	public void visit(LinearRing ring) {
		type = ring.is3D() ? 15 : 5; // PolygonZ, Polygon
	}

	@Override
	public void visit(MultiLine multiLine) {
		type = multiLine.is3D() ? 13 : 3; // PolyLineZ, PolyLine
	}

	@Override
	public void visit(MultiLinearRings rings) {
		type = rings.is3D() ? 15 : 5; // PolygonZ, Polygon
	}

	@Override
	public void visit(MultiPoint multiPoint) {
		type = multiPoint.is3D() ? 18 : 8; // MultiPointZ, MultiPoint
	}

	@Override
	public void visit(MultiPolygons polygons) {
		type = polygons.is3D() ? 15 : 5; // PolygonZ, Polygon
	}

	@Override
	public void visit(Point point) {
		type = point.is3D() ? 11 : 1; // PointZ, Point
	}

	@Override
	public void visit(Polygon polygon) {
		type = polygon.is3D() ? 15 : 5; // PolygonZ, Polygon
	}
}
