/***************************************************************************
 * $Id: Line.java 1320 2011-07-01 19:46:50Z mathews $
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
 * The Line class represents an ordered list of Geodetic2DPoint points for input and output in
 * GIS formats such as ESRI Shapefiles or Google Earth KML files.  This type of object does
 * not exist as a primitive in ESRI Shapefiles, but it can be represented as an ESRI Polyline
 * with a single part.  In Google KML files, this object corresponds to a Geometry object of
 * type LineString. <p>
 *
 * <b>Notes/Limitations:</b>
 * - If have points of mixed dimensions then line is downgraded to 2d. <br/>
 *
 * @author Paul Silvey
 */
public class Line extends GeometryBase implements Iterable<Point> {

	private static final long serialVersionUID = 1L;
	
    private static final Logger log = LoggerFactory.getLogger(Line.class);

	@NotNull
    private List<Point> pointList;

    private boolean idlWrap;  // International Date Line Wrap

	/**
	 * Empty ctor for object io.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise object is invalid.
	 */
	public Line() {
		pointList = Collections.emptyList();
	}

    /**
     * The Constructor takes a list of points and initializes a Geometry Object for this Line.
     * <P>
     * Note list of points is copied by reference so caller must copy list if need to modify them after
     * this constructor is invoked.
     *
     * @param pts List of Geodetic2DPoint point objects to use for the parts of this Line.
     * @throws IllegalArgumentException error if object is not valid.
     */
    public Line(List<Point> pts) throws IllegalArgumentException {
        if (pts == null || pts.size() < 2)
            throw new IllegalArgumentException("Line must contain at least 2 Points");
        init(pts);
    }

	/**
     * This Constructor takes a bounding box and initializes a Line Object
     * for it. This points will be in a clockwise direction starting at the
	 * south-west corner. If bounding box forms a line (N-S or E-W) then
	 * line with have 2 points, otherwise it will have 5 points with the first
	 * and last point the same.
     *
     * @param box the bounding box to represent as a line, never null.
     * @throws IllegalArgumentException if the bounding box is null or a single point
     */
    public Line(Geodetic2DBounds box) {
        if (box == null)
            throw new IllegalArgumentException("box must be non-null");
		boolean pointCheck = box.getEastLon().equals(box.getWestLon());
		final List<Point> points;
		final Double alt;
		if (box instanceof Geodetic3DBounds) {
			Geodetic3DBounds bbox = (Geodetic3DBounds) box;
			alt = bbox.maxElev;
		} else alt = null;
		if (box.getNorthLat().equals(box.getSouthLat())) {
			if (pointCheck) {
				throw new IllegalArgumentException("Line must contain at least 2 Points");
			}
			log.debug("Bounding box is a line - north and south latitude are the same."); // east-west line
			points = new ArrayList<>(2);
			points.add(new Point(box.getSouthLat(), box.getWestLon(), alt));
			points.add(new Point(box.getSouthLat(), box.getEastLon(), alt));
		} else if (pointCheck) {
			log.debug("Bounding box is a line - east and west longitude are the same."); // north-south line
			points = new ArrayList<>(2);
			points.add(new Point(box.getNorthLat(), box.getWestLon(), alt));
			points.add(new Point(box.getSouthLat(), box.getWestLon(), alt));
		} else {
			points = new ArrayList<>(5);
			// add points in clock-wise direction starting at the south-west corner
			final Point firstPt = new Point(box.getSouthLat(), box.getWestLon(), alt);
			points.add(firstPt);
			points.add(new Point(box.getNorthLat(), box.getWestLon(), alt));
			points.add(new Point(box.getNorthLat(), box.getEastLon(), alt));
			points.add(new Point(box.getSouthLat(), box.getEastLon(), alt));
			points.add(firstPt);
		}
        init(points);
    }

    /**
     * This method returns an iterator for cycling through the Points in this Line.
     * This class supports use of Java 'for each' syntax to cycle through the Points.
     *
     * @return Iterator over Geodetic2DPoint point objects.
     */
    @NotNull
    public Iterator<Point> iterator() {
        return  Collections.unmodifiableList(pointList).iterator();
    }

	/**
	 * This method returns the points in this line.
	 * <br/>
	 * The returned collection is unmodifiable.
	 *
	 * @return Collection of the point objects.
	 */
    @NotNull
	public List<Point> getPoints() {
		return Collections.unmodifiableList(pointList);
	}

    /**
     * Initialize given a set of points
     * @param pts
     */
	private void init(List<Point> pts) {
		// Make sure all the points have the same number of dimensions (2D or 3D)
        is3D = pts.get(0).is3D();
        for (Point p : pts) {
            if (is3D != p.is3D()) {
                log.info("Line points have mixed dimensionality: downgrading line to 2d");
                is3D = false;
                break;
            }
        }
        pointList = pts;
	}   

    /* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#computeBoundingBox()
	 */
	@Override
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
            // change between points and one of the points is -180.0, we classify this Line
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
     * The toString method returns a String representation of this Object suitable for debugging.
     *
     * @return String containing Geometry Object type, bounding coordinates, and number of parts.
     */
    public String toString() {
        return "Line within " + getBoundingBox() + " consists of " + pointList.size() + " Points";
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
			IllegalAccessException {
		super.readData(in);
		idlWrap = in.readBoolean();
		List<Point> plist = (List<Point>) in.readObjectCollection();
		if (plist == null) {
			pointList = Collections.emptyList(); // normally should never be null
			is3D = false;
		} else
			init(plist);
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

	@Override
	public int getNumParts() {
		return 1;
	}

	@Override
	public int getNumPoints() {
		return pointList.size();
	}
}
