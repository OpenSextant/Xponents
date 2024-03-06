/************************************************************************************
 * TestISO8601DateTime.java 12/22/10 11:08 AM psilvey
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
package org.opensextant.geodesy.test;

import java.util.Random;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.opensextant.geodesy.ISO8601DateTimeInterval;
import org.opensextant.geodesy.ISO8601DateTimePoint;

public class TestISO8601DateTime extends TestCase {
    private final static Random rand = new Random();

    private final static String[] partials = {
                "1970",
                "1970-01",
                "1970-01-01",
                "1970-01-01T00",
                "1970-01-01T00:00",
                "1970-01-01T00:00:00",
                "1970-01-01T00:00:00.000",
                "1970-01-01T00:00:00.000Z"
        };

    @Test
    public static void testTimePoint() throws Exception {
        ISO8601DateTimePoint t1;
        ISO8601DateTimePoint t2;

        for (int trials = 0; trials < 1000; trials++) {
            // Create a random point in time (within about 8000 years of the Epoch)
            long rTime = (long) (100.0 * 365.25 * 24.0 *
                    60.0 * 60.0 * 1000.0 * rand.nextDouble());
            t1 = new ISO8601DateTimePoint(rTime);
            String isoDateTime = t1.toString();
            t2 = new ISO8601DateTimePoint(isoDateTime);
            assertEquals(t1, t2);
            assertEquals(isoDateTime, t2.toString());
			assertEquals(t1.hashCode(), t2.hashCode());
        }

        ISO8601DateTimePoint lastTime = null;
        for (String isoStr : partials) {
            t1 = new ISO8601DateTimePoint(isoStr);
            // all times should be equivalent
            if (lastTime != null) assertEquals(lastTime, t1);
            lastTime = t1;
            System.out.println(isoStr + " means " + t1.toString());
        }
    }

	@Test
    public static void testDefaultTimeInterval() {
		final long time = System.currentTimeMillis();
		ISO8601DateTimeInterval t1 = new ISO8601DateTimeInterval();
		final long startTime = t1.getStartTimeInMillis();
		assertTrue(startTime >= time);
		assertEquals(startTime, t1.getEndTimeInMillis());
	}

	@Test
	public static void testCompareTo() throws Exception {
        ISO8601DateTimePoint t1 = new ISO8601DateTimePoint();
        ISO8601DateTimePoint t2 = new ISO8601DateTimePoint(t1.getStartTimeInMillis());
		assertEquals(0, t1.compareTo(t2));
		ISO8601DateTimePoint t3 = new ISO8601DateTimePoint(t1.getStartTimeInMillis() + 1000);
		assertEquals(-1, t1.compareTo(t3));
		ISO8601DateTimePoint t4 = new ISO8601DateTimePoint(t1.getStartTimeInMillis() - 1000);
		assertEquals(1, t1.compareTo(t4));
	}

    @Test
    public static void testTimeInterval() throws Exception {
        ISO8601DateTimeInterval t1;
        ISO8601DateTimeInterval t2;

        for (int trials = 0; trials < 1000; trials++) {
            // Create random points in time (within about 8000 years of the Epoch)
            long rt1 = (long) (100.0 * 365.25 * 24.0 *
                    60.0 * 60.0 * 1000.0 * rand.nextDouble());
            long rt2 = (long) (100.0 * 365.25 * 24.0 *
                    60.0 * 60.0 * 1000.0 * rand.nextDouble());
            // Make sure they are in the correct temporal order
            if (rt1 > rt2) {
                long temp = rt1;
                rt1 = rt2;
                rt2 = temp;
            }

            t1 = new ISO8601DateTimeInterval(rt1, rt2);
            String isoDateTime = t1.toString();
            t2 = new ISO8601DateTimeInterval(isoDateTime);
            assertEquals(t1, t2);
            assertEquals(isoDateTime, t2.toString());
			assertEquals(t1.hashCode(), t2.hashCode());
        }

		t1 = new ISO8601DateTimeInterval();
		ISO8601DateTimePoint pt = new ISO8601DateTimePoint();
		assertFalse(t1.equals(pt)); // t1.class != pt.class

        for (String isoStr : partials) {
            t1 = new ISO8601DateTimeInterval(isoStr);
            System.out.println(isoStr + " means " + t1.toString());
        }
    }

	@Test
	public void testInvalidCreation() {
		try {
			new ISO8601DateTimeInterval(2000, 1000);
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected invalid time order
		}

		try {
			new ISO8601DateTimeInterval("2011-06-21T18:06:37.039Z--2011-01-01T18:06:37.039Z");
			fail("Expected to throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected invalid time order
		}
	}

	@Test
	public static void testIntervalCompareTo() {
        ISO8601DateTimeInterval t1 = new ISO8601DateTimeInterval();
		ISO8601DateTimePoint tp = new ISO8601DateTimePoint(t1.getStartTimeInMillis());
		assertEquals(0, t1.compareTo(tp));

		ISO8601DateTimeInterval t2 = new ISO8601DateTimeInterval(t1.getStartTimeInMillis(), t1.getEndTimeInMillis());

		assertEquals(0, t1.compareTo(t2));

		t2.setEndTimeInMillis(t2.getEndTimeInMillis() + 1000);
		assertEquals(-1, t1.compareTo(t2));

		t2.setStartTimeInMillis(t2.getStartTimeInMillis() - 5000);
		t2.setEndTimeInMillis(t2.getEndTimeInMillis() - 2000);
		assertEquals(1, t1.compareTo(t2));
	}

	@Test
    public static void testNullCompare() throws Exception {
		ISO8601DateTimePoint t1 = new ISO8601DateTimePoint();
		ISO8601DateTimePoint t2 = null;
		assertFalse(t1.equals(t2));
	}

	@Test
    public static void testNullTimeInterval() {
		ISO8601DateTimeInterval t1 = new ISO8601DateTimeInterval();
        ISO8601DateTimeInterval t2 = null;
		assertFalse(t1.equals(t2));
	}

    /**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
        JUnitCore.runClasses(TestISO8601DateTime.class);
    }
}
