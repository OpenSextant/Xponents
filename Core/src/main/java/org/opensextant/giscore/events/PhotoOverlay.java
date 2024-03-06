/****************************************************************************************
 *  PhotoOverlay.java
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

import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * Wrapper for {@code PhotoOverlay} creates an basic PhotoOverlay object.
 * TODO: implement other PhotoOverlay-specific properties (ViewVolume, ImagePyramid, shape).
 *
 * @author DRAND
 */
public class PhotoOverlay extends Overlay {

    private static final long serialVersionUID = 1L;

    // <element ref="kml:rotation" minOccurs="0"/>
    private Double rotation;

    // <element ref="kml:Point" minOccurs="0"/> stored in Feature superclass as Geometry

    /*
        add other PhotoOverlay fields:
          <element ref="kml:ViewVolume" minOccurs="0"/>
          <element ref="kml:ImagePyramid" minOccurs="0"/>
          <element ref="kml:shape" minOccurs="0"/>
     */

    /**
	 * @return the type
	 */
	public String getType() {
		return IKml.PHOTO_OVERLAY;
	}

    /**
     * Get rotation angle. This adjusts how the photo is placed inside the field of view.
     * This element is useful if your photo has been rotated and deviates slightly
     * from a desired horizontal view.
	 * @return the rotation angle in degrees
	 */
    // @CheckForNull

	public Double getRotation() {
		return rotation;
	}

	/**
     * Adjusts how the photo is placed inside the field of view. This element
     * is useful if your photo has been rotated and deviates slightly from a
     * desired horizontal view. Values can be +/- 180. The default is 0 (north).
     *
     * @param rotationAngle angle of rotation
     * @throws IllegalArgumentException if rotation angle is out of range [-180,+180]
	 */
	public void setRotation(Double rotationAngle) {
        if (rotationAngle != null) {
            final double rotation = rotationAngle;
            if (Double.isNaN(rotation) || rotation < -180 || rotation > 180)
                throw new IllegalArgumentException("Rotation out of range [-180,+180]: " + rotation);
        }
        this.rotation = rotationAngle;
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
		if (!(tf instanceof PhotoOverlay))
			return false;

		if (!super.approximatelyEquals(tf))
			return false;

		final PhotoOverlay gother = (PhotoOverlay) tf;
        // TODO: check ViewVolume, ImagePyramid, shape fields
        return closeDouble(rotation, gother.rotation);
    }

    /* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Overlay#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		rotation = in.readDouble();

	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Overlay#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeDouble(rotation != null ? rotation : 0.0);
	}
}
