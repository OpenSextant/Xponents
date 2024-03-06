package org.opensextant.giscore.test.utils;

import org.junit.Test;
import org.opensextant.giscore.utils.Color;
import static org.junit.Assert.*;

/**
 * Tests for the Color class.
 *
 * @author Jason Mathews, MITRE Corporation
 *         Date: 7/5/12 7:02 PM
 */
public class TestColor {

    @Test
    public void testCreateWithAlpha() {
        Color c = new Color(0x11223344, true);
        assertEquals(c.getAlpha(), 0x11);
        assertEquals(c.getRed(), 0x22);
        assertEquals(c.getGreen(), 0x33);
        assertEquals(c.getBlue(), 0x44);
        assertEquals(c.getRGB(), 0x11223344);
        assertEquals(c.getTransparency(), 3);
    }

    @Test
    public void testCreateNoAlpha() {
        Color c = new Color(0x223344);
        assertEquals(c.getAlpha(), 0xff);
        assertEquals(c.getRed(), 0x22);
        assertEquals(c.getGreen(), 0x33);
        assertEquals(c.getBlue(), 0x44);
        assertEquals(c.getRGB(), 0xff223344);
        assertEquals(c.getTransparency(), 1);
    }

    @Test
    public void testOutRange() {
        try {
            new Color(300, 300, 300);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testEquals() {
        Color c = new Color(0, 0, 0);
        assertEquals(c, Color.BLACK);
        assertFalse(c.equals(Color.WHITE));
        assertFalse(c.equals(null));
    }

    @Test
    public void testFromKmlColor() {
		// convert from KML aabbggrr color representation
		assertEquals(Color.RED,		Color.fromKmlColor("FF0000FF")); // red
		assertEquals(Color.GREEN,	Color.fromKmlColor("FF00FF00")); // green
		assertEquals(Color.BLUE,	Color.fromKmlColor("FFFF0000")); // blue
		assertEquals(Color.YELLOW,	Color.fromKmlColor("FF00FFFF")); // yellow
    }

    @Test
    public void testFromAwtColor() {
        assertEquals(Color.BLACK, new Color(java.awt.Color.BLACK));
    }

    @Test
    public void testToAwtColor() {
        assertEquals(Color.BLACK.getRGB(), Color.BLACK.toAwtColor().getRGB());
    }

    @Test
    public void testToAwtColorAlpha() {
        Color c = new Color(10, 20, 30, 128);
        assertEquals(128, c.toAwtColor(true).getAlpha());
        assertEquals(255, c.toAwtColor(false).getAlpha());

        c = new Color(10, 20, 30);
        assertEquals(255, c.toAwtColor(true).getAlpha());
        assertEquals(255, c.toAwtColor(false).getAlpha());
    }

}
