import static org.junit.Assert.*;

import org.junit.Test;
import org.opensextant.util.TextUtils;

public class TestTextUtils {

    //@Test
    public static void testRemoveLeft() {
        int count = TextUtils.removeAnyLeft("-+*ABC", "-").length();

        assert (count == 5); // Trim
                             // left
        count = TextUtils.removeAnyLeft("-+*ABC", "+-").length();
        assert (count == 4); // Trim left

        count = TextUtils.removeAny("-+*ABC", "+ - * (^%").length();
        assert (count == 3); // Remove any chars from string. yields ABC
    }

    public static void main(String[] x) {
        testRemoveLeft();

        for (String l : TextUtils.getLanguageMap().keySet()) {
            if (l.startsWith("c")) {
                System.out.println("l=" + l + " name=" + TextUtils.getLanguage(l));
            }
        }
        System.out.println("Chinese? "+TextUtils.getLanguage("chi"));
    }

}
