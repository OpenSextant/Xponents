/****************************************************************************************
 *  AbstractObject.java
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

import org.jetbrains.annotations.Nullable;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * AbstractObject is a base IGISObject that simply has a unique id field.
 * Conceptually same as the KML AbstractObjectGroup element. 
 *  
 * @author Jason Mathews, MITRE Corp.
 * Date: Sep 15, 2009 10:50:00 AM
 */
public abstract class AbstractObject implements IDataSerializable, IGISObject {

	private static final long serialVersionUID = 1L;

	@Nullable
    private String id;

    /**
     * @return the id to use to distinguish this object from another.
     * Value is either a non-empty string or {@code null}.
     */
    @Nullable
    public String getId() {
        return id;
    }

    /**
	 * Set unique identifier to this object
     * @param id the id to set, if blank or empty string then null is assigned.
     * 		Whitespace is stripped from start and end of the string if present.
     */
    public void setId(String id) {
        if (id != null) {
            id = id.trim();
            if (id.length() == 0) id = null;
        }
        this.id = id; // aka StringUtils.trimToNull(id)
    }

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || !(obj instanceof AbstractObject))
			return false;
		AbstractObject other = (AbstractObject) obj;
		if (id == null) {
			return other.id == null;
		} else return id.equals(other.id);
	}

	/**
     * Returns a hash code for this {@code AbstractObject}.
     *
     * @return  a hash code value for this object, equal to the
     *          hash code of its {@code id} or
	 *          0 if {@code id} is {@code null}.
     */
	@Override
	public int hashCode() {
		return (id == null) ? 0 : id.hashCode();
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		id = in.readString();
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		out.writeString(id);
	}
    
}
