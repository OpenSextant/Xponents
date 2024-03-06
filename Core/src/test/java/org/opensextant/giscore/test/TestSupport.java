package org.opensextant.giscore.test;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;


public class TestSupport {

    // TODO: migrate test output to ./target/
    public static String OUTPUT = "target/giscore-tests";

    final static UniformRandomProvider RandomUtils = RandomSource.XO_RO_SHI_RO_128_PP.create();

    public static boolean randomBool(){
        return RandomUtils.nextBoolean();
    }
    public static double randomLat() {
        return RandomUtils.nextDouble(0,89);
    }
    public static double randomLon() {
        return RandomUtils.nextDouble(0,179);
    }
    public static double randomLatSmall() {
        return RandomUtils.nextDouble(0,1.0);
    }
    public static double randomLonSmall() {
        return RandomUtils.nextDouble(0,1.0);
    }
    public static double nextDouble(){
        return RandomUtils.nextDouble(0,1.0);
    }
    public static int nextInt(int r){
        return RandomUtils.nextInt(r);
    }
    public static float nextFloat(int a, int b){
        return RandomUtils.nextFloat(a, b);
    }
    public static float nextFloat(){
        return RandomUtils.nextFloat(-1, 1);
    }
}
