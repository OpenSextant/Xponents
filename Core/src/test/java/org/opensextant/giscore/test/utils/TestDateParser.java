package org.opensextant.giscore.test.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;
import org.opensextant.geodesy.SafeDateFormat;
import org.opensextant.giscore.utils.DateParser;
import static org.junit.Assert.*;

/**
 * @author Jason Mathews
 * Date: 6/1/12 2:44 PM
 */
public class TestDateParser {

    private static final TimeZone tz = TimeZone.getTimeZone("UTC");
    private final SafeDateFormat dateFormat = new SafeDateFormat("yyyyMMdd"); // default to UTC time zone

    @Test
    public void testParser() {
        String[] dates = {
                "2012-05-29T17:00:00.000Z", // ISO 8601
                "2012-05-29T17:00:00.000",
                "2012-05-29T17:00:00Z",
                "2012-05-29T17:00:00",
                "2012-05-29T17:00:00-0700",
                "2012-05-29T22:30+04",
                "2012-05-29T1130-0700",
                // YYYY-MM-DDThh:mmTZD (eg 1997-07-16T19:20+01:00)
                // YYYY-MM-DDThh:mm:ssTZD (eg 1997-07-16T19:20:30+01:00)
                // YYYY-MM-DDThh:mm:ss.sTZD (eg 1997-07-16T19:20:30.45+01:00)
                "2012-05-29T15:00-03:30",
                "05/29/2012 17:00:00",
                "05/29/2012 17:00",
                "05/29/2012",
                "29-May-2012",
                "29 May 2012",
                " 29 May 2012 ",
                "29-MAY-2012",
                "May-29-2012",
                "2012-May-29",
                "20120529",
                "201205291730",
                "20120529170000",
                "5/29/2012 1:45:30 PM",
                "Tue, 29 May 2012 08:49:37 GMT",  // RFC 822, updated by RFC 1123
                "2012.05.29 AD at 12:08:56 PDT",
                "Tue, 29 May 2012 12:08:56 -0700",
                "Tue, 29 May 2012 13:19:41",
                "Tuesday, May 29, 2012 17:37:43-PST",
                "Tuesday, May 29, 2012",
                "May 29, 2012",
        };
        Calendar cal = Calendar.getInstance(tz);
        cal.set(2012, Calendar.MAY, 29);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date date = cal.getTime();
        for (String s : dates) {
            Date d = DateParser.parse(s);
            assertEquals(date, d);
        }
    }

    @Test
    public void testShortDate() {
        Date d = DateParser.parse("5 May 2012");
        assertNotNull(d);
        assertEquals("20120505", dateFormat.format(d));
    }

    @Test
    public void testLongMonth() {
        Date d = DateParser.parse("5 January 2012");
        assertNotNull(d);
        assertEquals("20120105", dateFormat.format(d));
    }

    @Test
    public void testBadDates() {
        String[] dates = {
                "01-02-04", // 2-digit years not allowed (2001 vs 2004 ??)
                "01-02-04 12:44PM",
                "Sunday, 06-Nov-94 08:49:37 GMT", // RFC 850, obsoleted by RFC 1036
                "Sun Nov  6 08:49:37 1994",       // ANSI C's asctime() format
                "Wed Sep 10 13:43:13 2003",
                "Tue Sep 09 11:34:27 EDT 2003", // java.util.Date format
                "05/31/000012:30PM", // bogus date format
                "May 2012",
                "May 29",
                "01:45:30",
                "1 2 3",
                "abc",
                "a b c"
        };
        for (String s : dates) {
            //System.out.println("---");
            //System.out.println(s);
            Date d = DateParser.parse(s);
            // System.out.println("XXX d="+d);
            assertNull(d);
        }
    }

}
