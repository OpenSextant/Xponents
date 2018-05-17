package org.opensextant.extractors.test;

import java.io.IOException;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;

public class TestPlaceGeocoderLanguages extends TestPlaceGeocoder {

    public TestPlaceGeocoderLanguages() throws ConfigException {
        super();
    }

    /**
     * Language-specific parsing will involve more testing...
     * For now, just making it available is enough.
     * 
     * @throws IOException
     */
    public void tagEvaluation() throws IOException {

        String[] textsAR = {
                "Mixed language text UAE place مدرسة الشيخة لطيفة بنت حمدان",
                "Hosp here عيادة بدر",
        };
        String[] textsCJK = {
                "冀州市冀州镇刘家埝小学-河北省衡水冀州冀州市冀州镇刘家埝小学.", // google search, yields baike.com         
                "Gazetteer entry in JPN スナモリ"
        };

        try {
            for (String t : textsAR) {
                print("TEST:\t" + t + "\n=====================");
                TextInput i = new TextInput("test", t);
                i.langid = TextUtils.arabicLang;
                List<TextMatch> matches = geocoder.extract(i);
                summarizeFindings(matches);
                print("\t\t\t Compare to Generic tagging:\n================");
                i.langid = null;
                matches = geocoder.extract(i);
                summarizeFindings(matches);
                print("\n");
            }

            for (String t : textsCJK) {
                print("TEST:\t" + t + "\n=====================");
                TextInput i = new TextInput("test", t);
                i.langid = TextUtils.chineseLang;
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
                //if (args.length == 1) {
                //    tester.tagFile(new File(args[0]));
                //} else if (args.length == 2) {
                //    tester.tagText(args[1]);
                //} else {
                tester.tagEvaluation();
                //}
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
