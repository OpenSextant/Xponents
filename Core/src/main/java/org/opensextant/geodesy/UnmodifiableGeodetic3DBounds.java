/****************************************************************************************
 *  UnmodifiableGeodetic3DBounds.java
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
package org.opensextant.geodesy;

/**
 * Provides an unmodifiable view of the specified bounding box.  This class
 * allows modules to provide users with "read-only" access to internal
 * bounds.  Attempts to modify the bounds result in an
 * {@code UnsupportedOperationException}.
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: Oct 5, 2009 1:57:23 PM
 */
public class UnmodifiableGeodetic3DBounds extends Geodetic3DBounds {

	private static final long serialVersionUID = 1L;

	public UnmodifiableGeodetic3DBounds(Geodetic3DBounds bbox) {
		super(bbox);
	}

	public UnmodifiableGeodetic3DBounds(Geodetic3DPoint seedPoint) {
		super(seedPoint);
	}

	/*
		Note: grow() throws UnsupportedOperationException only if appropriate include()
	 	is called and the bounds are attempted to be modified...
	 */

	public void include(Geodetic3DPoint newPoint) {
		throw new UnsupportedOperationException();
	}

	public void include(Geodetic3DBounds bbox) {
		throw new UnsupportedOperationException();
	}

	public void include(Geodetic2DPoint newPoint) {
		throw new UnsupportedOperationException();
	}

	public void include(Geodetic2DBounds bbox) {
		throw new UnsupportedOperationException();
	}

	public void setWestLon(final Longitude westLon) {
		throw new UnsupportedOperationException();
	}

	public void setEastLon(final Longitude eastLon) {
		throw new UnsupportedOperationException();
	}

	public void setSouthLat(final Latitude southLat) {
		throw new UnsupportedOperationException();
	}

	public void setNorthLat(final Latitude northLat) {
		throw new UnsupportedOperationException();
	}
}
