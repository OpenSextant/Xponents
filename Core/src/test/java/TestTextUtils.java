import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.opensextant.util.TextUtils;

public class TestTextUtils {

    private void print(String m) {
        System.out.println(m);
    }

    @Test
    public void testAlphaUtils() {
        assertTrue(TextUtils.isASCIILetter('M'));
        assertTrue(TextUtils.isASCIILetter('n'));
        assertTrue(!TextUtils.isASCIILetter('9'));
    }

    @Test
    public void testFormatting() {
        assertEquals(1, TextUtils.countFormattingSpace("a\nb"));
        assertEquals(2, TextUtils.countFormattingSpace("a\n\u000Bb"));
    }

    @Test
    public void testTokens() {
        String[] toks = TextUtils.tokensRight("\n     ");
        assert(toks.length == 1); /* Not null */
        print(StringUtils.join(toks, ","));
    }

    @Test
    public void testEOL() {
        /*
         * Test parsing text with multiple lines -- get all tokens, get Right token, get
         * Left token.
         */
        String buf = "\t ABC\r\n\r\n123 x y z ";
        print(StringUtils.join(TextUtils.tokens(buf), ","));
        print(StringUtils.join(TextUtils.tokensRight(buf), ","));
        print(StringUtils.join(TextUtils.tokensLeft(buf), ","));
        String[] toks = TextUtils.tokensLeft(buf);
        assertEquals(1, toks.length);

        print(StringUtils.join(TextUtils.tokensRight(""), ","));
        print(StringUtils.join(TextUtils.tokensRight("ABC_NO_EOL"), ","));
    }

    @Test
    public void testRemoveSomeEmoticon() {
        String result = TextUtils.removeEmoticons("😪😔😱😱😱");
        System.out.println("Any emojis left? " + result);
        assertEquals("{icon}", TextUtils.removeEmoticons("😪😔😱😱😱"));
    }

    @Test
    public void testRemoveLeft() {
        int count = TextUtils.removeAnyLeft("-+*ABC", "-").length();

        assertEquals(5, count); // Trim
        // left
        count = TextUtils.removeAnyLeft("-+*ABC", "+-").length();
        assertEquals(4, count); // Trim left

        count = TextUtils.removeAny("-+*ABC", "+ - * (^%").length();
        assertEquals(3, count); // Remove any chars from string. yields ABC
    }

    @Test
    public void testScriptDetection() {

        assertTrue(TextUtils.isLatin("Ö"));
        assertTrue(TextUtils.isLatin("a Ö 5 !"));
        assertTrue(!TextUtils.isLatin("a Ö 杨寨 5 !"));

        String t = TextUtils.replaceDiacritics("a Ö ø Ø é å Å 杨寨 5 ! ē M ē ā");
        if (!t.equals("a O o O e a A 杨寨 5 ! e M e a")) {
            fail("Diacritics not replaced!");
        }
        assertTrue(!TextUtils.isASCII("xÖx"));
        assertTrue(TextUtils.isLatin("O a b c d O"));
    }

    @Test
    public void testLanguageCodes() {
        assertEquals("Chinese", TextUtils.getLanguage("chi").getName());
        assertEquals("French", TextUtils.getLanguage("fre").getName());
        assertEquals("French", TextUtils.getLanguage("fra").getName());
        assertEquals("French", TextUtils.getLanguage("FRENCH").getName());
    }

    @Test
    public void testCase() {

        String UPPER = "This IS MOSTLY 898 UPPER Case data $%%";
        String LOWER = "This is mostly lower cased data çx®tÇ 512131";

        /**
         * UPPER CASE tests. Mostly upper case vs. all upper case.
         */
        int[] checkCase = TextUtils.measureCase(UPPER);
        if (checkCase != null) {
            print("NOT uppercase\t" + UPPER);
            assertTrue(TextUtils.isUpperCaseDocument(checkCase));
        }
        checkCase = TextUtils.measureCase(UPPER.toUpperCase());
        if (checkCase != null) {
            print("IS uppercase\t" + UPPER.toUpperCase());
            assertTrue(TextUtils.isUpperCaseDocument(checkCase));
        }

        /**
         * LOWER CASE tests. Mostly lower case vs. all lower case.
         */
        checkCase = TextUtils.measureCase(LOWER);
        if (checkCase != null) {
            print("NOT lower\t" + LOWER);
            assertFalse(TextUtils.isLowerCaseDocument(checkCase));
        }
        checkCase = TextUtils.measureCase(LOWER.toLowerCase());
        if (checkCase != null) {
            print("IS lower\t" + LOWER.toLowerCase());
            assertTrue(TextUtils.isLowerCaseDocument(checkCase));
        }

        assertTrue(!TextUtils.isLower("Abc"));
        assertTrue(TextUtils.isLower("abc"));
        assertTrue(!TextUtils.isLower("a b c 9 1$% Ö"));
        assertTrue(TextUtils.isLower("a b c 9 1$% ø"));

        assertTrue(!TextUtils.isUpper("ABc"));
        assertTrue(!TextUtils.isUpper("abc"));
        assertTrue(TextUtils.isUpper("A B C  9 1$% Ö"));
        assertTrue(!TextUtils.isUpper("A B C 9 1$% ø"));

        String arabicText = "المناطق:";
        assertTrue(!TextUtils.isUpper(arabicText));
        String chineseText = "杨寨";
        assertTrue(!TextUtils.isUpper(chineseText));
        chineseText = "a 杨寨";
        assertTrue(TextUtils.isLower(chineseText));
        chineseText = "A 杨寨";
        assertTrue(TextUtils.isUpper(chineseText));
        String latinText = "ø Ø";
        // Neither upper or lower. Mixed.
        assertTrue(!TextUtils.isUpper(latinText) && !TextUtils.isLower(latinText));
        assertTrue(TextUtils.isLower("øh baby") && TextUtils.isUpper("ØH BABY"));
    }
}
