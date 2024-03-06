/***************************************************************************
 * $Id: MultiPoint.java 1214 2011-06-20 15:05:39Z mathews $
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
import org.jetbrains.annotations.Nullable;
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
 * The MultiPoint class represents a list of geodetic Points for input and output in
 * GIS formats such as ESRI Shapefiles or Google Earth KML files.  In ESRI Shapefiles,
 * this object corresponds to a ShapeType of MultiPoint. This type of object does not
 * exist as a primitive in Google KML files, so it is just written as a list of Points.
 * <p>
 * Note if have points of mixed dimensions then MultiPoint container is downgraded to 2d.
 *
 * @author Paul Silvey
 */
public class MultiPoint extends Geometry implements Iterable<Point> {
	
	private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(MultiPoint.class);

    @NotNull
    private List<Point> pointList;

    /**
	 * Empty ctor for object io.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise MultiPoint instance will remain empty.
	 */
	public MultiPoint() {
		pointList = Collections.emptyList();
	}

    /**
     * The Constructor takes a list of points and initializes a Geometry Object for this MultiPoint.
     *
     * @param pts List of Geodetic2DPoint point objects to use for the parts of this MultiPoint.
     * @throws IllegalArgumentException error if object is not valid.
     */
    public MultiPoint(List<Point> pts) throws IllegalArgumentException {
        if (pts == null || pts.isEmpty())
            throw new IllegalArgumentException("MultiPoint must contain at least 1 Point");
        init(pts);
    }

    /**
     * This method returns an iterator for cycling through the geodetic Points in this MultiPoint.
     * This class supports use of Java 'for each' syntax to cycle through the geodetic Points.
     *
     * @return Iterator over geodetic Point objects.
     */
    @NotNull
    public Iterator<Point> iterator() {
        return Collections.unmodifiableCollection(pointList).iterator();
    }

	/**
	 * This method returns the {@code Point}s in this {@code MultiPoint}.
	 * <br/>
	 * The returned collection is unmodifiable.
	 *
	 * @return Collection of the {@code Point} objects.
	 */
    @NotNull
	public List<Point> getPoints() {
		return Collections.unmodifiableList(pointList);
	}

    /**
     * Initialize
     * @param pts
     */
	private void init(List<Point> pts) {
		// Make sure all the points have the same number of dimensions (2D or 3D)
        is3D = pts.get(0).is3D();
        for (Point p : pts) {
            if (is3D != p.is3D()) {
                log.info("MultiPoint points have mixed dimensionality: downgrading to 2d");
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
		Geodetic2DPoint gp = pointList.get(0).asGeodetic2DPoint();
		bbox = is3D ? new Geodetic3DBounds((Geodetic3DPoint) gp)
				: new Geodetic2DBounds(gp);
		for (Point p : pointList)
			bbox.include(p.asGeodetic2DPoint());
		// make bbox unmodifiable
		bbox = is3D ? new UnmodifiableGeodetic3DBounds((Geodetic3DBounds) bbox)
				: new UnmodifiableGeodetic2DBounds(bbox);
	}

	/**
     * Tests whether this MultiPoint geometry is a container for otherGeom's type.
     *
     * @param otherGeom the geometry from which to test if this is a container for
     * @return true if the geometry of this object is a "proper" container for otherGeom features
     *          which in this case is a Point.
     */
    public boolean containerOf(Geometry otherGeom) {
        return otherGeom instanceof Point;
    }

    /**
     * The toString method returns a String representation of this Object suitable for debugging
     *
     * @return String containing Geometry Object type, bounding coordintates, and number of parts.
     */
    public String toString() {
        return "MultiPoint within " + getBoundingBox() + " consists of " + pointList.size() + " Points";
    }
    
    public void accept(IStreamVisitor visitor) {
    	visitor.visit(this);
    }
    
	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		final int pcount = in.readInt();
		if (pcount == 0) {
			pointList = Collections.emptyList();
			is3D = false;
		} else {
			List<Point> plist = new ArrayList<>(pcount);
			for(int i = 0; i < pcount; i++) {
				plist.add((Point) in.readObject());
			}
			init(plist);
		}
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeInt(pointList.size());
		for(Point p : pointList) {
			out.writeObject(p);
		}
	}

	@Override
	public int getNumParts() {
		return pointList.size();
	}
	
	@Override
    @Nullable
	public Geometry getPart(int i) {
		return i >= 0 && i < pointList.size() ? pointList.get(i) : null;
	}

	@Override
	public int getNumPoints() {
		return getNumParts(); // Happily the count of parts and points is the same
	}
}
