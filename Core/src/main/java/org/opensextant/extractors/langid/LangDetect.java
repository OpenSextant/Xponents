/**
 * Copyright 2015-2016 The MITRE Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensextant.extractors.langid;

import java.io.File;
import java.net.URL;
import java.util.*;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import org.opensextant.ConfigException;
import org.opensextant.data.Language;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around cybozu labs langdetect. This tool provides a simple
 * "guessLanguage", where default Cybozu LangDetect may fail to return a
 * response due to IO errors and/or may provide multiple guesses
 * w/propabilities.
 * GuessLanguage here offers a fall back to look at unknown text to see if
 * it is in the ASCII or CJK families.
 * Use this API wrapper in conjunction with the Xponents TextUtils.getLanguage()
 * routine and Language class
 * to facilitate connecting LangID output with actual ISO 639 standards code
 * pages.
 * ISO 2-char and 3-char language IDs differ depending on the use --
 * historical/bibliographic vs. linguistic/locales.
 *
 * @author ubaldino
 */
public class LangDetect {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private String profilePath = null;

    /**
     * Default use requires you unpack LangDetect profiles here:
     * /langdetect-profiles
     *
     * @throws ConfigException
     */
    public LangDetect() throws ConfigException {
        initLangId();
    }

    public LangDetect(String profiles) throws ConfigException {
        profilePath = profiles;
        initLangId();
    }

    /**
     * If you anticipate working with short text - queries, tweets, excerpts, etc.
     * Then indicate that here. text working size is in # of Chars.
     *
     * @param textSz
     * @throws ConfigException
     */
    public LangDetect(int textSz) throws ConfigException {
        setWorkingSize(textSz);
        initLangId();
    }

    public LangDetect(int textSz, String profiles) throws ConfigException {
        profilePath = profiles;
        setWorkingSize(textSz);
        initLangId();
    }

    private int workingSize = -1;

    /**
     * If working size, in CHARS, is less than 180 (20 8 char words + 1 whitespace
     * word break);
     */
    public static final int DEFAULT_WORKING_SIZE = 20 * (8 + 1);

    /**
     * @param sz
     */
    public void setWorkingSize(int sz) {
        workingSize = sz;
    }

    /**
     * Taken straight from LangDetect example NOTE: /langdetect/profiles must be
     * a folder on disk, although I have a variation that could work with JAR
     * resources. So ordering classpath is important, but also folder itself
     * must exist.
     * TODO: workingSize is used only to guide default profile directory - short
     * message (sm) or not.
     * In the future workingSize
     */
    public void initLangId() throws ConfigException {
        boolean useShortTexts = (workingSize > 0 && workingSize < DEFAULT_WORKING_SIZE);

        boolean useClasspath = profilePath == null;
        if (profilePath == null) {
            profilePath = (useShortTexts ? "/langdetect/profiles.sm" : "/langdetect/profiles");
        }

        try {
            File profilePathDir = null;

            /*
             * Get directory from CLASSPATH or use absolute path given.
             * Either way this results in a directory with langdetect profiles.
             */
            if (useClasspath) {
                URL folder = LangDetect.class.getResource(profilePath);
                if (folder == null) {
                    throw new ConfigException("Failed to load profiles -- folder not in CLASSPATH");
                }
                profilePathDir = new File(folder.getPath());
            } else {
                profilePathDir = new File(profilePath);
            }

            DetectorFactory.loadProfile(profilePathDir);
            if (useShortTexts) {
                DetectorFactory.setSeed(0);
            }
        } catch (Exception err) {
            throw new ConfigException("Failed to load profiles", err);
        }
    }

    /**
     * API for LangDetect, cybozu.labs
     *
     * @param text ISO language ID or Locale. Straight from the Cybozu API
     * @return
     * @throws LangDetectException
     */
    public String detect(String text) throws LangDetectException {
        Detector detector = DetectorFactory.create();
        detector.append(text);
        return detector.detect();
    }

    /**
     * API for LangDetect, cybozu.labs.
     * However, this does not return cybozu.Language object; this method returns its
     * own LangID class
     *
     * @param text
     * @param withProbabilities true to include propabilities on results
     * @return
     * @throws LangDetectException
     */
    public Map<String, LangID> detect(String text, boolean withProbabilities) throws LangDetectException {
        Detector detector = DetectorFactory.create();
        detector.append(text);
        String langid = detector.detect();

        Map<String, LangID> detections = new HashMap<>();
        List<com.cybozu.labs.langdetect.Language> probabilities = detector.getProbabilities();
        for (com.cybozu.labs.langdetect.Language lid : probabilities) {
            detections.put(lid.lang, new LangID(lid.lang, lid.prob, langid.equals(lid.lang)));
        }
        return detections;
    }

    /**
     * Sort what was found; Returns LangID by highest score to lowest.
     *
     * @param lids
     * @return
     */
    public static List<LangID> sort(Map<String, LangID> lids) {
        List<LangID> sorted = new ArrayList<>(lids.values());
        Collections.sort(sorted);
        Collections.reverse(sorted);
        return sorted;
    }

    /**
     * Routine to guess the language ID
     * Scrub data prior to guessing language. If you feed that non-language text
     * (jargon, codes, tables, URLs, hashtags,
     * data) will interfere or overwhelm to volume of natural language text.
     *
     * @param data
     * @return
     */
    public Language guessLanguage(String data) {
        if (data == null) {
            return null;
        }
        // Get a reasonable language ID.
        try {
            String lid = detect(data);
            Language L = TextUtils.getLanguage(lid);
            if (L == null) {
                return new Language(lid, lid);
            }
        } catch (Exception lderr) {
            // log.error("LANG-ID", lderr);
        }

        return LangDetect.alternativeLangID(data);
    }

    public static final Language LANGUAGE_ID_GROUP_ENGLISH = new Language("eng", TextUtils.englishLang, "English");
    public static final Language LANGUAGE_ID_GROUP_CJK = new Language("cjk", "cjk", "Chinese/Japanese/Korean");
    public static final Language LANGUAGE_ID_GROUP_UNKNOWN = new Language("unk", "unk", "Unknown");

    /**
     * Look at raw bytes/characters to see which Unicode block they fall into.
     *
     * @param data
     * @return
     */
    public static Language alternativeLangID(String data) {

        // Fall-back: IF data is pure ASCII AND you could not identify
        // the language using lang-detect, then just assume English.
        // Otherwise assess if text is CJK
        // Lastly, just claim UNK
        //
        if (TextUtils.isASCII(data.getBytes())) {
            return LANGUAGE_ID_GROUP_ENGLISH;
        } else {
            double cjkRatio = TextUtils.measureCJKText(data);
            if (cjkRatio > 0.10) {
                return LANGUAGE_ID_GROUP_CJK;
                // at least mark it as
                // Chinese/Japanese/Korean
            }
        }

        return LANGUAGE_ID_GROUP_UNKNOWN;
    }

    /**
     * detecting if script of text is Japanese, Korean or Chinese.
     * Given Chinese Unicode block contains CJK unified ideographs, the presence of
     * Chinese characters does not indicate any of the three langugaes uniquely.
     * This is used only if CyboZu LangDetect fails OR if you want to detect
     * language(s)
     * in mixed text.
     *
     * @param data
     * @return
     */
    public static Map<String, LangID> alternativeCJKLangID(String data) {

        int c = 0;
        int j = 0;
        int k = 0;
        int len = data.length();
        int chars = 0;
        char ch;
        for (int x = 0; x < len; ++x) {
            ch = data.charAt(x);
            if (ch <= 0x20) {
                continue;
            }
            ++chars;
            // Ignore ASCII outright.
            // Ignore Latin-1 outright.
            if (ch < 0xFE) {
                continue;
            }
            Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
            if (TextUtils.isJapanese(b)) {
                ++j; // Japanese
            } else if (TextUtils.isKorean(b)) {
                ++k; // Or Korean
            } else if (TextUtils.isChinese(b)) {
                ++c; // Or Chinese, but only if not Japanese or Korean already.
            }
        }

        if (c == 0 && k == 0 && j == 0) {
            return null;
        }

        Map<String, LangID> langid = new HashMap<>();
        int cjk = c + j + k;
        if (j > 0) {
            langid.put(TextUtils.japaneseLang, new LangID(TextUtils.japaneseLang, cjkRatio(chars, cjk, j), false));
        }
        if (k > 0) {
            // This  is primary only if  Japanese is 0.
            langid.put(TextUtils.koreanLang, new LangID(TextUtils.koreanLang, cjkRatio(chars, cjk, k), false));
        }
        if (c > 0) {
            // This is primary language
            langid.put(TextUtils.chineseLang,
                    new LangID(TextUtils.chineseLang, cjkRatio(chars, cjk, c), (j == 0 && k == 0)));
            // Distinct chinese script === if and only if there are no Korean or Japanese characters.
        }

        return langid;
    }

    /**
     * L = Unique C, J, or K characters
     * CJK = total CJK
     * TOT = total characters non-control or whitespace.
     * ratio = 0.5 * (L/CJK + CJK/TOT) produces a number always less than 1.0
     * 1 Japanese char in 5 CJK chars out of a text of 20 characters (regardless of whitespace).
     * ratio is 1/2 * (1/5 + 5/20) === 9/40 ~ 0.21 is score for this text, with Japanese being the primary choice.
     * ratio for Chinese would be:
     * 1/2 * (4/5 + 5/20) === 21/40 ~ 0.51, which is higher than that for Japanese,
     * however, as CJK share a common character base, you first measure if any J or K is present, and then C.
     *
     * @param total
     * @param cjk
     * @param langCount
     * @return
     */
    private static double cjkRatio(int total, int cjk, int langCount) {
        double uniqueness = (double) langCount / cjk;
        return (uniqueness + ((double) cjk / total)) / 2;
    }

    /**
     * Language ID mappings to Locales. That is, LangDetect detects mostly
     * languages by reporting ID, but occassionally refines this by reporting a
     * Locale (lang+country)
     */
    static final Map<String, String> lookupLanguage = new HashMap<>();
    static final Map<String, Integer> ignoredLanguage = new HashMap<>();

    static {
        lookupLanguage.put("en-gb", TextUtils.englishLang);
        lookupLanguage.put("zh-cn", TextUtils.chineseLang);
        lookupLanguage.put("zh-tw", TextUtils.chineseLang);

        // Languages classified as such, will be ignored.
        ignoredLanguage.put("cjk", 0);
        ignoredLanguage.put("unk", 0);
        //  These languages are not reliably detected with shorter texts
        ignoredLanguage.put("tl", -1);
        ignoredLanguage.put("ro", -1);
        ignoredLanguage.put("ca", -1);
        ignoredLanguage.put("it", -1);
        ignoredLanguage.put("fr", -1);
        ignoredLanguage.put("es", -1);
        ignoredLanguage.put("de", -1);
        ignoredLanguage.put("sv", -1);
        ignoredLanguage.put("da", -1);
        ignoredLanguage.put("~en", -1);
    }

    /**
     * Find best lang ID for short texts. By default this will not search for CJK
     * language ID if CJK characters are
     * present.
     *
     * @param lang
     * @param naturalLanguage
     * @return
     */
    public Language detectSocialMediaLang(String lang, String naturalLanguage) {
        return detectSocialMediaLang(lang, naturalLanguage, false);
    }

    /**
     * A simple threshold for demarcating when we might infer simple language ID
     * with minimal content.
     * E.g. 16 chars of ASCII text ~ we can possibly say it is English. However,
     * this is really only making an guess.
     */
    public static final int MIN_LENGTH_UNK_TEXT_THRESHOLD = 16; /* Characters */

    public static double MIN_LANG_DETECT_PROBABILITY = 0.60;

    /**
     * EXPERIMENTAL , EXPERIMENTAL, EXPERIMENTAL
     * UPDATE, 2015. Using Cybozu LangDetect 1.3 (released June 2014) operates
     * better on tweets
     * than previous version. A lot of this confusion was related to the lack of
     * optimization early versions had for social media.
     * ===============================
     * Not the proper method for general use. Lang ID is shunted for short text.
     * If lang is non-null, then "~lang" is returned for short text If lang is
     * null, we'll give it a shot. Short ~ two words of natural language, approx
     * 16 chars.
     * Objective is to return a single, best lang-id. More general purpose
     * routines are TBD: e.g., validate all lang-id found by LangDetect or other
     * solution.
     *
     * <pre>
     * Workflow used here for ANY text:
     * - get natural language of text ( the data, less any URLs, hashtags, etc.)
     *   For large documents, this is not necessary. TODO: evaluate LangDetect or others
     *   on longer texts (Blog with comments) to find all languages, etc.
     *
     * - Text is too Short?  if lang is non-null, then return "~XX"
     * - Find if text contains CJK:
     *      if contains K or J,  then return respective langID
     *      else text is unified CJK chars which is at least Chinese.
     *
     * - Use LangDetect
     *      if Error, use alternate LangID detection
     *      if Good and answer &lt; 0.65 (threshold), then report "~XX", as "~" implies low confidence.
     *
     * - Have a "lang-id" from all of the above?
     *      if lang-id is a locale, e.g, en_au, en_gb,  zh_tw, cn_tw, etc.
     *      return just the language part;
     *
     *  Return a two-char ISO langID
     * </pre>
     *
     * @param lang            given lang ID or null
     * @param naturalLanguage text to determine lang ID; Caller must prepare this
     *                        text, so consider using
     *                        DataUtility.scrubTweetText(t).trim();
     * @param findCJK         - if findCJK is true, then this will try to find the
     *                        best language ID if Chinese/Japanese/Korean
     *                        characters exist at all.
     * @return lang ID, possibly different than given lang ID.
     */
    public Language detectSocialMediaLang(String lang, String naturalLanguage, boolean findCJK) {

        int chars = naturalLanguage.length();
        boolean isASCII = TextUtils.isASCII(naturalLanguage);

        if (chars < MIN_LENGTH_UNK_TEXT_THRESHOLD && isASCII) {
            return LANGUAGE_ID_GROUP_ENGLISH;
        }

        /*
         * From here on down you are working with just the text that appears to
         * be natural language. That is the data has been stripped of URLs or
         * junk. Estimates of chars length reflect the human language text
         * better.
         */
        if (lang != null && chars < MIN_LENGTH_UNK_TEXT_THRESHOLD && isASCII) {
            /*
             * Punt. Not enough text in such a tweet. We'll call this about 2
             * words, on average. If it is pure ASCII,.. we don't care much
             * about further langID or translation.
             */
            log.debug("Insufficient content: {}\t{}", lang, naturalLanguage);
            return new Language(lang, String.format("~%s", lang));
        }

        Map<String, LangID> langids = null;
        String langID = null;

        /*
         * If we're looking for CJK, then determine if a single character of CJK is
         * present.
         */
        boolean hasCJK = (findCJK && TextUtils.hasCJKText(naturalLanguage));

        int confidence = -1;

        try {
            langids = detect(naturalLanguage, true);
            if (langids != null && langids.size() > 0) {
                List<LangID> langidsSorted = LangDetect.sort(langids);
                LangID choice = langidsSorted.get(0);
                confidence = choice.probability > MIN_LANG_DETECT_PROBABILITY ? 1 : -1;
                langID = choice.langid;

                /*
                 * Special case -- caller thinks data contains CJK
                 * And if it does contain CJK, but lang-id returns non-CJK language, nullify
                 * and keep searching.
                 */
                if (hasCJK && !TextUtils.isCJK(langID)) {
                    langID = null;
                    log.debug("Nullify langdetect result -- content has CJK chars, you are looking for CJK langid");
                }
            }
        } catch (Exception langidErr) {
            log.debug("Failure in lang-id", langidErr);
            langID = null;
        }

        Language L = null;
        if (langID == null) {
            if (!isASCII) {
                langids = LangDetect.alternativeCJKLangID(naturalLanguage);

                // Dealing with mixed languages.
                //
                if (langids != null) {
                    List<LangID> langidsSorted = LangDetect.sort(langids);

                    // Close to 100% one language.
                    //
                    if (langidsSorted.size() == 1) {
                        langID = langidsSorted.get(0).langid;
                        log.debug("One Lang: {}\t{}", langID, naturalLanguage);
                        return TextUtils.getLanguage(langID);
                    }

                    langID = langidsSorted.get(0).langid;
                    log.debug("Multiple Lang: {}\t{}", langID, naturalLanguage);

                    return TextUtils.getLanguage(langID);
                } else {
                    L = LangDetect.alternativeLangID(naturalLanguage);
                    langID = L.getCode();
                }
            } else {
                // Either Null or isASCII:
                return LANGUAGE_ID_GROUP_ENGLISH;
            }
        }
        if (langID == null) {
            return LANGUAGE_ID_GROUP_UNKNOWN;
        }

        if (ignoredLanguage.containsKey(langID)) {
            boolean isUpper = TextUtils.isUpperCaseDocument(TextUtils.measureCase(naturalLanguage));
            if (isUpper && isASCII) {
                return LANGUAGE_ID_GROUP_ENGLISH;
            }
            int threshold = (isUpper ? 200 : 120);

            // For shorter texts, if ASCII, just assume this is english.
            // For UPPER CASE texts not ASCII, you need more content to rely on lang ID.
            //
            // LENGTH FILTER.
            if (chars < threshold && lang != null) {
                if (isASCII) {
                    return LANGUAGE_ID_GROUP_ENGLISH;
                }
                return new Language(lang, lang);
            } else if (lang == null && isASCII) {
                // ASCII FILTER.
                return LANGUAGE_ID_GROUP_ENGLISH;
            }

            // OKAY, you win Cybozu.
            // This is usually wrong. :(
            return TextUtils.getLanguage(langID);
        }

        log.debug("LangDetect Lang: {}\t{}", langID, naturalLanguage);

        /*
         * Unanticipated Lang ID came back, as it is longer than 2-chars.
         * NOTE, Almost 100% of the time LangDetect reports "zh-tw" for any chinese
         * tweets. Traditional Chinese, really? Cybozu needs more training there.
         */
        if (langID.length() > 2) {
            String langID_notLocale = lookupLanguage.get(langID);
            if (langID_notLocale != null) {
                langID = langID_notLocale;
            }
        }

        return (confidence < 0 ? new Language(langID, String.format("~%s", langID)) : TextUtils.getLanguage(langID));
    }
}
