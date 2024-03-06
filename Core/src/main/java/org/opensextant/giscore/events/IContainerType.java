package org.opensextant.giscore.events;

/**
 * Interface indicating the feature is an container type and has open attribute
 * @author Jason Mathews, MITRE Corp.
 * Date: Nov 5, 2010 10:41:20 AM
 */
public interface IContainerType {

	/**
	 * Specifies whether a Container (Document or Folder) appears closed or open when first loaded.
	 * false=collapsed (the default), true=expanded. This element applies only to Document, Folder, and NetworkLink.
	 * @return whether the Container appears open or closed
	 */
	boolean isOpen();

	/**
	 * Set open flag.
	 * @param open True if container appears open when first loaded
	 */
    void setOpen(boolean open);

    /**
     * Returns type of container
     * @return the type, never null
     */
    String getType();
}
