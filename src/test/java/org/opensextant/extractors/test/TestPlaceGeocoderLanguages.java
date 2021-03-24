package org.opensextant.extractors.test;

import java.io.IOException;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.Language;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.langid.LangDetect;
import org.opensextant.util.TextUtils;

public class TestPlaceGeocoderLanguages extends TestPlaceGeocoder {

    public TestPlaceGeocoderLanguages() throws ConfigException {
        super();
    }

    /**
     * Language-specific parsing will involve more testing... For now, just making
     * it available is enough.
     *
     * @throws IOException
     */
    @Override
    public void tagEvaluation() throws IOException {

        String[] textsMiddleEastScripts = {
                /* ARABIC */
                "Mixed language text UAE place مدرسة الشيخة لطيفة بنت حمدان",
                /* ASCII/ENG + ARABIC */
                "Hosp here عيادة بدر",
                /*
                 * URDU. Excerpt source is BBC Urdu service Google Translate: "After the
                 * announcement of Pakistan's 16-member team for the Asia Cup cricket
                 * tournament, the practice of the national cricket team in Lahore's Gaddafi
                 * Stadium continues."
                 */
                "ایشیا کپ کرکٹ ٹورنامنٹ کے لیے پاکستان کی 16 رکنی ٹیم کے اعلان کے بعد قومی کرکٹ ٹیم کی لاہور کے قذافی سٹیڈیم میں پریکٹس جاری ہے۔",

                /*
                 * Farsi. Except source is BBC Farsi service Google Translate:
                 * "Djokovic won the 14th Grand Prix in New York"
                 */
                "جوکوویچ چهاردهمین جام جایزه بزرگ خود را در نیویورک برد" };

        String[] textsCJK = { "冀州市冀州镇刘家埝小学-河北省衡水冀州冀州市冀州镇刘家埝小学.", // google search, yields baike.com
                "Gazetteer entry in JPN スナモリ",

                /*
                 * Source:https://www.sponichi.co.jp/sports/news/2018/09/10/kiji/
                 * 20180910s00028000088000c.html Google Translate: "Haitian American father and
                 * Japanese mother born in Hokkaido, I migrated from Osaka to the United States
                 * at the age of 3 and entered tennis, I am playing as Japan national team to
                 * speak English better than Japanese. Not only the inner gap between on and off
                 * but also the gap and complexity of cultural background is in Osaka"
                 */
                "ハイチ系米国人の父と北海道生まれの日本人の母を持ち、３歳で大阪から米国に移住してテニスに打ち込み、日本語より英語をうまく話すのに日本代表としてプレーしている。オンとオフの内面的なギャップだけでなく、文化的背景のギャップ、複雑さも大坂にはある. " };

        int textSize = 50;
        LangDetect langid = new LangDetect(textSize);

        try {
            for (String t : textsMiddleEastScripts) {
                print("%%%%%%%%%%%  %%%%%%%%%%%%%   %%%%%%%%%%%  %%%%%%%%%");
                print("TEST:\t" + t + "\n=====================");
                String languageID = langid.detect(t);
                TextInput i = new TextInput("test", t);
                // i.langid = TextUtils.arabicLang;
                i.langid = languageID;
                print("\tDetected language " + TextUtils.getLanguage(languageID).getName());
                List<TextMatch> matches = geocoder.extract(i);
                summarizeFindings(matches);
                print("\t\t\t Compare to Generic tagging:\n================");
                i.langid = null;
                matches = geocoder.extract(i);
                summarizeFindings(matches);
                print("\n");
            }

            for (String t : textsCJK) {
                print("%%%%%%%%%%%  %%%%%%%%%%%%%   %%%%%%%%%%%  %%%%%%%%%");
                print("TEST:\t" + t + "\n=====================");
                TextInput i = new TextInput("test", t);
                String languageID = null; /* langid.detect(t); */
                Language L = langid.detectSocialMediaLang(null, t, true);
                languageID = L.getCode();
                i.langid = languageID;
                print("\tDetected language " + TextUtils.getLanguage(languageID).getName());
                List<TextMatch> matches = geocoder.extract(i);
                summarizeFindings(matches);
                print("\t\t\t Compare to Generic tagging:\n================");
                i.langid = null;
                matches = geocoder.extract(i);
                summarizeFindings(matches);
                print("\n");
            }

        } catch (Exception procErr) {
            procErr.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            TestPlaceGeocoder tester = new TestPlaceGeocoderLanguages();

            try {
                // if (args.length == 1) {
                // tester.tagFile(new File(args[0]));
                // } else if (args.length == 2) {
                // tester.tagText(args[1]);
                // } else {
                tester.tagEvaluation();
                // }
            } catch (Exception err) {
                err.printStackTrace();
            }
            tester.cleanup();

            System.exit(0);

        } catch (Exception err) {
            err.printStackTrace();
        }
    }

}
