package org.opensextant.extractors.test;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensextant.extraction.MatcherUtils;
import org.opensextant.extraction.TextEntity;

public class TestMoreTextUtilis {

    @Before
    public void setUp() throws Exception {
    }

    private static void print(String s) {
        System.out.println(s);
    }

    @Test
    public void testFindSpans() {
        String text = "ABC <span style=color: Silver;><br><br><span style=font-size: inherit;>---------- 123 </span>";
        print(text);
        List<TextEntity> spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());

        text = "ABC <br>yakkety yak and <tag Dont talk bak.";
        spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());

        text = "ABC [TAG]<br>yakkety yak and <tag > Dont talk bak.[TAG data] text...";
        spans = MatcherUtils.findTagSpans(text);
        print(spans.toString());
    }

    public static void testSomeSpans(String buf) {
        List<TextEntity> spans = MatcherUtils.findTagSpans(buf);
        print(spans.toString());
    }

    public static void main(String[] args) {
        testSomeSpans(args[0]);
    }
}
