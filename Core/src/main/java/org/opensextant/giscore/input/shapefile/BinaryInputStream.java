/****************************************************************************************
 *  BinaryInputStream.java
 *
 *  Created: Mar 2, 2007
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
package org.opensextant.giscore.input.shapefile;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * The BinaryInputStream class extends java.io.DataInputStream to allow ints, shorts, and
 * doubles to be read in either (endian) byte order.
 */
public class BinaryInputStream extends DataInputStream {
    /**
     * The Constructor takes an InputStream and decorates it as a BinaryInputStream, capable
     * of reading ints and doubles in either (endian) byte order.
     *
     * @param iStream InputStream to base BinaryInputStream on.
     */
    public BinaryInputStream(InputStream iStream) {
        super(iStream);
    }

    /**
     * Reads Integer (4 bytes) from this BinaryInputStream according to the byte order specified.
     *
     * @param order ByteOrder constant indicating endian order
     * @return integer read from this stream
     * @throws java.io.IOException error if problem occurs reading data
     */
    public int readInt(ByteOrder order) throws java.io.IOException {
        int result = 0;
        if (order == ByteOrder.LITTLE_ENDIAN)
            for (int i = 0; i < 4; i++) result |= ((short) (this.readByte() & 0xff) << (i * 8));
        else
            result = this.readInt();
        return result;
    }

    /**
     * Reads Short (2 bytes) from this BinaryInputStream according to the byte order specified.
     *
     * @param order ByteOrder constant indicating endian order
     * @return short read from this stream
     * @throws java.io.IOException error if problem occurs reading data
     */
    public short readShort(ByteOrder order) throws java.io.IOException {
        short result = 0;
        if (order == ByteOrder.LITTLE_ENDIAN)
            for (int i = 0; i < 2; i++) result |= ((short) (this.readByte() & 0xff) << (i * 8));
        else
            result = this.readShort();
        return result;
    }

    /**
     * Reads IEEE 754 Floating Point Double (8 bytes) from this BinaryInputStream according to
     * the byte order specified.
     *
     * @param order ByteOrder constant indicating endian order
     * @return double read from this stream
     * @throws java.io.IOException error if problem ocurs reading data
     */
    public double readDouble(ByteOrder order) throws java.io.IOException {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            long accum = 0L;
            for (int i = 0; i < 8; i++) accum |= ((long) (this.readByte() & 0xff)) << (i * 8);
            return Double.longBitsToDouble(accum);
        } else
            return this.readDouble();
    }
}
