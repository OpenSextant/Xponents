/****************************************************************************************
 *  TestTransfusionFields.java
 *
 *  Created: Feb 27, 2009
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
package org.opensextant.giscore.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.geometry.MultiPoint;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.IGISOutputStream;

import static org.opensextant.giscore.test.TestSupport.nextDouble;
import static org.opensextant.giscore.test.TestSupport.nextFloat;

/**
 * Test to localize issues with specific fields used for transfusion. Just tries
 * to create a file gdb with these fields.
 *
 * @author DRAND
 *
 */
public class TestTransfusionFields {
    public static File tempdir = null;


    static {
        String dir = System.getProperty("java.io.tmpdir");
        tempdir = new File(dir);
    }

    private static final String XFUSION_SCHEMA_PT = "#xfusion_schema_pt";
    private static final String XFUSION_SCHEMA_RING = "#xfusion_schema_ring";

    private static List<SimpleField> ms_fields = new ArrayList<>();
    private static SimpleField ms_field_earliestReportDate;
    private static SimpleField ms_field_latestReportDate;
    private static SimpleField ms_field_lat;
    private static SimpleField ms_field_lon;
    private static SimpleField ms_field_desc;

    static {
        ms_field_earliestReportDate = makeSimpleField("startRepDt",
                "Earliest_Report_Date", SimpleField.Type.DATE);
        ms_field_latestReportDate = makeSimpleField("endRepDt",
                "Latest_Report_Date", SimpleField.Type.DATE);
        ms_field_lat = makeSimpleField("lat", "Lat", SimpleField.Type.DOUBLE);
        ms_field_lon = makeSimpleField("lon", "Long", SimpleField.Type.DOUBLE);
        ms_field_desc = makeSimpleField("desc_", "Description", SimpleField.Type.STRING);
    }

    /**
     * Create a simple field and set the information
     *
     * @param name
     * @param displayName
     * @param type
     * @return
     */
    private static SimpleField makeSimpleField(String name, String displayName,
                                               Type type) {
        SimpleField rval = new SimpleField(name);
        rval.setType(type);
        rval.setDisplayName(StringUtils.isBlank(displayName) ? name
                : displayName);
        ms_fields.add(rval);
        return rval;
    }

    // @Test
    public void createFileGDB() throws Exception {
        File temp = File.createTempFile("test", ".zip");
        OutputStream os = new FileOutputStream(temp);
        ZipOutputStream zos = new ZipOutputStream(os);
        File gdb = new File(tempdir, "test" + System.currentTimeMillis() + ".gdb");
        IGISOutputStream gos = GISFactory.getOutputStream(DocumentType.FileGDB,
                zos, gdb);
        Schema s = new Schema();
        s.setName(XFUSION_SCHEMA_PT);
        s.setId(new URI(XFUSION_SCHEMA_PT));
        for (SimpleField field : ms_fields) {
            s.put(field);
        }
        gos.write(s);

        Schema s2 = new Schema();
        s2.setName(XFUSION_SCHEMA_RING);
        s2.setId(new URI(XFUSION_SCHEMA_RING));
        gos.write(s2);

        for (int i = 0; i < 10; i++) {
            gos.write(getFeatureT1());
            gos.write(getFeatureT2());
        }

        gos.close();
        zos.close();
        os.close();
    }

    @Test
    public void createShapefile() throws Exception {
        File temp = File.createTempFile("test", ".zip");
        OutputStream os = new FileOutputStream(temp);
        ZipOutputStream zos = new ZipOutputStream(os);
        File sf = new File(tempdir, "test" + System.currentTimeMillis());
        IGISOutputStream gos = GISFactory.getOutputStream(DocumentType.Shapefile,
                zos, sf);
        Schema s = new Schema();
        s.setName(XFUSION_SCHEMA_PT);
        s.setId(new URI(XFUSION_SCHEMA_PT));
        for (SimpleField field : ms_fields) {
            s.put(field);
        }
        gos.write(s);

        for (int i = 0; i < 10; i++) {
            gos.write(getFeatureT1());
            gos.write(getFeatureT2());
        }

        gos.close();
        zos.close();
        os.close();
    }

    public Feature getFeatureT1() throws URISyntaxException {
        Feature f = new Feature();
        f.setName("f1");
        f.setSchema(new URI(XFUSION_SCHEMA_PT));
        List<Point> pts = new ArrayList<>();
        // pts.add(new Point(new Geodetic2DPoint(RandomUtils.JDKRandom)));
        Latitude y = new Latitude(nextFloat());
        Longitude x = new Longitude(nextFloat());

        pts.add(new Point(new Geodetic2DPoint(x, y)));
        f.setGeometry(new MultiPoint(pts));
        f.putData(ms_field_earliestReportDate, new Date());
        f.putData(ms_field_latestReportDate, new Date());
        f.putData(ms_field_desc, "A random feature");
        f.putData(ms_field_lat, nextDouble() * 10.0);
        f.putData(ms_field_lon, nextDouble() * 10.0);
        return f;
    }

    public Feature getFeatureT2() throws URISyntaxException {
        Feature f = new Feature();
        f.setName("f1");
        f.setSchema(new URI(XFUSION_SCHEMA_PT));
        List<Point> pts = new ArrayList<>();
		/* TEST -- requires rewrite to make use of random point constructor
		pts.add(new Point(new Geodetic2DPoint(RandomUtils.JVM_RANDOM)));
		pts.add(new Point(new Geodetic2DPoint(RandomUtils.JVM_RANDOM)));
		pts.add(new Point(new Geodetic2DPoint(RandomUtils.JVM_RANDOM)));
		pts.add(new Point(new Geodetic2DPoint(RandomUtils.JVM_RANDOM)));
		f.putData(ms_field_lat, RandomUtils.nextDouble() * 10.0);
		f.putData(ms_field_lon, RandomUtils.nextDouble() * 10.0);
		Polygon p = new Polygon(new LinearRing(pts));
		f.setGeometry(p);
		f.putData(ms_field_earliestReportDate, new Date());
		f.putData(ms_field_latestReportDate, new Date());
		f.putData(ms_field_desc, "A random feature");
		 */
        return f;
    }
}
