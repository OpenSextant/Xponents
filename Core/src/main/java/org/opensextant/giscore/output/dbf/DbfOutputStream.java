/****************************************************************************************
 *  DbfOutputStream.java
 *
 *  Created: Jun 24, 2009
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
package org.opensextant.giscore.output.dbf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.input.dbf.IDbfConstants;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.output.shapefile.BinaryOutputStream;
import org.opensextant.giscore.utils.DateParser;
import org.opensextant.giscore.utils.FieldCachingObjectBuffer;
import org.opensextant.giscore.utils.ObjectBuffer;
import org.opensextant.giscore.utils.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output a DBF file using the gisoutputstream interface.
 *
 * @author DRAND
 */
public class DbfOutputStream implements IGISOutputStream, IDbfConstants {
    private static final Logger log = LoggerFactory.getLogger(DbfOutputStream.class);

    private static final String US_ASCII = "US-ASCII";
    private static final byte[] blankpad = new byte[255];
    private final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    private final DecimalFormat decimalFormat = new DecimalFormat(
            "+###############0.################;-###############0.################");
    private final DateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    {
        // instance initialization
        TimeZone tz = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(tz);
        isoDateFormat.setTimeZone(tz);
    }

    static {
        for (int i = 0; i < blankpad.length; i++) {
            blankpad[i] = ' ';
        }
    }

    /**
     * Output stream, set in ctor.
     */
    private BinaryOutputStream stream;

    /**
     * A data holder for the rows being written.
     */
    private final ObjectBuffer buffer;

    /**
     * The schema. The first object handled must be the schema. This value
     * should never be {@code null} after that. If a second schema arrives
     * an illegal state exception is thrown.
     */
    private Schema schema;

    /**
     * Track the number of records to save in the header
     */
    private int numRecords = 0;

    /**
     * Ctor
     *
     * @param outputStream the output stream
     * @param arguments    the optional arguments, none are defined for this stream
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if outputStream is null
     */
    public DbfOutputStream(OutputStream outputStream, Object[] arguments)
            throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException(
                    "outputStream should never be null");
        }
        stream = new BinaryOutputStream(outputStream);
        this.buffer = new FieldCachingObjectBuffer();

        // Write the xBaseFile signature (should be 0x03 for dBase III)
        stream.writeByte(SIGNATURE);
    }

    /**
     * Ctor
     *
     * @param outputStream the output stream
     * @param schema    the optional arguments, none are defined for this stream
     * @param buffer    the buffer
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if outputStream is null
     */
    public DbfOutputStream(OutputStream outputStream, Schema schema,
                           ObjectBuffer buffer) throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream should never be null");
        }
        this.schema = schema;
        this.buffer = buffer;
        stream = new BinaryOutputStream(outputStream);
        numRecords = (int) buffer.count();

        // Write the xBaseFile signature (should be 0x03 for dBase III)
        stream.writeByte(SIGNATURE);
    }

    /**
     * Write GISObject object to DBF
     *
     * @param object GISObject object
     * @throws IllegalArgumentException if multiple schemas are detected
     */
    public void write(IGISObject object) {
        if (object instanceof Schema) {
            if (schema == null) {
                schema = (Schema) object;
            } else {
                throw new IllegalStateException(
                        "Dbf can only handle one set of column definitions");
            }
        } else if (object instanceof Row) {
            try {
                writeRow((Row) object);
            } catch (IOException e) {
                log.error("", e);
            }
        }
    }

    private void writeRow(Row object) throws IOException {
        numRecords++;
        buffer.write(object);
    }

    /**
     * Close stream
     *
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if there is an error with the underlying data structure
     */
    public void close() throws IOException {
        if (stream != null) {
            try {
                if (buffer.count() > Integer.MAX_VALUE) {
                    throw new IllegalStateException(
                            "Trying to persist too many elements to DBF file, only 2^32 - 1 are allowed");
                }

                // Write today's date as the date of last update (3 byte binary YY
                // MM DD
                // format)
                String today = dateFormat.format(new Date(System
                        .currentTimeMillis()));
                // 2 digit year is written with Y2K +1900 assumption so add 100
                // since
                // we're past 2000
                stream.write(100 + Byte.parseByte(today.substring(2, 4)));
                for (int i = 4; i <= 6; i += 2)
                    stream.write(Byte.parseByte(today.substring(i, i + 2)));

                // Write record count (offset 0x4), header length (based on number of fields),
                // and
                // record length
                stream.writeInt((int) buffer.count(), ByteOrder.LITTLE_ENDIAN);
                stream.writeShort((short) ((schema.getKeys().size() * 32) + 33),
                        ByteOrder.LITTLE_ENDIAN);
                stream.writeShort(getRecordLength(), ByteOrder.LITTLE_ENDIAN);

                // Fill in reserved and unused header fields we don't care about
                // with
                // zeros
                for (int k = 0; k < 20; k++)
                    stream.writeByte(NUL);

                byte[] len = outputHeader();

                try {
                    outputRows(len);
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                    throw new IllegalStateException(e);
                }
            } finally {
                IOUtils.closeQuietly(stream);
                stream = null;
            }
        }
    }

    private short getRecordLength() {
        short rval = 1; // Marker byte for deleted records
        for (String fieldname : schema.getKeys()) {
            SimpleField field = schema.get(fieldname);
            rval += getFieldLength(field);
        }
        return rval;
    }

    private static int getFieldLength(SimpleField field) {
        int fieldlen = field.getLength();
        switch (field.getType()) {
            case STRING:
                if (fieldlen > MAX_CHARLEN)
                    fieldlen = MAX_CHARLEN;
                break;

            case DOUBLE:
            case FLOAT:
                fieldlen = 34;
                break;

            case INT:
            case UINT:
                fieldlen = 10;
                break;

            case SHORT:
            case USHORT:
                fieldlen = 6;
                break;

            case LONG:
                if (fieldlen < 15) fieldlen = 15; // set minlength = 15
                else if (fieldlen > 20) {
                    // max formatting of Long value (Long.MIN_VALUE) is -9223372036854775808 or 20 characters
                    fieldlen = 20;
                }
                break;

            case OID:
                fieldlen = 10;
                break;

            case DATE:
                // dates stored as string (8-bytes) in the format YYYYMMDD
                fieldlen = 8;
                break;

            case BOOL:
                fieldlen = 1;
                break;

            default:
                fieldlen = 32;
        }
        return fieldlen;
    }

    private void outputRows(byte[] len) throws ClassNotFoundException,
            IOException, InstantiationException, IllegalAccessException {
        Row row = (Row) buffer.read();

        while (row != null) {
            stream.writeByte(' ');
            int i = 0;
            for (SimpleField field : schema.getFields()) {
                short length = len[i++];
                if (length < 0) length += 256;
                Type ft = field.getType();
                if (Type.STRING.equals(field.getType())) {
                    String data = getString(row.getData(field));
                    writeStringField(stream, data, length);
                } else if (Type.DOUBLE.equals(ft) || Type.FLOAT.equals(ft)) {
                    Number data = getNumber(row.getData(field));
                    if (data == null)
                        writeField(stream, "", length);
                    else {
                        double number = data.doubleValue();
                        String decimalString = decimalFormat.format(number);
                        if (decimalString.length() > 34) {
                            // value would be truncated - use numeric exponent format (e.g. 1.2e+308)
                            decimalString = doubleExpFormat(number);
                        }
                        writeField(stream, decimalString, 34);
                    }
                } else if (Type.INT.equals(ft) || Type.UINT.equals(ft)) {
                    Number data = getNumber(row.getData(field));
                    if (data != null) {
                        int val = data.intValue();
                        writeField(stream, Integer.toString(val), 10);
                    } else {
                        writeField(stream, "", 10);
                    }
                } else if (Type.SHORT.equals(ft) || Type.USHORT.equals(ft)) {
                    Number data = getNumber(row.getData(field));
                    if (data != null) {
                        short val = data.shortValue();
                        writeField(stream, Short.toString(val), 6);
                    } else {
                        writeField(stream, "", 6);
                    }
                } else if (Type.LONG.equals(ft) || Type.OID.equals(ft)) {
                    Number data = getNumber(row.getData(field));
                    if (data == null) {
                        // some DBF implementations also interpret values starting with '*' as null
                        writeField(stream, "", length);
                    } else {
                        writeField(stream, Long.toString(data.longValue()), length);
                    }
                } else if (Type.DATE.equals(ft)) {
                    Date data = getDate(row.getData(field));
                    // NOTE: dates stored as string (8-bytes) in the format (YYYYMMDD)
                    // and timestamp if any is discarded.
                    if (data != null) {
                        writeStringField(stream, dateFormat.format(data), 8);
                    } else {
                        writeStringField(stream, "", 8);
                    }
                } else if (Type.BOOL.equals(ft)) {
                    Boolean bool = getBoolean(row.getData(field));
                    if (bool == null)
                        writeStringField(stream, "?", 1);
                    else if (bool)
                        writeStringField(stream, "T", 1);
                    else
                        writeStringField(stream, "F", 1);
                } else {
                    String data = getString(row.getData(field));
                    writeField(stream, data, 32);
                }
            }
            row = (Row) buffer.read();
        }
    }

    /**
     * Write the field data, truncating at the field length
     *
     * @param stream the stream
     * @param data   the string to write, may be more or less than the field
     *               length. This will be converted to ascii
     * @param length the field length
     * @throws IOException if an error occurs
     */
    private void writeField(BinaryOutputStream stream, String data, int length)
            throws IOException {
        byte[] str = data.getBytes(StandardCharsets.US_ASCII);
        if (str.length < length) {
            // Numeric fields are right-justified and padded with blanks to width of field in Shape DBF files
            stream.write(blankpad, 0, length - str.length);
            stream.write(str, 0, str.length);
        } else {
            if (str.length > length) {
                log.trace("Value truncated - value too large for field: {} maxlen={}", data, length);
            }
            stream.write(str, 0, length);
        }
    }

    /**
     * Write String field data, truncating value at the field length
     *
     * @param stream the stream
     * @param data   the string to write, may be more or less than the field
     *               length. This will be converted to ascii
     * @param length the field length
     * @throws IOException if an error occurs
     */
    private void writeStringField(BinaryOutputStream stream, String data, int length)
            throws IOException {
        byte[] str = data.getBytes(StandardCharsets.US_ASCII);
        if (str.length < length) {
            // String fields are left-justified and padded with blanks to width of field in Shape DBF files
            stream.write(str, 0, str.length);
            stream.write(blankpad, 0, length - str.length);
        } else {
            stream.write(str, 0, length);
        }
    }

    @Nullable
    private static Boolean getBoolean(Object data) {
        if (data instanceof Boolean) {
            return (Boolean) data;
        } else if (data instanceof String) {
            String val = (String) data;
            if (val.equals("?"))
                return null;
            val = val.toLowerCase();
            return val.startsWith("t") || val.startsWith("y")
                    || "1".equals(val);
        } else {
            return Boolean.FALSE;
        }
    }

    @NotNull
    private String getString(Object data) {
        if (data == null) {
            return "";
        } else if (data instanceof Date) {
            return isoDateFormat.format((Date) data);
        } else {
            return data.toString();
        }
    }

    private Date getDate(Object data) {
        if (data == null) {
            return null;
        } else if (data instanceof Date) {
            return (Date) data;
        } else {
            String dstr = data.toString();
            return DateParser.parse(dstr);
        }
    }

    /**
     * Format decimal number in format consistent with ESRI Arc output.
     * Example: +987000000000000000000000000000000 => 9.87e+32
     *
     * @param d the {@code double} to be converted.
     * @return a string representation of the argument.
     */
    @NotNull
    private static String doubleExpFormat(double d) {
        /*
         * Double.toString() formats exponents as 9.87E32
         * ESRI ArcMap outputs 9.87e+32
         */
        String str = Double.toString(d);
        int ind = str.lastIndexOf('E');
        if (ind == -1) {
            // ind = str.lastIndexOf('e');
            // if (ind == -1) return str;
            return str; // no exponent return as-is
        }
        StringBuilder sb = new StringBuilder();
        sb.append('e');
        if (Character.isDigit(str.charAt(ind + 1)))
            sb.append('+');
        sb.append(str.substring(ind + 1));
        if (ind + sb.length() > 24) {
            // keep up to 24 characters
            ind = 24 - sb.length();
            if (ind <= 0) return str;
        }
        sb.insert(0, str.substring(0, ind));
        return sb.toString();
    }

    @Nullable
    private Number getNumber(Object data) {
        if (data == null) {
            return null;
        } else if (data instanceof Number) {
            return (Number) data;
        } else {
            String str = data.toString();
            // max possible Long value has 20 characters
            if (str.length() <= 20 && str.indexOf('.') == -1) {
                try {
                    return Long.valueOf(str);
                } catch (NumberFormatException nfe) {
                    // ignore and try as Double
                }
            }
            return Double.valueOf(str);
        }
    }

    private byte[] outputHeader() throws IOException {
        if (schema == null) {
            throw new IllegalStateException(
                    "May not write dbf without a schema");
        }
        byte[] len = new byte[schema.getKeys().size()];
        int i = 0;
        Collection<String> attrNames = new HashSet<>();
        for (SimpleField field : schema.getFields()) {
            // Write the field name, padded with null bytes
            String fieldname = field.getName();
            byte[] name = new byte[11];
            byte[] fn = StringHelper.esriFieldName(fieldname, attrNames);
            // 11-byte field for attribute. last byte expected to be 0
            for (int k = 0; k < 11; k++) {
                if (k < fn.length) {
                    name[k] = fn[k];
                } else {
                    name[k] = 0;
                }
            }
            stream.write(name); // 0 -> 10
            byte type;
            int fieldlen = getFieldLength(field);
            int fielddec = 0;
            Type ft = field.getType();
            if (Type.STRING.equals(ft)) {
                type = 'C';
                // fieldlen varies (1..253)
            } else if (Type.DOUBLE.equals(ft) || Type.FLOAT.equals(ft)) {
                type = 'F';
                fielddec = 16;
                // fieldlen=34
                // TODO: double values > 1E+32 exceed the fixed 34 character length and will be truncated.
                // likewise 1.7E+308 cannot be a whole string -- exceeds max 253 characters in length
            } else if (Type.LONG.equals(ft)) {
                type = 'N';
                // fieldlen=min(max(fieldlen, 15), 20)
                // N (Numeric) type -> Integer or Long or Double (depends on field's decimal count and fieldLength)
                // decimal count = 0 (Integer (w/fieldLength < 10) or Long) decimal Count > 0 => Double
            } else if (Type.INT.equals(ft) || Type.UINT.equals(ft)) {
                type = 'N';
                // fieldlen=10
            } else if (Type.SHORT.equals(ft) || Type.USHORT.equals(ft)) {
                type = 'N';
                // fieldlen=6
            } else if (Type.OID.equals(ft)) {
                type = 'N';
                // fieldlen=10
            } else if (Type.DATE.equals(ft)) {
                type = 'D';
                // fieldlen=8
            } else if (Type.BOOL.equals(ft)) {
                type = 'L';
                // fieldlen=1
            } else {
                type = 'C';
                // fieldlen=32
            }
            len[i++] = (byte) fieldlen;
            stream.writeByte(type); // 11
            stream.writeByte(NUL); // 12
            stream.writeByte(NUL); // 13
            stream.writeByte(NUL); // 14
            stream.writeByte(NUL); // 15
            stream.writeByte(fieldlen); // 16 Field length, max 254
            stream.writeByte(fielddec); // 17 Decimal count
            stream.writeByte(NUL); // 18 Reserved
            stream.writeByte(NUL); // 19 Reserved
            stream.writeByte(1); // 20 Work area id, 01h for dbase III
            stream.writeByte(NUL); // 21 Reserved
            stream.writeByte(NUL); // 22 Reserved
            stream.writeByte(NUL); // 23 Flag for set fields
            stream.writeByte(NUL); // 24 Reserved
            stream.writeByte(NUL); // 25 Reserved
            stream.writeByte(NUL); // 26 Reserved
            stream.writeByte(NUL); // 27 Reserved
            stream.writeByte(NUL); // 28 Reserved
            stream.writeByte(NUL); // 29 Reserved
            stream.writeByte(NUL); // 30 Reserved
            stream.writeByte(Type.OID.equals(ft) ? 1 : 0); // 31 Index field
            // marker
        }

        // Write end-of-header (EOH) carriage-return character (hex 0x0d)
        stream.writeByte(EOH);
        return len;
    }
}
