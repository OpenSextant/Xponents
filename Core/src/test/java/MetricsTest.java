import org.junit.Test;
import org.opensextant.extraction.ExtractionMetrics;
import static org.junit.Assert.assertEquals;

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

        assertEquals(3, measure.getCallCount());
        System.out.println(measure.toString());
    }

}
