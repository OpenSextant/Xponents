/**
 *
 * Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
///** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//   OpenSextant Commons
// *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
// */
package org.opensextant.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.Locale;

import org.apache.commons.lang.StringUtils;
import org.opensextant.data.Language;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

/**
 * 
 * @author ubaldino
 */
public class TextUtils {

    /**
     * @threadsafe False; use TextUtils() instance instead.
     * 
     */
    protected static MessageDigest md5 = null;
    /**
     * @threadsafe True; a private instance for use with a TextUtils instance.
     */
    private MessageDigest _md5 = null;

    static {
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (Exception err) {
            System.err.println("MD5 algorighthm could not intitialize");
        }

    }
    final static Pattern delws = Pattern.compile("\\s+");
    // Match ALL empty lines:
    // \n followed by other ootional whitespace
    // Up to 2 empty lines or more. This matches 3 line endings
    // The first EOL could be on a non-empty line, but then followed by 2 empty
    // lines.
    // The intent is to reduce 3 or more EOL to 2. Preserving paragraph breaks.
    //
    final static Pattern multi_eol = Pattern.compile("(\n[ \t\r]*){3,}");

    /**
     * Convenience constructor -- for thread-safe instances
     */
    public TextUtils() {
        try {
            _md5 = MessageDigest.getInstance("MD5");
        } catch (Exception err) {
            System.err.println("MD5 algorighthm could not intitialize");
        }
    }

    /**
     * @param data
     * @return boolean if data is ASCII or not
     */
    public static boolean isASCII(byte[] data) {
        for (byte b : data) {
            if (b < 0 || b > 0x7F) {
                return false;
            }
        }
        return true;
    }

    /**
     * count the number of ASCII bytes
     * 
     * @param data
     * @return count of ASCII bytes
     */
    public static int countASCIIChars(byte[] data) {
        int ascii = 0;
        for (byte b : data) {
            if (b > 0 || b < 0x80) {
                ++ascii;
            }
        }
        return ascii;
    }

    /**
     * Replaces all 3 or more blank lines with a single paragraph break (\n\n)
     * 
     * @param t
     * @return A string with fewer line breaks;
     * 
     */
    public static String reduce_line_breaks(String t) {

        Matcher m = multi_eol.matcher(t);
        if (m != null) {
            return m.replaceAll("\n\n");
        }
        return t;
    }

    /**
     * Delete whitespace of any sort.
     * 
     * @param t
     * @return String, without whitespace.
     */
    public static String delete_whitespace(String t) {
        Matcher m = delws.matcher(t);
        if (m != null) {
            return m.replaceAll("");
        }
        return t;
    }

    /**
     * Minimize whitespace.
     * 
     * @param t
     * @return String
     */
    public static String squeeze_whitespace(String t) {
        Matcher m = delws.matcher(t);
        if (m != null) {
            return m.replaceAll(" ");
        }
        return t;
    }

    /**
     * 
     * @param t
     * @return
     */
    public static String delete_eol(String t) {
        return t.replace('\n', ' ').replace('\r', ' ');
    }

    public final static char NL = '\n';
    public final static char CR = '\r';
    public final static char SP = ' ';
    public final static char TAB = '\t';
    public final static char DEL = 0x7F;

    /**
     * Delete control chars from text data; leaving text and whitespace only.
     * Delete char (^?) is also removed. Length may differ if ctl chars are
     * removed.
     * 
     * @param t
     * @return scrubbed buffer
     */
    public static String delete_controls(String t) {

        if (t == null) {
            return null;
        }
        StringBuilder tmpCleanBuf = new StringBuilder();

        for (char ch : t.toCharArray()) {
            if ((ch < 0x20 && !(ch == TAB || ch == NL)) || (ch == DEL)) {
                continue;
            }

            tmpCleanBuf.append(ch);
        }
        return tmpCleanBuf.toString();
    }

    /**
     * Counts all digits in text.
     * 
     * @param txt
     * @return
     */
    public static int count_digits(String txt) {
        if (txt == null) {
            return 0;
        }

        int digits = 0;
        for (char c : txt.toCharArray()) {
            if (Character.isDigit(c)) {
                ++digits;
            }
        }
        return digits;
    }

    /**
     * 
     * @param v
     * @return
     */
    public static boolean is_numeric(String v) {

        if (v == null) {
            return false;
        }

        for (char ch : v.toCharArray()) {

            if (ch == '.' || ch == '-' || ch == '+') {
                continue;
            }

            if (!Character.isDigit(ch)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Counts all digits in text.
     * 
     * @param txt
     * @return
     */
    public static int count_ws(String txt) {
        if (txt == null) {
            return 0;
        }

        int ws = 0;
        for (char c : txt.toCharArray()) {
            // isWhitespaceChar(c)?
            if (Character.isWhitespace(c)) {
                ++ws;
            }
        }
        return ws;
    }

    /**
     * For measuring the upper-case-ness of short texts. Returns true if ALL
     * letters in text are UPPERCASE. Allows for non-letters in text.
     * 
     * @param dat
     * @return
     */
    public static boolean isUpper(String dat) {
        if (dat == null) {
            return false;
        }
        int letterCount = 0;
        for (char c : dat.toCharArray()) {
            if (!Character.isLetter(c)) {
                continue;
            }
            ++letterCount;
            if (Character.isLowerCase(c)) {
                return false;
            }
        }
        // IF at least one Letter found
        // then
        return letterCount > 0;
    }

    /**
     * EH: EHCommons.get_text_window
     * 
     * @param offset
     * @param width
     * @param textsize
     * @param matchlen
     * @return
     */
    public static int[] get_text_window(int offset, int matchlen, int textsize, int width) {
        /*
         * prepreprepre MATCH postpostpost ^ ^ ^ ^ l-width l l+len l+len+width
         * left_y left_x right_x right_y
         */
        int left_x = offset - width;
        int left_y = offset - 1;
        int right_x = offset + matchlen;
        int right_y = right_x + width;
        if (left_x < 0) {
            left_x = 0;
        }

        // Fix left side of bounds
        if (left_y < left_x) {
            left_y = left_x;
        }

        // Fix right side of bounds
        if (right_y >= textsize) {
            right_y = textsize;
        }
        if (right_x > right_y) {
            right_x = right_y;
        }

        int[] slice = { left_x, left_y, right_x, right_y };

        return slice;
    }

    /**
     * Get a single text window around the offset.
     * 
     * @param offset
     * @param width
     * @param textsize
     * @return offsets of window bounded by document ends.
     */
    public static int[] get_text_window(int offset, int textsize, int width) {
        /*
         * left .... match .... right
         */
        int half = (width / 2);
        int left = offset - half;
        int right = offset + half;

        if (left < 0) {
            left = 0;
        }

        // Fix right side of bounds
        if (right >= textsize) {
            right = textsize;
        }

        int[] slice = { left, right };

        return slice;
    }

    /**
     * Static method -- use only if you are sure of thread-safety.
     * 
     * @param text
     * @return
     */
    public static String text_id(String text) {
        if (text == null) {
            return null;
        }

        md5.reset();
        md5.update(text.getBytes());

        return md5_id(md5.digest());
    }

    /**
     * Generate a Text ID using the raw bytes and MD5 algorithm.
     * 
     * @param text
     * @return
     */
    public String genTextID(String text) {
        if (text == null) {
            return null;
        }

        _md5.reset();
        _md5.update(text.getBytes());

        return md5_id(_md5.digest());
    }

    /**
     * 
     * @param md5digest
     * @return
     */
    public static String md5_id(byte[] md5digest) {
        // Thanks to javacream:
        // create hex string from the 16-byte hash
        StringBuilder hashbuf = new StringBuilder(md5digest.length * 2);
        for (byte b : md5digest) {
            int intVal = b & 0xff;
            if (intVal < 0x10) {
                hashbuf.append("0");
            }
            hashbuf.append(Integer.toHexString(intVal));
        }
        return hashbuf.toString().toLowerCase();
    }

    /**
     * Get a list of values into a nice, scrubbed array of values, no
     * whitespace.
     * 
     * a, b, c d e, f => [ "a", "b", "c d e", "f" ]
     * 
     * @param s
     * @param delim
     * @return
     */
    public static List<String> string2list(String s, String delim) {
        if (s == null) {
            return null;
        }

        List<String> values = new ArrayList<String>();
        String[] _vals = s.split(delim);
        for (String v : _vals) {
            String val = v.trim();
            if (!val.isEmpty()) {
                values.add(val);
            }
        }
        return values;
    }

    /**
     * Given a string S and a list of characters to replace with a substitute,
     * 
     * return the new string, S'.
     * 
     * "-name-with.invalid characters;" // replace "-. ;" with "_"
     * "_name_with_invalid_characters_" //
     * 
     * @param buf
     * @param replace
     *            string of characters to replace with the one substitute char
     * @param substitution
     * @return
     */
    public static String fast_replace(String buf, String replace, String substitution) {

        StringBuilder _new = new StringBuilder();
        for (char ch : buf.toCharArray()) {
            if (replace.indexOf(ch) >= 0) {
                _new.append(substitution);
            } else {
                _new.append(ch);
            }
        }
        return _new.toString();
    }

    /**
     * Remove instances of any char in the remove string from buf
     * 
     * @param buf
     * @param remove
     * @return
     */
    public static String removeAny(String buf, String remove) {

        StringBuilder _new = new StringBuilder();
        for (char ch : buf.toCharArray()) {
            if (remove.indexOf(ch) < 0) {
                _new.append(ch);
            }
        }
        return _new.toString();
    }

    /**
     * Replace any of the removal chars with the sub.  A many to one replacement.
     * alt:  use regex String.replace(//, '')
     * @param buf
     * @param remove
     * @param sub
     * @return
     */
    public static String replaceAny(String buf, String remove, String sub) {

        StringBuilder _new = new StringBuilder();
        for (char ch : buf.toCharArray()) {
            if (remove.indexOf(ch) < 0) {
                _new.append(ch);
            } else {
                _new.append(sub);
            }
        }
        return _new.toString();
    }

    /**
     * compare to trim( string, chars ), but you can trim any chars
     * 
     * Example: - a b c remove "-" from string above.
     * 
     * @param buf
     * @param remove
     * @return
     */
    public static String removeAnyLeft(String buf, String remove) {

        boolean eval = true;
        // Start from left.
        int x = 0;
        for (char ch : buf.toCharArray()) {
            if (eval && remove.indexOf(ch) >= 0) {
                ++x;
                continue;
            } else {
                eval = false; // shunt the evaluation of the chars.
            }
        }
        return buf.substring(x);
    }

    /**
     * Normalization: Clean the ends, Remove Line-endings from middle of entity.
     * 
     * <pre>
     *  Example:
     *        TEXT: **The Daily Newsletter of \n\rBarbara, So.**
     *       CLEAN: __The Daily Newsletter of __Barbara, So___
     * 
     * Where "__" represents omitted characters.
     * </pre>
     */
    public static String normalizeTextEntity(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }

        char[] chars = str.toCharArray();

        int s1 = 0, s2 = chars.length - 1;
        int end = s2;

        while (s1 < s2 && !(Character.isLetter(chars[s1]) || Character.isDigit(chars[s1]))) {
            ++s1;
        }

        // No text found
        if (s1 == s2) {
            return null;
        }

        while (s2 > s1 && !(Character.isLetter(chars[s2]) || Character.isDigit(chars[s2]))) {
            --s2;
        }

        if (s1 == 0 && s2 == end) {
            // No cleanup to do.
            return squeeze_whitespace(str);
        }

        // NOT possible, I hope...
        if (s2 <= s1) {
            return "";
        }

        // Some cleanup was done on ends of String. Now clear up whitespace.
        //
        return squeeze_whitespace(str.substring(s1, s2 + 1));
    }

    /**
     * Intended only as a filter for punctuation within a word. Text of the form
     * A.T.T. or U.S. becomes ATT and US. A text such as Mr.Pibbs incorrectly
     * becomes MrPibbs but for the purposes of normalizing tokens this should be
     * fine. Use appropriate tokenization prior to using this as a filter.
     */
    public static String normalizeAbbreviation(String word) {
        return word.replace(".", "");
    }

    /**
     * @see Phoneticizer utility from OpenSextant v1.x Remove diacritics from a
     *      phrase
     */
    public static String removeDiacritics(String word) {

        // first, fully decomposed all chars
        String tmpWord = Normalizer.normalize(word, Normalizer.Form.NFD);
        StringBuilder newWord = new StringBuilder();
        char[] chars = tmpWord.toCharArray();
        // now, discard any characters from one of the "Mark" categories.
        for (char c : chars) {
            if (Character.getType(c) != Character.NON_SPACING_MARK
                    && Character.getType(c) != Character.COMBINING_SPACING_MARK
                    && Character.getType(c) != Character.ENCLOSING_MARK) {
                newWord.append(c);
            }
        }
        return newWord.toString();
    }

    /**
     * Normalize to "Normalization Form Canonical Decomposition" (NFD) REF:
     * http:
     * //stackoverflow.com/questions/3610013/file-listfiles-mangles-unicode-
     * names-with-jdk-6-unicode-normalization-issues This supports proper file
     * name retrieval from file system, among other things. In many situations
     * we see unicode file names -- Java can list them, but in using the
     * Java-provided version of the filename the OS/FS may not be able to find
     * the file by the name given in a particular normalized form.
     * 
     * @param str
     * @return
     */
    public static String normalizeUnicode(String str) {
        Normalizer.Form form = Normalizer.Form.NFD;
        if (!Normalizer.isNormalized(str, form)) {
            return Normalizer.normalize(str, form);
        }
        return str;
    }

    /**
     * Matches non-text after a word.
     */
    final static Pattern CLEAN_WORD_RIGHT = Pattern.compile("[^\\p{L}\\p{Nd}]+$");
    /**
     * Matches non-text preceeding text
     */
    final static Pattern CLEAN_WORD_LEFT = Pattern.compile("^[^\\p{L}\\p{Nd}]+");
    /**
     * Obscure punctuation pattern that also deals with Unicode single and
     * double quotes
     */
    final static Pattern CLEAN_WORD_PUNCT = Pattern.compile("[\"'.`\\u00B4\\u2018\\u2019]");

    /**
     * Remove any leading and trailing punctuation and some internal
     * punctuation. Internal punctuation which indicates conjunction of two
     * tokens, e.g. a hyphen, should have caused a split into separate tokens at
     * the tokenization stage.
     * 
     * @see Phoneticizer utility from OpenSextant v1.x Remove punctuation from a
     *      phrase
     */
    public static String removePunctuation(String word) {

        String tmp = CLEAN_WORD_LEFT.matcher(word).replaceAll(" ");
        tmp = CLEAN_WORD_RIGHT.matcher(tmp).replaceAll(" ");

        // remove some internal punctuation. To be removed: char hex
        // unicode_name
        // " 22 QUOTATION MARK
        // ' 27 APOSTROPHE
        // . 2e FULL STOP
        // ` 60 GRAVE ACCENT
        // � b4 ACUTE ACCENT
        // � 2018 LEFT SINGLE QUOTATION MARK
        // � 2019 RIGHT SINGLE QUOTATION MARK
        return CLEAN_WORD_PUNCT.matcher(tmp).replaceAll("").trim();
    }

    // Alphabetic list of top-N languages -- ISO-639_1 "ISO2" language codes
    //
    public final static String arabicLang = "ar";
    public final static String bahasaLang = "id";
    public final static String chineseLang = "zh";
    public final static String chineseTradLang = "zt";
    public final static String englishLang = "en";
    public final static String farsiLang = "fa";
    public final static String frenchLang = "fr";
    public final static String germanLang = "de";
    public final static String italianLang = "it";
    public final static String japaneseLang = "ja";
    public final static String koreanLang = "ko";
    public final static String portugueseLang = "pt";
    public final static String russianLang = "ru";
    public final static String spanishLang = "es";
    public final static String turkishLang = "tr";
    public final static String thaiLang = "th";
    public final static String vietnameseLang = "vi";
    public final static String romanianLang = "ro";
    private final static Map<String, Language> LanguageMap_ISO639 = new HashMap<String, Language>();

    static {
        try {
            initLanguageData();
            initLOCLanguageData(); // Only fills in if a language code does not
                                   // exist.
            // initICULanguageData();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    /**
     * TODO: should be immutable list. But this is not a security issue; If
     * caller wants to add language they can.
     */
    public static Map<String, Language> getLanguageMap() {
        return LanguageMap_ISO639;
    }

    /**
     * Initialize language codes and metadata. This establishes a map for the
     * most common language codes/names that exist in at least ISO-639-1 and
     * have a non-zero 2-char ID.
     * 
     * Based on:
     * http://stackoverflow.com/questions/674041/is-there-an-elegant-way
     * -to-convert-iso-639-2-3-letter-language-codes-to-java-lo
     * 
     * Actual code mappings: en => eng eng => en
     * 
     * cel => '' // Celtic; Avoid this.
     * 
     * tr => tur tur => tr
     * 
     * Names: tr => turkish tur => turkish turkish => tr // ISO2 only
     * 
     * 
     */
    public static void initLanguageData() {
        Locale[] locales = Locale.getAvailableLocales();
        for (Locale locale : locales) {
            Language l = new Language(locale.getISO3Language(), locale.getLanguage(),
                    locale.getDisplayLanguage());
            addLanguage(l);
        }

    }

    /**
     * This is Libray of Congress data for language IDs. This is offered as a tool to help downstream language ID
     * and enrich metadata when tagging data from particular countries.
     * 
     * TODO: consider whether this should be initialized optionally.
     * @see http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt  for reference data used here.
     * 
     * @throws java.io.IOException
     */
    public static void initLOCLanguageData() throws java.io.IOException {
        //
        // DATA FILE: http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt

        java.io.InputStream io = TextUtils.class.getResourceAsStream("/ISO-639-2_utf-8.txt");
        java.io.Reader featIO = new InputStreamReader(io);
        // Pipe-delimited data.
        CsvMapReader langReader = new CsvMapReader(featIO,
                new CsvPreference.Builder('"', '|', "\n").build());

        String[] columns = langReader.getHeader(true);

        /*
         * ISO3,XX,ISO2,NAME,NAME_FR
         */
        Map<String, String> lang;
        while ((lang = langReader.read(columns)) != null) {
            //
            String names = lang.get("NAME");
            if (names == null) {
                continue;
            }
            List<String> namelist = TextUtils.string2list(names, ";");
            Language l = new Language(lang.get("ISO3"), lang.get("ISO2"), namelist.get(0));
            addLanguage(l);
        }

        langReader.close();
    }

    /**
     * Extend the basic language dictionary.
     * 
     * @param lg
     */
    public static void addLanguage(Language lg) {
        if (lg == null) {
            return;
        }
        if (lg.getCode() != null && !LanguageMap_ISO639.containsKey(lg.getCode())) {
            LanguageMap_ISO639.put(lg.getCode(), lg);
        }
        if (lg.getISO639_1_Code() != null && !LanguageMap_ISO639.containsKey(lg.getISO639_1_Code())) {
            LanguageMap_ISO639.put(lg.getISO639_1_Code(), lg);
        }
        String namekey = lg.getName();
        if (namekey != null) {
            namekey = namekey.toLowerCase();
            if (!LanguageMap_ISO639.containsKey(namekey)) {
                LanguageMap_ISO639.put(namekey, lg);
            }
        }
    }

    /**
     * Given an ISO2 char code (least common denominator) retrieve Language
     * Name.
     * 
     * This is best effort, so if your code finds nothing, this returns code
     * normalized to lowercase.
     */
    public static String getLanguageName(String code) {
        if (code == null) {
            return null;
        }

        Language l = LanguageMap_ISO639.get(code.toLowerCase());
        if (l == null) {
            return code;
        }
        return l.getName();
    }

    /**
     * ISO2 and ISO3 char codes for languages are unique.
     * 
     * @param code iso2 or iso3 code
     * @return the other code.
     */
    public static Language getLanguage(String code) {
        if (code == null) {
            return null;
        }

        return LanguageMap_ISO639.get(code.toLowerCase());
    }

    /**
     * ISO2 and ISO3 char codes for languages are unique.
     * 
     * @param code iso2 or iso3 code
     * @return the other code.
     */
    public static String getLanguageCode(String code) {
        if (code == null) {
            return null;
        }

        Language l = LanguageMap_ISO639.get(code.toLowerCase());
        if (l != null) {
            return l.getCode();
        }
        return null;
    }

    private static boolean _isRomanceLanguage(String l) {
        return (l.equals(spanishLang) || l.equals(portugueseLang) || l.equals(italianLang)
                || l.equals(frenchLang) || l.equals(romanianLang));
    }

    /**
     * European languages = Romance + GER + ENG
     * 
     * Extend definition as needed.
     */
    public static boolean isEuroLanguage(String l) {
        Language lang = getLanguage(l);

        if (lang == null) {
            return false;
        }
        String id = lang.getISO639_1_Code();
        return (_isRomanceLanguage(id) || id.equals(germanLang) || id.equals(englishLang));
    }

    /**
     * Romance languages = SPA + POR + ITA + FRA + ROM
     * 
     * Extend definition as needed.
     */
    public static boolean isRomanceLanguage(String l) {
        Language lang = getLanguage(l);

        if (lang == null) {
            return false;
        }
        String id = lang.getISO639_1_Code();
        return _isRomanceLanguage(id);
    }

    /**
     * Utility method to check if lang ID is English...
     * 
     * @param x
     *            a langcode
     * @return whether langcode is english
     */
    public static boolean isEnglish(String x) {
        Language lang = getLanguage(x);

        if (lang == null) {
            return false;
        }
        String id = lang.getISO639_1_Code();
        return (id.equals(englishLang));
    }

    /**
     * Utility method to check if lang ID is Chinese(Traditional or
     * Simplified)...
     * 
     * @param x
     *            a langcode
     * @return whether langcode is chinese
     */
    public static boolean isChinese(String x) {
        Language lang = getLanguage(x);

        if (lang == null) {
            return false;
        }
        String id = lang.getISO639_1_Code();
        return (id.equals(chineseLang) || id.equals(chineseTradLang));
    }

    /**
     * Utility method to check if lang ID is Chinese, Korean, or Japanese
     * 
     * @param x  a langcode
     * @return whether langcode is a CJK language
     */
    public static boolean isCJK(String x) {
        Language lang = getLanguage(x);

        if (lang == null) {
            return false;
        }
        String id = lang.getISO639_1_Code();
        return (id.equals(koreanLang) || id.equals(japaneseLang) || id.equals(chineseLang) || id
                .equals(chineseTradLang));
    }

    /**
     * Returns a ratio of Chinese/Japanese/Korean characters: CJK chars / ALL
     * 
     * TODO: needs testing; not sure if this is sustainable if block; or if it
     * is comprehensive. TODO: for performance reasons the internal chain of
     * comparisons is embedded in the method; Otherwise for each char, an
     * external method invocation is required.
     * 
     * @param buf  the character to be tested
     * @return true if CJK, false otherwise
     */
    public static double measureCJKText(String buf) {

        if (buf == null) {
            return -1.0;
        }

        int cjkCount = countCJKChars(buf.toCharArray());

        return ((double) cjkCount) / buf.length();
    }

    /**
     * Counts the CJK characters in buffer, buf chars Inspiration:
     * http://stackoverflow
     * .com/questions/1499804/how-can-i-detect-japanese-text-in-a-java-string
     * 
     * @param buf
     * @return
     */
    public static int countCJKChars(char[] chars) {
        int cjkCount = 0;
        for (char ch : chars) {
            // Ignore ASCII outright.
            // Ignore Latin-1 outright.
            if (ch < 0xFE) {
                continue;
            }
            Character.UnicodeBlock blk = Character.UnicodeBlock.of(ch);
            if (isCJK(blk)) {
                // increment counter:
                ++cjkCount;
            }
        }
        return cjkCount;
    }

    /**
     * A simple test to see if text has any CJK characters at all. It returns
     * after the first such character.
     * 
     * @param buf
     * @return if buf has at least one CJK char.
     */
    public static boolean hasCJKText(String buf) {
        if (buf == null) {
            return false;
        }

        for (char ch : buf.toCharArray()) {
            // Ignore ASCII outright.
            // Ignore Latin-1 outright.
            if (ch < 0xFE) {
                continue;
            }
            Character.UnicodeBlock blk = Character.UnicodeBlock.of(ch);
            if (isCJK(blk)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isCJK(Character.UnicodeBlock blk) {
        // Chinese/CJK group:
        return (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                || (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A)
                || (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B)
                || (blk == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS)
                || (blk == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS)
                || (blk == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT)
                || (blk == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION)
                || (blk == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS)
                // Korean: Hangul
                || (blk == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO)
                || (blk == Character.UnicodeBlock.HANGUL_JAMO)
                || (blk == Character.UnicodeBlock.HANGUL_SYLLABLES)
                // Japanese:
                || (blk == Character.UnicodeBlock.HIRAGANA)
                || (blk == Character.UnicodeBlock.KATAKANA);
    }

    /**
     * Compress bytes from a Unicode string. Conversion to bytes first to avoid
     * unicode or platform-dependent IO issues.
     * 
     * @param buf UTF-8 encoded text
     * @return byte array
     * @throws IOException
     */
    public static byte[] compress(String buf) throws IOException {
        return compress(buf, "UTF-8");
    }

    /**
     * 
     * @param buf text
     * @param charset character set encoding for text
     * @return
     * @throws IOException
     */
    public static byte[] compress(String buf, String charset) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(out);
        gz.write(buf.getBytes(charset));
        gz.close();

        return out.toByteArray();
    }

    /**
     * 
     * @param gzData
     * @return buffer UTF-8 decoded string
     * 
     * @throws IOException
     */
    public static String uncompress(byte[] gzData) throws IOException {
        return uncompress(gzData, "UTF-8");
    }

    /**
     * 
     * @param gzData
     * @param charset character set decoding for text
     * @return
     * @throws IOException
     */
    public static String uncompress(byte[] gzData, String charset) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzData));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buf = new byte[1024];
        int len;
        while ((len = gzipInputStream.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        gzipInputStream.close();
        out.close();

        return new String(out.toByteArray(), charset);
    }

}
