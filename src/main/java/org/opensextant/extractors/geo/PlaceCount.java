package org.opensextant.extractors.geo;

import org.opensextant.data.Place;

import java.util.HashSet;
import java.util.Set;

/**
 * Place metrics. Everything other than coords, countries, and filtered-out
 *
 * @author ubaldino
 */
public class PlaceCount {
    public int count = 1;
    private double ratio = 0;
    public Place place = null;
    public int total = 1;
    public String label;
    public Set<String> names = new HashSet<>();

    public PlaceCount(String l){
        label = l;
    }
    public void add(String nm){
        names.add(nm);
    }
    public int getCount(){
        return names.size();
    }

    public String getCountryCode(){
        if (place != null){
            return place.getCountryCode();
        }
        if (label != null){
            // We'll blindly wait until this is used.
            return label.split("\\.")[0];
        }
        return null;
    }

    /**
     * given a total number of ALL place mentions, you can derive a ratio, e.g.,
     * text ABC is 45% about province1, 34% about province2, etc.
     */
    public double getRatio() {
        ratio = (double) count / total;
        return ratio;
    }

    @Override
    public String toString() {
        if (place!=null) {
            return String.format("%s (%d or %03.1f pct)", place.getName(), count, 100 * getRatio());
        }
        if (label!=null){
            return String.format("'%s' (%d)", label, getCount());
        }
        return "empty";
    }
}
