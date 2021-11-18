package org.opensextant.extraction;

import java.util.ArrayList;
import java.util.List;

import org.opensextant.util.TextUtils;

public class MatcherUtils {

    /**
     * Reduce actual valid matches by identifying duplicates or sub-matches.
     * Overlapping spans are not
     * considered filtered out.
     *
     * @param matches set of matches you need to sift through to find filtered out
     *                items.
     */
    public static void reduceMatches(List<TextMatch> matches) {
        int len = matches.size();

        for (int i = 0; i < len; ++i) {
            TextMatch M = matches.get(i);
            long m1 = M.start;
            long m2 = M.end;
            // Compare from
            for (int j = i + 1; j < len; ++j) {
                TextMatch N = matches.get(j);

                long n1 = N.start;
                long n2 = N.end;

                if (m2 < n1) {
                    // M before N entirely
                    continue;
                }
                if (m1 > n2) {
                    // M after N entirely
                    continue;
                }

                // Same span, but duplicate.
                if (n1 == m1 && n2 == m2) {
                    N.is_duplicate = true;
                    M.is_overlap = true;
                    continue;
                }
                // M entirely within N
                if (n1 <= m1 && m2 <= n2) {
                    M.is_submatch = true;
                    N.is_overlap = true;
                    continue;
                }

                // N entirely within M
                if (n1 >= m1 && m2 >= n2) {
                    M.is_overlap = true;
                    N.is_submatch = true;
                    continue;
                }

                // Overlapping spans
                M.is_overlap = true;
                N.is_overlap = true;
            }
        }
    }

    public static final String START_CHARS = "<[";
    public static final String CLOSE_CHARS = ">]";

    /**
     * Trivial attempt at locating edges of tags in data. This allows us to tag any
     * data, but post-filter any items match within tags, that is if you have
     *
     * <pre>
     * [A]text[A] [A]text[/A] [A data]text
     * </pre>
     *
     * where A is a tag, but the
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
     * </pre>
     *
     * @param text
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
            int starter_cx = START_CHARS.indexOf(c);
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
                closer_c = CLOSE_CHARS.charAt(starter_cx);
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
     * matches appear within
     * tags. So we say [A]match[/A] -- match is good. [A]match -- match is good. [A
     * match]text
     * other_match -- match is not good; other_match is fine.
     * The result is that matches inside tags are "filteredOut"
     *
     * @param buffer  the raw signal text where the matches were found.
     * @param matches TextMatch array
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
            if (m.isFilteredOut()) {
                continue;
            }
            for (TextEntity span : spans) {
                if (m.isWithin(span)) {
                    m.setFilteredOut(true);
                    break;
                }
            }
        }
    }
}
