/***************************************************************************
 * (C) Copyright MITRE Corporation 2012
 *
 * The program is provided "as is" without any warranty express or implied,
 * including the warranty of non-infringement and the implied warranties of
 * merchantibility and fitness for a particular purpose.  The Copyright
 * owner will not be liable for any damages suffered by you as a result of
 * using the Program.  In no event will the Copyright owner be liable for
 * any special, indirect or consequential damages or lost profits even if
 * the Copyright owner has been advised of the possibility of their
 * occurrence.
 ***************************************************************************/
package org.opensextant.giscore.input.shapefile;

/**
 * An enumeration of the supported components of a Shapefile.
 * <br>
 * A "Shapefile" actually consists of several different files, most of which
 * are optional.
 *
 * @author jgibson
 */
public enum ShapefileComponent {
    /**
     * The main component of a shapefile that contains geometry.
     */
    SHP,
    /**
     * The main component of a shapefile that contains fields and shape metadata.
     */
    DBF,
    /**
     * Defines the coordinate projection of the shapefile.
     * <br>
     * Note that currently the only supported projection is WGS-84.
     */
    PRJ
    // Unsupported components
    //CPG,
    //SHX,
    //SBN,
    //SBX,
    //FBN,
    //FBX,
    //AIN,
    //AIX,
    //IXS,
    //MXS,
    //ATX,
    //SHP_XML

}
