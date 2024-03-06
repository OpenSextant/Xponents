/***************************************************************************
 * $Id: Point.java 1316 2011-07-01 18:18:32Z mathews $
 *
 * (C) Copyright MITRE Corporation 2006-2007
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
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.opensextant.geodesy.*;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * The Point class represents a single Geodetic point (Geodetic2DPoint or
 * Geodetic3DPoint) for input and output in GIS formats such as ESRI Shapefiles
 * or Google Earth KML files. In ESRI Shapefiles, this object corresponds to a
 * ShapeType of Point or PointZ. In Google KML files, this object corresponds to
 * a Geometry object of type Point. <p>
 *
 * Note Point extends GeometryBase including tessellate and drawOrder fields but those
 * fields are not applicable to the Point geometry and ignored.
 *
 * @author Paul Silvey
 */
public class Point extends GeometryBase {

	private static final long serialVersionUID = 1L;

	@NotNull
	private Geodetic2DPoint pt; // or extended Geodetic3DPoint

	/**
	 * Empty ctor only for object IO.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise object is invalid.
	 * @see SimpleObjectInputStream
	 * @see SimpleObjectOutputStream
	 */
	public Point() {
		pt = new Geodetic2DPoint();
	}

	/**
	 * Ctor, create a point given a lat and lon value in a WGS84 spatial
	 * reference system.
	 * 
	 * @param lat
	 *            the latitude in degrees
	 * @param lon
	 *            the longitude in degrees
	 */
	public Point(double lat, double lon) {
		this(lat, lon, false);
	}

	/**
	 * Create a point given a lat and lon value in a WGS84 spatial
	 * reference system.
	 *
	 * @param lat
	 *            the latitude, never null
	 * @param lon
	 *            the longitude, never null
	 * @throws NullPointerException if latitude or longitude are null
	 */
	public Point(Latitude lat, Longitude lon) {
		this(new Geodetic2DPoint(lon, lat));
	}

	/**
	 * Ctor, create a point given a lat and lon value in a WGS84 spatial
	 * reference system.
	 * 
	 * @param lat
	 *            the latitude in degrees
	 * @param lon
	 *            the longitude in degrees
	 * @param is3d
	 *            a three d point if {@code true}
	 * 
	 */
	public Point(double lat, double lon, boolean is3d) {
		this(is3d ? new Geodetic3DPoint(new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES), 0.0) : new Geodetic2DPoint(
				new Longitude(lon, Angle.DEGREES), new Latitude(lat,
						Angle.DEGREES)));
	}

    /**
     * This constructor takes a Longitude, Latitude, and an elevation value in meters.
     * The default elevation reference point (vertical datum) is the tangent surface plane
     * touching the WGS84 Ellipsoid at the given longitude and latitude.
	 *
	 * @param lat
	 *            the latitude in degrees
	 * @param lon
	 *            the longitude in degrees
	 * @param elevation
     *              elevation in meters from the assumed reference point
	 */
	public Point(double lat, double lon, double elevation) {
        this(new Geodetic3DPoint(new Longitude(lon, Angle.DEGREES),
				new Latitude(lat, Angle.DEGREES), elevation));
    }

	/**
	 * This constructor takes a Longitude, Latitude, and an elevation value in meters.
	 *
	 * @param lat       the latitude in degrees, never null
	 * @param lon       the longitude in degrees, never null
	 * @param elevation elevation in meters from the assumed reference point
	 *                  if elevation is null then Geodetic2DPoint is created otherwise
	 *                  Geodetic3DPoint is created with the elvation.
	 * @throws NullPointerException if lat or lon are null
	 */
	public Point(Latitude lat, Longitude lon, Double elevation) {
		this(elevation == null
				? new Geodetic2DPoint(lon, lat)
				: new Geodetic3DPoint(lon, lat, elevation));
	}

	/**
	 * The Constructor takes a GeoPoint that is either a {@code Geodetic2DPoint} or a
	 * {@code Geodetic3DPoint} and initializes a Geometry object for it.
     * <P>
     * Note {@code GeoPoint} object is copied by reference so caller must copy this
     * point and/or construct a new {@code GeoPoint} object if need to modify its
     * state after this constructor is invoked. Assumes GeoPoints are immutable
     * if used in context of Point geometries.
	 * 
	 * @param gp
	 *            GeoPoint to initialize this Point with (must be Geodetic form)
	 * @throws IllegalArgumentException
	 *             error if object is not valid.
	 */
	public Point(GeoPoint gp) throws IllegalArgumentException {
		if (gp == null)
			throw new IllegalArgumentException("Point must not be null");
		if (gp instanceof Geodetic3DPoint) {
			is3D = true;
		} else if (gp instanceof Geodetic2DPoint) {
			is3D = false;
		} else
			throw new IllegalArgumentException("Point must be in Geodetic form");
		pt = (Geodetic2DPoint) gp;
	}

	/**
	 * This method returns a Geodetic2DPoint that is at the center of this
	 * Geometry object's Bounding Box.
	 * 
	 * @return Geodetic2DPoint at the center of this Geometry object
	 */
    @Override
    @NotNull
	public Geodetic2DPoint getCenter() {
		// for point feature just return the point
		return pt;
	}
	
	/* (non-Javadoc)
	 * @see org.opensextant.giscore.geometry.Geometry#computeBoundingBox()
	 */
	protected void computeBoundingBox() {
        // make bbox unmodifiable
        bbox = is3D ? new UnmodifiableGeodetic3DBounds(new Geodetic3DBounds((Geodetic3DPoint) pt))
                : new UnmodifiableGeodetic2DBounds(new Geodetic2DBounds(pt));
	}

	/**
	 * This method simply returns the Geodetic point object (modeled like a cast
	 * operation).
	 * 
	 * @return the Geodetic2DPoint object that defines this Point.
	 */
    @NotNull
	public Geodetic2DPoint asGeodetic2DPoint() {
		return pt;
	}

	/**
	 * This method returns a hash code for this Point object.
	 * 
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return pt.hashCode();
	}

	/**
	 * This method is used to test whether two Points are equal in the sense
	 * that have the same coordinate value.
	 * 
	 * @param that
	 *            Point to compare against this one.
	 * @return true if specified Point is equal in value to this Point.
	 */
	public boolean equals(Point that) {
		return that != null && this.pt.equals(that.pt);
	}

	/**
	 * This method is used to test whether two Points are equal in the sense
	 * that have the same coordinate value.
	 * 
	 * @param that
	 *            Point to compare against this one.
	 * @return true if specified Point is equal in value to this Point.
	 */
	@Override
	public boolean equals(Object that) {
		return (that instanceof Point) && this.equals((Point) that);
	}

	/**
	 * The toString method returns a String representation of this Object
	 * suitable for debugging
	 * 
	 * @return String containing Geometry Object type, bounding coordintates,
	 *         and number of parts.
	 */
	public String toString() {
		return "Point at (" + pt.getLongitude() + ", " + pt.getLatitude() + ")";
	}

	public void accept(IStreamVisitor visitor) {
		visitor.visit(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.geometry.Geometry#readData(org.opensextant.giscore.utils.
	 * SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		boolean is3d = in.readBoolean();
		double elevation = 0.0;
		if (is3d) {
			elevation = in.readDouble();
		}
		Angle lat = readAngle(in);
		Angle lon = readAngle(in);
		if (is3d)
			pt = new Geodetic3DPoint(new Longitude(lon), new Latitude(lat), elevation);
		else
			pt = new Geodetic2DPoint(new Longitude(lon), new Latitude(lat));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.geometry.Geometry#writeData(org.opensextant.giscore.utils
	 * .SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		boolean is3d = pt instanceof Geodetic3DPoint;
		out.writeBoolean(is3d);
		if (is3d) {
			out.writeDouble(((Geodetic3DPoint) pt).getElevation());
		}
		writeAngle(out, pt.getLatitude());
		writeAngle(out, pt.getLongitude());
	}

	@Override
	public int getNumParts() {
		return 1;
	}

	@Override
	public int getNumPoints() {
		return 1;
	}

	@Override
    @NotNull
	public List<Point> getPoints() {
		return Collections.singletonList(new Point(pt));
	}

}
