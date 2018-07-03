import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensextant.data.Language;
import org.opensextant.extractors.langid.LangDetect;

public class TestLangID {

    private static LangDetect langid = null;

    public static void main(String[] args) {
        try {
            new TestLangID().test();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        langid = new LangDetect(100, "./etc/langdetect/profiles.sm");
    }

    /**
     * These are trivial experiments -- not trying to truth the language ID of this data
     * ... just trying to see where various thresholds lie when working with CyboZu.
     */
    @Test
    public void test() {

        String itText = "RT RT_com: PHOTO: Inside Karachi airport, security personnel responding to militant attack";

        Language lid = langid.detectSocialMediaLang("en", itText);
        System.out.println("Lang is ? " + lid);
        String caText = "RT NBCNewYork: BREAKING: Penn Station evacuated as police investigate suspicious package (Pic via Desta_lux) http:...";
        lid = langid.detectSocialMediaLang("en", caText);
        System.out.println("Lang is ? " + lid);

        String inText = "DARLA DILI BASTA2 KAU CUM LAUDE SA UNIVERSITY OF THE PHILIPPINES!!";
        lid = langid.detectSocialMediaLang("en", inText);
        System.out.println("Lang is ? " + lid);
        String inText2 = "CUM LAUDE SA UNIVERSITY OF THE PHILIPPINES !! ";
        lid = langid.detectSocialMediaLang("en", inText2);
        System.out.println("Lang is ? " + lid);
        lid = langid.detectSocialMediaLang("en",
                inText.toLowerCase() + inText + inText.toLowerCase() + " Random not ROMANIAN TEXT Heré");
        System.out.println("MixedCase Is Tagalog Test: Lang is ? " + lid);

        inText = "SERIOUS QUESTION WHAT HAPPENS WHEN YOURE GOING THROUGH AIRPORT SECURITY IF YOU HAVE A DERMAL PIERCING ";
        lid = langid.detectSocialMediaLang("en", inText);
        System.out.println("Lang is ? " + lid);
        lid = langid.detectSocialMediaLang("en", inText + inText);
        System.out.println("Double Text Lang is ? " + lid);
        lid = langid.detectSocialMediaLang("en", inText.toLowerCase());
        System.out.println("LowerCase Test: Lang is ? " + lid);

        /** A tweet */
        String cjkText = "@xxxxxxxxxxxxx 재환아 ㅜㅠ 너두 감기 조심하구 옷 꼭꼭 챙겨입구 따신거 먹엉 ㅠㅠㅠ 촬영 힘내서 잘해요ヽ(；▽；)ノ"; /* Korean */
        String chiText = "2014年韩国影展 10部展映影片介绍: 《流感》香港版海报1、流感감기TheFlu导演：金成洙KIMSung-soo主演：张赫JANGHyuk秀爱SuAe类型：动作/剧情/冒险片长：121分钟制作年份：2013年剧情一... http://t.co/5mR4RNLL9f";
        String[] japText = { "おいおい大事な穴馬が...。 RT gendai_keiba: マイルＣＳ、サンライズメジャーが感冒のため出走取消（枠順発表前）。",
                "余は、流行りの感冒にかかった。朝議は欠席する。守護職に急ぎ使いを送り、病になれ。参内するなと伝えよ。" };

        /** Cybozu classifies this as Albanian: */
        String notChinese = "RT @1jqi: 千千万万用户不在乎RT @tyuidl: 总算见识了传说中的杀毒软件公司放病毒。 http://abc.co/6789afguhqrt";
        lid = langid.detectSocialMediaLang("zh", cjkText);
        System.out.println("Lang is ? " + lid);
        lid = langid.detectSocialMediaLang("zh", chiText);
        System.out.println("Lang is ? " + lid);

        for (String t : japText) {
            lid = langid.detectSocialMediaLang("ja", t);
            System.out.println("Lang is ? " + lid);
        }

        lid = langid.detectSocialMediaLang("zh", notChinese);
        System.out.println("Lang is ? " + lid);

    }
}
