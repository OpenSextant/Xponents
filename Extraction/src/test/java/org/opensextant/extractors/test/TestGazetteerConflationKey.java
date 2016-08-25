package org.opensextant.extractors.test;
import static org.junit.Assert.*;

import org.junit.Test;

public class TestGazetteerConflationKey {

    @Test
    public void test() {
        String data = "USGS1496538\tLee County\t36.70545\t-83.12853\tA\tADM2\tUS\tUS\tUSA\tUS51\tUS51.0105\t\t\t\tUSGS\t1496538\t[LATIN]\tname\tF\tlee county\t2\t327078029";
        String[] fields = data.split("\t");

        Double lat = new Double(fields[2]);
        Double lon = new Double(fields[3]);
        String name = fields[1];
        String feat = fields[5];
        // name + "-" + cc +"-" + type +"-" + lat +"-" + lon;
        String key = name + "-" + fields[6] + "-" + feat + "-" + lat + "-" + lon;
        Long l = new Long(key.hashCode());
        l = ((Integer) key.hashCode()).longValue();
        System.out.println("Key  = " + key);
        System.out.println("Hash = " + l);
        
        Object[] args = {name.replace(" ","_"), feat, lat, lon};
        key = String.format("%s;%s;%2.4f;%3.4f", name.replace(" ","_"), feat, lat.doubleValue(), lon.doubleValue());
        System.out.println("Key  = " + key);
        key = String.format("%s;%s;%2.4f;%3.4f", args);
        System.out.println("Key  = " + key);
        System.out.println("Hash  = " + key.hashCode());
    }

}
