/***************************************************************************
 * $Id: MultiPolygons.java 1208 2011-06-20 14:01:08Z mathews $
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic3DBounds;
import org.opensextant.geodesy.UnmodifiableGeodetic2DBounds;
import org.opensextant.geodesy.UnmodifiableGeodetic3DBounds;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MultiPolygons class represents an ordered list of Polygon objects for input
 * and output in GIS formats such as ESRI Shapefiles or Google Earth KML files. In ESRI
 * Shapefiles, this type of object corresponds to a ShapeType of Polygon, but this class
 * imposes some additional constraints on the order of the base rings. In ESRI Shapefiles,
 * outer and inner rings of polygons can be included in any order, but here they are grouped
 * such that each outer is followed by its list of inners. In Shapefiles, this object
 * corresponds to a ShapeType of Polygon. This type of object does not exist as a primitive
 * in Google KML files, but it is a simple list of KML Polygon types represented as
 * as a MultiGeometry container with Polygon children.
 * <p>
 * Note if have polygons of mixed dimensions then MultiPolygons container is downgraded to 2d.
 *
 * @author Paul Silvey
 */
public class MultiPolygons extends Geometry implements Iterable<Polygon> {
	
	private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(MultiPolygons.class);

    @NotNull
    private List<Polygon> polygonList;

	/**
	 * Empty ctor for object io.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise object is invalid.
	 */
	public MultiPolygons() {
		polygonList = Collections.emptyList();
	}
	
    /**
     * The Constructor takes a list of Polygon Objects to initialize this {@code MultiPolygons} object.
     *
     * @param polygonList List of Polygons objects which define the parts of this MultiPolygons.
     * @throws IllegalArgumentException error if object is not valid.
     */
    public MultiPolygons(List<Polygon> polygonList) throws IllegalArgumentException {
        init(polygonList);
    }

    /**
     * Initialize
     * @param polygonList
	 * @throws IllegalArgumentException error if object is not valid.
     */
	private void init(List<Polygon> polygonList) {
		if (polygonList == null || polygonList.isEmpty())
            throw new IllegalArgumentException("MultiPolygons must contain " +
                    "at least 1 Polygons object");
		// Make sure all the polygons have the same number of dimensions (2D or 3D)
        is3D = polygonList.get(0).is3D();
        boolean mixedDims = false;
        for (Polygon nr : polygonList) {
            if (is3D != nr.is3D()) {
				mixedDims = true;
				break;
			}
        }
        if (mixedDims) {
            log.info("Polygons have mixed dimensionality: downgrading MultiPolygon to 2d");
            is3D = false;
        }
        this.polygonList = polygonList; 
	}

	/**
	 * This method returns an iterator for cycling through the Polygons in this Object.
	 * This class supports use of Java 'for each' syntax to cycle through the Polygons.
	 *
	 * @return Iterator over Polygons objects.
	 */
	public Iterator<Polygon> iterator() {
		return Collections.unmodifiableCollection(polygonList).iterator();
	}

	/**
	 * This method returns the {@code Polygon}s in this {@code MultiPolygons}.
	 * <br/>
	 * The returned collection is unmodifiable.
	 *
	 * @return Collection of the {@code Polygon} objects.
	 */
    @NotNull
	public Collection<Polygon> getPolygons() {
		return Collections.unmodifiableCollection(polygonList);
	}	

    /* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#computeBoundingBox()
	 */
	protected void computeBoundingBox() {
		bbox = null;
        if (is3D) {
            for (Polygon nr : polygonList) {
                Geodetic3DBounds bbox3 = (Geodetic3DBounds) nr.getBoundingBox();
                if (bbox == null) bbox = new Geodetic3DBounds(bbox3);
                else bbox.include(bbox3);
            }
        } else {
            for (Polygon nr : polygonList) {
                Geodetic2DBounds bbox2 = nr.getBoundingBox();
                if (bbox == null) bbox = new Geodetic2DBounds(bbox2);
                else bbox.include(bbox2);
            }
        }
		// make bbox unmodifiable
		bbox = is3D ? new UnmodifiableGeodetic3DBounds((Geodetic3DBounds)bbox)
				: new UnmodifiableGeodetic2DBounds(bbox);
	}

	/**
     * Tests whether this MultiPolygons geometry is a container for otherGeom's type.
     *
     * @param otherGeom the geometry from which to test if this is a container for
     * @return true if the geometry of this object is a "proper" container for otherGeom features
     *          which in this case is a Polygon.
     */
    public boolean containerOf(Geometry otherGeom) {
        return otherGeom instanceof Polygon;
    }

    /**
     * The toString method returns a String representation of this Object suitable for debugging
     *
     * @return String containing Geometry Object type, bounding coordintates, and number of parts.
     */
    public String toString() {
        return "Polygons within " + getBoundingBox() + " consists of " +
                polygonList.size() + " Polygons";

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
			ClassNotFoundException, InstantiationException, IllegalAccessException {
		super.readData(in);
		List<Polygon> plist = (List<Polygon>) in.readObjectCollection();
		// if for any reason list is null init() throws IllegalArgumentException
		init(plist);
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeObjectCollection(polygonList);
	}

	@Override
	public int getNumParts() {
		return polygonList.size();
	}
	
	@Override
    // @CheckForNull

	public Geometry getPart(int i) {
		return i >= 0 && i < polygonList.size() ? polygonList.get(i) : null;
	}

	@Override
	public int getNumPoints() {
		int pcount = 0;
		for(Polygon poly : polygonList) {
			pcount += poly.getNumPoints();
		}
		return pcount;
	}
	
	@Override
    @NotNull
	public List<Point> getPoints() {
		List<Point> rval = new ArrayList<>();
		for(Polygon poly : polygonList) {
			rval.addAll(poly.getPoints());
		}
		return rval;
	}	
}
