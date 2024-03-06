/***************************************************************************
 * $Id: LinearRing.java 1576 2012-07-11 00:44:21Z jgibson $
 *
 * (C) Copyright MITRE Corporation 2006-2008
 *
 * The program is provided "as is" without any warranty express or implied,
 * including the warranty of non-infringement and the implied warranties of
 * merchantability and fitness for a particular purpose.  The Copyright
 * owner will not be liable for any damages suffered by you as a result of
 * using the Program.  In no event will the Copyright owner be liable for
 * any special, indirect or consequential damages or lost profits even if
 * the Copyright owner has been advised of the possibility of their
 * occurrence.
 *
 * Portions of this file are controlled by the following license:
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ***************************************************************************/
package org.opensextant.giscore.geometry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DBounds;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.UnmodifiableGeodetic2DBounds;
import org.opensextant.geodesy.UnmodifiableGeodetic3DBounds;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LinearRing class represents an ordered list of Geodetic2DPoint points for input
 * and output in GIS formats such as ESRI Shapefiles or Google Earth KML files.
 * <p>
 * A LinearRing is a closed-loop, double-digitized list, which means the first and
 * last point must be the same. This type of object does not exist as a primitive
 * in ESRI Shapefiles. In Google KML files, this object corresponds to a Geometry
 * object of type LinearRing. Note that the topological predicates assume that
 * LinearRings do not wrap around the international date line.
 * <p>
 * A LinearRing object in itself may be a closed-line (i.e. polyline) shape or the inner/outer
 * boundary of a polygon so depends on the context of the Ring. The first ring in a Polygon
 * container is considered the outer boundary of that Polygon and any other Rings are used
 * as the inner boundary of a Polygon to create holes in the Polygon.
 * <p>
 * Notes/Restrictions: <br/>
 *   -LinearRings can have mixed dimensionality but such rings are downgraded to 2d. <br/>
 *   -LinearRing must contain at least 4 Points. <br/>
 *   -If validateTopology is selected then constructor enforces that the ring must start and end
 *    with the same point and the ring does not self-intersect. <br/>
 *   -Portions of the class were taken from the Apache Harmony project and are subject
 *    to the Apache license. Those portions were (probably) written by Denis M. Kishenko.
 *
 * @author Paul Silvey
 */
public class LinearRing extends GeometryBase implements Iterable<Point> {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LinearRing.class);

    @NotNull
    private List<Point> pointList;

    private boolean idlWrap;  // International Date Line Wrap

	/**
	 * Empty ctor for object io.  Constructor must be followed by call to {@code readData()}
	 * to initialize the object instance otherwise object is invalid.
	 */
	public LinearRing() {
		// list must be non-null but is still invalid (requires at least 4 pts)
		pointList = Collections.emptyList();
	}

	/**
     * This Constructor takes a list of points and initializes a Geometry Object for this Ring. By
     * default, it does not do topology validation.  To do validation, use alternate constructor.
     * <P>
     * Note points are copied by reference (assuming multiple geometries may share the same
     * list of points for smaller memory footprint) so modifications to point list after
     * constructor called will change the internal state of this instance which may leave
     * object in inconsistent state. Make copy of list if need to modify contents after
     * constructing a {@code LinearRing} instance.
     *
     * @param pts List of Geodetic2DPoint point objects to use for the vertices of this Ring
     * @throws IllegalArgumentException error if point list is empty
     *          or number of points is less than 4
     */
    public LinearRing(List<Point> pts) throws IllegalArgumentException {
        init(pts, false);
    }

    /**
     * This Constructor takes a list of points and initializes a Geometry Object for this Ring,
     * performing topology validation if requested.
     * <P>
     * Note points are copied by reference so caller must copy lists if need to modify them after
     * this constructor is invoked.
     *
     * @param pts              List of Geodetic2DPoint objects to use as Ring vertices
     * @param validateTopology boolean flag indicating that Ring topology should be validated
     * @throws IllegalArgumentException error if point list is empty
     *          or number of points is less than 4
     */
    public LinearRing(List<Point> pts, boolean validateTopology) throws IllegalArgumentException {
        init(pts, validateTopology);
    }

    /**
     * This Constructor takes a bounding box and initializes a LinearRing Object
     * for it. This ring points will be clockwise direction starting at the
	 * south-west corner.
     *
     * @param box the bounding box to represent as a ring, never null.
     * @throws IllegalArgumentException if the bounding box is null, a point or a line
     */
    public LinearRing(Geodetic2DBounds box) {
        if (box == null)
            throw new IllegalArgumentException("box must be non-null");
        if (box.getEastLon().equals(box.getWestLon())) {
            log.error("Bounding box not a polygon - east and west longitude are the same.");
			throw new IllegalArgumentException("LinearRing must contain at least 4 Points");
		}
		if (box.getNorthLat().equals(box.getSouthLat())) {
            log.error("Bounding box not a polygon - north and south latitude are the same.");
			throw new IllegalArgumentException("LinearRing must contain at least 4 Points");
		}

        double elev = 0;
        boolean is3d = false;
        if (box instanceof Geodetic3DBounds) {
            Geodetic3DBounds bbox = (Geodetic3DBounds)box;
            // use max elevation as elevation of points
            elev = bbox.maxElev; // (bbox.minElev + bbox.maxElev) / 2.0;
            // using 1e-3 meters for elevation test for precision up to 1 millimeter (below this is treated as 2D with 0 elevation)
            is3d = Math.abs(elev) > 1e-3;
        }

        final List<Point> points = new ArrayList<>(5);
		final Double elevation = is3d ? elev : null;
		final Point firstPt = new Point(box.getSouthLat(), box.getWestLon(), elevation);
        points.add(firstPt);
		points.add(new Point(box.getNorthLat(), box.getWestLon(), elevation));
		points.add(new Point(box.getNorthLat(), box.getEastLon(), elevation));
		points.add(new Point(box.getSouthLat(), box.getEastLon(), elevation));
        points.add(firstPt);
        init(points, false);
    }

	/**
     * This method returns an iterator for cycling through the Points in this Ring.
     * This class supports use of Java 'for each' syntax to cycle through the Points.
     *
     * @return Iterator over Geodetic2DPoint point objects
     */
    @NotNull
    public Iterator<Point> iterator() {
        return Collections.unmodifiableList(pointList).iterator();
    }

	/**
	 * This method returns the points in this ring.
	 * <br/>
	 * The returned collection is unmodifiable.
	 *
	 * @return Collection of the point objects.
	 */
    @NotNull
	public List<Point> getPoints() {
		return Collections.unmodifiableList(pointList);
	}

    // This method will check that this LinearRing is closed and non-self-intersecting.
    private void validateTopology(List<Point> pts) throws IllegalArgumentException {
        int n = pts.size();
        // Verify that ring is closed, i.e. that beginning and ending point are equal
        if (!pts.get(0).equals(pts.get(n - 1)))
            throw new IllegalArgumentException("LinearRing must start and end with the same point.");

        // Look at each line segment in turn, and compare to every other non-neighbor
        // For neighbor segments, make sure distance to non-shared endpoint is positive.
        // This requires (n*(n-1)/2) comparisons
		// TODO: if points are at polar projection and wrap IDL then test fails
        Geodetic2DPoint gp1, gp2, gp3, gp4;
        for (int i = 0; i < n - 2; i++) {
            gp1 = pts.get(i).asGeodetic2DPoint();
            double x1 = gp1.getLongitude().inRadians();
            double y1 = gp1.getLatitude().inRadians();
            gp2 = pts.get(i + 1).asGeodetic2DPoint();
            double x2 = gp2.getLongitude().inRadians();
            double y2 = gp2.getLatitude().inRadians();
            for (int j = i + 1; j < n - 1; j++) {
                gp3 = pts.get(j).asGeodetic2DPoint();
                double x3 = gp3.getLongitude().inRadians();
                double y3 = gp3.getLatitude().inRadians();
                gp4 = pts.get(j + 1).asGeodetic2DPoint();
                double x4 = gp4.getLongitude().inRadians();
                double y4 = gp4.getLatitude().inRadians();
                boolean inv;
                if ((j - i) == 1) {
                    // make sure non-zero distance from (x1, y1)->(x2, y2) to (x4, y4)
                    inv = (ptLineDist(x1, y1, x2, y2, x4, y4) == 0.0);
                } else if ((i == 0) && (j == (n - 2))) {
                    // make sure non-zero distance from (x1, y1)->(x2, y2) to (x3, y3)
                    inv = (ptLineDist(x1, y1, x2, y2, x3, y3) == 0.0);
                } else {
                    // make sure non-adjacent segments do not intersect
                    inv = linesIntersect(x1, y1, x2, y2, x3, y3, x4, y4);
                }
                if (inv) {
                    if (log.isDebugEnabled())
                        log.debug(String.format("LinearRing self-intersects at i=%d j=%d", i, j));
                    throw new IllegalArgumentException("LinearRing cannot self-intersect");
                }
            }
        }
    }

    /**
     * Private init method shared by Constructors
     * @param pts
     * @param validateTopology
     * @throws IllegalArgumentException error if point list is empty
     *          or number of points is less than 4
     */
    private void init(List<Point> pts, boolean validateTopology) throws IllegalArgumentException {
        if (pts == null || pts.size() < 4)
            throw new IllegalArgumentException("LinearRing must contain at least 4 Points");
        if (validateTopology) validateTopology(pts);
        else {
            int n = pts.size();
            // ring expected to be closed, i.e. that beginning and ending point are equal
            if (!pts.get(0).equals(pts.get(n - 1))) {
                log.warn("LinearRing should start and end with the same point, closing the ring");
                // Close it
                ArrayList<Point> copypts = new ArrayList<>(pts.size() + 1);
                copypts.addAll(pts);
                copypts.add(pts.get(0));
                pts = copypts;
            }
        }
        // Make sure all the points have the same number of dimensions (2D or 3D)
        is3D = pts.get(0).is3D();
        for (Point p : pts) {
            if (is3D != p.is3D()) {
                log.info("LinearRing points have mixed dimensionality: downgrading ring to 2d");
                is3D = false;
                break;
            }
        }
        pointList = pts;
    }

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#computeBoundingBox()
	 */
	protected void computeBoundingBox() {
        Geodetic2DPoint gp1 = pointList.get(0).asGeodetic2DPoint();
        bbox = is3D ? new Geodetic3DBounds((Geodetic3DPoint) gp1) : new Geodetic2DBounds(gp1);

        Geodetic2DPoint gp2;
        double lonRad1, lonRad2;
        idlWrap = false;
        for (Point p : pointList) {
            gp2 = p.asGeodetic2DPoint();
            bbox.include(gp2);
            // Test for Longitude wrap at International Date Line (IDL)
            // Line segments always connect following the shortest path around the globe,
            // and we assume lines are clipped at the IDL crossing, so if there is a sign
            // change between points and one of the points is -180.0, we classify this LinearRing
            // as having wrapped. This allows the -180 to be written as +180 on export,
            // to satisfy GIS tools that expect this.
            lonRad1 = gp1.getLongitude().inRadians();
            lonRad2 = gp2.getLongitude().inRadians();
            // It is a wrap if any segment that changes lon sign has an endpoint on the line
            if (((lonRad1 < 0.0 && lonRad2 >= 0.0) || (lonRad2 < 0.0 && lonRad1 >= 0.0)) &&
                    (lonRad1 == -Math.PI || lonRad2 == -Math.PI)) idlWrap = true;
            gp1 = gp2;
        }
		// make bbox unmodifiable
		bbox = is3D ? new UnmodifiableGeodetic3DBounds((Geodetic3DBounds)bbox)
				: new UnmodifiableGeodetic2DBounds(bbox);
	}

	@Override
	public int getNumParts() {
		return 1;
	}

	@Override
	public int getNumPoints() {
		return pointList.size();
	}

    /**
     * This predicate method is used to tell if this Ring has positive Longitude points
     * that are part of segments which are clipped at the International Date Line (IDL)
     * (+/- 180 Longitude). If so, -180 values may need to be written as +180 by
     * export methods to satisfy GIS tools that expect this.
     *
     * @return boolean value indicating whether this ring is clipped at the IDL
     */
    public boolean clippedAtDateLine() {
        // must calculate bounding box to set idlWrap
        if (bbox == null) computeBoundingBox();
        return idlWrap;
    }

    /**
     * This predicate method tests whether this Ring object is in clockwise point order or not.
     *
     * @return true if this Ring's points are in clockwise order, false otherwise
     */
    public boolean clockwise() {
        Geodetic2DPoint gp1, gp2;
        double doubleArea = 0.0;
        for (int i = 0; i < pointList.size() - 1; i++) {
            gp1 = pointList.get(i).asGeodetic2DPoint();
            gp2 = pointList.get(i + 1).asGeodetic2DPoint();
            doubleArea += gp1.getLongitude().inRadians() * gp2.getLatitude().inRadians();
            doubleArea -= gp1.getLatitude().inRadians() * gp2.getLongitude().inRadians();
        }
        return (doubleArea < 0);
    }

    /**
     * This predicate method determines whether this Ring contains a test point, by counting
     * line crossings and determining their parity.  This version of the algorithm adapted from
     * code written by W. Randolph Franklin.
     *
     * @param p Geodetic2DPoint test point
     * @return true if the test point is inside of this ring of points
     */
    public boolean contains(Geodetic2DPoint p) {
        boolean in = false;
        double x = p.getLongitude().inRadians();
        double y = p.getLatitude().inRadians();
        Geodetic2DPoint gp1, gp2;
        for (int i = 0; i < pointList.size() - 1; i++) {
            gp1 = pointList.get(i).asGeodetic2DPoint();
            gp2 = pointList.get(i + 1).asGeodetic2DPoint();
            double xi = gp1.getLongitude().inRadians();
            double yi = gp1.getLatitude().inRadians();
            double xj = gp2.getLongitude().inRadians();
            double yj = gp2.getLatitude().inRadians();
            if ((((yi <= y) && (y < yj)) || ((yj <= y) && (y < yi))) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi))
                in = !in;
        }
        return in;
    }

    /**
     * This predicate method determines whether this Ring overlaps another Ring (that).
     *
     * @param that Ring object to test for overlapping with this Ring
     * @return true if this Ring overlaps the specified Ring 'that'
     */
    public boolean overlaps(LinearRing that) {
        // Compare each segment in this ring to every segment in that ring to see if they cross.
        // Short-circuit exit as soon as any pair of segments being compared cross.
        Geodetic2DPoint gp1, gp2, gp3, gp4;
        int n1 = this.pointList.size();
        int n2 = that.pointList.size();
        for (int i = 0; i < n1 - 1; i++) {
            gp1 = this.pointList.get(i).asGeodetic2DPoint();
            double x1 = gp1.getLongitude().inRadians();
            double y1 = gp1.getLatitude().inRadians();
            gp2 = this.pointList.get(i + 1).asGeodetic2DPoint();
            double x2 = gp2.getLongitude().inRadians();
            double y2 = gp2.getLatitude().inRadians();
            for (int j = 0; j < n2 - 1; j++) {
                gp3 = that.pointList.get(j).asGeodetic2DPoint();
                double x3 = gp3.getLongitude().inRadians();
                double y3 = gp3.getLatitude().inRadians();
                gp4 = that.pointList.get(j + 1).asGeodetic2DPoint();
                double x4 = gp4.getLongitude().inRadians();
                double y4 = gp4.getLatitude().inRadians();
                if (linesIntersect(x1, y1, x2, y2, x3, y3, x4, y4))
                    return true;
            }
        }
        return false;
    }

    /**
     * This predicate method determines whether this Ring completely contains another Ring
     * (that).  It first makes sure that no pair of line segments between the two rings
     * intersect, and if that is true, it then checks to see if a single point of the
     * proposed inner Ring is in fact inside this Ring.
     *
     * @param that Ring object to test for containment within this Ring
     * @return true if this Ring completely contains the specified Ring 'that'
     */
    public boolean contains(LinearRing that) {
        // If not overlapping, then all points are either in or they're out, so only test one
        return (!this.overlaps(that) &&
                this.contains(that.pointList.get(0).asGeodetic2DPoint()));
    }

    /**
     * This predicate method determines whether the specified Ring (that) has any area in common with
     * this Ring, that is, whether they intersect or not.
     *
     * @param that Ring to test for intersection with this Ring
     * @return true if the specified Ring intersects this Ring
     */
    public boolean intersects(LinearRing that) {
        // If not overlapping, then see if a point from this is in that, or vice versa
        return (this.overlaps(that) ||
                this.contains(that.pointList.get(0).asGeodetic2DPoint()) ||
                that.contains(this.pointList.get(0).asGeodetic2DPoint()));
    }

	/**
     * The toString method returns a String representation of this Object suitable for debugging
     *
     * @return String containing Geometry Object type, bounding coordintates, and number of parts
     */
    public String toString() {
        return "LinearRing within " + getBoundingBox() + " consists of " +
                pointList.size() + " Points";
    }
    
    public void accept(IStreamVisitor visitor) {
    	visitor.visit(this);
    }
    
	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException, IllegalArgumentException {
		super.readData(in);
		idlWrap = in.readBoolean();
		List<Point> plist = (List<Point>) in.readObjectCollection();
		// if for any reason list is null or # points < 4 init() throws IllegalArgumentException
		init(plist, false);
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeBoolean(idlWrap);
		out.writeObjectCollection(pointList);
	}
	
	/**
	 * Taken from Apache Harmony's implementation of Line2D.
	 *
	 * @see java.awt.geom.Line2D
	 */
    private static double ptLineDistSq(double x1, double y1, double x2, double y2, double px, double py) {
        x2 -= x1;
        y2 -= y1;
        px -= x1;
        py -= y1;
        double s = px * y2 - py * x2;
        return s * s / (x2 * x2 + y2 * y2);
    }

	/**
	 * Taken from Apache Harmony's implementation of Line2D.
	 *
	 * @see java.awt.geom.Line2D
	 */
    private static double ptLineDist(double x1, double y1, double x2, double y2, double px, double py) {
        return Math.sqrt(ptLineDistSq(x1, y1, x2, y2, px, py));
    }

	/**
	 * Taken from Apache Harmony's implementation of Line2D.
	 *
	 * @see java.awt.geom.Line2D
	 */
    private static boolean linesIntersect(double x1, double y1, double x2,
            double y2, double x3, double y3, double x4, double y4)
    {
        /*
         * A = (x2-x1, y2-y1) B = (x3-x1, y3-y1) C = (x4-x1, y4-y1) D = (x4-x3,
         * y4-y3) = C-B E = (x1-x3, y1-y3) = -B F = (x2-x3, y2-y3) = A-B
         *
         * Result is ((AxB) * (AxC) <=0) and ((DxE) * (DxF) <= 0)
         *
         * DxE = (C-B)x(-B) = BxB-CxB = BxC DxF = (C-B)x(A-B) = CxA-CxB-BxA+BxB =
         * AxB+BxC-AxC
         */

        x2 -= x1; // A
        y2 -= y1;
        x3 -= x1; // B
        y3 -= y1;
        x4 -= x1; // C
        y4 -= y1;

        double AvB = x2 * y3 - x3 * y2;
        double AvC = x2 * y4 - x4 * y2;

        // Online
        if (AvB == 0.0 && AvC == 0.0) {
            if (x2 != 0.0) {
                return
                    (x4 * x3 <= 0.0) ||
                    ((x3 * x2 >= 0.0) &&
                     (x2 > 0.0 ? x3 <= x2 || x4 <= x2 : x3 >= x2 || x4 >= x2));
            }
            if (y2 != 0.0) {
                return
                    (y4 * y3 <= 0.0) ||
                    ((y3 * y2 >= 0.0) &&
                     (y2 > 0.0 ? y3 <= y2 || y4 <= y2 : y3 >= y2 || y4 >= y2));
            }
            return false;
        }

        double BvC = x3 * y4 - x4 * y3;

        return (AvB * AvC <= 0.0) && (BvC * (AvB + BvC - AvC) <= 0.0);
    }
}
