/****************************************************************************************
 *  SingleShapefileInputHandler.java
 *
 *  Created: Jul 22, 2009
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
package org.opensextant.giscore.input.shapefile;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DBounds;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.input.GISInputStreamBase;
import org.opensextant.giscore.input.dbf.DbfInputStream;
import org.opensextant.giscore.utils.PolyHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read a single shapefile (.prj, .shp, .dbf, etc) to an object buffer for later
 * processing.
 *
 * <a href="https://www.esri.com/library/whitepapers/pdfs/shapefile.pdf">...</a>
 *
 * @author DRAND
 */
public class SingleShapefileInputHandler extends GISInputStreamBase {

    private static final Logger logger = LoggerFactory.getLogger(SingleShapefileInputHandler.class);

    // Constants
    protected static final int SIGNATURE = 9994;
    protected static final int VERSION = 1000;

    // These are the 2D ESRI shape types. Add 10 to get the equivalent 3D types.
    protected static final int NULL_TYPE = 0;
    protected static final int POINT_TYPE = 1;             // 1 is Point,     11 is PointZ
    protected static final int MULTILINE_TYPE = 3;         // 3 is MultiLine  13 is MultiLineZ (aka PolyLine)
    protected static final int MULTINESTEDRINGS_TYPE = 5;  // 5 is Polygon    15 is PolygonZ
    protected static final int MULTIPOINT_TYPE = 8;        // 8 is MultiPoint 18 is MultiPointZ

    /*
      * Schema, derived from the read dbf file
      */
    // private Schema schema;

    /*
      * Style derived from the shm file if present
      */
    //private Style style;

    /*
      * Files that hold the essential information for the shapefile.
      */
    //private final File dbfFile;
    //private final File shpFile;
    //private final File prjFile;

    /**
     * Generates to be returned features, which are decorated with the geo data
     * from the shp file and returned.
     */
    private DbfInputStream dbf;

    /**
     * Open shp file as a binary input stream
     */
    private FileChannel fileChannel;

    /**
     * Open shp stream as a channel
     */
    private ReadableByteChannel plainChannel;

    /**
     * Ensures that we don't read bytes out of order when reading from an
     * InputStream.
     */
    private int plainFileOffsetSanity = 0;
    
    /**
     * Geometry type for this shapefile
     */
    private int shpType;

    /**
     * Where we are in the shpFile currently
     */
    private int fileOffset = 0;

    /**
     * The total length of the file, used to know if we are at the end of the
     * shp, in particular when we don't have an associated dbf but always useful
     * in case of some sort of error.
     */
    private int fileLength = 0;

    /*
      * Holds the current record length, used to figure out if a geometry read
      * is overrunning the current written record for error detection purposes
      */
    // private int recLen;

    /**
     * Create {@code SingleShapefileInputHandler} for single shapefile given
     * as a series of {@code InputStream}s.
     *
     * @param shpStream the shapefile (.shp) stream.
     * @param otherStreams any other shapefile components as streams, may be {@code null}.
     * @param shapefilename  base shape file name (never {@code null} or blank
     *                       string) without the .shp extension
     * @throws IllegalArgumentException if shape stream is {@code null} or if
     *                                  shapefilename is {@code null} or blank
     *                                  string.
     * @throws IllegalStateException    if Schema not the first thing returned from dbf
     * @throws IOException              if an I/O error occurs
     */
    public SingleShapefileInputHandler(InputStream shpStream,
            Map<ShapefileComponent, InputStream> otherStreams,
            String shapefilename) throws IOException {
        if(shpStream == null) {
            throw new IllegalArgumentException("shpStream must not be null");
        }
        if (StringUtils.isBlank(shapefilename)) {
            throw new IllegalArgumentException(
                    "shapefilename should never be null or blank");
        }
        if(otherStreams != null) {
            final InputStream prjStream = otherStreams.get(ShapefileComponent.PRJ);
            if(prjStream != null) {
                checkPrj(prjStream);
            }
            final InputStream dbfStream = otherStreams.get(ShapefileComponent.DBF);
            if(dbfStream != null) {
                loadDbf(dbfStream, shapefilename);
            }
        }
        plainChannel = Channels.newChannel(shpStream);
        readHeader();
        fileOffset = 100;
    }

    /**
     * Create <code>SingleShapefileInputHandler</code> for single shapefile given
     * input directory and base filename (not including .shp extension) as
     * base name for .shp, .dbf, and .prj files.
     *
     * @param inputDirectory input directory, must exist
     * @param shapefilename  base shape file name (never null or blank string)
     *                       without the .shp extension
     * @throws IllegalArgumentException if Input directory is null or does not exist
     *                                  or if shapefilename is null or blank string.
     * @throws IllegalStateException    if Schema not the first thing returned from dbf
     * @throws IOException              if an I/O error occurs
     */
    public SingleShapefileInputHandler(File inputDirectory, String shapefilename)
            throws IOException {
        if (inputDirectory == null || !inputDirectory.exists()) {
            throw new IllegalArgumentException(
                    "Input directory must exist and be non-null");
        }
        if (StringUtils.isBlank(shapefilename)) {
            throw new IllegalArgumentException(
                    "shapefilename should never be null or blank");
        }
        //URI uri = new URI("urn:org:mitre:giscore:schema:"
        //+ UUID.randomUUID().toString());
        File dbfFile = new File(inputDirectory, shapefilename + ".dbf");
        File shpFile = new File(inputDirectory, shapefilename + ".shp");
        File prjFile = new File(inputDirectory, shapefilename + ".prj");

        if (!shpFile.exists()) {
            throw new IllegalArgumentException(
                    "SHP file missing for shapefile " + shapefilename);
        }
        if (prjFile.exists()) {
            checkPrj(new FileInputStream(prjFile));
        }

        if (dbfFile.exists()) {
            loadDbf(new FileInputStream(dbfFile), shapefilename);
        }

        FileInputStream fis = new FileInputStream(shpFile);
        try {
          fileChannel = fis.getChannel();
          readHeader();
          fileOffset = 100;
        } catch(IOException | RuntimeException e) {
            IOUtils.closeQuietly(fis);
            throw e;
        }
    }

    /**
     * Check contents of the prj file to
     *
     * @param stream                        InputStream to project file
     * @throws IOException                  if an I/O error occurs
     */
    private void checkPrj(InputStream stream) throws IOException {
        try {
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            StringWriter writer = new StringWriter();
            IOUtils.copy(reader, writer);
            PrjReader wkt = new PrjReader(writer.toString());
            PrjReader.Entry geogcs = wkt.getEntry("GEOGCS");
            if (geogcs != null && geogcs.getValues().size() > 0) {
                Object v1 = geogcs.getValues().get(0);
                if (v1 instanceof String) {
                    String datum = (String) v1;
                    if (!"GCS_WGS_1984".equals(datum) && !"WGS 84".equals(datum)) {
                        logger.warn("Shapefile is not a WGS 84 datum: " + datum);
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private void loadDbf(InputStream stream, String shapefilename) throws IOException {
        dbf = new DbfInputStream(stream, null);
        dbf.setRowClass(Feature.class);
        
        // First thing in the dbf should be a schema
        IGISObject ob = dbf.read();
        if (ob instanceof Schema) {
            Schema schema = (Schema) ob;
            schema.setName(shapefilename);
            addFirst(schema);
        } else {
            throw new IllegalStateException(
                    "Schema not the first thing returned from dbf");
        }
    }
    
    private ByteBuffer readFromChannel(int position, int size) throws IOException {
        if(fileChannel != null) {
            return fileChannel.map(MapMode.READ_ONLY, position, size);
        } else {
            if(position != plainFileOffsetSanity) {
                throw new AssertionError("Stream reading was not fully sequential, requested: " + position + " furthest seen: " + plainFileOffsetSanity);
            }
            plainFileOffsetSanity += size;
            final ByteBuffer ret = ByteBuffer.allocate(size);
            do {
                if(plainChannel.read(ret) == -1) {
                    throw new IOException("Unexpected EOF while reading at: " + position + " len: " + size);
                }
            } while(ret.hasRemaining());
            ret.rewind();
            return ret;
        }
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     *
     * @throws IllegalStateException if an I/O error occurs closing shp stream
     */
    public void close() {
        IllegalStateException channelCloseException = null;
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                channelCloseException = new IllegalStateException("Problem closing shp stream", e);
            }
            fileChannel = null;
        }
        if (plainChannel != null) {
            try {
                plainChannel.close();
            } catch (IOException e) {
                channelCloseException = new IllegalStateException("Problem closing shp stream", e);
            }
            plainChannel = null;
        }
        if (dbf != null) {
            dbf.close();
            dbf = null;
        }
        // if failed closing channel then throw exception now
        if (channelCloseException != null) throw channelCloseException;
    }

    /**
     * Read the next feature from the shapefile. A shapefile will contain a
     * uniform set of geometry features.
     *
     * @return the next feature or {@code null} if we are done.
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if unable to read a valid geometry
     */
    public IGISObject read() throws IOException {
        if (hasSaved()) {
            return readSaved();
        } else {
            return readNext();
        }
    }

    /**
     * Read the next feature from the shapefile. A shapefile will contain a
     * uniform set of geometry features.
     *
     * @return the next feature or {@code null} if we are done.
     * @throws IOException              if an I/O error occurs
     */
    private IGISObject readNext() throws IOException {
        if (fileOffset >= (2 * fileLength)) return null;

        Feature f;
        if (dbf != null) {
            f = (Feature) dbf.read();
        } else {
            f = new Feature();
        }
        boolean is3D = is3D(shpType);
        boolean includeM = isM(shpType);
        if (f != null) {
            Geometry geo = getGeometry(is3D, includeM);
            f.setGeometry(geo);
        }
        return f;
    }

    /**
     * Read the next Geometry Object and validate type. Return null at valid EOF.
     *
     * @param is3D
     * @param includeM
     * @return
     * @throws IOException              if an I/O error occurs
     */
    private Geometry getGeometry(boolean is3D, boolean includeM)
            throws IOException {
        // EOF is OK if it occurs here, otherwise we'll throw the exception to caller
        ByteBuffer buffer = readFromChannel(fileOffset, 8);
        int num = readInt(buffer, ByteOrder.BIG_ENDIAN);
        int contentLen = readInt(buffer, ByteOrder.BIG_ENDIAN); // In 16 bit words
        int nextFilePos = 2 * (contentLen + 4) + fileOffset;
        if (contentLen <= 4)
            throw new IOException("Shapefile contains badly formatted record");
        Geometry geomObj = null;
        int recLen = contentLen * 2;
        buffer = readFromChannel(fileOffset + 8, recLen);
        int recShapeType = readInt(buffer, ByteOrder.LITTLE_ENDIAN);
        if (recShapeType != NULL_TYPE) {
            if (recShapeType != shpType)
                throw new IOException("Shapefile contains record with " +
                        "unexpected shape type " + recShapeType + ", expecting " + shpType);
            // Now read the record content, corresponding to specified shapeType (adjusted if 3D)
            int st = is3D ? shpType - 10 : shpType;
            if (st == POINT_TYPE) {
                try {
                    geomObj = getPoint(buffer, is3D);
                } catch(IllegalArgumentException e ) {
                    throw new IOException(e);
                }
            } else {
                // Skip over Bounding Box coordinates (we'll reconstruct directly from points)
                for (int i = 0; i < 4; i++) readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
                if (st == MULTILINE_TYPE) geomObj = getPolyLine(buffer, is3D, includeM);
                else if (st == MULTINESTEDRINGS_TYPE) geomObj = getPolygon(buffer, is3D, includeM);
                else if (st == MULTIPOINT_TYPE) geomObj = getMultipoint(buffer, is3D, includeM);
                else throw new IOException("Shapefile contains shape type (" +
                            shpType + ") that is currently unsupported");
            }
        }
        fileOffset = nextFilePos; // Reposition for next call
        return geomObj;
    }

    /**
     * Utility method to test for 3D geometry based on shapeType code
     *
     * @param shapeType
     * @return
     */
    protected boolean is3D(int shapeType) {
        return (shapeType > 10);
    }

    /**
     * Utility method to test for geometry that include M values. We ignore M
     * values in these methods but must account for them in the data
     *
     * @param shapeType
     * @return
     */
    protected boolean isM(int shapeType) {
        return (shapeType > 20);
    }

    /**
     * Read the header from the stream
     *
     * @throws IOException              if an error occurs
     * @throws IllegalArgumentException if shape file has invalid metadata
     *                                  or not supported (e.g. not WGS-84)
     */
    private void readHeader() throws IOException {
        ByteBuffer buffer = readFromChannel(0, 100);
        shpType = getShapeTypeFromHeader(buffer);
        getBoundingBoxFromHeader(buffer, is3D(shpType));
    }

	/**
     * Read first part of shapefile header and get shapeType if possible
     * @param buffer
     * @return shapeType
     * @throws IllegalArgumentException if shape file has invalid metadata
     */
    private int getShapeTypeFromHeader(ByteBuffer buffer) {
        // Read and validate the shapefile signature (should be 9994)
        int fileSig = readInt(buffer, ByteOrder.BIG_ENDIAN);
        if (fileSig != SIGNATURE)
            throw new IllegalArgumentException("Invalid Shapefile signature");
        // Skip over unused bytes in header
        for (int i = 0; i < 5; i++) {
            readInt(buffer, ByteOrder.BIG_ENDIAN);
        }
        // Read the file length (total number of 2-byte words, including header)
        fileLength = readInt(buffer, ByteOrder.BIG_ENDIAN);  // fileLen (not used)
        // Read and validate the shapefile version (should be 1000)
        int version = readInt(buffer, ByteOrder.LITTLE_ENDIAN);
        if (version != VERSION)
            throw new IllegalArgumentException("Invalid Shapefile version " + version);
        // Read return the shapeType (validation is done later)
        return readInt(buffer, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read the remainder of shapefile header and get bounding box if possible.
     *
     * @param buffer
     * @param is3D
     * @return bounding box
     * @throws IllegalArgumentException   if lat/lon value > 8.0 * PI most likely if
     *          coordinate system is NOT WGS-84 which is assumed
     */
    private Geodetic2DBounds getBoundingBoxFromHeader(ByteBuffer buffer, boolean is3D) {
        // Read Bounding Box coordinates (assume WGS-84 decimal degrees, elevation in meters)
        double xMin = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
        double yMin = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
        double xMax = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
        double yMax = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
        double zMin = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
        double zMax = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
        // Read remaining header fields (not currently used)
        readDouble(buffer, ByteOrder.LITTLE_ENDIAN);  // mMin (not used)
        readDouble(buffer, ByteOrder.LITTLE_ENDIAN);  // mMax (not used)
        // See if the shapeType is valid and whether the points are 3D
        // check below throws IllegalArgumentException if lat/lon angles > 8.0 * PI
        Geodetic2DBounds bbox;
        if (is3D) {
            Geodetic3DPoint sw = new Geodetic3DPoint(new Longitude(xMin, Angle.DEGREES),
                    new Latitude(yMin, Angle.DEGREES), zMin);
            Geodetic3DPoint ne = new Geodetic3DPoint(new Longitude(xMax, Angle.DEGREES),
                    new Latitude(yMax, Angle.DEGREES), zMax);
            bbox = new Geodetic3DBounds(sw);
            bbox.include(ne);
        } else {
            // NOTE: if shapefile not WGS84 then may have values out of expected range
            Geodetic2DPoint sw = new Geodetic2DPoint(new Longitude(xMin, Angle.DEGREES),
                    new Latitude(yMin, Angle.DEGREES));
            Geodetic2DPoint ne = new Geodetic2DPoint(new Longitude(xMax, Angle.DEGREES),
                    new Latitude(yMax, Angle.DEGREES));
            bbox = new Geodetic2DBounds(sw);
            bbox.include(ne);
        }
        return bbox;
    }

    // Read next Point (ESRI Point or PointZ) record
    // throws IllegalArgumentException error if units or value are out of range
    private Point getPoint(ByteBuffer buffer, boolean is3D) {
        Geodetic2DPoint gp;
        Longitude lon = new Longitude(readDouble(buffer, ByteOrder.LITTLE_ENDIAN), Angle.DEGREES);
        Latitude lat = new Latitude(readDouble(buffer, ByteOrder.LITTLE_ENDIAN), Angle.DEGREES);
        if (!is3D) {
            gp = new Geodetic2DPoint(lon, lat); // ESRI Point type (X and Y fields)
        } else {
            // ESRI PointZ type (x, y, z, and m fields)
            gp = new Geodetic3DPoint(lon, lat, readDouble(buffer, ByteOrder.LITTLE_ENDIAN));
            readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // measure value M not used
        }
        return new Point(gp);
    }

    // Read PolyLine and Polygon parts offset array
    private int[] getPartOffsets(ByteBuffer buffer, int nParts, int nPoints) {
        // read index array of starting positions for each part, put total numPoints at end
        int[] parts = new int[nParts + 1];
        parts[nParts] = nPoints;
        for (int i = 0; i < nParts; i++) parts[i] = readInt(buffer, ByteOrder.LITTLE_ENDIAN);
        return parts;
    }

    // Read PolyLine and Polygon point values and return Geodetic point array
    // throws IllegalArgumentException error if units or value are out of range
    private Geodetic2DPoint[] getPolyPoints(ByteBuffer buffer, int nPoints, boolean is3D, boolean includeM) {
        Geodetic2DPoint[] pts;
        // Read the X and Y points into arrays
        double[] x = new double[nPoints];
        double[] y = new double[nPoints];
        for (int i = 0; i < nPoints; i++) {
            x[i] = readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // Longitude
            y[i] = readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // Latitude
        }
        // If 3D, read the Z bounds + values and skip over rest of record (M bounds and values)
        if (is3D) {
            readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // skip Zmin
            readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // skip Zmax
            double[] z = new double[nPoints];
            try {
                for (int i = 0; i < nPoints; i++) {
                    z[i] = readDouble(buffer, ByteOrder.LITTLE_ENDIAN);
                }
            } catch (BufferUnderflowException bfe) {
                logger.warn("Found too few z-values, the rest will be taken as 0.0");
            }
            // Convert x, y, and z values into Geodetic points, ignoring the m values
            pts = new Geodetic3DPoint[nPoints];
            for (int i = 0; i < nPoints; i++)
                pts[i] = new Geodetic3DPoint(new Longitude(x[i], Angle.DEGREES),
                        new Latitude(y[i], Angle.DEGREES), z[i]);
        } else {
            // Convert x and y values into Geodetic points
            pts = new Geodetic2DPoint[nPoints];
            for (int i = 0; i < nPoints; i++)
                pts[i] = new Geodetic2DPoint(new Longitude(x[i], Angle.DEGREES),
                        new Latitude(y[i], Angle.DEGREES));
        }
        // Do the following just to get the spanning right, we ignore the m 
        // values
        if (includeM) {
            try {
                readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // skip Mmin
                readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // skip Mmax
                for (int i = 0; i < nPoints; i++) {
                    readDouble(buffer, ByteOrder.LITTLE_ENDIAN); // skip measured vals
                }
            } catch (BufferUnderflowException bfe) {
                logger.warn("Found too few m-values, but they were being ignored anyway");
            }
        }
        return pts;
    }

    // Read next MultiLine (ESRI Polyline or PolylineZ) record
    // Take flattened file structure and construct the part hierarchy
    // throws IllegalArgumentException error if units or value are out of range
    private Geometry getPolyLine(ByteBuffer buffer, boolean is3D, boolean includeM) {
        int nParts = readInt(buffer, ByteOrder.LITTLE_ENDIAN);
        int nPoints = readInt(buffer, ByteOrder.LITTLE_ENDIAN);  // total numPoints
        int[] parts = getPartOffsets(buffer, nParts, nPoints);
        Geodetic2DPoint[] pts = getPolyPoints(buffer, nPoints, is3D, includeM);
        ArrayList<Line> lnList = new ArrayList<>();
        // Collect up the Geodetic points into the line parts
        int k = 0; // point index
        for (int j = 1; j <= nParts; j++) {
            ArrayList<Point> ptList = new ArrayList<>();
            int n = parts[j] - parts[j - 1];
            for (int i = 0; i < n; i++) ptList.add(new Point(pts[k++]));
            lnList.add(new Line(ptList));
        }
        if (lnList.size() == 1)
            return lnList.get(0);
        else
            return new MultiLine(lnList);
    }


    /**
     * Read next Polygon (ESRI Polygon or PolygonZ) record.
     * Get the polygon(s) from the information present. We take the points in the
     * poly and turn them into rings. Then the rings are sorted into outer rings,
     * which are distinguished by being clockwise rings, and inner rings, which
     * are sorted by containment.
     *
     * @param is3D     the shapefile type indicated that this shape has z data
     * @param includeM the shapefile type indicated that this shape has m or
     *                 measured data
     * @return a geometry
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     */
    private Geometry getPolygon(ByteBuffer buffer, boolean is3D, boolean includeM) {
        int nParts = readInt(buffer, ByteOrder.LITTLE_ENDIAN);
        int nPoints = readInt(buffer, ByteOrder.LITTLE_ENDIAN);  // total numPoints
        int[] parts = getPartOffsets(buffer, nParts, nPoints);
        Geodetic2DPoint[] pts = getPolyPoints(buffer, nPoints, is3D, includeM);
        // Shapefiles allow multiple outer rings intermixed with multiple inner rings
        // Our MultiLinearRings Object requires 1 outer and 0 or more inner.  We'll assume
        // inner rings follow their outer ring, and use direction as a list delimiter.
        ArrayList<PolyHolder> polyholders = new ArrayList<>();
        ArrayList<LinearRing> savedRings = new ArrayList<>();
        int k = 0; // point index
        for (int j = 1; j <= nParts; j++) {
            ArrayList<Point> ptList = new ArrayList<>();
            int n = parts[j] - parts[j - 1];
            for (int i = 0; i < n; i++) ptList.add(new Point(pts[k++]));
            LinearRing r = new LinearRing(ptList);
            if (r.clockwise()) {
                PolyHolder newPoly = new PolyHolder();
                newPoly.setOuterRing(r);
                polyholders.add(newPoly);
            } else {
                // Find a holder that has the given inner in its bounds
                boolean found = false;
                for (PolyHolder holder : polyholders) {
                    LinearRing outer = holder.getOuterRing();
                    if (outer.contains(r)) {
                        holder.addInnerRing(r);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    savedRings.add(r);
                }
            }
        }
        ArrayList<Polygon> polyList = new ArrayList<>();
        // Address all the saved rings
        for (LinearRing saved : savedRings) {
            // Find a holder that has the given inner in its bounds
            boolean found = false;
            for (PolyHolder holder : polyholders) {
                LinearRing outer = holder.getOuterRing();
                if (outer.contains(saved)) {
                    holder.addInnerRing(saved);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // If we don't find something then we'll treat the ring as a
                // poly itself
                List<Point> rpts = new ArrayList<>();
                rpts.addAll(saved.getPoints());
                Collections.reverse(rpts);
                Polygon poly = new Polygon(new LinearRing(rpts));
                polyList.add(poly);
            }
        }
        // Decide what to do. If we have one object we should return a single
        // polygon or ring. Otherwise we return a multipolygons
        if ((polyholders.size() + polyList.size()) > 1) {
            // Make polygons from holders and create
            for (PolyHolder holder : polyholders) {
                polyList.add(holder.toPolygon());
            }
            return new MultiPolygons(polyList);
        } else if ((polyholders.size() + polyList.size()) == 1) {
            Polygon poly;
            if (polyholders.size() > 0) {
                poly = polyholders.get(0).toPolygon();
            } else {
                poly = polyList.get(0);
            }
            // If this has only an outer ring then return just that
            if (poly.getLinearRings().isEmpty())
                return poly.getOuterRing();
            else
                return poly;
        } else {
            throw new IllegalStateException("Where's the geometry?");
        }
    }

    // Read next MultiPoint (ESRI MultiPoint or MultiPointZ) record
    // throws IllegalArgumentException error if units or value are out of range
    private Geometry getMultipoint(ByteBuffer buffer, boolean is3D, boolean includeM) {
        int nPoints = readInt(buffer, ByteOrder.LITTLE_ENDIAN);  // total numPoints
        Geodetic2DPoint[] pts = getPolyPoints(buffer, nPoints, is3D, includeM);
        ArrayList<Point> ptList = new ArrayList<>();
        for (int i = 0; i < nPoints; i++) ptList.add(new Point(pts[i]));
        if (ptList.size() == 1)
            return ptList.get(0);
        else
            return new MultiPoint(ptList);
    }

    /**
     * Set the order and read the next integer from the buffer
     *
     * @param buffer
     * @param order
     * @return
     */
    private int readInt(ByteBuffer buffer, ByteOrder order) {
        buffer.order(order);
        return buffer.getInt();
    }

    /**
     * Set the order and read the next double from the buffer
     *
     * @param buffer
     * @param order
     * @return The double value at the buffer's current position
     */
    private double readDouble(ByteBuffer buffer, ByteOrder order) {
        buffer.order(order);
        return buffer.getDouble();
    }
}
