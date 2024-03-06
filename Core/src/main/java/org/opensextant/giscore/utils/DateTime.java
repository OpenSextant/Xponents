package org.opensextant.giscore.utils;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.giscore.input.kml.IKml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class {@code DateTime} represents a specific instant
 * in time, with millisecond precision. DateTime has a date type
 * for formatting matching associated XML Schema date types (e.g.,
 * gYear, gYearMonth, date, dateTime) which is used to format
 * string representation as returned by the toString() method.
 * <pre>
 * Examples:
 *
 * <i>value                     type         format</i>
 * 1997                      gYear        YYYY
 * 1997-07                   gYearMonth   YYYY-MM
 * 1997-07-16                date         YYYY-MM-DD
 * 1997-07-16T07:30:15Z      dateTime     YYYY-MM-DDThh:mm:ssZ
 * 1997-07-16T07:30:15.30Z   dateTime     YYYY-MM-DDThh:mm:ss.SSSZ
 * </pre>
 * <P>
 * Class is thread-safe. <p>
 *
 * Extends {@link Number} such that it can safely be used in
 * java.text.SimpleDateFormat.format().
 *
 * @author Jason Mathews, MITRE Corp.
 * created 4/17/14.
 */
public class DateTime extends Number implements java.io.Serializable, Cloneable, Comparable<DateTime> {

	private static final Logger log = LoggerFactory.getLogger(DateTime.class);

	private static final long serialVersionUID = -1L;

	private static final SafeDateFormat[] ms_dateFormats = new SafeDateFormat[6];

	private static final SafeDateFormat ISO_DATE_FORMATTER = new SafeDateFormat(IKml.ISO_DATE_FMT);

	private static DatatypeFactory fact;

	private final long time;

	@NotNull
	private DateTimeType type;

	public enum DateTimeType {
		gYear,		// yyyy
		gYearMonth,	// yyyy-MM
		date,  		// yyyy-MM-DD
		dateTime	// yyyy-MM-DD'T'HH:mm'Z'
		;

		/**
		 * Returns the enum constant of the specified enum type with the
		 * specified ordinal value.  The value must match exactly the ordinal value used
		 * to declare an enum constant in this type.
		 *
		 * @param typeCode the ordinal value of the constant to return
		 * @return the enum constant of the specified enum type with the
		 *      specified value
		 * @throws IllegalArgumentException if the specified enum type has
		 *         no constant with the specified value
		 */
		public static DateTimeType valueOf(int typeCode) {
			switch(typeCode) {
				case 0: return gYear;
				case 1: return gYearMonth;
				case 2: return date;
				case 3: return dateTime;
				default:
					throw new IllegalArgumentException();
			}
		}
	}

	static {
		// Reference states: dateTime (YYYY-MM-DDThh:mm:ssZ) in KML states that T is the separator
		// between the calendar and the hourly notation of time, and Z indicates UTC. (Seconds are required.)
		// however, we will also check time w/o seconds since it is accepted by Google Earth.
		// Thus allowing the form: YYYY-MM-DDThh:mm[:ss][Z]
		// http://code.google.com/apis/kml/documentation/kmlreference.html#timestamp

		ms_dateFormats[0] = ISO_DATE_FORMATTER; // default: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
		ms_dateFormats[1] = new SafeDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ms_dateFormats[2] = new SafeDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // dateTime format w/o seconds
		ms_dateFormats[3] = new SafeDateFormat("yyyy-MM-dd"); // date (YYYY-MM-DD)
		ms_dateFormats[4] = new SafeDateFormat("yyyy-MM");    // gYearMonth (YYYY-MM)
		ms_dateFormats[5] = new SafeDateFormat("yyyy");       // gYear (YYYY)
	}

	/**
	 * Constructs a {@code DateTime} object and initializes it so that
	 * it represents the time at which it was allocated, measured to the
	 * nearest millisecond.
	 */
	public DateTime() {
		this(System.currentTimeMillis());
	}

	/**
	 * Constructs a {@code DateTime} object and initializes it to
	 * represent the specified number of milliseconds since the
	 * standard base time known as "the epoch", namely January 1,
	 * 1970, 00:00:00 GMT.
	 *
	 * @param   date   the milliseconds since January 1, 1970, 00:00:00 GMT.
	 */
	public DateTime(long date) {
		this(date, DateTimeType.dateTime);
	}

	/**
	 * Constructs a {@code DateTime} object and initializes it to
	 * represent the specified number of milliseconds since the
	 * standard base time known as "the epoch", namely January 1,
	 * 1970, 00:00:00 GMT.
	 *
	 * @param   date   the milliseconds since January 1, 1970, 00:00:00 GMT.
	 * @param   type   DateTimeType
	 *
	 * @throws IllegalArgumentException if type is null
	 */
	public DateTime(long date, DateTimeType type) {
		if (type == null) throw new IllegalArgumentException();
		this.time = date;
		this.type = type;
	}

	/**
	 * Constructs a {@code DateTime} object and initializes it so that
	 * it represents the date and time indicated by the string
	 * {@code datestr}.
	 *
	 * @param datestr   a string to be parsed as a DsateTime.
	 * @throws ParseException if specified string is null, empty or cannot be parsed.
	 */
	public DateTime(String datestr) throws ParseException {
		if (StringUtils.isBlank(datestr)) throw new ParseException("Empty or null date string", 0);
		this.time = parseDate(datestr);
	}

    /**
     * Returns the number of milliseconds since January 1, 1970, 00:00:00 GMT
     * represented by this {@code Date} object.
     */
	public long getTime() {
		return time;
	}

	@NotNull
	public DateTimeType getType() {
		return type;
	}

	/**
	 * Convert DateTime to Date object.
	 * @return Date
	 */
	public Date toDate() {
		return new Date(time);
	}

    @Override
    public int intValue() {
        return (int)time;
    }

    @Override
    public long longValue() {
        return time;
    }

    @Override
    public float floatValue() {
        return (float)time;
    }

    @Override
    public double doubleValue() {
        return (double)time;
    }

	/**
	 *
	 * @param datestr
	 * @return
	 * @throws ParseException
	 */
	private long parseDate(String datestr) throws ParseException {
		String message;
		Exception ex;
		try {
			if (fact == null) fact = DatatypeFactory.newInstance();
			XMLGregorianCalendar o = fact.newXMLGregorianCalendar(datestr);
			GregorianCalendar cal = o.toGregorianCalendar();
			String type = o.getXMLSchemaType().getLocalPart();
			boolean useUTC = true;
			if ("dateTime".equals(type)) {
				this.type = DateTimeType.dateTime;
				// dateTime (YYYY-MM-DDThh:mm:ssZ)
				// dateTime (YYYY-MM-DDThh:mm:sszzzzzz)
				// Second form gives the local time and then the +/- conversion to UTC.
				// Set timezone to UTC if other than dateTime formats with explicit timezones
				int ind = datestr.lastIndexOf('T') + 1; // index should never be -1 if type is dateTime
				if (ind > 0 && (datestr.indexOf('+', ind) > 0 || datestr.indexOf('-', ind) > 0)) {
					// e.g. 2009-03-14T18:10:46+03:00 or 2009-03-14T18:10:46-05:00
					useUTC = false;
				}
				// if timeZone is missing (e.g. 2009-03-14T21:10:50) then 'Z' is assumed and UTC is used
			} else {
				//System.out.println("DateTime2::type=" + type); //debug
				this.type = DateTimeType.valueOf(type);
			}

			if (useUTC) cal.setTimeZone(TimeZone.getTimeZone("UTC"));
			//else datestr += "*";
			//System.out.format("%-10s\t%s%n", type, datestr);
			/*
                 possible dateTime types: { dateTime, date, gYearMonth, gYear }
                 if other than dateTime then must adjust the time to 0

                 1997                      gYear        (YYYY)						1997-01-01T00:00:00.000Z
                 1997-07                   gYearMonth   (YYYY-MM)					1997-07-01T00:00:00.000Z
                 1997-07-16                date         (YYYY-MM-DD)				1997-07-16T00:00:00.000Z
                 1997-07-16T07:30:15Z      dateTime (YYYY-MM-DDThh:mm:ssZ)			1997-07-16T07:30:15.000Z
                 1997-07-16T07:30:15.30Z   dateTime     							1997-07-16T07:30:15.300Z
                 1997-07-16T10:30:15+03:00 dateTime (YYYY-MM-DDThh:mm:sszzzzzz)		1997-07-16T07:30:15.000Z
                */
			if (!"dateTime".equals(type)) {
				cal.set(Calendar.HOUR_OF_DAY, 0);
			}
			return cal.getTimeInMillis();
		} catch (IllegalArgumentException iae) {
			message = "Failed to parse date with DatatypeFactory: " + datestr;
			ex = iae;
		} catch (DatatypeConfigurationException ce) {
			// NOTE: maybe JODA time would be be better generic time parser but would be a new dependency
			// if unable to create factory then try brute force
			message = "Failed to get DatatypeFactory";
			ex = ce;
		}

		// try individual date formats
		int ind = datestr.indexOf('T');
		int i;
		/*
		   date formats:
		   0: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
		   1: yyyy-MM-dd'T'HH:mm:ss'Z'
		   2: yyyy-MM-dd'T'HH:mm'Z' (dateTime format w/o seconds)
		   3: yyyy-MM-dd (date)
		   4: yyyy-MM 	 (gYearMonth)
		   5: yyyy		 (gYear)
	   */
		if (ind == -1) {
			i = 3; // if no 'T' in date then skip to date (YYYY-MM-DD) format @ index=3
		} else {
			i = 0;
			// Sloppy KML might drop the 'Z' suffix or for dates. Google Earth defaults to UTC.
			// Likewise KML might drop the seconds field in timestamp.
			// Note these forms are not valid with respect to KML Schema and kml:dateTimeType
			// definition but Google Earth has lax parsing for such cases so we attempt
			// to parse as such.
			// This will NOT handle alternate time zones format with missing second field: e.g. 2009-03-14T16:10-05:00
			// note: this does not correctly handle dateTime (YYYY-MM-DDThh:mm:sszzzzzz) format
			if (!datestr.endsWith("Z") && datestr.indexOf(':', ind + 1) > 0
					&& datestr.indexOf('-', ind + 1) == -1
					&& datestr.indexOf('+', ind + 1) == -1) {
				log.debug("Append Z suffix to date");
				datestr += 'Z'; // append 'Z' to date
			}
		}
		final int fmtCount = ms_dateFormats.length;
		while (i < fmtCount) {
			SafeDateFormat fmt = ms_dateFormats[i];
			try {
				Date date = fmt.parse(datestr);
				if (i == 3) type = DateTimeType.date;
				else if (i == 4) type = DateTimeType.gYearMonth;
				else if (i == 5) type = DateTimeType.gYear;
				else type = DateTimeType.dateTime; // default
				// System.out.printf("DateTime3::%s type=%s %d%n", datestr, type, i); //debug
				log.warn(message);
				log.debug("Parsed using dateFormat: {}", fmt.toPattern());
				return date.getTime();
			} catch (ParseException pe) {
				// ignore
			}
			i++;
		}

		// give up
		final ParseException e2 = new ParseException(message, 0);
		e2.initCause(ex);
		throw e2;
	}

	/**
	 * Compares two dates for equality.
	 * The result is {@code true} if and only if the argument is
	 * not {@code null} and is a {@code DateTime} object that
	 * represents the same point in time, to the millisecond, as this object.
	 *
	 * @param   other   the object to compare with.
	 * @return  {@code true} if the objects are the same;
	 *          {@code false} otherwise.
	 */
	public boolean equals(Object other) {
		return other instanceof DateTime && time == ((DateTime)other).time;
	}

	/**
	 * Compares two DateTime for ordering.
	 *
	 * @param   anotherDate   the {@code Date} to be compared.
	 * @return  the value {@code 0} if the argument DateTime is equal to
	 *          this DateTime; a value less than {@code 0} if this DateTime
	 *          is before the DateTime argument; and a value greater than
	 *      {@code 0} if this DateTime is after the DateTime argument.
	 * @since   1.2
	 * @exception NullPointerException if {@code anotherDate} is null.
	 */
	public int compareTo(DateTime anotherDate) {
		final long anotherTime = anotherDate.time;
		return (time < anotherTime ? -1 : (time == anotherTime ? 0 : 1));
	}

	/**
	 * Returns a hash code value for this object.
	 *
	 * @return  a hash code value for this object.
	 */
	public int hashCode() {
		return (int)(time ^ (time >>> 32));
	}

	/**
	 * Return a copy of this {@code DateTime} object.
	 */
	public Object clone() {
		return new DateTime(time, type);
	}

    /**
     * Creates a string representation of this {@code DateTime} object
     * based on the type value. <p>
     * Here is list of types and corresponding formats:
     * <pre>
     *   date -> yyyy-MM-dd
     *   gYearMonth -> yyyy-MM
     *   gYear -> yyyy
     *   dateTime -> yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (default)
     *  </pre>
     *  If want custom format use java.text.SimpleDateFormat.format().
     *
     * @param type DateTimeType used to select format for this DateTime object
     * @return a string representation of this date
     */
	public String toString(DateTimeType type) {
		SafeDateFormat dateFormatter;
		/*
		date formats:
		0: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
		3: yyyy-MM-dd (date)
		4: yyyy-MM 	 (gYearMonth)
		5: yyyy		 (gYear)
		*/
		switch(type) {
			case date:
				dateFormatter = ms_dateFormats[3]; // yyyy-MM-DD
				break;
			case gYearMonth:
				dateFormatter = ms_dateFormats[4]; // yyyy-MM
				break;
			case gYear:
				dateFormatter = ms_dateFormats[5]; // yyyy
				break;
			default:
				dateFormatter = ISO_DATE_FORMATTER;
		}
		return dateFormatter.format(time);
	}

	/**
	 * Creates a string representation of this {@code DateTime} object of
	 * the form:
	 * <blockquote><pre>
	 * YYYY-MM-DDThh:mm:ss.SSSZ</pre></blockquote>
	 * where:<ul>
	 * <li><i>yyyy</i> is the year, as four decimal digits.</li>
	 * <li><i>MM</i> is the month, as two decimal digits.</li>
	 * <li><i>DD</i> is the day of the month ({@code 01} through <code>31</code>),
	 *     as two decimal digits.
	 *     </li>
	 * <li><i>hh</i> is the hour of the day ({@code 00} through <code>23</code>),
	 *     as two decimal digits.
	 *     </li>
	 * <li><i>mm</i> is the minute within the hour ({@code 00} through
	 *     {@code 59}), as two decimal digits.
	 *     </li>
	 * <li><i>ss</i> is the second within the minute ({@code 00} through
	 *     {@code 60}), as two decimal digits.
	 *     </li>
	 * </ul>
	 * @return  a string representation of this date, using the XML Schema
	 *          dateTime convention with UTC time zone.
	 */
	public String toUTCString() {
		return ISO_DATE_FORMATTER.format(time);
	}

	/**
	 * Formats the given {@code DateTime} into a date/time string using format
	 * defined by the date time type (e.g. YYYY for gYear; YYYY-MM for gYearMonth, etc.).
	 *
	 * @return  a string representation of this DateTime.
	 */
	public String toString() {
		return toString(type);
	}

}
