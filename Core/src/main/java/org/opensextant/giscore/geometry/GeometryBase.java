package org.opensextant.giscore.geometry;




import java.io.IOException;

import org.opensextant.giscore.events.AltitudeModeEnumType;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * Abstract Geometry class with common altitudeMode and extrude
 * fields associated with Points, Lines, Polygons, etc.
 *
 * @author Jason Mathews, MITRE Corp.
 *         Date: Jun 15, 2009 2:02:35 PM
 */
public abstract class GeometryBase extends Geometry {

    private static final long serialVersionUID = 1L;

    private AltitudeModeEnumType altitudeMode; // default (clampToGround)

    private Boolean extrude; // default (false)

    private Boolean tessellate; // default (false)

    private Integer drawOrder; // indicates gx:drawOrder (default = 0) for non-point geometries (line, ring, polygon)

    /**
     * Altitude Mode ([clampToGround], relativeToGround, absolute). If value is null
     * then the default clampToGround is assumed and altitude can be ignored.
     *
     * @return the altitudeMode
     */
    // @CheckForNull

    public AltitudeModeEnumType getAltitudeMode() {
        return altitudeMode;
    }

    /**
     * Set altitudeMode
     *
     * @param altitudeMode the altitudeMode to set ([clampToGround], relativeToGround, absolute)
     */
    public void setAltitudeMode(AltitudeModeEnumType altitudeMode) {
        this.altitudeMode = altitudeMode;
    }

    /**
     * Set altitudeMode to normalized AltitudeModeEnumType value or null if invalid.
     *
     * @param altitudeMode the altitudeMode to set ([clampToGround], relativeToGround, absolute)
     *                     also includes gx:extensions (clampToSeaFloor and relativeToSeaFloor)
     */
    public void setAltitudeMode(String altitudeMode) {
        this.altitudeMode = AltitudeModeEnumType.getNormalizedMode(altitudeMode);
    }

    // @CheckForNull

    public Boolean getExtrude() {
        return extrude;
    }

    public void setExtrude(Boolean extrude) {
        this.extrude = extrude;
    }

    // @CheckForNull

    public Boolean getTessellate() {
        return tessellate;
    }

    public void setTessellate(Boolean tessellate) {
        this.tessellate = tessellate;
    }

    public Integer getDrawOrder() {
        return drawOrder;
    }

    public void setDrawOrder(Integer drawOrder) {
        this.drawOrder = drawOrder;
    }

    /**
     * Read data from SimpleObjectInputStream
     *
     * @param in SimpleObjectInputStream
     * @throws IOException              if an I/O error occurs or if this input stream has reached the end.
     * @throws ClassNotFoundException   if the class cannot be located
     * @throws IllegalAccessException   if the class or its nullary
     *                                  constructor is not accessible.
     * @throws InstantiationException   if this {@code Class} represents an abstract class,
     *                                  an interface, an array class, a primitive type, or void;
     *                                  or if the class has no nullary constructor;
     *                                  or if the instantiation fails for some other reason.
     * @throws IllegalArgumentException if enumerated AltitudeMode value is invalid
     * @see org.opensextant.giscore.utils.SimpleObjectInputStream#readObject()
     */
    @Override
    public void readData(SimpleObjectInputStream in) throws IOException,
            ClassNotFoundException, InstantiationException,
            IllegalAccessException {
        super.readData(in);
        String val = in.readString();
        altitudeMode = val != null && val.length() != 0 ? AltitudeModeEnumType.valueOf(val) : null;
        int mask = in.readByte();
        int exMask = mask & 0x3; // extrude: 2 = null, 1 = true, 0 = false
        extrude = (exMask == 0 || exMask == 0x1) ? exMask == 0x1 : null;
        int tessMask = mask & 0x30; // tessellate: 2x = null, 1x = true, 0x = false
        tessellate = (tessMask == 0 || tessMask == 0x10) ? tessMask == 0x10 : null;
        this.drawOrder = (mask & 0x80) != 0 ? null : in.readInt();
    }

    /*
      * (non-Javadoc)
      * @see SimpleObjectOutputStream#writeObject(org.opensextant.giscore.utils.IDataSerializable)
      */
    @Override
    public void writeData(SimpleObjectOutputStream out) throws IOException {
        super.writeData(out);
        out.writeString(altitudeMode == null ? "" : altitudeMode.toString());
        // write out extrude and tessellate into same byte mask field since both are Boolean fields
        // with values: 0,1,null
        int mask = extrude == null ? 0x2 : extrude ? 0x1 : 0;
        if (tessellate == null) mask |= 0x20;
        else if (tessellate) mask |= 0x10;
        if (drawOrder == null) mask |= 0x80;
        out.writeByte(mask);
        if (drawOrder != null) {
            out.writeInt(drawOrder);
        }
    }

}
