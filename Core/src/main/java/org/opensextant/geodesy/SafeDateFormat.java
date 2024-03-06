/****************************************************************************************
 *  SafeDateFormat.java
 *
 *  Created: Apr 10, 2009
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
package org.opensextant.geodesy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.jetbrains.annotations.NotNull;

/**
 * {@link SimpleDateFormat} is not a thread safe class and therefore needs to be
 * instantiated on a per-thread basis. This class is a simplistic wrapper that
 * wraps a per-thread formatter and creates them on the fly, exposing only 
 * needed functionality.
 * 
 * @author DRAND
 */
public class SafeDateFormat {

	private final ThreadLocal<SimpleDateFormat> ms_dateFormatter =
            new ThreadLocal<>();
	
	@NotNull private final String pattern;
	@NotNull private TimeZone timeZone;

	/**
	 * Constructs a {@code SafeDateFormat} using the given pattern
	 * and UTC (aka GMT) as the default time zone.
	 *  
	 * @param pattern the pattern describing the date and time format
	 * @throws IllegalArgumentException if pattern is empty string or null
	 */
	public SafeDateFormat(String pattern) {
		this(pattern, null); // default time zone to UTC
	}

	/**
	 * Constructs a {@code SafeDateFormat} using the given pattern and TimeZone.
	 *  
	 * @param pattern the pattern describing the date and time format
	 * @param tz the given new time zone, if null UTC is used by default.
	 * @throws IllegalArgumentException if pattern is empty string, blank or null
	 */
	public SafeDateFormat(String pattern, TimeZone tz) {
		if (pattern == null || pattern.trim().length() == 0) {
			throw new IllegalArgumentException(
					"pattern should never be null or empty");
		}
		this.pattern = pattern;
		this.timeZone = (tz == null) ? TimeZone.getTimeZone("UTC") : tz;
	}
	
	private SimpleDateFormat getInstance() {
		SimpleDateFormat df = ms_dateFormatter.get();
		if (df == null) {
			df = new SimpleDateFormat(pattern);
			df.setTimeZone(timeZone);
			ms_dateFormatter.set(df);
		}
		return df;
	}
	
	/**
	 * Format the value
	 * @param value the value, never {@code null}
	 * @return the formatted value, never {@code null} or empty
	 */
	@NotNull
	public String format(Date value) {
		return getInstance().format(value);
	}

	/**
	 * Format the value
	 * @param value    The value to format
	 * @return the formatted value, never {@code null} or empty
	 * @exception IllegalArgumentException if the Format cannot format the given
	 *            object
	 */
	@NotNull
	public String format(Number value) {
		return getInstance().format(value);
	}
	
	/**
	 * Parse the value
	 * @param value the value, never {@code null}
	 * @return the parsed value, never {@code null}
	 * @exception ParseException if the beginning of the specified string
	 *            cannot be parsed.
	 */
	@NotNull
	public Date parse(String value) throws ParseException {
		return getInstance().parse(value);
	}

	/**
	 * Sets the time zone for the calendar of this SafeDateFormat object.
	 * 
     * @param timeZone the given new time zone.
	 */
	public void setTimeZone(TimeZone timeZone) {
		if (timeZone == null) timeZone = TimeZone.getTimeZone("UTC");
		this.timeZone = timeZone;
		getInstance().setTimeZone(timeZone);
	}

	/**
     * Gets the time zone.
     * @return the time zone associated with the calendar of this SafeDateFormat.
     */
	@NotNull
	public TimeZone getTimeZone() {
		return timeZone;
	}

	public String toPattern() {
		return pattern;
	}
}
