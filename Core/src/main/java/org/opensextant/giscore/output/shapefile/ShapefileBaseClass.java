package org.opensextant.giscore.output.shapefile;

import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.output.StreamVisitorBase;

/**
 * Common methods and constants for use with both input and output of shapefiles
 * as GIS objects. Values copied from {@code ShpHandler} in transfusion's mediate
 * package.
 * 
 * @author DRAND
 * 
 */
public abstract class ShapefileBaseClass extends StreamVisitorBase {
    
    // Constants
    protected static final int SIGNATURE = 9994;
    protected static final int VERSION = 1000;

    // These are the 2D ESRI shape types. Add 10 to get the equivalent 3D types.
    protected static final int NULL_TYPE = 0;
    protected static final int POINT_TYPE = 1;             // 1 is Point,     11 is PointZ
    protected static final int MULTILINE_TYPE = 3;         // 3 is MultiLine  13 is MultiLineZ
    protected static final int MULTINESTEDRINGS_TYPE = 5;  // 5 is Polygon    15 is PolygonZ
    protected static final int MULTIPOINT_TYPE = 8;        // 8 is MultiPoint 18 is MultiPointZ

    /**
     * Utility method to test for 3D geometry based on shapeType code
     * 
     * @param shapeType
     * @return
     */
    protected boolean is3D(int shapeType) {
        return shapeType > 10;
    }
    
    /**
     * This predicate method determines if the specified ESRI shapeType is valid, and then
     * if it is a 2D or a 3D type. If invalid, an IllegalArgumentException is thrown.
     * @param esriShapeType
     * @return
     * @throws IllegalArgumentException
     */
    protected boolean is3DType(int esriShapeType) throws IllegalArgumentException {
        // Loop thru the type comparisons twice, once for ESRI 2D types and then for
        // 3D types. Short-circuit exit if match is found.
        int st = esriShapeType;
        boolean valid3D = false;
        for (int i = 0; i < 2; i++) {
            if (st == POINT_TYPE) return valid3D;
            else if (st == MULTIPOINT_TYPE) return valid3D;
            else if (st == MULTILINE_TYPE) return valid3D;
            else if (st == MULTINESTEDRINGS_TYPE) return valid3D;
            st -= 10;   // 3D (Z Types) are exactly 10 more than 2D type ints
            valid3D = true;
        }
        // If we are still here, no type matches, so throw exception
        throw new IllegalArgumentException("Invalid ESRI Shape Type " + esriShapeType);
    }
    
	@Override
	public void visit(Feature feature) {
		// Ignore
	}

}
