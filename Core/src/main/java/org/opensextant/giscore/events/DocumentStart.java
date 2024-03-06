/****************************************************************************************
 *  DocumentStart.java
 *
 *  Created: Jan 28, 2009
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
package org.opensextant.giscore.events;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.Namespace;

/**
 * This tags the document with the source information of what format it came
 * from, useful for back end processors to decide on the meaning of various 
 * values.
 * 
 * @author DRAND
 *
 */
public class DocumentStart implements IGISObject {

    private static final long serialVersionUID = 1L;

	private DocumentType type;
	private final transient List<Namespace> namespaces = new ArrayList<>();

	/**
	 * Ctor
	 * @param type
	 */
	public DocumentStart(DocumentType type) {
		setType(type);
	}

	/**
	 * @return the type
	 */
	@Nullable
	public DocumentType getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(DocumentType type) {
		this.type = type;
	}
	
    /**
	 * @return the namespaces
	 */
	@NotNull
	public List<Namespace> getNamespaces() {
		return namespaces;
	}

	/**
	 * Add namespace. Verifies namespace prefix is unique in the list
	 * as required in a XML context with the XML unique attribute constraint.
	 * Duplicate prefixes are discarded and not added to the list.
	 * URIs may be duplicates in the list but its prefix must be different.
	 * @param aNamespace Namespace to add, never <code>null</code>
	 * @return true if namespace was added or if already existed in the list,
	 * otherwise <code>false</code> if not added because either argument was
	 * <code>null</code> or its prefix conflicted with one having another URI.
	 */
	public boolean addNamespace(Namespace aNamespace) {
		if (aNamespace == null) return false;
		final String targetPrefix = aNamespace.getPrefix();
		for (Namespace ns : namespaces) {
			if (targetPrefix.equals(ns.getPrefix()))
				return aNamespace.equals(ns);
		}
		namespaces.add(aNamespace);
		return true;
	}

	public void accept(IStreamVisitor visitor) {
    	visitor.visit(this);
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}
}
