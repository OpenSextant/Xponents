package org.opensextant.extraction;

import java.util.ArrayList;
import java.util.List;

import org.opensextant.util.TextUtils;

public class MatcherUtils {

    public static final String startChars = "<[";
    public static final String closeChars = ">]";

    /**
     * Trivial attempt at locating edges of tags in data. This allows us to tag any
     * data, but post-filter any items match within tags, that is if you have <pre>
     * [A]text[A] [A]text[/A] [A data]text </pre> where A is a tag, but the
     * (angle,paren,square,curly) bracket marks the start of a tag area. We are
     * finding those start/ends of the tag area, not the text span represented by
     * the matching tags. Supported characters are &gt; and [ for now.
     * 
     * <pre>
     *  Tags are:
     *    CHAR TEXT ? CHAR     # &lt;a href=''&gt;
     *    
     *  Tags are not:
     *    CHAR SPACE TEXT .....# an open tag, followed by non-alpha and/or not closed.
     *    
     *  Tag names are always ASCII, as these are simple tag detection tools.
     *  Uniccode tags are allowable.
     *  
     *  To properly detect end tags, [/a] or &lt;/a&gt; then "/" is the only allowable character after 
     *  an opening char for a tag. 
     *    </pre>
     * @param text
     * 
     * @return list of TextEntity with no text, just span offsets
     */
    public static List<TextEntity> findTagSpans(String text) {
        List<TextEntity> spans = new ArrayList<>();
        boolean inSpan = false;
        char closer_c = '\0';
        int span_start = 0;
        char c;
        char next_c;
        for (int x = 0; x < text.length(); ++x) {
            c = text.charAt(x);
            int starter_cx = startChars.indexOf(c);
            if (!inSpan && starter_cx >= 0) {
                /*
                 * Check if this is a bare open tag. Ignore if so.
                 */
                next_c = text.charAt(x + 1);
                if (!TextUtils.isASCIILetter(next_c) && next_c != '/') {
                    continue;
                }
                span_start = x;
                inSpan = true;
                closer_c = closeChars.charAt(starter_cx);
            } else {
                if (c == closer_c) {
                    inSpan = false;
                    spans.add(new TextEntity(span_start, x));
                }
            }
        }

        // If a tag was opened, but not closed before end of text snippet, 
        // we will not report that span, as these are limited utilities.
        // SAX-style or DOM-style parsing may provide more rigorous detection of tags 
        return spans;
    }

    /**
     * A simple demonstration of how to sift through matches identifying which
     * matches appear within tags. So we say [A]match[/A] -- match is good. [A]match
     * -- match is good. [A match]text other_match -- match is not good; other_match
     * is fine.
     * 
     * The result is that matches inside tags are "filteredOut"
     * 
     * @param buffer
     *            the raw signal text where the matches were found.
     * @param matches
     *            TextMatch array
     */
    public static void filterMatchesBySpans(String buffer, List<TextMatch> matches) {
        if (matches.isEmpty()) {
            return;
        }
        List<TextEntity> spans = findTagSpans(buffer);
        if (spans.isEmpty()) {
            return;
        }

        for (TextMatch m : matches) {
            for (TextEntity span : spans) {
                if (span.contains(m.start) || span.contains(m.end)) {
                    m.setFilteredOut(true);
                    break;
                }
            }
        }
    }
}
