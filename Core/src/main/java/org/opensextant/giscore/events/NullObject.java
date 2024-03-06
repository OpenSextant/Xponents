package org.opensextant.giscore.events;

import org.opensextant.giscore.IStreamVisitor;

/**
 * Create a NullObject to indicate an XML element was skipped
 * but parse is recoverable and end of stream not reached yet.
 * This object is used internally and never returned to the user
 * while reading data.
 * 
 * @author Jason Mathews, MITRE Corp.
 * Date: Mar 6, 2009 2:17:03 PM
 */
public final class NullObject implements IGISObject {

    private static final long serialVersionUID = 1L;

    private static final NullObject NULL_OBJECT = new NullObject();

    private NullObject() {
        // no instances other than static instance
    }

    /**
     * Get NullObject instance
     * @return NullObject
     */
    public static NullObject getInstance() {
        return NULL_OBJECT;
    }

    /**
     * Visit the object
     *
     * @param visitor the visitor to dispatch to, never {@code null}
     */
    public void accept(IStreamVisitor visitor) {
        // do nothing
    }
}
