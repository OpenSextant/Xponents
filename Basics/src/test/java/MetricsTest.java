import static org.junit.Assert.*;

import org.junit.Test;
import org.opensextant.extraction.ExtractionMetrics;

public class MetricsTest {

    @Test
    public void test() {
        ExtractionMetrics measure = new ExtractionMetrics("test");
        measure.addBytes(55);
        measure.addTime(500);

        measure.addBytes(5531);
        measure.addTime(100);

        measure.addBytes(-90);
        measure.addTime(555);
        measure.addTime(-555);

        assertTrue(measure.getCallCount() == 3);
        System.out.println(measure.toString());
    }

}
