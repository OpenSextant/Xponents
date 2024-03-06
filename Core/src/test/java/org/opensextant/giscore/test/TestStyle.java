package org.opensextant.giscore.test;

import org.junit.Test;
import org.opensextant.giscore.events.Style;
import org.opensextant.giscore.utils.Color;
import static org.junit.Assert.*;

/**
 * @author Jason Mathews, MITRE Corp.
 *         Date: 4/18/12 3:15 PM
 */
public class TestStyle {

	@Test
	public void testStyle() {
		Style style = new Style("123");
		style.setIconStyle(Color.red, null);
		assertTrue(style.hasIconStyle());

		style.setIconUrl("http://maps.google.com/mapfiles/kml/shapes/airports.png");
		style.setListStyle(Color.GREEN, Style.ListItemType.check);
		style.setBalloonStyle(Color.BLUE, "text $[description]", Color.BLACK, "default");

		assertNotNull(style.getId());
		assertNotNull(style.getIconUrl());
		assertTrue(style.hasBalloonStyle());
		assertTrue(style.hasIconStyle());
		assertTrue(style.hasListStyle());
		assertFalse(style.hasLineStyle());
		assertFalse(style.hasPolyStyle());
		assertFalse(style.hasLabelStyle());
		assertNull(style.getLineColor());
	}

	@Test
	public void testStyleCopyEquals() {
		Style s1 = new Style("123");
		s1.setBalloonStyle(Color.BLUE, "text $[description]", Color.BLACK, "default");
		s1.setIconStyle(Color.red, 1.4, "http://maps.google.com/mapfiles/kml/shapes/airports.png");
		s1.setLabelStyle(Color.BLUE, 1.1);
		s1.setLineStyle(Color.BLUE, 0.0);
		s1.setListStyle(Color.GREEN, Style.ListItemType.check);

		Style s2 = new Style(s1);
		assertNull(s2.getId());
		s2.setId("123"); // must explicitly set id
		assertEquals(s1, s2);
		assertEquals(s1.hashCode(), s2.hashCode());

		s2.setListStyle(Color.BLACK, Style.ListItemType.check);
		assertFalse(s1.equals(s2));
		assertFalse(s2.equals(s1));

		Style s3 = new Style();
		Style s4 = new Style(s3);
		assertEquals(s3, s4);
	}

	@Test
	public void testMerge() {
		Style style = new Style("123");
		style.setIconStyle(Color.red, null);
		style.setLineStyle(Color.YELLOW, 1.1);
		assertNull(style.getIconScale());
		assertNull(style.getIconUrl());

		Style s2 = new Style("123");
		s2.setBalloonStyle(Color.BLUE, "text $[description]", Color.BLACK, "default");
		s2.setIconStyle(Color.red, 1.4, "http://maps.google.com/mapfiles/kml/shapes/airports.png");
		s2.setLabelStyle(Color.BLUE, 1.1);
		s2.setLineStyle(Color.BLUE, 0.0);
		s2.setListStyle(Color.GREEN, Style.ListItemType.check);

		style.merge(s2);
		assertTrue(style.hasBalloonStyle());
		assertTrue(style.hasIconStyle());
		assertTrue(style.hasListStyle());
		assertTrue(style.hasLineStyle());
		assertFalse(style.hasPolyStyle());
		assertTrue(style.hasLabelStyle());
		assertNotNull(style.getIconScale());
		assertNotNull(style.getIconUrl());
	}
}
