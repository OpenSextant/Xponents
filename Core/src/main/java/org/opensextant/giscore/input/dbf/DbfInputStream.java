/****************************************************************************************
 *  DbfInputStream.java
 *
 *  Created: June 23, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
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
package org.opensextant.giscore.input.dbf;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.input.GISInputStreamBase;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.input.shapefile.BinaryInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read a dbase file in as a schema and series of rows. This will also be used
 * as the basis of reading the dbf file as part of a shapefile. Implicitly this
 * class will deal with a single dbf file.
 *
 * @author Doug Rand, portions copied from DbfHandler by Paul Silvey
 */
public class DbfInputStream extends GISInputStreamBase implements
        IGISInputStream, IDbfConstants {
    private static final Logger logger = LoggerFactory
            .getLogger(DbfInputStream.class);

    /**
     * Class to instantiate when reading in rows
     */
    Class<? extends Row> rowClass = Row.class;

    /**
     * Stream reading in the dbf file
     */
    private BinaryInputStream stream;

    /**
     * The schema, derived from the dbf file
     */
    private Schema schema;

    /**
     * The count of available records in the file
     */
    private int count = 0;

    /**
     * The size of the records in bytes
     */
    private int recordSize = 0;

    /**
     * The current record pointer
     */
    private int current = 0;

    /**
     * Holds the current data record as read from the DBF
     */
    private byte[] dataBuffer;

    private transient SimpleDateFormat dateFormatter;

    /**
     * @param file
     * @param arguments
     * @throws IOException              error if problem occurs reading data
     * @throws IllegalArgumentException if {@code is} is null
     * @throws IllegalStateException    if invalid field type is encountered
     */
    public DbfInputStream(File file, Object[] arguments) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file should never be null");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("file does not exist: " + file);
        }
        InputStream is = new FileInputStream(file);
        init(is, arguments);
    }

    /**
     * @param is        InputStream, never null
     * @param arguments
     * @throws IOException              error if problem occurs reading data
     * @throws IllegalArgumentException if {@code is} is null
     * @throws IllegalStateException    if invalid field type is encountered
     */
    public DbfInputStream(InputStream is, Object[] arguments)
            throws IOException {
        if (is == null) {
            throw new IllegalArgumentException("is should never be null");
        }
        init(is, arguments);
    }

    /**
     * Do initial reading of the DBF file header. Verify that the signature is
     * of a version that this code supports and read in the column definitions.
     *
     * @param is        InputStream
     * @param arguments
     * @throws EOFException          if this input stream reaches the end before
     *                               reading all the bytes.
     * @throws IOException           error if problem occurs reading data
     * @throws IllegalStateException if invalid field type is encountered
     */
    @SuppressWarnings("fallthrough")
    private void init(InputStream is, Object[] arguments) throws IOException {
        stream = new BinaryInputStream(is);

        byte[] headBuffer = new byte[20];

        // Read and validate the xBaseFile signature (should be 0x03 for dBase
        // III)
        byte fileSig = stream.readByte();
        if (fileSig != SIGNATURE)
            throw new IOException(
                    "Expecting dBase III format signature (3), found ("
                            + fileSig + ")");

        // Read the date of last update
        if (stream.read(headBuffer, 0, 3) != 3)
            throw new EOFException();

        // Read record count, header length (used to compute the number of
        // fields), and record length
        count = stream.readInt(ByteOrder.LITTLE_ENDIAN);
        int numFields = (stream.readShort(ByteOrder.LITTLE_ENDIAN) - 33) / 32;
        recordSize = stream.readShort(ByteOrder.LITTLE_ENDIAN);

        // Skip over bytes we don't care about
        if (stream.read(headBuffer, 0, 20) != 20)
            throw new EOFException();

        schema = new Schema();
        addFirst(schema);

        for (int i = 0; i < numFields; i++) {
            // Read the field name, padded with null bytes
            if (stream.read(headBuffer, 0, 11) != 11)
                throw new EOFException();
            int j = 0;
            while ((j < 11) && (headBuffer[j] != 0))
                j++;
            SimpleField field = new SimpleField(new String(headBuffer, 0, j,
                    StandardCharsets.US_ASCII));
            char typeChar = (char) stream.readByte();

            // Skip over bytes we don't care about (field displacement in
            // memory)
            if (stream.read(headBuffer, 0, 4) != 4)
                throw new EOFException();
            int len = stream.readByte();
            // Unsigned value, correct length if byte is read as negative
            if (len < 0)
                len += 256;
            field.setLength(len);

            // read the field decimal count in bytes
            int decimalCount = stream.readByte();
            switch (typeChar) {
                case 'C':
                    field.setType(Type.STRING);
                    break;
                case 'N':
                    // Integer or Long or Double (depends on field's decimal count and fieldLength)
                    if (decimalCount == 0) {
                        if (len < 5) field.setType(Type.SHORT);
                        else if (len < 10) field.setType(Type.INT);
                        else if (len < 19) field.setType(Type.LONG);
                        else field.setType(Type.DOUBLE);
                        break;
                    }
                    // fall through
                case 'F': // Floating point/Double
                    field.setType(Type.DOUBLE);
                    field.setScale(decimalCount);
                    break;
                case 'D':
                    field.setType(Type.DATE);
                    break;
                case 'L': // Logical
                    field.setType(Type.BOOL);
                    break;
                default:
                    // could possibly encounter less commonly used DBF types ('@', 'B', 'M', 'I', '+', 'O', or 'G')
                    throw new IllegalStateException("Found unknown type "
                            + typeChar);
            }

            // Skip over bytes we don't care about
            if (stream.read(headBuffer, 0, 14) != 14)
                throw new EOFException();
            schema.put(field);
        }
        // Validate end-of-header (EOH) carriage-return character (hex 0x0d)
        byte term = stream.readByte();
        if (term != EOH)
            throw new IOException(
                    "Expecting dbf end-of-header flag (hex '0d'),"
                            + " found hex '" + byteToHex(term) + "'");
        dataBuffer = new byte[recordSize];
    }

    /**
     * Utility method for printing out bytes
     */
    private static String byteToHex(byte b) {
        String hex = Integer.toHexString(b & 0xff);
        if (hex.length() == 1)
            hex = "0" + hex;
        return hex;
    }

    public void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                logger.error("Problem closing stream", e);
            }
            stream = null;
        }
    }

  /**
   * Reads the next {@code IGISObject} from the InputStream.
   *
   * @return next {@code IGISObject},
   *         or {@code null} if the end of the stream is reached.
   * @throws IOException
   *            if an I/O error occurs or if there
   * @throws IllegalArgumentException
   *            if a fatal error with the underlying data
   */
    public IGISObject read() throws IOException {
        if (hasSaved())
            return readSaved();
        else {
            Row rval;
            try {
                rval = rowClass.newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot instantiate given row class "
                                + rowClass.getCanonicalName(), e);
            }
            if (readRecord(rval))
                return rval;
            else
                return null;
        }
    }

    /**
     * Read the next row into the given row data item. This method will also be
     * called with Feature objects when we are dealing with a shapefile.
     *
     * @param row row to be populated, never {@code null}
     * @return
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if row is null
     */
    public boolean readRecord(Row row) throws IOException {
        if (row == null) {
            throw new IllegalArgumentException("row should never be null");
        }
        if (current >= count)
            return false; // EOF
        int numRead = stream.read(dataBuffer, 0, recordSize);
        int nBytes = numRead;
        while (nBytes < recordSize) {
            numRead = stream.read(dataBuffer, nBytes, recordSize - nBytes);
            if (numRead < 0)
                throw new EOFException();
            nBytes += numRead;
        }
        // Verify Record is OK (not marked for deletion: ' ' == OK, '*' ==
        // deleted)
        if (dataBuffer[0] != ROK)
            throw new IOException("Record " + current
                    + " has deletion flag of hex " + byteToHex(dataBuffer[0]));
        int start = 1; // skip over record delete flag
        for (String fieldname : schema.getKeys()) {
            SimpleField field = schema.get(fieldname);
            // Create the appropriate type of Object for this data field and add
            // it to list
            String valStr = new String(dataBuffer, start, field.getLength(),
                    StandardCharsets.US_ASCII).trim();
            try {
                row.putData(field, parseValStr(field.getType(), valStr));
            } catch (ParseException e) {
                final IOException e2 = new IOException(e);
                throw e2;
            }
            start += field.getLength();
        }
        current++; // Point to next
        return true;
    }

    /**
     * This method is used to convert a String attribute value into a Java
     * Object of the appropriate class, based on the DBase data type.
     *
     * @param type   the type that the value must be converted to
     * @param valStr String value to be converted to Object
     * @return Object value result of parsing valStr
     * @throws ParseException error if value can not be parsed or type is unrecognized
     */
    @SuppressWarnings("fallthrough")
    private Object parseValStr(Type type, String valStr) throws ParseException {
        valStr = valStr.trim();
        if (valStr.isEmpty()) {
            // null values represented as all spaces
            return null;
        }
        // allow SHORT/INT/LONG pre-check for DOUBLE
        boolean typeCheck = true;
        switch (type) {
            case STRING:
                return valStr;

            case SHORT:
                try {
                    return Short.valueOf(valStr);
                } catch (NumberFormatException e) {
                    // fall through and try as INT
                }

            case INT:
                try {
                    return Integer.valueOf(valStr);
                } catch (NumberFormatException e) {
                    // fall through and try as LONG
                }

            case LONG:
                // numeric fields starting with '*'  are considered null
                if (valStr.startsWith("*")) return null;
                try {
                    return Long.valueOf(valStr);
                } catch (NumberFormatException e) {
                    typeCheck = false; // skip SHORT/INT/LONG pre-check
                    // fall through and try as DOUBLE
                }

            case DOUBLE:
                if (typeCheck &&
                        valStr.indexOf('.') == -1 && valStr.indexOf('+') == -1) {
                    try {
                        int len = valStr.length();
                        if (len < 5) return Short.valueOf(valStr);
                        if (len < 10) return Integer.valueOf(valStr);
                        return Long.valueOf(valStr);
                    } catch (NumberFormatException e) {
                        // try as Double
                    }
                }
                try {
                    return Double.valueOf(valStr);
                } catch (NumberFormatException e) {
                    final ParseException e2 = new ParseException(
                            "Could not parse numeric value " + valStr, 0);
                    e2.initCause(e);
                    throw e2;
                }

            case DATE:
                return getDateFormatter().parse(valStr);

            case BOOL:
                final char c = valStr.charAt(0);
                // null value for boolean represented as '?'
                return (c == '?') ? null : (c == 'Y') || (c == 'y') || (c == 'T')
                        || (c == 't');

            default:
                throw new ParseException("type '" + type
                        + "' not supported or recognized.", 0);
        }
    }

    public Class<? extends Row> getRowClass() {
        return rowClass;
    }

    public void setRowClass(Class<? extends Row> rowClass) {
        this.rowClass = rowClass;
    }

    private SimpleDateFormat getDateFormatter() {
        if (dateFormatter == null) {
            dateFormatter = new SimpleDateFormat(DATEFMT);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return dateFormatter;
    }
}
