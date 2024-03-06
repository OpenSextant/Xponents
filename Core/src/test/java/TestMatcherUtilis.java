import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensextant.extraction.MatcherUtils;
import org.opensextant.extraction.TextEntity;
import org.opensextant.util.FileUtility;
import static org.junit.Assert.*;

public class TestMatcherUtilis {

    @Before
    public void setUp() throws Exception {
    }

    private static void print(String s) {
        System.out.println(s);
    }

    @Test
    public void testTextEntity(){
        print("Test Overlaps and Proximity");
        TextEntity a  = new TextEntity( 5, 10);
        TextEntity b  = new TextEntity( 25, 40);
        TextEntity c  = new TextEntity( 7, 10); // right align with a
        TextEntity d  = new TextEntity( 7, 12); // overlap, skew right with a

        assertTrue( a.isWithinChars(a, 0)); // Identity test.
        assertTrue( a.isWithinChars(a, 4));

        //  a NOT within 4 chars of b
        assertFalse( a.isWithinChars(b, 4));
        //  test invalid distance
        assertFalse( a.isWithinChars(b, -4));
        // test valid case -- a <> b 15 chars, edge case
        assertTrue( a.isWithinChars(b, 15));
        assertTrue( b.isWithinChars(a, 15));
        // test valid case -- a <> b 14 chars, edge case
        assertFalse( a.isWithinChars(b, 14));
        assertFalse( b.isWithinChars(a, 14));
        // test valid case -- b <> a 20 chars
        assertTrue( a.isWithinChars(b, 20));
        assertTrue( b.isWithinChars(a, 20));

        assertTrue( a.isOverlap(a) );
        assertTrue( a.isOverlap(c) );
        assertTrue( a.isOverlap(d) );

    }

    @Test
    public void testFindSpans() {
        TestTools.printOffsetBanner();
        String text = "ABC <span style=color: Silver;><br><br><span style=font-size: inherit;>---------- 123 </span>";
        print("======\n" + text);
        List<TextEntity> spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());

        text = "ABC <br>yakkety yak and <tag Dont talk bak.";
        print("======\n" + text);
        spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());

        text = "ABC <br>yakkety yak and <tag Dont talk bak./ >.  Unless you mean it.";
        print("======\n" + text);
        spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());

        text = "ABC [TAG]<br>yakkety yak and <tag > Dont talk bak.[TAG data] text...";
        print("======\n" + text);
        spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());
        assertEquals(4, spans.size());
    }

    public static void testSomeSpans(String buf) throws IOException {
        String content = buf;
        if (new File(buf).exists()) {
            content = FileUtility.readFile(buf);
        }
        if (content == null) {
            print("Nothing given....");
            return;
        }
        List<TextEntity> spans = MatcherUtils.findTagSpans(content);
        for (TextEntity x : spans) {
            print(String.format("\t%s\t%s", x.toString(), content.substring(x.start, x.end + 1)));
        }
        // print(spans.toString());

    }

    public static void main(String[] args) {
        try {
            testSomeSpans(args[0]);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
