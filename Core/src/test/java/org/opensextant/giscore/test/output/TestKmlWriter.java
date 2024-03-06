package org.opensextant.giscore.test.output;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipFile;

import org.junit.Test;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.NetworkLink;
import org.opensextant.giscore.events.TaggedMap;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.KmlReader;
import org.opensextant.giscore.output.kml.KmlOutputStream;
import org.opensextant.giscore.output.kml.KmlWriter;
import org.opensextant.giscore.test.TestGISBase;
import org.opensextant.giscore.utils.DateTime;
import static junit.framework.Assert.*;

import static org.opensextant.giscore.test.TestSupport.OUTPUT;
/**
 * @author Jason Mathews, MITRE Corp.
 * Date: Mar 20, 2009 11:54:04 AM
 */
public class TestKmlWriter extends TestGISBase {

    private static final File tempKmlDir = new File(OUTPUT + "/kml");

    static {
        if (tempKmlDir.mkdirs())
            System.out.println("Created temp output directory: " + tempKmlDir);
    }

    private void checkDir(File dir) {
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) checkDir(file);
			else {
				String name = file.getName().toLowerCase();
				if (name.endsWith(".kml") || name.endsWith(".kmz"))
					try {
						checkKmlFile(file);
					} catch (IOException e) {
						System.out.println("Failed to read/write: " + file + " " + e);
					}
			}
		}
    }

    private void checkKmlFile(File file) throws IOException {
        System.out.println("Testing " + file);
        KmlReader reader = new KmlReader(file);
        checkKml(reader, file.getName());
    }

    private void checkKml(KmlReader reader, String name) throws IOException {
        List<IGISObject> objs = reader.readAll(); // implicit close
        //System.out.format("features = %d%n", objs.size());
        normalizeUrls(objs);
        List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
        List<URI> links = reader.getNetworkLinks();
        // ignore error if remote test host: if unavailable then skip assertion test
        if (!links.isEmpty()) {
            // && !links.get(0).toString().startsWith("http://jason-stage")) {
            for (URI link : links) {
                if ("file".equals(link.getScheme())) {
                    // if load link from local file then should have linked features
                    assertFalse(linkedFeatures.isEmpty());
                    break;
                }
            }
            // if failed to import Failed to import from network link then linked feature count might be 0
            // assertTrue(linkedFeatures.size() != 0);
        }
        File temp;
        if (autoDelete)
            temp = new File(OUTPUT + "/test." + (reader.isCompressed() ? "kmz" : "kml"));
        else {
            String suff = name;
            if (suff == null) suff = "test";
            else {
              // strip off file extension
              int ind = suff.lastIndexOf('.');
              if (ind != -1) suff = suff.substring(0, ind);
            }
            if (suff.length() < 3) suff = "x" + suff;
            temp = createTemp(suff + "-", reader.isCompressed() ? ".kmz" : ".kml", tempKmlDir);
        }
        KmlReader reader2 = null;
		try {
			System.out.println(">create " + temp);
			KmlWriter writer = new KmlWriter(temp, reader.getEncoding());
            int features = 0;
            try {
                for (IGISObject o : objs) {
                    if (o instanceof Feature) features++;
                    writer.write(o);
                }
            } finally {
			    writer.close();
            }
			// Filter original list such that it will match the re-imported list
			/*
			List<IGISObject> objs2 = new ArrayList<IGISObject>();
			for (int i = 0; i < objs.size(); i++) {
				IGISObject o = objs.get(i);
				// KmlReader may introduce Comment Objects for skipped elements
				// so need to remove these since reading them back in will not preserve them
				if (o instanceof Comment) continue;
				// KmlWriter ignores any empty containers so any ContainerStart
				// followed by a ContainerEnd will be discarded.
				// need to remove any of these from the list from which
				// to compare to original list.
				if (o instanceof ContainerStart && i + 1 < objs.size()) {
					IGISObject next = objs.get(i + 1);
					if (next instanceof ContainerEnd) {
						if (i > 0) {
							IGISObject prev = objs.get(i - 1);
							// ignore unless previous elements are Style and StyleMaps
							// which are added to an empty container...
							if (prev instanceof Style || prev instanceof StyleMap) {
								objs2.add(o);
								continue;
							}
						}
						i++; // skip current and next items
						continue;
					}
				}
				objs2.add(o);
			}
			objs = objs2;
			*/
			reader2 = new KmlReader(temp);
			IGISObject o;
			int features2 = 0;
            /*
            Note that at random times some KML files fail while reading with
            ArrayIndexOutOfBoundsException or javax.xml.stream.XMLStreamException: ParseError
            but running same test on same test files works some times and fails other times ???
            */
			while ((o = reader2.read()) != null) {
				if (o instanceof Feature) features2++;
			}
			if (features != features2)
				System.out.println("ERROR: element count failed");
			assertEquals(features, features2);
			//List<IGISObject> elements = reader2.readAll();
			/*
			if (objs.size() != elements.size()) {
					for(Object o : objs) {
						System.out.println(" >" + o.getClass().getName());
					}
					System.out.println();
					for(Object o : elements) {
						System.out.println(" <" + o.getClass().getName());
					}
					//System.out.println("\nelts1=" + elements);
					//System.out.println("\nelts2=" + elements2);
					//System.out.println();
			}
			*/
			/*
			if (objs.size() != elements.size())
				System.out.println("ERROR: element count failed");
			assertEquals(objs.size(), elements.size());
			*/
		} finally {
            if (reader2 != null) {
                reader2.close();
            }
			// delete temp file
			if (autoDelete && temp.exists()) temp.delete();
		}
	}

	private void normalizeUrls(List<IGISObject> objs) {
		for (IGISObject o : objs) {
			KmlWriter.normalizeUrls(o);
		}
	}

	@Test
	public void test_write_kml_byte_stream() throws IOException, XMLStreamException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		KmlOutputStream kos = new KmlOutputStream(bos);
		KmlWriter writer = new KmlWriter(kos);
        try {
            kos.write(new DocumentStart(DocumentType.KML));
            Feature f = new Feature();
            f.setGeometry(new Point(42.504733587704, -71.238861602674));
            f.setName("test");
            f.setDescription("this is a test placemark");
            writer.write(f);
        } finally {
		    writer.close();
        }
		assertTrue(bos.toString().contains("this is a test placemark"));
	}

    // @Test
	public void test_NetworkLink_Kmz() throws IOException, XMLStreamException {
		File temp = createTemp("testNetworkLinks", ".kmz", tempKmlDir);
		ZipFile zf = null;
		try {
			KmlWriter writer = new KmlWriter(temp);
            NetworkLink nl = new NetworkLink();
            Feature f = null;
            try {
                assertTrue(writer.isCompressed());
                TaggedMap link = new TaggedMap(IKml.LINK);
                link.put(IKml.HREF, "kml/link.kml");
                nl.setName("NetworkLink Test");
                nl.setLink(link);
                writer.write(nl);

                // add linked KML entry to KMZ file as "kml/link.kml"
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                KmlOutputStream kos = new KmlOutputStream(bos);
                kos.write(new DocumentStart(DocumentType.KML));
                /*
                 could fill out completed GroundOverlay with icon href to image here
                 (see data/kml/groundoverlay/etna.kml) but doesn't change the test
                 results so just write out a simple Placemark.
                */
                // GroundOverlay o = new GroundOverlay();
                f = new Feature();
                f.setGeometry(new Point(42.504733587704, -71.238861602674));
                f.setName("test");
                f.setDescription("this is a test placemark");
                kos.write(f);
                kos.close();
                writer.write(new ByteArrayInputStream(bos.toByteArray()), "kml/link.kml");

                // added image entry to KMZ file
                File file = new File("data/kml/GroundOverlay/etna.jpg");
                writer.write(file, "images/etna.jpg");
            } finally {
			    writer.close();
            }

			KmlReader reader = new KmlReader(temp);
			List<IGISObject> objs = reader.readAll(); // implicit close
			// System.out.println(objs);
			/*
			for(Object o : objs) {
				System.out.println(" >" + o.getClass().getName());
			}
			System.out.println();
			*/

			assertEquals(2, objs.size());
			TestKmlOutputStream.checkApproximatelyEquals(nl, objs.get(1));

			List<IGISObject> linkedFeatures = reader.importFromNetworkLinks();
			List<URI> links = reader.getNetworkLinks();
			//System.out.println("linkedFeature=" + linkedFeatures);
			//System.out.println("links=" + links);
			assertEquals(2, linkedFeatures.size());
			assertEquals(1, links.size());
			TestKmlOutputStream.checkApproximatelyEquals(f, linkedFeatures.get(1));

			zf = new ZipFile(temp);
			assertEquals(3, zf.size());
		} finally {
			if (zf != null) zf.close();
			// delete temp file
			if (autoDelete && temp.exists()) temp.delete();
		}
	}

	/**
	 * Using TimeTest.kml example test 6 variations of timeStamps and timeSpans.
     * Verify read and write of various time start and end time combinations.
	 *
	 * @throws Exception
	 */
    // @Test
	public void test_Timestamp_Feature() throws Exception {
		File input = new File("data/kml/time/TimeTest.kml");
		//TimeZone gmt = TimeZone.getTimeZone("GMT");
		File temp = createTemp("TestTimeTest", ".kml", tempKmlDir);
		try {
			KmlReader reader = new KmlReader(input);
			List<IGISObject> objs = reader.readAll(); // implicit close

			//System.out.println("# features=" + objs.size());
			//System.out.println(objs);
			assertEquals(9, objs.size());

			/*
			 Structure of KML objects:
			  org.opensextant.giscore
.events.DocumentStart
			  org.opensextant.giscore
.events.ContainerStart
			  org.opensextant.giscore
.events.Feature - feature 0 timeStamp placemark - start marker marks earlier time in dataset
			  org.opensextant.giscore
.events.Feature - feature 1 TimeSpan only end time
			  org.opensextant.giscore
.events.Feature - feature 2 TimeSpan both start and end
			  org.opensextant.giscore
.events.Feature - feature 3 TimeSpan with only begin time
			  org.opensextant.giscore
.events.Feature - feature 4 timeStamp placemark - end marker marks latest time in dataset
			  org.opensextant.giscore
.events.Feature - feature 5 no time -> static placemark
			  org.opensextant.giscore
.events.ContainerEnd
			 */

			List<Feature> features = new ArrayList<>(6);
			for (IGISObject o : objs) {
				if (o instanceof Feature)
					features.add((Feature)o);
			}
			assertEquals(6, features.size());

			// feature 0 timeStamp placemark - start marker marks earlier time in dataset
			Feature f = features.get(0);
			/*
			DatatypeFactory fact = DatatypeFactory.newInstance();
			XMLGregorianCalendar xmlCal = fact.newXMLGregorianCalendar("2008-08-12T01:00:00Z");
			GregorianCalendar cal = xmlCal.toGregorianCalendar();
			cal.setTimeZone(gmt);
            */
			DateTime firstTime = new DateTime("2008-08-12T01:00:00Z");
			assertEquals(firstTime, f.getStartDate());
			assertEquals(firstTime, f.getEndDate());
			Geometry geom = f.getGeometry();
			Geodetic2DPoint center = geom.getCenter();
			assertEquals(new Latitude(Math.toRadians(39.104144789924)).inDegrees(), center.getLatitudeAsDegrees(), 1e-5);
			assertEquals(new Longitude(Math.toRadians(-76.72894181350101)).inDegrees(), center.getLongitudeAsDegrees(), 1e-5);

			// feature 1 TimeSpan only end time
			assertNull(features.get(1).getStartDate());
			assertNotNull(features.get(1).getEndDate());

			// feature 2 TimeSpan both start and end
			assertNotNull(features.get(2).getStartDate());
			assertNotNull(features.get(2).getEndDate());

			// feature 3 TimeSpan with only begin time
			assertNotNull(features.get(3).getStartDate());
			assertNull(features.get(3).getEndDate());

			// feature 4 timeStamp placemark - end marker marks latest time in dataset
			DateTime lastEndTime = features.get(4).getEndDate();
			assertNotNull(lastEndTime);

			// feature 5 no time -> static placemark
			assertNull(features.get(5).getStartDate());
			assertNull(features.get(5).getEndDate());

			for (Feature feat : features) {
				DateTime starTime = feat.getStartDate();
				// all begin times will be greater or equal to the time of the first feature
				if (starTime != null) {
					assertTrue(starTime.compareTo(firstTime) >= 0);
				}
				DateTime endTime = feat.getEndDate();
				// all end times will be less or equal to the end time of the last feature
				if (endTime != null)
					assertTrue(endTime.compareTo(lastEndTime) <= 0);
			}

			KmlWriter writer = new KmlWriter(temp);
            try {
                assertFalse(writer.isCompressed());
                for (IGISObject o : objs) {
                    writer.write(o);
                }
            } finally {
			    writer.close();
            }

			reader = new KmlReader(temp);
			List<IGISObject> objs2 = reader.readAll(); // implicit close
			assertEquals(objs.size(), objs2.size());
			for (int i = 0; i < objs.size(); i++) {
				TestKmlOutputStream.checkApproximatelyEquals(objs.get(i), objs2.get(i));
			}
		} finally {
			if (autoDelete && temp.exists()) temp.delete();
		}
	}

	private static final String[] timestamps = {
			"2009-01-01T00:00:00.000Z	2009-01-01T00:00:00.000Z", // when 2009
			"2009-01-01T00:00:00.000Z	2009-01-01T00:00:00.000Z", // span 2009
			"2009-02-01T00:00:00.000Z	2009-02-01T00:00:00.000Z", // when 2009-02
			"2009-02-01T00:00:00.000Z	2009-02-01T00:00:00.000Z", // span 2009-02
			"2009-03-01T00:00:00.000Z	2009-03-01T00:00:00.000Z", // when 2009-03-01
			"2009-03-01T00:00:00.000Z	2009-03-01T00:00:00.000Z", // span 2009-03-01
			"2009-04-01T01:06:30.000Z	2009-04-01T01:06:30.000Z", // when 2009-04-01T01:06:30Z
			"2009-04-02T02:06:00.000Z	2009-04-02T02:06:59.000Z", // span 2009-04-02T02:06Z
			"2009-04-03T03:10:46.000Z	2009-04-03T03:10:46.000Z", // when 2009-04-03T06:10:46+03:00
			"2009-04-04T04:10:50.000Z	2009-04-04T04:10:50.000Z", // when 2009-04-04T00:10:50-04:00
			"2009-04-05T05:10:50.000Z	2009-04-05T05:10:50.000Z"  // when 2009-04-05T05:10:50 (no timezone assumes UTC)
	};

	// @Test
	public void test_Time_Feature() throws Exception {
		File input = new File("data/kml/time/timestamps.kml");
		TimeZone tz = TimeZone.getTimeZone("UTC");
		File temp = createTemp("testTimestamps", ".kml", tempKmlDir);
		try {
			KmlReader reader = new KmlReader(input);
			List<IGISObject> objs = reader.readAll(); // implicit close

			//System.out.println(objs);
			//System.out.println("# features=" + objs.size());
			// assertEquals(9, objs.size());

			List<Feature> features = new ArrayList<>(11);
			for (IGISObject o : objs) {
				if (o instanceof Feature)
					features.add((Feature)o);
			}
			assertEquals(11, features.size());

			SimpleDateFormat df = new SimpleDateFormat(IKml.ISO_DATE_FMT);
        	df.setTimeZone(tz);

			for (int i = 0; i < features.size(); i++) {
				Feature f = features.get(i);
				Date start = f.getStartTime();
				Date end = f.getEndTime();
				String startFmt = start == null ? null : df.format(start);
				String endFmt = end == null ? null : df.format(end);
				System.out.println("\n >" + f.getClass().getName());
				System.out.format("\t%s\t%s%n", startFmt, endFmt);
				String[] startEnd = timestamps[i].split("\t");
				String expStartTime = startEnd[0];
				String expEndTime = startEnd[1];
				System.out.println("\t" + expStartTime + "\t"+ expEndTime );
				assertEquals("startTime compare @" + i, expStartTime, startFmt);
				assertEquals("endTime compare @" + i, expEndTime, endFmt);
			}

			KmlWriter writer = new KmlWriter(temp);
			for (IGISObject o : objs) {
				writer.write(o);
			}
			writer.close();

			reader = new KmlReader(temp);
			List<IGISObject> objs2 = reader.readAll(); // implicit close
			assertEquals(14, objs2.size());
			for (int i = 0; i < objs.size(); i++) {
				TestKmlOutputStream.checkApproximatelyEquals(objs.get(i), objs2.get(i));
			}
		} finally {
			if (autoDelete && temp.exists()) temp.delete();
		}
	}

    // @Test
    public void test_read_write_Kml() {
        // try bulk tests on all KML/KMZ files found in test data directories
        // read KML/KMZ and write out new file then re-read generated KML output
        // and compare to original.
        checkDir(new File("data/kml"));
    }
}
