/****************************************************************************************
 *  TaggedMap.java
 *
 *  Created: Feb 4, 2009
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


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There are a number of elements in KML that simply need their data 
 * carried through the pipe. This class holds a named set of name-value pairs.
 * <p>
 * For simple elements just need to add the name values pairs into the TaggedMap.
 * However, for complex elements names are flattened or normalized delimited by /'s
 * in an XPATH like syntax such that the following KML structure:
 *
 * <pre>
 *     &lt;LookAt&gt;
 *  	&lt;gx:TimeSpan&gt;
 *  	    &lt;begin&gt;2010-05-28T02:02:09Z&lt;/begin&gt;
 *  	    &lt;end&gt;2010-05-28T02:02:56Z&lt;/end&gt;
 *  	&lt;/gx:TimeSpan&gt;
 *  	&lt;longitude&gt;143.1066665234362&lt;/longitude&gt;
 *  	&lt;latitude&gt;37.1565775502346&lt;/latitude&gt;
 *     &lt;/LookAt&gt;
 * </pre>
 *
 * could be represented with following:
 *
 * <pre>{@code
 * 	TaggedMap lookAt = new TaggedMap("LookAt");
 * 	lookAt.put("longitude", "143.1066665234362");
 * 	lookAt.put("latitude", "37.1565775502346");
 * 	lookAt.put("gx:TimeSpan/begin", "2011-03-11T01:00:24.012Z");
 * 	lookAt.put("gx:TimeSpan/end", "2011-03-11T05:46:24.012Z");
 * }</pre>
 *
 * @author DRAND
 *
 */
public class TaggedMap extends HashMap<String, String> implements IDataSerializable {

	private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(TaggedMap.class);

	/**
	 * The tag of the element being held
	 */
    @NotNull private String tag;
	
	/**
	 * Empty ctor for IO only.  Constructor must be followed by call to {@code readData()}
     * to initialize the object instance otherwise object is invalid.
	 */
	public TaggedMap() {
		// caller must explicitly set tag otherwise object is invalid
		tag = ""; // must be non-null
	}
	
	/**
	 * Default constructor.
	 *
	 * @param tag the tag for the collection, never {@code null} or empty
     * @throws IllegalArgumentException if tag is null or empty 
	 */
	public TaggedMap(String tag) {
		if (StringUtils.isBlank(tag)) {
			throw new IllegalArgumentException(
					"tag should never be null or empty");
		}
		this.tag = tag;
	}

	/**
	 * @return the tag
	 */
    @NotNull
	public String getTag() {
		return tag;
	}

    /**
     * Searches for the property with the specified key in this Map.
     * The method returns the default value argument if the property is not found.
     * @param   key            the key.
     * @param   defaultValue   a default value.
     * @return  the value in this Map with the specified key value
     *          or defaultValue if not found.  Not {@code null} unless defaultValue is null.
     */
    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

	/**
	 * {@inheritDoc}
	 *
	 * @throws IllegalStateException if serialized tag is null
	 */
	public void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		String value = in.readString();
		if (value == null) throw new IllegalStateException("tag cannot be null");
		tag = value;
		int count = in.readInt();
		for(int i = 0; i < count; i++) {
			String key = in.readString();
			value = in.readString();
			put(key, value);
		}
		
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.utils.IDataSerializable#writeData(org.opensextant.giscore.utils.SimpleObjectOutputStream)
	 */
	public void writeData(SimpleObjectOutputStream out) throws IOException {
		out.writeString(tag);
		out.writeInt(size());
		for(Map.Entry<String,String> entry : entrySet()) {
			out.writeString(entry.getKey());
			out.writeString(entry.getValue());
		}
	}

    /**
     * Converts property to Integer if found.
     * @param key
     * @return Integer if property is found with a valid Integer value, otherwise null.
     */
    // @CheckForNull

    public Integer getIntegerValue(String key) {
        String val = get(key);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException nfe) {
                log.error("Error in " + key + " with value: " + val, nfe);
            }
        }
        return null;
    }

    /**
     * Converts property to Double if found.
     * @param key
     * @return Double if property is found with a valid Double value, otherwise null.
     */
    // @CheckForNull

    public Double getDoubleValue(String key) {
        String val = get(key);
        if (val != null) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException nfe) {
                log.error("Error in " + key + " with value: " + val, nfe);
            }
        }
        return null;
    }

	/**
	 * Helper method to add an integer value into the Map
	 * @param key key with which the specified value is to be associated
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with <code>key</code>, or
	 *         <code>null</code> if there was no mapping for <code>key</code>.
	 */
	public String put(String key, int value) {
		return super.put(key, Integer.toString(value));
	}

}
