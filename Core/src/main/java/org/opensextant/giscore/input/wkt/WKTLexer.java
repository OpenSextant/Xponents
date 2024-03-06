/****************************************************************************************
 *  WKTLexer.java
 *
 *  Created: Jan 12, 2012
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2012
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
package org.opensextant.giscore.input.wkt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Stack;

/**
 * Read tokens from a OGC WKT file. Ignores whitespace. Reads strings, numbers
 * and special characters.
 * 
 * @author DRAND
 */
public class WKTLexer {
	/**
	 * The reader, must be a buffered reader
	 */
	private final Reader reader;

	/**
	 * Buffer of tokens
	 */
	private final Stack<WKTToken> buffer = new Stack<>();

	/**
	 * Ctor
	 * 
	 * @param reader
	 */
	public WKTLexer(Reader reader) {
		if (reader == null) {
			throw new IllegalArgumentException("reader should never be null");
		}
		if (!(reader instanceof BufferedReader)) {
			throw new IllegalArgumentException(
					"reader must be a buffered reader");
		}
		this.reader = reader;
	}

	/**
	 * Get the nextToken. Reads the first character and that decides what we're
	 * reading.
	 * 
	 * @return if there are tokens saved on the stack then pop the first one off
	 *         the stack and return it. Otherwise read the next token string or
	 *         return {@code null} if we're at the end of the stream
	 * @throws IOException  If an I/O error occurs
	 */
	public WKTToken nextToken() throws IOException {
		if (!buffer.empty()) {
			return buffer.pop();
		}

		int ch; // Skip all leading whitespace
		do {
			ch = reader.read();
			if (ch < 0)
				return null;
		} while (Character.isWhitespace(ch));
		if (Character.isLetter(ch)) {
			return readIdentifier(ch);
		} else if (Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.') {
			try {
				return readNumber(ch);
			} catch(NumberFormatException nfe) {
				throw new IOException(nfe);
			}
		} else {
			return new WKTToken((char) ch); // Treat as a special character
		}
	}
	
	/**
	 * Push back the given token onto the stack. The given token will be the
	 * next token to be returned by the next call to {@link #nextToken()} 
	 * until the next call to push(). Calls to push therefore
	 * establish a last in first out stack.
	 * <p>
	 * For example, if one reads token a, then token b and pushes a first then
	 * b and reads next token, b will be the first to be returned then a.
	 * 
	 * @param token
	 */
	public void push(WKTToken token) {
		buffer.push(token);
	}

	/**
	 * Read a number from the reader. By counting dots we avoid reading a second
	 * decimal point. Thus +1234.213.4123 should be read by the lexer as
	 * +1234.213 and .4123 not as one single number. This is probably a user
	 * error, but better to write hard to break code.
	 * 
	 * @param ch
	 *            initial character read
	 * @return token
	 * @throws IOException  If an I/O error occurs
	 * @throws NumberFormatException if the value does not contain
     *         a parsable {@code double}.
	 */
	private WKTToken readNumber(int ch) throws IOException {
		StringBuilder sb = new StringBuilder(10);
		int dots = ch == '.' ? 1 : 0;
		do {
			sb.append((char) ch);
			reader.mark(1);
			ch = reader.read();
			if (ch == '.')
				dots++;
			if (dots > 1)
				break;
		} while (ch >= 0 && (Character.isDigit(ch) || ch == '.'));
		if (ch >= 0)
			reader.reset();
		return new WKTToken(Double.parseDouble(sb.toString()));
	}

	/**
	 * Read an identifier. For this lexer an identifier is a series of letters.
	 * 
	 * @param ch
	 *            initial character read
	 * @return token
	 * @throws IOException  If an I/O error occurs
	 */
	private WKTToken readIdentifier(int ch) throws IOException {
		StringBuilder sb = new StringBuilder(32);
		while (ch >= 0
				&& (Character.isLetter(ch) || Character.isWhitespace(ch))) {
			if (!Character.isWhitespace(ch)) {
				sb.append((char) ch);
			}
			reader.mark(1);
			ch = reader.read();
		}
		if (ch >= 0)
			reader.reset();
		return new WKTToken(sb.toString());
	}
}
