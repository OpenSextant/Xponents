import java.io.File;
import java.util.Map;

import org.opensextant.data.Language;
import org.opensextant.extractors.langid.LangDetect;
import org.opensextant.extractors.langid.LangID;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

public class TestLangIDUtility {

    public static void print(String m) {
        System.out.println(m);
    }

    public static String usage() {
        return "\tLangDetect -t <text>" + "\n\tLangDetect -f <file>";
    }

    public static void main(String[] args) {

        try {
            if (args.length < 2) {
                System.out.println(usage());
            }
            String opt = args[0];
            String val = args[1];
            boolean readFile = ("-f".equalsIgnoreCase(opt));
            String buffer = null;
            if (readFile) {
                File f = new File(val);
                buffer = FileUtility.readFile(f, "UTF-8");
            } else {
                buffer = val;
            }

            int len = buffer.length();
            LangDetect lid = new LangDetect(len);
            // More direct access to langdetect, which can offer probabilities of guesses:

            boolean simple = false;
            if (simple) {
                Language lang = lid.guessLanguage(buffer);
                print("For item @ " + (readFile ? "FILE=" : "TEXT=") + val);
                print("LANG ID = " + lang);
            } else {
                Map<String, LangID> langids = null;
                try {

                    langids = lid.detect(buffer, true);

                    for (LangID id : langids.values()) {
                        Language L = TextUtils.getLanguage(id.langid);
                        String langname = "unk";
                        if (L == null) {
                            langname = L.getName();
                        }
                        print(String.format("LANG ID = %s/%s with probability %f", id.langid, langname,
                                id.probability));
                    }
                } catch (Exception err) {
                    err.printStackTrace();
                }
                System.out.println("============Assessing CJK=============");
                langids = LangDetect.alternativeCJKLangID(buffer);
                if (langids != null) {
                    for (LangID id : langids.values()) {
                        org.opensextant.data.Language L = TextUtils.getLanguage(id.langid);
                        String langname = "unk";
                        if (L == null) {
                            langname = L.getName();
                        }
                        print(String.format("LANG ID = %s/%s with probability %f", id.langid, langname,
                                id.probability));
                    }
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
