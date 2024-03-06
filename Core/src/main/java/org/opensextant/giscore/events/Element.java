/****************************************************************************************
 *  Element.java
 *
 *  Created: Jul 15, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
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

// import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * An element represents an XML element found in an XML type document such
 * as KML. This is a limited representation that does not allow for nested
 * element structures, although such a thing could be added in the future.
 * 
 * @author DRAND
 */
public class Element implements IGISObject, IDataSerializable, Serializable {
    
	private static final long serialVersionUID = 1L;

	@NotNull
    private transient Namespace namespace;

	// @CheckForNull

	private transient Set<Namespace> namespaces;

	/**
	 * The name of the element
	 */
	@NotNull
	private String name;
	
	/**
	 * Attribute/value pairs found on the element
	 */
	private final Map<String, String> attributes = new HashMap<>();
	
	/**
	 * Child elements contained within the element
	 */
	private final List<Element> children = new ArrayList<>();
	
	/**
	 * Text content 
	 */
	private String text;

    /**
	 * Empty constructor requires caller to call setName() and optionally setNamespace()
     * directly or deserialize with {@link #readData}.
	 */
	public Element() {
        namespace = Namespace.NO_NAMESPACE;
	}

	/**
	 * Create Element with noname namespace by default.
	 */
	public Element(String name) {
		setName(name);
		namespace = Namespace.NO_NAMESPACE;
	}
	
	/**
	 * Create XML Element object.
     * 
	 * @param namespace Namespace of this {@code Element}. If
     *      the namespace is {@code null}, the element will have no namespace.
      @param  name                 local name of the element
     *
     * @throws IllegalArgumentException if name is blank string or <code>null</code>. 
	 */
	public Element(Namespace namespace, String name) {
		setName(name);
		setNamespace(namespace);
	}

    /**
     * Get namespace that this element belongs to.
	 * Never {@code null}.
     * @return                     the element's namespace
     */
    @NotNull
    public Namespace getNamespace() {
        return namespace;
    }

    /**
     * Set the Namespace of this XML {@code Element}. If the provided namespace is null,
     * the element will have no namespace.
     * @param namespace Namespace of this {@code Element},
     *      may be {@code null}.
     */
    public void setNamespace(Namespace namespace) {
        this.namespace = namespace == null ? Namespace.NO_NAMESPACE : namespace;
    }

	/**
	 * @return the prefix
	 */
    @NotNull
	public String getPrefix() {
		return namespace.getPrefix();
	}

     /**
     * Get the Namespace URI of this XML {@code Element}.
     *
     * @return Namespace URI of this {@code Element}
     */
    @NotNull
    public String getNamespaceURI() {
        return namespace.getURI();
    }

	/**
	 * @return the name
	 */
    @NotNull
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set. Names must conform to name construction
	 * rules in the XML 1.0 specification as it applies to an XML element.
     * 
     * @throws IllegalArgumentException if name is blank string or <code>null</code>.
	 */
	public void setName(String name) {
        if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException(
					"name should never be null or empty");
		}
		this.name = name;
	}

	/**
	 * @return the text
	 */
    // @CheckForNull

	public String getText() {
		return text;
	}

	/**
	 * @param text the text to set
	 */
	public void setText(String text) {
		this.text = text;
	}

	/**
	 * Set text and return this.
	 * This allows creating an element and setting text in one line such as:
	 * <blockquote>
	 * 	{@code Element e = new Element("when").withText("2010-05-28T02:02:09Z");}
	 * </blockquote>
	 * @param text the text to set
	 * @return the Element itself
	 */
	public Element withText(String text) {
		setText(text);
		return this;
	}

	/**
	 * @return the attributes
	 */
    @NotNull
	public Map<String, String> getAttributes() {
		return attributes;
	}

	/**
	 * @return the namespaces
	 */
	@NotNull
	public Set<Namespace> getNamespaces() {
		return namespaces == null ? Collections.emptySet() : namespaces;
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
		if (targetPrefix.isEmpty()) return false; // must have explicit prefix
		if (aNamespace.equals(namespace)) return true; // don't need to add the element's default namespace here
		if (namespaces == null) namespaces = new NamespaceSet();
		return namespaces.add(aNamespace);
	}

	/**
	 * @return the children, never null
	 */
    @NotNull
	public List<Element> getChildren() {
		return children;
	}

    /**
     * This returns the first child element within this element with the
     * given local name and belonging to the given namespace.
     * If no elements exist for the specified name and namespace, null is
     * returned.
     *
     * @param name local name of child element to match
     * @param ns {@code Namespace} to search within. If ns is null
     *      then only the name field is checked.
     * @return the first matching child element, or null if not found
     */
    // @CheckForNull

    public Element getChild(final String name, final Namespace ns) {
        if (name == null || children.isEmpty()) return null;
        for (Element child : children) {
            if (name.equals(child.getName())) {
                if (ns == null || ns.equals(child.getNamespace()))
                    return child;
            }
        }
        return null;
    }

    /**
     * This returns the first child element within this element with the
     * given local name regardless of the namespace.
     * If no elements exist for the specified name, null is
     * returned.
     *
     * @param name local name of child element to match
     * @return the first matching child element, or null if not found
     */
    // @CheckForNull

    public Element getChild(final String name) {
        return getChild(name, null);
    }

	public void accept(IStreamVisitor visitor) {
		visitor.visit(this);	
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Element [");
		b.append("Namespace=").append(namespace.getPrefix()).append(':').append(namespace.getURI()).append(", ");
        b.append("name=").append(name);
        if (!attributes.isEmpty())
            b.append(", attributes=").append(attributes);
        if (text != null) {
            String txtout = text.trim();
            if (txtout.length() != 0)
                b.append(", text=").append(text);
        }
        if (!children.isEmpty())
            b.append('\n').append("  children=").append(children);
        b.append(']');
        return b.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = attributes.hashCode(); // never null
		result = prime * result + children.hashCode(); // never null
		result = prime * result + name.hashCode(); // never null
		result = prime * result + namespace.hashCode(); // never null
		result = prime * result + ((text == null) ? 0 : text.hashCode());
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
		Element other = (Element) obj;
		if (!attributes.equals(other.attributes))
			return false;
		if (!name.equals(other.name))
			return false;
		if (!namespace.equals(other.namespace))
			return false;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
			return false;
		return children.equals(other.children);
	}

	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
        String prefix = in.readString();
        if (prefix != null) {
            String nsURI = in.readString();
            namespace = Namespace.getNamespace(prefix, nsURI);
        } else
            namespace = Namespace.NO_NAMESPACE;
		name = in.readString();
		if (name == null) throw new IOException("name field cannot be null");
		text = in.readString();
		int count = in.readInt();
		attributes.clear();
		for(int i = 0; i < count; i++) {
			String attr = in.readString();
			String val = in.readString();
			attributes.put(attr, val);
		}
		@SuppressWarnings("unchecked")
        List<Element> collection = (List<Element>) in.readObjectCollection();
        children.clear();
        if (collection != null && !collection.isEmpty())
            children.addAll(collection);

		count = in.readInt();
		if (namespaces != null) namespaces.clear();
		if (count == 0) namespaces = null;
		else {
			if (namespaces == null)
				namespaces = new NamespaceSet(count);
			for(int i = 0; i < count; i++) {
				String prefix1 = in.readString();
				String uri = in.readString();
				if (StringUtils.isNotBlank(prefix1) && StringUtils.isNotBlank(uri)) {
					namespaces.add(Namespace.getNamespace(prefix1, uri));
				}
			}
		}
	}

	public void writeData(SimpleObjectOutputStream out) throws IOException {
		if (namespace == Namespace.NO_NAMESPACE)
			out.writeString(null);
		else {
			out.writeString(namespace.getPrefix());
			out.writeString(namespace.getURI());
		}
		out.writeString(name);
		out.writeString(text);
		out.writeInt(attributes.size());
		for(Map.Entry<String,String>entry : attributes.entrySet()) {
			out.writeString(entry.getKey());
			out.writeString(entry.getValue());
		}
		out.writeObjectCollection(children);
		if (namespaces == null || namespaces.isEmpty())
			out.writeInt(0);
		else {
			out.writeInt(namespaces.size());
			for(Namespace ns : namespaces) {
				out.writeString(ns.getPrefix());
				out.writeString(ns.getURI());
			}
		}
	}

	private final static class NamespaceSet extends LinkedHashSet<Namespace> {

		private static final long serialVersionUID = -1L;

		/**
		 * Constructs a new, empty set with the default initial capacity
		 */
		public NamespaceSet() {
			// default constructor
		}

		public NamespaceSet(int initialCapacity) {
			super(initialCapacity);
		}

		/**
		 * Adds namespace to set. Enforces that only namespaces with non-empty
		 * and unique prefixes are added. If attempt to add two namespaces with
		 * different URIs but same prefix will ignore the second namespace.
		 *
		 * @param ns Namespace to add, ignored if null
		 * @return <code>true</code> if this set did not already contain the specified
		 * element
		 */
		public boolean add(Namespace ns) {
			if (ns == null || ns.getPrefix().isEmpty()) return false;
			final String targetPrefix = ns.getPrefix();
			Iterator<Namespace> it = iterator();
			while (it.hasNext()) {
				// check if namespace and/or its prefix is already present
				Namespace namespace = it.next();
				if (targetPrefix.equals(namespace.getPrefix()))
					return ns.equals(namespace);
			}
			return super.add(ns);
		}
	}

}
