package org.opensextant.extractors.test;

import java.io.File;
import java.net.URL;

import org.opensextant.util.AnyFilenameFilter;
import org.opensextant.util.FileUtility;
import org.opensextant.util.TextUtils;

public class TestUtils {

    public void testISO639Names(String name_or_code) {
        System.out.println("Get Code=" + name_or_code + " =>>> " + TextUtils.getLanguage(name_or_code));
        System.out.println("Get Name=" + name_or_code + " =>>> " + TextUtils.getLanguageName(name_or_code));
    }

    public void test() {

        System.out.println("Known languages");
        for (String lang : TextUtils.getLanguageMap().keySet()) {
            System.out.println(String.format("Lang %s %s", lang, TextUtils.getLanguage(lang)));
        }
        testISO639Names("en");
        testISO639Names("eng");
        testISO639Names("English");
        testISO639Names("EN");
        testISO639Names("enG");
        testISO639Names("ENGLISH");
        testISO639Names("ara");
        testISO639Names("ar");
        testISO639Names("Arabic");
        testISO639Names("msa"); // Modern Standard Arabic or Malay?
        testISO639Names("Burmese");
        testISO639Names("Pol");
        testISO639Names("Not a language");
        testISO639Names("tl");

        testFilenames();

        String emo = "\ud83d\ude1d";
        System.out.println("What am I? " + emo);
        System.out.println(TextUtils.removeEmoticons("bla blah blahhh ;)  " + emo));
    }

    private boolean testFile(File testfile) {
        try {
            if (testfile.exists()) {
                System.out.println("file does exist.");
                return true;
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
        System.out.println("file does not exist.");
        return false;

    }

    public void testFilenames() {
        try {
            String rawpath = "/unicode-filenãme.txt";
            URL testobj = TestUtils.class.getResource(rawpath);
            File testfile = new File(testobj.toURI());
            File parentdir = testfile.getParentFile();
            testFile(testfile);

            String testpath = FileUtility.getValidFilename(testobj.toURI().getPath());
            testfile = new File(testpath);
            testFile(testfile);

            File[] testfiles = parentdir.listFiles(new AnyFilenameFilter(".txt"));
            for (File f : testfiles) {
                System.out.println("File exists? FILE:" + f.getAbsolutePath() + "? " + (f.exists() ? "Y" : "N"));
            }

        } catch (Exception err) {
            System.out.println("Failed to verify path");
        }
    }

    /**
     * 
     * @param args
     */
    public static void main(String[] args) {
        new TestUtils().test();
    }
}
