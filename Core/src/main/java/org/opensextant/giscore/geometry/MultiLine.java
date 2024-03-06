/***************************************************************************
 * $Id: MultiLine.java 1209 2011-06-20 14:05:56Z mathews $
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
import org.jetbrains.annotations.Nullable;
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
 * The MultiLine class represents an ordered list of Line objects for input and output in
 * GIS formats such as ESRI Shapefiles or Google Earth KML files.  In ESRI Shapefiles, this
 * object corresponds to a ShapeType of PolyLine.  This type of object does not exist as a
 * primitive in Google KML files.
 * <p>
 * Note if have lines of mixed dimensions then MultiLine container is downgraded to 2d.
 *
 * @author Paul Silvey
 */
public class MultiLine extends Geometry implements Iterable<Line> {
	
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(MultiLine.class);

    @NotNull
    private List<Line> lineList;

    /**
     * Empty ctor for object io.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise object is invalid.
     */
    public MultiLine() {
        lineList = Collections.emptyList();
    }

    /**
     * The Constructor takes a list of points and initializes a Geometry Object for this MultiPoint.
     *
     * @param lines List of Line objects to use for the parts of this MultiLine.
     * @throws IllegalArgumentException error if object is not valid.
     */
    public MultiLine(List<Line> lines) throws IllegalArgumentException {
        init(lines);
    }

    /**
     * This method returns an iterator for cycling through Lines in this MultiLine.
     * This class supports use of Java 'for each' syntax to cycle through the Lines.
     *
     * @return Iterator over Line objects.
     */
    @NotNull
    public Iterator<Line> iterator() {
        return Collections.unmodifiableList(lineList).iterator();
    }

	/**
	 * This method returns the {@code Line}s in this {@code MultiLine}.
	 * <br/>
	 * The returned collection is unmodifiable.
	 *
	 * @return Collection of the {@code Line} objects.
	 */
	@NotNull
	public Collection<Line> getLines() {
		return Collections.unmodifiableList(lineList);
	}

    /**
     * Initialize
     * @param lines
     * @throws IllegalArgumentException error if object is not valid.
     */
	private void init(List<Line> lines) {
		if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("MultiLine must contain at least 1 Line");
		// Make sure all the lines have the same number of dimensions (2D or 3D)
        is3D = lines.get(0).is3D();
        boolean mixedDims = false;
        for (Line ln : lines) {
            if (is3D != ln.is3D()) {
                mixedDims = true;
                is3D = false;
				break;
            }
        }
        if (mixedDims)
            log.info("MultiLine lines have mixed dimensionality: downgrading to 2d");
        lineList = lines;
	}
	
    /* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#computeBoundingBox()
	 */
	@Override
	protected void computeBoundingBox() {
        bbox = null;
        if (is3D) {
            for (Line ln : lineList) {
                Geodetic3DBounds bbox3 = (Geodetic3DBounds) ln.getBoundingBox();
                if (bbox == null) bbox = new Geodetic3DBounds(bbox3);
                else bbox.include(bbox3);
            }
        } else {
            for (Line ln : lineList) {
                Geodetic2DBounds bbox2 = ln.getBoundingBox();
                if (bbox == null) bbox = new Geodetic2DBounds(bbox2);
                else bbox.include(bbox2);
            }
        }
		// make bbox unmodifiable
		bbox = is3D ? new UnmodifiableGeodetic3DBounds((Geodetic3DBounds)bbox)
				: new UnmodifiableGeodetic2DBounds(bbox);
	}

	/**
     * Tests whether this MultiLine geometry is a container for otherGeom's type.
     *
     * @param otherGeom the geometry from which to test if this is a container for
     * @return true if the geometry of this object is a "proper" container for otherGeom features
     *          which in this case is a Line.
     */
    public boolean containerOf(Geometry otherGeom) {
        return otherGeom instanceof Line;
    }

    /**
     * The toString method returns a String representation of this Object suitable for debugging
     *
     * @return String containing Geometry Object type, bounding coordintates, and number of parts.
     */
    public String toString() {
        return "MultiLine within " + getBoundingBox() + " consists of " + lineList.size() + " Lines";
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
		List<Line> llist = (List<Line>) in.readObjectCollection();
		// if for any reason list is null init() throws IllegalArgumentException
		init(llist);
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeObjectCollection(lineList);
	}

	@Override
	public int getNumParts() {
		return lineList.size();
	}

	@Override
	@Nullable
	public Geometry getPart(int i) {
		return i >= 0 && i < lineList.size() ? lineList.get(i) : null;
	}
	
	@Override
	public int getNumPoints() {
		int count = 0;
		for(Line l : lineList) {
			count += l.getNumPoints();
		}
		return count;
	}

	@Override
	@NotNull
	public List<Point> getPoints() {
		List<Point> pts = new ArrayList<>();
		for(Line l : lineList) {
			pts.addAll(l.getPoints());
		}
		return Collections.unmodifiableList(pts);
	}
}
