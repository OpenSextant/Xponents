import static org.junit.Assert.*;

import org.junit.Test;
import org.opensextant.util.TextUtils;

public class TestTextUtils {

    @Test
    public void testRemoveLeft() {
        int count = TextUtils.removeAnyLeft("-+*ABC", "-").length();

        assert (count == 5); // Trim
                             // left
        count = TextUtils.removeAnyLeft("-+*ABC", "+-").length();
        assert (count == 4); // Trim left

        count = TextUtils.removeAny("-+*ABC", "+ - * (^%").length();
        assert (count == 3); // Remove any chars from string. yields ABC
    }

    @Test
    public void testScriptDetection() {

        assert (TextUtils.isLatin("Ö"));
        assert (TextUtils.isLatin("a Ö 5 !"));
        assert (!TextUtils.isLatin("a Ö 杨寨 5 !"));

        String t = TextUtils.replaceDiacritics("a Ö ø Ø é å Å 杨寨 5 ! ē M ē ā");
        if (!t.equals("a O o O e a A 杨寨 5 ! e M e a")) {
            fail("Diacritics not replaced!");
        }
    }

    @Test
    public void testLanguageCodes() {
        assert ("Chinese".equals(TextUtils.getLanguage("chi").getName()));
        assert ("French".equals(TextUtils.getLanguage("fre").getName()));
        assert ("French".equals(TextUtils.getLanguage("fra").getName()));
        assert ("French".equals(TextUtils.getLanguage("FRENCH").getName()));
    }

    @Test
    public void testCase() {
        boolean b1 = !TextUtils.isLower("Abc");
        boolean b2 = TextUtils.isLower("abc");
        boolean b3 = !TextUtils.isLower("a b c 9 1$% Ö");
        boolean b4 = TextUtils.isLower("a b c 9 1$% ø");
        if (!(b1 && b2 && b3 && b4)) {
            fail("lower case tests failed.");
        }

        b1 = !TextUtils.isUpper("ABc");
        b2 = !TextUtils.isUpper("abc");
        b3 = TextUtils.isUpper("A B C  9 1$% Ö");
        b4 = !TextUtils.isUpper("A B C 9 1$% ø");
        if (!(b1 && b2 && b3 && b4)) {
            fail("upper case tests failed.");
        }
    }

}
