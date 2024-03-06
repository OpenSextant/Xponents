/****************************************************************************************
 *  GroundOverlay.java
 *
 *  Created: Feb 4, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.events;



import java.io.IOException;

import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DBounds;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * This element draws an image overlay draped onto the terrain. The href child
 * of {@code <Icon>} specifies the image to be used as the overlay. This file can be
 * either on a local file system or on a web server. If this element is omitted
 * or contains no href, a rectangle is drawn using the color and LatLonBox
 * bounds defined by the ground overlay.
 * 
 * @author DRAND
 * 
 */
public class GroundOverlay extends Overlay {

    private static final long serialVersionUID = 1L;

	private Double north, south, east, west, rotation, altitude;

	private AltitudeModeEnumType altitudeMode; // default (clampToGround)

	private static final double TWO_PI = 2.0 * Math.PI;  // 360 degrees

    /**
	 * @return the type
	 */
	public String getType() {
		return IKml.GROUND_OVERLAY;
	}

	/**
	 * @return the latitude of the north edge of the bounding box, in decimal degrees
	 */
    // @CheckForNull

	public Double getNorth() {
		return north;
	}

	/**
	 * @param north
	 *            the north to set
	 */
	public void setNorth(Double north) {
		this.north = north;
	}

	/**
	 * @return the latitude of the south edge of the bounding box, in decimal degrees
	 */
    // @CheckForNull

	public Double getSouth() {
		return south;
	}

	/**
	 * @param south
	 *            the south to set
	 */
	public void setSouth(Double south) {
		this.south = south;
	}

	/**
	 * @return longitude of the east edge of the bounding box, in decimal degrees
	 */
    // @CheckForNull

	public Double getEast() {
		return east;
	}

	/**
	 * @param east
	 *            the east to set
	 */
	public void setEast(Double east) {
		this.east = east;
	}

	/**
	 * @return longitude of the west edge of the bounding box, in decimal degrees
	 */
    // @CheckForNull

	public Double getWest() {
		return west;
	}

	/**
	 * @param west
	 *            the west to set
	 */
	public void setWest(Double west) {
		this.west = west;
	}

    /**
     * Set overlay bounding box from Geodetic2DBounds. If bbox
     * is 3d then sets overlay altitude to max elevation and assumes absolute MSL.
     * @throws IllegalArgumentException if bbox is null
     */
    public void setBoundingBox(Geodetic2DBounds bbox) {
        if (bbox == null)
            throw new IllegalArgumentException("bbox is null");
        north = bbox.getNorthLat().inDegrees();
        south = bbox.getSouthLat().inDegrees();
        east = bbox.getEastLon().inDegrees();
        west = bbox.getWestLon().inDegrees();
        if (bbox instanceof Geodetic3DBounds) {
            altitude = ((Geodetic3DBounds)bbox).maxElev;
            altitudeMode = AltitudeModeEnumType.absolute;
        }
    }

    /**
     * Determines appropriate bounding box for overlay if north, south, east,
     * and west edges are defined. Bounds are 3d if altitude is defined and
     * altitudeMode is absolute otherwise 2d is used.
     *
     * @return bounding box for image, null if missing either north, south, east,
     *          or west edge from LatLonBox.
     * @throws IllegalArgumentException   if coordinate values are out of range
     */
    // @CheckForNull

    public Geodetic2DBounds getBoundingBox() {
        Geodetic2DBounds bbox = null;
        if (north != null && south != null && east != null && west != null) {
            if (altitude != null && altitudeMode == AltitudeModeEnumType.absolute) {
                Geodetic3DPoint westCoordinate = new Geodetic3DPoint(new Longitude(west, Angle.DEGREES),
                        new Latitude(south, Angle.DEGREES), altitude);
                Geodetic3DPoint eastCoordinate =  new Geodetic3DPoint(new Longitude(east, Angle.DEGREES),
                        new Latitude(north, Angle.DEGREES), altitude);
                bbox = new Geodetic3DBounds(westCoordinate, eastCoordinate);
            } else {
                Geodetic2DPoint westCoordinate = new Geodetic2DPoint(new Longitude(west, Angle.DEGREES),
                        new Latitude(south, Angle.DEGREES));
                Geodetic2DPoint eastCoordinate = new Geodetic2DPoint(new Longitude(east, Angle.DEGREES),
                        new Latitude(north, Angle.DEGREES));
                bbox = new Geodetic2DBounds(westCoordinate, eastCoordinate);
            }
        }
        return bbox;
    }

	/**
	 * Test for Longitude wrap at International Date Line (IDL).
	 * IDL crossing is when a sign changes between longitude end points.
	 * @return true if ground overlay east-west bounds cross the International Date Line
	 */
	public boolean crossDateLine() {
		return west != null && east != null
				&& normalize(west) >= 0.0 && normalize(east) < 0.0;
	}

	/** Reduce angle in degrees to normalized range in radians [-PI .. +PI)
     *
     * @param degrees angle value in degrees
     * @return normalized value in radians (-PI .. PI)
     */
    static double normalize(double degrees) {
		double rad = Math.toRadians(degrees);
        // This is somewhat arbitrary to limit angles to 4 revolutions
        if (Math.abs(rad) <= (4.0 * TWO_PI)) {
			while (rad >= Math.PI) rad -= TWO_PI;
			// Normalize west - check west < -180 degrees and add 360.
			// Example: 125 degrees west longitude can also be expressed as -235.
			// This is auto-checked in KmlInputStream.handleLatLonBox()
			// but unknown if set directly or from non-KML sources.
			while (rad < -Math.PI) rad += TWO_PI;
		}
        return rad;
    }

	/**
	 * @return the rotation
	 */
    // @CheckForNull

	public Double getRotation() {
		return rotation;
	}

	/**
     * Set angle of rotation of the overlay image about its center, in degrees
     * counterclockwise starting from north. The default is 0 (north).
     *
     * @param rotationAngle angle of rotation
     * @throws IllegalArgumentException if rotation angle is out of range [-180,+180]
	 */
	public void setRotation(Double rotationAngle) {
        if (rotationAngle != null) {
            double rotation = rotationAngle;
            if (Double.isNaN(rotation) || rotation < -180 || rotation > 180)
                throw new IllegalArgumentException("Rotation out of range [-180,+180]: " + rotation);
        }
        this.rotation = rotationAngle;
	}

	/**
     * Get distance above the earth's surface, in meters.
	 * @return the altitude
	 */
    // @CheckForNull

	public Double getAltitude() {
		return altitude;
	}

	/**
     * Set altitude (in meters, and is interpreted according to the altitude mode)
	 * @param altitude
	 *            the altitude to set
	 */
	public void setAltitude(Double altitude) {
		this.altitude = altitude;
	}

	/**
     * Altitude Mode ([clampToGround], relativeToGround, absolute). If value is null
     * then the default clampToGround is assumed and altitude can be ignored. 
	 * @return the altitudeMode
	 */
    // @CheckForNull

	public AltitudeModeEnumType getAltitudeMode() {
		return altitudeMode;
	}

	/**
     * Set altitudeMode 
	 * @param altitudeMode
	 *            the altitudeMode to set ([clampToGround], relativeToGround, absolute) 
	 */
	public void setAltitudeMode(AltitudeModeEnumType altitudeMode) {
		this.altitudeMode = altitudeMode;
	}

    /**
     * Set altitudeMode
	 * @param altitudeMode
	 *            the altitudeMode to set ([clampToGround], relativeToGround, absolute)
	 * 				also including gx:extensions (clampToSeaFloor, relativeToSeaFloor)
     *              If altitudeMode value is invalid, null or empty string then null
     *              is assigned and default value is assumed.
	 */    
    public void setAltitudeMode(String altitudeMode) {
        this.altitudeMode = AltitudeModeEnumType.getNormalizedMode(altitudeMode);
	}
	
    public void accept(IStreamVisitor visitor) {
    	visitor.visit(this);
    }

	/**
	 * The approximately equals method checks all the fields for equality with
	 * the exception of the geometry.
	 * 
	 * @param tf Target feature to compare against
	 * @return true if this and other are approximately equal
	 */
	public boolean approximatelyEquals(Feature tf) {
		if (!(tf instanceof GroundOverlay))
			return false;
		if (!super.approximatelyEquals(tf))
			return false;

		GroundOverlay gother = (GroundOverlay) tf;
		boolean equals = closeDouble(altitude, gother.altitude)
				&& closeDouble(east, gother.east)
				&& closeDouble(north, gother.north)
				&& closeDouble(west, gother.west)
				&& closeDouble(south, gother.south)
				&& closeDouble(rotation, gother.rotation);

		if (!equals)
			return false;

        // note: if value is null then it's treated in KML the same as the default clampToGround value
        // but our test below treats null different than clampToGround. 
        return altitudeMode == gother.altitudeMode;
        /*
		if (altitudeMode == null && 
				gother.altitudeMode == null)
			return true;
		else if (altitudeMode != null)
			return altitudeMode.equals(gother.altitudeMode);
		else
			return false;
        */
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Overlay#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		altitude = in.readDouble();
		east = in.readDouble();
		north = in.readDouble();
		rotation = in.readDouble();
		south = in.readDouble();
		west = in.readDouble();
        String val = in.readString();
		altitudeMode = val != null && val.length() != 0 ? AltitudeModeEnumType.valueOf(val) : null;
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Overlay#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeDouble(altitude != null ? altitude : 0.0);
		out.writeDouble(east != null ? east : 0.0);
		out.writeDouble(north != null ? north : 0.0);
		out.writeDouble(rotation != null ? rotation : 0.0);
		out.writeDouble(south != null ? south : 0.0);
		out.writeDouble(west != null ? west : 0.0);
		out.writeString(altitudeMode == null ? null : altitudeMode.toString());
	}

}
