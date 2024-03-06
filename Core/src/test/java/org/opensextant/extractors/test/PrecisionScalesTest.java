package org.opensextant.extractors.test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.junit.Test;
import org.opensextant.extractors.xcoord.PrecisionScales;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author jgibson
 */
public class PrecisionScalesTest {

    /**
     * Rounds the number to the given digits past the decimal point. The code is
     * pretty simple and is very fast as it doesn't use DecimalFormat or printf. See
     * <a href="http://stackoverflow.com/a/5806991/92186">here</a> for the
     * algorithm.
     *
     * @param f
     * @param digits
     * @deprecated TODO needs to be re-evaluated
     * @author dsmiley
     * @return String formatted number
     */
    @Deprecated
    public static String format2(double f, int digits) {
        long rounderShift = 1;
        // TODO DWS: the comment here is probably obsolete since adding the round()
        // Must shift one past requested, as long conversion consistently rounds down
        // 42.3 == 1 digit of precision
        // 42.2999999 (in Java as a double, geesh.)
        // 42.29999 *10 ==> LONG = 422
        // Answer: 42.2 *Wrong.
        // 42.29999 *100 ==> LONG = 4229
        // Answer: 42.29 still wrong.
        //
        // TODO Math.pow() instead? Same thing but we have a nice error check here.
        for (int i = 0; i <= digits; i++) {
            rounderShift *= 10;
            if (rounderShift < 1) {
                throw new IllegalArgumentException("Digits is too large: " + digits);
            }
        }
        long fShifted = Math.round(f * rounderShift);// truncates trailing decimals by conversion to long
        return "" + ((double) fShifted / rounderShift);
    }

    /**
     *
     * @param f
     * @param digits
     * @return
     */
    public static double bigDecimalRounder(double f, int digits) {
        return new BigDecimal(f).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /**
     * @deprecated see setDDPrecision
     * @param lat String version of decimal latitude, AS found raw
     * @return
     */
    @Deprecated
    public static double DD_precision(String lat) {
        if (lat == null) {
            return -1;
        }

        if (!lat.contains(".")) {
            return PrecisionScales.LAT_DEGREE_PRECISION;
        }

        // Try calculating the sig-figs on the string repr of the number.
        String[] parts = lat.split("\\.", 2);
        if (parts.length == 2) {
            int sig_figs = parts[1].length();
            if (sig_figs < PrecisionScales.DD_precision_list.length) {
                return PrecisionScales.DD_precision_list[sig_figs];
            }
        }
        return PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION;
    }

    /**
     * @deprecated Counting DMS digits is not accurate portrayal of precision
     *             02:02:33N 6 digits 2:2:33N 4 digits
     *
     *             Ah same precision, but different number of digits.
     */
    @Deprecated
    public static float[] DM_precision_list = { PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION, // 0
            PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION, // 1
            PrecisionScales.LAT_DEGREE_PRECISION, // 2
            10000f, // 3
            1300f, // 4
            220f, // 5
            22f, 2.2f, 0.2f, 0.02f, 0.002f, 0.0002f };

    /**
     * @deprecated use setDMSPrecision()
     * @param lat String version of decimal latitude, AS found raw
     * @return
     */
    @Deprecated
    public static float DM_precision(String lat) {
        if (lat == null) {
            return -1;
        }

        /*
         * Given an LAT / Y coordinate, determine the amount of precision therein.
         * 
         * @TODO: redo, given "x xx xx" vs. "xx xx xx" -- resolution is the same. Should
         * only count digits after
         *
         * LON / X coordinate can be up to 179.9999... so would always have one more
         * leading digit than lat.
         *
         */
        int digits = 0;
        for (char ch : lat.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits += 1;
            }
        }

        if (digits > 11) {
            return PrecisionScales.DEFAULT_UNKNOWN_RESOLUTION;
        }

        return DM_precision_list[digits];
    }

    @Test
    public void precisionScalesFormattingComparison() {
        float[] data = { 42.3f, 0.0f, 1.0f, 1.1f, 12.0f, 12.1f, 12.09f, 12.19f, 117.4f, 117.04f, 117.0400f };
        double[] dataD = { 42.3f, // in memory as 42.9999992370 .... Okay, but not 42.30000000 ?
                42.300909099918, // in memory as 42.9999992370 .... Okay, but not 42.30000000 ?
                0.0f, 1.0f, 1.1f, 12.0f, 12.1f, 12.09f, 12.19f, 117.4f, 117.04f, 117.0400f };

        System.out.println("=======High precision test=========");

        double D = 13.8888999977771111d;
        float F = 13.8888999977771111f;

        // 13 decimal places is higher precision than is worth processing:
        System.out.println("NUM=" + D + "\format2(D,13)=" + format2(D, 13));
        // 11 okay:
        System.out.println("NUM=" + D + "\tformat2(D,11)=" + format2(D, 11));
        System.out.println("NUM=" + F + "\tformat(F, 16)=" + PrecisionScales.format(F, 16));
        System.out.println("NUM=" + D + "\tformat(D, 16)=" + PrecisionScales.format(D, 16));
        System.out.println("NUM=" + D + "\tformat(D, 13)=" + PrecisionScales.format(D, 13));
        System.out.println("NUM=" + D + "\tformat(D,  7)=" + PrecisionScales.format(D, 7));
        System.out.println("NUM=" + D + "\tformat(D,  1)=" + PrecisionScales.format(D, 1));

        for (double f : dataD) {
            System.out.println("NUM=" + f + "\tbd(f,2)=" + bigDecimalRounder(f, 2 + 1) + "\tf(f,2)="
                    + PrecisionScales.format(f, 2) + "\tf2(f,2)=" + format2(f, 2));
        }
        System.out.println("=======Speed tests (ms) =========");

        // Speed:
        F = 117.0404f;
        int digits = 4; // 4 decimal places to the right.

        long t1 = new java.util.Date().getTime();
        for (int x = 0; x < 100000; ++x) {
            String.format("%1.4f", F);
        }
        long t2 = new java.util.Date().getTime();
        System.out.println("STRING.format 100K: " + (t2 - t1));

        t1 = t2;
        for (int x = 0; x < 100000; ++x) {
            format2(F, digits);
        }
        t2 = new java.util.Date().getTime();
        System.out.println("Format2 100K: " + (t2 - t1));

        t1 = t2;
        for (int x = 0; x < 100000; ++x) {
            new DecimalFormat("#.####").format(F);
        }
        t2 = new java.util.Date().getTime();
        System.out.println("DECIMAL FORMAT, NO CACHE 100K: " + (t2 - t1));

        t1 = t2;
        // String res = null;
        for (int x = 0; x < 100000; ++x) {
            PrecisionScales.format(F, digits);
        }
        t2 = new java.util.Date().getTime();
        System.out.println("ROUNDER FORMAT 100K: " + (t2 - t1));

        t1 = t2;
        for (int x = 0; x < 100000; ++x) {
            bigDecimalRounder(F, digits);
        }
        t2 = new java.util.Date().getTime();
        System.out.println("BD-ROUNDER FORMAT 100K: " + (t2 - t1));

        System.out.println("STRING FMT:   " + String.format("%1.4f", F));
        System.out.println("DECIMAL FMT:  " + PrecisionScales.format(F, digits));
        System.out.println("ROUNDER:      " + PrecisionScales.format(F, digits));
        System.out.println("BD-ROUNDER:   " + bigDecimalRounder(F, digits));

        boolean trivialPerformanceTest = true;
        assertTrue(trivialPerformanceTest);
    }

}
