/****************************************************************************************
 *  StyleMap.java
 *
 *  Created: Jan 28, 2009
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
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;

/**
 * A {@code StyleMap>} maps between two different Styles.
 *
 * Typically a {@code StyleMap} element is used to provide separate
 * normal and highlighted styles for a placemark, so that the highlighted
 * version appears when the user mouses over the icon in Google Earth client.
 *
 * <p>Support referenced styles via URLs as typically used in all generated
 * KML as well as inline Styles.
 *
 * @author DRAND
 * @author J.Mathews
 */
public class StyleMap extends StyleSelector {

    private static final long serialVersionUID = 1L;

    private final Map<String, Pair> mappings = new TreeMap<>();

    public final static String NORMAL = "normal";
    public final static String HIGHLIGHT = "highlight";

    /**
     * Default Ctor
     */
    public StyleMap() {
        super();
    }

    /**
     * Constructor StyleMap with id
     * @param id
     */
    public StyleMap(String id) {
        setId(id);
    }

    /**
     * Add key/url pair to StyleMap
     * @param key
     * @param url
     * throws IllegalArgumentException if key or url is null or empty string 
     */
	public void put(String key, String url) {
        key = StringUtils.trimToNull(key);
		if (key == null) {
			throw new IllegalArgumentException(
					"key should never be null or empty");
		}
        url = StringUtils.trimToNull(url);
		if (url == null) {
			throw new IllegalArgumentException(
					"url should never be null or empty");
		}
		mappings.put(key, new Pair(key, url));
	}

	/*
	 * Add key/url Pair to StyleMap.
	 * @return the previous Pair associated with same key assigned to <code>pair</code>, or
	 *         <code>null</code> if there was no mapping for its key.
	 */
	public Pair add(Pair pair) {
		if (pair == null) return null;
		return mappings.put(pair.getKey(), pair);
	}

    public boolean containsKey(String key) {
        return mappings.containsKey(key);
    }

	/**
	 * Lookup a StyleUrl from the StyleMap given a key (e.g. normal or highlight)
	 * @param key the key whose associated value is to be returned
	 * @return StyleUrl associated with key or <code>null</code> if no pair is found
	 * 		or the styleUrl for that key is <code>null</code>.
	 */
	// @CheckForNull

    public String get(String key) {
		final Pair pair = mappings.get(key);
		return pair != null ? pair.getStyleUrl() : null;
	}

	/**
	 * Lookup a particular StyleMap Pair given its <code>key</code>.
	 * @param key the key whose associated value is to be returned
	 * @return Pair associated with key or <code>null</code> if no pair is found.
	 */
	// @CheckForNull

	public Pair getPair(String key) {
		return mappings.get(key);
	}

	/**
	 * Get collection of keys associated with this StyleMap
	 * @return iterator for collection of keys, which is typically <code>normal</code>
	 * and <code>highlight</code> in a KML context.
	 */
	@NotNull
	public Iterator<String> keys() {
		return mappings.keySet().iterator();
	}

	/**
	 * Get collection of pairs for this StyleMap
	 * @return iterator for the collection of pairs, never null.
	 */
	@NotNull
	public Iterator<Pair> getPairs() {
		return mappings.values().iterator();
	}

    public void accept(IStreamVisitor visitor) {
    	visitor.visit(this);
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
		StyleMap other = (StyleMap) obj;
		return mappings.equals(other.mappings);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		// note: mappings is TreeMap so hashCode is computed in natural order of the keys
		// if Map iteration of values is in different order then two StyleMaps would have different hashCodes
		result = prime * result + mappings.hashCode();
		return result;
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#readData(org.opensextant.giscore.utils.SimpleObjectInputStream)
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		super.readData(in);
		final int count = in.readInt();
		mappings.clear();
		for (int i=0; i < count; i++) {
			Pair pair = (Pair)in.readObject();
			if (pair != null) {
				mappings.put(pair.getKey(), pair);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		super.writeData(out);
		if (mappings.isEmpty()) {
			out.writeInt(0);
		} else {
			out.writeInt(mappings.size());
			for (Pair pair : mappings.values()) {
				out.writeObject(pair);
			}
		}
	}

}
