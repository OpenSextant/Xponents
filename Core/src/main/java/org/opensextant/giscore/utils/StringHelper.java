/****************************************************************************************
 *  StringHelper.java
 *
 *  Created: Jul 16, 2009
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
package org.opensextant.giscore.utils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

/**
 * A set of useful string utility functions
 * 
 * @author DRAND
 * @author J.Mathews
 */
public final class StringHelper {
	private static final char[] vowelsLower = new char[] {'a', 'e', 'i', 'o', 'u'};
	private static final char[] vowelsUpper = new char[] {'A', 'E', 'I', 'O', 'U'};
	
	/**
	 * String as an ascii string. If the field name is too long, first try
	 * removing special chars such as punctuation and whitespace then
	 * try removing vowels (after the first character). If the remainder is 
	 * still too long, truncate to no more than 10 characters.
	 * @param fieldname the fieldname, never {@code null} or empty
	 * @return a byte array with the fieldname in ascii
	 */
	public static byte[] esriFieldName(String fieldname) {
		return esriFieldName(fieldname, null);
	}

	/**
	 * String as an ascii string. If the field name is too long, first try
	 * removing special chars such as punctuation and whitespace then
	 * try removing vowels (after the first character). If the remainder is
	 * still too long, truncate to no more than 10 characters.
	 * @param fieldname the fieldname, never {@code null} or empty
	 * @param attrNames Collection of attribute names, if not null then creates
	 * 		unique attribute names and adds those field names to the collection.
	 * @return a byte array with the fieldname in ascii
	 */
	public static byte[] esriFieldName(String fieldname, Collection<String> attrNames) {
		if (StringUtils.isBlank(fieldname)) {
			throw new IllegalArgumentException("fieldname should never be null or empty");
		}
		// first try to remove any special chars such as punctuation and whitespace from the name
		// note some symbols (e.g. '.', '-') work in Shape attribute names loaded from ESRI ArcCatalog
		// but fail to load using mapbojects API so we restrict to the simple set of alpha numerics chars only
		fieldname = fieldname.trim().replaceAll("[^A-Za-z0-9_]+", "");
		if (fieldname.length() > 10) {
			if ("description".equals(fieldname) && attrNames != null && !attrNames.contains("desc")) {
				// shorten 'description' to 'desc'
				fieldname = "desc";
			} else {
			// quick fix 'Error' suffix -> 'Err'
			if (fieldname.endsWith("Error")) fieldname = fieldname.substring(0, fieldname.length() - 2);
			// first try to remove lower-case vowels from the name
			// e.g. Cross-Range Speed Error -> CrssRngSpdErr -> CrssRngSpd
			fieldname = extractVowels(fieldname, vowelsLower, fieldname.length());
			if (fieldname.length() > 10) {
				fieldname = extractDuplicates(fieldname); // e.g. CrssRngErr -> CrsRngEr
				if (fieldname.length() > 10) {
					// if removing lower-case vowels still doesn't make it 10-letters then
					// final name is truncated at 10-characters
					fieldname = extractVowels(fieldname, vowelsUpper, 10);
				}
			}
		}
		}
		else {
			if (fieldname.isEmpty()) {
				// if fieldname entirely composed of non-alphanumeric chars then just use current timestamp
				fieldname = Long.toHexString(System.currentTimeMillis() & 0xffffffffffL);
			}
		}
		if (attrNames != null) {
			// ensure attribute name is unique and add to mapping table, etc.
			if (attrNames.contains(fieldname)) {
				// duplicate. name already exists
				// truncate symbols at end of name to add numeric suffix
				// and keep incrementing until unique name is found
				String baseName = fieldname;
				int count = 1;
				do {
					String suffix = Integer.toString(count++);
					fieldname = baseName.substring(0, baseName.length() - suffix.length()) + suffix;
				} while (attrNames.contains(fieldname));
			}
			// else new attribute. no duplicate
			attrNames.add(fieldname);
		}
        return fieldname.getBytes(StandardCharsets.US_ASCII);
    }

	// remove duplicate chars (e.g. CrssRngSpdErr -> CrsRngSpdEr)
	private static String extractDuplicates(String fieldname) {
		StringBuilder sb = new StringBuilder();
		int len = fieldname.length();
		char lastCh = 0;
		for(int i = 0; i < fieldname.length(); i++) {
			char ch = fieldname.charAt(i);
			if (i > 0 && len > 10 && lastCh == ch) {
				len--; // remove this duplicate letter
			} else {
				sb.append(ch);
				lastCh = ch;
			}
		}
		return sb.toString();
	}

	// remove vowels from fieldname 
	private static String extractVowels(String fieldname, final char[] vowels, int limit) {
		int len = fieldname.length();
		StringBuilder sb = new StringBuilder(limit);
		for(int i = 0; i < fieldname.length(); i++) {
			char ch = fieldname.charAt(i);
			boolean found = false;
			if (len > 10 && i > 0) {
				for (char vowel : vowels) {
					if (vowel == ch) {
						found = true; // found candidate letter to remove
						break;
					}
				}
			}
			if (!found)	{
				sb.append(ch);
				if (sb.length() == limit) {
					break; // limit reached
				}
			}
			else len--; // removed one letter from the field name
		}
		return sb.toString();
	}
}
