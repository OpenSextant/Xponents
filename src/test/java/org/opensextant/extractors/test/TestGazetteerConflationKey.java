package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestGazetteerConflationKey {

    @Test
    public void test() {
        String data = "USGS1496538\tLee County\t36.70545\t-83.12853\tA\tADM2\tUS\tUS\tUSA\tUS51\tUS51.0105\t\t\t\tUSGS\t1496538\t[LATIN]\tname\tF\tlee county\t2\t327078029";
        String[] fields = data.split("\t");

        Double lat = Double.valueOf(fields[2]);
        Double lon = Double.valueOf(fields[3]);
        String name = fields[1];
        String feat = fields[5];
        // name + "-" + cc +"-" + type +"-" + lat +"-" + lon;
        String key1 = name + "-" + fields[6] + "-" + feat + "-" + lat + "-" + lon;
        Long l = Long.valueOf(key1.hashCode());
        l = ((Integer) key1.hashCode()).longValue();
        System.out.println("Key 1  = " + key1);
        System.out.println("Hash1 = " + l);

        Object[] args = { name.replace(" ", "_"), feat, lat, lon };
        String key2 = String.format("%s;%s;%2.4f;%3.4f", name.replace(" ", "_"), feat, lat, lon);
        System.out.println("Key 2 = " + key2);
        System.out.println("Hash2 = " + key2.hashCode());
        String key3 = String.format("%s;%s;%2.4f;%3.4f", args);
        System.out.println("Key 3 = " + key3);
        System.out.println("Hash3 = " + key3.hashCode());

        assertEquals(key2, key3);
    }

}
