package org.opensextant.geodesy.test;

import java.text.ParseException;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;
import org.opensextant.geodesy.SafeDateFormat;

public class TestSimpleDate {

	private volatile boolean running = true;
	private final SafeDateFormat df = new SafeDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
	private Thread current;
	private final Random rand = new Random();

	@Test
	public void testFormat() {
		Assert.assertEquals(TimeZone.getTimeZone("UTC"), df.getTimeZone());
		Assert.assertEquals("1970-01-01T00:00:00.000", df.format(0));
	}

	@Test
	public void testTimeZome() {
		TimeZone tz = TimeZone.getDefault();
		SafeDateFormat b = new SafeDateFormat("HH:mm:ss", tz);
		Assert.assertEquals(tz, b.getTimeZone());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullFormat() {
		new SafeDateFormat(null);
	}


	@Test(expected = IllegalArgumentException.class)
	public void testEmptyFormat() {
		new SafeDateFormat("");
	}

	@Test
	public void testThreads() {
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		current = Thread.currentThread();
		// System.out.format("now = %x%n", System.currentTimeMillis());
		for (int i=0; i < 20; i++) {
			Thread t = new Thread(new Runner(i));
			t.setDaemon(true);
			t.start();
		}

		// run thread tests for 5 seconds
		// if SimpleDateFormat used instead of SafeDateFormat then fails after few iterations
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// one of the test threads probably failed the test
		}
		// System.out.println("running = " + running);
		Assert.assertTrue("format returned non-consistent result", running);
		running = false; // kill running threads
	}

	private class Runner implements Runnable {

		private int id;

		public Runner(int id) {
			this.id = id;
		}

		public void run() {
			long ts = rand.nextLong() & 0x14FFFFFFFFFL;
			Date date = new Date(ts);
			String output;
			synchronized (df) {
				output = df.format(date);
			}
			int count = 0;
			while (running) {
				count++;
				String result = df.format(date);
				if (!output.equals(result)) {
					System.out.format("T%d:%d Failed actual=%s expected=%s%n", id, count, result, output);
					running = false;
					current.interrupt();
					return; // failed -> stop
				}
				try {
					Date parsedDate = df.parse(output);
					if (!date.equals(parsedDate)) {
						System.out.format("T%d:%d Failed to parse date: actual=%s expected=%s%n", id, count, date, parsedDate );
						running = false;
						current.interrupt();
					}
				} catch (NumberFormatException e) {
					System.out.format("T%d:%d Failed to parse date: NumberFormatException: %s %n", id, count, e.getMessage() );
					running = false;
					current.interrupt();
				} catch (ParseException e) {
					System.out.format("T%d:%d Failed to parse date: %s%n", id, count, e.getMessage() );
					running = false;
					current.interrupt();
				}
			}
		}
	}

}
