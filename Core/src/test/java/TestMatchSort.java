import org.junit.Test;
import static org.junit.Assert.*;
import org.opensextant.extraction.TextMatch;

public class TestMatchSort {
    @Test
    public void testSorting() {

        TextMatch a = new TextMatch(14, 21);
        TextMatch b = new TextMatch(3, 7);
        assertTrue(b.isBefore(a));
        assertTrue(a.isAfter(b));
        // A occurs after B
        assertTrue(a.compareTo(b)<0);

        // A and B start together. A occurs after B
        a = new TextMatch(3, 21);
        b = new TextMatch(3, 7);
        assertFalse(b.isBefore(a));
        assertTrue(a.compareTo(b)<0);

        a = new TextMatch(3, 4);
        b = new TextMatch(3, 7);
        assertTrue(a.compareTo(b)>0);

        a = new TextMatch(2, 4);
        b = new TextMatch(3, 7);
        assertTrue(a.compareTo(b)>0);

        a = new TextMatch(3, 7);
        b = new TextMatch(3, 7);
        assertTrue(a.compareTo(b)==0);

    }
}
