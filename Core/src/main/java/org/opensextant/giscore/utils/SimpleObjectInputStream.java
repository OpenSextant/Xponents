/****************************************************************************************
 *  SimpleObjectInputStream.java
 *
 *  Created: Mar 23, 2009
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
package org.opensextant.giscore.utils;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simplified stream that doesn't hold object references on input. This class
 * replaces {@code java.io.ObjectInputStream} with a stream-lined implementation.
 * An SimpleObjectInputStream deserializes primitive data and objects previously
 * written using an SimpleObjectOutputStream.
 *
 * <p>SimpleObjectInputStream is used to recover the state of those objects previously
 * serialized. Other uses include passing objects between hosts using a socket stream
 * or for marshaling and unmarshaling arguments and parameters in a remote communication
 * system or caching to disk.
 *
 * <p>Only objects that support the IDataSerializable interface can be read from streams.
 *
 * <p>The method {@code readObject} is used to read an object from the
 * stream.
 *
 * <p>Primitive data types can be read from the stream using the appropriate
 * method on DataInput.
 * 
 * @author DRAND
 * 
 */
public class SimpleObjectInputStream implements Closeable {

	static final int NULL = 0;
	static final int BOOL = 1;
	static final int SHORT = 2;
	static final int INT = 3;
	static final int LONG = 4;
	static final int FLOAT = 5;
	static final int DOUBLE = 6;
	static final int STRING = 7;
	static final int OBJECT_NULL = 8;
	static final int DATE = 9;
	static final int COLOR = 10;
	
	// Sync with output
	private static final short UNCACHED = 1;
	private static final short INSTANCE = 2;
	private static final short REF = 3;
	
	private final DataInputStream stream;
	
	private final Map<Integer, Class<IDataSerializable>> classMap = new HashMap<>();
	
	/**
	 * Objects that are references in the input stream. Used to reduce small
	 * counts of objects that are used thousands of times in the input stream.
	 */
	private final Map<String, Object> refs = new HashMap<>();
	
	/**
	 * Creates an SimpleObjectInputStream that reads from the specified InputStream.
	 * 
	 * @param	in  input stream to read from, never null
	 * @throws  IllegalArgumentException if {@code in} is {@code null}
	 */
	public SimpleObjectInputStream(InputStream in) {
		if (in == null) {
			throw new IllegalArgumentException("in should never be null");
		}
		stream = new DataInputStream(in);
	}

	/**
	 * Closes the input stream. Must be called to release any resources
     * associated with the stream.
	 */
	@Override
	public void close() {
		IOUtils.closeQuietly(stream);
	}

	/**
	 * Read the next object from the stream
	 * 
	 * @return the next object, or {@code null} if the stream is empty.
	 *
	 * @exception IOException if an I/O error occurs
	 * @exception ClassNotFoundException if the class cannot be located
	 * @exception  IllegalAccessException  if the class or its nullary
     *               constructor is not accessible.
     * @exception  InstantiationException
     *               if this {@code Class} represents an abstract class,
     *               an interface, an array class, a primitive type, or void;
     *               or if the class has no nullary constructor;
     *               or if the instantiation fails for some other reason.
	 * @exception IllegalStateException
	 * 				if cannot determine the class to instantiate
	 */
    @Nullable
	public Object readObject() throws ClassNotFoundException, IOException,
			InstantiationException, IllegalAccessException {
		try {
			short type = readShort();
			IDataSerializable rval;
			if (type == NULL) {
				return null;
			} else if (type == UNCACHED) {
				rval = readClass();
				if (rval == null) {
					throw new IllegalStateException("Couldn't reify class");
				}
				rval.readData(this);
			} else {
				String ref = readString();
				if (type == INSTANCE) {
					rval = readClass();
					if (rval == null) {
						throw new IllegalStateException("Couldn't reify class");
					}
					rval.readData(this);
					refs.put(ref, rval);
				} else {
					rval = (IDataSerializable) refs.get(ref);
				}
			}
			return rval;
		} catch(EOFException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private IDataSerializable readClass() throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		boolean classref = readBoolean();
		Class<IDataSerializable> clazz;
		if (classref) {
			int refid = readInt();
			if (refid == 0) {
				return null;
			} else {
				clazz = classMap.get(refid);
				// clazz may be null if reference is bogus
				if (clazz == null) return null;
			}
		} else {
			String className = readString();
			int refid = readInt();
			clazz = (Class<IDataSerializable>) Class.forName(className);
			classMap.put(refid, clazz);
		}
		return clazz.newInstance();
	}

	/**
	 * Read a collection of objects from the stream. Note that {@link SimpleObjectOutputStream#writeObjectCollection}
	 * makes no difference if the original list was null or empty so must use
	 * {@link #readNonNullObjectCollection} instead if expecting a non-null list.
	 * @return the collection of object, may be {@code null}
	 * @throws IOException if an I/O error occurs
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public List<? extends IDataSerializable> readObjectCollection()
			throws IOException, ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		int count = readInt();
		if (count == 0) {
			return null;
		}
		List rval = new ArrayList(count);
		for (int i = 0; i < count; i++) {
			rval.add(readObject());
		}
		return rval;
	}

	/**
	 * Read a collection of objects from the stream and return a non-null
	 * List. This method reverses the effects of {@link SimpleObjectOutputStream#writeObjectCollection}
	 * where empty Lists are indistinguishable from null objects. Use this method rather than
	 * {@code readObjectCollection()} when implementing {@link IDataSerializable#readData}
	 * for non-nullable List objects.
	 * @return the collection of objects or
	 * 		an empty list of target type if serialized list was {@code null}, never null
	 * @throws IOException if an I/O error occurs
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> readNonNullObjectCollection()
		throws IOException, ClassNotFoundException, InstantiationException,
			IllegalAccessException {
		List<T> list = (List<T>)readObjectCollection();
		return (list == null) ? new ArrayList<>() : list;
	}

	/**
	 * Read an enumeration value from the data stream.
	 * <p>The following example illustrates writing and reading an enumeration value:
	 * <pre>{@code
	 *	import org.opensextant.giscore.events.Style.ListItemType;
	 *	// ...
	 *	ListItemType listItemType = ListItemType.checkHideChildren;
	 *	SimpleObjectOutputStream out = ...
	 *	// write enumeration value:
	 *	out.writeEnum(listItemType);
	 *	// ...
	 *	SimpleObjectInputStream in = ...
	 *	// read back the enumeration value from SimpleObjectInputStream
	 *	listItemType = (ListItemType)in.readEnum(ListItemType.class);
	 * }</pre>
	 * @param enumClass Target enumeration class, never null
	 * @return the next Enum value, {@code null} if the enumeration value was originally null
	 * 	or if the saved ordinal index isn't in range of the Enumeration.
	 * @throws IOException if an I/O error occurs
	 * @throws NullPointerException if enumClass is {@code null}
	 */
	@Nullable
	public Enum readEnum(@NotNull Class<? extends Enum> enumClass) throws IOException {
		boolean isnull = stream.readBoolean();
		if (isnull) {
			return null;
		} else {
			int ord = stream.readInt();
			Enum[] enumValues = enumClass.getEnumConstants();
			return enumValues != null && ord >= 0 && ord < enumValues.length
					? enumValues[ord] : null;
		}
	}

	/**
	 * @return the next scalar object from the stream
	 * @throws IOException if an I/O error occurs
	 * @throws UnsupportedOperationException if encounters an unknown type
	 */
	@Nullable
	public Object readScalar() throws IOException {
		int type = stream.readShort();
		switch (type) {
		case NULL:
			return null;
		case OBJECT_NULL:
			return ObjectUtils.NULL;
		case SHORT:
			return stream.readShort();
		case INT:
			return stream.readInt();
		case LONG:
			return stream.readLong();
		case DOUBLE:
			return stream.readDouble();
		case FLOAT:
			return stream.readFloat();
		case STRING:
			return readString();
		case BOOL:
			return stream.readBoolean();
		case DATE:
			return new Date(stream.readLong());
		case COLOR:
			int value = stream.readInt();
			return new Color(value, true);
		default:
			throw new UnsupportedOperationException(
					"Found unsupported scalar enum " + type);
		}
	}

	/**
	 * Read a string from the data stream
	 * 
	 * @return The string read, possibly {@code null}.
	 * @throws IOException if an I/O error occurs
	 */
    @Nullable
	public String readString() throws IOException {
		boolean isnull = stream.readBoolean();
		if (isnull) {
			return null;
		} else {
			return stream.readUTF();
		}
	}

	/**
	 * @return the next long value
	 * @throws IOException if an I/O error occurs
	 */
	public long readLong() throws IOException {
		return stream.readLong();
	}

	/**
	 * @return the next int value
	 * @throws IOException if an I/O error occurs
	 */
	public int readInt() throws IOException {
		return stream.readInt();
	}

    /**
     * See the general contract of the {@code readByte}
     * method of {@code DataInput}.
	 * @return the next byte value
	 * @throws IOException if an I/O error occurs
	 */
	public int readByte() throws IOException {
		return stream.readByte();
	}

	/**
	 * @return the next boolean value
	 * @throws IOException if an I/O error occurs
	 */
	public boolean readBoolean() throws IOException {
		return stream.readBoolean();
	}

	/**
	 * @return the next double value
	 * @throws IOException if an I/O error occurs
	 */
	public double readDouble() throws IOException {
		return stream.readDouble();
	}

	/**
	 * @return the next short value
	 * @throws IOException if an I/O error occurs
	 */
	public short readShort() throws IOException {
		return stream.readShort();
	}

}
