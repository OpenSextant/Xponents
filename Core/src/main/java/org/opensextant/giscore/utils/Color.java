/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * @author Oleg V. Khaschansky
 */
package org.opensextant.giscore.utils;

import java.io.Serializable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Color class is a light-weight replacement for {@code java.awt.Color},
 * which is restricted from being used in environments such as the Google App
 * Engine (GAE). The primary use for Color in giscore library is for the Color
 * constructors, alpha support, and the color constants (e.g. Color.BLACK),
 * so this class has all the core methods and constants of
 * {@code java.awt.Color} and acts as a direct replacement. Most code
 * only need to change the package name from java.awt to this package.
 * <p>
 * Original source code from Apache Harmony 6.0M3 (apache-harmony-6.0-src-r991881).
 * <p>
 * Modified by Jason Mathews, July 2012.
 */
public class Color implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(Color.class);

	private static final long serialVersionUID = -1L;

    /*
	 * The values of the following colors are based on 1.5 release behavior which
     * can be revealed using the following or similar code:
     *   Color c = Color.white;
     *   System.out.println(c);
     */

	public static final Color white = new Color(255, 255, 255);

	public static final Color WHITE = white;

	public static final Color lightGray = new Color(192, 192, 192);

	public static final Color LIGHT_GRAY = lightGray;

	public static final Color gray = new Color(128, 128, 128);

	public static final Color GRAY = gray;

	public static final Color darkGray = new Color(64, 64, 64);

	public static final Color DARK_GRAY = darkGray;

	public static final Color black = new Color(0, 0, 0);

	public static final Color BLACK = black;

	public static final Color red = new Color(255, 0, 0);

	public static final Color RED = red;

	public static final Color pink = new Color(255, 175, 175);

	public static final Color PINK = pink;

	public static final Color orange = new Color(255, 200, 0);

	public static final Color ORANGE = orange;

	public static final Color yellow = new Color(255, 255, 0);

	public static final Color YELLOW = yellow;

	public static final Color green = new Color(0, 255, 0);

	public static final Color GREEN = green;

	public static final Color magenta = new Color(255, 0, 255);

	public static final Color MAGENTA = magenta;

	public static final Color cyan = new Color(0, 255, 255);

	public static final Color CYAN = cyan;

	public static final Color blue = new Color(0, 0, 255);

	public static final Color BLUE = blue;

	/**
	 * integer RGB value
	 *
	 * @see #getRGB
	 */
	int value;

	/**
	 * Creates an RGB color with the specified combined RGBA value consisting
	 * of the alpha component in bits 24-31, the red component in bits 16-23,
	 * the green component in bits 8-15, and the blue component in bits 0-7.
	 * If the {@code hasalpha} argument is <code>false</code>, alpha
	 * is defaulted to 255.
	 *
	 * @param rgba     the combined RGBA components
	 * @param hasAlpha {@code true} if the alpha bits are valid;
	 *                 {@code false} otherwise
	 * @see #getRed
	 * @see #getGreen
	 * @see #getBlue
	 * @see #getAlpha
	 * @see #getRGB
	 */
	public Color(int rgba, boolean hasAlpha) {
		if (!hasAlpha) {
			value = rgba | 0xFF000000;
		} else {
			value = rgba;
		}
	}

	/**
	 * Creates an RGB color with the specified red, green, blue, and alpha
	 * values in the range (0 - 255).
	 *
	 * @param r the red component
	 * @param g the green component
	 * @param b the blue component
	 * @param a the alpha component
	 * @throws IllegalArgumentException if {@code r}, <code>g</code>,
	 *                                  {@code b} or <code>a</code> are outside of the range
	 *                                  0 to 255, inclusive
	 * @see #getRed
	 * @see #getGreen
	 * @see #getBlue
	 * @see #getAlpha
	 * @see #getRGB
	 */
	public Color(int r, int g, int b, int a) {
		if ((r & 0xFF) != r || (g & 0xFF) != g || (b & 0xFF) != b || (a & 0xFF) != a) {
			// awt.109=Color parameter outside of expected range.
			throw new IllegalArgumentException("Color parameter outside of expected range"); //$NON-NLS-1$
		}
		value = b | (g << 8) | (r << 16) | (a << 24);
	}

	/**
	 * Creates an opaque RGB color with the specified red, green,
	 * and blue values in the range (0 - 255).
	 * The actual color used in rendering depends
	 * on finding the best match given the color space
	 * available for a given output device.
	 * Alpha is defaulted to 255.
	 *
	 * @param r the red component
	 * @param g the green component
	 * @param b the blue component
	 * @throws IllegalArgumentException if {@code r}, <code>g</code>
	 *                                  or {@code b} are outside of the range
	 *                                  0 to 255, inclusive
	 * @see #getRed
	 * @see #getGreen
	 * @see #getBlue
	 * @see #getRGB
	 */
	public Color(int r, int g, int b) {
		if ((r & 0xFF) != r || (g & 0xFF) != g || (b & 0xFF) != b) {
			// awt.109=Color parameter outside of expected range.
			throw new IllegalArgumentException("Color parameter outside of expected range"); //$NON-NLS-1$
		}
		// 0xFF for alpha channel
		value = b | (g << 8) | (r << 16) | 0xFF000000;
	}

	/**
	 * Creates an opaque RGB color with the specified combined RGB value
	 * consisting of the red component in bits 16-23, the green component
	 * in bits 8-15, and the blue component in bits 0-7.  The actual color
	 * used in rendering depends on finding the best match given the
	 * color space available for a particular output device.  Alpha is
	 * defaulted to 255.
	 *
	 * @param rgb the combined RGB components
	 * @see #getRed
	 * @see #getGreen
	 * @see #getBlue
	 * @see #getRGB
	 */
	public Color(int rgb) {
		value = rgb | 0xFF000000;
	}

	/**
	 * Creates an RGB color with the specified {@code java.awt.Color} instance
	 *
	 * @param c the Color
	 * @throws NullPointerException if c is null
	 */
	public Color(java.awt.Color c) {
		value = c.getRGB();
	}

	/**
	 * Computes the hash code for this {@code Color}.
	 *
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return value;
	}

	/**
	 * Determines whether another object is equal to this
	 * {@code Color}.
	 * <p>
	 * The result is {@code true} if and only if the argument is not
	 * {@code null} and is a <code>Color</code> object that has the same
	 * red, green, blue, and alpha values as this object.
	 *
	 * @param obj the object to test for equality with this
	 *            {@code Color}
	 * @return {@code true} if the objects are the same;
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		return obj instanceof Color && ((Color) obj).value == this.value;
	}

	/**
	 * Returns the transparency mode for this {@code Color}.
	 *
	 * @return this {@code Color} object's transparency mode.
	 */
	public int getTransparency() {
		switch (getAlpha()) {
			case 0xff:
				return 1; // Transparency.OPAQUE;
			case 0:
				return 2; // Transparency.BITMASK;
			default:
				return 3; // Transparency.TRANSLUCENT;
		}
	}

	/**
	 * Returns the RGB value representing the color in the default RGB
	 * (Bits 24-31 are alpha, 16-23 are red, 8-15 are green, 0-7 are
	 * blue).
	 *
	 * @return the RGB value of the color in the default RGB
	 * @see #getRed
	 * @see #getGreen
	 * @see #getBlue
	 */
	public int getRGB() {
		return value;
	}

	public int getRed() {
		return (value >> 16) & 0xFF;
	}

	public int getGreen() {
		return (value >> 8) & 0xFF;
	}

	public int getBlue() {
		return value & 0xFF;
	}

	/**
	 * Returns the alpha component in the range 0-255.
	 *
	 * @return the alpha component.
	 * @see #getRGB
	 */
	public int getAlpha() {
		return (value >> 24) & 0xFF;
	}

	/**
	 * Returns a string representation of this {@code Color}. This
	 * method is intended to be used only for debugging purposes.
	 * The returned string cannot be {@code null}.
	 *
	 * @return a string representation of this {@code Color}.
	 */
	@NotNull
	@Override
	public String toString() {
        /*
           The format of the string is based on 1.5 release behavior which
           can be revealed using the following code:

           Color c = new Color(1, 2, 3);
           System.out.println(c);
        */
		return getClass().getName() +
				"[r=" + getRed() + //$NON-NLS-1$
				",g=" + getGreen() + //$NON-NLS-1$
				",b=" + getBlue() + //$NON-NLS-1$
				"]"; //$NON-NLS-1$
	}

	/**
	 * Convert to a {@code java.awt.Color} instance with an opaque
	 * RGB color.
	 */
	@NotNull
	public java.awt.Color toAwtColor() {
		return new java.awt.Color(value);
	}

	/**
	 * Convert to a {@code java.awt.Color} instance.
	 * If the {@code hasAlpha} argument is <code>false</code>, alpha
	 * is defaulted to 255.
	 *
	 * @param hasAlpha {@code true} if the alpha bits are valid;
	 *                 {@code false} otherwise
	 */
	@NotNull
	public java.awt.Color toAwtColor(boolean hasAlpha) {
		return new java.awt.Color(value, hasAlpha);
	}

	/**
	 * Convert KML color string representation to equivalent Color object instance
	 *
	 * @param aabbggrr the KML color string with ABGR components.
	 *                 string length must be 8 (e.g. FF006400) otherwise not a valid color value.
	 * @return Color or null if color is invalid
	 */
	public static Color fromKmlColor(String aabbggrr) {
		if (aabbggrr != null && aabbggrr.length() == 8) {
			try {
				final int abgr = (int) Long.parseLong(aabbggrr, 16);
				return new Color(abgr & 0xff, (abgr >> 8) & 0xff, (abgr >> 16) & 0xff, (abgr >> 24) & 0xff);
			} catch (NumberFormatException e) {
				// log error below
			}
		}
		logger.warn("invalid color: {}", aabbggrr);
		return null;
	}
}
