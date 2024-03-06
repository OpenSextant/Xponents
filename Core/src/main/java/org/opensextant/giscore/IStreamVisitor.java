/****************************************************************************************
 *  IStreamVisitor.java
 *
 *  Created: Apr 16, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
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
package org.opensextant.giscore;

import org.opensextant.giscore.events.*;
import org.opensextant.giscore.geometry.*;

/**
 * @author DRAND
 *
 */
public interface IStreamVisitor {

    /**
     * Default behavior ignores containers
     * @param containerStart
     */
    void visit(ContainerStart containerStart);

    /**
     * @param styleMap
     */
    void visit(StyleMap styleMap);

    /**
     * @param style
     */
    void visit(Style style);

    /**
     * @param schema
     */
    void visit(Schema schema);

    /**
     * Visting a row causes an error for non-row oriented output streams
     * @param row
     */
    void visit(Row row);

    /**
     * @param feature
     */
    void visit(Feature feature);

    /**
     * @param link
     */
    void visit(NetworkLink link);

    /**
     * @param networkLinkControl
     */
    void visit(NetworkLinkControl networkLinkControl);

    /**
     * @param overlay
     */
    void visit(GroundOverlay overlay);

    /**
     * @param overlay
     */
    void visit(PhotoOverlay overlay);

    /**
     * @param overlay
     */
    void visit(ScreenOverlay overlay);

    /**
     * @param documentStart
     */
    void visit(DocumentStart documentStart);

    /**
     * @param containerEnd
     */
    void visit(ContainerEnd containerEnd);

    /**
     * @param point
     */
    void visit(Point point);

    /**
     * @param multiPoint
     */
    void visit(MultiPoint multiPoint);

    /**
     * @param line
     */
    void visit(Line line);

    /**
     * @param geobag a geometry bag
     */
    void visit(GeometryBag geobag);

    /**
     * @param multiLine
     */
    void visit(MultiLine multiLine);

    /**
     * @param ring
     */
    void visit(LinearRing ring);

    /**
     * @param rings
     */
    void visit(MultiLinearRings rings);

    /**
     * @param polygon
     */
    void visit(Polygon polygon);

    /**
     * @param polygons
     */
    void visit(MultiPolygons polygons);

    /**
     * @param comment
     */
    void visit(Comment comment);

    /**
     * @param model
     */
    void visit(Model model);

    /**
     * @param circle
     */
    void visit(Circle circle);

    /**
     * @param element
     */
    void visit(Element element);

}
