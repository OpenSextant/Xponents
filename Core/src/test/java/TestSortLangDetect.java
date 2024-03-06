import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensextant.extractors.langid.LangDetect;
import org.opensextant.extractors.langid.LangID;
import static org.junit.Assert.assertEquals;

public class TestSortLangDetect {

    @Test
    public void test() {
        Map<String, LangID> lids = new HashMap<>();
        LangID l3 = new LangID("aa", 0.80, false);
        LangID l1 = new LangID("ja", 0.88, false);
        LangID l2 = new LangID("zh", 0.10, false);
        LangID l4 = new LangID("zh1", 0.99, false);

        lids.put(l4.langid, l4);
        lids.put(l1.langid, l1);
        lids.put(l2.langid, l2);
        lids.put(l3.langid, l3);
        System.out.println("DESCENDING SORT");
        // So, sort as provided here is best: This returns LangID highest to lowest.
        List<LangID> sorted = LangDetect.sort(lids);
        assertEquals(99, sorted.get(0).score);
        for (LangID l : sorted) {
            System.out.println("L=" + l);
        }
        System.out.println("ASCENDING SORT");
        Collections.reverse(sorted);
        for (LangID l : sorted) {
            System.out.println("L=" + l);
        }
    }
}
