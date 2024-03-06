/****************************************************************************************
 *  DocType.java
 *
 *  Created: May 2, 2013
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
package org.opensextant.giscore.data;

/**
 * A small internal doctype object that can be used for extension types and
 * is now used to represent the internal DocumentType enumeration.
 * 
 * @author DRAND
 *
 */
public class DocType {
	private final String name;
	private final boolean requiresZipStream;
	private final boolean requiresZipEntry;
	
	public DocType(String name, boolean requiresZipStream, boolean requiresZipEntry) {
		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException(
					"name should never be null or empty");
		}
		this.name = name;
		this.requiresZipEntry = requiresZipEntry;
		this.requiresZipStream = requiresZipStream;
	}
	
	/**
	 * @return the name
	 */
	public String name() {
		return name;
	}

	/**
	 * @return the requiresZipStream
	 */
	public boolean requiresZipStream() {
		return requiresZipStream;
	}

	/**
	 * @return the requiresZipEntry
	 */
	public boolean requiresZipEntry() {
		return requiresZipEntry;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (requiresZipEntry ? 1231 : 1237);
		result = prime * result + (requiresZipStream ? 1231 : 1237);
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DocType other = (DocType) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (requiresZipEntry != other.requiresZipEntry)
			return false;
		return requiresZipStream == other.requiresZipStream;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "DocType [name=" + name + ", requiresZipStream="
				+ requiresZipStream + ", requiresZipEntry=" + requiresZipEntry
				+ "]";
	}
}
