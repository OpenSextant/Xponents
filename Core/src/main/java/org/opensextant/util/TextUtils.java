/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
 */
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
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
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
package org.opensextant.util;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.text.StringEscapeUtils;
import org.joda.time.Instant;
import org.opensextant.data.Language;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

/**
 * @author ubaldino
 */
public class TextUtils {

    static final Pattern delws = Pattern.compile("\\s+");
    // Match ALL empty lines:
    // \n followed by other ootional whitespace
    // Up to 2 empty lines or more. This matches 3 line endings
    // The first EOL could be on a non-empty line, but then followed by 2 empty
    // lines.
    // The intent is to reduce 3 or more EOL to 2. Preserving paragraph breaks.
    //
    static final Pattern multi_eol = Pattern.compile("(\n[ \t\r]*){3,}");
    static final Pattern multi_eol2 = Pattern.compile("(\n\r?){2,}");

    /**
     * Checks if non-ASCII and non-LATIN characters are present.
     *
     * @param data any textual data
     * @return true if content is strictly ASCII or Latin1 extended.
     */
    public static final boolean isLatin(String data) {
        char[] ch = data.toCharArray();
        boolean isLatin = true;
        for (char c : ch) {
            if (isASCII(c)) {
                continue;
            }
            if (!Character.isLetter(c)) {
                continue;
            }
            Character.UnicodeBlock blk = Character.UnicodeBlock.of(c);
            if (blk == Character.UnicodeBlock.LATIN_1_SUPPLEMENT || blk == Character.UnicodeBlock.LATIN_EXTENDED_A
                    || blk == Character.UnicodeBlock.LATIN_EXTENDED_B || blk == Character.UnicodeBlock.LATIN_EXTENDED_C
                    || blk == Character.UnicodeBlock.LATIN_EXTENDED_D
                    || blk == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL) {
                continue;
            }

            isLatin = false;
            break;
        }

        //
        return isLatin;
    }

    /**
     * Helpful hints on parsing Unicode phrases. Reference:
     * http://www.rgagnon.com/javadetails/java-0456.html
     */

    private static final String ALPHAMAP_PLAIN_ASCII = "AaEeIiOoUu" // grave
            + "AaEeIiOoUuYy" // acute
            + "AaEeIiOoUuYy" // circumflex
            + "AaOoNn" // tilde
            + "AaEeIiOoUuYy" // umlaut
            + "Aa" // ring
            + "Cc" // cedilla
            + "OoUu" // double acute
            + "Oo" // Scandanavian o
            + "AaEe" // A/E wiht micron
    ;

    private static final String ALPHAMAP_UNICODE = "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9" // grave
            + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD" // acute
            + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177" // circumflex
            + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1" // tilde
            + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF" // umlaut
            + "\u00C5\u00E5" // ring
            + "\u00C7\u00E7" // cedilla
            + "\u0150\u0151\u0170\u0171" // double acute
            + "\u00D8\u00F8" // Scandanavian o Øø
            + "\u0100\u0101\u0112\u0113" // A-bar,  E-bar
    ;

    private static final String COMMON_DIACRITC_HASHMARKS = "\"'`\u00B4\u2018\u2019";

    /**
     * If a string has extended latin diacritics.
     *
     * @param s string to test
     * @return true if a single diacritic is found.
     */
    public static final boolean hasDiacritics(final String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ALPHAMAP_UNICODE.indexOf(c) >= 0) {
                return true;
            }
            if (COMMON_DIACRITC_HASHMARKS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * remove accents from a string and replace with ASCII equivalent Reference:
     * http://www.rgagnon.com/javadetails/java-0456.html Caveat: This implementation
     * is not exhaustive.
     *
     * @param s the string
     * @return converted string
     */
    public static final String replaceDiacritics(final String s) {
        if (s == null) {
            return null;
        }
        if ("".equals(s)) {
            return s;
        }

        StringBuilder sb = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            int pos = ALPHAMAP_UNICODE.indexOf(c);
            if (pos > -1) {
                sb.append(ALPHAMAP_PLAIN_ASCII.charAt(pos));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * @param c a character
     * @return true if c is ASCII
     */
    public static final boolean isASCII(char c) {
        return c > 0 && c <= ASCII_END;
    }

    /**
     * @param c character
     * @return true if c is ASCII a-z or A-Z
     */
    public static final boolean isASCIILetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static final int ASCII_END = 0x7F;

    /**
     * @param data bytes to test
     * @return boolean if data is ASCII or not
     */
    public static boolean isASCII(byte[] data) {
        for (byte b : data) {
            if (b < 0 || b > ASCII_END) {
                return false;
            }
        }
        return true;
    }

    /**
     * Early exit test -- return false on first non-ASCII character found.
     *
     * @param t buffer of text
     * @return true only if every char is in ASCII table.
     */
    public static boolean isASCII(String t) {
        char c;
        for (int x = 0; x < t.length(); ++x) {
            c = t.charAt(x);
            if (c > ASCII_END) {
                return false;
            }
        }
        return true;
    }

    /**
     * count the number of ASCII bytes
     *
     * @param data bytes to count
     * @return count of ASCII bytes
     */
    public static int countASCIIChars(byte[] data) {
        int ascii = 0;
        for (byte b : data) {
            if (b > 0 || b <= ASCII_END) {
                ++ascii;
            }
        }
        return ascii;
    }

    /**
     * Replaces all 3 or more blank lines with a single paragraph break (\n\n)
     *
     * @param t text
     * @return A string with fewer line breaks;
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
     * @param t text
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
     * @param t text
     * @return scrubbed string
     */
    public static String squeeze_whitespace(String t) {
        Matcher m = delws.matcher(t);
        if (m != null) {
            return m.replaceAll(" ");
        }
        return t;
    }

    /**
     * Replace line endings with SPACE
     *
     * @param t text
     * @return scrubbed string
     */
    public static String delete_eol(String t) {
        return t.replace('\n', ' ').replace('\r', ' ');
    }

    public static final char NL = '\n';
    public static final char CR = '\r';
    public static final char SP = ' ';
    public static final char TAB = '\t';
    public static final char DEL = 0x7F;

    /**
     * Delete control chars from text data; leaving text and whitespace only. Delete
     * char (^?) is also
     * removed. Length may differ if ctl chars are removed.
     *
     * @param t text
     * @return scrubbed buffer
     */
    public static String delete_controls(String t) {

        if (t == null) {
            return null;
        }
        StringBuilder tmpCleanBuf = new StringBuilder();

        for (char ch : t.toCharArray()) {
            if ((ch < ' ' && !(ch == TAB || ch == NL)) || (ch == DEL)) {
                continue;
            }

            tmpCleanBuf.append(ch);
        }
        return tmpCleanBuf.toString();
    }

    public static boolean hasDigits(String txt) {
        return (countDigits(txt) > 0);
    }

    public static int countDigits(String txt) {
        return count_digits(txt);
    }

    /**
     * Counts all digits in text.
     *
     * @param txt text to count
     * @return count of digits
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
     * StringUtils in commons isNumeric("1.234") is NOT numeric. Here "1.234" is
     * numeric.
     *
     * @param v val to parse
     * @return true if val is a number
     */
    public static final boolean isNumeric(final String v) {

        if (v == null) {
            return false;
        }

        for (char ch : v.toCharArray()) {

            /*
             * Is the character in .-+Ee ?
             */
            if (ch == '.' || ch == '-' || ch == '+' || ch == 'e' || ch == 'E') {
                continue;
            }

            if (!Character.isDigit(ch)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Counts all whitespace in text.
     *
     * @param txt text
     * @return whitespace count
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
     * Count formatting whitespace. This is helpful in determining if text spans are
     * phrases with
     * multiple TAB or EOL characters. For that matter, any control character
     * contributes to formatting
     * in some way. DEL, VT, HT, etc. So all control characters ( c &lt; ' ') are
     * counted.
     *
     * @param txt input string
     * @return count of format chars
     */
    public static int countFormattingSpace(String txt) {
        if (txt == null) {
            return 0;
        }

        int ws = 0;
        for (char c : txt.toCharArray()) {
            // if (c == '\n' || c == '\r' || c == '\t') {
            if (c < 0x20) {
                ++ws;
            }
        }
        return ws;
    }

    /**
     * For measuring the upper-case-ness of short texts. Returns true if ALL letters
     * in text are
     * UPPERCASE. Allows for non-letters in text.
     *
     * @param dat text or data
     * @return true if text is Upper
     */
    public static boolean isUpper(String dat) {
        return checkCase(dat, 2);
    }

    public static boolean isLower(String dat) {
        return checkCase(dat, 1);
    }

    /**
     * detects if string alpha chars are purely lower case.
     *
     * @param text     text
     * @param textcase 1 lower, 2 upper
     * @return if case matches given textcase param
     */
    public static boolean checkCase(String text, int textcase) {
        if (text == null) {
            return false;
        }
        int caseCount = 0;

        for (char c : text.toCharArray()) {
            if (!Character.isLetter(c)) {
                continue;
            }
            if (textcase == 1) {
                if (Character.isUpperCase(c)) {
                    // Checking for lower case; Fail if upper case is found.
                    return false;
                } else if (Character.isLowerCase(c)) {
                    ++caseCount;
                }
            } else if (textcase == 2) {
                if (Character.isLowerCase(c)) {
                    // Checking for upper case; Fail if lower case is found.
                    return false;
                } else if (Character.isUpperCase(c)) {
                    ++caseCount;
                }
            }
        }
        // IF at least one letter found in the case, return true.
        // It is possible that mixed-language text that has no case-sense
        // is mixed up with ASCII or Romance language text.
        // test LOWER UPPER
        // A b ==> no no
        // A 寨 ==> no yes
        // a 寨 ==> yes no
        return caseCount > 0;
    }

    /**
     * Measure character count, upper, lower, non-Character, whitespace
     *
     * @param text text
     * @return int array with counts.
     */
    public static int[] measureCase(String text) {
        if (text == null) {
            return null;
        }
        int u = 0, l = 0, ch = 0, nonCh = 0, ws = 0;
        int[] counts = new int[5];

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                ++ch;
                if (Character.isUpperCase(c)) {
                    ++u;
                } else if (Character.isLowerCase(c)) {
                    ++l;
                }
            } else if (Character.isWhitespace(c)) {
                ++ws;
            } else {
                ++nonCh; // Other content?
            }
        }

        counts[0] = ch;
        counts[1] = u;
        counts[2] = l;
        counts[3] = nonCh;
        counts[4] = ws;
        return counts;
    }

    /**
     * First measureCase(Text) to acquire counts, then call this routine for a
     * heuristic that suggests
     * the text is mainly upper case. These routines may not work well on languages
     * that are not
     * Latin-alphabet.
     *
     * @param counts word stats from measureCase()
     * @return true if counts represent text that exceeds the "UPPER CASE" threshold
     */
    public static boolean isUpperCaseDocument(final int[] counts) {
        // Method 1: Content = chars + non-chars (not whitespace)
        // measure upper case against ALL content.
        // Method 2: measure upper case against just char content.
        //
        // Method 2 seems best.
        int content = counts[0] /* + counts[3] */ ;
        float uc = ((float) counts[1] / content);
        if (content < 100) {
            return uc > 0.50;
        }
        if (content < 500) {
            return uc > 0.60;
        }
        // Imagine 1KB of text,.. 75% of it is upper case...the document is largely
        // uppercase.
        return uc > 0.75;
    }

    /**
     * This measures the amount of upper case See Upper Case. Two methods to measure
     * -- lower case count
     * compared to all content (char+non-char) or compared to just char content.
     *
     * @param counts word stats from measureCase()
     * @return true if counts represent text that exceeds the "lower case" threshold
     */
    public static boolean isLowerCaseDocument(final int[] counts) {
        int content = counts[0] /* + counts[3] */;
        float lc = ((float) counts[2] / content);
        if (content < 100) {
            return lc > 0.97;
        }
        return lc > 0.98;
    }

    /**
     * Find the text window(s) around a match. Given the size of a buffer, the match
     * and desired width
     * return
     *
     * <pre>
     * prepreprepre      MATCH        postpostpost
     * ^           ^                  ^          ^
     * l-width     l                 l+len   l+len+width
     * left1     left2              right1    right2
     * </pre>
     *
     * @param offset   offset of match
     * @param width    width of window left and right of match
     * @param textsize size of buffer containing match; used for boundary conditions
     * @param matchlen length of match
     * @return window offsets left of match, right of match: [ l1, l2, r1, r2 ]
     */
    public static int[] get_text_window(int offset, int matchlen, int textsize, int width) {
        /*
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
     * @param offset   offset of match
     * @param width    width of window left and right of match
     * @param textsize size of buffer containing match; used for boundary conditions
     * @return window offsets of a text span contianing match [ left, right ]
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
     * @param text text or data
     * @return identifier for the text, an MD5 hash
     * @throws NoSuchAlgorithmException     on err
     * @throws UnsupportedEncodingException on err
     */
    public static String text_id(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        if (text == null) {
            return null;
        }

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        /*
         * For this to be reproducible on all machines, we cannot rely on a
         * default encoding for getBytes. So use getBytes(enc) to be explicit.
         */
        md5.update(text.getBytes("UTF-8"));
        return md5_id(md5.digest());
    }

    /**
     * @param md5digest byte array
     * @return MD5 hash for the data
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
     * Get a list of values into a nice, scrubbed array of values, no whitespace.
     * a, b, c d e, f =&gt; [ "a", "b", "c d e", "f" ]
     *
     * @param s     string to split
     * @param delim delimiter, no default.
     * @return list of split strings, which are also whitespace trimmed
     */
    public static List<String> string2list(String s, String delim) {
        if (s == null) {
            return null;
        }

        List<String> values = new ArrayList<>();
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
     * return the new string, S'.
     * "-name-with.invalid characters;" // replace "-. ;" with "_"
     * "_name_with_invalid_characters_" //
     *
     * @param buf          buffer
     * @param replace      string of characters to replace with the one substitute
     *                     char
     * @param substitution string to insert in place of chars
     * @return scrubbed text
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
     * @param buf    text
     * @param remove string to remove
     * @return scrubbed text
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
     * Replace any of the removal chars with the sub. A many to one replacement.
     * alt: use regex
     * String.replace(//, '')
     *
     * @param buf    text
     * @param remove string to replace
     * @param sub    the replacement string
     * @return scrubbed text
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
     * Example: - a b c remove "-" from string above.
     *
     * @param buf    text
     * @param remove string to remove
     * @return scrubbed text
     */
    public static String removeAnyLeft(String buf, String remove) {

        boolean eval = true;
        // Start from left.
        int x = 0;
        for (char ch : buf.toCharArray()) {
            if (eval && remove.indexOf(ch) >= 0) {
                ++x;
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
     *
     * @param str text
     * @return scrubbed text
     */
    public static String normalizeTextEntity(String str) {
        if (isBlank(str)) {
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

    private static final Pattern tokenizer = Pattern.compile("\\s+");

    /**
     * Return just white-space delmited tokens.
     *
     * @param str text
     * @return tokens
     */
    public static String[] tokens(String str) {
        return tokenizer.split(str.trim());
    }

    /**
     * Return tokens on the right most part of a buffer. If a para break occurs,
     * \n\n or \r\n\r\n, then
     * return the part on the right of the break.
     *
     * @param str text
     * @return whitespace delimited tokens
     */
    public static final String[] tokensRight(String str) {
        if (str.length() == 0) {
            return null;
        }
        String[] toks = multi_eol2.split(str);
        if (toks.length == 0) {
            return null;
        }
        return tokens(toks[toks.length - 1]); // Rightmost
    }

    /**
     * See tokensRight()
     *
     * @param str text
     * @return whitespace delimited tokens
     */
    public static final String[] tokensLeft(String str) {
        if (str.length() == 0) {
            return null;
        }
        String[] toks = multi_eol2.split(str);
        if (toks.length == 0) {
            return null;
        }
        return tokens(toks[0]); // Leftmost
    }

    /**
     * Intended only as a filter for punctuation within a word. Text of the form
     * A.T.T. or U.S. becomes
     * ATT and US. A text such as Mr.Pibbs incorrectly becomes MrPibbs but for the
     * purposes of
     * normalizing tokens this should be fine. Use appropriate tokenization prior to
     * using this as a
     * filter.
     *
     * @param word phrase with periods denoting some abbreviation.
     * @return scrubbed text
     */
    public static String normalizeAbbreviation(String word) {
        return word.replace(".", "");
    }

    /**
     * Supports Phoneticizer utility from OpenSextant v1.x Remove diacritics from a
     * phrase
     *
     * @param word text
     * @return scrubbed text
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
     * Normalize to "Normalization Form Canonical Decomposition" (NFD) REF: http:
     * //stackoverflow.com/questions/3610013/file-listfiles-mangles-unicode-
     * names-with-jdk-6-unicode-normalization-issues This supports proper file name
     * retrieval from file
     * system, among other things. In many situations we see unicode file names --
     * Java can list them,
     * but in using the Java-provided version of the filename the OS/FS may not be
     * able to find the file
     * by the name given in a particular normalized form.
     *
     * @param str text
     * @return normalized string, encoded with NFD bytes
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
    static final Pattern CLEAN_WORD_RIGHT = Pattern.compile("[^\\p{L}\\p{Nd}]+$");
    /**
     * Matches non-text preceeding text
     */
    static final Pattern CLEAN_WORD_LEFT = Pattern.compile("^[^\\p{L}\\p{Nd}]+");
    /**
     * Obscure punctuation pattern that also deals with Unicode single and double
     * quotes
     */
    static final Pattern CLEAN_WORD_PUNCT = Pattern.compile("[\"'.`\\u00B4\\u2018\\u2019]");

    /**
     * Remove any leading and trailing punctuation and some internal punctuation.
     * Internal punctuation
     * which indicates conjunction of two tokens, e.g. a hyphen, should have caused
     * a split into
     * separate tokens at the tokenization stage.
     * Phoneticizer utility from OpenSextant v1.x Remove punctuation from a phrase
     *
     * @param word text
     * @return scrubbed text
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
        // * b4 ACUTE ACCENT
        // * 2018 LEFT SINGLE QUOTATION MARK
        // * 2019 RIGHT SINGLE QUOTATION MARK
        return CLEAN_WORD_PUNCT.matcher(tmp).replaceAll(" ").trim();
    }

    // Alphabetic list of top-N languages -- ISO-639_1 "ISO2" language codes
    //
    public static final String arabicLang = "ar";
    public static final String bahasaLang = "id";
    public static final String chineseLang = "zh";
    public static final String chineseTradLang = "zt";
    public static final String englishLang = "en";
    public static final String farsiLang = "fa";
    public static final String frenchLang = "fr";
    public static final String germanLang = "de";
    public static final String italianLang = "it";
    public static final String japaneseLang = "ja";
    public static final String koreanLang = "ko";
    public static final String portugueseLang = "pt";
    public static final String russianLang = "ru";
    public static final String spanishLang = "es";
    public static final String turkishLang = "tr";
    public static final String thaiLang = "th";
    public static final String vietnameseLang = "vi";
    public static final String romanianLang = "ro";
    private static final Map<String, Language> languageMapISO639 = new HashMap<String, Language>();

    /*
     * Initialize some langauge metadata.
     */
    static {
        try {
            // initLanguageData(); // Barely useful -- this pulls out lang
            // Locales.
            initLOCLanguageData(); // LOC language data is a list of all known
                                   // languages w/ISO codes.
                                   // initICULanguageData(); ICU did not seem to be the right solution.
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    /**
     * If caller wants to add language they can.
     *
     * @return map of lang ID to language obj
     */
    public static Map<String, Language> getLanguageMap() {
        return languageMapISO639;
    }

    /**
     * Initialize language codes and metadata. This establishes a map for the most
     * common language
     * codes/names that exist in at least ISO-639-1 and have a non-zero 2-char ID.
     *
     * <pre>
     * Based on:
     * http://stackoverflow.com/questions/674041/is-there-an-elegant-way
     * -to-convert-iso-639-2-3-letter-language-codes-to-java-lo
     *
     * Actual code mappings: en =&gt; eng eng =&gt; en
     *
     * cel =&gt; '' // Celtic; Avoid this.
     *
     * tr =&gt; tur tur =&gt; tr
     *
     * Names: tr =&gt; turkish tur =&gt; turkish turkish =&gt; tr // ISO2 only
     * </pre>
     */
    public static void initLanguageData() {
        Locale[] locales = Locale.getAvailableLocales();
        for (Locale locale : locales) {
            Language l = new Language(locale.getISO3Language(), locale.getLanguage(), locale.getDisplayLanguage());
            addLanguage(l);
        }
    }

    /**
     * This is Libray of Congress data for language IDs. This is offered as a tool
     * to help downstream
     * language ID and enrich metadata when tagging data from particular countries.
     * Reference: http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt
     *
     * @throws java.io.IOException if resource file is not found
     */
    public static void initLOCLanguageData() throws java.io.IOException {
        //
        // DATA FILE: http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt

        java.io.InputStream io = TextUtils.class.getResourceAsStream("/ISO-639-2_utf-8.txt");
        java.io.Reader featIO = new InputStreamReader(io, "UTF-8");
        CsvListReader langReader = new CsvListReader(featIO, new CsvPreference.Builder('"', '|', "\n").build());

        CellProcessor[] cells = { new Optional(), new Optional(), new Optional(), new Optional(), new NotNull() };
        List<Object> lang = null;

        /*
         * ISO3,XX,ISO2,NAME,NAME_FR
         */
        while ((lang = langReader.read(cells)) != null) {
            //
            String names = (String) lang.get(3);
            if (isBlank(names)) {
                continue;
            }
            if ("NAME".equals(names)) {
                continue;
            }
            List<String> namelist = TextUtils.string2list(names, ";");
            String iso3 = (String) lang.get(0);
            if (iso3.startsWith("#")) {
                continue;
            }
            String iso2 = (String) lang.get(2);
            Language l = new Language(iso3, iso2, namelist.get(0));
            addLanguage(l);
        }

        langReader.close();

        // Popular languages that go by other codes.
        // ISO languages as listed by LOC are listed with Bibliographic vs.
        // Terminological codes.
        // FRE vs. FRA are subtle difference for French, but important if you
        // cannot find French by lang ID.
        //
        // Fully override French and Trad Chinese:
        Language fr = new Language("fra", "fr", "French");
        addLanguage(fr, true);

        Language zhtw = new Language("zh-tw", "zt", "Chinese/Taiwain");
        addLanguage(zhtw, true);

        // Delicately insert more common names and codes as well as locales
        // here.
        Language zh = new Language("zho", "zh", "Chinese");
        languageMapISO639.put("zho", zh);

        Language zhcn = new Language("chi", "zh", "Chinese");
        languageMapISO639.put("zh-cn", zhcn);

        Language fas = new Language("per", "fa", "Farsi");
        languageMapISO639.put("farsi", fas);

        // Locales of English -- are still "English"
        Language en1 = new Language("eng", "en", "English");
        languageMapISO639.put("en-gb", en1);
        languageMapISO639.put("en-us", en1);
        languageMapISO639.put("en-au", en1);
    }

    public static void addLanguage(Language lg) {
        addLanguage(lg, false);
    }

    /**
     * Extend the basic language dictionary. Note -- First language is listed in
     * language map by Name,
     * and is not overwritten. Language objects may be overwritten in map using lang
     * codes.
     * For example, fre = French(fre), fra = French(fra), and french = French(fra)
     * the last one, 'french' = could have been the French(fre) or (fra).
     * Example, 'ger' and 'deu' are both valid ISO 3-alpha codes for German. What to
     * do?
     * TODO: Create a language object that lists both language biblio/terminology
     * codes.
     *
     * @param lg       language object
     * @param override if this value should overwrite an existing one.
     */
    public static void addLanguage(Language lg, boolean override) {
        if (lg == null) {
            return;
        }
        if (lg.getCode() != null) {
            if (override || !languageMapISO639.containsKey(lg.getCode())) {
                languageMapISO639.put(lg.getCode(), lg);
            }
        }
        if (lg.getISO639_1_Code() != null) {
            if (override || !languageMapISO639.containsKey(lg.getISO639_1_Code())) {
                languageMapISO639.put(lg.getISO639_1_Code(), lg);
            }
        }
        if (lg.getNameCode() != null) {
            if (!languageMapISO639.containsKey(lg.getNameCode())) {
                languageMapISO639.put(lg.getNameCode(), lg);
            }
        }
    }

    /**
     * Given an ISO2 char code (least common denominator) retrieve Language Name.
     * This is best effort, so if your code finds nothing, this returns code
     * normalized to lowercase.
     *
     * @param code lang ID
     * @return name of language
     */
    public static String getLanguageName(String code) {
        if (code == null) {
            return null;
        }

        Language L = getLanguage(code);
        return (L != null ? L.getName() : null);
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

        String lookup = code.toLowerCase();
        Language l = languageMapISO639.get(lookup);
        if (l != null) {
            return l;
        }
        // Keep looking.
        if (lookup.contains("_")) {
            lookup = lookup.split("_")[0];
            l = languageMapISO639.get(lookup);
            if (l != null) {
                return l;
            }
        }
        return null;
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

        Language l = getLanguage(code);
        if (l != null) {
            return l.getCode();
        }
        return null;
    }

    private static boolean _isRomanceLanguage(String l) {
        return (l.equals(spanishLang) || l.equals(portugueseLang) || l.equals(italianLang) || l.equals(frenchLang)
                || l.equals(romanianLang));
    }

    /**
     * European languages = Romance + GER + ENG Extend definition as needed.
     *
     * @param l language ID
     * @return true if language is European in nature
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
     * Extend definition as needed.
     *
     * @param l lang ID
     * @return true if language is a Romance language
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
     * @param x a langcode
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
     * Utility method to check if lang ID is Chinese(Traditional or Simplified)...
     *
     * @param x a langcode
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
     * @param x a langcode
     * @return whether langcode is a CJK language
     */
    public static boolean isCJK(String x) {
        Language lang = getLanguage(x);

        if (lang == null) {
            return false;
        }
        String id = lang.getISO639_1_Code();
        if (isBlank(id)) {
            return false;
        }

        return (id.equals(koreanLang) || id.equals(japaneseLang) || id.equals(chineseLang)
                || id.equals(chineseTradLang));
    }

    /**
     * Returns a ratio of Chinese/Japanese/Korean characters: CJK chars / ALL
     * TODO: needs testing; not sure if this is sustainable if block; or if it is
     * comprehensive. TODO:
     * for performance reasons the internal chain of comparisons is embedded in the
     * method; Otherwise
     * for each char, an external method invocation is required.
     *
     * @param buf the character to be tested
     * @return true if CJK, false otherwise
     */
    public static double measureCJKText(String buf) {

        if (buf == null) {
            return -1.0;
        }

        int cjkCount = countCJKChars(buf.toCharArray());

        return ((double) cjkCount) / buf.length();
    }

    private static final int LATIN1_END = 0xFE;

    /**
     * Counts the CJK characters in buffer, buf chars Inspiration:
     * http://stackoverflow
     * .com/questions/1499804/how-can-i-detect-japanese-text-in-a-java-string
     * Assumption is that the
     * char array is Unicode characters.
     *
     * @param chars char array for the text in question.
     * @return count of CJK characters
     */
    public static int countCJKChars(char[] chars) {
        int cjkCount = 0;
        for (char ch : chars) {
            // Ignore ASCII outright.
            // Ignore Latin-1 outright.
            if (ch < LATIN1_END) {
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
     * A simple test to see if text has any CJK characters at all. It returns after
     * the first such
     * character.
     *
     * @param buf text
     * @return if buf has at least one CJK char.
     */
    public static boolean hasCJKText(String buf) {
        if (buf == null) {
            return false;
        }

        char ch;
        for (int x = 0; x < buf.length(); ++x) {
            ch = buf.charAt(x);
            // Ignore ASCII outright.
            // Ignore Latin-1 outright.
            if (ch < LATIN1_END) {
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
        return isChinese(blk) || isJapanese(blk) || isKorean(blk);
    }

    public static boolean isChinese(Character.UnicodeBlock blk) {
        return (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                || (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A)
                || (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B)
                || (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C)
                || (blk == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D)
                || (blk == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS)
                || (blk == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS)
                || (blk == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT)
                || (blk == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION)
                || (blk == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS)
                || (blk == Character.UnicodeBlock.KANGXI_RADICALS) || (blk == Character.UnicodeBlock.YI_SYLLABLES)
                || (blk == Character.UnicodeBlock.YI_RADICALS) || (blk == Character.UnicodeBlock.BOPOMOFO)
                || (blk == Character.UnicodeBlock.BOPOMOFO_EXTENDED) || (blk == Character.UnicodeBlock.KANBUN);
    }

    /**
     * Likely to be uniquely Korean if the character block is in Hangul. But also,
     * it may be Korean if
     * block is part of the CJK ideographs at large. User must check if text in its
     * entirety is part of
     * CJK &amp; Hangul, independently. This method only detects if character block
     * is uniquely Hangul
     * or not.
     *
     * @param blk a Java Unicode block
     * @return true if char block is Hangul
     */
    public static boolean isKorean(Character.UnicodeBlock blk) {
        return (blk == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) || (blk == Character.UnicodeBlock.HANGUL_JAMO)
                || (blk == Character.UnicodeBlock.HANGUL_SYLLABLES)
                || (blk == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A)
                || (blk == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B);
    }

    /**
     * Checks if char block is uniquely Japanese. Check other chars isChinese
     *
     * @param blk a Java Unicode block
     * @return true if char block is Hiragana or Katakana
     */
    public static boolean isJapanese(Character.UnicodeBlock blk) {
        return (blk == Character.UnicodeBlock.HIRAGANA) || (blk == Character.UnicodeBlock.KATAKANA)
                || (blk == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS);
    }

    /**
     * Compress bytes from a Unicode string. Conversion to bytes first to avoid
     * unicode or
     * platform-dependent IO issues.
     *
     * @param buf UTF-8 encoded text
     * @return byte array
     * @throws IOException on error with compression or text encoding
     */
    public static byte[] compress(String buf) throws IOException {
        return compress(buf, "UTF-8");
    }

    /**
     * @param buf     text
     * @param charset character set encoding for text
     * @return byte array for the compressed result
     * @throws IOException on error with compression or text encoding
     */
    public static byte[] compress(String buf, String charset) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(out);
        gz.write(buf.getBytes(charset));
        gz.close();

        return out.toByteArray();
    }

    /**
     * @param gzData byte array containing gzipped buffer
     * @return buffer UTF-8 decoded string
     * @throws IOException on error with decompression or text encoding
     */
    public static String uncompress(byte[] gzData) throws IOException {
        return uncompress(gzData, "UTF-8");
    }

    private static final int ONEKB = 1024;

    /**
     * @param gzData  byte array containing gzipped buffer
     * @param charset character set decoding for text
     * @return buffer of uncompressed, decoded string
     * @throws IOException on error with decompression or text encoding
     */
    public static String uncompress(byte[] gzData, String charset) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzData));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buf = new byte[ONEKB];
        int len;
        while ((len = gzipInputStream.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        gzipInputStream.close();
        out.close();

        return out.toString(charset);
    }

    /**
     * Unicode and social media -- We encounter all sorts of hangups when processing
     * modern unicode
     * text. XML issues, JNI issues, escape utilities, etc. All sorts of problems
     * arise with emoticons
     * aka emoji, and other symbols used in online media. So these utilities are
     * offered to help remove
     * such things prior to data processing.
     */
    // UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS;
    private static final Pattern SCRUB_SYM = Pattern.compile("\\p{block=Miscellaneous Symbols And Pictographs}+");
    private static final Pattern SCRUB_SYM2 = Pattern.compile("\\p{block=Transport and Map Symbols}+");
    private static final Pattern SCRUB_EMOTICONS = Pattern.compile("\\p{block=Emoticons}+");
    private static final Pattern SCRUB_ALPHASUP = Pattern.compile("\\p{block=Enclosed Alphanumeric Supplement}+");
    private static final Pattern SCRUB_TILES1 = Pattern.compile("\\p{block=Mahjong Tiles}+");
    private static final Pattern SCRUB_TILES2 = Pattern.compile("\\p{block=Domino Tiles}+");
    private static final Pattern SCRUB_SYM_MISC = Pattern.compile("\\p{block=Miscellaneous Symbols}+");
    private static final Pattern SCRUB_PLAYCARDS = Pattern.compile("\\p{block=Playing Cards}+");

    /**
     * replace Emoticons with something less nefarious -- UTF-16 characters do not
     * play well with some
     * I/O routines.
     *
     * @param t text
     * @return scrubbed text
     */
    public static String removeEmoticons(String t) {
        return SCRUB_EMOTICONS.matcher(t).replaceAll("{icon}");
    }

    /**
     * Replace symbology
     *
     * @param t text
     * @return scrubbed text
     */
    public static String removeSymbols(String t) {
        String _new = SCRUB_SYM.matcher(t).replaceAll("{sym}");
        _new = SCRUB_SYM2.matcher(_new).replaceAll("{sym2}");
        _new = SCRUB_ALPHASUP.matcher(_new).replaceAll("{asup}");
        _new = SCRUB_TILES1.matcher(_new).replaceAll("{tile1}");
        _new = SCRUB_TILES2.matcher(_new).replaceAll("{tile2}");
        _new = SCRUB_SYM_MISC.matcher(_new).replaceAll("{sym}");
        _new = SCRUB_PLAYCARDS.matcher(_new).replaceAll("{card}");

        return _new;
    }

    /**
     * Count number of non-alphanumeric chars are present.
     *
     * @param t text
     * @return count of chars
     */
    public static int countNonText(final String t) {

        int nonText = 0;
        for (char c : t.toCharArray()) {
            if (!Character.isLetter(c) && Character.isDigit(c) && Character.isWhitespace(c)) {
                ++nonText;
            }
        }
        return nonText;
    }

    /**
     * Find any pattern "ABC#[ABC 123]" -- a hashtag with whitespace. Java Regex
     * note: UNICODE flags are
     * important, otherwise "\w" and other classes match only ASCII. NOTE: These are
     * Twitter hashtags
     * primarily
     */
    public final static Pattern hashtagPattern1 = Pattern.compile("#(\\[\\w+[\\d\\s\\w]+\\])",
            Pattern.UNICODE_CHARACTER_CLASS);
    /**
     * Find any pattern "#ABC123" -- normal hashtag, Java Regex note: UNICODE flags
     * are important,
     * otherwise "\w" and other classes match only ASCII. NOTE: These are Twitter
     * hashtags primarily
     */
    public final static Pattern hashtagPattern2 = Pattern.compile("#(\\w+[\\d\\w]+)", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Parse the typical Twitter hashtag variants.
     *
     * @param tweetText
     * @return
     */
    public static Set<String> parseHashTags(String tweetText) {
        return parseHashTags(tweetText, false);
    }

    /**
     * Takes a string and returns all the hashtags in it. Normalized tags are just
     * lowercased and
     * deduplicated.
     * https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/intro-to-tweet-json
     *
     * @param tweetText text
     * @param normalize if to normalize text by lowercasing tags, etc.
     */
    public static Set<String> parseHashTags(String tweetText, boolean normalize) {
        if (!tweetText.contains("#")) {
            return null;
        }
        Set<String> tagList = null;
        Matcher tagmatch = hashtagPattern1.matcher(tweetText);
        while (tagmatch.find()) {
            String tag = tagmatch.group();
            if (tagList == null) {
                tagList = new HashSet<>();
            }

            tagList.add(normalize ? tag.toLowerCase() : tag);
        }

        tagmatch = hashtagPattern2.matcher(tweetText);
        while (tagmatch.find()) {
            String tag = tagmatch.group();
            if (tagList == null) {
                tagList = new HashSet<>();
            }

            tagList.add(normalize ? tag.toLowerCase() : tag);
        }
        return tagList;
    }

    /**
     * see default implementation below
     *
     * @see #parseNaturalLanguage(String)
     *      replace HTML, URLs removed, Tags and entity markers (@ and #) stripped;
     *      Tags and entities
     *      left in place.
     * @param raw raw text
     * @return cleaner looking text
     */
    public static String parseNaturalLanguage(String raw) {
        return parseNaturalLanguage(raw, true, true, true, true);
    }

    /**
     * HTTP://ADDRESS pattern, gets ADDRESS; Case insensitive and contains ASCII
     * chars.
     */
    static Pattern urlHTTPPattern = Pattern.compile("https?://[\u0021-\u007F]+", Pattern.CASE_INSENSITIVE);

    /**
     * Given tweet text or any [social media] text remove entities or other markers:
     * - URLs are removed
     * - entities are stripped of "@" - hashtags are stripped of "#" - HTML: &amp;
     * is converted to an
     * ampersand - HTML: escaped angle brackets are replaced with { and } for gt and
     * lt, respectively -
     * HTML: remaining special chars are converted back to unicode; remaining
     * ampersand is replaced with
     * "+" Whitespaces (space, newlines, tabs, etc.) are reduced.
     * DEPRECATED: the use of the tags=true flag to replace hashtags with blank is
     * not supported.
     * #tag&lt;unicode text&gt; is a problem. It is hard to tell in some cases where
     * the hashtag ends.
     * In Weibo, #tag#&lt;unicode text&gt; is used to denote that tag has a
     * start/end But in Twitter,
     * tag format is "#tag" or "#[phrase here]" etc. So there is no generic hashtag
     * replacement.
     *
     * @param raw          original text
     * @param unescapeHtml unescape HTML
     * @param remURLs      remove URLs
     * @param remTags      remove hash tags
     * @param remEntities  remove other entities
     * @return text less entities.
     */
    public static String parseNaturalLanguage(final String raw, boolean unescapeHtml, boolean remURLs, boolean remTags,
            boolean remEntities) {
        String text = raw;
        if (remURLs) {
            text = urlHTTPPattern.matcher(text).replaceAll(" ");
        }

        if (unescapeHtml) {
            // Unescape text. This now becomes almost HTML in places.
            text = text.replace("&amp;", "&").replace("&gt;", "}").replace("&lt;", "{");
            if (text.contains("&#")) {
                text = StringEscapeUtils.unescapeHtml4(text);
            }

            if (text.contains("&")) {
                text = text.replace("&", "+");
            }
        }
        if (remTags) {
            text = text.replace("#", " ");
        }
        if (remEntities) {
            text = text.replace("@", " ");
        }
        // Elipsis appears to be common enough, we replace its unicode variant with
        // "...".
        //
        text = text.replace("…", "...");

        /* Offsets are not preserved with this sort of function */
        return TextUtils.squeeze_whitespace(text);
    }

    /**
     * A limited-scope date parsing: Parse properly formatted strings for example,
     * ISO date/time strings
     * stored in one of our Solr indices.
     *
     * @param dt ISO date/time string.
     * @return
     */
    public final static java.util.Date parseDate(final String dt) {
        if (dt == null) {
            return null;
        }
        return Instant.parse(dt).toDate();
    }
}
