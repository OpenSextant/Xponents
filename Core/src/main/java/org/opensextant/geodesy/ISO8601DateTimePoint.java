/************************************************************************************
 * ISO8601DateTimePoint.java 12/22/10 10:16 AM psilvey
 *
 *  (C) Copyright MITRE Corporation 2010
 *
 * The program is provided "as is" without any warranty express or implied, including
 * the warranty of non-infringement and the implied warranties of merchantability and
 * fitness for a particular purpose.  The Copyright owner will not be liable for any
 * damages suffered by you as a result of using the Program. In no event will the
 * Copyright owner be liable for any special, indirect or consequential damages or
 * lost profits even if the Copyright owner has been advised of the possibility of
 * their occurrence.
 *
 ***********************************************************************************/
package org.opensextant.geodesy;

import java.util.Date;
import java.util.TimeZone;

/**
 * The ISO8601DateTimePoint class is a simple wrapper for a time instant standardized
 * as a number of milliseconds since midnight Jan 1, 1970 GMT. This class has a
 * String constructor and toString method that respectively parse and format dates
 * and times in the ISO 8601 format. Partial dates are parsed to the beginning of the
 * time interval they define.
 */
public class ISO8601DateTimePoint implements Comparable<ISO8601DateTimePoint> {
    protected static final String INVALID_POINT = "invalid time point";

    protected final static TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");
    protected final static String dtSuffix = "-01-01T00:00:00.000Z";
    protected final static SafeDateFormat DF;

    static {
        DF = new SafeDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        DF.setTimeZone(UTC_TIMEZONE);
    }

    protected long startTime;

    /**
     * Constructor using the current local time for start time
     */
    public ISO8601DateTimePoint() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Constructor using the specified start time
     *
     * @param startTime long start time for interval
     */
    public ISO8601DateTimePoint(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Constructor that takes an ISO Date Time String to be interpreted as an
     * instant in time (start of interval if partial date time specified).
     *
     * @param isoDateTimeStr String containing an ISO 8601 Date Time point or interval
     * @throws IllegalArgumentException if a parsing error occurs
     */
    public ISO8601DateTimePoint(String isoDateTimeStr) {
		String ymdInput, ymdOutput;
        try {
            String toParse = isoDateTimeStr;
            int eoy = toParse.indexOf("-");
            if (eoy < 0) eoy = toParse.length();
            int n = toParse.length();
            toParse = toParse.replace(" ", "T");
            toParse += dtSuffix.substring(n - eoy);
            this.startTime = DF.parse(toParse).getTime();
            // Final validation that yyyy-MM-dd is a valid day (round trip test)
            ymdInput = toParse.substring(0, toParse.indexOf("T"));
            ymdOutput = this.toString();
            ymdOutput = ymdOutput.substring(0, ymdOutput.indexOf("T"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid ISO 8601 date and time, " +
                    isoDateTimeStr, ex);
        }
		if (!ymdInput.equals(ymdOutput))
				throw new IllegalArgumentException("Invalid ISO 8601 date and time, " +
                    isoDateTimeStr);
    }

    /**
     * Getter method for this point's start time in milliseconds
     *
     * @return long start time
     */
    public long getStartTimeInMillis() {
        return this.startTime;
    }

    /**
     * Setter method for this point's start time in milliseconds
     *
     * @param startTime long start time
     */
    public void setStartTimeInMillis(long startTime) {
        this.startTime = startTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ISO8601DateTimePoint that = (ISO8601DateTimePoint) o;
        return this.startTime == that.startTime;
    }

    @Override
    public int hashCode() {
        return (int) (this.startTime ^ (this.startTime >>> 32));
    }

    /**
     * Return int indicating whether this ISO8601DateTimePoint is equal to (0),
     * is before (-1), or is after (+1) the specified ISO8601DateTimePoint argument.
     *
     * @param anotherTime ISO8601DateTimePoint to compare to this ISO8601DateTimePoint
     * @return -1, 0, or +1 depending if this point is &lt;, ==, or > {@code anotherTime}
	 * @throws NullPointerException if {@code anotherTime} is <code>null</code>
     */
    public int compareTo(ISO8601DateTimePoint anotherTime) {
        return (this.startTime == anotherTime.startTime) ?
                0 : (this.startTime < anotherTime.startTime) ? -1 : + 1;
    }

    /**
     * Format this TmPoint object using the ISO 8601 point syntax, which can
     * be parsed using the String argument constructor method of this class.
     *
     * @return String ISO 8601 Date Time point syntax for this TmPoint
     */
    @Override
    public String toString() {
        return DF.format(new Date(this.startTime));
    }
}
