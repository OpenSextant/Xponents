/***************************************************************************
 * $Id$
 *
 * (C) Copyright MITRE Corporation 2006-2008
 *
 * The program is provided "as is" without any warranty express or implied,
 * including the warranty of non-infringement and the implied warranties of
 * merchantability and fitness for a particular purpose.  The Copyright
 * owner will not be liable for any damages suffered by you as a result of
 * using the Program.  In no event will the Copyright owner be liable for
 * any special, indirect or consequential damages or lost profits even if
 * the Copyright owner has been advised of the possibility of their
 * occurrence.
 *
 ***************************************************************************/
package org.opensextant.geodesy.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;

import junit.framework.JUnit4TestAdapter;
import org.junit.Assert;
import org.junit.Test;
import org.opensextant.geodesy.Angle;

/**
 * 
 * @author Paul Silvey
 */
public class TestAngle {

    /**
     * This is the unit test case for functionality provided by the Angle class
     */
	@Test
    public void testAngle() {
        Random r = new Random();
        int fractDig = 2;
        for (int i = 0; i < 1000; i++) {
            // Test radians constructor and conversion to degrees
            double rad = (r.nextDouble() * (2.0 * Math.PI)) - Math.PI;
            Angle r1 = new Angle(rad, Angle.RADIANS);
            Angle r2 = new Angle(r1.inDegrees(), Angle.DEGREES);
            Assert.assertEquals(r1.toString(fractDig), r2.toString(fractDig));

            // Test degrees, minutes, seconds constructor and conversion to radians
            int degrees = r.nextInt(360) - 180;
            int minutes = r.nextInt(60);
            double seconds = r.nextDouble() * 60.0;
            Angle d1 = new Angle(degrees, minutes, seconds);
            Angle d2 = new Angle(d1.inRadians());
			Assert.assertEquals(d1.toString(fractDig), d2.toString(fractDig));

            // Test String constructor
            Angle s1 = new Angle(r1.toString(5));
            Angle s2 = new Angle(s1.inDegrees(), Angle.DEGREES);
			Assert.assertEquals(s1.toString(fractDig), s2.toString(fractDig));
        }

        Angle z000 = new Angle();   // default constructor makes 0 deg angle
        // Some common positive angles (testing add method)
        Angle p045 = new Angle(45.0, Angle.DEGREES);
        Angle p090 = p045.add(p045);
        Angle p135 = p090.add(p045);
        Angle p180 = p090.add(p090);
        // Some common negative angles (testing difference method)
        Angle m045 = p045.difference(z000);
        Angle m090 = p090.difference(z000);
        Angle m135 = p090.difference(m045);
        Angle m180 = p090.difference(m090);

        Assert.assertTrue(p135.add(p090).equals(m135));
        Assert.assertEquals(m180.toString(fractDig), p180.toString(fractDig));
        Assert.assertEquals(m180, p180);

//        System.out.println("\rString constructor parse tests:");
//        String[] parseTests = new String[]{
//                "77.5 degrees", "77 deg:15 min", "1.4r", "-23 deg, 15 min", "3.141592654 radians",
//                "-180deg, 15:23.2", "1:2:5", "4 23 15", "60 61", "25:25.3:56"
//        };
//        for (String s : parseTests) {
//            System.out.print("Angle string \"" + s + "\" interpreted as ");
//            try {
//                System.out.println(new Angle(s).toString(fractDig));
//            } catch (Exception ex) {
//                System.out.println(ex);
//            }
//        }
    }

	@Test
	public void testEquals() {
		Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            // Test radians constructor and conversion to degrees with equals() and hashCode() test
            double rad = (r.nextDouble() * (2.0 * Math.PI)) - Math.PI;
            Angle r1 = new Angle(rad, Angle.RADIANS);
			Angle r2 = new Angle(r1.inDegrees(), Angle.DEGREES);
			Assert.assertEquals(r1, r2);
			Assert.assertEquals(r1.hashCode(), r2.hashCode());
			// now test from degrees back to radians
			Angle r3 = new Angle(r2.inRadians(), Angle.RADIANS);
			Assert.assertEquals(r2, r3);
			Assert.assertEquals(r2.hashCode(), r3.hashCode());
		}
		// small EPSILON delta but equal
		Angle r1 = new Angle(1);
		Angle r2 = new Angle(r1.inRadians() + 1e-8);
		Assert.assertEquals(r1, r2);
		Assert.assertEquals(r1.hashCode(), r2.hashCode());
		// "large" enough EPSILON delta so not equal
		Angle r3 = new Angle(r1.inRadians() + 1e-7);
		Assert.assertFalse(r1.equals(r3));
	}

	@Test
	public void testCompare() {
		Angle a = new Angle(0);
		Angle b = new Angle(0, Angle.DEGREES);
		Assert.assertEquals(0, a.compareTo(b));
		double delta = 0.1;
		for(int i=0; i < 8; i++) {
			Angle c = new Angle(a.inRadians() + delta);
			Assert.assertEquals(-1, a.compareTo(c));
			delta /= 10.0;
		}
		Assert.assertEquals(0.0, a.getValue(Angle.RADIANS), 1e-6);
		Assert.assertEquals(0.0, a.getValue(Angle.DEGREES), 1e-6);
	}

	@Test
    public void testNullAngleCompare() {
		Angle r1 = new Angle(Math.PI, Angle.RADIANS);
		Angle r2 = null;
		Assert.assertFalse(r1.equals(r2));
	}

	@Test
	public void testDegreeSymbol() throws IOException {
		InputStreamReader reader = null;
		final InputStream stream = getStream("DEGREE_SYMBOL.txt");
		Assert.assertNotNull(stream);
		try {
			reader = new InputStreamReader(stream, "ISO-8859-1");
			final StringBuilder buff = new StringBuilder();
			for (int c = reader.read(); c != -1; c = reader.read()) {
				buff.append((char) c);
			}
			final String file_degree = buff.toString().trim();
			Assert.assertEquals(Angle.DEGSYM, file_degree);
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) { }
				try {
					stream.close();
				} catch (IOException e) { }
		}
	}

    private InputStream getStream(String filename) throws IOException {
        File file = new File("src/test/resources/org/opensextant/geodesy/test/" + filename);
        if (file.exists()) return new java.io.FileInputStream(file);
        return getClass().getResourceAsStream(filename);
    }

	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(TestAngle.class);
	}

	/**
     * Main method for running class tests.
     *
     * @param args standard command line arguments - ignored.
     */
    public static void main(String[] args) {
		new junit.textui.TestRunner().doRun(suite());
		System.exit(0);
    }
}
