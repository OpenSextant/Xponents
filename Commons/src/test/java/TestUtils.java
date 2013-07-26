
import org.mitre.opensextant.util.*;

public class TestUtils {

    public void testISO639Names(String name_or_code) {
        System.out.println("Get Code=" + name_or_code + " =>>> " + TextUtils.getLanguage(name_or_code));
        System.out.println("Get Name=" + name_or_code + " =>>> " + TextUtils.getLanguageName(name_or_code));
    }

    public void test() {
        String emo = "\ud83d\ude1d";
        System.out.println("What am I? " + emo);
        System.out.println(UnicodeTextUtils.remove_emoticons("bla blah blahhh ;)  " + emo));

        testISO639Names("en");
        testISO639Names("eng");
        testISO639Names("English");
        testISO639Names("EN");
        testISO639Names("enG");
        testISO639Names("ENGLISH");
        testISO639Names("ara");
        testISO639Names("ar");
        testISO639Names("Arabic");
        testISO639Names("msa");
        testISO639Names("Burmese");
        testISO639Names("Pol");
        testISO639Names("Not a language");
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        new TestUtils().test();
    }
}
