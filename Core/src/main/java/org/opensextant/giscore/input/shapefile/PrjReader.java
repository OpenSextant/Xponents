/****************************************************************************************
 *  PrjReader.java
 *
 *  Created: Jul 28, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.input.shapefile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read ESRI WKT for a spatial reference system from a source into an internal
 * datastructure for processing. Each WKT entry is of the following form:<br>
 * TAG := [a-zA-Z]{1}[a-zA-Z0-9_]*<br>
 * START := '['<br>
 * END := ']'<br>
 * VALUE := '"'.*'"'<br>
 * NUMBER := [-+]?[0-9]*'.'[0-9]*<br>
 * ENTRY := VALUE | TAG | NUMBER<br>
 * NIL := <br>
 * LIST := NIL | ENTRY | ENTRY , LIST<br>
 * ENTRY := TAG [ START LIST END ]
 * 
 * @author DRAND
 * 
 */
public class PrjReader {

	private static final Pattern TAG = Pattern
			.compile("\\s*(\\p{Alpha}[\\p{Alnum}_]*)");
	private static final Pattern START = Pattern.compile("\\s*\\[");
	private static final Pattern END = Pattern.compile("\\s*\\]");
	private static final Pattern QSTRING = Pattern.compile("\\s*\"([^\"]*)\"");
	private static final Pattern NUMBER = Pattern.compile("\\s*([-+]?" + //
			"(?:" + //
			"(?:\\p{Digit}*\\.\\p{Digit}+)" + //
			"|" + //
			"(?:\\p{Digit}+\\.?)" + //
			")" + //
			")\\s*");
	private static final Pattern SEP = Pattern.compile("\\s*,");
	/**
	 * Root entry, setup in ctor when parse is called.
	 */
	private final Entry root;

	@SuppressWarnings("unchecked")
	public static class Entry {
		private final String tag;
		private final List values = new ArrayList();

		/**
		 * Ctor
		 * 
		 * @param tag
		 */
		public Entry(String tag) {
			if (tag == null || tag.trim().length() == 0) {
				throw new IllegalArgumentException(
						"tag should never be null or empty");
			}
			this.tag = tag;
		}

		public String getTag() {
			return tag;
		}

		public List getValues() {
			return Collections.unmodifiableList(values);
		}

		public void addValue(Object val) {
			if (val == null) {
				throw new IllegalArgumentException("val should never be null");
			}
			values.add(val);
		}

		public String toString() {
			StringBuilder b = new StringBuilder();
			toString(0, this, b);
			return b.toString();
		}

		private static void toString(int indent, Entry entry,
				StringBuilder buffer) {
			if (indent > 0) {
				buffer.append('\n');
				buffer.append(" ".repeat(indent));
			}
			buffer.append(entry.getTag());
			List vals = entry.getValues();
			if (vals != null) {
				buffer.append('[');
				boolean first = true;
				for (Object val : vals) {
					if (first) {
						first = false;
					} else {
						buffer.append(",");
					}
					if (val instanceof Number) {
						buffer.append(val);
					} else if (val instanceof String) {
						buffer.append('"');
						buffer.append(val);
						buffer.append('"');
					} else if (val instanceof Entry) {
						toString(indent + 4, (Entry) val, buffer);
					} else {
						buffer.append("unexpected: ").append(val.getClass());
					}
				}
				buffer.append(']');
			}
		}
	}

	/**
	 * Ctor
	 * 
	 * @param text
	 *            text to parse, never {@code null} or empty
	 */
	public PrjReader(String text) {
		if (text == null || text.trim().length() == 0) {
			throw new IllegalArgumentException(
					"text should never be null or empty");
		}
		Object[] val = parseEntry(0, text);
		root = (Entry) val[0];
	}

	/**
	 * For each entry step through and parse. Recurse if we find a subentry.
	 * 
	 * @param offset
	 *            offset into the text
	 * @param wkt
	 *            text to parse
	 * @return the entry [0] and the offset [1]
	 */
	protected Object[] parseEntry(int offset, String wkt) {
		Matcher m = TAG.matcher(wkt.substring(offset));
		if (m.lookingAt()) {
			String tag = m.group(1);
			Entry rval = new Entry(tag);
			offset = offset + m.end();
			m = START.matcher(wkt.substring(offset));
			if (m.lookingAt()) {
				offset = offset + m.end();
				while (true) {
					offset = parseValue(rval, offset, wkt);
					m = SEP.matcher(wkt.substring(offset));
					if (!m.lookingAt()) {
						break;
					}
					offset = offset + m.end();
				}
				m = END.matcher(wkt.substring(offset));
				if (!m.lookingAt()) {
					throw new IllegalStateException(
							"Expected ] at the end of the WKT entry");
				}
				offset = offset + m.end();
			}
			return new Object[] { rval, offset };
		} else {
			throw new IllegalStateException(
					"Found no tag at start of substring "
							+ wkt.substring(offset, offset + 50));
		}
	}

	/**
	 * Parse a single value, recursing if we find another entry
	 * 
	 * @param entry
	 *            the current entry being parsed
	 * @param offset
	 *            the offset into the string
	 * @param wkt
	 *            the string
	 * @return
	 */
	private int parseValue(Entry entry, int offset, String wkt) {
		Matcher m = NUMBER.matcher(wkt.substring(offset));
		if (m.lookingAt()) {
			entry.addValue(Double.valueOf(m.group(1)));
			return offset + m.end();
		}
		m = QSTRING.matcher(wkt.substring(offset));
		if (m.lookingAt()) {
			entry.addValue(m.group(1));
			return offset + m.end();
		}
		Object[] val = parseEntry(offset, wkt);
		entry.addValue(val[0]);
		return ((Number) val[1]).intValue();
	}

	/**
	 * Find a value starting at the root using the given path of tags. If a tag
	 * is not found at the root then the hierarchy is searched depth first.
	 * 
	 * @param tags
	 *            tags to search for, the first tag is matched against the
	 *            current element or the children of the element. Additional
	 *            tags must be immediately below the matching element, never
	 *            {@code null} or empty for any individual value.
	 * @return {@code null} if no match is found, the entry otherwise
	 */
	public Entry getEntry(String... tags) {
		if (tags == null || tags.length == 0) {
			throw new IllegalArgumentException(
					"tags should never be null or empty");
		}
		return getEntry(root, 0, tags);
	}

	/**
	 * Get the value specified
	 * 
	 * @param current
	 * @param index
	 * @param tags
	 * @return
	 */
	private Entry getEntry(Entry current, int index, String[] tags) {
		String first = tags[index];
		int next = index + 1; // Point to the next tag
		if (current.getTag().equals(first)) {
			if (next >= tags.length) {
				return current;
			}
		} else {
			if ((index + 1) >= tags.length) {
				// No more tags but no match, return null
				return null;
			}
			next = index; // Continue searching at the next level down at the
			// same tag
		}
		List col = current.getValues();
		for (Object el : col) {
			if (el instanceof Entry) {
				Entry rval = getEntry((Entry) el, next, tags);
				if (rval != null) {
					return rval;
				}
			}
		}
		return null;
	}

	/**
	 * @return get the root
	 */
	public Entry getRoot() {
		return root;
	}
}
