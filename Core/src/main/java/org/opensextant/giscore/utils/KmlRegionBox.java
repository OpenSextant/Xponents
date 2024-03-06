package org.opensextant.giscore.utils;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.geodesy.Geodetic2DBounds;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.events.Common;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.TaggedMap;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.input.kml.KmlReader;
import org.opensextant.giscore.input.kml.UrlRef;
import org.opensextant.giscore.output.kml.KmlOutputStream;

/**
 * Create KML output with bounding box outlines from KML regions.
 * 
 * Parse KML sources and extract each bounding box from defined Regions ignoring
 * duplicates.  Creates a KML output file 'bbox.kml' (or as specified) in current
 * directory with a Placemark with LinearRing geometry for the bounding box of
 * each Region with a valid LatLonAltBox.
 * 
 * @author Jason Mathews, MITRE Corp.
 * Date: Nov 5, 2009 9:18:24 PM
 */
public class KmlRegionBox {

	private KmlOutputStream kos;
	private final List<Geodetic2DBounds> regions = new ArrayList<>();
	private File outFile;
	private boolean followLinks;

    public void checkSource(URL url) throws IOException, XMLStreamException {
		System.out.println(url);
		processKmlSource(new KmlReader(url), url.toString());
	}

	public void checkSource(File file) throws XMLStreamException, IOException {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File f : files)
					if (f.isDirectory())
						checkSource(f);
					else {
						String name = f.getName().toLowerCase();
						if (name.endsWith(".kml") || name.endsWith(".kmz"))
							checkSource(f);
					}
			}
		} else {
			System.out.println(file.getAbsolutePath());
			String name = file.getName();
			if ("doc.kml".equals(name)) {
				File parent = file.getParentFile();
				if (parent != null)
					name = parent.getName() + "/" + name;
			}
			processKmlSource(new KmlReader(file), name);
		}
	}

	private void processKmlSource(KmlReader reader, String source) throws XMLStreamException, IOException {
		try (reader){
			IGISObject o;
			while ((o = reader.read()) != null) {
				checkObject(o, source);
			}
		}

		if (followLinks) {
			List<URI> networkLinks = reader.getNetworkLinks();
			if (!networkLinks.isEmpty()) {
				reader.importFromNetworkLinks(new KmlReader.ImportEventHandler() {
                    private URI last;
					@Override
					public boolean handleEvent(UrlRef ref, IGISObject gisObj) {
                        URI uri = ref.getURI();
                        if (!uri.equals(last)) {
							// first gisObj found from a new KML source
                            System.out.println("Check NetworkLink: " +
                                    (ref.isKmz() ? ref.getKmzRelPath() : uri.toString()));
                            System.out.println();
                            last = uri;
                        }
						try {
							checkObject(gisObj, ref.toString());
						} catch (Exception e) {
							System.out.println("\t*** " + e.getMessage());
							return false;
						}
						return true;
					}
					public void handleError(URI uri, Exception ex) {
						// exceptions already logged -- no special handling needed
					}
				});
			}
		}
	}

	/* SAMPLE  checkObject()

	       <Region id="ID">
          <LatLonAltBox> <!-- optional -->
            <north></north>                            <!-- required; kml:angle90 -->
            <south></south>                            <!-- required; kml:angle90 -->
            <east></east>                              <!-- required; kml:angle180 -->
            <west></west>                              <!-- required; kml:angle180 -->
            <minAltitude>0</minAltitude>               <!-- float -->
            <maxAltitude>0</maxAltitude>               <!-- float -->
            <altitudeMode>clampToGround</altitudeMode>
                <!-- kml:altitudeModeEnum: [clampToGround], relativeToGround, or absolute -->
                <!-- or, substitute gx:altitudeMode: clampToSeaFloor, relativeToSeaFloor -->
          </LatLonAltBox>
          <Lod>  <!-- optional -->
            <minLodPixels>0</minLodPixels>             <!-- float -->
            <maxLodPixels>-1</maxLodPixels>            <!-- float -->
            <minFadeExtent>0</minFadeExtent>           <!-- float -->
            <maxFadeExtent>0</maxFadeExtent>           <!-- float -->
          </Lod>
        </Region>

	 */
    /**
     * Parse KML resource looking for Regions:
     *
     * @param o
     * @param source
     * @throws FileNotFoundException
     * @throws XMLStreamException
     */
	private void checkObject(IGISObject o, String source) throws FileNotFoundException, XMLStreamException {
		if (o instanceof Common) {
			Common f = (Common) o;
			TaggedMap region = f.getRegion();
			if (region != null) {
				List<Point> pts;
				String name = f.getName();
				try {
					double north = handleTaggedElement(IKml.NORTH, region, 90);
					double south = handleTaggedElement(IKml.SOUTH, region, 90);
					double east = handleTaggedElement(IKml.EAST, region, 180);
					double west = handleTaggedElement(IKml.WEST, region, 180);
					if (Math.abs(north - south) < 1e-5 || Math.abs(east - west) < 1e-5) {
						// incomplete bounding box or too small so skip it
						// 0.0001 (1e-4) degree dif  =~ 10 meter
						// 0.00001 (1e-5) degree dif =~ 1 meter
						// if n/s/e/w values all 0's then ignore it
						if (north != 0 || south != 0 || east != 0 || west != 0)
							System.out.println("\tbbox appears to be very small area: " + name);
						return;
					}

					// check valid Region-LatLonAltBox values:
					// kml:north > kml:south; lat range: +/- 90
					// kml:east > kml:west;   lon range: +/- 180
					if (north < south || east < west) {
						System.out.println("\tRegion has invalid LatLonAltBox: " + name);
					}

					pts = new ArrayList<>(5);
					pts.add(new Point(north, west));
					pts.add(new Point(north, east));
					pts.add(new Point(south, east));
					pts.add(new Point(south, west));
					pts.add(pts.get(0));
				} catch (NumberFormatException nfe) {
					System.out.println("\t" + nfe.getMessage() + ": " + name);
					return;
				}
				LinearRing ring = new LinearRing(pts);

				Geodetic2DBounds bounds = ring.getBoundingBox();
				if (regions.contains(bounds)) {
					System.out.println("\tduplicate bbox: " + bounds);
					return;
				}
				regions.add(bounds);
				//regions.put(bounds, bbox);

				Feature bbox = new Feature();
				bbox.setDescription(source);
				ring.setTessellate(true);
				bbox.setGeometry(ring);
				if (StringUtils.isNotBlank(name))
					bbox.setName(name + " bbox");
				else
					bbox.setName("bbox");

				if (kos == null) {
					// initialize KmlOutputStream
					if (outFile == null) outFile = new File("bbox.kml");
					kos = new KmlOutputStream(new FileOutputStream(outFile));
					kos.write(new DocumentStart(DocumentType.KML));
					ContainerStart cs = new ContainerStart(IKml.FOLDER);
					cs.setName("Region boxes");
					kos.write(cs);
				}
				kos.write(bbox);
			}
		}
	}

    public List<Geodetic2DBounds> getRegions() {
        return regions;
    }

    public void setFollowLinks(boolean followLinks) {
        this.followLinks = followLinks;
    }

    public void setOutFile(File outFile) {
        this.outFile = outFile;
    }

    public void close() {
        if (kos != null) {
			try {
				kos.close();
			} catch (IOException e) {
				System.out.println("\t*** " + e.getMessage());
			}
            kos = null;
        }
    }

	private static double handleTaggedElement(String tag, TaggedMap region, int maxDegrees) {
		String val = region.get(tag);
		if (val != null && val.length() != 0) {
			double rv;
			try {
				rv = Double.parseDouble(val);
			} catch (NumberFormatException nfe) {
				throw new NumberFormatException(String.format("Invalid value: %s=%s", tag, val));
			}
			if (Math.abs(rv) > maxDegrees) {
				throw new NumberFormatException(String.format("Invalid value out of range: %s=%s", tag, val));
			}
			return rv;
		}
		return 0;
	}

	public static void main(String[] args) {

		KmlRegionBox app = new KmlRegionBox();

		List<String> sources = new ArrayList<>();
		for (String arg : args) {
			if ("-f".equals(arg))
                app.setFollowLinks(true);
			else if (StringUtils.startsWith(arg, "-o")) {
                if (arg.length() > 2)
				    app.setOutFile(new File(arg.substring(2)));
            } else if (!arg.startsWith("-"))
				sources.add(arg);
			//System.out.println("Invalid argument: " + arg);
		}

		if (sources.isEmpty()) {
			System.out.println("Must specify file and/or URL");
			//usage();
			return;
		}

        try {
            for (String arg : sources) {
                try {
                    if (arg.startsWith("http:") || arg.startsWith("file:")) {
                        URL url = new URL(arg);
                        app.checkSource(url);
                    } else {
                        File f = new File(arg);
                        if (f.exists()) {
                            try {
                                f = f.getCanonicalFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            app.checkSource(f);
                        } else
                            app.checkSource(new URL(arg));
                    }
                } catch (MalformedURLException e) {
                    System.out.println(arg);
                    System.out.println("\t*** " + e.getMessage());
                    System.out.println();
                } catch (IOException | XMLStreamException e) {
                    System.out.println(e);
                }
			}
        } finally {
            app.close();
        }
	}

}
