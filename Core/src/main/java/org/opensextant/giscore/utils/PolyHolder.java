/****************************************************************************************
 *  PolyHolder.java
 *
 *  Created: Jan 4, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.geometry.Polygon;

/**
 * Holds rings to form polygons and allows simple manipulations. Meant to be
 * formed into lists and later collapsed into polygon objects. This object is
 * created empty and then filled with the first outer ring. The holder does not
 * care about the order of the points in the ring, only the containment.
 * <p>
 * The holder is used when accumulating rings into polygons with inner and outer
 * pieces. It is assumed that containment dictates which rings are inner and
 * which are outer rings. The set of rings is traversed. Let us call each ring
 * to be considered ring X, and the current outer ring in the holder ring O. If
 * O contains X then X is added as an inner ring in the holder. If X contains O
 * then O becomes another inner ring and X becomes the new O. If X and O do not
 * contain one another then X will be used to create another holder.
 * <p>
 * This algorithm is repeated until all rings are consumed. Then the holders are
 * used to create finished polygons. Each outer ring is checked and if the 
 * points are not clockwise they are reversed. Each inner ring is check and if
 * not counterclockwise they are reversed. Then the polygons are created.
 * 
 * @author DRAND
 */
public class PolyHolder {
	/**
	 * The current outer ring, must contain the inner rings
	 */
	private LinearRing outerRing;

	/**
	 * Zero or more inner rings that are contained by the outer ring.
	 */
	private final List<LinearRing> innerRings = new ArrayList<>();

	/**
	 * Empty ctor
	 */
	public PolyHolder() {
		//
	}
	
	/**
	 * @return get the current outer ring, may be {@code null}
	 */
	public LinearRing getOuterRing() {
		return outerRing;
	}
	
	/**
	 * Add a new inner ring
	 * @param ring the new inner ring, never {@code null}
	 */
	public void addInnerRing(LinearRing ring) {
		if (ring == null) {
			throw new IllegalArgumentException(
					"ring should never be null");
		}
		innerRings.add(ring);
	}
	
	/**
	 * Replace the current outer ring, adding the current outer ring (if any)
	 * to the current inner rings
	 * @param ring the new outer ring, never {@code null}
	 */
	public void setOuterRing(LinearRing ring) {
		if (ring == null) {
			throw new IllegalArgumentException(
					"ring should never be null");
		}
		if (outerRing != null) {
			innerRings.add(outerRing);
		}
		outerRing = ring;
	}
	
	/**
	 * @return create and return a polygon from the rings with the outer ring
	 * having its points in a clockwise direction and the inner rings having
	 * their points in a counter clockwise direction.
	 */
	public Polygon toPolygon() {
		if (!outerRing.clockwise()) {
			List<Point> pts = new ArrayList<>();
			pts.addAll(outerRing.getPoints());
			Collections.reverse(pts);
			outerRing = new LinearRing(pts);
		}
		for(int i = 0; i < innerRings.size(); i++) {
			LinearRing ring = innerRings.get(i);
			if (ring.clockwise()) {
				List<Point> pts = new ArrayList<>();
				pts.addAll(ring.getPoints());
				Collections.reverse(pts);
				innerRings.set(i, new LinearRing(pts));
			}
		}
		return new Polygon(outerRing, innerRings);
	}
}
