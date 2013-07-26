/*
 * Copyright 2013 ubaldino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.opensextant.util;

import java.util.regex.Pattern;

/**
 * In working with some Unicode texts we found that certain character blocks had
 * very different behavior when processed by some IO libraries, namely opensextant's GISCore GDB library
 * which has a JNI binding.  Just the same, it is helpful to be able to scrub text down to 
 * plain text with fewer symbols for certain use cases.
 * 
 * 
 * 
 * Java7 required here.
 * 
 * @author ubaldino
 */
public class UnicodeTextUtils {
    
    
    // UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS;
    private final static Pattern scrub_symbols = Pattern.compile("\\p{block=Miscellaneous Symbols And Pictographs}+");
    private final static Pattern scrub_symbols2 = Pattern.compile("\\p{block=Transport and Map Symbols}+");
    private final static Pattern scrub_emoticon = Pattern.compile("\\p{block=Emoticons}+");
    private final static Pattern scrub_alphasup = Pattern.compile("\\p{block=Enclosed Alphanumeric Supplement}+");
    private final static Pattern scrub_symbols_tiles1 = Pattern.compile("\\p{block=Mahjong Tiles}+");
    private final static Pattern scrub_symbols_tiles2 = Pattern.compile("\\p{block=Domino Tiles}+");
    private final static Pattern scrub_symbols_misc = Pattern.compile("\\p{block=Miscellaneous Symbols}+");
    private final static Pattern scrub_symbols_cards = Pattern.compile("\\p{block=Playing Cards}+");

    /**
     * replace Emoticons with something less nefarious -- UTF-16 characters do
     * not play well with some I/O routines.
     *
     * @param t
     * @return
     */
    public static String remove_emoticons(String t) {
        return scrub_emoticon.matcher(t).replaceAll("{icon}");
    }

    /**
     * Replace symbology
     *
     * @param t
     * @return
     */
    public static String remove_symbols(String t) {
        String _new = scrub_symbols.matcher(t).replaceAll("{sym}");
        _new = scrub_symbols2.matcher(_new).replaceAll("{sym2}");
        _new = scrub_alphasup.matcher(_new).replaceAll("{asup}");
        _new = scrub_symbols_tiles1.matcher(_new).replaceAll("{tile1}");
        _new = scrub_symbols_tiles2.matcher(_new).replaceAll("{tile2}");
        _new = scrub_symbols_misc.matcher(_new).replaceAll("{sym}");
        _new = scrub_symbols_cards.matcher(_new).replaceAll("{card}");

        return _new;
    }    
}
