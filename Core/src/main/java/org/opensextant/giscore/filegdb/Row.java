/****************************************************************************************
 *  Row.java
 *
 *  Created: Nov 9, 2012
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2012
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
package org.opensextant.giscore.filegdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.MultiLine;
import org.opensextant.giscore.geometry.MultiPoint;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.geometry.Polygon;

/**
 * The Row class encapsulates the FileGDB row class to efficiently provide the
 * functionality. It caches data to avoid crossing the JNI boundary more than
 * necessary, and uses simple Java constructs to move data across while 
 * reorganizing the data in a; more java native fashion locally.
 * 
 * @author DRAND
 */
public class Row extends GDB {

	private Map<String, Object> attrs;
	private Geometry geo;
	protected Table table;

	protected Row(Table t) {
		this.table = t;
	}
	
	/**
	 * @return the OID for the row
	 */
	public native Integer getOID();
	
	/**
	 * @return the geometry associated with the row if the row is part of a
	 * feature class or {@code null} if no geometry is set on the row.
	 */
	public Geometry getGeometry() {
		if (geo == null) {
			Object[] shapeInfo = getGeo();
			// Decode
			Short type = (Short) shapeInfo[0];
			Boolean hasz = (Boolean) shapeInfo[1];
			List<Point>[] lists;
			switch(type) {
			case 0: { // Point
				MutableInt ptr = new MutableInt(4);
				geo = getPoint(ptr, shapeInfo, hasz);
				break;
			}
			case 1: // Multipoint
				lists = getPointLists(shapeInfo, hasz);
				geo = new MultiPoint(lists[0]);
				break;
			case 2: // Polyline
				lists = getPointLists(shapeInfo, hasz);
				List<Line> lines = new ArrayList<>();
				for(List<Point> pts : lists) {
					lines.add(new Line(pts));
				}
				geo = new MultiLine(lines);
				break;
			case 3: // Polygon
				lists = getPointLists(shapeInfo, hasz);
				if (lists.length == 0) break;
				LinearRing outerRing = new LinearRing(lists[0]);
				List<LinearRing> innerRings = new ArrayList<>();
				for(int i = 1; i < lists.length; i++) {
					innerRings.add(new LinearRing(lists[i]));
				}
				geo = new Polygon(outerRing, innerRings, true);
				break;
			case 4: // General Polyline, Polygon
				break;
			case 5: // Patches
				// Unsupported
			default:
				// Ignore
			}
		}
		return geo;
	}
	
	/**
	 * For all multipart geometries, the first several elements in the 
	 * shapeInfo array are the shapeType, the hasz boolean, the point
	 * count and the part count. The next two things are then the
	 * part array and the point array. This method handles turning
	 * the part array and point array into an array of point lists.
	 * @param shapeInfo
	 * @param hasz
	 * @return
	 */
	private List<Point>[] getPointLists(Object[] shapeInfo, boolean hasz) {
		int pointcount = (Integer) shapeInfo[2];
		int partcount = (Integer) shapeInfo[3];
		int[] partarray = new int[partcount == 0 ? 1 : partcount];
		MutableInt ptr = new MutableInt(4);
		if (partcount == 0) {
			partarray[0] = pointcount;
			partcount = 1;
		} else {
			for(int i = 0; i < partcount; i++) {
				boolean end = (partcount - i) <= 1; // == 1 really
				int lower = (Integer) shapeInfo[ptr.intValue()];
				ptr.increment();
				int upper = end ? pointcount : (Integer) shapeInfo[ptr.intValue()];
				partarray[i] = upper - lower;
			}
		}
		@SuppressWarnings("unchecked")
		List<Point>[] rval = new List[partcount];
		for(int i = 0; i < partcount; i++) {
			int count = partarray[i];
			rval[i] = getPointList(ptr, count, shapeInfo, hasz);
		}
		return rval;
	}
	
	private List<Point> getPointList(MutableInt ptr, int count, Object[] shapeInfo, boolean hasz) {
		List<Point> pts = new ArrayList<>();
		for(int j = 0; j < count; j++) {
			pts.add(getPoint(ptr, shapeInfo, hasz));
		}
		return pts;
	}
	
	private Point getPoint(MutableInt ptr, Object[] shapeInfo, boolean hasz) {
		Double lon = (Double) shapeInfo[ptr.intValue()];
		ptr.increment();
		Double lat = (Double) shapeInfo[ptr.intValue()];
		ptr.increment();
		if (hasz) {
			Double elev = (Double) shapeInfo[ptr.intValue()];
			ptr.increment();
			return new Point(lat, lon, elev);
		} else {
			return new Point(lat, lon, 0.0);
		}
		
	}

	/**
	 * @return the geometry associated with the row if the row is part of a
	 * feature class or {@code null} if no geometry is set on the row. The
	 * geometry information is directly derived from the shape buffer returned
	 * from the row. The information is serialized into a series of java 
	 * primitives.
	 * 
	 * The first object returned is a Short representing the Shape Type. The
	 * rest of the objects are dependent on the type.
	 * 
	 * M is ignored for all types since giscore has no representation
	 */
	 
	//	  Line:
	//	  Integer: npoints
	//	  point array
	//	  
	//	  PolyLine:
	//	  Integer: npoints
	//	  Integer: nparts
	//    part array
	//	  point array
	//	  
	//	  Polygon:
	//	  Integer: npoints
	//	  Integer: nparts
	//	  part array
	//	  point array
	// 
	// part arrays are filled with ints
	//
	//	  Other shapes are not supported at this time
	 
	private native Object[] getGeo();
	
	public native void setGeometry(Object[] buffer);
	
	/**
	 * @return get the attributes as a map where the key is the field name
	 * and the value is the field value
	 */
	public Map<String, Object> getAttributes() {
		if (attrs == null) {
			Object[] data = getAttrArray();
			attrs = new HashMap<>(data.length / 2);
			for(int i = 0; i < data.length; i += 2) {
				String name = (String) data[i];
				Object datum = data[i+1];
				attrs.put(name, datum);
			}
		}
		return attrs;
	}
	
	/**
	 * Set new attribute data on the row
	 * @param data the new data
	 */
	public void setAttributes(Map<String, Object> data) {
		attrs = data; // Replace old data
		Object[] darray = new Object[attrs.size() * 2];
		int i = 0;
		for(Map.Entry<String,Object>entry : data.entrySet()) {
			final String field = entry.getKey();
			final Object val = entry.getValue();
			darray[i++] = field;
			if (val == GDB.NULL_OBJECT) {
				darray[i++] = null;
			} else {
				darray[i++] = val;
			}
		}
		setAttrArray(darray);
	}
	
	/**
	 * @return the attribute values as an alternating vector of field names
	 * and values.
	 */
	private native Object[] getAttrArray();
	
	/**
	 * Set the attribute values
	 * @param attrs the new values as an alternating vector of field names
	 * and values
	 */
	private native void setAttrArray(Object[] attrs);
}
