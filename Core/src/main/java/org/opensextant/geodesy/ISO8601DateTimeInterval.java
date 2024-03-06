/************************************************************************************
 * ISO8601DateTimeInterval.java 12/22/10 10:40 AM psilvey
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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * The ISO8601DateTimeInterval class is a simple wrapper for a time interval
 * with a start and end time.
 */
public class ISO8601DateTimeInterval extends ISO8601DateTimePoint {
    private static final String INVALID_ORDER = "invalid time order";

    private long endTime;

    /**
     * Constructor using the current local time for both start and end times
     */
    public ISO8601DateTimeInterval() {
        super();
        this.endTime = this.startTime;
    }

    /**
     * Constructor using the specified start and end times such that
	 * the start time is earlier or equal to the end time.
     *
     * @param startTime long start time for interval
     * @param endTime   long end time for interval
     * @throws IllegalArgumentException if points are not in correct time order
     */
    public ISO8601DateTimeInterval(long startTime, long endTime)
            throws IllegalArgumentException {
        if (endTime < startTime) throw new IllegalArgumentException(INVALID_ORDER);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Constructor that takes an ISO Date Time String time point or interval.
     *
     * @param isoDateTimeStr String containing ISO 8601 Date Time point or interval
     * @throws IllegalArgumentException if a parsing error occurs
     */
    public ISO8601DateTimeInterval(String isoDateTimeStr) {
        try {
            int i = isoDateTimeStr.indexOf("--");
            if (i > 0) {
                ISO8601DateTimeInterval tSeg1 =
                        new ISO8601DateTimeInterval(isoDateTimeStr.substring(0, i));
                ISO8601DateTimeInterval tSeg2 =
                        new ISO8601DateTimeInterval(isoDateTimeStr.substring(i + 2));
                long startTime1 = tSeg1.getStartTimeInMillis();
                long startTime2 = tSeg2.getStartTimeInMillis();
                long endTime1 = tSeg1.getEndTimeInMillis();
                long endTime2 = tSeg2.getEndTimeInMillis();
                if (startTime1 > startTime2 || endTime1 > endTime2)
                    throw new IllegalArgumentException(INVALID_ORDER  + " for " + isoDateTimeStr);
                this.startTime = startTime1;
                this.endTime = endTime2;
            } else {
                String toParse = isoDateTimeStr;
                int eoy = toParse.indexOf("-");
                if (eoy < 0) eoy = toParse.length();
                int n = toParse.length();
                toParse = toParse.replace(" ", "T");
                toParse += dtSuffix.substring(n - eoy);
                Integer calField = null;
                if (n == eoy) calField = Calendar.YEAR;
                else if (n == eoy + 3) calField = Calendar.MONTH;
                else if (n == eoy + 6) calField = Calendar.DAY_OF_MONTH;
                else if (n == eoy + 9) calField = Calendar.HOUR_OF_DAY;
                else if (n == eoy + 12) calField = Calendar.MINUTE;
                else if (n == eoy + 15) calField = Calendar.SECOND;
                GregorianCalendar cal = new GregorianCalendar(UTC_TIMEZONE);
                Date d1 = DF.parse(toParse);
                cal.setTime(d1);
                if (calField == null) {
                    this.startTime = d1.getTime();
                    this.endTime = this.startTime;
                } else {
                    cal.add(calField, 1);
                    Date d2 = cal.getTime();
                    this.startTime = d1.getTime();
                    this.endTime = d2.getTime() - 1;
                }
                // Final validation that yyyy-MM-dd is a valid day (round trip test)
                String ymdInput = toParse.substring(0, toParse.indexOf("T"));
                String ymdOutput = this.toString();
                ymdOutput = ymdOutput.substring(0, ymdOutput.indexOf("T"));
                if (!ymdInput.equals(ymdOutput))
					throw new IllegalArgumentException("Invalid ISO 8601 date and time, " +
                    	isoDateTimeStr);
			}
		} catch (IllegalArgumentException ex) {
			throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid ISO 8601 date and time, " +
                    isoDateTimeStr, ex);
        }
    }

    /**
     * Getter method for this intervals's end time in milliseconds
     *
     * @return long end time
     */
    public long getEndTimeInMillis() {
        return this.endTime;
    }

    /**
     * Setter method for this interval's end time in milliseconds
     *
     * @param endTime long end time
     */
    public void setEndTimeInMillis(long endTime) {
        this.endTime = endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ISO8601DateTimeInterval that = (ISO8601DateTimeInterval) o;
        return this.endTime == that.endTime;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (this.endTime ^ (this.endTime >>> 32));
        return result;
    }

    /**
     * Return int indicating whether this ISO8601DateTimeInterval is equal to (0),
     * starts before or starts equal but ends before (-1), or starts after or starts
     * equal and ends after (+1) the specified ISO8601DateTimePoint argument.
     *
     * @param anotherTime ISO8601DateTimePoint to compare to this ISO8601DateTimeInterval
     * @return -1, 0, or +1 depending if this interval is &lt;, ==, or &gt; {@code anotherTime}
	 * @throws NullPointerException if {@code anotherTime} is <code>null</code>
     */
    public int compareTo(ISO8601DateTimePoint anotherTime) {
        int result = (this.startTime == anotherTime.startTime) ?
                0 : (this.startTime < anotherTime.startTime) ? -1 : +1;
        if (result == 0) {
            long et = (anotherTime instanceof ISO8601DateTimeInterval) ?
                    ((ISO8601DateTimeInterval) anotherTime).endTime : anotherTime.startTime;
            result = (this.endTime == et) ?
                    0 : (this.endTime < et) ? -1 : +1;
        }
        return result;
    }

    /**
     * Format this ISO8601DateTimeInterval object using the ISO 8601 interval syntax,
     * which can be parsed using the String argument constructor method of this
     * class.
     *
     * @return String IOS 8601 Date Time interval syntax for this time segment
     */
    @Override
    public String toString() {
        return DF.format(new Date(this.startTime)) +
                "--" + DF.format(new Date(this.endTime));
    }
}
