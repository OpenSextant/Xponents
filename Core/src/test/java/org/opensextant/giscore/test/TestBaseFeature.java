package org.opensextant.giscore.test;

import java.util.Date;

import junit.framework.Assert;
import org.junit.Test;
import org.opensextant.giscore.events.AltitudeModeEnumType;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.TaggedMap;
import org.opensextant.giscore.events.WrappedObject;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.input.kml.IKml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Jason Mathews, MITRE Corp.
 *         Date: Sep 28, 2009 2:12:09 PM
 */
public class TestBaseFeature {

    @Test
    public void testEquals() {
        Feature f = new Feature();
        f.setId("foo");
        Feature f2 = new Feature();
        Assert.assertFalse(f.equals(f2)); // f2 has null id
        Assert.assertFalse(f2.equals(f)); // f2 has null id
        f2.setId("bar");
        Assert.assertFalse(f.equals(f2)); // f.id != f2.id

        Feature f3 = new Feature();
        f3.setId("foo");
        assertEquals(f, f3);
        assertEquals(f.hashCode(), f3.hashCode());

        // add geometry to make objects different
        f3.setGeometry(new Point(37, -122));
        Assert.assertFalse(f.equals(f3));

        Row row = new Row();
        row.setId("foo");
        Assert.assertFalse(f.equals(row));
        Assert.assertFalse(row.equals(f));
    }

    @Test
    public void testNullCompare() {
        Feature f = new Feature();
        Feature f2 = null;
        Assert.assertFalse(f.equals(f2));

        Row row = new Row();
        Row r2 = null;
        Assert.assertFalse(row.equals(r2));
    }

    @Test
    public void testGetSetDates() {
        Feature f = new Feature();
        Date now = new Date();
        Date start = (Date) now.clone();
        f.setStartTime(now);
        assertEquals(start, f.getStartTime());
        now.setTime(now.getTime() + 60000);
        Date endTime = (Date) now.clone();
        f.setEndTime(now);
        // if we're storing clones of Date objects then changing the internal structure of date
        // outside feature context won't affect what we set in our private Date field.
        assertEquals(start, f.getStartTime());
        assertEquals(endTime, f.getEndTime());
        /*
        now = f.getStartTime();
        now.setTime(now.getTime() + 60000);
        now = f.getEndTime();
        now.setTime(now.getTime() + 60000);
        // likewise when we return our private Dates the caller shouldn't be able to change
        // the internal structure
        assertEquals(start, f.getStartTime());
        assertEquals(endTime, f.getEndTime());
        */
    }

    @Test
    public void testView() {
        Feature f = new Feature();
        f.setGeometry(new Point(37, -122));
        TaggedMap viewGroup = new TaggedMap(IKml.LOOK_AT);
        viewGroup.put(IKml.LONGITUDE, "-122.081253214144");
        viewGroup.put(IKml.LATITUDE, "37.41937112712314");
        viewGroup.put(IKml.HEADING, "-145.6454960761126");
        viewGroup.put(IKml.TILT, "65.3863434407203");
        viewGroup.put(IKml.RANGE, "34.59480922516595");
        viewGroup.put(IKml.ALTITUDE_MODE, AltitudeModeEnumType.clampToGround.toString());
        viewGroup.put("foo", "bar"); // non-kml store tag
        f.setViewGroup(viewGroup);
        TaggedMap lookAt = f.getViewGroup();
        assertEquals(viewGroup.size(), lookAt.size());
    }

    @Test
    public void testNullWrapper() throws Exception {
        Feature f = null;
        WrappedObject obj = new WrappedObject(f);
        assertTrue(obj.equals(f));
    }

}

