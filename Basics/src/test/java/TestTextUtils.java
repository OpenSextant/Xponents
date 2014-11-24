import static org.junit.Assert.*;

import org.junit.Test;
import org.opensextant.util.TextUtils;

public class TestTextUtils {

    @Test
    public void testRemoveLeft() {
        int count = TextUtils.removeAnyLeft("-+*ABC", "-").length();

        assertTrue (count == 5); // Trim
                             // left
        count = TextUtils.removeAnyLeft("-+*ABC", "+-").length();
        assertTrue (count == 4); // Trim left

        count = TextUtils.removeAny("-+*ABC", "+ - * (^%").length();
        assertTrue (count == 3); // Remove any chars from string. yields ABC
    }

    @Test
    public void testScriptDetection() {

        assertTrue (TextUtils.isLatin("Ö"));
        assertTrue (TextUtils.isLatin("a Ö 5 !"));
        assertTrue (!TextUtils.isLatin("a Ö 杨寨 5 !"));

        String t = TextUtils.replaceDiacritics("a Ö ø Ø é å Å 杨寨 5 ! ē M ē ā");
        if (!t.equals("a O o O e a A 杨寨 5 ! e M e a")) {
            fail("Diacritics not replaced!");
        }
    }

    @Test
    public void testLanguageCodes() {
        assertTrue ("Chinese".equals(TextUtils.getLanguage("chi").getName()));
        assertTrue ("French".equals(TextUtils.getLanguage("fre").getName()));
        assertTrue ("French".equals(TextUtils.getLanguage("fra").getName()));
        assertTrue ("French".equals(TextUtils.getLanguage("FRENCH").getName()));
    }

    @Test
    public void testCase() {
        assertTrue( !TextUtils.isLower("Abc") );
        assertTrue( TextUtils.isLower("abc") );
        assertTrue( !TextUtils.isLower("a b c 9 1$% Ö") );
        assertTrue( TextUtils.isLower("a b c 9 1$% ø"));

        assertTrue( !TextUtils.isUpper("ABc") );
        assertTrue(!TextUtils.isUpper("abc"));
        assertTrue( TextUtils.isUpper("A B C  9 1$% Ö"));
        assertTrue(! TextUtils.isUpper("A B C 9 1$% ø"));
    }

}
