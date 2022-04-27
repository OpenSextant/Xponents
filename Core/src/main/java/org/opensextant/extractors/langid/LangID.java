package org.opensextant.extractors.langid;

public class LangID implements Comparable<LangID> {
    public String langid = null;
    public double probability = 0.0;
    /**
     * On a scale of 0 to 100, where 100 = 100% confident,
     * how would you score this language identity?
     * It is easier to compare scores 55 == 55, vs. 0.550 to 0.550.... in the
     * decimal space.
     * Errors out 3 significant figures is not useful for comparison.
     */
    public int score = 0;
    public boolean primary = false;

    public LangID(String l, double prob, boolean isFirstChoice) {
        langid = l;
        probability = prob;
        score = (int) (100 * prob);
        primary = isFirstChoice;
    }

    /**
     * If THIS langID score is less than argument, return -1
     * If THIS langID score is greater than argument, return 1
     * @param o
     * @return
     */
    @Override
    public int compareTo(LangID o) {
        return Integer.compare(this.score, o.score);
    }

    @Override
    public String toString() {
        return String.format("%s (%03f, %d)", langid, probability, score);
    }
}