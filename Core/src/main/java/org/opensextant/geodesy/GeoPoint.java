/****************************************************************************************
 *  GeoPoint.java
 *
 *  Created: Mar 23, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2007
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
 * A GeoPoint is defined by its interface as any object that can be accurately
 * converted to a Geodetic3DPoint point given a FrameOfReference. GeoPoints can
 * therefore be compared for nearness on the surface of an Ellipsoid model of
 * the Earth, or nearness in space as measured by Euclidean distance. This
 * interface enables the three different point representations used in this
 * geodesy package (Geodetic, Geocentric, and Topocentric) to be abstracted
 * into a single logical type.
 */
public interface GeoPoint {
    Geodetic3DPoint toGeodetic3D(FrameOfReference fRef);
}
