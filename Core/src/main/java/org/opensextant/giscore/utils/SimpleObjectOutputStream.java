/****************************************************************************************
 *  SimpleObjectOutputStream.java
 *
 *  Created: Mar 24, 2009
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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified Object Output Stream that saves data object. This class
 * replaces {@code java.io.ObjectOutputStream} with a stream-lined implementation.
 * An SimpleObjectOutputStream writes primitive data types and Java objects
 * to an OutputStream.  The objects can be read (reconstituted) using an
 * {@link SimpleObjectInputStream}.  Persistent storage of objects can be
 * accomplished by using a file for the stream.  If the stream is a network
 * socket stream, the objects can be reconstituted on another host or in another process.
 *
 * <p>Only objects that support the IDataSerializable interface can be
 * written to streams.
 *
 * <p>The method writeObject is used to write an object to the stream.  Any
 * object, including Strings and arrays, is written with writeObject. Multiple
 * objects or primitives can be written to the stream.  The objects must be
 * read back from the corresponding SimpleObjectInputStream with the same types and
 * in the same order as they were written.
 *
 * <p>The writeObject method is responsible for writing the state of the object
 * for its particular class so that the corresponding readObject method can
 * restore it.  The method does not need to concern itself with the state
 * belonging to the object's superclasses or subclasses.  State is saved by
 * writing the individual fields to the ObjectOutputStream using the
 * writeObject method or by using the methods for primitive data types
 * supported by DataOutput.
 *
 * @author DRAND
 */
public class SimpleObjectOutputStream implements Closeable {
	// Sync with input
	private static final short NULL = 0;
	private static final short UNCACHED = 1;
	private static final short INSTANCE = 2;
	private static final short REF = 3;
	
	private static final Logger log = LoggerFactory.getLogger(SimpleObjectOutputStream.class);
	
	private final DataOutputStream stream;
	
	private final IObjectCacher cacher;
	
	/**
	 * Tracks the correspondence between a generated id and the class
	 */
	private final Map<Class<?extends IDataSerializable>,Integer> classMap = new HashMap<>();

	/**
	 * Current class id value, incremented for each additional class. Zero
	 * is reserved for {@code null} objects.
	 */
	private int cid = 1;
	
	/**
	 * Creates an ObjectOutputStream that writes to the specified OutputStream.
	 *
	 * @param s stream to hold the output data, never {@code null}
	 * @throws	IllegalArgumentException if {@code s} is {@code null}
	 */
	public SimpleObjectOutputStream(OutputStream s) {
		this(s, null);
	}
	
	/**
	 * Creates an ObjectOutputStream that writes to the specified OutputStream.
	 *
	 * @param s stream to hold the output data, never {@code null}
	 * @param cacher the cacher that decides what objects can be deduplicated, may be {@code null}
	 * @throws	IllegalArgumentException if {@code s} is {@code null}
	 */
	public SimpleObjectOutputStream(OutputStream s, IObjectCacher cacher) {
		if (s == null) {
			throw new IllegalArgumentException("s should never be null");
		}
		stream = new DataOutputStream(s);
		this.cacher = cacher; 
	}
	
	/**
	 * Close the stream. This method must be called to release any resources
     * associated with the stream.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		stream.close();
	}
	
	/**
	 * Write an object to the output object stream. Default serialization
	 * for a class is handled by its implementation of the writeObject and
	 * the readObject methods.
	 *
	 * All exceptions are fatal to the OutputStream, which is left in an
	 * indeterminate state, and it is up to the caller to ignore or recover
	 * the stream state.
	 *
	 * @param object object to be written in a serialized representation to the underlying stream
	 * @throws	IOException Any exception thrown by the underlying
     * 		OutputStream.
	 */
	public void writeObject(IDataSerializable object) throws IOException {
		if (object == null) {
			writeShort(NULL);
		} else {
			boolean writeData = true;
			boolean caching;
			if (cacher != null && cacher.shouldCache(object)) {
				caching = true;
				writeData = !cacher.hasBeenCached(object); 
			} else {
				caching = false;
			}
			if (caching) {
				if (!cacher.hasBeenCached(object)) {
					cacher.addToCache(object);
				}
				writeShort(writeData ? INSTANCE : REF);
				writeString(cacher.getObjectOutputReference(object).toString());
				if (writeData) {
					writeClass(object);
					object.writeData(this);
				} 
			} else {
				writeShort(UNCACHED);
				writeClass(object);
				object.writeData(this);
			}
		}
		
	}

	/**
	 * Write class information for the given object.
	 * 
	 * @param object
	 * @throws	IOException Any exception thrown by the underlying
     * 		OutputStream.
	 */
	private void writeClass(IDataSerializable object) throws IOException {
		Class<? extends IDataSerializable> clazz = object.getClass();
		Integer classid = classMap.get(clazz);
		if (classid != null) {
			writeBoolean(true);
			writeInt(classid);
		} else {
			writeBoolean(false);
			writeString(object.getClass().getName());
			writeInt(cid);
			classMap.put(clazz, cid);
			cid++;
		}
	}
	
	/**
	 * Write a collection of objects.
	 * @param objects the object collection, {@code null} and empty list are handled the same
	 * @throws IOException if an I/O error occurs
	 */
	public void writeObjectCollection(Collection<? extends IDataSerializable> objects) throws IOException {
		if (objects == null || objects.isEmpty()) {
			writeInt(0);
		} else {
			writeInt(objects.size());
			for(IDataSerializable ser : objects) {
				writeObject(ser);
			}
		}
	}

	/**
	 * Write an enumeration value
	 * @param v
	 * @throws IOException if an I/O error occurs
	 */
	public void writeEnum(Enum v)  throws IOException {
		if (v == null) {
			stream.writeBoolean(true);
		} else {
			stream.writeBoolean(false);
			stream.writeInt(v.ordinal());
		}
	}
	
	/**
	 * Write primitive non-array value, i.e. a string, boolean, java.awt.Color, integer,
	 * float, etc. The value is written with a prior marker to allow reading later
	 * @param value the value, must be a supported type
	 * @throws IOException if an I/O error occurs
	 */
	public void writeScalar(Object value) throws IOException {
		if (value == null) {
			stream.writeShort(SimpleObjectInputStream.NULL);
		} else if (ObjectUtils.NULL.equals(value)) {
			stream.writeShort(SimpleObjectInputStream.OBJECT_NULL);
		} else if (value instanceof Short) {
			stream.writeShort(SimpleObjectInputStream.SHORT);
			stream.writeShort(((Short)value).shortValue()); 
		} else if (value instanceof Integer) {
			stream.writeShort(SimpleObjectInputStream.INT);
			stream.writeInt(((Integer)value).intValue()); 
		} else if (value instanceof Long) {
			stream.writeShort(SimpleObjectInputStream.LONG);
			stream.writeLong(((Long)value).longValue()); 
		} else if (value instanceof Double) {
			stream.writeShort(SimpleObjectInputStream.DOUBLE);
			stream.writeDouble(((Double) value).doubleValue());
		} else if (value instanceof Float) {
			stream.writeShort(SimpleObjectInputStream.FLOAT);
			stream.writeFloat(((Float) value).floatValue());
		} else if (value instanceof String) {
			stream.writeShort(SimpleObjectInputStream.STRING);
			writeString((String) value);
		} else if (value instanceof Boolean) {
			stream.writeShort(SimpleObjectInputStream.BOOL);
			writeBoolean((Boolean) value);
		} else if (value instanceof Date) {
			stream.writeShort(SimpleObjectInputStream.DATE);
			writeLong(((Date) value).getTime());
        } else if (value instanceof Color) {
            stream.writeShort(SimpleObjectInputStream.COLOR);
            stream.writeInt(((Color)value).getRGB());
		} else if (value instanceof java.awt.Color) {
			stream.writeShort(SimpleObjectInputStream.COLOR);
			stream.writeInt(((java.awt.Color)value).getRGB());
		} else {
			log.warn("Failed to serialize unsupported type: " + value.getClass().getName());
			//throw new UnsupportedOperationException("Found unsupported type " + value.getClass());
			stream.writeShort(SimpleObjectInputStream.NULL);
		}		
	}
	

	/**
	 * Helper method that aids in writing a string to the data stream
	 * @param str the string to write.
	 * @throws IOException if an I/O error occurs
	 */
	public void writeString(String str) throws IOException {
		if (str == null) {
			stream.writeBoolean(true);
		} else {
			stream.writeBoolean(false);
			stream.writeUTF(str);
		}
	}

	/**
	 * Write a long value
	 * @param lval
	 * @throws IOException if an I/O error occurs
	 */
	public void writeLong(long lval) throws IOException {
		stream.writeLong(lval);
	}

	/**
	 * Write an int value
	 * @param ival
	 * @throws IOException if an I/O error occurs
	 */
	public void writeInt(int ival) throws IOException {
		stream.writeInt(ival);
	}
	
	/**
	 * Write a boolean value
	 * @param bval
	 * @throws IOException if an I/O error occurs
	 */
	public void writeBoolean(boolean bval) throws IOException {
		stream.writeBoolean(bval);
	}

    /**
     * Writes out a {@code byte} to the underlying output stream as
     * a 1-byte value. 
     * @param      v   a {@code byte} value to be written.
     * @throws IOException if an I/O error occurs
     */
    public void writeByte(int v) throws IOException {
        stream.writeByte(v);
    }

	/**
	 * Write a double value
	 * @param dval
	 * @throws IOException if an I/O error occurs
	 */
	public void writeDouble(double dval) throws IOException {
		stream.writeDouble(dval);
	}

	/**
	 * Write a short value
	 * @param sval
	 * @throws IOException if an I/O error occurs
	 */
	public void writeShort(short sval) throws IOException {
		stream.writeShort(sval);
	}

	/**
	 * Flushes the stream. This will write any buffered output bytes and flush
     * through to the underlying stream.
	 * @throws IOException if an I/O error occurs
	 */
	public void flush() throws IOException {
		stream.flush();
	}

}
