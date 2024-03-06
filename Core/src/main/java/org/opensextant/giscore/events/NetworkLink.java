/****************************************************************************************
 *  NetworkLink.java
 *
 *  Created: Feb 4, 2009
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
package org.opensextant.giscore.events;




import java.io.IOException;

import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * Represents a remote resource
 * 
 * @author DRAND
 */
public class NetworkLink extends Feature implements IContainerType {

	private static final long serialVersionUID = 1L;
	private boolean refreshVisibility;	// default = 0 (false)
	private boolean flyToView;			// default = 0 (false)
	private boolean open; 				// default = 0 (false)
	private TaggedMap link;

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Feature#getType()
	 */
	@Override
	public String getType() {
		return IKml.NETWORK_LINK;
	}

	/**
	 * @return the refreshVisibility
	 */
	public boolean isRefreshVisibility() {
		return refreshVisibility;
	}

	/**
	 * @param refreshVisibility the refreshVisibility to set
	 */
	public void setRefreshVisibility(boolean refreshVisibility) {
		this.refreshVisibility = refreshVisibility;
	}

	/**
	 * @return the flyToView
	 */
	public boolean isFlyToView() {
		return flyToView;
	}

	/**
	 * @param flyToView the flyToView to set
	 */
	public void setFlyToView(boolean flyToView) {
		this.flyToView = flyToView;
	}

	/**
	 * Specifies whether a Container (Document or Folder) appears closed or open when first loaded into the Places panel.
	 * false=collapsed (the default), true=expanded. This element applies only to Document, Folder, and NetworkLink.
	 * @return whether the Container appears open or closed
	 */
    public boolean isOpen() {
        return open;
    }

	/**
	 * Set open flag.
	 * @param open True if container appears open when first loaded
	 */
    public void setOpen(boolean open) {
        this.open = open;
    }

	/**
     * Get Link property TaggedMap. TaggedMap includes the child elements
     * associated with the networkLink Link or Url tag which may contain
     * any of the following attributes: refreshMode, refreshInterval,
     *  viewRefreshMode, viewRefreshTime, viewBoundScale, viewFormat, httpQuery.
	 * @return the link
	 */
    // @CheckForNull

	public TaggedMap getLink() {
		return link;
	}

	/**
	 * @param link the link to set
	 */
	public void setLink(TaggedMap link) {
		this.link = link;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((link == null || link.isEmpty()) ? 0 : link.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		NetworkLink other = (NetworkLink) obj;

		// NOTE: link empty or null is treated the same in both equals and hashCode
		// especially since readData() converts empty lists into null object
		if (link == null) {
			return other.link == null || other.link.isEmpty();
		} else {
			if (link.isEmpty() && (other.link == null || other.link.isEmpty()))
				return true;
			return link.equals(other.link);
		}
	}
	
    public void accept(IStreamVisitor visitor) {
    	visitor.visit(this);
    }
	
	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Feature#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	@Override
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		flyToView = in.readBoolean();
		refreshVisibility = in.readBoolean();
		link = (TaggedMap) in.readObject();
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.events.Feature#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	@Override
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		out.writeBoolean(flyToView);
		out.writeBoolean(refreshVisibility);
		out.writeObject(link);
	}
}
