/****************************************************************************************
 *  FileGdbConstants.java
 *
 *  Created: Jan 3, 2013
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2013
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
package org.opensextant.giscore.output.gdb;

public interface FileGdbConstants {
	short shapeNull = 0;
	short shapePoint = 1;
	short shapePointM = 21;
	short shapePointZM = 11;
	short shapePointZ = 9;
	short shapeMultipoint = 8;
	short shapeMultipointM = 28;
	short shapeMultipointZM = 18;
	short shapeMultipointZ = 20;
	short shapePolyline = 3;
	short shapePolylineM = 23;
	short shapePolylineZM = 13;
	short shapePolylineZ = 10;
	short shapePolygon = 5;
	short shapePolygonM = 25;
	short shapePolygonZM = 15;
	short shapePolygonZ = 19;
	short shapeMultiPatchM = 31;
	short shapeMultiPatch = 32;
	short shapeGeneralPolyline = 50;
	short shapeGeneralPolygon = 51;
	short shapeGeneralPoint = 52;
	short shapeGeneralMultipoint = 53;
	short shapeGeneralMultiPatch  = 54;
}
