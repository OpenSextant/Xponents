/****************************************************************************************
 *  IDataSerializable.java
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

import java.io.IOException;

/**
 * @author DRAND
 *
 */
public interface IDataSerializable {

	/**
	 * Read object from the data stream.
	 * <p>Note other exceptions such as <code>ClassCastException</code> or <code>NullPointerException</code>
	 * and others could be thrown if the order of writeData() calls does not match the
	 * corresponding readData() calls for the same classes.
	 * 
	 * @param in
	 *            the input stream, never {@code null}
	 * 
	 * @throws IOException if an I/O error occurs or if this input stream has reached the end.
	 * @throws ClassNotFoundException if the class cannot be located
	 * @throws  IllegalAccessException  if the class or its nullary
     *               constructor is not accessible.
     * @throws  InstantiationException
     *               if this {@code Class} represents an abstract class,
     *               an interface, an array class, a primitive type, or void;
     *               or if the class has no nullary constructor;
     *               or if the instantiation fails for some other reason.
	 *
 	 * @see org.opensextant.giscore.utils.SimpleObjectInputStream#readObject()
	 */
    void readData(SimpleObjectInputStream in) throws IOException,
			ClassNotFoundException, InstantiationException, IllegalAccessException;

	/**
	 * Write the object to the data stream. This method handles
	 * the serialization to recreate the state of this object.
	 * 
	 * @param out
	 *            the output stream, never {@code null}
	 * @throws	IOException Any exception thrown by the underlying
     * 		OutputStream.
	 * @see SimpleObjectOutputStream#writeObject(org.opensextant.giscore.utils.IDataSerializable)
	 */
    void writeData(SimpleObjectOutputStream out) throws IOException;
}