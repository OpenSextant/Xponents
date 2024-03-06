/****************************************************************************************
 *  BinaryOutputStream.java
 *
 *  Created: Apr 16, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2006
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
package org.opensextant.giscore.output.shapefile;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;

/**
 * The BinaryOutputStream class extends java.io.DataOutputStream to allow ints, shorts, and
 * doubles to be written in either (endian) byte order.
 */
public class BinaryOutputStream extends DataOutputStream {
    /**
     * The Constructor takes an OutputStream and decorates it as a BinaryOutputStream, capable
     * of writing ints and doubles in either (endian) byte order.
     *
     * @param oStream InputStream to base BinaryOutputStream on.
     */
    public BinaryOutputStream(OutputStream oStream) {
        super(oStream);
    }

    /**
     * @return the wrapped output stream
     */
    public OutputStream getWrappedStream() {
    	return out;
    }
    
    /**
     * Writes Integer (4 bytes) to this BinaryOutputStream according to the byte order specified.
     *
     * @param order ByteOrder constant indicating endian order
     * @param anInt integer to write to this stream
     * @throws java.io.IOException error if problem occurs writing data
     */
    public void writeInt(int anInt, ByteOrder order) throws java.io.IOException {
        if (order == ByteOrder.LITTLE_ENDIAN)
            for (int i = 0; i < 4; i++) this.writeByte((byte)(anInt >> (i * 8)));
        else
            this.writeInt(anInt);
    }

    /**
     * Writes Short (2 bytes) to this BinaryOutputStream according to the byte order specified.
     *
     * @param order ByteOrder constant indicating endian order
     * @param aShort short to write to this stream
     * @throws java.io.IOException error if problem occurs reading data
     */
    public void writeShort(short aShort, ByteOrder order) throws java.io.IOException {
        if (order == ByteOrder.LITTLE_ENDIAN)
            for (int i = 0; i < 2; i++) this.writeByte((byte)(aShort >> (i * 8)));
        else
            this.writeInt(aShort);
    }

    /**
     * Writes IEEE 754 Floating Point Double (8 bytes) to this BinaryOutputStream according to
     * the byte order specified.
     *
     * @param order ByteOrder constant indicating endian order
     * @param aDouble double to write to this stream
     * @throws java.io.IOException error if problem ocurs reading data
     */
    public void writeDouble(double aDouble, ByteOrder order) throws java.io.IOException {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            long aLong = Double.doubleToLongBits(aDouble);
            for (int i = 0; i < 8; i++) this.writeByte((int) (aLong >> (i * 8)));
        } else
            this.writeDouble(aDouble);
    }
}
