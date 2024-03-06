/****************************************************************************************
 *  TestObjectDataSerialization.java
 *
 *  Created: Oct 28, 2009
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
package org.opensextant.giscore.test.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Test;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.Pair;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.events.SimpleField.Type;
import org.opensextant.giscore.events.Style;
import org.opensextant.giscore.events.StyleMap;
import org.opensextant.giscore.events.StyleSelector;
import org.opensextant.giscore.input.kml.IKml;
import org.opensextant.giscore.utils.Color;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import static org.junit.Assert.*;

/**
 * Test individual object data serialization 
 * @author DRAND
 *
 */
public class TestObjectDataSerialization {

    UniformRandomProvider RandomUtils = RandomSource.XO_RO_SHI_RO_128_PP.create();

    @Test
    public void testRowAndTypes() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(100);
        SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
        Row r = new Row();
        SimpleField f1 = new SimpleField("a", Type.BOOL);
        SimpleField f2 = new SimpleField("b", Type.DATE);
        SimpleField f3 = new SimpleField("c", Type.DOUBLE);
        SimpleField f4 = new SimpleField("d", Type.FLOAT);
        SimpleField f5 = new SimpleField("e", Type.INT);
        SimpleField f6 = new SimpleField("f", Type.SHORT);
        SimpleField f7 = new SimpleField("g", Type.STRING);
        SimpleField f8 = new SimpleField("h", Type.UINT);
        SimpleField f9 = new SimpleField("i", Type.USHORT);
        SimpleField f10 = new SimpleField("j", Type.LONG);
        r.putData(f1, true);
        r.putData(f2, new Date());
        r.putData(f3, RandomUtils.nextDouble());
        r.putData(f4, RandomUtils.nextFloat());
        r.putData(f5, RandomUtils.nextInt(100));
        r.putData(f6, RandomUtils.nextInt(100));
        r.putData(f7, "str" + RandomUtils.nextInt(100));
        r.putData(f8, RandomUtils.nextInt(100));
        r.putData(f9, RandomUtils.nextInt(100));
        r.putData(f10, RandomUtils.nextLong());
        soos.writeObject(r);
        soos.close();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
        Row r2 = (Row) sois.readObject();
        assertEquals(r, r2);
    }

    @Test
    public void testContainerStart() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(100);
        SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
        ContainerStart c = new ContainerStart();
        c.setId("cs1");
        c.setName("cs1");
        c.setType(IKml.DOCUMENT);
        c.setDescription("desc1");
        c.setStartTime(new Date(1));
        c.setEndTime(new Date(2));
        c.setSnippet("snippet description");
        c.setSchema(new URI("urn:xyz"));
        c.setStyleUrl("#style1");
        c.setVisibility(true);
        c.setOpen(true);
        // set some extended data properties
        c.putData(new SimpleField("date", Type.DATE), c.getStartTime());
        c.putData(new SimpleField("flag", Type.BOOL), Boolean.TRUE);
        c.putData(new SimpleField("double", Type.DOUBLE), Math.PI);

        StyleMap sm = new StyleMap();
        Style normal = new Style("sn");
        normal.setIconStyle(new Color(0, 0, 255, 127), 1.0, "normal.png");
        normal.setListStyle(null, Style.ListItemType.checkHideChildren);
        normal.setLabelStyle(null, 3.0);
        normal.setLineStyle(null, 1.1);
        normal.setPolyStyle(null, true, false);
        Style hightlight = new Style();
        hightlight.setIconStyle(Color.RED, 1.2, "hightlight.png");
        hightlight.setListStyle(Color.GREEN, null);
        hightlight.setLabelStyle(Color.WHITE, null);
        hightlight.setLineStyle(Color.RED, 1.1);
        hightlight.setPolyStyle(Color.RED, true, true);
        sm.add(new Pair(StyleMap.NORMAL, null, normal));
        sm.add(new Pair(StyleMap.HIGHLIGHT, null, hightlight));
        final Pair pair = new Pair("foo", "#style1");
        pair.setId("s1");
        sm.add(pair); // add Pair with bogus key name and a StyleUrl string
        c.addStyle(sm);

        Style style = new Style("style1");
        style.setIconStyle(new Color(0, 0, 255, 127), 1.0, "http://maps.google.com/mapfiles/kml/shapes/airports.png");
        style.setListStyle(Color.GREEN, Style.ListItemType.check);
        style.setBalloonStyle(Color.BLUE, "text", Color.BLACK, "default");
        c.addStyle(style);

        // System.out.println(c);

        soos.writeObject(c);
        soos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
        ContainerStart c2 = (ContainerStart) sois.readObject();
        sois.close();

        assertNotNull(c2);
        assertTrue(c2.isOpen());

        List<StyleSelector> styles = c2.getStyles();
        assertEquals(2, styles.size());

        assertEquals(c, c2);
        assertEquals(c.hashCode(), c2.hashCode());
    }

    @Test
    public void testNullScalar() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(100);
        SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
        soos.writeScalar(ObjectUtils.NULL);
        // next write a non-scalar object to the stream which will be serialized as null
        soos.writeScalar(this);
        soos.flush();
        soos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
        Object obj = sois.readScalar();
        Object obj2 = sois.readScalar();
        // reading past EOF should return null
        Object obj3 = sois.readObject();
        sois.close();
        assertEquals(ObjectUtils.NULL, obj);
        assertNull(obj2);
        assertNull(obj3);
    }

    // REMOVED testElement() which relied on Atom namespace.
}
