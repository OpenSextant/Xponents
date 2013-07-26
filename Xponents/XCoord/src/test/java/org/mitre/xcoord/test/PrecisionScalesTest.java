/**
 * **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2012 The MITRE Corporation. All Rights Reserved.
 */
package org.mitre.xcoord.test;

import org.junit.Test;
import org.mitre.xcoord.PrecisionScales;

import java.text.DecimalFormat;

/**
 *
 * @author jgibson
 */
public class PrecisionScalesTest {
    
    @Test
    public void precisionScalesFormattingComparison() {
        float[] data = {
            42.3f,
            0.0f,
            1.0f,
            1.1f,
            12.0f,
            12.1f,
            12.09f,
            12.19f,
            117.4f,
            117.04f,
            117.0400f
        };
        double[] dataD = {
            42.3f, // in memory as 42.9999992370 ....  Okay, but not 42.30000000 ?
            42.300909099918, // in memory as 42.9999992370 ....  Okay, but not 42.30000000 ?
            0.0f,
            1.0f,
            1.1f,
            12.0f,
            12.1f,
            12.09f,
            12.19f,
            117.4f,
            117.04f,
            117.0400f
        };

        System.out.println("=======High precision test=========");

        double D = 13.8888999977771111d;
        float F = 13.8888999977771111f;

        // 13 decimal places is higher precision than is worth processing:
        System.out.println("NUM=" + D + "\format2(D,13)=" + PrecisionScales.format2(D, 13));
        // 11 okay:
        System.out.println("NUM=" + D + "\tformat2(D,11)=" + PrecisionScales.format2(D, 11));
        System.out.println("NUM=" + F + "\tformat(F, 16)=" + PrecisionScales.format(F, 16));
        System.out.println("NUM=" + D + "\tformat(D, 16)=" + PrecisionScales.format(D, 16));
        System.out.println("NUM=" + D + "\tformat(D, 13)=" + PrecisionScales.format(D, 13));
        System.out.println("NUM=" + D + "\tformat(D,  7)=" + PrecisionScales.format(D, 7));
        System.out.println("NUM=" + D + "\tformat(D,  1)=" + PrecisionScales.format(D, 1));

        for (double f : dataD) {
            System.out.println("NUM=" + f
                    + "\tbd(f,2)=" + PrecisionScales.bigDecimalRounder(f, 2 + 1)
                    + "\tf(f,2)=" + PrecisionScales.format(f, 2)
                    + "\tf2(f,1)=" + PrecisionScales.format2(f, 1));
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
            PrecisionScales.format2(F, digits);
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
        //String res = null;
        for (int x = 0; x < 100000; ++x) {
            PrecisionScales.format(F, digits);
        }
        t2 = new java.util.Date().getTime();
        System.out.println("ROUNDER FORMAT 100K: " + (t2 - t1));

        t1 = t2;
        for (int x = 0; x < 100000; ++x) {
            PrecisionScales.bigDecimalRounder(F, digits);
        }
        t2 = new java.util.Date().getTime();
        System.out.println("BD-ROUNDER FORMAT 100K: " + (t2 - t1));

        System.out.println("STRING FMT:   " + String.format("%1.4f", F));
        System.out.println("DECIMAL FMT:  " + PrecisionScales.format(F, digits));
        System.out.println("ROUNDER:      " + PrecisionScales.format(F, digits));
        System.out.println("BD-ROUNDER:   " + PrecisionScales.bigDecimalRounder(F, digits));
    }

}