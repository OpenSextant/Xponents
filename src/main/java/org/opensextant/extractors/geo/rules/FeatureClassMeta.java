package org.opensextant.extractors.geo.rules;

/**
 * data structure to capture our assumptions about feature types.
 *
 * @author ubaldino
 */
public class FeatureClassMeta {
    public String label;
    /** Absolute gazetteer count */
    public int count;
    /** Relative gazetteer proportion for this feature */
    public double proportion;
    /** Mention Weight */
    public double weight;

    /**
     * Mention Weight X Relative proportion
     * - common features are more likely to be mentioned. - certain features we know should outweigh
     * others (e.g., Intermittent Streams are not often true positives.)
     */
    public double factor;

    /**
     * number of entries; used as a denominator.
     */
    private final static int GAZETTEER_BASE_COUNT = 20000000;

    public FeatureClassMeta(String l, int c, double wt) {
        this.label = l;
        this.count = c;
        this.weight = wt;
        this.proportion = (double) this.count / GAZETTEER_BASE_COUNT;

        // We have a number between 0 and 1 that increases with prevalence of feature
        // type but is adjusted on a-priori preference.
        this.factor = this.weight * this.proportion;
    }

    @Override
    public String toString() {
        return String.format("%s %f%%, factor=%f", this.label, 100 * this.proportion, this.factor);
    }
}