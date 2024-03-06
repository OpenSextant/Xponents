/****************************************************************************************
 *  StreamVisitorBase.java
 *
 *  Created: Jan 30, 2009
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
package org.opensextant.giscore.output;

import java.io.File;

import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.geometry.*;

/**
 * The stream visitor base extends the original visitor base and changes the
 * default behaviors to be compatible with the new stream elements. It hides
 * the elements that should no longer be used (org.opensextant.itf.Feature and 
 * org.opensextant.itf.ThematicLayer). 
 * 
 * @author DRAND
 *
 */
public class StreamVisitorBase implements IStreamVisitor {

    /**
	 * Default behavior ignores containers
	 * @param containerStart 
	 */
	@Override
	public void visit(ContainerStart containerStart) {
		// Ignored by default
	}

	/**
	 * @param styleMap
	 */
	@Override
	public void visit(StyleMap styleMap) {
		// Ignored by default
	}

	/**
	 * @param style
	 */
	@Override
	public void visit(Style style) {
		// Ignored by default		
	}

	/**
	 * @param schema
	 */
	@Override
	public void visit(Schema schema) {
		// Ignored by default	
	}

    /**
     * Visting a row causes an error for non-row oriented output streams
     * @param row
     */
    @Override
	public void visit(Row row) {
    	throw new UnsupportedOperationException("Can't output a tabular row");
    }
    
	/**
	 * @param feature
	 */
	@Override
	public void visit(Feature feature) {
		final Geometry geometry = feature.getGeometry();
		if (geometry != null) {
			geometry.accept(this);
		}
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.IStreamVisitor#visit(org.opensextant.giscore.events.NetworkLink)
	 */
	@Override
	public void visit(NetworkLink link) {
		visit((Feature) link);
	}

	/**
	 * Visit NetworkLinkControl.
	 * Default behavior ignores NetworkLinkControls 
	 * @param networkLinkControl
	 */
	@Override
	public void visit(NetworkLinkControl networkLinkControl) {
		// Ignored by default
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.IStreamVisitor#visit(org.opensextant.giscore.events.PhotoOverlay)
	 */
	@Override
	public void visit(PhotoOverlay overlay) {
		visit((Feature) overlay);
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.IStreamVisitor#visit(org.opensextant.giscore.events.ScreenOverlay)
	 */
	@Override
	public void visit(ScreenOverlay overlay) {
		visit((Feature) overlay);
	}

	/**
     * @param overlay
     */
    @Override
	public void visit(GroundOverlay overlay) {
    	visit((Feature) overlay);
    } 
    
	/**
	 * @param documentStart
	 */
	@Override
	public void visit(DocumentStart documentStart) {
		// Ignored by default
	}

	/**
	 * @param containerEnd
	 */
	@Override
	public void visit(ContainerEnd containerEnd) {
		// Ignored by default	
	}
	
    /**
	 * @param point
	 */
	@Override
	public void visit(Point point) {
		// do nothing
	}

    /**
     * @param multiPoint
     */
    @Override
	public void visit(MultiPoint multiPoint) {
        for (Point point : multiPoint) {
            point.accept(this);
        }
    }

    /**
     * @param line
     */
    @Override
	public void visit(Line line) {
        for (Point pt : line) {
            pt.accept(this);
        }
	}

    /**
     * @param geobag a geometry bag
     */
    @Override
	public void visit(GeometryBag geobag) {
    	for(Geometry geo : geobag) {
    		geo.accept(this);
    	}
    }
    
	/**
	 * @param multiLine
	 */
	@Override
	public void visit(MultiLine multiLine) {
        for (Line line : multiLine) {
            line.accept(this);
        }
	}

	/**
	 * @param ring
	 */
	@Override
	public void visit(LinearRing ring) {
        for (Point pt : ring) {
            pt.accept(this);
        }
	}

    /**
     * @param rings
     */
    @Override
	public void visit(MultiLinearRings rings) {
        for (LinearRing ring : rings) {
            ring.accept(this);
        }
    }

    /**
	 * @param polygon
	 */
	@Override
	public void visit(Polygon polygon) {
        polygon.getOuterRing().accept(this);
        for (LinearRing ring : polygon.getLinearRings()) {
            ring.accept(this);
        }
	}

    /**
     * @param polygons
     */
    @Override
	public void visit(MultiPolygons polygons) {
        for (Polygon polygon : polygons) {
            polygon.accept(this);
        }
    }

	@Override
	public void visit(Comment comment) {
		// Ignored by default
	}

    @Override
	public void visit(Model model) {
        // Ignored by default
    }

	/**
     * Handle the output of a Circle
     *
     * @param circle the circle
     */
    @Override
	public void visit(Circle circle) {
        // treat as Point by default
        visit((Point)circle);
    }

    @Override
	public void visit(Element element) {
    	// Ignored by default
    }
    

	/**
	 * delete dir content
	 * @param directory
	 */
	protected static void deleteDirContents(File directory) {
		if (directory != null) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						deleteDirContents(file);
					}
					file.delete();
				}
			}
		}
	}

}
